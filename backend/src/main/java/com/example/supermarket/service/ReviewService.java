package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.ReviewCreateRequest;
import com.example.supermarket.dto.ReviewResponse;
import com.example.supermarket.entity.OrderEntity;
import com.example.supermarket.entity.OrderItem;
import com.example.supermarket.entity.ProductReview;
import com.example.supermarket.entity.SysUser;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.OrderItemRepository;
import com.example.supermarket.repository.OrderRepository;
import com.example.supermarket.repository.ProductRepository;
import com.example.supermarket.repository.ProductReviewRepository;
import com.example.supermarket.repository.SysUserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ReviewService {

    private static final String COMPLETED = "COMPLETED";
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ProductReviewRepository reviewRepository;
    private final SysUserRepository userRepository;

    public ReviewService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            ProductReviewRepository reviewRepository,
            SysUserRepository userRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public List<ReviewResponse> createOrderReview(Long userId, Long orderId, ReviewCreateRequest request) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!COMPLETED.equals(order.getStatus())) {
            throw new BusinessException(409, "Only completed orders can be reviewed");
        }
        if (reviewRepository.existsByOrderId(orderId)) {
            throw new BusinessException(409, "Order already reviewed");
        }
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(orderId);
        if (items.isEmpty()) {
            throw new BusinessException(409, "Order has no items to review");
        }
        String content = StringUtils.hasText(request.getContent()) ? request.getContent().trim() : null;
        List<ProductReview> reviews = items.stream()
                .filter(item -> !reviewRepository.existsByOrderItemId(item.getId()))
                .map(item -> buildReview(order, item, userId, request.getRating(), content, request.getImageUrls()))
                .toList();
        List<ProductReview> saved = reviewRepository.saveAll(reviews);
        SysUser user = userRepository.findById(userId).orElse(null);
        return saved.stream()
                .map(review -> ReviewResponse.from(review, user, productName(review.getProductId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listOrderReviews(Long userId, Long orderId) {
        orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        List<ProductReview> reviews = reviewRepository.findByOrderId(orderId);
        if (reviews.isEmpty()) {
            return List.of();
        }
        Map<Long, SysUser> userMap = userRepository.findAllById(
                        reviews.stream().map(ProductReview::getUserId).distinct().toList()
                )
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (left, right) -> left));
        return reviews.stream()
                .map(review -> ReviewResponse.from(review, userMap.get(review.getUserId()), productName(review.getProductId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listProductReviews(Long productId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProductReview> reviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable);
        if (reviews.isEmpty()) {
            return PageResponse.of(List.of(), safePage, safeSize, reviews.getTotalElements());
        }
        Map<Long, SysUser> userMap = userRepository.findAllById(
                        reviews.getContent().stream().map(ProductReview::getUserId).distinct().toList()
                )
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (left, right) -> left));
        List<ReviewResponse> items = reviews.getContent().stream()
                .map(review -> ReviewResponse.from(review, userMap.get(review.getUserId()), productName(productId)))
                .toList();
        return PageResponse.of(items, safePage, safeSize, reviews.getTotalElements());
    }

    private ProductReview buildReview(OrderEntity order, OrderItem item, Long userId, Integer rating, String content, List<String> imageUrls) {
        ProductReview review = new ProductReview();
        review.setOrderId(order.getId());
        review.setOrderItemId(item.getId());
        review.setProductId(item.getProductId());
        review.setUserId(userId);
        review.setRating(rating.byteValue());
        review.setContent(content);
        review.setImageUrls(imageUrls == null ? null : String.join(",", imageUrls));
        return review;
    }

    private String productName(Long productId) {
        return productRepository.findById(productId)
                .map(product -> product.getName())
                .orElse("已下架商品");
    }
}
