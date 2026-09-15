package com.example.supermarket.service;

import com.example.supermarket.dto.AddCartItemRequest;
import com.example.supermarket.dto.CartItemResponse;
import com.example.supermarket.dto.CartResponse;
import com.example.supermarket.dto.SelectCartItemsRequest;
import com.example.supermarket.dto.UpdateCartItemRequest;
import com.example.supermarket.entity.CartItem;
import com.example.supermarket.entity.FlashSale;
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
    private final FlashSaleService flashSaleService;

    public CartService(CartItemRepository cartItemRepository, ProductRepository productRepository,
            ActivityService activityService, FlashSaleService flashSaleService) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.activityService = activityService;
        this.flashSaleService = flashSaleService;
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
        validateFlashLimit(userId, product, newQuantity);
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
            // 只在「增加」时校验限购：减少数量（或删除）永远不该被拒 —— 名额被别人抢走、
            // 后台把限购调小时，若连改小都拦，用户就被锁在一个自己收拾不了的购物车里了。
            if (request.getQuantity() > cartItem.getQuantity()) {
                validateFlashLimit(userId, product, request.getQuantity());
            }
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
        // 限时秒杀：按「此刻进行中」的场次定价，与 OrderService.buildOrderItem 用同一套 min() 规则
        Map<Long, FlashSale> flashSales = flashSaleService.runningByProductIds(productIds);
        List<CartItemResponse> items = cartItems.stream()
                .map(item -> CartItemResponse.from(
                        item,
                        requireProduct(productMap, item.getProductId()),
                        flashSales.get(item.getProductId())))
                .toList();
        int selectedCount = items.stream()
                .filter(CartItemResponse::getSelected)
                .mapToInt(CartItemResponse::getQuantity)
                .sum();
        BigDecimal selectedAmount = items.stream()
                .filter(CartItemResponse::getSelected)
                .map(CartItemResponse::getSubtotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 同一商品可能因规格不同存在多行，活动门槛金额必须按商品累加所有行，
        // 而非只取第一行（否则门槛判定/折扣基数偏小，出现「购物车合计与活动优惠对不上」）。
        Map<Long, BigDecimal> productSubtotal = new HashMap<>();
        items.stream().filter(CartItemResponse::getSelected).forEach(item ->
                productSubtotal.merge(item.getProductId(), item.getSubtotalAmount(), BigDecimal::add));
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

    /**
     * 秒杀每人限购校验（加购 / 改数量时）。
     *
     * <p><b>为什么要在购物车层就拦</b>：限购是稳定且因人而异的硬约束。若只在结算时校验，用户会在
     * 购物车里把数量加到远超上限、一路填完地址、点下结算才被打回 —— 白费操作且不知道错在哪。
     * 服务端这一层同时覆盖前端管不住的几条路径：多开标签页、直调接口，以及游客登录后合并购物车
     * （{@code mergeGuestCartToServer} 走的也是本接口）。
     *
     * <p><b>只拦每人限购，不拦全站剩余名额</b>：剩余名额是所有用户共享且随时在变的，拿它做硬拦会出现
     * 「名额被别人抢走一件，连把自己购物车里的数量改小都被拒」。剩余名额仍由下单时的原子扣减兜底。
     *
     * @param targetQuantity 本次操作后、该商品在本用户购物车里的总件数
     */
    private void validateFlashLimit(Long userId, Product product, int targetQuantity) {
        FlashSale sale = flashSaleService.runningByProductIds(List.of(product.getId()))
                .get(product.getId());
        if (sale == null) {
            return;   // 该商品此刻没有进行中的秒杀，不受限购约束
        }
        Integer remaining = flashSaleService.remainingForUser(userId, sale);
        if (remaining == null) {
            return;   // 该场次不限购
        }
        if (targetQuantity <= remaining) {
            return;
        }
        // 文案必须说清「上限卡在哪」：remaining 只扣了订单占用，没扣购物车。
        // 若只说「你还能买 N 件」，而用户购物车里本来就放着 N 件，就是自相矛盾 ——
        // 所以这里给出「购物车里最多放几件」，并在有订单占用时说明占用了多少。
        long bought = flashSaleService.quotaFor(userId, sale).bought();
        String cap = "「" + sale.getName() + "」每人限购 " + sale.getPerUserLimit() + " 件";
        throw new BusinessException(409, remaining == 0
                ? cap + "，你已经买满了"
                : cap + "，购物车里最多放 " + remaining + " 件"
                        + (bought > 0 ? "（已下单占用 " + bought + " 件）" : ""));
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
