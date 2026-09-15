package com.example.supermarket.service;

import com.example.supermarket.dto.AdminDashboardStats;
import com.example.supermarket.dto.AdminStatsOverview;
import com.example.supermarket.entity.FlashSale;
import com.example.supermarket.entity.OrderEntity;
import com.example.supermarket.entity.Product;
import com.example.supermarket.entity.ProductCategory;
import com.example.supermarket.repository.FlashSaleRepository;
import com.example.supermarket.repository.OrderItemRepository;
import com.example.supermarket.repository.OrderRepository;
import com.example.supermarket.repository.ProductCategoryRepository;
import com.example.supermarket.repository.ProductRepository;
import com.example.supermarket.repository.SysUserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 经营数据看板。
 *
 * <p>口径与 {@code AdminStatsService} 严格一致（已支付订单、按订单创建时间落在区间内），
 * 两处数字必须能对上，否则会出现"看板说成交 100、首页说成交 80"这种互相打架的情况。
 *
 * <p>环比对照「紧邻的等长上一区间」。上期为 0 时百分比返回 {@code null}（不可比），
 * 前端显示"—"而不是 Infinity。
 */
@Service
public class AdminDashboardService {

    /** 已支付口径：与 AdminStatsService.PAID_STATUSES 保持一致 */
    private static final List<String> PAID_STATUSES = List.of("PAID", "SHIPPED", "COMPLETED");

    private static final String REFUND_APPROVED = "APPROVED";
    private static final byte NOT_DELETED = 0;

    private static final int TOP_PRODUCT_LIMIT = 10;
    private static final int FLASH_SALE_LIMIT = 10;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SysUserRepository sysUserRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final FlashSaleRepository flashSaleRepository;
    private final MemberService memberService;

    public AdminDashboardService(OrderRepository orderRepository,
                                 OrderItemRepository orderItemRepository,
                                 SysUserRepository sysUserRepository,
                                 ProductRepository productRepository,
                                 ProductCategoryRepository categoryRepository,
                                 FlashSaleRepository flashSaleRepository,
                                 MemberService memberService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.sysUserRepository = sysUserRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.flashSaleRepository = flashSaleRepository;
        this.memberService = memberService;
    }

    @Transactional(readOnly = true)
    public AdminDashboardStats dashboard(String rangeParam) {
        Range range = parseRange(rangeParam);
        LocalDate today = LocalDate.now();
        LocalDate fromDate = today.minusDays(range.days() - 1L);
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();
        // 紧邻的等长上一区间
        LocalDateTime prevFrom = from.minusDays(range.days());
        LocalDateTime prevTo = from;

        return new AdminDashboardStats(
                range.key(),
                fromDate,
                today,
                range.days(),
                buildTrade(from, to),
                buildGrowth(from, to, prevFrom, prevTo),
                buildUsers(from, to),
                buildMemberLevels(),
                buildTopProducts(from, to),
                buildCategoryShare(from, to),
                buildTrend(fromDate, today),
                buildFlashSales()
        );
    }

    // ==================== 交易 ====================

    private AdminDashboardStats.Trade buildTrade(LocalDateTime from, LocalDateTime to) {
        BigDecimal gmv = nz(orderRepository.sumPayAmountByStatusInAndCreatedAtBetween(PAID_STATUSES, from, to));
        long paidOrders = orderRepository.countByStatusInAndCreatedAtBetween(PAID_STATUSES, from, to);
        BigDecimal refundAmount = nz(orderRepository.sumRefundedAmountInRange(REFUND_APPROVED, from, to));
        return new AdminDashboardStats.Trade(
                gmv,
                paidOrders,
                avgOrderValue(gmv, paidOrders),
                orderRepository.countByRefundStatusAndRefundedAtBetween(REFUND_APPROVED, from, to),
                refundAmount,
                orderRepository.countByStatus("PAID"),
                orderRepository.countByStatus("PENDING_PAYMENT")
        );
    }

