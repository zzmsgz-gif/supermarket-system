package com.example.supermarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserRoleRequest {

    @NotBlank(message = "Role is required")
    @Size(max = 20, message = "Role must be at most 20 characters")
    private String role;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
