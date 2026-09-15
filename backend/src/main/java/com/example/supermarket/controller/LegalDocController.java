package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.LegalDocResponse;
import com.example.supermarket.service.LegalDocService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 协议 / 隐私政策正文：游客可读（注册页勾选前就要能看到）。 */
@RestController
@RequestMapping("/legal-docs")
public class LegalDocController {

    private final LegalDocService legalDocService;

    public LegalDocController(LegalDocService legalDocService) {
        this.legalDocService = legalDocService;
    }

    @GetMapping
    public ApiResponse<List<LegalDocResponse>> list() {
        return ApiResponse.ok(legalDocService.listPublic());
    }

    @GetMapping("/{docKey}")
    public ApiResponse<LegalDocResponse> get(@PathVariable String docKey) {
        return ApiResponse.ok(legalDocService.getPublic(docKey));
    }
}
