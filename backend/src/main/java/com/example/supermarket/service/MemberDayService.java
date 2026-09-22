package com.example.supermarket.service;

import com.example.supermarket.dto.MemberDayRequest;
import com.example.supermarket.dto.MemberDayResponse;
import com.example.supermarket.entity.MemberDay;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.MemberDayRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会员日：**指定具体日期**（年月日）当天消费积分翻倍。
 *
 * <p>以前「会员日 每月18号 双倍积分」只是公告里的一句话、后端根本没实现（假承诺）。
 * 现在日期与倍率由后台「会员日」菜单维护，积分发放时按**下单日**判定并加倍
 * （见 {@link MemberService#earnPoints(BigDecimal, LocalDate)}）。
 *
 * <p>⚠️ 两个刻意的边界：
 * <ul>
 *   <li><b>公告不再由系统改写</b>：公告栏那条「会员日…」是运营文案，由管理员自己维护 ——
 *       系统代写会和运营的手写内容打架（2026-09-22 按用户要求去掉自动同步）。</li>
 *   <li><b>不允许配过去的日期</b>：那种日期永远不会命中，只会让列表越堆越脏。已到期的行留在列表里
 *       （标「已过期」），前台不返回。</li>
 * </ul>
 */
@Service
public class MemberDayService {

    private static final byte ENABLED = 1;
    private static final BigDecimal MIN_MULTIPLIER = new BigDecimal("1.0");
    private static final BigDecimal MAX_MULTIPLIER = new BigDecimal("10.0");
    private static final BigDecimal NO_BONUS = BigDecimal.ONE;

    /** slogan 里最多列出几个日期，超过就用「等 N 天」收尾 */
    private static final int SLOGAN_MAX_DATES = 3;

    private final MemberDayRepository memberDayRepository;

    public MemberDayService(MemberDayRepository memberDayRepository) {
        this.memberDayRepository = memberDayRepository;
    }

    // ---------------- 判定（积分发放用） ----------------

    /** 该日期对应的积分倍率；不是会员日（或该会员日被停用）时返回 1（不加倍）。 */
    @Transactional(readOnly = true)
    public BigDecimal multiplierFor(LocalDate date) {
        if (date == null) {
            return NO_BONUS;
        }
        return memberDayRepository.findByMemberDate(date)
                .filter(row -> Byte.valueOf(ENABLED).equals(row.getEnabled()))
                .map(MemberDay::getMultiplier)
                .filter(m -> m != null && m.compareTo(NO_BONUS) > 0)
                .orElse(NO_BONUS);
    }

    @Transactional(readOnly = true)
    public boolean isMemberDay(LocalDate date) {
        return multiplierFor(date).compareTo(NO_BONUS) > 0;
    }

    // ---------------- 查询 ----------------

    /** 后台列表：全部日期，含已过期（前台不展示的也要能看到，否则管理员不知道有历史数据） */
    @Transactional(readOnly = true)
    public List<MemberDayResponse> listAll() {
        return memberDayRepository.findAllByOrderByMemberDateAsc().stream()
                .map(MemberDayResponse::from)
                .toList();
    }

    /**
     * 前台展示用：启用中且**未过期**的会员日 + 下一次的日期 + 一句话描述
     * （会员中心直接用 `slogan`，免得前端拼文案）。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> publicView() {
        LocalDate today = LocalDate.now();
        List<MemberDay> active = memberDayRepository
                .findByEnabledAndMemberDateGreaterThanEqualOrderByMemberDateAsc(ENABLED, today);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("enabled", !active.isEmpty());
        view.put("days", active.stream().map(MemberDayResponse::from).toList());
        view.put("multiplier", active.stream().map(MemberDay::getMultiplier)
                .filter(m -> m != null)
                .max(BigDecimal::compareTo)
                .orElse(NO_BONUS));
        view.put("slogan", slogan(active));
        view.put("nextDate", active.isEmpty() ? null : active.get(0).getMemberDate());
        return view;
    }

    // ---------------- 后台维护 ----------------

    @Transactional
    public MemberDayResponse create(MemberDayRequest request) {
        LocalDate date = request.getMemberDate();
        requireNotPast(date);
        memberDayRepository.findByMemberDate(date).ifPresent(existing -> {
            throw new BusinessException(400, dateLabel(date) + " 已经配置过了");
        });
        MemberDay entity = new MemberDay();
        entity.setMemberDate(date);
        entity.setMultiplier(normalizeMultiplier(request.getMultiplier()));
        entity.setRemark(normalizeRemark(request.getRemark()));
        entity.setEnabled((byte) (Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1));
        return MemberDayResponse.from(memberDayRepository.save(entity));
    }

    @Transactional
    public MemberDayResponse update(Long id, MemberDayRequest request) {
        MemberDay entity = memberDayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("会员日不存在"));
        LocalDate date = request.getMemberDate();
        requireNotPast(date);
        memberDayRepository.findByMemberDate(date)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BusinessException(400, dateLabel(date) + " 已经配置过了");
                });
        entity.setMemberDate(date);
        entity.setMultiplier(normalizeMultiplier(request.getMultiplier()));
        entity.setRemark(normalizeRemark(request.getRemark()));
        if (request.getEnabled() != null) {
            entity.setEnabled((byte) (request.getEnabled() ? 1 : 0));
        }
        return MemberDayResponse.from(memberDayRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        MemberDay entity = memberDayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("会员日不存在"));
        memberDayRepository.delete(entity);
    }

    private void requireNotPast(LocalDate date) {
        if (date != null && date.isBefore(LocalDate.now())) {
            throw new BusinessException(400, "会员日不能设在今天之前（" + dateLabel(date) + " 已过去）");
        }
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

    // ---------------- 文案 ----------------

    /** 「会员日（10月1日、11月11日）消费可得双倍积分」；没有可用日期时返回空串。 */
    private String slogan(List<MemberDay> active) {
        if (active.isEmpty()) {
            return "";
        }
        String dates = active.stream()
                .limit(SLOGAN_MAX_DATES)
                .map(d -> dateLabel(d.getMemberDate()))
                .collect(Collectors.joining("、"));
        if (active.size() > SLOGAN_MAX_DATES) {
            dates = dates + " 等 " + active.size() + " 天";
        }
        boolean sameMultiplier = active.stream().map(MemberDay::getMultiplier).distinct().count() == 1;
        String factor = sameMultiplier
                ? multiplierLabel(active.get(0).getMultiplier()) + "积分"
                : "指定日期积分翻倍";
        return "会员日（" + dates + "）消费可得" + factor;
    }

    private String dateLabel(LocalDate date) {
        return date == null ? "" : date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }

    private String multiplierLabel(BigDecimal multiplier) {
        if (multiplier == null || multiplier.compareTo(new BigDecimal("2.0")) == 0) {
            return "双倍";
        }
        return multiplier.stripTrailingZeros().toPlainString() + " 倍";
    }

}
