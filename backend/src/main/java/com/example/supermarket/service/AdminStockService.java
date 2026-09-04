package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.StockAlertResponse;
import com.example.supermarket.dto.StockLogResponse;
import com.example.supermarket.entity.Product;
import com.example.supermarket.entity.StockLog;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminStockService {

    private static final int MAX_PAGE_SIZE = 100;

    private final com.example.supermarket.repository.StockLogRepository stockLogRepository;
    private final com.example.supermarket.repository.ProductRepository productRepository;

    public AdminStockService(
            com.example.supermarket.repository.StockLogRepository stockLogRepository,
            com.example.supermarket.repository.ProductRepository productRepository
    ) {
        this.stockLogRepository = stockLogRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<StockAlertResponse> listLowStockProducts() {
        return productRepository.findLowStockProducts().stream()
                .map(StockAlertResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<StockLogResponse> listStockLogs(int page, int size, Long productId, String bizType) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StockLog> logs = stockLogRepository.findAll(buildSpec(productId, bizType), pageable);
        List<StockLogResponse> items = logs.getContent().stream()
                .map(StockLogResponse::from)
                .toList();
        return PageResponse.of(items, safePage, safeSize, logs.getTotalElements());
    }

    private Specification<StockLog> buildSpec(Long productId, String bizType) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (productId != null) {
                predicates.add(cb.equal(root.get("productId"), productId));
            }
            if (StringUtils.hasText(bizType)) {
                predicates.add(cb.equal(root.get("bizType"), bizType.trim()));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
