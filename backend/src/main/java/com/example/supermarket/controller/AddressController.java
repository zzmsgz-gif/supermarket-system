package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.AddressRequest;
import com.example.supermarket.dto.AddressResponse;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.AddressService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ApiResponse<List<AddressResponse>> listAddresses(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(addressService.listAddresses(currentUser.getId()));
    }

    @PostMapping
    public ApiResponse<AddressResponse> createAddress(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody AddressRequest request
    ) {
        return ApiResponse.ok(addressService.createAddress(currentUser.getId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AddressResponse> updateAddress(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request
    ) {
        return ApiResponse.ok(addressService.updateAddress(currentUser.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAddress(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id
    ) {
        addressService.deleteAddress(currentUser.getId(), id);
        return ApiResponse.ok();
    }

    @PatchMapping("/{id}/default")
    public ApiResponse<AddressResponse> setDefaultAddress(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(addressService.setDefaultAddress(currentUser.getId(), id));
    }
}