    private AdminDashboardStats.Growth buildGrowth(LocalDateTime from, LocalDateTime to,
                                                   LocalDateTime prevFrom, LocalDateTime prevTo) {
        BigDecimal gmv = nz(orderRepository.sumPayAmountByStatusInAndCreatedAtBetween(PAID_STATUSES, from, to));
        BigDecimal prevGmv = nz(orderRepository.sumPayAmountByStatusInAndCreatedAtBetween(PAID_STATUSES, prevFrom, prevTo));
        long orders = orderRepository.countByStatusInAndCreatedAtBetween(PAID_STATUSES, from, to);
        long prevOrders = orderRepository.countByStatusInAndCreatedAtBetween(PAID_STATUSES, prevFrom, prevTo);
        return new AdminDashboardStats.Growth(
                growthPercent(gmv, prevGmv),
                growthPercent(BigDecimal.valueOf(orders), BigDecimal.valueOf(prevOrders)),
                growthPercent(avgOrderValue(gmv, orders), avgOrderValue(prevGmv, prevOrders))
        );
    }

    // ==================== 用户 ====================

    private AdminDashboardStats.Users buildUsers(LocalDateTime from, LocalDateTime to) {
        long newUsers = sysUserRepository.countByDeletedAndCreatedAtBetween(NOT_DELETED, from, to);
        long buyers = orderRepository.countDistinctBuyersInRange(PAID_STATUSES, from, to);
        long repeatBuyers = orderRepository.findRepeatBuyerIds(PAID_STATUSES, from, to, 2).size();
        return new AdminDashboardStats.Users(newUsers, buyers, repeatBuyers, rate(repeatBuyers, buyers));
    }

