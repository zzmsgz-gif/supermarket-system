package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.PriceAlertResponse;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.FavoriteService;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 降价提醒：收藏商品的现价低于收藏时价格时产生 */
@RestController
@RequestMapping("/price-alerts")
public class PriceAlertController {

    private final FavoriteService favoriteService;

    public PriceAlertController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PriceAlertResponse>> list(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return ApiResponse.ok(favoriteService.listAlerts(currentUser.getId(), page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(Map.of("count", favoriteService.unreadCount(currentUser.getId())));
    }

    @PostMapping("/read")
    public ApiResponse<Map<String, Object>> markRead(@AuthenticationPrincipal CurrentUser currentUser) {
        int updated = favoriteService.markAllRead(currentUser.getId());
        return ApiResponse.ok(Map.of("updated", updated));
    }
}
