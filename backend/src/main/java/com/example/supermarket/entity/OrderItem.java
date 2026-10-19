package com.example.supermarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Column(name = "product_sku", nullable = false, length = 64)
    private String productSku;

    @Column(name = "product_cover_url", length = 500)
    private String productCoverUrl;

    @Column(name = "sku_spec", length = 255)
    private String skuSpec;

    @Column(name = "product_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal productPrice;

    /**
     * 下单时的划线价（原价）快照，仅用于订单详情展示「划线优惠（已省）」。
     * 商品没有划线价时为 null；历史订单未回填（当时的原价已不可考），保持 null 即可不展示该行。
     */
    @Column(name = "original_price", precision = 10, scale = 2)
    private BigDecimal originalPrice;

    /**
     * 会员让利来源快照（下单时定格）：{@code member}=商品会员价更低、{@code tier}=等级折扣更低、
     * {@code NULL}=无会员让利（含秒杀价成交）。订单详情页据此把「会员让利」拆成
     * 「会员优惠」（会员价）与「会员折扣」（等级折扣）两行分开列——两者是性质不同的钱。
     * 历史订单未回填（当时来源已不可考），前端对 NULL 整单回退旧的合并展示。
     */
    @Column(name = "member_source", length = 10)
    private String memberSource;

    /**
     * 命中的秒杀场次 id 快照。取消/超时/退款时据此回退秒杀名额；
     * 秒杀价本身体现在 {@code productPrice}（与会员价同一套口径），不另加优惠金额列。
     */
    @Column(name = "flash_sale_id")
    private Long flashSaleId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "subtotal_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotalAmount;

    /**
     * 该订单行是否已退货退款（按商品行维度的售后标记）。
     * 仅当对应的 return_request 退款放款成功后才置 true；
     * 后台全额退款或按行退款时分别更新全部 / 该行。NULL 视为未退。
     *
     * ⚠️ 字段默认值必须是 false 而不能是 null：Hibernate 会把 null **显式写进 INSERT**，
     * 绕过列上的 DEFAULT 0 —— 2026-10-16 生产故障「Column 'refunded' cannot be null」：
     * 所有新建订单插 order_item 全被拒（前端立即购买/购物车结算双双 500，且错误被吞成"没反应"）。
     */
    @Column(name = "refunded", nullable = false, columnDefinition = "tinyint(1) NOT NULL DEFAULT 0")
    private Boolean refunded = false;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
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

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
    }

    public Long getFlashSaleId() {
        return flashSaleId;
    }

    public void setFlashSaleId(Long flashSaleId) {
        this.flashSaleId = flashSaleId;
    }

    public String getMemberSource() {
        return memberSource;
    }

    public void setMemberSource(String memberSource) {
        this.memberSource = memberSource;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public void setSubtotalAmount(BigDecimal subtotalAmount) {
        this.subtotalAmount = subtotalAmount;
    }

    public Boolean getRefunded() {
        return refunded;
    }

    public void setRefunded(Boolean refunded) {
        this.refunded = refunded;
    }
}
