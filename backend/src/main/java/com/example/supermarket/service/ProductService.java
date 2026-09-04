package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.ProductAttributeResponse;
import com.example.supermarket.dto.ProductDetailResponse;
import com.example.supermarket.dto.ProductImageResponse;
import com.example.supermarket.dto.ProductSkuResponse;
import com.example.supermarket.dto.ProductSummaryResponse;
import com.example.supermarket.entity.PageDwell;
import com.example.supermarket.entity.Product;
import com.example.supermarket.entity.ProductCategory;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.ProductAttributeRepository;
import com.example.supermarket.repository.ProductCategoryRepository;
import com.example.supermarket.repository.ProductImageRepository;
import com.example.supermarket.repository.ProductRepository;
import com.example.supermarket.repository.PageDwellRepository;
import com.example.supermarket.repository.ProductSkuRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProductService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String ON_SALE = "ON_SALE";
    private static final byte NOT_DELETED = 0;

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductImageRepository imageRepository;
    private final ProductSkuRepository skuRepository;
    private final ProductAttributeRepository attributeRepository;
    private final PageDwellRepository dwellRepository;

    public ProductService(
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository,
            ProductImageRepository imageRepository,
            ProductSkuRepository skuRepository,
            ProductAttributeRepository attributeRepository,
            PageDwellRepository dwellRepository
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.imageRepository = imageRepository;
        this.skuRepository = skuRepository;
        this.attributeRepository = attributeRepository;
        this.dwellRepository = dwellRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> searchProducts(
            int page, int size, Long categoryId, String keyword,
            BigDecimal minPrice, BigDecimal maxPrice, String brand, String sort
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, resolveSort(sort));
        List<Long> categoryIds = expandCategoryIds(categoryId);
        Page<Product> products = productRepository.findAll(
                buildPublicSearchSpec(categoryIds, keyword, minPrice, maxPrice, brand), pageable);
        List<ProductSummaryResponse> items = products.getContent().stream()
                .map(ProductSummaryResponse::from).toList();
        return PageResponse.of(items, safePage, safeSize, products.getTotalElements());
    }

    @Cacheable(cacheNames = "productDetail", key = "#id")
    @Transactional(readOnly = true)
    public ProductDetailResponse getPublicProduct(Long id) {
        Product product = productRepository.findByIdAndStatusAndDeleted(id, ON_SALE, NOT_DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        ProductDetailResponse detail = ProductDetailResponse.from(product);
        detail.setImages(imageRepository.findByProductIdAndDeletedOrderBySortNoAscIdAsc(id, NOT_DELETED).stream()
                .map(ProductImageResponse::from).collect(Collectors.toList()));
        detail.setSkus(skuRepository.findByProductIdAndDeletedOrderBySortNoAscIdAsc(id, NOT_DELETED).stream()
                .map(ProductSkuResponse::from).collect(Collectors.toList()));
        detail.setAttributes(attributeRepository.findByProductIdAndDeletedOrderBySortNoAscIdAsc(id, NOT_DELETED).stream()
                .map(ProductAttributeResponse::from).collect(Collectors.toList()));
        return detail;
    }

    @Cacheable(cacheNames = "hotProducts")
    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> listHot(int limit) {
        return productRepository.findByStatusAndDeletedAndIsHot(
                        ON_SALE, NOT_DELETED, (byte) 1,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "sales")))
                .stream().map(ProductSummaryResponse::from).toList();
    }

    @Cacheable(cacheNames = "newProducts")
    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> listNew(int limit) {
        return productRepository.findByStatusAndDeletedAndIsNew(
                        ON_SALE, NOT_DELETED, (byte) 1,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream().map(ProductSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> listRelated(Long id, int limit) {
        Product product = productRepository.findByIdAndStatusAndDeleted(id, ON_SALE, NOT_DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        List<String> baseTags = splitTags(product.getTags());
        List<Product> candidates = productRepository.findByStatusAndDeletedAndCategoryIdAndIdNot(
                ON_SALE, NOT_DELETED, product.getCategoryId(), id,
                PageRequest.of(0, Math.max(limit * 4, 20), Sort.by(Sort.Direction.DESC, "sales")));
        return candidates.stream()
                .map(p -> new AbstractMap.SimpleEntry<>(p, relatedScore(p, baseTags)))
                .sorted(Comparator.<Map.Entry<Product, Double>>comparingDouble(Map.Entry::getValue).reversed()
                        .thenComparing(e -> e.getKey().getSales(), Comparator.reverseOrder()))
                .limit(limit)
                .map(e -> ProductSummaryResponse.from(e.getKey()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> guessYouLike(Long userId, int limit) {
        if (userId != null) {
            List<PageDwell> dwells = dwellRepository.findByUserIdOrderByIdDesc(userId);
            if (!dwells.isEmpty()) {
                List<Long> dwProductIds = dwells.stream().map(PageDwell::getProductId)
                        .filter(p -> p != null).distinct().toList();
                Map<Long, Product> dwMap = productRepository.findAllById(dwProductIds).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
                Map<Long, Integer> categoryWeight = new LinkedHashMap<>();
                Map<String, Integer> tagWeight = new LinkedHashMap<>();
                for (PageDwell d : dwells) {
                    Product p = dwMap.get(d.getProductId());
                    if (p == null) {
                        continue;
                    }
                    categoryWeight.merge(p.getCategoryId(), 1, Integer::sum);
                    for (String t : splitTags(p.getTags())) {
                        tagWeight.merge(t, 1, Integer::sum);
                    }
                }
                List<Product> candidates = productRepository.findByStatusAndDeleted(
                        ON_SALE, NOT_DELETED, PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "sales")));
                List<Product> scored = candidates.stream()
                        .map(p -> new AbstractMap.SimpleEntry<>(p, guessScore(p, categoryWeight, tagWeight)))
                        .filter(e -> e.getValue() > 0)
                        .sorted(Comparator.<Map.Entry<Product, Double>>comparingDouble(Map.Entry::getValue).reversed()
                                .thenComparing(e -> e.getKey().getSales(), Comparator.reverseOrder()))
                        .limit(limit)
                        .map(Map.Entry::getKey)
                        .toList();
                if (!scored.isEmpty()) {
                    return scored.stream().map(ProductSummaryResponse::from).toList();
                }
            }
        }
        // 兜底：热门 + 新品
        List<Product> fallback = new ArrayList<>();
        fallback.addAll(productRepository.findByStatusAndDeletedAndIsHot(
                ON_SALE, NOT_DELETED, (byte) 1, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "sales"))));
        fallback.addAll(productRepository.findByStatusAndDeletedAndIsNew(
                ON_SALE, NOT_DELETED, (byte) 1, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))));
        LinkedHashMap<Long, Product> dedup = new LinkedHashMap<>();
        for (Product p : fallback) {
            dedup.putIfAbsent(p.getId(), p);
        }
        return dedup.values().stream().limit(limit).map(ProductSummaryResponse::from).toList();
    }

    private double relatedScore(Product p, List<String> baseTags) {
        double score = 1.0;
        for (String t : splitTags(p.getTags())) {
            if (baseTags.contains(t)) {
                score += 1.5;
            }
        }
        if (Byte.valueOf((byte) 1).equals(p.getIsHot())) {
            score += 2.0;
        }
        if (Byte.valueOf((byte) 1).equals(p.getIsNew())) {
            score += 1.0;
        }
        score += Math.min(p.getSales(), 1000) * 0.001;
        return score;
    }

    private double guessScore(Product p, Map<Long, Integer> categoryWeight, Map<String, Integer> tagWeight) {
        double score = 0.0;
        Integer cw = categoryWeight.get(p.getCategoryId());
        if (cw != null) {
            score += cw * 2.0;
        }
        for (String t : splitTags(p.getTags())) {
            Integer tw = tagWeight.get(t);
            if (tw != null) {
                score += tw * 1.5;
            }
        }
        if (Byte.valueOf((byte) 1).equals(p.getIsHot())) {
            score += 0.5;
        }
        if (Byte.valueOf((byte) 1).equals(p.getIsNew())) {
            score += 0.3;
        }
        return score;
    }

    private List<String> splitTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return List.of();
        }
        return java.util.Arrays.stream(tags.split("[,，\\s]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private Sort resolveSort(String sort) {
        if ("price_asc".equals(sort)) {
            return Sort.by(Sort.Direction.ASC, "price");
        }
        if ("price_desc".equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "price");
        }
        if ("sales_desc".equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "sales");
        }
        if ("new_desc".equals(sort) || "newest".equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return Sort.by(Sort.Direction.DESC, "sortNo").and(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private List<Long> expandCategoryIds(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        List<ProductCategory> all = categoryRepository.findByDeletedOrderBySortNoAscIdAsc(NOT_DELETED);
        Map<Long, List<Long>> childrenMap = new LinkedHashMap<>();
        for (ProductCategory c : all) {
            childrenMap.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c.getId());
        }
        Set<Long> result = new HashSet<>();
        collectDescendants(categoryId, childrenMap, result);
        return new ArrayList<>(result);
    }

    private void collectDescendants(Long id, Map<Long, List<Long>> childrenMap, Set<Long> acc) {
        if (!acc.add(id)) {
            return;
        }
        for (Long child : childrenMap.getOrDefault(id, new ArrayList<>())) {
            collectDescendants(child, childrenMap, acc);
        }
    }

    private Specification<Product> buildPublicSearchSpec(
            List<Long> categoryIds, String keyword, BigDecimal minPrice, BigDecimal maxPrice, String brand
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), ON_SALE));
            predicates.add(cb.equal(root.get("deleted"), NOT_DELETED));

            if (categoryIds != null && !categoryIds.isEmpty()) {
                predicates.add(root.get("categoryId").in(categoryIds));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            if (StringUtils.hasText(brand)) {
                predicates.add(cb.equal(cb.lower(root.get("brand")), brand.trim().toLowerCase()));
            }
            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                Predicate nameLike = cb.like(cb.lower(root.get("name")), pattern);
                Predicate skuLike = cb.like(cb.lower(root.get("sku")), pattern);
                predicates.add(cb.or(nameLike, skuLike));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
