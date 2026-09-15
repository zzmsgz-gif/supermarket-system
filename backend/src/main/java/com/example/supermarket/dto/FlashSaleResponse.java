package com.example.supermarket.dto;

import com.example.supermarket.entity.FlashSale;
import com.example.supermarket.entity.Product;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/** 秒杀场次（含商品快照信息，前台秒杀区可直接渲染，不用二次查商品）。 */
public class FlashSaleResponse {

    public static final String STATE_RUNNING = "RUNNING";
    public static final String STATE_UPCOMING = "UPCOMING";
    public static final String STATE_ENDED = "ENDED";

    private Long id;
    private String name;
    private Long productId;
    private String productName;
    private String productSubtitle;
    private String productCoverUrl;
    private String productBrand;
    private String productUnit;
    /** 商品正常售价（秒杀价的对照价） */
    private BigDecimal price;
    private BigDecimal memberPrice;
    private BigDecimal flashPrice;
    private Integer totalQuota;
    private Integer soldQuota;
    private Integer remainingQuota;
    private Integer perUserLimit;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Byte status;
    private Integer sortNo;
    /** RUNNING / UPCOMING / ENDED，由服务端按当前时间算好，避免前后端各算一套 */
    private String state;
    /** 距开始（UPCOMING）或距结束（RUNNING）的秒数，前端倒计时直接用 */
    private Long countdownSeconds;
    /** 已抢百分比，用于进度条 */
    private Integer progressPercent;

    /* ===== 与「当前登录用户」相关的占用情况（未登录时全部为 null，由服务层填入） ===== */

    /**
     * 当前用户在此场次已占用的件数（<b>含未付款订单占用的名额</b>）。
     * 前端用它算出「还能买几件」，进而在购物车里提前禁用 + 按钮，而不是等到结算才报错。
     */
    private Integer myBoughtQuantity;

    /** 其中来自「未付款订单」的件数 —— 这部分用户可自行支付或取消订单来释放 */
    private Integer myUnpaidQuantity;

    /** 最早那笔未付款订单（最先超时），用于给出「去支付 / 取消订单释放名额」入口；无则为 null */
    private Long myUnpaidOrderId;
    private String myUnpaidOrderNo;

    /** 该用户此刻还能买几件 = 每人限购 − 已占用；未登录或该场次不限购时为 null（表示无上限） */
    private Integer myRemainingQuota;

    public static FlashSaleResponse from(FlashSale sale, Product product, LocalDateTime now) {
        FlashSaleResponse response = new FlashSaleResponse();
        response.setId(sale.getId());
        response.setName(sale.getName());
        response.setProductId(sale.getProductId());
        response.setFlashPrice(sale.getFlashPrice());
        response.setTotalQuota(sale.getTotalQuota());
        response.setSoldQuota(sale.getSoldQuota());
        response.setRemainingQuota(Math.max(
                (sale.getTotalQuota() == null ? 0 : sale.getTotalQuota())
                        - (sale.getSoldQuota() == null ? 0 : sale.getSoldQuota()), 0));
        response.setPerUserLimit(sale.getPerUserLimit());
        response.setStartTime(sale.getStartTime());
        response.setEndTime(sale.getEndTime());
        response.setStatus(sale.getStatus());
        response.setSortNo(sale.getSortNo());
        if (product != null) {
            response.setProductName(product.getName());
            response.setProductSubtitle(product.getSubtitle());
            response.setProductCoverUrl(product.getCoverUrl());
            response.setProductBrand(product.getBrand());
            response.setProductUnit(product.getUnit());
            response.setPrice(product.getPrice());
            response.setMemberPrice(product.getMemberPrice());
        }
        boolean started = !now.isBefore(sale.getStartTime());
        boolean ended = !now.isBefore(sale.getEndTime());
        if (ended) {
            response.setState(STATE_ENDED);
            response.setCountdownSeconds(0L);
        } else if (started) {
            response.setState(STATE_RUNNING);
            response.setCountdownSeconds(Duration.between(now, sale.getEndTime()).getSeconds());
        } else {
            response.setState(STATE_UPCOMING);
            response.setCountdownSeconds(Duration.between(now, sale.getStartTime()).getSeconds());
        }
        int total = sale.getTotalQuota() == null ? 0 : sale.getTotalQuota();
        int sold = sale.getSoldQuota() == null ? 0 : sale.getSoldQuota();
        response.setProgressPercent(total <= 0 ? 0 : Math.min(100, (int) Math.round(sold * 100.0 / total)));
        return response;
    }

