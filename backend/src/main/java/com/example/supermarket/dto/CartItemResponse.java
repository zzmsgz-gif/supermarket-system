package com.example.supermarket.dto;

import com.example.supermarket.service.MemberService;

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
    /**
     * 划线对照价 = <b>售价</b>（即会员折前、非会员看到的价格），不是吊牌价。
     *
     * <p><b>为什么是售价而不是吊牌价</b>：用户把划线数字减一下，得到的差额必须<b>恰好等于会员优惠</b>。
     * 用吊牌价（product.original_price）会得到「吊牌价 − 会员价」，那笔差额里混着商家的
     * 定价让利，用户拿界面上任何一个数字都核不出「省了多少」——2026-10-07 用户原话：
     * 「省5块省在哪？」。改成售价后，划线数字 − 成交价 = 会员让利，一目了然。
     *
     * <p>吊牌价仍有其用途（展示商品原价促销），但它与会员折扣是两笔不同的钱，
     * 混在同一个划线数字里会让用户误以为会员白拿了商家的促销。
     */
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
     * 本行「会员让利」（等级折扣 / 商品会员价带来的优惠，不含秒杀），单位：元。
     * = (非会员单价 − 会员成交单价) × 数量；非会员或会员价未低于基准价时为 0。
     * 供前端结算页把「会员折扣」单独拆出一行，与活动 / 优惠券并列展示。
     */
    private BigDecimal memberDiscount;
    /**
     * 本行会员让利的<b>来源</b>：{@code "member"} = 商品专属会员价更低，
     * {@code "tier"} = 等级折扣（如银卡 0.98）更低，{@code null} = 本行没有会员让利。
     *
     * <p><b>为什么必须透出</b>：银卡 0.98 折和「商品专属会员价」是两笔不同来源的钱，
     * 冠同一个名字就是张冠李戴——用户看到鸡蛋（无 member_price，只靠 0.98 折便宜 3 毛）
     * 被标成「会员价」时会问「关会员价什么事，我又不是因为会员价便宜」。
     * 商品页/详情页本来就按 source 分开显示（「会员价 ¥X」/「银卡 9.8折」），
     * 购物车与结算页此前缺这个分支，现已对齐。
     */
    private String memberSource;
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
        return from(item, product, flashSale, flashQty, null);
    }

    /**
     * @param skuPrice 该行所选规格的售价（由 {@code SkuPriceSupport} 解析）；
     *                 为 null 表示未选规格或该规格未单独定价 → 回落商品基准价。
     *                 注意：规格价优先，且此时不再叠加会员价 —— 会员价是按商品基准价定的绝对值，
     *                 拿它去比一个更大规格的价钱会算出更便宜的怪价（如 1.5L 比 350ml 还低）。
     */
    public static CartItemResponse from(CartItem item, Product product, FlashSale flashSale, Integer flashQty,
            BigDecimal skuPrice) {
        // 未传会员等级时按普通用户处理（level0 不享会员价）
        return from(item, product, flashSale, flashQty, skuPrice, 0);
    }

    /**
     * @param memberLevel 买家会员等级：决定能否享「商品会员价」与「等级折扣」；
     *                    与下单 {@code OrderService} 同口径（逐商品取优、不叠加）。传 null 按普通用户处理。
     */
    public static CartItemResponse from(CartItem item, Product product, FlashSale flashSale, Integer flashQty,
            BigDecimal skuPrice, Integer memberLevel) {
        CartItemResponse response = new CartItemResponse();
        response.setId(item.getId());
        response.setProductId(product.getId());
        response.setProductName(product.getName());
        response.setProductSku(product.getSku());
        response.setProductCoverUrl(product.getCoverUrl());
        response.setSkuSpec(item.getSkuSpec());
        int qty = item.getQuantity();
        // 逐商品取优：会员价（level>=1 才享）与「等级折扣价」取更低，两者不叠加。
        // 走规格价时不比较会员价 —— 会员价是商品级绝对值，与规格价不可直接比大小。
        BigDecimal regular = MemberService.unitPriceFor(memberLevel,
                skuPrice != null ? skuPrice : product.getPrice(),
                skuPrice == null ? product.getMemberPrice() : null);
        // 秒杀价按「折扣率」套到规格价上：走规格价时 = 规格价 × (基准秒杀价 / 基准价)，否则用基准秒杀绝对值
        BigDecimal flashUnitPrice = flashSale != null
                ? flashSale.flashPriceFor(product.getPrice(), skuPrice) : regular;
        boolean flashApplies = flashSale != null && flashUnitPrice.compareTo(regular) < 0;
        int fq = 0;
        if (flashApplies) {
            fq = (flashQty == null) ? qty : Math.min(flashQty, qty);
        }
        int overflow = qty - fq;
        BigDecimal flashUnit = flashApplies ? flashUnitPrice : regular;
        // 精确小计：秒杀段 + 原价段，避免「平均单价」带来的四舍五入漂移
        BigDecimal subtotal = flashUnit.multiply(BigDecimal.valueOf(fq))
                .add(regular.multiply(BigDecimal.valueOf(overflow)));
        // 等价单价 = 小计 / 数量，保留两位小数；再用它回填小计，确保 productPrice×quantity == subtotal
        BigDecimal unit = subtotal.divide(BigDecimal.valueOf(qty), 2, java.math.RoundingMode.HALF_UP);
        subtotal = unit.multiply(BigDecimal.valueOf(qty));
        response.setProductPrice(unit);
        response.setSubtotalAmount(subtotal);
        response.setRegularPrice(regular);
        // 会员折扣（仅常规成交；秒杀行的优惠走秒杀行，不计入会员折扣）
        BigDecimal memberDiscount = BigDecimal.ZERO;
        // 让利来源：与 MemberService.unitPriceFor 的取值优先级逐字对齐 ——
        // 商品会员价更低 → 'member'；否则等级折扣更低 → 'tier'。两者都不更低则无让利。
        String memberSource = null;
        if (!flashApplies) {
            BigDecimal baseUnit = skuPrice != null ? skuPrice : product.getPrice();
            BigDecimal perUnitMd = baseUnit.subtract(regular);
            if (perUnitMd.compareTo(BigDecimal.ZERO) > 0) {
                memberDiscount = perUnitMd.multiply(BigDecimal.valueOf(qty));
                // unitPriceFor 里 memberPrice 只在 skuPrice == null 时参与比较（规格价不与商品会员价比），
                // 这里必须用同一条判定，否则来源会标错
                boolean memberPriceWon = skuPrice == null
                        && product.getMemberPrice() != null
                        && product.getMemberPrice().compareTo(baseUnit) < 0
                        && product.getMemberPrice().compareTo(regular) <= 0;
                memberSource = memberPriceWon ? "member" : "tier";
            }
        }
        response.setMemberDiscount(memberDiscount.setScale(2, java.math.RoundingMode.HALF_UP));
        response.setMemberSource(memberSource);
        if (fq > 0) {
            response.setFlashSaleId(flashSale.getId());
            response.setFlashPrice(flashUnit);
            response.setFlashEndTime(flashSale.getEndTime());
        }
        response.setFlashQty(fq);
        // 划线对照价 = 售价（会员折前）。见字段注释：差额必须恰好等于会员让利，用户才核得出来。
        // 秒杀行用 regular（min(售价, 会员价)）= 秒杀前的价，同样与 unitPriceFor 同源。
        response.setProductOriginalPrice(flashApplies
                ? regular : (skuPrice != null ? skuPrice : product.getPrice()));
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

    public BigDecimal getMemberDiscount() {
        return memberDiscount;
    }

    public void setMemberDiscount(BigDecimal memberDiscount) {
        this.memberDiscount = memberDiscount;
    }

    public String getMemberSource() {
        return memberSource;
    }

    public void setMemberSource(String memberSource) {
        this.memberSource = memberSource;
    }

}
