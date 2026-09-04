package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.AdminProductCreateRequest;
import com.example.supermarket.dto.AdminProductUpdateRequest;
import com.example.supermarket.dto.ProductAttributeRequest;
import com.example.supermarket.dto.ProductAttributeResponse;
import com.example.supermarket.dto.ProductDetailResponse;
import com.example.supermarket.dto.ProductImageRequest;
import com.example.supermarket.dto.ProductImageResponse;
import com.example.supermarket.dto.ProductSkuRequest;
import com.example.supermarket.dto.ProductSkuResponse;
import com.example.supermarket.dto.ProductStatusRequest;
import com.example.supermarket.dto.ProductSummaryResponse;
import com.example.supermarket.dto.StockAdjustmentRequest;
import com.example.supermarket.dto.StockLogResponse;
import com.example.supermarket.entity.Product;
import com.example.supermarket.entity.ProductAttribute;
import com.example.supermarket.entity.ProductImage;
import com.example.supermarket.entity.ProductSku;
import com.example.supermarket.entity.StockLog;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.ProductAttributeRepository;
import com.example.supermarket.repository.ProductCategoryRepository;
import com.example.supermarket.repository.ProductImageRepository;
import com.example.supermarket.repository.ProductRepository;
import com.example.supermarket.repository.ProductSkuRepository;
import com.example.supermarket.repository.StockLogRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminProductService {

    private static final byte NOT_DELETED = 0;
    private static final byte DELETED = 1;
    private static final String STATUS_ON_SALE = "ON_SALE";
    private static final String STATUS_OFF_SALE = "OFF_SALE";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final Set<String> ALLOWED_STATUSES = Set.of(STATUS_ON_SALE, STATUS_OFF_SALE, STATUS_DRAFT);
    private static final Set<String> ALLOWED_BIZ_TYPES = Set.of("INIT", "PURCHASE", "ORDER_DEDUCT", "CANCEL_RETURN", "MANUAL");
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final StockLogRepository stockLogRepository;
    private final ProductImageRepository imageRepository;
    private final ProductSkuRepository skuRepository;
    private final ProductAttributeRepository attributeRepository;

    public AdminProductService(
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository,
            StockLogRepository stockLogRepository,
            ProductImageRepository imageRepository,
            ProductSkuRepository skuRepository,
            ProductAttributeRepository attributeRepository
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.stockLogRepository = stockLogRepository;
        this.imageRepository = imageRepository;
        this.skuRepository = skuRepository;
        this.attributeRepository = attributeRepository;
    }

    /**
     * Admin product list. Unlike the public list this one ignores the sale status
     * so off-shelf products stay reachable and can be put back on sale.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> listProducts(int page, int size, String status, String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> products = productRepository.findAll(buildAdminSearchSpec(status, keyword), pageable);
        List<ProductSummaryResponse> items = products.getContent()
                .stream()
                .map(ProductSummaryResponse::from)
                .toList();
        return PageResponse.of(items, safePage, safeSize, products.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProduct(Long id) {
        return enrich(getActiveProduct(id));
    }

    @Transactional
    public ProductDetailResponse createProduct(AdminProductCreateRequest request) {
        String sku = normalize(request.getSku());
        validateSkuAvailable(sku, null);
        validateCategory(request.getCategoryId());
        validateStatus(request.getStatus());

        Product product = new Product();
        applyCreate(product, request, sku);
        product.setSales(0);
        product.setDeleted(NOT_DELETED);
        Product saved = productRepository.save(product);
        persistImages(saved.getId(), request.getImages());
        persistSkus(saved.getId(), request.getSkus());
        persistAttributes(saved.getId(), request.getAttributes());
        return enrich(saved);
    }

    @Transactional
    public ProductDetailResponse updateProduct(Long id, AdminProductUpdateRequest request) {
        Product product = getActiveProduct(id);
        String sku = normalize(request.getSku());
        validateSkuAvailable(sku, id);
        validateCategory(request.getCategoryId());

        product.setCategoryId(request.getCategoryId());
        product.setSku(sku);
        product.setName(normalize(request.getName()));
        product.setSubtitle(trimToNull(request.getSubtitle()));
        product.setDescription(trimToNull(request.getDescription()));
        product.setCoverUrl(trimToNull(request.getCoverUrl()));
        product.setPrice(request.getPrice());
        validatePrices(request.getPrice(), request.getOriginalPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setUnit(normalize(request.getUnit()));
        product.setBrand(trimToNull(request.getBrand()));
        product.setIsHot(request.getIsHot() == null ? product.getIsHot() : request.getIsHot());
        product.setIsNew(request.getIsNew() == null ? product.getIsNew() : request.getIsNew());
        product.setTags(trimToNull(request.getTags()));
        productRepository.save(product);

        // 编辑商品时允许直接设定库存（绝对值）。仅当请求显式携带 stock 时生效，
        // 与「入库」(增量调整) 互补；差额通过库存调整写入并校验非负。
        if (request.getStock() != null) {
            int target = request.getStock();
            if (target < 0) {
                throw new BusinessException(400, "库存不能为负数");
            }
            int delta = target - product.getStock();
            if (delta != 0) {
                productRepository.adjustStock(product.getId(), delta, NOT_DELETED);
                product.setStock(target);
            }
        }

        imageRepository.deleteByProductIdAndDeleted(id, NOT_DELETED);
        skuRepository.deleteByProductIdAndDeleted(id, NOT_DELETED);
        attributeRepository.deleteByProductIdAndDeleted(id, NOT_DELETED);
        persistImages(id, request.getImages());
        persistSkus(id, request.getSkus());
        persistAttributes(id, request.getAttributes());
        return enrich(product);
    }

    @Transactional
    public ProductDetailResponse updateStatus(Long id, ProductStatusRequest request) {
        Product product = getActiveProduct(id);
        String status = normalize(request.getStatus());
        validateStatus(status);
        product.setStatus(status);
        return enrich(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = getActiveProduct(id);
        product.setDeleted(DELETED);
        product.setStatus(STATUS_OFF_SALE);
        productRepository.save(product);
    }

    @Transactional
    public StockLogResponse adjustStock(Long productId, Long operatorId, StockAdjustmentRequest request) {
        Product product = getActiveProduct(productId);
        Integer changeQuantity = request.getChangeQuantity();
        if (changeQuantity == 0) {
            throw new BusinessException(400, "Change quantity cannot be zero");
        }
        String bizType = normalize(request.getBizType());
        if (!ALLOWED_BIZ_TYPES.contains(bizType)) {
            throw new BusinessException(400, "Invalid biz type");
        }
        int stockBefore = product.getStock();
        int stockAfter = stockBefore + changeQuantity;
        if (stockAfter < 0) {
            throw new BusinessException(409, "Stock cannot be negative");
        }
        int updated = productRepository.adjustStock(product.getId(), changeQuantity, NOT_DELETED);
        if (updated == 0) {
            throw new BusinessException(409, "Failed to adjust stock");
        }
        StockLog log = new StockLog();
        log.setProductId(product.getId());
        log.setOrderId(null);
        log.setChangeQuantity(changeQuantity);
        log.setStockBefore(stockBefore);
        log.setStockAfter(stockAfter);
        log.setBizType(bizType);
        log.setOperatorId(operatorId);
        log.setRemark(trimToNull(request.getRemark()));
        return StockLogResponse.from(stockLogRepository.save(log));
    }

    private ProductDetailResponse enrich(Product product) {
        ProductDetailResponse detail = ProductDetailResponse.from(product);
        detail.setImages(imageRepository.findByProductIdAndDeletedOrderBySortNoAscIdAsc(product.getId(), NOT_DELETED)
                .stream().map(ProductImageResponse::from).collect(Collectors.toList()));
        detail.setSkus(skuRepository.findByProductIdAndDeletedOrderBySortNoAscIdAsc(product.getId(), NOT_DELETED)
                .stream().map(ProductSkuResponse::from).collect(Collectors.toList()));
        detail.setAttributes(attributeRepository.findByProductIdAndDeletedOrderBySortNoAscIdAsc(product.getId(), NOT_DELETED)
                .stream().map(ProductAttributeResponse::from).collect(Collectors.toList()));
        return detail;
    }

    private void persistImages(Long productId, List<ProductImageRequest> images) {
        if (images == null) {
            return;
        }
        for (ProductImageRequest r : images) {
            ProductImage e = new ProductImage();
            e.setProductId(productId);
            e.setUrl(r.getUrl());
            e.setSortNo(r.getSortNo() == null ? 0 : r.getSortNo());
            e.setDeleted(NOT_DELETED);
            imageRepository.save(e);
        }
    }

    private void persistSkus(Long productId, List<ProductSkuRequest> skus) {
        if (skus == null) {
            return;
        }
        for (ProductSkuRequest r : skus) {
            ProductSku e = new ProductSku();
            e.setProductId(productId);
            e.setSpecJson(r.getSpecJson());
            e.setSkuCode(r.getSkuCode());
            e.setImage(r.getImage());
            e.setSortNo(r.getSortNo() == null ? 0 : r.getSortNo());
            e.setDeleted(NOT_DELETED);
            skuRepository.save(e);
        }
    }

    private void persistAttributes(Long productId, List<ProductAttributeRequest> attributes) {
        if (attributes == null) {
            return;
        }
        for (ProductAttributeRequest r : attributes) {
            ProductAttribute e = new ProductAttribute();
            e.setProductId(productId);
            e.setAttrName(r.getAttrName());
            e.setAttrValue(r.getAttrValue());
            e.setSortNo(r.getSortNo() == null ? 0 : r.getSortNo());
            e.setDeleted(NOT_DELETED);
            attributeRepository.save(e);
        }
    }

    private Specification<Product> buildAdminSearchSpec(String status, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("deleted"), NOT_DELETED));

            if (StringUtils.hasText(status)) {
                String normalized = normalize(status);
                validateStatus(normalized);
                predicates.add(cb.equal(root.get("status"), normalized));
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

    private Product getActiveProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (product.getDeleted() == null || product.getDeleted() != NOT_DELETED) {
            throw new ResourceNotFoundException("Product not found");
        }
        return product;
    }

    private void validateCategory(Long categoryId) {
        if (!categoryRepository.existsByIdAndDeleted(categoryId, NOT_DELETED)) {
            throw new ResourceNotFoundException("Category not found");
        }
    }

    private void validateSkuAvailable(String sku, Long currentId) {
        boolean exists = currentId == null
                ? productRepository.existsBySkuAndDeleted(sku, NOT_DELETED)
                : productRepository.existsBySkuAndDeletedAndIdNot(sku, NOT_DELETED, currentId);
        if (exists) {
            throw new BusinessException(409, "SKU already exists");
        }
    }

    private void validateStatus(String status) {
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException(400, "Invalid product status");
        }
    }

    /**
     * 原价（划线价）为可选字段，但一旦填写必须不低于现价，避免"原价 < 现价"的逻辑错误数据。
     */
    private void validatePrices(BigDecimal price, BigDecimal originalPrice) {
        if (originalPrice != null && originalPrice.compareTo(price) < 0) {
            throw new BusinessException(400, "原价不能低于现价");
        }
    }

    private void applyCreate(Product product, AdminProductCreateRequest request, String sku) {
        product.setCategoryId(request.getCategoryId());
        product.setSku(sku);
        product.setName(normalize(request.getName()));
        product.setSubtitle(trimToNull(request.getSubtitle()));
        product.setDescription(trimToNull(request.getDescription()));
        product.setCoverUrl(trimToNull(request.getCoverUrl()));
        product.setPrice(request.getPrice());
        validatePrices(request.getPrice(), request.getOriginalPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setStock(request.getStock());
        product.setLowStockThreshold(request.getLowStockThreshold() == null ? 10 : request.getLowStockThreshold());
        product.setUnit(normalize(request.getUnit()));
        product.setStatus(normalize(request.getStatus()));
        product.setBrand(trimToNull(request.getBrand()));
        product.setIsHot(request.getIsHot() == null ? (byte) 0 : request.getIsHot());
        product.setIsNew(request.getIsNew() == null ? (byte) 0 : request.getIsNew());
        product.setTags(trimToNull(request.getTags()));
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, "Required field is blank");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