    private List<AdminDashboardStats.MemberLevelSlice> buildMemberLevels() {
        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] row : sysUserRepository.countGroupByMemberLevel(NOT_DELETED)) {
            counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        // 固定输出 4 个等级（即使为 0），图表列数才稳定
        List<AdminDashboardStats.MemberLevelSlice> slices = new ArrayList<>(4);
        for (int level = 0; level <= 3; level++) {
            slices.add(new AdminDashboardStats.MemberLevelSlice(
                    level, memberService.tierName(level), counts.getOrDefault(level, 0L)));
        }
        return slices;
    }

    // ==================== 商品 / 品类 ====================

    private List<AdminDashboardStats.ProductSalesSlice> buildTopProducts(LocalDateTime from, LocalDateTime to) {
        return orderItemRepository
                .topProducts(PAID_STATUSES, from, to, PageRequest.of(0, TOP_PRODUCT_LIMIT))
                .stream()
                .map(row -> new AdminDashboardStats.ProductSalesSlice(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        ((Number) row[2]).longValue(),
                        toMoney(row[3])))
                .toList();
    }

    private List<AdminDashboardStats.CategorySlice> buildCategoryShare(LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = orderItemRepository.categoryShare(PAID_STATUSES, from, to);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = categoryNames(rows.stream()
                .map(row -> ((Number) row[0]).longValue()).distinct().toList());
        BigDecimal total = rows.stream().map(row -> toMoney(row[2])).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AdminDashboardStats.CategorySlice> slices = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Long categoryId = ((Number) row[0]).longValue();
            BigDecimal amount = toMoney(row[2]);
            Double percent = total.signum() == 0 ? 0.0
                    : Math.round(amount.doubleValue() * 1000.0 / total.doubleValue()) / 10.0;
            slices.add(new AdminDashboardStats.CategorySlice(
                    categoryId,
                    names.getOrDefault(categoryId, "未分类"),
                    ((Number) row[1]).longValue(),
                    amount,
                    percent));
        }
        return slices;
    }

    private Map<Long, String> categoryNames(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName, (a, b) -> a));
    }

    // ==================== 趋势 ====================

    private List<AdminStatsOverview.TrendPoint> buildTrend(LocalDate fromDate, LocalDate toDate) {
        Map<LocalDate, List<OrderEntity>> byDay = orderRepository
                .findByCreatedAtBetween(fromDate.atStartOfDay(), toDate.plusDays(1).atStartOfDay())
                .stream()
                .collect(Collectors.groupingBy(order -> order.getCreatedAt().toLocalDate()));
        List<AdminStatsOverview.TrendPoint> trend = new ArrayList<>();
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            List<OrderEntity> dayOrders = byDay.getOrDefault(date, List.of());
            BigDecimal sales = dayOrders.stream()
                    .filter(order -> PAID_STATUSES.contains(order.getStatus()))
                    .map(OrderEntity::getPayAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            trend.add(new AdminStatsOverview.TrendPoint(date, dayOrders.size(), sales));
        }
        return trend;
    }

    // ==================== 秒杀 ====================

    private List<AdminDashboardStats.FlashSaleSlice> buildFlashSales() {
        List<FlashSale> sales = flashSaleRepository.findAll().stream()
                .filter(sale -> NOT_DELETED == (sale.getDeleted() == null ? (byte) 1 : sale.getDeleted()))
                .toList();
        if (sales.isEmpty()) {
            return List.of();
        }
        Map<Long, long[]> quantityBySale = new HashMap<>();
        Map<Long, BigDecimal> amountBySale = new HashMap<>();
        for (Object[] row : orderItemRepository.flashSaleSales(PAID_STATUSES)) {
            Long saleId = ((Number) row[0]).longValue();
            quantityBySale.put(saleId, new long[]{((Number) row[1]).longValue()});
            amountBySale.put(saleId, toMoney(row[2]));
        }
        Map<Long, Product> products = productRepository.findAllById(
                        sales.stream().map(FlashSale::getProductId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));

        List<AdminDashboardStats.FlashSaleSlice> slices = new ArrayList<>();
        for (FlashSale sale : sales) {
            int total = sale.getTotalQuota() == null ? 0 : sale.getTotalQuota();
            int sold = sale.getSoldQuota() == null ? 0 : sale.getSoldQuota();
            long[] qty = quantityBySale.getOrDefault(sale.getId(), new long[]{0L});
            Product product = products.get(sale.getProductId());
            slices.add(new AdminDashboardStats.FlashSaleSlice(
                    sale.getId(),
                    sale.getName(),
                    product == null ? "（商品已删除）" : product.getName(),
                    sale.getFlashPrice(),
                    total,
                    sold,
                    total <= 0 ? 0.0 : Math.round(sold * 1000.0 / total) / 10.0,
                    qty[0],
                    amountBySale.getOrDefault(sale.getId(), BigDecimal.ZERO)));
        }
        slices.sort((a, b) -> Integer.compare(b.soldQuota(), a.soldQuota()));
        return slices.size() > FLASH_SALE_LIMIT ? slices.subList(0, FLASH_SALE_LIMIT) : slices;
    }

    // ==================== 工具 ====================

    private record Range(String key, int days) {
    }

    private Range parseRange(String raw) {
        String key = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "today", "1d", "1" -> new Range("today", 1);
            case "30d", "30" -> new Range("30d", 30);
            case "90d", "90" -> new Range("90d", 90);
            default -> new Range("7d", 7);
        };
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal toMoney(Object raw) {
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        return raw instanceof BigDecimal decimal
                ? decimal
                : BigDecimal.valueOf(((Number) raw).doubleValue());
    }

    private static BigDecimal avgOrderValue(BigDecimal gmv, long orders) {
        if (orders <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return gmv.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP);
    }

    /** 百分比（保留 1 位）；分母为 0 时返回 null 表示不可比 */
    private static Double rate(long part, long total) {
        if (total <= 0) {
            return null;
        }
        return Math.round(part * 1000.0 / total) / 10.0;
    }

    private static Double growthPercent(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        double percent = current.subtract(previous).doubleValue() * 100.0 / previous.doubleValue();
        return Math.round(percent * 10.0) / 10.0;
    }
}
