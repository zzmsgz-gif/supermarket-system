package com.example.supermarket.service;

import com.example.supermarket.dto.AdminCategoryRequest;
import com.example.supermarket.dto.CategoryResponse;
import com.example.supermarket.dto.CategoryStatusRequest;
import com.example.supermarket.entity.Product;
import com.example.supermarket.entity.ProductCategory;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.ProductCategoryRepository;
import com.example.supermarket.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminCategoryService {

    private static final byte ENABLED = 1;
    private static final byte DISABLED = 0;
    private static final byte NOT_DELETED = 0;
    private static final byte DELETED = 1;
    private static final long ROOT_PARENT_ID = 0L;

    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public AdminCategoryService(
            ProductCategoryRepository categoryRepository,
            ProductRepository productRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findByDeletedOrderBySortNoAscIdAsc(NOT_DELETED)
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public CategoryResponse createCategory(AdminCategoryRequest request) {
        Long parentId = request.getParentId();
        validateParent(parentId, null);
        validateStatus(request.getStatus());
        String name = normalizeName(request.getName());
        validateNameAvailable(name, parentId, null);

        ProductCategory category = new ProductCategory();
        category.setParentId(parentId);
        category.setName(name);
        category.setIconUrl(normalizeIconUrl(request.getIconUrl()));
        category.setSortNo(request.getSortNo());
        category.setStatus(request.getStatus());
        category.setDeleted(NOT_DELETED);
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public CategoryResponse updateCategory(Long id, AdminCategoryRequest request) {
        ProductCategory category = getActiveCategory(id);
        Long parentId = request.getParentId();
        validateParent(parentId, id);
        validateStatus(request.getStatus());
        String name = normalizeName(request.getName());
        validateNameAvailable(name, parentId, id);

        category.setParentId(parentId);
        category.setName(name);
        category.setIconUrl(normalizeIconUrl(request.getIconUrl()));
        category.setSortNo(request.getSortNo());
        category.setStatus(request.getStatus());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public CategoryResponse updateStatus(Long id, CategoryStatusRequest request) {
        ProductCategory category = getActiveCategory(id);
        validateStatus(request.getStatus());
        category.setStatus(request.getStatus());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public void deleteCategory(Long id) {
        ProductCategory category = getActiveCategory(id);
        if (categoryRepository.existsByParentIdAndDeleted(id, NOT_DELETED)) {
            // message 用中文：这里会原样弹给后台用户看，早期是英文 "Category has child categories"（2026-11-06 改）
            throw new BusinessException(409, "该分类下还有子分类，请先删除或移走子分类再删本分类");
        }
        // ⚠️ 口径：「未删除」——已下架但没删的商品算存在；**秒杀独立商品（kind=FLASH）也算**，
        // 而后者在后台「商品管理」列表里是刻意不显示的（见 AdminProductService.buildAdminSearchSpec）。
        // 于是用户把看得见的商品全删光后仍被拦，且完全不知道还挂着什么（2026-11-07 用户实测：
        // 「中秋团圆」下只剩 1 件限时秒杀商品，列表里根本看不到）。
        // 所以这里分情况给准确提示，重点点明「秒杀商品 + 去限时秒杀删场次（会连带软删商品）」。
        long total = productRepository.countByCategoryIdAndDeleted(id, NOT_DELETED);
        if (total > 0) {
            long flashCount = productRepository.countByCategoryIdAndDeletedAndKind(id, NOT_DELETED, Product.KIND_FLASH);
            if (flashCount > 0) {
                String tip = flashCount == total
                        ? String.format("该分类下还有 %d 件商品，全部是「限时秒杀」商品（商品管理列表不显示这类商品）。"
                                        + "请先到「限时秒杀」删除对应场次，秒杀商品会随之删除，然后再删分类", flashCount)
                        : String.format("该分类下还有 %d 件商品，其中 %d 件是「限时秒杀」商品（商品管理列表不显示）。"
                                        + "请先到「限时秒杀」删除对应场次，并在商品管理里删掉其余商品", total, flashCount);
                throw new BusinessException(409, tip);
            }
            throw new BusinessException(409, String.format(
                    "该分类下还有 %d 件商品（含已下架的商品），请先把商品移到其它分类或删除商品后再删分类", total));
        }
        category.setStatus(DISABLED);
        category.setDeleted(DELETED);
        categoryRepository.save(category);
    }

    private ProductCategory getActiveCategory(Long id) {
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (category.getDeleted() == null || category.getDeleted().byteValue() != NOT_DELETED) {
            throw new ResourceNotFoundException("Category not found");
        }
        return category;
    }

    private void validateParent(Long parentId, Long currentId) {
        if (parentId == null) {
            throw new BusinessException(400, "Parent id is required");
        }
        if (parentId.equals(currentId)) {
            throw new BusinessException(400, "Category cannot be its own parent");
        }
        if (parentId != ROOT_PARENT_ID && !categoryRepository.existsByIdAndDeleted(parentId, NOT_DELETED)) {
            throw new ResourceNotFoundException("Parent category not found");
        }
    }

    private void validateNameAvailable(String name, Long parentId, Long currentId) {
        boolean exists = currentId == null
                ? categoryRepository.existsByNameAndParentIdAndDeleted(name, parentId, NOT_DELETED)
                : categoryRepository.existsByNameAndParentIdAndDeletedAndIdNot(name, parentId, NOT_DELETED, currentId);
        if (exists) {
            throw new BusinessException(409, "Category name already exists under the same parent");
        }
    }

    private void validateStatus(Byte status) {
        if (!Byte.valueOf(ENABLED).equals(status) && !Byte.valueOf(DISABLED).equals(status)) {
            throw new BusinessException(400, "Invalid category status");
        }
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(400, "Category name is required");
        }
        return name.trim();
    }

    /** 图标可空：留空或全空白都存 NULL（前端按「无图」处理，不要给默认占位图）。 */
    private String normalizeIconUrl(String iconUrl) {
        return StringUtils.hasText(iconUrl) ? iconUrl.trim() : null;
    }
}
