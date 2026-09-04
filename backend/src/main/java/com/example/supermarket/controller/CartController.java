package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.AddCartItemRequest;
import com.example.supermarket.dto.CartResponse;
import com.example.supermarket.dto.SelectCartItemsRequest;
import com.example.supermarket.dto.UpdateCartItemRequest;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.CartService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<CartResponse> getCart(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(cartService.getCart(currentUser.getId()));
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> addItem(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        return ApiResponse.ok(cartService.addItem(currentUser.getId(), request));
    }

    @PutMapping("/items/{id}")
    public ApiResponse<CartResponse> updateItem(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        return ApiResponse.ok(cartService.updateItem(currentUser.getId(), id, request));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<CartResponse> deleteItem(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(cartService.deleteItem(currentUser.getId(), id));
    }

    @PatchMapping("/items/selection")
    public ApiResponse<CartResponse> updateSelection(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody SelectCartItemsRequest request
    ) {
        return ApiResponse.ok(cartService.updateSelection(currentUser.getId(), request));
    }


}
