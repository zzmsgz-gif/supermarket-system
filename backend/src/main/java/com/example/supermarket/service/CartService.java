package com.example.supermarket.service;

import com.example.supermarket.dto.AddCartItemRequest;
import com.example.supermarket.dto.CartItemResponse;
import com.example.supermarket.dto.CartResponse;
import com.example.supermarket.dto.SelectCartItemsRequest;
import com.example.supermarket.dto.UpdateCartItemRequest;
import com.example.supermarket.entity.CartItem;
import com.example.supermarket.entity.Product;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.service.ActivityEvaluation;
import com.example.supermarket.service.ActivityService;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.CartItemRepository;
import com.example.supermarket.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private static final byte SELECTED = 1;
    private static final byte NOT_SELECTED = 0;
    private static final byte NOT_DELETED = 0;
    private static final String ON_SALE = "ON_SALE";

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ActivityService activityService;

    public CartService(CartItemRepository cartItemRepository, ProductRepository productRepository,
            ActivityService activityService) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.activityService = activityService;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        List<CartItem> cartItems = cartItemRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        return buildCartResponse(cartItems);
    }

    @Transactional
    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        Product product = getAvailableProduct(request.getProductId());
        String spec = normalizeSpec(request.getSkuSpec());
        // 关键修复：按 (userId, productId, skuSpec) 定位，不同规格是独立购物车行，
        // 同规格才累加数量，避免「换规格被覆盖 / 数量被偷偷累加」的问题。
        CartItem cartItem = cartItemRepository
                .findByUserIdAndProductIdAndSkuSpec(userId, request.getProductId(), spec)
                .orElseGet(() -> newCartItem(userId, request.getProductId()));
        int newQuantity = cartItem.getQuantity() + request.getQuantity();
        validateStock(product, newQuantity);
        cartItem.setQuantity(newQuantity);
        cartItem.setSelected(SELECTED);
        cartItem.setSkuSpec(spec);
        cartItemRepository.save(cartItem);
        return getCart(userId);
    }

    @Transactional
    public CartResponse updateItem(Long userId, Long itemId, UpdateCartItemRequest request) {
        if (request.getQuantity() == null && request.getSelected() == null) {
            throw new BusinessException(400, "Quantity or selected is required");
        }
        CartItem cartItem = getOwnedCartItem(userId, itemId);
        if (request.getQuantity() != null) {
            Product product = getAvailableProduct(cartItem.getProductId());
            validateStock(product, request.getQuantity());
            cartItem.setQuantity(request.getQuantity());
        }
        if (request.getSelected() != null) {
            cartItem.setSelected(toByte(request.getSelected()));
        }
        cartItemRepository.save(cartItem);
        return getCart(userId);
    }

    @Transactional
    public CartResponse updateSelection(Long userId, SelectCartItemsRequest request) {
        List<CartItem> items = cartItemRepository.findByUserIdAndIdIn(userId, request.getItemIds());
        if (items.size() != request.getItemIds().stream().distinct().count()) {
            throw new ResourceNotFoundException("Cart item not found");
        }
        byte selected = toByte(request.getSelected());
        items.forEach(item -> item.setSelected(selected));
        cartItemRepository.saveAll(items);
        return getCart(userId);
    }

    @Transactional
    public CartResponse deleteItem(Long userId, Long itemId) {
        CartItem cartItem = getOwnedCartItem(userId, itemId);
        cartItemRepository.delete(cartItem);
        return getCart(userId);
    }

    private CartResponse buildCartResponse(List<CartItem> cartItems) {
        if (cartItems.isEmpty()) {
            return new CartResponse(List.of(), 0, BigDecimal.ZERO, BigDecimal.ZERO, null);
        }
        List<Long> productIds = cartItems.stream().map(CartItem::getProductId).distinct().toList();
        Map<Long, Product> productMap = productRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (left, right) -> left, HashMap::new));
        List<CartItemResponse> items = cartItems.stream()
                .map(item -> CartItemResponse.from(item, requireProduct(productMap, item.getProductId())))
                .toList();
        int selectedCount = items.stream()
                .filter(CartItemResponse::getSelected)
                .mapToInt(CartItemResponse::getQuantity)
                .sum();
        BigDecimal selectedAmount = items.stream()
                .filter(CartItemResponse::getSelected)
                .map(CartItemResponse::getSubtotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Long, BigDecimal> productSubtotal = items.stream()
                .filter(CartItemResponse::getSelected)
                .collect(Collectors.toMap(CartItemResponse::getProductId, CartItemResponse::getSubtotalAmount, (left, right) -> left));
        ActivityEvaluation activityEval = activityService.evaluateBestActivity(productSubtotal, productMap);
        return new CartResponse(items, selectedCount, selectedAmount, activityEval.getDiscount(),
                activityEval.getActivity() != null ? activityEval.getActivity().getName() : null);
    }

    private Product getAvailableProduct(Long productId) {
        return productRepository.findByIdAndStatusAndDeleted(productId, ON_SALE, NOT_DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private CartItem getOwnedCartItem(Long userId, Long itemId) {
        return cartItemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));
    }

    private CartItem newCartItem(Long userId, Long productId) {
        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setProductId(productId);
        item.setQuantity(0);
        item.setSelected(SELECTED);
        return item;
    }

    private Product requireProduct(Map<Long, Product> productMap, Long productId) {
        Product product = productMap.get(productId);
        if (product == null) {
            throw new ResourceNotFoundException("Product not found");
        }
        return product;
    }

    private void validateStock(Product product, int quantity) {
        if (product.getStock() < quantity) {
            throw new BusinessException(409, "Insufficient stock");
        }
    }

    private String normalizeSpec(String spec) {
        // 空规格统一为 ""（而非 null），与 cart_item 的 NOT NULL DEFAULT '' 及唯一键 (user_id,product_id,sku_spec) 语义一致，
        // 保证「无规格商品」的不同加购会正确合并到同一行，而非被 MySQL 唯一索引当成非法重复行。
        return (spec == null || spec.isBlank()) ? "" : spec.trim();
    }

    private byte toByte(Boolean value) {
        return Boolean.TRUE.equals(value) ? SELECTED : NOT_SELECTED;
    }

}
