package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.RechargeRequest;
import com.example.supermarket.dto.WalletResponse;
import com.example.supermarket.dto.WalletTransactionResponse;
import com.example.supermarket.entity.SysUser;
import com.example.supermarket.entity.WalletTransaction;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.SysUserRepository;
import com.example.supermarket.repository.WalletTransactionRepository;
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

    public WalletService(SysUserRepository userRepository, WalletTransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
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
        List<WalletTransactionResponse> items = transactions.getContent().stream()
                .map(WalletTransactionResponse::from)
                .toList();
        return PageResponse.of(items, safePage, safeSize, transactions.getTotalElements());
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
