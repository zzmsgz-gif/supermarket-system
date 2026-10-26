package com.example.supermarket.service;

import com.example.supermarket.entity.PointLedger;
import com.example.supermarket.entity.SysUser;
import com.example.supermarket.entity.UserMessage;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.PointLedgerRepository;
import com.example.supermarket.repository.SysUserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
    /** 100 积分 = 1 元（方案D-B 现金扣回换算也用它，故 public static） */
    public static final BigDecimal POINTS_PER_YUAN = BigDecimal.valueOf(100);
    /** 积分抵扣最多占（优惠后）应付金额的比例 */
    private static final BigDecimal MAX_REDEEM_RATIO = new BigDecimal("0.5");

    /** 等级阈值（累计消费，元）/ 折扣率 / 名称，数组下标即等级。
     *  7 档：在「钻石 0.90」与「黑卡 0.85」之间插入「紫钻 0.88」（用户要求新增一档 88 折）。 */
    private static final BigDecimal[] TIER_THRESHOLDS = {
        BigDecimal.ZERO, new BigDecimal("1000"), new BigDecimal("5000"), new BigDecimal("20000"),
        new BigDecimal("30000"), new BigDecimal("50000"), new BigDecimal("100000")
    };
    private static final BigDecimal[] TIER_RATES = {
        BigDecimal.ONE, new BigDecimal("0.98"), new BigDecimal("0.95"), new BigDecimal("0.90"),
        new BigDecimal("0.88"), new BigDecimal("0.85"), new BigDecimal("0.80")
    };
    private static final String[] TIER_NAMES = {"普通用户", "银卡会员", "金卡会员", "钻石会员", "紫钻会员", "黑卡会员", "至尊会员"};
    /** 享受商品「会员价」的最低等级：level0=普通用户不享会员价（否则原价失去意义） */
    private static final int MEMBER_PRICE_MIN_LEVEL = 1;

    private final SysUserRepository userRepository;
    private final PointLedgerRepository ledgerRepository;
    private final MessageService messageService;
    private final MemberDayService memberDayService;

    public MemberService(SysUserRepository userRepository, PointLedgerRepository ledgerRepository,
                         MessageService messageService, MemberDayService memberDayService) {
        this.userRepository = userRepository;
        this.ledgerRepository = ledgerRepository;
        this.messageService = messageService;
        this.memberDayService = memberDayService;
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

    /** 是否有资格享受商品「会员价」：level0=普通用户不行，避免原价失去意义 */
    public static boolean canUseMemberPrice(Integer level) {
        return level != null && level >= MEMBER_PRICE_MIN_LEVEL;
    }

    /**
     * 单品成交单价（逐商品取优，不叠加）：
     * 基准价（规格价/售价）→ 与「会员价」（够格时）取更低 → 再与「等级折扣价」取更低。
     * 会员价与等级折扣互斥取优，避免同一件商品吃两道会员优惠（原实现会 ¥118 再打 98 折）。
     *
     * <p>刻意做成 static：购物车预览 {@code CartItemResponse.from} 是静态组装、拿不到 Spring bean，
     * 下单与预览必须调同一个方法，否则又会出现「预览价 ≠ 实付价」。
     */
    public static BigDecimal unitPriceFor(Integer level, BigDecimal basePrice, BigDecimal memberPrice) {
        if (basePrice == null) {
            return null;
        }
        BigDecimal best = basePrice;
        if (canUseMemberPrice(level) && memberPrice != null && memberPrice.compareTo(best) < 0) {
            best = memberPrice;
        }
        int idx = level == null ? 0 : Math.max(0, Math.min(level, TIER_RATES.length - 1));
        BigDecimal levelPrice = basePrice.multiply(TIER_RATES[idx]).setScale(2, RoundingMode.HALF_UP);
        if (levelPrice.compareTo(best) < 0) {
            best = levelPrice;
        }
        return best;
    }

    /**
     * 会员让利**来源**判定（与 {@link #unitPriceFor} 同一优先级，必须逐字对齐）：
     * <ul>
     *   <li>{@code "member"} —— 商品会员价被选中（够格、比基准低、且不高于等级折扣价）</li>
     *   <li>{@code "tier"}  —— 等级折扣价被选中（比基准与会员价都低；平手时会员价优先，同 unitPriceFor）</li>
     *   <li>{@code null}    —— 无会员让利（成交价=基准价）</li>
     * </ul>
     * 下单时把结果快照进 {@code order_item.member_source}，订单详情页据此把
     * 「会员优惠」（会员价）与「会员折扣」（等级折扣）拆成两行分开列——
     * 两者是性质不同的钱，冠同一个名字就是张冠李戴（购物车 memberSource 同口径，小写）。
     */
    public static String sourceOf(Integer level, BigDecimal basePrice, BigDecimal memberPrice) {
        if (basePrice == null) {
            return null;
        }
        BigDecimal best = unitPriceFor(level, basePrice, memberPrice);
        if (best == null || best.compareTo(basePrice) >= 0) {
            return null;
        }
        int idx = level == null ? 0 : Math.max(0, Math.min(level, TIER_RATES.length - 1));
        BigDecimal levelPrice = basePrice.multiply(TIER_RATES[idx]).setScale(2, RoundingMode.HALF_UP);
        boolean memberPriceWon = canUseMemberPrice(level) && memberPrice != null
                && memberPrice.compareTo(basePrice) < 0
                && memberPrice.compareTo(best) == 0
                && memberPrice.compareTo(levelPrice) <= 0;
        if (memberPriceWon) {
            return "member";
        }
        if (levelPrice.compareTo(best) == 0) {
            return "tier";
        }
        return null;
    }

    /** 实付金额可获积分（每满 1 元 1 分，向下取整）；按「今天」判定会员日。 */
    public long earnPoints(BigDecimal paidAmount) {
        return earnPoints(paidAmount, LocalDate.now());
    }

    /**
     * 实付金额可获积分：每满 1 元 1 分（向下取整），**会员日按倍率加倍**（见 {@code member_day} 表）。
     *
     * @param consumeDate 判定会员日用的「消费日」—— 项目里用**下单日**（订单创建日），
     *                    而不是支付日：订单上存的 {@code pointsEarned} 也是下单当天算的，
     *                    两边同口径才不会出现「订单显示 20 分、实际发了 10 分」。
     */
    public long earnPoints(BigDecimal paidAmount, LocalDate consumeDate) {
        if (paidAmount == null) {
            return 0L;
        }
        long base = paidAmount.setScale(0, RoundingMode.FLOOR).longValue();
        BigDecimal multiplier = memberDayService.multiplierFor(consumeDate);
        if (multiplier.compareTo(BigDecimal.ONE) <= 0) {
            return base;
        }
        return BigDecimal.valueOf(base).multiply(multiplier)
                .setScale(0, RoundingMode.FLOOR).longValue();
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
        // 2026-10-24 口径调整：冻结分不再锁抵扣。可用积分 = 当前余额。
        // 冻结池仅作退款回收对账用：售后通过时 recoverForRefund 先收冻结池，
        // 积分已被花掉的部分自动从退款现金中等价扣回（1积分=1/100元），系统不亏。
        // 此前「可用 = 余额 − 冻结」会把冻结后仍有余款的用户可用分清零、抵扣被禁。
        long available = Math.max(0L, user.getPoints());
        long usable = Math.min(Math.min(want, moneyCapPoints), available);
        if (usable <= 0) {
            return 0L;
        }
        // 抵扣只消耗「可用」部分，冻结池不动（冻结的积分仍被保留，用于退款回收对账）
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

    /**
     * 退款逆向（点3「全逆向」）：按**非秒杀退款占比** {@code ratio} 同时做三件事 ——
     * <ol>
     *   <li>返还下单时抵扣的积分（TYPE_REDEEM → TYPE_REFUND），按占比算；</li>
     *   <li>回收支付后发放的积分（TYPE_EARN → TYPE_EARN_REVERSE），按占比算；</li>
     *   <li>回退累计消费与会员等级（沉默重算，不弹降级通知），按占比算。</li>
     * </ol>
     *
     * <p><b>为什么按「非秒杀占比」</b>：秒杀行是纯折扣通道、与券/积分/活动互斥（口径 B），
     * 不参与积分发放与抵扣，退款时自然也不能动那部分。ratio = 本次退的非秒杀小计 / 整单非秒杀小计。
     * 纯秒杀退款（ratio = 0）直接跳过，券/积分都不参与。
     *
     * <p><b>幂等</b>：以 {@code TYPE_REFUND} 或 {@code TYPE_EARN_REVERSE} 是否已存在同一订单为防重闸门，
     * 与 cancelOrder 里的 refundRedeem（整单退、全额返还）互不冲突（退货单不会先走取消路径）。
     *
     * @param payAmount 订单实付（含运费），用于按比例回退累计消费；可空（空则不回退累计消费）
     * @param ratio     非秒杀退款占比，[0,1]；<=0 视为无需逆向
     */
    /**
     * 方案D-C：申请退款即冻结该订单按 ratio 应收回的发放积分。
     * 冻结上限 = 当前可用积分（已花掉的部分无法冻结，退款时由 recoverForRefund 的现金扣回兜底），
     * 故冻结绝不会超过「未花掉的发放积分」，也不会让积分变负。
     *
     * @return 实际冻结的积分额（写入 return_request.frozen_points，供驳回释放 / 退款回收对账）
     */
    @Transactional
    public long freezeForReturn(Long userId, Long orderId, BigDecimal ratio) {
        if (ratio == null || ratio.compareTo(ZERO) <= 0) {
            return 0L;
        }
        SysUser user = lockUser(userId);
        long rec = ledgerRepository.findByRefOrderIdAndType(orderId, PointLedger.TYPE_EARN)
                .map(e -> new BigDecimal(e.getAmount()).multiply(ratio)
                        .setScale(0, RoundingMode.FLOOR).longValue())
                .orElse(0L);
        if (rec <= 0) {
            return 0L;
        }
        long frozen = user.getFrozenPoints() == null ? 0L : user.getFrozenPoints();
        long available = Math.max(0L, user.getPoints() - frozen);   // 只能冻结「未花掉」的发放积分
        long freeze = Math.min(rec, available);
        if (freeze > 0) {
            user.setFrozenPoints(frozen + freeze);
            userRepository.save(user);
        }
        return freeze;
    }

    /** 方案D-C：驳回/取消退款时释放本单冻结的积分（至多释放本单冻结额，绝不让冻结变负）。 */
    @Transactional
    public void releaseFreeze(Long userId, Long amount) {
        if (amount == null || amount <= 0) {
            return;
        }
        SysUser user = lockUser(userId);
        long frozen = user.getFrozenPoints() == null ? 0L : user.getFrozenPoints();
        long release = Math.min(amount, frozen);
        user.setFrozenPoints(frozen - release);
        userRepository.save(user);
    }

    /**
     * 退款逆向（方案D「全逆向 + 冻结回收」）：按**非秒杀占比** {@code ratio} 同时做三件事 ——
     * <ol>
     *   <li>返还下单时抵扣的积分（TYPE_REDEEM → TYPE_REFUND），按占比算；</li>
     *   <li>回收支付后发放的积分（TYPE_EARN → TYPE_EARN_REVERSE）：<b>优先从冻结池回收</b>，
     *       冻结不足（用户申请退款前已花掉）的部分计为 {@code shortfall}，由调用方从现金退款等价扣回（方案D-B）；</li>
     *   <li>回退累计消费与会员等级（沉默重算）。</li>
     * </ol>
     *
     * <p><b>与旧 reversePointsOnRefund 的差别</b>：旧实现只把「回收额」用 Math.min(rec, 当前余额) 钳到 0，
     * 相当于对「先花掉再退单」睁一只眼（套利口子）。本方法把无法从冻结池回收的发放积分精确量化为
     * {@code shortfall} 返回，让退款链路把这部分等价现金扣回，商家零风险。
     *
     * <p><b>幂等</b>：以 {@code TYPE_REFUND} 是否已存在同一订单为防重闸门（与旧实现一致）。
     *
     * @param payAmount   订单实付（含运费），用于按比例回退累计消费；可空
     * @param ratio       非秒杀退款占比，[0,1]；<=0 视为无需逆向
     * @param frozenReturn 本单在 apply 时冻结的积分额（return_request.frozen_points）
     * @return 现金扣回所需的积分缺口（已花掉、无法回收的发放积分，单位：分），0 表示无需扣回
     */
    @Transactional
    public long recoverForRefund(Long userId, Long orderId, BigDecimal payAmount, BigDecimal ratio, Long frozenReturn) {
        if (ratio == null || ratio.compareTo(ZERO) <= 0) {
            return 0L;
        }
        // ⚠️ 不做「订单级」防重：退款项是按商品行并行的（同一订单可有多笔独立售后），
        // 每笔售后只对本行占比做一次逆向，流水可叠加。调用方（ReturnRequestService.refundForReturn）
        // 保证每个售后单只会走到这里一次（审核/确认收货都有终态守卫），无需在此二次判重。
        SysUser user = lockUser(userId);
        long fRet = frozenReturn == null ? 0L : frozenReturn;
        long shortfall = 0L;

        // 1) 抵扣积分按占比返还（与冻结无关，原样返还用户）
        ledgerRepository.findByRefOrderIdAndType(orderId, PointLedger.TYPE_REDEEM).ifPresent(redeem -> {
            long back = new BigDecimal(redeem.getAmount()).multiply(ratio)
                    .setScale(0, RoundingMode.FLOOR).longValue();
            if (back > 0) {
                user.setPoints(user.getPoints() + back);
                saveLedger(user.getId(), PointLedger.TYPE_REFUND, back, user.getPoints(), orderId, "退款返还抵扣积分");
            }
        });

        // 2) 发放积分按占比回收：优先从冻结池回收，冻结不足（已花掉）的部分计 shortfall
        PointLedger earn = ledgerRepository.findByRefOrderIdAndType(orderId, PointLedger.TYPE_EARN).orElse(null);
        if (earn != null) {
            long rec = new BigDecimal(earn.getAmount()).multiply(ratio)
                    .setScale(0, RoundingMode.FLOOR).longValue();
            if (rec > 0) {
                long curFrozen = user.getFrozenPoints() == null ? 0L : user.getFrozenPoints();
                long fromFrozen = Math.min(rec, fRet);                 // 从冻结池能收回的（申请后才花，被 C 挡下）
                long newFrozen = curFrozen - fromFrozen;               // 释放本单冻结（>=0）
                user.setFrozenPoints(newFrozen);
                long remaining = rec - fromFrozen;                     // 还需从余额收的
                long actual = 0L;
                if (remaining > 0) {
                    // 余额里「未冻结」部分 = 总积分 − 本单冻结池(fromFrozen)。
                    // ⚠️ 不可用 points − newFrozen：points 仍包含 fromFrozen 这部分，
                    // 否则 actual 会与 fromFrozen 叠加超过真实余额 → 积分变负（严重 bug）。
                    long nonFrozen = Math.max(0L, user.getPoints() - fromFrozen);
                    actual = Math.min(remaining, nonFrozen);           // 余额里还没花的才收得回
                }
                long removed = fromFrozen + actual;                    // 本次实际从积分余额收回的总数
                if (removed > 0) {
                    long newPoints = user.getPoints() - removed;
                    user.setPoints(newPoints);
                    saveLedger(user.getId(), PointLedger.TYPE_EARN_REVERSE, removed, newPoints, orderId, "退款回收发放积分");
                }
                shortfall = remaining - actual;   // 已花掉、收不回的发放积分 → 交调用方现金扣回（方案D-B）
            }
        }

        // 3) 累计消费与会员等级按占比回退（沉默重算：不发降级通知，避免打扰；等级本就是累计消费的函数）
        if (payAmount != null && payAmount.compareTo(ZERO) > 0) {
            BigDecimal spentBack = payAmount.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            BigDecimal spent = user.getTotalSpent() == null ? ZERO : user.getTotalSpent();
            spent = spent.subtract(spentBack).max(ZERO);
            user.setTotalSpent(spent);
            user.setMemberLevel(levelForSpent(spent));
        }
        userRepository.save(user);
        return shortfall;
    }

    /** 支付成功后发放积分 + 累计消费升级（幂等：同一订单仅发放一次） */
    @Transactional
    /** @param payAmount 必须是**含运费的实付**（order.payAmount）。
     *  下单时（OrderService）用同一个口径算并存进 orders.points_earned，
     *  两处若一个含运费一个不含，订单详情与积分明细就会显示不同的积分。 */
    public void awardOnPaidOrder(Long userId, Long orderId, BigDecimal payAmount, LocalDate consumeDate) {
        if (ledgerRepository.existsByRefOrderIdAndType(orderId, PointLedger.TYPE_EARN)) {
            return;
        }
        long earn = earnPoints(payAmount, consumeDate);
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
            // 会员日当天多给的那部分在备注里点明，用户对账时看得懂为什么多
            BigDecimal multiplier = memberDayService.multiplierFor(consumeDate);
            String remark = multiplier.compareTo(BigDecimal.ONE) > 0
                    ? "消费获得积分（会员日 " + multiplier.stripTrailingZeros().toPlainString() + " 倍）"
                    : "消费获得积分";
            saveLedger(user.getId(), PointLedger.TYPE_EARN, earn, user.getPoints(), orderId, remark);
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
        long points = user.getPoints() == null ? 0L : user.getPoints();
        long frozen = user.getFrozenPoints() == null ? 0L : user.getFrozenPoints();
        // 2026-10-24 口径调整：可用积分 = 当前余额（冻结不再锁抵扣）。
        // 冻结分如实透出仅作提示：关联订单售后通过时按规则扣回，
        // 积分不足部分从退款现金中等价扣回（见 ReturnRequestService.recoverForRefund）。
        result.put("points", points);
        result.put("frozenPoints", frozen);
        result.put("availablePoints", points);
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
