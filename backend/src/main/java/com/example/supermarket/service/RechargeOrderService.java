package com.example.supermarket.service;

import com.example.supermarket.dto.RechargeOrderCreateRequest;
import com.example.supermarket.dto.RechargeOrderResponse;
import com.example.supermarket.entity.RechargeOrder;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.repository.RechargeOrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RechargeOrderService {

    /** 充值订单有效时长（分钟），超时未支付自动关闭 */
    private static final int ORDER_DURATION_MINUTES = 10;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000");

    private final RechargeOrderRepository repository;
    private final WalletService walletService;

    public RechargeOrderService(RechargeOrderRepository repository, WalletService walletService) {
        this.repository = repository;
        this.walletService = walletService;
    }

    @Transactional
    public RechargeOrderResponse createOrder(Long userId, RechargeOrderCreateRequest request) {
        BigDecimal amount = normalizeAmount(request.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "充值金额必须大于 0");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(400, "单笔充值金额不能超过 1,000,000");
        }
        String method = request.getMethod();
        if (!RechargeOrder.METHOD_ALIPAY.equals(method) && !RechargeOrder.METHOD_WECHAT.equals(method)) {
            throw new BusinessException(400, "不支持的支付方式");
        }
        RechargeOrder order = new RechargeOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setAmount(amount);
        order.setMethod(method);
        order.setStatus(RechargeOrder.STATUS_PENDING);
        order.setExpireAt(LocalDateTime.now().plusMinutes(ORDER_DURATION_MINUTES));
        order = repository.save(order);
        return RechargeOrderResponse.from(order);
    }

    @Transactional
    public RechargeOrderResponse pay(Long userId, Long orderId) {
        RechargeOrder order = getOwnedPending(orderId, userId);
        // 二次校验是否真超时（防止前端倒计时与服务端漂移）
        if (order.getExpireAt().isBefore(LocalDateTime.now())) {
            order.setStatus(RechargeOrder.STATUS_EXPIRED);
            repository.save(order);
            throw new BusinessException(409, "充值订单已超时，请重新创建");
        }
        order.setStatus(RechargeOrder.STATUS_PAID);
        order.setPaidAt(LocalDateTime.now());
        repository.save(order);
        walletService.creditRecharge(userId, order.getAmount());
        return RechargeOrderResponse.from(order);
    }

    @Transactional
    public RechargeOrderResponse cancel(Long userId, Long orderId) {
        RechargeOrder order = getOwnedPending(orderId, userId);
        order.setStatus(RechargeOrder.STATUS_CANCELLED);
        repository.save(order);
        return RechargeOrderResponse.from(order);
    }

    @Transactional
    public RechargeOrderResponse getOrder(Long userId, Long orderId) {
        RechargeOrder order = getOwned(orderId, userId);
        // 懒过期：读取时若已超时才落库为 EXPIRED
        if (RechargeOrder.STATUS_PENDING.equals(order.getStatus())
                && order.getExpireAt().isBefore(LocalDateTime.now())) {
            order.setStatus(RechargeOrder.STATUS_EXPIRED);
            order = repository.save(order);
        }
        return RechargeOrderResponse.from(order);
    }

    /** 调度任务：把超过截止时间仍未支付的订单置为 EXPIRED */
    @Transactional
    public void expireOverdueOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<RechargeOrder> overdue = repository.findByStatusAndExpireAtBefore(
                RechargeOrder.STATUS_PENDING, now);
        for (RechargeOrder order : overdue) {
            order.setStatus(RechargeOrder.STATUS_EXPIRED);
            repository.save(order);
        }
    }

    private RechargeOrder getOwned(Long orderId, Long userId) {
        return repository.findByUserIdAndId(userId, orderId).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "充值订单不存在"));
    }

    private RechargeOrder getOwnedPending(Long orderId, Long userId) {
        RechargeOrder order = getOwned(orderId, userId);
        if (!RechargeOrder.STATUS_PENDING.equals(order.getStatus())) {
            throw new BusinessException(409, "该订单已不可支付（当前状态：" + order.getStatus() + "）");
        }
        return order;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String generateOrderNo() {
        return "RC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
