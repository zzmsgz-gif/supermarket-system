package com.example.supermarket.dto;

import com.example.supermarket.entity.WalletTransaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WalletTransactionResponse {

    private Long id;
    private String transactionNo;
    private Long userId;
    private Long orderId;
    private String type;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String status;
    private String remark;
    /** 关联订单号（join 出来的，用户看一串 id 认不出是哪单） */
    private String orderNo;
    /**
     * 方向：1=支出（钱出去），-1=收入（钱进来）。
     *
     * <p><b>由后端根据 type 算好，前端不要自己猜</b> —— 方向判断写死在前端的话，
     * 以后新增一种流水类型就会漏改（2026-10-09「金额明细」按这个字段染色）。
     */
    private Integer direction;
    /** 展示用文案，如「订单支付」「退款到账」「账户充值」 */
    private String typeLabel;
    /**
     * 支付方式标签（2026-10-13 用户要求「金额明细得标注哪种支付方式」）：
     * 订单支付按 payment_record.channel 映射（微信支付/支付宝/余额支付），
     * 充值/退款这类不经过支付渠道的流水由 type 推（余额充值/退款至余额）。
     * 由后端算好下发，前端不自己猜渠道。
     */
    private String channelLabel;
    /**
     * 是否外部渠道流水（微信/支付宝）：这类支付不扣余额、退款原路返回，
     * **不计入**金额明细的累计收支（只作记录展示）。前端据此打「不计入」标。
     */
    private Boolean external;
    /**
     * 退款流水专用：退款时已花掉、等价扣回的发放积分对应的金额（元）。
     * 仅退款且发生积分扣回时有值；「原应退金额 = amount + pointsDeducted」，差额即此处（2026-10-22 #713）。
     */
    private BigDecimal pointsDeducted;
    private LocalDateTime createdAt;

    public static WalletTransactionResponse from(WalletTransaction transaction) {
        return from(transaction, null, null);
    }

    public static WalletTransactionResponse from(WalletTransaction transaction, String orderNo) {
        return from(transaction, orderNo, null);
    }

    public static WalletTransactionResponse from(WalletTransaction transaction, String orderNo, String channel) {
        WalletTransactionResponse response = new WalletTransactionResponse();
        response.setId(transaction.getId());
        response.setTransactionNo(transaction.getTransactionNo());
        response.setUserId(transaction.getUserId());
        response.setOrderId(transaction.getOrderId());
        response.setType(transaction.getType());
        response.setAmount(transaction.getAmount());
        response.setBalanceBefore(transaction.getBalanceBefore());
        response.setBalanceAfter(transaction.getBalanceAfter());
        response.setStatus(transaction.getStatus());
        response.setRemark(transaction.getRemark());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setOrderNo(orderNo);
        response.setDirection(directionOf(transaction.getType()));
        response.setTypeLabel(labelOf(transaction.getType()));
        response.setChannelLabel(channelLabelOf(transaction.getType(), channel));
        response.setExternal(isExternalChannel(channel));
        response.setPointsDeducted(transaction.getPointsDeducted());
        return response;
    }

    /** 渠道码 → 中文标签。未知/缺失按类型推合理默认，不显示 null。
     *
     * <p>2026-10-14 记账口径修正：退款按**原渠道**标注 —— 外部渠道(微信/支付宝)退款
     * 显示「已退回微信 / 已退回支付宝」且不进累计收支；余额退款才显示「退款至余额」。 */
    public static String channelLabelOf(String type, String channel) {
        boolean refund = "REFUND".equals(type);
        if ("WECHAT".equals(channel)) return refund ? "已退回微信" : "微信支付";
        if ("ALIPAY".equals(channel)) return refund ? "已退回支付宝" : "支付宝";
        if ("BALANCE".equals(channel)) return refund ? "退款至余额" : "余额支付";
        if ("RECHARGE".equals(type)) return "余额充值";
        if (refund) return "退款至余额";
        // PAYMENT 但没有渠道记录（极早期订单）——按余额支付兜底：
        // 那批订单本来就是走钱包扣款的，标错比留空好。
        return "PAYMENT".equals(type) ? "余额支付" : "";
    }

    /** 外部渠道（不走钱包余额的模拟支付）：其支付/退款都只作记录，不计入累计收支。 */
    public static boolean isExternalChannel(String channel) {
        return "WECHAT".equals(channel) || "ALIPAY".equals(channel);
    }

    /**
     * 方向由 type 决定：RECHARGE（充值）/ REFUND（退款）是**钱进来**，
     * PAYMENT（支付）是**钱出去**。未知类型按支出处理并留空文案 ——
     * 宁可显示成支出让人发现，也不要静默不显示。
     */
    private static Integer directionOf(String type) {
        if (type == null) return 1;
        return switch (type) {
            case "RECHARGE", "REFUND" -> -1;
            default -> 1;
        };
    }

    private static String labelOf(String type) {
        if (type == null) return "";
        return switch (type) {
            case "RECHARGE" -> "账户充值";
            case "PAYMENT" -> "订单支付";
            case "REFUND" -> "退款到账";
            default -> type;
        };
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Integer getDirection() {
        return direction;
    }

    public void setDirection(Integer direction) {
        this.direction = direction;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public void setTypeLabel(String typeLabel) {
        this.typeLabel = typeLabel;
    }

    public String getChannelLabel() {
        return channelLabel;
    }

    public void setChannelLabel(String channelLabel) {
        this.channelLabel = channelLabel;
    }

    public Boolean getExternal() {
        return external;
    }

    public void setExternal(Boolean external) {
        this.external = external;
    }

    public BigDecimal getPointsDeducted() {
        return pointsDeducted;
    }

    public void setPointsDeducted(BigDecimal pointsDeducted) {
        this.pointsDeducted = pointsDeducted;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTransactionNo() {
        return transactionNo;
    }

    public void setTransactionNo(String transactionNo) {
        this.transactionNo = transactionNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBalanceBefore() {
        return balanceBefore;
    }

    public void setBalanceBefore(BigDecimal balanceBefore) {
        this.balanceBefore = balanceBefore;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
