package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.FavoriteResponse;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.FavoriteService;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public ApiResponse<PageResponse<FavoriteResponse>> list(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return ApiResponse.ok(favoriteService.listFavorites(currentUser.getId(), page, size));
    }

    /** 一次性返回全部已收藏商品 id，供商品卡渲染心形状态 */
    @GetMapping("/ids")
    public ApiResponse<List<Long>> ids(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(favoriteService.favoriteIds(currentUser.getId()));
    }

    @PostMapping("/{productId}")
    public ApiResponse<FavoriteResponse> add(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long productId
    ) {
        return ApiResponse.ok(favoriteService.add(currentUser.getId(), productId));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Map<String, Object>> remove(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long productId
    ) {
        boolean removed = favoriteService.remove(currentUser.getId(), productId);
        return ApiResponse.ok(Map.of("removed", removed, "productId", productId));
    }
}
