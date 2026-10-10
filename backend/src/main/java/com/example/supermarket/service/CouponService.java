package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.CouponCreateRequest;
import com.example.supermarket.dto.CouponResponse;
import com.example.supermarket.dto.UserCouponResponse;
import com.example.supermarket.entity.Coupon;
import com.example.supermarket.entity.OrderEntity;
import com.example.supermarket.entity.UserCoupon;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.CouponRepository;
import com.example.supermarket.repository.UserCouponRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CouponService.class);

    private static final byte NOT_DELETED = 0;
    private static final byte ENABLED = 1;

    // ===== 领取方式（2026-10-10）=====
    /** 每人限领一次（历史默认值） */
    private static final byte CLAIM_ONCE = 0;
    /** 每天可领一次（当天 0 点重置） */
    private static final byte CLAIM_DAILY = 1;

    /**
     * 服务器本地时区的「今天 00:00」。
     *
     * <p>刻意用服务器本地时区而不是 UTC：用户判断「今天领过没有」用的也是本地日期。
     * 若按 UTC 算，东八区凌晨 0~8 点会被算成「昨天」，导致早上想领却提示已领过。
     */
    private LocalDateTime todayStart() {
        return LocalDate.now().atStartOfDay();
    }

    /** 该券今天是否已领过（仅 claim_type=1 时有意义） */
    private boolean claimedToday(Long userId, Coupon coupon) {
        if (coupon.getClaimType() == null || coupon.getClaimType() != CLAIM_DAILY) return false;
        return userCouponRepository.existsReceivedSince(userId, coupon.getId(), todayStart());
    }
    private static final String UNUSED = "UNUSED";
    private static final String USED = "USED";
    private static final int MAX_PAGE_SIZE = 100;

    // 注册自动发放的新人券 ID；为空则不发放。可在 application.yml 配置。
    @Value("${app.new-user-coupon-id:}")
    private String newUserCouponIdRaw;

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    public CouponService(CouponRepository couponRepository, UserCouponRepository userCouponRepository) {
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
    }

    /**
     * 注册成功后自动发放新人券。best-effort：券未配置、不存在、已被领取或发放异常时
     * 仅记录日志，绝不影响注册主流程。
     */
    public void issueNewUserCoupon(Long userId) {
        if (!StringUtils.hasText(newUserCouponIdRaw)) {
            return;
        }
        Long couponId;
        try {
            couponId = Long.valueOf(newUserCouponIdRaw.trim());
        } catch (NumberFormatException e) {
            log.warn("app.new-user-coupon-id 配置无效: {}", newUserCouponIdRaw);
            return;
        }
        try {
            Coupon coupon = couponRepository.findById(couponId)
                    .filter(c -> Byte.valueOf(NOT_DELETED).equals(c.getDeleted()))
                    .orElse(null);
            if (coupon == null) {
                log.warn("新人券不存在或已删除，couponId={}", couponId);
                return;
            }
            if (userCouponRepository.findByUserIdAndCouponId(userId, couponId).isPresent()) {
                return;
            }
            UserCoupon userCoupon = new UserCoupon();
            userCoupon.setCouponId(couponId);
            userCoupon.setUserId(userId);
            userCoupon.setStatus(UNUSED);
            userCoupon.setReceivedAt(LocalDateTime.now());
            userCouponRepository.save(userCoupon);
            couponRepository.increaseReceivedCount(couponId);
            log.info("已向新用户发放新人券，userId={} couponId={}", userId, couponId);
        } catch (Exception e) {
            log.warn("发放新人券失败，userId={} couponId={}: {}", userId, couponId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> listAdminCoupons(int page, int size, String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<Coupon> spec = notDeleted();
        if (StringUtils.hasText(keyword)) {
            String kw = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), kw));
        }
        Page<Coupon> coupons = couponRepository.findAll(spec, pageable);
        List<CouponResponse> items = coupons.getContent().stream()
                .map(coupon -> CouponResponse.from(coupon, false))
                .toList();
        return PageResponse.of(items, safePage, safeSize, coupons.getTotalElements());
    }

    @Transactional
    public CouponResponse createCoupon(CouponCreateRequest request) {
        if (request.getStartTime() == null || request.getEndTime() == null
                || !request.getStartTime().isBefore(request.getEndTime())) {
            throw new BusinessException(400, "Coupon end time must be after start time");
        }
        if (request.getDiscountAmount().compareTo(request.getThresholdAmount()) > 0) {
            throw new BusinessException(400, "Discount amount cannot exceed threshold amount");
        }
        Coupon coupon = new Coupon();
        coupon.setName(request.getName().trim());
        coupon.setThresholdAmount(request.getThresholdAmount());
        coupon.setDiscountAmount(request.getDiscountAmount());
        coupon.setTotalCount(request.getTotalCount());
        coupon.setReceivedCount(0);
        coupon.setStartTime(request.getStartTime());
        coupon.setEndTime(request.getEndTime());
        coupon.setStatus(ENABLED);
        // 领取方式（2026-10-10）：null 按 0（每人限领一次）处理，
        // 保证不传这个字段的老调用方行为不变。
        coupon.setClaimType(request.getClaimType() == null ? CLAIM_ONCE : request.getClaimType().byteValue());
        coupon.setDeleted(NOT_DELETED);
        return CouponResponse.from(couponRepository.save(coupon), false);
    }

    @Transactional
    public CouponResponse updateCouponStatus(Long id, Integer status) {
        Coupon coupon = getActiveCoupon(id);
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(400, "Invalid coupon status");
        }
        coupon.setStatus((byte) status.intValue());
        return CouponResponse.from(couponRepository.save(coupon), false);
    }

    // ==================== 编辑 / 删除（2026-10-11）====================

    /**
     * 修改优惠券。
     *
     * <p>⚠️ 关键约束：**已有人领取时不允许下调面额 / 抬高门槛 / 收紧总量**。
     * 已经领到手的券如果后台悄悄改小，用户下单时会发现金额对不上 ——
     * 这是会引发投诉的问题。有效期和领取方式可以改（对已持券者只有好处或中性）。
     *
     * <p>⚠️ totalCount 不允许小于已领数量：否则 received_count > total_count，
     * 进度条会算出 >100% 的进度、列表显示成负数余量。
     *
     * @return 更新后的券；{@code affectedHolders} 由前端用来提示「已影响 N 位已领用户」
     */
    @Transactional
    public CouponResponse updateCoupon(Long id, CouponCreateRequest request) {
        Coupon coupon = getActiveCoupon(id);
        if (request.getStartTime() == null || request.getEndTime() == null
                || !request.getStartTime().isBefore(request.getEndTime())) {
            throw new BusinessException(400, "优惠券的过期时间必须晚于生效时间");
        }
        if (request.getDiscountAmount().compareTo(request.getThresholdAmount()) > 0) {
            throw new BusinessException(400, "优惠金额不能超过使用门槛");
        }
        final long holders = coupon.getReceivedCount() == null ? 0L : coupon.getReceivedCount().longValue();
        if (holders > 0) {
            if (request.getDiscountAmount().compareTo(coupon.getDiscountAmount()) < 0) {
                throw new BusinessException(409, "已有 " + holders + " 人领取，不能下调优惠金额");
            }
            if (request.getThresholdAmount().compareTo(coupon.getThresholdAmount()) > 0) {
                throw new BusinessException(409, "已有 " + holders + " 人领取，不能抬高使用门槛");
            }
        }
        final int newTotal = request.getTotalCount() == null ? 0 : Math.max(0, request.getTotalCount());
        if (newTotal > 0 && newTotal < coupon.getReceivedCount()) {
            throw new BusinessException(400, "发放总量不能小于已领取数量（" + coupon.getReceivedCount() + "）");
        }

        coupon.setName(request.getName().trim());
        coupon.setThresholdAmount(request.getThresholdAmount());
        coupon.setDiscountAmount(request.getDiscountAmount());
        coupon.setTotalCount(newTotal);
        coupon.setStartTime(request.getStartTime());
        coupon.setEndTime(request.getEndTime());
        coupon.setClaimType(request.getClaimType() == null ? CLAIM_ONCE : request.getClaimType().byteValue());
        return CouponResponse.from(couponRepository.save(coupon), false);
    }

    /**
     * 删除优惠券（**软删**，与项目其余模块一致）。
     *
     * <p>已领到手的券**不受影响**：user_coupon 是独立记录，用户仍可正常使用，
     * 只是这张券不再出现在后台列表和用户端的可领列表里。
     * 不做物理删除 —— 订单里的优惠金额要能追溯到券。
     */
    @Transactional
    public void deleteCoupon(Long id) {
        Coupon coupon = getActiveCoupon(id);
        coupon.setDeleted((byte) 1);
        couponRepository.save(coupon);
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> listAvailableCoupons(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        Specification<Coupon> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("deleted"), NOT_DELETED),
                cb.equal(root.get("status"), ENABLED),
                cb.lessThanOrEqualTo(root.get("startTime"), now),
                cb.greaterThanOrEqualTo(root.get("endTime"), now)
        );
        List<Coupon> coupons = couponRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "discountAmount"));
        Set<Long> receivedCouponIds = userCouponRepository.findByUserIdOrderByIdDesc(userId).stream()
                .map(UserCoupon::getCouponId)
                .collect(Collectors.toSet());
        // ⚠️ 两类券的「已领」含义不同（2026-10-10）：
        //   限领一次券：历史上领过 = 已领（receivedCouponIds 够用）
        //   每日可领券：只有「今天」领过才算已领 —— 否则用户昨天领过，
        //   今天打开看到「已领取」按钮灰了，实际却能领，前端与后端打架。
        // 所以 receivedByCurrentUser 按 claimType 分别取值，
        // claimedToday 只对每日券为 true（前端据此显示「今日已领，明天再来」）。
        return coupons.stream()
                .map(coupon -> {
                    boolean daily = coupon.getClaimType() != null && coupon.getClaimType() == CLAIM_DAILY;
                    if (!daily) {
                        return CouponResponse.from(coupon, receivedCouponIds.contains(coupon.getId()), false);
                    }
                    boolean today = claimedToday(userId, coupon);
                    return CouponResponse.from(coupon, today, today);
                })
                .toList();
    }

    @Transactional
    public UserCouponResponse receiveCoupon(Long userId, Long couponId) {
        Coupon coupon = getActiveCoupon(couponId);
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStatus() == null || coupon.getStatus() != ENABLED) {
            throw new BusinessException(409, "Coupon is not available");
        }
        if (coupon.getStartTime().isAfter(now) || coupon.getEndTime().isBefore(now)) {
            throw new BusinessException(409, "Coupon is not in its valid period");
        }
        // 领取限制按 claim_type 分支（2026-10-10）：
        //   0 = 每人限领一次（**改动前的行为**，保持不变）
        //   1 = 每天可领一次（当天 0 点重置）
        boolean dailyClaim = coupon.getClaimType() != null && coupon.getClaimType() == CLAIM_DAILY;
        if (dailyClaim) {
            // 每日可领：只看「今天」领没领过。昨天领过今天照样能领。
            if (userCouponRepository.existsReceivedSince(userId, couponId, todayStart())) {
                throw new BusinessException(409, "今日已领取，明天再来");
            }
        } else if (userCouponRepository.findByUserIdAndCouponId(userId, couponId).isPresent()) {
            throw new BusinessException(409, "Coupon already received");
        }
        int updated = couponRepository.increaseReceivedCount(couponId);
        if (updated == 0) {
            throw new BusinessException(409, "Coupon is fully claimed");
        }
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setCouponId(couponId);
        userCoupon.setUserId(userId);
        userCoupon.setStatus(UNUSED);
        userCoupon.setReceivedAt(now);
        UserCoupon saved = userCouponRepository.save(userCoupon);
        return UserCouponResponse.from(saved, couponRepository.findById(couponId).orElse(coupon));
    }

    @Transactional(readOnly = true)
    public List<UserCouponResponse> listMyCoupons(Long userId) {
        List<UserCoupon> userCoupons = userCouponRepository.findByUserIdOrderByIdDesc(userId);
        return toResponses(userCoupons);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserCouponResponse> listMyCoupons(Long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<UserCoupon> pageResult = userCouponRepository.findByUserId(userId, pageable);
        return PageResponse.of(toResponses(pageResult.getContent()), safePage, safeSize, pageResult.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<UserCouponResponse> listUsableCoupons(Long userId, BigDecimal orderAmount) {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal target = orderAmount == null ? BigDecimal.ZERO : orderAmount;
        return userCouponRepository.findByUserIdOrderByIdDesc(userId).stream()
                .filter(userCoupon -> UNUSED.equals(userCoupon.getStatus()))
                .filter(userCoupon -> {
                    Coupon coupon = couponRepository.findById(userCoupon.getCouponId()).orElse(null);
                    return coupon != null
                            && !coupon.getStartTime().isAfter(now)
                            && !coupon.getEndTime().isBefore(now)
                            && coupon.getThresholdAmount().compareTo(target) <= 0;
                })
                .map(userCoupon -> UserCouponResponse.from(userCoupon, couponRepository.findById(userCoupon.getCouponId()).orElseThrow()))
                .toList();
    }

    /** 核销结果：券名快照 + 实际抵扣金额（券名要落到订单上，订单详情才能说清用了哪张券） */
    public record CouponUsage(String couponName, BigDecimal discountAmount) {
    }

    @Transactional
    public CouponUsage consumeForOrder(Long userId, Long userCouponId, Long orderId, BigDecimal orderTotal) {
        UserCoupon userCoupon = userCouponRepository.findByIdAndUserIdForUpdate(userCouponId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
        Coupon coupon = getActiveCoupon(userCoupon.getCouponId());
        LocalDateTime now = LocalDateTime.now();
        if (!UNUSED.equals(userCoupon.getStatus())) {
            throw new BusinessException(409, "Coupon is already used or expired");
        }
        if (coupon.getStartTime().isAfter(now) || coupon.getEndTime().isBefore(now)) {
            throw new BusinessException(409, "Coupon is not in its valid period");
        }
        if (coupon.getThresholdAmount().compareTo(orderTotal) > 0) {
            throw new BusinessException(409, "Order total does not reach the coupon threshold");
        }
        userCoupon.setStatus(USED);
        userCoupon.setOrderId(orderId);
        userCoupon.setUsedAt(now);
        userCouponRepository.save(userCoupon);
        return new CouponUsage(coupon.getName(), coupon.getDiscountAmount());
    }

    @Transactional
    public void restoreIfUnpaid(OrderEntity order) {
        if (order.getUserCouponId() == null || !"UNPAID".equals(order.getPaymentStatus())) {
            return;
        }
        userCouponRepository.findById(order.getUserCouponId()).ifPresent(userCoupon -> {
            if (USED.equals(userCoupon.getStatus())
                    && order.getId().equals(userCoupon.getOrderId())) {
                userCoupon.setStatus(UNUSED);
                userCoupon.setOrderId(null);
                userCoupon.setUsedAt(null);
                userCouponRepository.save(userCoupon);
            }
        });
    }

    private List<UserCouponResponse> toResponses(List<UserCoupon> userCoupons) {
        if (userCoupons.isEmpty()) {
            return List.of();
        }
        Map<Long, Coupon> couponMap = couponRepository.findAllById(
                        userCoupons.stream().map(UserCoupon::getCouponId).distinct().toList()
                )
                .stream()
                .collect(Collectors.toMap(Coupon::getId, Function.identity(), (left, right) -> left));
        return userCoupons.stream()
                .map(userCoupon -> UserCouponResponse.from(userCoupon,
                        couponMap.getOrDefault(userCoupon.getCouponId(), placeholderCoupon(userCoupon))))
                .toList();
    }

    private Coupon placeholderCoupon(UserCoupon userCoupon) {
        Coupon coupon = new Coupon();
        coupon.setId(userCoupon.getCouponId());
        coupon.setName("已删除优惠券");
        coupon.setThresholdAmount(BigDecimal.ZERO);
        coupon.setDiscountAmount(BigDecimal.ZERO);
        coupon.setTotalCount(0);
        coupon.setReceivedCount(0);
        coupon.setStartTime(LocalDateTime.now());
        coupon.setEndTime(LocalDateTime.now().minusDays(1));
        return coupon;
    }

    private Coupon getActiveCoupon(Long id) {
        return couponRepository.findById(id)
                .filter(coupon -> Byte.valueOf(NOT_DELETED).equals(coupon.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
    }

    private Specification<Coupon> notDeleted() {
        return (root, query, cb) -> cb.equal(root.get("deleted"), NOT_DELETED);
    }
}
