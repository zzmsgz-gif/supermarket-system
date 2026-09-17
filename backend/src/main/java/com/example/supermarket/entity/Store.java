package com.example.supermarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 门店 / 自提点。自提订单在下单时把门店信息快照到订单上，避免门店改名影响历史订单。 */
@Entity
@Table(name = "store")
public class Store {

    public static final byte OPEN = 1;
    public static final byte CLOSED = 0;
    public static final byte NOT_DELETED = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(length = 20)
    private String phone;

    /** 营业/自提时间，如 08:00-22:00 */
    @Column(name = "business_hours", length = 60)
    private String businessHours;

    @Column(length = 40)
    private String city;

    @Column(length = 40)
    private String district;

    /**
     * 即时配送服务区域：逗号分隔的「城市/区县」，区县可省略表示全城（如 {@code 深圳市/南山区,深圳市/福田区}）。
     *
     * <p>留空时回退为「仅本店 {@code city + district}」。**即时配送可送达范围 = 所有营业中门店的并集**，
     * 因此门店停业/下线会自动收缩配送范围；快递配送不受此限制。
     */
    @Column(name = "service_areas", length = 255)
    private String serviceAreas;

    /** 自提须知，展示在结算页门店下方 */
    @Column(name = "pickup_notice", length = 255)
    private String pickupNotice;

    /** 1 营业 0 停业（停业门店不出现在结算页可选列表） */
    @Column(nullable = false)
    private Byte status;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo;

    @Column(nullable = false)
    private Byte deleted;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false, columnDefinition = "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false, columnDefinition = "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getBusinessHours() {
        return businessHours;
    }

    public void setBusinessHours(String businessHours) {
        this.businessHours = businessHours;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getServiceAreas() {
        return serviceAreas;
    }

    public void setServiceAreas(String serviceAreas) {
        this.serviceAreas = serviceAreas;
    }

    public String getPickupNotice() {
        return pickupNotice;
    }

    public void setPickupNotice(String pickupNotice) {
        this.pickupNotice = pickupNotice;
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

    public Byte getDeleted() {
        return deleted;
    }

    public void setDeleted(Byte deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
