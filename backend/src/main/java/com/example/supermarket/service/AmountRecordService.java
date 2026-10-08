package com.example.supermarket.service;

import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.AmountRecordResponse;
import com.example.supermarket.dto.AmountSummaryResponse;
import com.example.supermarket.entity.AmountRecord;
import com.example.supermarket.entity.OrderEntity;
import com.example.supermarket.repository.AmountRecordRepository;
import com.example.supermarket.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 金额流水：把一单的金额构成拆成多条可读的记录（2026-10-07 用户第 7 条）。
 *
 * <p><b>为什么单独建表而不是复用 wallet_transaction</b>：
 * wallet_transaction 记的是**钱包余额变动**，只有充值/支付/退款会写。
 * 而用户想看的是「这单我实付多少、优惠抵了多少」—— 优惠抵扣不产生余额变动，
 * 在 wallet_transaction 里查不到，久了用户完全对不上账。
 *
 * <p><b>幂等</b>：写之前用 existsByOrderIdAndType 查重。下单/支付可能被重复调用
 * （连点、网络重试），不查重会出现两条一模一样的流水，金额加起来对不上。
 */
@Service
public class AmountRecordService {

    public static final String TYPE_ORDER_PAY = "ORDER_PAY";
    public static final String TYPE_ORDER_REFUND = "ORDER_REFUND";
    public static final String TYPE_COUPON_DISCOUNT = "COUPON_DISCOUNT";
    public static final String TYPE_ACTIVITY_DISCOUNT = "ACTIVITY_DISCOUNT";
    public static final String TYPE_MEMBER_DISCOUNT = "MEMBER_DISCOUNT";
    public static final String TYPE_POINTS_DISCOUNT = "POINTS_DISCOUNT";
    public static final String TYPE_FREIGHT = "FREIGHT";
    public static final String TYPE_POINTS_EARN = "POINTS_EARN";

    /** 支出（付出去） */
    public static final int DIR_OUT = 1;
    /** 收入（退回来 / 省下来） */
    public static final int DIR_IN = -1;

    private final AmountRecordRepository repository;
    private final OrderRepository orderRepository;

    public AmountRecordService(AmountRecordRepository repository, OrderRepository orderRepository) {
        this.repository = repository;
        this.orderRepository = orderRepository;
    }

    /**
     * 为一笔已支付订单补齐全部金额流水。
     *
     * <p>幂等：逐条 existsByOrderIdAndType 查重，重复调用不会写重。
     */
    @Transactional
    public void recordOrderAmounts(OrderEntity order) {
        if (order == null || order.getId() == null) {
            return;
        }
        List<AmountRecord> pending = new ArrayList<>();

        // 优惠类先记（收入方向 = 省下来的钱）
        addIfPositive(pending, order, TYPE_COUPON_DISCOUNT, "优惠券抵扣",
                order.getCouponName(), order.getDiscountAmount(), DIR_IN);
        addIfPositive(pending, order, TYPE_ACTIVITY_DISCOUNT, "活动优惠",
                order.getActivityName(), order.getActivityDiscount(), DIR_IN);
        addIfPositive(pending, order, TYPE_MEMBER_DISCOUNT, "会员优惠",
                "会员等级折扣（已含在商品单价里）", order.getMemberDiscount(), DIR_IN);
        addIfPositive(pending, order, TYPE_POINTS_DISCOUNT, "积分抵扣",
                num(order.getPointsUsed()).longValue() + " 分", order.getPointsDiscount(), DIR_IN);

        // 支出类
        addIfPositive(pending, order, TYPE_FREIGHT, "运费", null, order.getFreightAmount(), DIR_OUT);
        if (order.getPayAmount() != null && order.getPayAmount().signum() > 0) {
            pending.add(build(order, TYPE_ORDER_PAY, DIR_OUT, order.getPayAmount(), "订单支付", null));
        }

        for (AmountRecord r : pending) {
            if (!repository.existsByOrderIdAndType(r.getOrderId(), r.getType())) {
                repository.save(r);
            }
        }
    }

    /** 退款到账 */
    @Transactional
    public void recordRefund(Long userId, Long orderId, String orderNo, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        AmountRecord r = new AmountRecord();
        r.setUserId(userId);
        r.setOrderId(orderId);
        r.setType(TYPE_ORDER_REFUND);
        r.setDirection(DIR_IN);
        r.setAmount(amount);
        r.setTitle("退款到账");
        r.setRemark(orderNo);
        repository.save(r);
    }

    /** 本单获得的积分（amount 记折算金额，便于「积分折现价值」统计）
     *
     * <p>points 用 Long 而非 int：OrderEntity.pointsEarned 是 Long，
     * 形参跟着实体走，调用方不必到处强转。 */
    @Transactional
    public void recordPointsEarned(Long userId, Long orderId, String orderNo,
                                   Long points, BigDecimal value) {
        if (points == null || points <= 0) {
            return;
        }
        AmountRecord r = new AmountRecord();
        r.setUserId(userId);
        r.setOrderId(orderId);
        r.setType(TYPE_POINTS_EARN);
        r.setDirection(DIR_IN);
        r.setAmount(value == null ? BigDecimal.ZERO : value);
        r.setTitle("获得积分");
        r.setRemark(points + " 分" + (orderNo == null ? "" : " · 订单 " + orderNo));
        repository.save(r);
    }

