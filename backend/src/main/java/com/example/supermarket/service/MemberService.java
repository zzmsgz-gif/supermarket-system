package com.example.supermarket.service;

import com.example.supermarket.entity.PointLedger;
import com.example.supermarket.entity.SysUser;
import com.example.supermarket.entity.UserMessage;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.PointLedgerRepository;
import com.example.supermarket.repository.SysUserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private static final byte NOT_DELETED = 0;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final int MAX_PAGE_SIZE = 100;
    /** 100 积分 = 1 元 */
    private static final BigDecimal POINTS_PER_YUAN = BigDecimal.valueOf(100);
    /** 积分抵扣最多占（优惠后）应付金额的比例 */
    private static final BigDecimal MAX_REDEEM_RATIO = new BigDecimal("0.5");

    /** 等级阈值（累计消费，元）/ 折扣率 / 名称，数组下标即等级 */
    private static final BigDecimal[] TIER_THRESHOLDS = {
        BigDecimal.ZERO, new BigDecimal("1000"), new BigDecimal("5000"), new BigDecimal("20000")
    };
    private static final BigDecimal[] TIER_RATES = {
        BigDecimal.ONE, new BigDecimal("0.98"), new BigDecimal("0.95"), new BigDecimal("0.90")
    };
    private static final String[] TIER_NAMES = {"普通会员", "银卡会员", "金卡会员", "钻石会员"};

    private final SysUserRepository userRepository;
    private final PointLedgerRepository ledgerRepository;
    private final MessageService messageService;

    public MemberService(SysUserRepository userRepository, PointLedgerRepository ledgerRepository,
                         MessageService messageService) {
        this.userRepository = userRepository;
        this.ledgerRepository = ledgerRepository;
        this.messageService = messageService;
    }

    public int levelForSpent(BigDecimal spent) {
        int level = 0;
        for (int i = 0; i < TIER_THRESHOLDS.length; i++) {
            if (spent.compareTo(TIER_THRESHOLDS[i]) >= 0) {
                level = i;
            }
        }
        return level;
    }

    public BigDecimal discountRateForLevel(int level) {
        return TIER_RATES[clampLevel(level)];
    }

    public String tierName(int level) {
        return TIER_NAMES[clampLevel(level)];
    }

    /** 等级折扣金额（在优惠后金额基础上再减的部分） */
    public BigDecimal memberDiscount(int level, BigDecimal base) {
        if (base == null || base.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        BigDecimal rate = discountRateForLevel(level);
        if (rate.compareTo(BigDecimal.ONE) >= 0) {
            return ZERO;
        }
        return base.multiply(BigDecimal.ONE.subtract(rate)).setScale(2, RoundingMode.HALF_UP).max(ZERO);
    }

    /** 实付金额可获积分（每满 1 元 1 分，向下取整） */
    public long earnPoints(BigDecimal paidAmount) {
        if (paidAmount == null) {
            return 0L;
        }
        return paidAmount.setScale(0, RoundingMode.FLOOR).longValue();
    }

    /** 下单时预留积分抵扣：扣减用户积分并记流水，返回实际抵扣积分（已按用户积分与金额上限封顶）。 */
    @Transactional
    public long reserveRedeem(Long userId, Long orderId, Long requestedPoints, BigDecimal moneyCap) {
        if (ledgerRepository.existsByRefOrderIdAndType(orderId, PointLedger.TYPE_REDEEM)) {
            return ledgerRepository.findByRefOrderIdAndType(orderId, PointLedger.TYPE_REDEEM)
                    .map(PointLedger::getAmount).orElse(0L);
        }
        long want = requestedPoints == null ? Long.MAX_VALUE : Math.max(0, requestedPoints);
        long moneyCapPoints = moneyCap == null ? 0
                : moneyCap.multiply(POINTS_PER_YUAN).setScale(0, RoundingMode.FLOOR).longValue();
        SysUser user = lockUser(userId);
        long usable = Math.min(Math.min(want, moneyCapPoints), user.getPoints());
        if (usable <= 0) {
            return 0L;
        }
        user.setPoints(user.getPoints() - usable);
        userRepository.save(user);
        saveLedger(user.getId(), PointLedger.TYPE_REDEEM, usable, user.getPoints(), orderId, "下单积分抵扣");
        return usable;
    }

    /** 取消/退款时回退已抵扣积分 */
    @Transactional
    public void refundRedeem(Long userId, Long orderId) {
        ledgerRepository.findByRefOrderIdAndType(orderId, PointLedger.TYPE_REDEEM).ifPresent(ledger -> {
            if (ledgerRepository.existsByRefOrderIdAndType(orderId, PointLedger.TYPE_REFUND)) {
                return;
            }
            SysUser user = lockUser(userId);
            long back = ledger.getAmount();
            user.setPoints(user.getPoints() + back);
            userRepository.save(user);
            saveLedger(user.getId(), PointLedger.TYPE_REFUND, back, user.getPoints(), orderId, "取消订单回退积分");
        });
    }

    /** 支付成功后发放积分 + 累计消费升级（幂等：同一订单仅发放一次） */
    @Transactional
    public void awardOnPaidOrder(Long userId, Long orderId, BigDecimal payAmount) {
        if (ledgerRepository.existsByRefOrderIdAndType(orderId, PointLedger.TYPE_EARN)) {
            return;
        }
        long earn = earnPoints(payAmount);
        SysUser user = lockUser(userId);
        user.setPoints(user.getPoints() + earn);
        BigDecimal spent = user.getTotalSpent() == null ? ZERO : user.getTotalSpent();
        spent = spent.add(payAmount).setScale(2, RoundingMode.HALF_UP);
        user.setTotalSpent(spent);
        int previousLevel = user.getMemberLevel() == null ? 0 : user.getMemberLevel();
        int currentLevel = levelForSpent(spent);
        user.setMemberLevel(currentLevel);
        userRepository.save(user);
        if (earn > 0) {
            saveLedger(user.getId(), PointLedger.TYPE_EARN, earn, user.getPoints(), orderId, "消费获得积分");
        }
        if (currentLevel > previousLevel) {
            // 消息中心：会员升级（同一等级只提醒一次）
            messageService.push(userId, UserMessage.TYPE_MEMBER, "恭喜升级为" + tierName(currentLevel),
                    "累计消费 ¥" + spent + "，会员折扣已提升，之后下单自动享受更低价格。",
                    "points", null, "MEMBER:LEVEL:" + currentLevel + ":" + userId);
        }
    }

    @Transactional(readOnly = true)
    public Page<PointLedger> listLedger(Long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ledgerRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> profile(Long userId) {
        SysUser user = userRepository.findById(userId)
                .filter(item -> NOT_DELETED == item.getDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        TierInfo info = tierInfo(user.getMemberLevel());
        BigDecimal progress = progressToNext(user.getMemberLevel(), user.getTotalSpent());
        Map<String, Object> result = new HashMap<>();
        result.put("points", user.getPoints());
        result.put("memberLevel", user.getMemberLevel());
        result.put("levelName", info.name());
        result.put("totalSpent", user.getTotalSpent());
        result.put("discountRate", info.rate());
        result.put("nextLevelThreshold", info.nextThreshold());
        result.put("nextLevelName", info.nextName());
        result.put("progressToNext", progress);
        result.put("maxRedeemRatio", MAX_REDEEM_RATIO);
        return result;
    }

    public record TierInfo(int level, String name, BigDecimal threshold, BigDecimal rate,
                           BigDecimal nextThreshold, String nextName) {
    }

    public TierInfo tierInfo(int level) {
        int clamped = clampLevel(level);
        BigDecimal nextThreshold = clamped + 1 < TIER_THRESHOLDS.length ? TIER_THRESHOLDS[clamped + 1] : null;
        String nextName = clamped + 1 < TIER_NAMES.length ? TIER_NAMES[clamped + 1] : null;
        return new TierInfo(clamped, TIER_NAMES[clamped], TIER_THRESHOLDS[clamped],
                TIER_RATES[clamped], nextThreshold, nextName);
    }

    public List<TierInfo> allTiers() {
        List<TierInfo> list = new ArrayList<>();
        for (int i = 0; i < TIER_NAMES.length; i++) {
            BigDecimal nextThreshold = i + 1 < TIER_THRESHOLDS.length ? TIER_THRESHOLDS[i + 1] : null;
            String nextName = i + 1 < TIER_NAMES.length ? TIER_NAMES[i + 1] : null;
            list.add(new TierInfo(i, TIER_NAMES[i], TIER_THRESHOLDS[i], TIER_RATES[i], nextThreshold, nextName));
        }
        return list;
    }

    /** 距下一等级的进度（0~1），已是顶级返回 1 */
    public BigDecimal progressToNext(int level, BigDecimal totalSpent) {
        TierInfo info = tierInfo(level);
        if (info.nextThreshold() == null) {
            return BigDecimal.ONE;
        }
        BigDecimal done = totalSpent.subtract(info.threshold()).max(ZERO);
        BigDecimal span = info.nextThreshold().subtract(info.threshold());
        if (span.compareTo(ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return done.divide(span, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE);
    }

    /** 积分最多可抵扣的金额（元） */
    public BigDecimal maxRedeemValue(BigDecimal payableAfterPromo) {
        if (payableAfterPromo == null) {
            return ZERO;
        }
        return payableAfterPromo.multiply(MAX_REDEEM_RATIO).setScale(2, RoundingMode.HALF_UP);
    }

    private int clampLevel(int level) {
        if (level < 0) {
            return 0;
        }
        if (level >= TIER_THRESHOLDS.length) {
            return TIER_THRESHOLDS.length - 1;
        }
        return level;
    }

    private SysUser lockUser(Long userId) {
        return userRepository.findByIdAndDeletedForUpdate(userId, NOT_DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void saveLedger(Long userId, String type, long amount, long balanceAfter, Long refOrderId, String remark) {
        PointLedger ledger = new PointLedger();
        ledger.setUserId(userId);
        ledger.setType(type);
        ledger.setAmount(amount);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setRefOrderId(refOrderId);
        ledger.setRemark(remark);
        ledgerRepository.save(ledger);
    }
}
