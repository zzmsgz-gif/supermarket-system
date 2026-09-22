package com.example.supermarket.service;

import com.example.supermarket.entity.Announcement;
import com.example.supermarket.entity.MemberDay;
import com.example.supermarket.dto.MemberDayRequest;
import com.example.supermarket.dto.MemberDayResponse;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.AnnouncementRepository;
import com.example.supermarket.repository.MemberDayRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会员日（每月几号消费积分翻倍）。
 *
 * <p>以前「会员日 每月18号 双倍积分」只是公告里的一句话，**后端根本没实现** —— 属于假承诺。
 * 现在：日期与倍率由后台「会员日」菜单维护，积分发放时按**下单日**判定并加倍
 * （见 {@link MemberService#earnPoints(java.math.BigDecimal, LocalDate)}）。
 *
 * <p>⚠️ 公告栏里那条「会员日…」**由本服务自动维护**：每次增删改都会按当前配置重写它的标题/正文，
 * 一条都没有时把它停用 —— 目的是让文案永远不会跟配置对不上（正是原先的问题）。
 * 所以后台面板里提示了「这条公告会随会员日设置自动更新」，想写自己的文案就另建一条公告。
 */
@Service
public class MemberDayService {

    private static final byte ENABLED = 1;
    private static final byte DISABLED = 0;
    private static final byte NOT_DELETED = 0;

    /** 系统维护的那条公告：标题以此开头（种子里的就是「会员日 每月18号 双倍积分」） */
    private static final String ANNOUNCEMENT_TITLE_PREFIX = "会员日";
    private static final int ANNOUNCEMENT_SORT_ORDER = 4;

    private static final BigDecimal MIN_MULTIPLIER = new BigDecimal("1.0");
    private static final BigDecimal MAX_MULTIPLIER = new BigDecimal("10.0");
    private static final BigDecimal NO_BONUS = BigDecimal.ONE;

    private final MemberDayRepository memberDayRepository;
    private final AnnouncementRepository announcementRepository;

    public MemberDayService(MemberDayRepository memberDayRepository, AnnouncementRepository announcementRepository) {
        this.memberDayRepository = memberDayRepository;
        this.announcementRepository = announcementRepository;
    }

    // ---------------- 判定（积分发放用） ----------------

    /**
     * 该日期对应的积分倍率；不是会员日（或会员日被停用）时返回 1（不加倍）。
     */
    @Transactional(readOnly = true)
    public BigDecimal multiplierFor(LocalDate date) {
        if (date == null) {
            return NO_BONUS;
        }
        return memberDayRepository.findByEnabledOrderByDayOfMonthAsc(ENABLED).stream()
                .filter(d -> d.getDayOfMonth() != null && d.getDayOfMonth() == date.getDayOfMonth())
                .map(MemberDay::getMultiplier)
                .filter(m -> m != null && m.compareTo(NO_BONUS) > 0)
                .max(BigDecimal::compareTo)
                .orElse(NO_BONUS);
    }

    @Transactional(readOnly = true)
    public boolean isMemberDay(LocalDate date) {
        return multiplierFor(date).compareTo(NO_BONUS) > 0;
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true)
    public List<MemberDayResponse> listAll() {
        return memberDayRepository.findAllByOrderByDayOfMonthAsc().stream()
                .map(MemberDayResponse::from)
                .toList();
    }

    /**
     * 前台展示用：启用中的会员日 + 下一次会员日 + 一句话描述（会员中心直接用，免得前端拼文案）。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> publicView() {
        List<MemberDay> active = memberDayRepository.findByEnabledOrderByDayOfMonthAsc(ENABLED);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("enabled", !active.isEmpty());
        view.put("days", active.stream().map(MemberDayResponse::from).toList());
        view.put("multiplier", active.stream().map(MemberDay::getMultiplier)
                .filter(m -> m != null)
                .max(BigDecimal::compareTo)
                .orElse(NO_BONUS));
        view.put("slogan", slogan(active));
        view.put("nextDate", nextDate(active));
        return view;
    }

    // ---------------- 后台维护 ----------------

    @Transactional
    public MemberDayResponse create(MemberDayRequest request) {
        Integer day = request.getDayOfMonth();
        memberDayRepository.findByDayOfMonth(day).ifPresent(existing -> {
            throw new BusinessException(400, "每月 " + day + " 号已经配置过了");
        });
        MemberDay entity = new MemberDay();
        entity.setDayOfMonth(day);
        entity.setMultiplier(normalizeMultiplier(request.getMultiplier()));
        entity.setRemark(normalizeRemark(request.getRemark()));
        entity.setEnabled((byte) (Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1));
        MemberDay saved = memberDayRepository.save(entity);
        syncAnnouncement();
        return MemberDayResponse.from(saved);
    }

    @Transactional
    public MemberDayResponse update(Long id, MemberDayRequest request) {
        MemberDay entity = memberDayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("会员日不存在"));
        Integer day = request.getDayOfMonth();
        memberDayRepository.findByDayOfMonth(day)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BusinessException(400, "每月 " + day + " 号已经配置过了");
                });
        entity.setDayOfMonth(day);
        entity.setMultiplier(normalizeMultiplier(request.getMultiplier()));
        entity.setRemark(normalizeRemark(request.getRemark()));
        if (request.getEnabled() != null) {
            entity.setEnabled((byte) (request.getEnabled() ? 1 : 0));
        }
        MemberDay saved = memberDayRepository.save(entity);
        syncAnnouncement();
        return MemberDayResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        MemberDay entity = memberDayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("会员日不存在"));
        memberDayRepository.delete(entity);
        syncAnnouncement();
    }

    private BigDecimal normalizeMultiplier(BigDecimal multiplier) {
        BigDecimal value = multiplier == null ? new BigDecimal("2.0")
                : multiplier.setScale(1, RoundingMode.HALF_UP);
        if (value.compareTo(MIN_MULTIPLIER) < 0 || value.compareTo(MAX_MULTIPLIER) > 0) {
            throw new BusinessException(400, "积分倍率需在 1.0 - 10.0 之间");
        }
        return value;
    }

    private String normalizeRemark(String remark) {
        return remark == null ? "" : remark.trim();
    }

    // ---------------- 文案与公告同步 ----------------

    /** 「会员日（每月18号）消费可得双倍积分」这类描述；没有启用项时返回空串。 */
    private String slogan(List<MemberDay> active) {
        if (active.isEmpty()) {
            return "";
        }
        String days = active.stream().map(d -> String.valueOf(d.getDayOfMonth())).collect(Collectors.joining("、"));
        BigDecimal multiplier = active.stream().map(MemberDay::getMultiplier)
                .filter(m -> m != null).max(BigDecimal::compareTo).orElse(new BigDecimal("2.0"));
        boolean same = active.stream().map(MemberDay::getMultiplier).distinct().count() == 1;
        String factor = same ? multiplierLabel(multiplier) + "积分" : "指定日期积分翻倍";
        return "会员日（每月" + days + "号）消费可得" + factor;
    }

    private String multiplierLabel(BigDecimal multiplier) {
        if (multiplier == null) {
            return "双倍";
        }
        if (multiplier.compareTo(new BigDecimal("2.0")) == 0) {
            return "双倍";
        }
        return multiplier.stripTrailingZeros().toPlainString() + " 倍";
    }

    /** 下一次会员日（今天就是会员日时返回今天） */
    private LocalDate nextDate(List<MemberDay> active) {
        if (active.isEmpty()) {
            return null;
        }
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 62; i++) {                     // 最多看两个月，必然能遇到
            LocalDate candidate = today.plusDays(i);
            int day = candidate.getDayOfMonth();
            boolean hit = active.stream().anyMatch(d -> d.getDayOfMonth() != null && d.getDayOfMonth() == day)
                    && day <= YearMonth.from(candidate).lengthOfMonth();
            if (hit) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 把「会员日」那条公告的标题/正文重写成与当前配置一致；一条会员日都没有时把它停用（文案不该再承诺）。
     * ⚠️ 只认标题以「会员日」开头的第一条 —— 系统托管，后台面板已提示。
     */
    private void syncAnnouncement() {
        List<MemberDay> active = memberDayRepository.findByEnabledOrderByDayOfMonthAsc(ENABLED);
        Optional<Announcement> existing = announcementRepository
                .findFirstByDeletedAndTitleStartingWith(NOT_DELETED, ANNOUNCEMENT_TITLE_PREFIX);

        if (active.isEmpty()) {
            existing.ifPresent(a -> {
                a.setEnabled(DISABLED);
                announcementRepository.save(a);
            });
            return;
        }

        Announcement target = existing.orElseGet(() -> {
            Announcement a = new Announcement();
            a.setType(Announcement.TYPE_PROMOTION);
            a.setSortOrder(ANNOUNCEMENT_SORT_ORDER);
            a.setDeleted(NOT_DELETED);
            return a;
        });
        target.setTitle("会员日 每月" + active.stream()
                .map(d -> String.valueOf(d.getDayOfMonth())).collect(Collectors.joining("、")) + "号 "
                + multiplierLabel(active.get(0).getMultiplier()) + "积分");
        target.setContent(slogan(active) + "；积分可在结算时抵扣现金。");
        target.setEnabled(ENABLED);
        target.setPublishTime(LocalDateTime.now());
        announcementRepository.save(target);
    }

}
