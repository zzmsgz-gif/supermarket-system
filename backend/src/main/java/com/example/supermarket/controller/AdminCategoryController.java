package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.AdminCategoryRequest;
import com.example.supermarket.dto.CategoryResponse;
import com.example.supermarket.dto.CategoryStatusRequest;
import com.example.supermarket.service.AdminCategoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/categories")
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    public AdminCategoryController(AdminCategoryService adminCategoryService) {
        this.adminCategoryService = adminCategoryService;
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> listCategories() {
        return ApiResponse.ok(adminCategoryService.listCategories());
    }

    @PostMapping
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody AdminCategoryRequest request) {
        return ApiResponse.ok(adminCategoryService.createCategory(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody AdminCategoryRequest request
    ) {
        return ApiResponse.ok(adminCategoryService.updateCategory(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<CategoryResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody CategoryStatusRequest request
    ) {
        return ApiResponse.ok(adminCategoryService.updateStatus(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        adminCategoryService.deleteCategory(id);
        return ApiResponse.ok();
    }
}
