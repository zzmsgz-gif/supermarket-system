package com.example.supermarket.service;

import com.example.supermarket.dto.AdminStatsOverview;
import com.example.supermarket.entity.OrderEntity;
import com.example.supermarket.repository.OrderRepository;
import com.example.supermarket.repository.ProductRepository;
import com.example.supermarket.repository.SysUserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 后台数据统计聚合：全部走数据库聚合查询，
 * 与分页列表解耦，避免「拿第一页数据凑 KPI」导致的不准。
 */
@Service
public class AdminStatsService {

    /** 已支付口径：与前端成交额统计一致 */
    private static final List<String> PAID_STATUSES = List.of("PAID", "SHIPPED", "COMPLETED");

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final SysUserRepository sysUserRepository;

    public AdminStatsService(OrderRepository orderRepository,
            ProductRepository productRepository,
            SysUserRepository sysUserRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.sysUserRepository = sysUserRepository;
    }

    @Transactional(readOnly = true)
    public AdminStatsOverview overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        Map<String, Long> statusDistribution = new LinkedHashMap<>();
        for (Object[] row : orderRepository.countGroupByStatus()) {
            statusDistribution.put((String) row[0], (Long) row[1]);
        }
        long orderTotal = statusDistribution.values().stream().mapToLong(Long::longValue).sum();

        return new AdminStatsOverview(
                productRepository.countOnSaleProducts(),
                productRepository.sumOnSaleStock(),
                sysUserRepository.count(),
                orderTotal,
                orderRepository.sumPayAmountByStatusIn((Collection<String>) PAID_STATUSES),
                orderRepository.countByCreatedAtAfter(todayStart),
                orderRepository.sumPayAmountByStatusInAndCreatedAtAfter((Collection<String>) PAID_STATUSES, todayStart),
                statusDistribution.getOrDefault("PAID", 0L),
                statusDistribution.getOrDefault("PENDING_PAYMENT", 0L),
                orderRepository.countByRefundStatus("APPLYING"),
                statusDistribution,
                buildSalesTrend()
        );
    }

    /** 近 7 天成交趋势：缺数的日子补 0，保证折线 x 轴连续 */
    private List<AdminStatsOverview.TrendPoint> buildSalesTrend() {
        LocalDate today = LocalDate.now();
        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();
        Map<LocalDate, List<OrderEntity>> byDay = orderRepository.findByCreatedAtAfter(weekStart)
                .stream()
                .collect(Collectors.groupingBy(order -> order.getCreatedAt().toLocalDate()));

        List<AdminStatsOverview.TrendPoint> trend = new ArrayList<>(7);
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            List<OrderEntity> dayOrders = byDay.getOrDefault(date, List.of());
            BigDecimal sales = dayOrders.stream()
                    .filter(order -> PAID_STATUSES.contains(order.getStatus()))
                    .map(OrderEntity::getPayAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            trend.add(new AdminStatsOverview.TrendPoint(date, dayOrders.size(), sales));
        }
        return trend;
    }
}
