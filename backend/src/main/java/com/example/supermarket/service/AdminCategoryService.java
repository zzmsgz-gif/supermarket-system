package com.example.supermarket.service;

import com.example.supermarket.dto.AdminCategoryRequest;
import com.example.supermarket.dto.CategoryResponse;
import com.example.supermarket.dto.CategoryStatusRequest;
import com.example.supermarket.entity.ProductCategory;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.ProductCategoryRepository;
import com.example.supermarket.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
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
        category.setSortNo(request.getSortNo());
        category.setStatus(request.getStatus());
        category.setDeleted(NOT_DELETED);
        return CategoryResponse.from(categoryRepository.save(category));
    }

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
        category.setSortNo(request.getSortNo());
        category.setStatus(request.getStatus());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateStatus(Long id, CategoryStatusRequest request) {
        ProductCategory category = getActiveCategory(id);
        validateStatus(request.getStatus());
        category.setStatus(request.getStatus());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long id) {
        ProductCategory category = getActiveCategory(id);
        if (categoryRepository.existsByParentIdAndDeleted(id, NOT_DELETED)) {
            throw new BusinessException(409, "Category has child categories");
        }
        if (productRepository.existsByCategoryIdAndDeleted(id, NOT_DELETED)) {
            throw new BusinessException(409, "Category has products");
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
}
