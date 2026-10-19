package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.RechargeRequest;
import com.example.supermarket.dto.WalletResponse;
import com.example.supermarket.dto.WalletTransactionResponse;
import com.example.supermarket.entity.SysUser;
import com.example.supermarket.entity.PaymentRecord;
import com.example.supermarket.repository.OrderRepository;
import com.example.supermarket.repository.PaymentRecordRepository;
import com.example.supermarket.entity.WalletTransaction;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.SysUserRepository;
import com.example.supermarket.repository.WalletTransactionRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.example.supermarket.entity.OrderEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {

    private static final byte NOT_DELETED = 0;
    private static final String TYPE_RECHARGE = "RECHARGE";
    private static final String TYPE_PAYMENT = "PAYMENT";
    private static final String TYPE_REFUND = "REFUND";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int RECENT_LIMIT = 5;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final SysUserRepository userRepository;
    private final WalletTransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final PaymentRecordRepository paymentRecordRepository;

    public WalletService(SysUserRepository userRepository, WalletTransactionRepository transactionRepository,
            OrderRepository orderRepository, PaymentRecordRepository paymentRecordRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.orderRepository = orderRepository;
        this.paymentRecordRepository = paymentRecordRepository;
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(Long userId) {
        SysUser user = userRepository.findById(userId)
                .filter(item -> Byte.valueOf(NOT_DELETED).equals(item.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return new WalletResponse(normalizeAmount(user.getBalance()), recentTransactions(userId));
    }

    @Transactional
    public WalletResponse recharge(Long userId, RechargeRequest request) {
        BigDecimal amount = normalizeAmount(request.getAmount());
        if (amount.compareTo(ZERO) <= 0) {
            throw new BusinessException(400, "Recharge amount must be greater than 0");
        }
        credit(userId, null, amount, TYPE_RECHARGE, "User recharge");
        return getWallet(userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> listTransactions(Long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WalletTransaction> transactions = transactionRepository.findAll(byUserId(userId), pageable);
        Map<Long, String> orderNos = orderNosOf(transactions.getContent());
        Map<Long, String> channels = channelsOf(transactions.getContent());
        List<WalletTransactionResponse> items = transactions.getContent().stream()
                .map(t -> WalletTransactionResponse.from(t, orderNos.get(t.getOrderId()), channels.get(t.getOrderId())))
                .toList();
        return PageResponse.of(items, safePage, safeSize, transactions.getTotalElements());
    }

    /**
     * 批量取订单的支付渠道（WECHAT/ALIPAY/BALANCE）：与 orderNosOf 同款批量查，避免 N+1。
     * 充值/退款流水 orderId 为 null，拿不到渠道，由 channelLabelOf 按 type 推标签。
     */
    private Map<Long, String> channelsOf(List<WalletTransaction> rows) {
        Set<Long> ids = rows.stream()
                .map(WalletTransaction::getOrderId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            // ⚠️ 必须返回可变 HashMap 而不是 Map.of()：Map.of().get(null) 会抛 NPE，
            // 而充值流水 orderId=null，下面 channels.get(null)/orderNos.get(null) 是常态
            //（纯充值用户最近流水全是 RECHARGE）——2026-10-14 修复线上钱包 500。
            return new HashMap<>();
        }
        Map<Long, String> map = new HashMap<>();
        for (PaymentRecord pr : paymentRecordRepository.findByOrderIdIn(ids)) {
            map.put(pr.getOrderId(), pr.getChannel());
        }
        return map;
    }

    /** 批量取订单号：逐条查会 N+1，这里一次性 findAllById */
    private Map<Long, String> orderNosOf(List<WalletTransaction> rows) {
        Set<Long> ids = rows.stream()
                .map(WalletTransaction::getOrderId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            // ⚠️ 必须返回可变 HashMap 而不是 Map.of()：Map.of().get(null) 会抛 NPE，
            // 而充值流水 orderId=null，下面 channels.get(null)/orderNos.get(null) 是常态
            //（纯充值用户最近流水全是 RECHARGE）——2026-10-14 修复线上钱包 500。
            return new HashMap<>();
        }
        Map<Long, String> map = new HashMap<>();
        for (OrderEntity o : orderRepository.findAllById(ids)) {
            map.put(o.getId(), o.getOrderNo());
        }
        return map;
    }

    /**
     * 金额明细的汇总：累计支出 / 累计收入。
     *
     * <p><b>只统计真正的资金进出</b>（支付 / 退款 / 充值），
     * **不把运费、优惠、积分算进来** —— 那些已经包含在订单实付金额里，
     * 再单拎出来加一遍就是重复计算（2026-10-09 用户指出的问题）。
     *
     * <p>想知道「这单省了多少」去看订单详情，那里有完整的金额构成。
     *
     * <p><b>2026-10-14 口径修正</b>：改成按**余额实际变动**归集（加钱=收入、减钱=支出），
     * 不再按 type 一把梭。好处：①「累计收入 − 累计支出 = 当前钱包余额」天然成立，用户能自己对账；
     * ② 微信/支付宝这类外部流水余额前后不变，自动不计入累计（只在明细里标注展示）。
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public com.example.supermarket.dto.WalletSummaryResponse summarize(Long userId) {
        List<WalletTransaction> all = transactionRepository.findAll(
                byUserId(userId), org.springframework.data.domain.Pageable.unpaged()).getContent();
        BigDecimal out = BigDecimal.ZERO;
        BigDecimal income = BigDecimal.ZERO;
        for (WalletTransaction t : all) {
            BigDecimal before = t.getBalanceBefore() == null ? ZERO : t.getBalanceBefore();
            BigDecimal after = t.getBalanceAfter() == null ? ZERO : t.getBalanceAfter();
            BigDecimal delta = after.subtract(before);
            if (delta.signum() > 0) {
                income = income.add(delta);
            } else if (delta.signum() < 0) {
                out = out.add(delta.negate());
            }
        }
        return new com.example.supermarket.dto.WalletSummaryResponse(out, income);
    }

    @Transactional
    public void payOrder(Long userId, Long orderId, BigDecimal amount) {
        BigDecimal normalizedAmount = normalizeAmount(amount);
        if (normalizedAmount.compareTo(ZERO) <= 0) {
            return;
        }
        SysUser user = lockUser(userId);
        BigDecimal before = normalizeAmount(user.getBalance());
        if (before.compareTo(normalizedAmount) < 0) {
            throw new BusinessException(409, "Insufficient balance");
        }
        BigDecimal after = before.subtract(normalizedAmount);
        user.setBalance(after);
        userRepository.save(user);
        transactionRepository.save(buildTransaction(userId, orderId, TYPE_PAYMENT, normalizedAmount, before, after, "Order payment"));
    }

    /**
     * 仅记一笔 PAYMENT 流水、不扣余额。用于微信 / 支付宝等**模拟支付**渠道：
     * 系统暂无真实商户网关，这些渠道不扣钱包余额，但用户确实发生了消费，
     * 应在「金额明细」里看到这笔支出（#544）。余额前后值保持相等（balanceBefore = balanceAfter）。
     */
    @Transactional
    public void recordPaymentOnly(Long userId, Long orderId, BigDecimal amount) {
        BigDecimal normalizedAmount = normalizeAmount(amount);
        if (normalizedAmount.compareTo(ZERO) <= 0) {
            return;
        }
        SysUser user = lockUser(userId);
        BigDecimal balance = normalizeAmount(user.getBalance());
        transactionRepository.save(buildTransaction(userId, orderId, TYPE_PAYMENT, normalizedAmount, balance, balance, "Order payment (simulated channel)"));
    }

    @Transactional
    public void refundOrder(Long userId, Long orderId, BigDecimal amount, String remark) {
        BigDecimal normalizedAmount = normalizeAmount(amount);
        if (normalizedAmount.compareTo(ZERO) <= 0) {
            return;
        }
        // 按**原支付渠道**退：外部渠道(微信/支付宝)原路返回，不加钱包余额、只记一条 REFUND
        // 流水（balanceBefore == balanceAfter）；余额支付才 credit 加回余额。
        // 2026-10-14 修资损：原先无条件 credit，微信/支付宝订单退款等于凭空给余额加钱，
        // 既污染钱包余额又被算进累计收入。cancelOrder / refundForReturn(整单+按行) 都走这里，一并修正。
        String channel = paymentRecordRepository.findByOrderId(orderId)
                .map(PaymentRecord::getChannel).orElse(null);
        if (WalletTransactionResponse.isExternalChannel(channel)) {
            SysUser user = lockUser(userId);
            BigDecimal balance = normalizeAmount(user.getBalance());
            transactionRepository.save(buildTransaction(userId, orderId, TYPE_REFUND,
                    normalizedAmount, balance, balance, remark));
        } else {
            credit(userId, orderId, normalizedAmount, TYPE_REFUND, remark);
        }
    }

    /**
     * 充值订单支付成功后给钱包入账。
     * 注意：这里 orderId 传 null，因为 wallet_transaction.order_id 外键指向 orders 表，
     * 充值订单不属于交易订单，复用外键会冲突；充值流水通过 type=RECHARGE 区分即可。
     */
    @Transactional
    public void creditRecharge(Long userId, BigDecimal amount) {
        BigDecimal normalizedAmount = normalizeAmount(amount);
        if (normalizedAmount.compareTo(ZERO) <= 0) {
            return;
        }
        credit(userId, null, normalizedAmount, TYPE_RECHARGE, "充值订单到账");
    }

    private void credit(Long userId, Long orderId, BigDecimal amount, String type, String remark) {
        SysUser user = lockUser(userId);
        BigDecimal before = normalizeAmount(user.getBalance());
        BigDecimal after = before.add(amount);
        user.setBalance(after);
        userRepository.save(user);
        transactionRepository.save(buildTransaction(userId, orderId, type, amount, before, after, remark));
    }

    private SysUser lockUser(Long userId) {
        return userRepository.findByIdAndDeletedForUpdate(userId, NOT_DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private List<WalletTransactionResponse> recentTransactions(Long userId) {
        Pageable pageable = PageRequest.of(0, RECENT_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<WalletTransaction> rows = transactionRepository.findAll(byUserId(userId), pageable).getContent();
        Map<Long, String> orderNos = orderNosOf(rows);
        Map<Long, String> channels = channelsOf(rows);
        return rows.stream()
                .map(t -> WalletTransactionResponse.from(t, orderNos.get(t.getOrderId()), channels.get(t.getOrderId())))
                .toList();
    }

    private WalletTransaction buildTransaction(
            Long userId,
            Long orderId,
            String type,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            String remark
    ) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.setTransactionNo(generateTransactionNo());
        transaction.setUserId(userId);
        transaction.setOrderId(orderId);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setStatus(STATUS_SUCCESS);
        transaction.setRemark(remark);
        return transaction;
    }

    private Specification<WalletTransaction> byUserId(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String generateTransactionNo() {
        return "WT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

}
