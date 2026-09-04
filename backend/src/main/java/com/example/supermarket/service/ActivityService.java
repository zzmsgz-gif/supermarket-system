package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.ActivityResponse;
import com.example.supermarket.dto.AdminActivityRequest;
import com.example.supermarket.entity.Activity;
import com.example.supermarket.entity.Product;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.ActivityRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ActivityService {

    private static final byte NOT_DELETED = 0;
    private static final byte ENABLED = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final ActivityRepository activityRepository;

    public ActivityService(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    /**
     * 对当前商品清单（productSubtotal: 商品ID -> 小计金额）匹配「最优」营销活动并计算可减免金额。
     * 命中多个活动时取减免金额最大者；无任何命中则 discount=0、activity=null。
     */
    public ActivityEvaluation evaluateBestActivity(Map<Long, BigDecimal> productSubtotal, Map<Long, Product> productMap) {
        List<Activity> active = listActiveNow();
        BigDecimal bestDiscount = BigDecimal.ZERO;
        Activity best = null;
        for (Activity activity : active) {
            BigDecimal qualifying = computeQualifying(activity, productSubtotal, productMap);
            BigDecimal discount = computeDiscount(activity, qualifying);
            if (discount.compareTo(bestDiscount) > 0) {
                bestDiscount = discount;
                best = activity;
            }
        }
        return new ActivityEvaluation(best, bestDiscount);
    }

    @Transactional(readOnly = true)
    public List<Activity> listActiveNow() {
        LocalDateTime now = LocalDateTime.now();
        return activityRepository.findByStatusAndDeletedAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
                ENABLED, NOT_DELETED, now, now);
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> listActivities(int page, int size, String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize,
                Sort.by(Sort.Order.desc("priority"), Sort.Order.desc("createdAt")));
        Specification<Activity> spec = notDeleted();
        if (StringUtils.hasText(keyword)) {
            String kw = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), kw));
        }
        Page<Activity> page1 = activityRepository.findAll(spec, pageable);
        List<ActivityResponse> items = page1.getContent().stream().map(ActivityResponse::from).toList();
        return PageResponse.of(items, safePage, safeSize, page1.getTotalElements());
    }

    @Transactional
    public ActivityResponse createActivity(AdminActivityRequest request) {
        validate(request);
        Activity activity = new Activity();
        applyRequest(activity, request);
        activity.setDeleted(NOT_DELETED);
        return ActivityResponse.from(activityRepository.save(activity));
    }

    @Transactional
    public ActivityResponse updateActivity(Long id, AdminActivityRequest request) {
        Activity activity = getActive(id);
        validate(request);
        applyRequest(activity, request);
        return ActivityResponse.from(activityRepository.save(activity));
    }

    @Transactional
    public void deleteActivity(Long id) {
        Activity activity = getActive(id);
        activity.setDeleted((byte) 1);
        activityRepository.save(activity);
    }

    @Transactional
    public ActivityResponse updateStatus(Long id, Integer status) {
        Activity activity = getActive(id);
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(400, "Invalid activity status");
        }
        activity.setStatus((byte) status.intValue());
        return ActivityResponse.from(activityRepository.save(activity));
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> listPublicActive() {
        return listActiveNow().stream().map(ActivityResponse::from).collect(Collectors.toList());
    }

    private void applyRequest(Activity activity, AdminActivityRequest request) {
        activity.setName(request.getName().trim());
        activity.setType(request.getType());
        activity.setScope(request.getScope());
        activity.setCategoryId(Activity.SCOPE_CATEGORY.equals(request.getScope()) ? request.getCategoryId() : null);
        activity.setProductId(Activity.SCOPE_PRODUCT.equals(request.getScope()) ? request.getProductId() : null);
        activity.setThreshold(request.getThreshold() != null ? request.getThreshold() : BigDecimal.ZERO);
        activity.setDiscount(request.getDiscount());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setStatus(request.getStatus() != null ? request.getStatus().byteValue() : ENABLED);
        activity.setPriority(request.getPriority() != null ? request.getPriority() : 0);
    }

    private void validate(AdminActivityRequest request) {
        if (!Activity.TYPE_FULL_REDUCTION.equals(request.getType())
                && !Activity.TYPE_DISCOUNT.equals(request.getType())) {
            throw new BusinessException(400, "活动类型仅支持 FULL_REDUCTION / DISCOUNT");
        }
        if (!Activity.SCOPE_ALL.equals(request.getScope())
                && !Activity.SCOPE_CATEGORY.equals(request.getScope())
                && !Activity.SCOPE_PRODUCT.equals(request.getScope())) {
            throw new BusinessException(400, "作用域仅支持 ALL / CATEGORY / PRODUCT");
        }
        if (Activity.SCOPE_CATEGORY.equals(request.getScope()) && request.getCategoryId() == null) {
            throw new BusinessException(400, "指定分类活动必须选择分类");
        }
        if (Activity.SCOPE_PRODUCT.equals(request.getScope()) && request.getProductId() == null) {
            throw new BusinessException(400, "指定商品活动必须选择商品");
        }
        if (request.getStartTime() == null || request.getEndTime() == null
                || !request.getStartTime().isBefore(request.getEndTime())) {
            throw new BusinessException(400, "活动结束时间必须晚于开始时间");
        }
        if (request.getDiscount() == null || request.getDiscount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "优惠值必须大于 0");
        }
        if (Activity.TYPE_FULL_REDUCTION.equals(request.getType())) {
            if (request.getThreshold() == null || request.getThreshold().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "满减活动的门槛必须大于 0");
            }
            if (request.getDiscount().compareTo(request.getThreshold()) > 0) {
                throw new BusinessException(400, "减免金额不能超过满减门槛");
            }
        }
        if (Activity.TYPE_DISCOUNT.equals(request.getType())) {
            if (request.getDiscount().compareTo(BigDecimal.ONE) >= 0
                    || request.getDiscount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "折扣率必须介于 0 与 1 之间（如 0.9 表示 9 折）");
            }
        }
    }

    private BigDecimal computeQualifying(Activity activity, Map<Long, BigDecimal> productSubtotal,
            Map<Long, Product> productMap) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> entry : productSubtotal.entrySet()) {
            Product product = productMap.get(entry.getKey());
            if (product == null) {
                continue;
            }
            if (matchesScope(activity, product)) {
                sum = sum.add(entry.getValue());
            }
        }
        return sum;
    }

    private boolean matchesScope(Activity activity, Product product) {
        if (Activity.SCOPE_ALL.equals(activity.getScope())) {
            return true;
        }
        if (Activity.SCOPE_CATEGORY.equals(activity.getScope())) {
            return activity.getCategoryId() != null && activity.getCategoryId().equals(product.getCategoryId());
        }
        if (Activity.SCOPE_PRODUCT.equals(activity.getScope())) {
            return activity.getProductId() != null && activity.getProductId().equals(product.getId());
        }
        return false;
    }

    private BigDecimal computeDiscount(Activity activity, BigDecimal qualifying) {
        if (qualifying == null || qualifying.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (Activity.TYPE_FULL_REDUCTION.equals(activity.getType())) {
            if (activity.getThreshold() != null && qualifying.compareTo(activity.getThreshold()) >= 0) {
                return activity.getDiscount() != null ? activity.getDiscount() : BigDecimal.ZERO;
            }
            return BigDecimal.ZERO;
        }
        if (Activity.TYPE_DISCOUNT.equals(activity.getType())) {
            if (activity.getDiscount() == null) {
                return BigDecimal.ZERO;
            }
            BigDecimal rate = activity.getDiscount();
            if (rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
                return BigDecimal.ZERO;
            }
            return qualifying.multiply(BigDecimal.ONE.subtract(rate)).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private Activity getActive(Long id) {
        return activityRepository.findById(id)
                .filter(a -> NOT_DELETED == a.getDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Activity not found"));
    }

    private Specification<Activity> notDeleted() {
        return (root, query, cb) -> cb.equal(root.get("deleted"), NOT_DELETED);
    }
}