    /**
     * 填入「当前登录用户」在此场次的占用情况。
     *
     * <p>单独一个方法而不是塞进 {@link #from}，是为了让后台列表复用同一个 from 而不带这些字段
     * —— 后台看的是全场数据，与"我买了多少"无关。
     *
     * <p>{@code myRemainingQuota} 在这里算好而不是让前端减，是为了让「能买几件」只有一个口径：
     * 后端加购校验、下单校验、前端按钮禁用三处都读同一个数，避免各算各的又对不上。
     */
    public void applyUserQuota(int boughtQuantity, int unpaidQuantity, Long unpaidOrderId, String unpaidOrderNo) {
        this.myBoughtQuantity = boughtQuantity;
        this.myUnpaidQuantity = unpaidQuantity;
        this.myUnpaidOrderId = unpaidOrderId;
        this.myUnpaidOrderNo = unpaidOrderNo;
        Integer limit = this.perUserLimit;
        this.myRemainingQuota = (limit == null || limit <= 0) ? null : Math.max(limit - boughtQuantity, 0);
    }

    public Integer getMyBoughtQuantity() {
        return myBoughtQuantity;
    }

    public void setMyBoughtQuantity(Integer myBoughtQuantity) {
        this.myBoughtQuantity = myBoughtQuantity;
    }

    public Integer getMyUnpaidQuantity() {
        return myUnpaidQuantity;
    }

    public void setMyUnpaidQuantity(Integer myUnpaidQuantity) {
        this.myUnpaidQuantity = myUnpaidQuantity;
    }

    public Long getMyUnpaidOrderId() {
        return myUnpaidOrderId;
    }

    public void setMyUnpaidOrderId(Long myUnpaidOrderId) {
        this.myUnpaidOrderId = myUnpaidOrderId;
    }

    public String getMyUnpaidOrderNo() {
        return myUnpaidOrderNo;
    }

    public void setMyUnpaidOrderNo(String myUnpaidOrderNo) {
        this.myUnpaidOrderNo = myUnpaidOrderNo;
    }

    public Integer getMyRemainingQuota() {
        return myRemainingQuota;
    }

    public void setMyRemainingQuota(Integer myRemainingQuota) {
        this.myRemainingQuota = myRemainingQuota;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getProductSubtitle() {
        return productSubtitle;
    }

    public void setProductSubtitle(String productSubtitle) {
        this.productSubtitle = productSubtitle;
    }

    public String getProductCoverUrl() {
        return productCoverUrl;
    }

    public void setProductCoverUrl(String productCoverUrl) {
        this.productCoverUrl = productCoverUrl;
    }

    public String getProductBrand() {
        return productBrand;
    }

    public void setProductBrand(String productBrand) {
        this.productBrand = productBrand;
    }

    public String getProductUnit() {
        return productUnit;
    }

    public void setProductUnit(String productUnit) {
        this.productUnit = productUnit;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getMemberPrice() {
        return memberPrice;
    }

    public void setMemberPrice(BigDecimal memberPrice) {
        this.memberPrice = memberPrice;
    }

    public BigDecimal getFlashPrice() {
        return flashPrice;
    }

    public void setFlashPrice(BigDecimal flashPrice) {
        this.flashPrice = flashPrice;
    }

    public Integer getTotalQuota() {
        return totalQuota;
    }

    public void setTotalQuota(Integer totalQuota) {
        this.totalQuota = totalQuota;
    }

    public Integer getSoldQuota() {
        return soldQuota;
    }

    public void setSoldQuota(Integer soldQuota) {
        this.soldQuota = soldQuota;
    }

    public Integer getRemainingQuota() {
        return remainingQuota;
    }

    public void setRemainingQuota(Integer remainingQuota) {
        this.remainingQuota = remainingQuota;
    }

    public Integer getPerUserLimit() {
        return perUserLimit;
    }

    public void setPerUserLimit(Integer perUserLimit) {
        this.perUserLimit = perUserLimit;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Byte getStatus() {
        return status;
    }

    public void setStatus(Byte status) {
        this.status = status;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Long getCountdownSeconds() {
        return countdownSeconds;
    }

    public void setCountdownSeconds(Long countdownSeconds) {
        this.countdownSeconds = countdownSeconds;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }
}
