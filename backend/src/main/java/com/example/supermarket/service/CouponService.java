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
        return coupons.stream()
                .map(coupon -> CouponResponse.from(coupon, receivedCouponIds.contains(coupon.getId())))
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
        if (userCouponRepository.findByUserIdAndCouponId(userId, couponId).isPresent()) {
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

    @Transactional
    public BigDecimal consumeForOrder(Long userId, Long userCouponId, Long orderId, BigDecimal orderTotal) {
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
        return coupon.getDiscountAmount();
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
