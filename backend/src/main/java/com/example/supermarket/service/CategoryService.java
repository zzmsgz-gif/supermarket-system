package com.example.supermarket.service;

import com.example.supermarket.dto.CategoryResponse;
import com.example.supermarket.repository.ProductCategoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final ProductCategoryRepository categoryRepository;

    public CategoryService(ProductCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listEnabledCategories() {
        return categoryRepository.findByStatusAndDeletedOrderBySortNoAscIdAsc((byte) 1, (byte) 0)
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
