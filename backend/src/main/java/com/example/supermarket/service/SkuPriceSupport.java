package com.example.supermarket.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.supermarket.entity.ProductSku;
import com.example.supermarket.repository.ProductSkuRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 规格价解析。
 *
 * <p>购物车 / 订单里存的只是规格文本（前端 {@code selectedSpecText}，形如 {@code "规格:550ml"}，
 * 多维时空格分隔），要拿到该规格的售价，得先反查回对应的 SKU 行。
 *
 * <p>约定：SKU 的 {@code price} 为 NULL 表示该规格跟随商品基准价 —— 这里也就返回 null，
 * 由调用方回落 {@code product.getPrice()}，保证老数据（只有规格、不分价）行为不变。
 */
@Service
public class SkuPriceSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final byte NOT_DELETED = 0;

    private final ProductSkuRepository skuRepository;

    public SkuPriceSupport(ProductSkuRepository skuRepository) {
        this.skuRepository = skuRepository;
    }

    /**
     * @return 该规格的售价；规格未设价 / 匹配不到 / 无规格文本时返回 null（= 用商品基准价）
     */
    public BigDecimal priceOf(Long productId, String skuSpec) {
        Map<String, String> wanted = parseSpecText(skuSpec);
        if (wanted.isEmpty()) {
            return null;
        }
        List<ProductSku> skus = skuRepository.findByProductIdAndDeletedOrderBySortNoAscIdAsc(productId, NOT_DELETED);
        for (ProductSku sku : skus) {
            if (sku.getPrice() == null) {
                continue;
            }
            if (parseSpecJson(sku.getSpecJson()).equals(wanted)) {
                return sku.getPrice();
            }
        }
        return null;
    }

    /** "规格:550ml" / "颜色:红 规格:M" → Map */
    private Map<String, String> parseSpecText(String text) {
        Map<String, String> map = new HashMap<>();
        if (text == null || text.isBlank()) {
            return map;
        }
        for (String token : text.trim().split("\\s+")) {
            int idx = token.indexOf(':');
            if (idx > 0 && idx < token.length() - 1) {
                map.put(token.substring(0, idx), token.substring(idx + 1));
            }
        }
        return map;
    }

    /** spec_json（{"规格":"550ml"}）→ Map；解析失败按空处理（该行不会被误匹配） */
    private Map<String, String> parseSpecJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = MAPPER.readValue(json, MAP_TYPE);
            Map<String, String> map = new HashMap<>();
            raw.forEach((k, v) -> map.put(k, v == null ? "" : String.valueOf(v)));
            return map;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