        /**
     * 分页查某用户的金额流水（供 controller 调用）。
     *
     * <p>带上订单号：流水只有 orderId，用户看到一串数字对不上是哪单，
     * 必须 join 出 order_no 才有「这是哪笔」的实感。
     */
    @Transactional(readOnly = true)
    public PageResponse<AmountRecordResponse> list(Long userId, int page, int size, String group) {
        List<String> types = typesOfGroup(group);
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size);
        List<AmountRecord> rows = types.isEmpty()
                ? repository.findPageByUserId(userId, pageable)
                : repository.findPageByUserIdAndTypes(userId, types, pageable);
        long total = types.isEmpty()
                ? repository.countByUserId(userId)
                : repository.countByUserIdAndTypes(userId, types);
        Map<Long, String> orderNos = orderNosOf(rows);
        List<AmountRecordResponse> items = rows.stream()
                .map(r -> AmountRecordResponse.from(r, orderNos.get(r.getOrderId())))
                .toList();
        return PageResponse.of(items, page, size, total);
    }

    /**
     * 前端筛选 tab → 后端类型集合。
     *
     * <p>映射放在服务端而不是前端传一串 type：前端传类型列表等于把枚举泄漏到 URL，
     * 后端还得逐个校验合法性；传分组名则只需一次白名单 switch。
     *
     * <p><b>未知分组按「全部」处理而不是报错</b>：tab 是导航性质，
     * 传错值给用户一个空列表比 500 更糟，也不该因为一个筛选参数让整页打不开。
     */
    private static List<String> typesOfGroup(String group) {
        if (group == null || group.isBlank() || "ALL".equalsIgnoreCase(group)) {
            return List.of();
        }
        return switch (group.toUpperCase()) {
            case "PAY" -> List.of(TYPE_ORDER_PAY, TYPE_FREIGHT);
            case "DISCOUNT" -> List.of(TYPE_COUPON_DISCOUNT, TYPE_ACTIVITY_DISCOUNT,
                    TYPE_MEMBER_DISCOUNT, TYPE_POINTS_DISCOUNT);
            case "REFUND" -> List.of(TYPE_ORDER_REFUND);
            case "POINTS" -> List.of(TYPE_POINTS_EARN);
            default -> List.of();
        };
    }

    /**
     * 三项汇总（累计支出 / 累计退回 / 优惠省下）。
     *
     * <p><b>刻意独立于 list 且不接筛选参数</b>：用户看的是「总账」而不是当前 tab 的小计。
     * 如果汇总跟着 tab 变，切到「退款」标签时「累计支出」会显示成退款额，
     * 读起来像账目错乱。
     *
     * <p>积分（POINTS_EARN）不计入任何金额汇总 —— 它只是折算价值，不是真金白银。
     */
    @Transactional(readOnly = true)
    public AmountSummaryResponse summarize(Long userId) {
        List<AmountRecord> all = repository.findPageByUserId(userId, Pageable.unpaged());
        // 金额一律 BigDecimal 累加：用 double 先加再转会引入浮点误差，
        // 对账页面上差一分钱都会被认为算错了。
        BigDecimal out = BigDecimal.ZERO;
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal saved = BigDecimal.ZERO;
        for (AmountRecord r : all) {
            if (TYPE_POINTS_EARN.equals(r.getType())) {
                continue;
            }
            BigDecimal v = r.getAmount() == null ? BigDecimal.ZERO : r.getAmount();
            if (Integer.valueOf(DIR_OUT).equals(r.getDirection())) {
                out = out.add(v);
            } else if (TYPE_ORDER_REFUND.equals(r.getType())) {
                income = income.add(v);
            } else {
                saved = saved.add(v);
            }
        }
        return new AmountSummaryResponse(out, income, saved);
    }

    /** 某订单的流水；**必须校验订单归属**，否则可枚举他人订单 */
    @Transactional(readOnly = true)
    public List<AmountRecordResponse> listByOrder(Long userId, Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权查看该订单");
        }
        List<AmountRecord> rows = repository.findByOrderIdOrderByIdAsc(orderId);
        return rows.stream().map(r -> AmountRecordResponse.from(r, order.getOrderNo())).toList();
    }

    private Map<Long, String> orderNosOf(List<AmountRecord> rows) {
        Set<Long> ids = rows.stream()
                .map(AmountRecord::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (OrderEntity o : orderRepository.findAllById(ids)) {
            map.put(o.getId(), o.getOrderNo());
        }
        return map;
    }

    private void addIfPositive(List<AmountRecord> pending, OrderEntity order, String type,
                               String title, String remark, BigDecimal amount, int direction) {
        if (amount != null && amount.signum() > 0) {
            pending.add(build(order, type, direction, amount, title, remark));
        }
    }

    private AmountRecord build(OrderEntity order, String type, int direction,
                               BigDecimal amount, String title, String remark) {
        AmountRecord r = new AmountRecord();
        r.setUserId(order.getUserId());
        r.setOrderId(order.getId());
        r.setType(type);
        r.setDirection(direction);
        r.setAmount(amount);
        r.setTitle(title);
        r.setRemark(remark);
        return r;
    }

    private static BigDecimal num(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(v));
    }
}
