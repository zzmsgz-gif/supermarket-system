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
import java.util.ArrayList;
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
        return buildCartResponse(userId, cartItems);
    }

    @Transactional
    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        addItemInternal(userId, request.getProductId(), request.getSkuSpec(), request.getQuantity());
        return getCart(userId);
    }

    /**
     * 加购的核心逻辑，走与正常加购**完全相同**的库存 / 限购 / 在售校验。
     *
     * <p>⚠️ 刻意**不加 {@code @Transactional}**：它要给「再来一单」逐条调用，而那条路径需要
     * 捕获单个商品的失败（已下架 / 售罄 / 超限购）后继续处理其余商品。若这里带事务注解，
     * Spring 会在异常穿出事务边界时把整个事务标记成 rollback-only，外层即便 catch 住也救不回来
     * （提交时会抛 UnexpectedRollbackException，等于整单都失败）。
     * 包内可见即可 —— 只有同包的 OrderService 需要它。
     */
    void addItemInternal(Long userId, Long productId, String skuSpec, int quantity) {
        Product product = getAvailableProduct(productId);
        String spec = normalizeSpec(skuSpec);
        // 关键修复：按 (userId, productId, skuSpec) 定位，不同规格是独立购物车行，
        // 同规格才累加数量，避免「换规格被覆盖 / 数量被偷偷累加」的问题。
        CartItem cartItem = cartItemRepository
                .findByUserIdAndProductIdAndSkuSpec(userId, productId, spec)
                .orElseGet(() -> newCartItem(userId, productId));
        int newQuantity = cartItem.getQuantity() + quantity;
        validateStock(product, newQuantity);
        cartItem.setQuantity(newQuantity);
        cartItem.setSelected(SELECTED);
        cartItem.setSkuSpec(spec);
        cartItemRepository.save(cartItem);
    }

    @Transactional
    public CartResponse updateItem(Long userId, Long itemId, UpdateCartItemRequest request) {
        if (request.getQuantity() == null && request.getSelected() == null) {
            throw new BusinessException(400, "Quantity or selected is required");
        }
        CartItem cartItem = getOwnedCartItem(userId, itemId);
        if (request.getQuantity() != null) {
            // 只在「增加」时才要求商品在售 + 校验库存/限购。理由与下面那条限购注释同源：
            // 商品一旦下架，若连减少数量都拦，用户就被锁在一个自己收拾不了的购物车里
            // （删不掉也改不小），只能看着它卡住结算。而减少数量在任何情况下都不会造成超卖。
            if (request.getQuantity() > cartItem.getQuantity()) {
                Product product = getAvailableProduct(cartItem.getProductId());
                validateStock(product, request.getQuantity());
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

    private CartResponse buildCartResponse(Long userId, List<CartItem> cartItems) {
        if (cartItems.isEmpty()) {
            return new CartResponse(List.of(), 0, BigDecimal.ZERO, BigDecimal.ZERO, null);
        }
        List<Long> productIds = cartItems.stream().map(CartItem::getProductId).distinct().toList();
        Map<Long, Product> productMap = productRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (left, right) -> left, HashMap::new));
        // 限时秒杀：按「此刻进行中」的场次定价，与 OrderService.buildOrderItem 用同一套 min() 规则
        Map<Long, FlashSale> flashSales = flashSaleService.runningByProductIds(productIds);
        // 每个限购场次：用户剩余秒杀名额（= 每人限购 − 历史已购，不含本购物车），不限购为 null。
        // 用于把「超出限购」的件数从秒杀价降级为原价，而不是拒绝加购。
        Map<Long, Integer> flashRemain = new HashMap<>();
        for (FlashSale sale : flashSales.values()) {
            Integer rem = flashSaleService.remainingForUser(userId, sale);
            if (rem != null) {
                flashRemain.put(sale.getProductId(), rem);
            }
        }
        List<CartItemResponse> items = new ArrayList<>();
        for (CartItem item : cartItems) {
            Product product = requireProduct(productMap, item.getProductId());
            FlashSale sale = flashSales.get(item.getProductId());
            Integer flashQty = null;
            if (sale != null && flashRemain.containsKey(item.getProductId())) {
                int rem = flashRemain.get(item.getProductId());
                flashQty = Math.min(item.getQuantity(), Math.max(rem, 0));
                flashRemain.put(item.getProductId(), Math.max(rem - flashQty, 0));
            }
            items.add(CartItemResponse.from(item, product, sale, flashQty));
        }
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
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(409, "该商品已不存在，请刷新页面后重试"));
        // 「不存在」与「已下架」必须分开报：混成一句会让用户对着一个还在列表里的商品
        // 反复怀疑自己看错了（旧商品 id 失效时最容易踩）。
        if (!ON_SALE.equals(product.getStatus())
                || product.getDeleted() == null || product.getDeleted() != NOT_DELETED) {
            throw new BusinessException(409, "该商品已下架，无法加入购物车或增加数量");
        }
        return product;
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
            // ⚠️ 刻意不带商品名：这条消息会被「再来一单」原样当成"跳过原因"回传，
            // 而前端已经在前面拼了商品名，带上会变成「面包（商品「面包」库存不足…）」。
            throw new BusinessException(409, "库存不足（仅剩 " + product.getStock() + " 件），请调整数量");
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
