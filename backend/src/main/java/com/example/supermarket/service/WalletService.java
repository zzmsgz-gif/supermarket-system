package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.RechargeRequest;
import com.example.supermarket.dto.WalletResponse;
import com.example.supermarket.dto.WalletTransactionResponse;
import com.example.supermarket.entity.SysUser;
import com.example.supermarket.repository.OrderRepository;
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

    public WalletService(SysUserRepository userRepository, WalletTransactionRepository transactionRepository,
            OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.orderRepository = orderRepository;
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
        List<WalletTransactionResponse> items = transactions.getContent().stream()
                .map(t -> WalletTransactionResponse.from(t, orderNos.get(t.getOrderId())))
                .toList();
        return PageResponse.of(items, safePage, safeSize, transactions.getTotalElements());
    }

    /** 批量取订单号：逐条查会 N+1，这里一次性 findAllById */
    private Map<Long, String> orderNosOf(List<WalletTransaction> rows) {
        Set<Long> ids = rows.stream()
                .map(WalletTransaction::getOrderId)
                .filter(java.util.Objects::nonNull)
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

    /**
     * 金额明细的汇总：累计支出 / 累计收入。
     *
     * <p><b>只统计真正的资金进出</b>（支付 / 退款 / 充值），
     * **不把运费、优惠、积分算进来** —— 那些已经包含在订单实付金额里，
     * 再单拎出来加一遍就是重复计算（2026-10-09 用户指出的问题）。
     *
     * <p>想知道「这单省了多少」去看订单详情，那里有完整的金额构成。
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public com.example.supermarket.dto.WalletSummaryResponse summarize(Long userId) {
        List<WalletTransaction> all = transactionRepository.findAll(
                byUserId(userId), org.springframework.data.domain.Pageable.unpaged()).getContent();
        BigDecimal out = BigDecimal.ZERO;
        BigDecimal income = BigDecimal.ZERO;
        for (WalletTransaction t : all) {
            BigDecimal v = t.getAmount() == null ? BigDecimal.ZERO : t.getAmount();
            if ("RECHARGE".equals(t.getType()) || "REFUND".equals(t.getType())) {
                income = income.add(v);
            } else {
                out = out.add(v);
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

    @Transactional
    public void refundOrder(Long userId, Long orderId, BigDecimal amount, String remark) {
        BigDecimal normalizedAmount = normalizeAmount(amount);
        if (normalizedAmount.compareTo(ZERO) <= 0) {
            return;
        }
        credit(userId, orderId, normalizedAmount, TYPE_REFUND, remark);
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
        return transactionRepository.findAll(byUserId(userId), pageable).getContent().stream()
                .map(WalletTransactionResponse::from)
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
