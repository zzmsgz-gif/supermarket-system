package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.AdminReviewResponse;
import com.example.supermarket.dto.AdminReviewSummary;
import com.example.supermarket.entity.Product;
import com.example.supermarket.entity.ProductReview;
import com.example.supermarket.entity.SysUser;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.ProductRepository;
import com.example.supermarket.repository.ProductReviewRepository;
import com.example.supermarket.repository.SysUserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 后台评价管理。
 *
 * <p>补的是一条**断掉的闭环**：此前评价只能写（前台晒图评价），后台 15 个菜单里没有任何评价入口、
 * 也没有评价查询接口，评价只在商品详情页出现 —— 商家看不到、回不了差评，等于用户说了话没人接。
 *
 * <p>这一页只做三件事，对应商家真实会做的三个动作：**看**（含按评分/未回复筛选）、**回**（公开回复）、
 * **藏**（违规隐藏）。
 */
@Service
public class AdminReviewService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String BLANK = "";

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final SysUserRepository userRepository;

    public AdminReviewService(
            ProductReviewRepository reviewRepository,
            ProductRepository productRepository,
            SysUserRepository userRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminReviewResponse> list(int page, int size, Integer rating, Boolean replied, String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProductReview> reviews = reviewRepository.findAll(buildSpec(rating, replied, keyword), pageable);
        if (reviews.isEmpty()) {
            return PageResponse.of(List.of(), safePage, safeSize, reviews.getTotalElements());
        }
        List<ProductReview> content = reviews.getContent();
        Map<Long, Product> products = productRepository
                .findAllById(content.stream().map(ProductReview::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity(), (left, right) -> left));
        Map<Long, SysUser> users = userRepository
                .findAllById(content.stream().map(ProductReview::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(SysUser::getId, Function.identity(), (left, right) -> left));
        List<AdminReviewResponse> items = content.stream()
                .map(review -> {
                    Product product = products.get(review.getProductId());
                    SysUser user = users.get(review.getUserId());
                    return AdminReviewResponse.from(
                            review,
                            product == null ? "已下架商品" : product.getName(),
                            user == null ? null : user.getUsername(),
                            user == null ? null : user.getNickname());
                })
                .toList();
        return PageResponse.of(items, safePage, safeSize, reviews.getTotalElements());
    }

    /** 顶部汇总：总评价数、已隐藏数、**未回复数**（这条才是行动信号）、平均分与星级分布 */
    @Transactional(readOnly = true)
    public AdminReviewSummary summary() {
        List<AdminReviewSummary.StarSlice> stars = reviewRepository.countByRating().stream()
                .map(row -> new AdminReviewSummary.StarSlice(
                        ((Number) row[0]).intValue(), ((Number) row[1]).longValue()))
                .toList();
        long total = reviewRepository.count();
        long hidden = reviewRepository.count(hiddenSpec());
        long unreplied = reviewRepository.count(unrepliedSpec());
        Double avg = reviewRepository.averageRating();
        return new AdminReviewSummary(total, hidden, unreplied, avg == null ? null : Math.round(avg * 10) / 10.0, stars);
    }

    /** 回复（传空字符串即撤回回复）。回复会立刻出现在前台商品详情页。 */
    @Transactional
    public AdminReviewResponse reply(Long id, String replyContent) {
        ProductReview review = getReview(id);
        String content = StringUtils.hasText(replyContent) ? replyContent.trim() : null;
        review.setReplyContent(content);
        // 撤回时把时间也清掉，避免留下"有回复时间但没有回复内容"的脏状态
        review.setReplyAt(content == null ? null : LocalDateTime.now());
        ProductReview saved = reviewRepository.save(review);
        return toResponse(saved);
    }

    /** 违规隐藏 / 恢复展示。**不物理删除**，否则用户能对同一张订单重复评价。 */
    @Transactional
    public AdminReviewResponse setHidden(Long id, boolean hidden) {
        ProductReview review = getReview(id);
        review.setHidden((byte) (hidden ? 1 : 0));
        ProductReview saved = reviewRepository.save(review);
        return toResponse(saved);
    }

    private ProductReview getReview(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
    }

    private AdminReviewResponse toResponse(ProductReview review) {
        Product product = productRepository.findById(review.getProductId()).orElse(null);
        SysUser user = userRepository.findById(review.getUserId()).orElse(null);
        return AdminReviewResponse.from(
                review,
                product == null ? "已下架商品" : product.getName(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getNickname());
    }

    private Specification<ProductReview> buildSpec(Integer rating, Boolean replied, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (rating != null) {
                predicates.add(cb.equal(root.get("rating"), rating.byteValue()));
            }
            if (replied != null) {
                Predicate blank = blankReply(root, cb);
                predicates.add(Boolean.TRUE.equals(replied) ? cb.not(blank) : blank);
            }
            if (StringUtils.hasText(keyword)) {
                predicates.add(cb.like(root.get("content"), "%" + keyword.trim() + "%"));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** 「未回复」＝ reply_content 为 null 或空串（历史行可能是空串，不能只判 null） */
    private Predicate blankReply(jakarta.persistence.criteria.Root<ProductReview> root,
                                 jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.or(cb.isNull(root.get("replyContent")), cb.equal(root.get("replyContent"), BLANK));
    }

    private Specification<ProductReview> hiddenSpec() {
        return (root, query, cb) -> cb.equal(root.get("hidden"), (byte) 1);
    }

    private Specification<ProductReview> unrepliedSpec() {
        return (root, query, cb) -> blankReply(root, cb);
    }
}
