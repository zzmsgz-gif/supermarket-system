package com.example.supermarket.service;

import com.example.supermarket.dto.FlashSaleRequest;
import com.example.supermarket.dto.FlashSaleResponse;
import com.example.supermarket.entity.FlashSale;
import com.example.supermarket.entity.OrderItem;
import com.example.supermarket.entity.Product;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.FlashSaleRepository;
import com.example.supermarket.repository.OrderItemRepository;
import com.example.supermarket.repository.ProductRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 限时秒杀。
 *
 * <p>两条关键约定：
 * <ol>
 *   <li><b>秒杀价是「替换订单行单价」，不额外加优惠金额列</b> —— 与会员价同一套口径。
 *       下单时取 {@code min(正常售价, 会员价, 秒杀价)} 作为结算单价，优惠已体现在
 *       {@code order_item.product_price} 里，因此订单金额恒等式不受影响。</li>
 *   <li><b>名额用带条件的 UPDATE 原子扣减</b>（{@link FlashSaleRepository#reserveQuota}），
 *       不能写成"先查剩余再改"，否则并发下会超卖。取消/超时/退款按
 *       {@code order_item.flash_sale_id} 回退。</li>
 * </ol>
 */
@Service
public class FlashSaleService {

    private static final byte NOT_DELETED = 0;
    private static final String ON_SALE = "ON_SALE";

    /** 这些状态的订单不再占用秒杀名额（取消/关闭时会回退），限购校验要排除掉 */
    private static final Set<String> QUOTA_RELEASED_STATUSES = Set.of("CANCELED", "CLOSED");

    /** 未付款订单：同样占着名额（不占就会超卖），但用户可自行支付或取消来释放 */
    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";

    /**
     * 某用户在某秒杀场次的占用情况。
     *
     * @param bought         已占用件数（<b>含未付款订单</b>，用于算「还能买几件」）
     * @param unpaidQuantity 其中来自未付款订单的件数
     * @param unpaidOrderId  最早那笔未付款订单 id（最先超时），无则 null
     * @param unpaidOrderNo  它的订单号，用于前台给出「去支付 / 取消订单」入口
     */
    public record UserQuota(long bought, long unpaidQuantity, Long unpaidOrderId, String unpaidOrderNo) {
        public static final UserQuota NONE = new UserQuota(0, 0, null, null);
    }

    private final FlashSaleRepository flashSaleRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    public FlashSaleService(FlashSaleRepository flashSaleRepository,
                            OrderItemRepository orderItemRepository,
                            ProductRepository productRepository) {
        this.flashSaleRepository = flashSaleRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
    }

    // ==================== 前台 ====================

    /**
     * 前台秒杀列表：进行中 + 即将开始。
     * 只保留商品仍在售且未删除的场次，避免前台点进去是下架商品。
     *
     * @param userId 当前登录用户；传 null（游客）则不填「我的占用」字段
     */
    @Transactional(readOnly = true)
    public List<FlashSaleResponse> listPublic(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, FlashSale> merged = new LinkedHashMap<>();
        for (FlashSale sale : flashSaleRepository.findRunning(NOT_DELETED, FlashSale.ENABLED, now)) {
            merged.put(sale.getId(), sale);
        }
        for (FlashSale sale : flashSaleRepository.findUpcoming(NOT_DELETED, FlashSale.ENABLED, now)) {
            merged.put(sale.getId(), sale);
        }
        if (merged.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> products = productsOf(merged.values().stream()
                .map(FlashSale::getProductId).distinct().toList());
        // 先按商品可售与否过滤，再对真正会返回的场次查用户占用 —— 不为展示不出来的场次白查
        List<FlashSale> visible = merged.values().stream()
                .filter(sale -> available(products.get(sale.getProductId())))
                .toList();
        Map<Long, UserQuota> quotas = userId == null
                ? Map.of()
                : userQuotas(userId, visible.stream().map(FlashSale::getId).toList());
        return visible.stream()
                .map(sale -> {
                    FlashSaleResponse response =
                            FlashSaleResponse.from(sale, products.get(sale.getProductId()), now);
                    if (userId != null) {
                        UserQuota quota = quotas.getOrDefault(sale.getId(), UserQuota.NONE);
                        response.applyUserQuota((int) quota.bought(), (int) quota.unpaidQuantity(),
                                quota.unpaidOrderId(), quota.unpaidOrderNo());
                    }
                    return response;
                })
                .toList();
    }

    /**
     * 批量取某用户在这些场次的占用情况（含未付款订单），供前台透出「我还能买几件」。
     *
     * <p>未登录（userId 为 null）返回空表，响应里相应字段保持 null ＝「不知道」，
     * 前端据此不对游客做限制 —— 游客的购物车在本地，本来也无从统计。
     */
    @Transactional(readOnly = true)
    public Map<Long, UserQuota> userQuotas(Long userId, Collection<Long> flashSaleIds) {
        if (userId == null || flashSaleIds == null || flashSaleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> bought = new HashMap<>();
        for (Object[] row : orderItemRepository.sumFlashQuantityByUserGrouped(
                userId, flashSaleIds, QUOTA_RELEASED_STATUSES)) {
            bought.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        Map<Long, Long> unpaidQty = new HashMap<>();
        Map<Long, Long> unpaidOrderId = new HashMap<>();
        Map<Long, String> unpaidOrderNo = new HashMap<>();
        for (Object[] row : orderItemRepository.findUnpaidFlashHolders(
                userId, flashSaleIds, PENDING_PAYMENT)) {
            Long saleId = ((Number) row[0]).longValue();
            unpaidQty.merge(saleId, ((Number) row[3]).longValue(), Long::sum);
            // 查询已按 order id 升序，putIfAbsent 留下的就是最早那笔（最先超时，最该提醒）
            unpaidOrderId.putIfAbsent(saleId, ((Number) row[1]).longValue());
            unpaidOrderNo.putIfAbsent(saleId, row[2] == null ? null : row[2].toString());
        }
        Map<Long, UserQuota> result = new HashMap<>();
        for (Long saleId : flashSaleIds) {
            result.put(saleId, new UserQuota(
                    bought.getOrDefault(saleId, 0L),
                    unpaidQty.getOrDefault(saleId, 0L),
                    unpaidOrderId.get(saleId),
                    unpaidOrderNo.get(saleId)));
        }
        return result;
    }

    /**
     * 某用户在某场次的占用情况。不限购的场次也允许调用（已购可能本就大于 0），
     * 所以这里不做「是否限购」的判断，交给调用方。
     */
    @Transactional(readOnly = true)
    public UserQuota quotaFor(Long userId, FlashSale sale) {
        if (sale == null || userId == null) {
            return UserQuota.NONE;
        }
        return userQuotas(userId, List.of(sale.getId())).getOrDefault(sale.getId(), UserQuota.NONE);
    }

    /**
     * 某用户在某场次「还能买几件」，不限购时返回 {@code null}（表示无上限）。
     *
     * <p>加购校验、下单校验、前台按钮禁用三处共用这一个口径 —— 各算各的就会出现
     * 「购物车说还能买、下单却报已买满」这种自相矛盾。
     *
     * <p><b>只扣了订单已占用的部分，没有扣购物车里已有的件数</b>：购物车件数由调用方
     * （{@code CartService}）按操作后的目标件数一并判断，报错文案也要带上这个区别，
     * 否则会出现「说还能买 2 件、可购物车里本来就放着 2 件」的对不上。
     */
    @Transactional(readOnly = true)
    public Integer remainingForUser(Long userId, FlashSale sale) {
        if (sale == null || userId == null) {
            return null;
        }
        Integer limit = sale.getPerUserLimit();
        if (limit == null || limit <= 0) {
            return null;
        }
        return (int) Math.max(limit - quotaFor(userId, sale).bought(), 0);
    }

    /**
     * 取这批商品此刻正在进行的秒杀，用于商品列表/详情透出秒杀价。
     * 同一商品万一有多个进行中的场次，取秒杀价最低的那个。
     */
    @Transactional(readOnly = true)
    public Map<Long, FlashSale> runningByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        LocalDateTime now = LocalDateTime.now();
        Map<Long, FlashSale> best = new HashMap<>();
        for (FlashSale sale : flashSaleRepository.findRunningByProductIds(
                NOT_DELETED, FlashSale.ENABLED, productIds, now)) {
            best.merge(sale.getProductId(), sale, (a, b) ->
                    b.getFlashPrice().compareTo(a.getFlashPrice()) < 0 ? b : a);
        }
        return best;
    }

    // ==================== 后台 ====================

    @Transactional(readOnly = true)
    public List<FlashSaleResponse> listAdmin(Integer status) {
        LocalDateTime now = LocalDateTime.now();
        Byte statusFilter = status == null ? null : status.byteValue();
        List<FlashSale> sales = flashSaleRepository.listForAdmin(NOT_DELETED, statusFilter);
        Map<Long, Product> products = productsOf(
                sales.stream().map(FlashSale::getProductId).distinct().toList());
        return sales.stream()
                .map(sale -> FlashSaleResponse.from(sale, products.get(sale.getProductId()), now))
                .toList();
    }

    @Transactional
    public FlashSaleResponse create(FlashSaleRequest request) {
        Product product = requireProduct(request.getProductId());
        validate(request, product, null);
        FlashSale sale = new FlashSale();
        apply(sale, request, product);
        sale.setSoldQuota(0);
        sale.setDeleted(NOT_DELETED);
        if (sale.getStatus() == null) {
            sale.setStatus(FlashSale.ENABLED);
        }
        FlashSale saved = flashSaleRepository.saveAndFlush(sale);
        return FlashSaleResponse.from(saved, product, LocalDateTime.now());
    }

    @Transactional
    public FlashSaleResponse update(Long id, FlashSaleRequest request) {
        FlashSale sale = flashSaleRepository.findByIdAndDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("秒杀场次不存在"));
        Product product = requireProduct(request.getProductId());
        validate(request, product, id);
        int sold = sale.getSoldQuota() == null ? 0 : sale.getSoldQuota();
        if (request.getTotalQuota() < sold) {
            throw new BusinessException(400, "秒杀名额不能小于已抢数量（已抢 " + sold + " 件）");
        }
        apply(sale, request, product);
        FlashSale saved = flashSaleRepository.saveAndFlush(sale);
        return FlashSaleResponse.from(saved, product, LocalDateTime.now());
    }

    @Transactional
    public FlashSaleResponse updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(400, "状态值只能是 0（停用）或 1（启用）");
        }
        FlashSale sale = flashSaleRepository.findByIdAndDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("秒杀场次不存在"));
        sale.setStatus((byte) (int) status);
        FlashSale saved = flashSaleRepository.saveAndFlush(sale);
        Product product = productRepository.findById(saved.getProductId()).orElse(null);
        return FlashSaleResponse.from(saved, product, LocalDateTime.now());
    }

    @Transactional
    public void delete(Long id) {
        FlashSale sale = flashSaleRepository.findByIdAndDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("秒杀场次不存在"));
        sale.setDeleted((byte) 1);
        flashSaleRepository.saveAndFlush(sale);
    }

    // ==================== 下单接入 ====================

    /**
     * 占用名额：先校验每人限购，再用带条件的 UPDATE 原子抢名额。
     * 失败一律抛 409，让下单整体回滚（不会留下半张订单）。
     *
     * @param orderId 当前正在创建的订单 id —— 限购统计要把它排除掉，
     *                因为此时订单行已落库，不排除会把自己算进"已买件数"
     */
    @Transactional
    public void reserveQuota(Long userId, Long orderId, FlashSale sale, int quantity) {
        Integer limit = sale.getPerUserLimit();
        if (limit != null && limit > 0) {
            long bought = orderItemRepository.sumFlashQuantityByUser(
                    userId, sale.getId(), orderId, QUOTA_RELEASED_STATUSES);
            if (bought + quantity > limit) {
                long left = Math.max(limit - bought, 0);
                throw new BusinessException(409, left == 0
                        ? "该秒杀商品你已买满 " + limit + " 件（每人限购）"
                        : "该秒杀商品每人限购 " + limit + " 件，你最多还能买 " + left + " 件");
            }
        }
        if (flashSaleRepository.reserveQuota(sale.getId(), quantity) != 1) {
            throw new BusinessException(409, "「" + sale.getName() + "」名额已被抢完，下次早点来");
        }
    }

    /** 回退某订单占用的秒杀名额（取消 / 超时关闭 / 退款时调用） */
    @Transactional
    public void releaseQuotaForOrder(Long orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderIdAndFlashSaleIdIsNotNull(orderId);
        for (OrderItem item : items) {
            flashSaleRepository.releaseQuota(item.getFlashSaleId(), item.getQuantity());
        }
    }

    // ==================== 内部 ====================

    private Map<Long, Product> productsOf(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
    }

    private boolean available(Product product) {
        return product != null && ON_SALE.equals(product.getStatus())
                && product.getDeleted() != null && product.getDeleted() == NOT_DELETED;
    }

    private Product requireProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("商品不存在"));
        if (product.getDeleted() != null && product.getDeleted() != NOT_DELETED) {
            throw new ResourceNotFoundException("商品不存在");
        }
        return product;
    }

    private void validate(FlashSaleRequest request, Product product, Long excludeId) {
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new BusinessException(400, "开始时间必须早于结束时间");
        }
        if (request.getFlashPrice().compareTo(product.getPrice()) >= 0) {
            throw new BusinessException(400, "秒杀价必须低于商品售价 " + product.getPrice());
        }
        // 同一商品不允许存在时间上重叠的场次，否则价格口径与名额归属都会含糊
        boolean overlap = excludeId == null
                ? flashSaleRepository.existsByProductIdAndDeletedAndStatusAndEndTimeAfter(
                        product.getId(), NOT_DELETED, FlashSale.ENABLED, LocalDateTime.now())
                : flashSaleRepository.existsByProductIdAndDeletedAndStatusAndEndTimeAfterAndIdNot(
                        product.getId(), NOT_DELETED, FlashSale.ENABLED, LocalDateTime.now(), excludeId);
        if (overlap) {
            throw new BusinessException(409, "该商品已有未结束的秒杀场次，请先结束或停用原场次");
        }
    }

    private void apply(FlashSale sale, FlashSaleRequest request, Product product) {
        sale.setProductId(product.getId());
        sale.setName(request.getName() == null || request.getName().isBlank()
                ? product.getName() + " 限时秒杀" : request.getName().trim());
        sale.setFlashPrice(request.getFlashPrice());
        sale.setTotalQuota(request.getTotalQuota());
        sale.setPerUserLimit(request.getPerUserLimit() == null ? 0 : request.getPerUserLimit());
        sale.setStartTime(request.getStartTime());
        sale.setEndTime(request.getEndTime());
        sale.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        if (request.getStatus() != null) {
            sale.setStatus((byte) (int) request.getStatus());
        }
    }

    /** 供其他服务读取当前有效秒杀（按下单口径取最优） */
    @Transactional(readOnly = true)
    public FlashSale bestRunning(Collection<Long> productIds, Long productId) {
        return runningByProductIds(productIds).get(productId);
    }

    /** 已抢名额从多到少，便于后台按热度看 */
    @Transactional(readOnly = true)
    public List<FlashSale> topBySold(int topN) {
        return flashSaleRepository.findAll().stream()
                .filter(sale -> NOT_DELETED == (sale.getDeleted() == null ? (byte) 1 : sale.getDeleted()))
                .sorted(Comparator.comparingInt((FlashSale s) -> s.getSoldQuota() == null ? 0 : s.getSoldQuota())
                        .reversed())
                .limit(topN)
                .toList();
    }
}
