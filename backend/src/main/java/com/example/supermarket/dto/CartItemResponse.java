package com.example.supermarket.dto;

import com.example.supermarket.entity.CartItem;
import com.example.supermarket.entity.FlashSale;
import com.example.supermarket.entity.Product;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CartItemResponse {

    /** 与 CartService / OrderService 同口径：仅「在售且未软删」的商品可下单 */
    private static final String ON_SALE = "ON_SALE";
    private static final byte NOT_DELETED = 0;

    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private String productCoverUrl;
    private String skuSpec;
    private BigDecimal productPrice;
    private BigDecimal productOriginalPrice;
    private Integer stock;
    private String unit;
    private Integer quantity;
    private Boolean selected;
    private BigDecimal subtotalAmount;
    /** 命中限时秒杀时才有值，供购物车打出「秒杀」标识与倒计时 */
    private Long flashSaleId;
    private BigDecimal flashPrice;
    private LocalDateTime flashEndTime;
    /**
     * 本行中「按秒杀价成交」的件数（自动拆分：超出每人限购的部分按原价）。
     * 用于前端展示「前 N 件秒杀价 + 超出 M 件原价」。等于 quantity 表示整行都秒杀，
     * 等于 0 表示已无秒杀名额（或本就不在秒杀），整行按原价。
     */
    private Integer flashQty;
    /** 非秒杀单价（min(正常售价, 会员价)），供前端展示「超出部分按原价」那一段 */
    private BigDecimal regularPrice;
    /**
     * 商品是否仍在售（未下架、未软删）。购物车/结算页必须能提前看出「这行已经买不了」——
     * 否则用户要等到提交订单才被后端拦下，而且只能拿到一句笼统的错误。
     */
    private Boolean onSale;

    public CartItemResponse() {
    }

    public static CartItemResponse from(CartItem item, Product product) {
        return from(item, product, null);
    }

    /**
     * 结算单价取「正常售价 / 会员价 / 秒杀价」三者最低 —— 必须与
     * {@code OrderService.buildOrderItem} 的算法逐字一致，否则购物车/结算预览会和实际下单对不上。
     *
     * <p>秒杀自动拆分：当本行有进行中的秒杀且秒杀价更低时，只有 {@code flashQty} 件按秒杀价，
     * 其余按原价（min(正常售价, 会员价)）。{@code productPrice} 取「按数量折算的等价单价」，
     * 保证 {@code productPrice × quantity == subtotalAmount} 恒成立（金额恒等式不被拆分破坏）；
     * 前端展示时用 {@code flashQty / flashPrice / regularPrice} 把两段价格显式拆开给用户看。
     *
     * @param flashSale 该商品此刻进行中的秒杀场次，没有则传 null
     * @param flashQty  本行按秒杀价成交的件数；传 null 表示整行不享受秒杀（常规路径）
     */
    public static CartItemResponse from(CartItem item, Product product, FlashSale flashSale) {
        return from(item, product, flashSale, null);
    }

    public static CartItemResponse from(CartItem item, Product product, FlashSale flashSale, Integer flashQty) {
        CartItemResponse response = new CartItemResponse();
        response.setId(item.getId());
        response.setProductId(product.getId());
        response.setProductName(product.getName());
        response.setProductSku(product.getSku());
        response.setProductCoverUrl(product.getCoverUrl());
        response.setSkuSpec(item.getSkuSpec());
        int qty = item.getQuantity();
        BigDecimal regular = product.getPrice();
        if (product.getMemberPrice() != null && product.getMemberPrice().compareTo(regular) < 0) {
            regular = product.getMemberPrice();
        }
        boolean flashApplies = flashSale != null && flashSale.getFlashPrice().compareTo(regular) < 0;
        int fq = 0;
        if (flashApplies) {
            fq = (flashQty == null) ? qty : Math.min(flashQty, qty);
        }
        int overflow = qty - fq;
        BigDecimal flashUnit = flashApplies ? flashSale.getFlashPrice() : regular;
        // 精确小计：秒杀段 + 原价段，避免「平均单价」带来的四舍五入漂移
        BigDecimal subtotal = flashUnit.multiply(BigDecimal.valueOf(fq))
                .add(regular.multiply(BigDecimal.valueOf(overflow)));
        // 等价单价 = 小计 / 数量，保留两位小数；再用它回填小计，确保 productPrice×quantity == subtotal
        BigDecimal unit = subtotal.divide(BigDecimal.valueOf(qty), 2, java.math.RoundingMode.HALF_UP);
        subtotal = unit.multiply(BigDecimal.valueOf(qty));
        response.setProductPrice(unit);
        response.setSubtotalAmount(subtotal);
        response.setRegularPrice(regular);
        if (fq > 0) {
            response.setFlashSaleId(flashSale.getId());
            response.setFlashPrice(flashSale.getFlashPrice());
            response.setFlashEndTime(flashSale.getEndTime());
        }
        response.setFlashQty(fq);
        // 划线对照价：命中秒杀时用商品正常售价（秒杀前的价），与订单行的 original_price 同口径
        response.setProductOriginalPrice(flashApplies
                ? product.getPrice() : product.getOriginalPrice());
        response.setStock(product.getStock());
        response.setOnSale(ON_SALE.equals(product.getStatus())
                && product.getDeleted() != null && product.getDeleted() == NOT_DELETED);
        response.setUnit(product.getUnit());
        response.setQuantity(qty);
        response.setSelected(Byte.valueOf((byte) 1).equals(item.getSelected()));
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductSku() {
        return productSku;
    }

    public void setProductSku(String productSku) {
        this.productSku = productSku;
    }

    public String getProductCoverUrl() {
        return productCoverUrl;
    }

    public void setProductCoverUrl(String productCoverUrl) {
        this.productCoverUrl = productCoverUrl;
    }

    public String getSkuSpec() {
        return skuSpec;
    }

    public void setSkuSpec(String skuSpec) {
        this.skuSpec = skuSpec;
    }

    public BigDecimal getProductPrice() {
        return productPrice;
    }

    public void setProductPrice(BigDecimal productPrice) {
        this.productPrice = productPrice;
    }

    public BigDecimal getProductOriginalPrice() {
        return productOriginalPrice;
    }

    public void setProductOriginalPrice(BigDecimal productOriginalPrice) {
        this.productOriginalPrice = productOriginalPrice;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Boolean getOnSale() {
        return onSale;
    }

    public void setOnSale(Boolean onSale) {
        this.onSale = onSale;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Boolean getSelected() {
        return selected;
    }

    public void setSelected(Boolean selected) {
        this.selected = selected;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public void setSubtotalAmount(BigDecimal subtotalAmount) {
        this.subtotalAmount = subtotalAmount;
    }

    public Long getFlashSaleId() {
        return flashSaleId;
    }

    public void setFlashSaleId(Long flashSaleId) {
        this.flashSaleId = flashSaleId;
    }

    public BigDecimal getFlashPrice() {
        return flashPrice;
    }

    public void setFlashPrice(BigDecimal flashPrice) {
        this.flashPrice = flashPrice;
    }

    public LocalDateTime getFlashEndTime() {
        return flashEndTime;
    }

    public void setFlashEndTime(LocalDateTime flashEndTime) {
        this.flashEndTime = flashEndTime;
    }

    public Integer getFlashQty() {
        return flashQty;
    }

    public void setFlashQty(Integer flashQty) {
        this.flashQty = flashQty;
    }

    public BigDecimal getRegularPrice() {
        return regularPrice;
    }

    public void setRegularPrice(BigDecimal regularPrice) {
        this.regularPrice = regularPrice;
    }

}
