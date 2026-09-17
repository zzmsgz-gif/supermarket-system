package com.example.supermarket.dto;

import com.example.supermarket.entity.Store;
import java.time.LocalDateTime;

public class StoreResponse {

    private Long id;
    private String name;
    private String address;
    private String phone;
    private String businessHours;
    private String city;
    private String district;
    /** 即时配送服务区域（逗号分隔），供前台展示与后台编辑；留空表示仅本店 city+district */
    private String serviceAreas;
    private String pickupNotice;
    private Byte status;
    private Integer sortNo;
    private LocalDateTime createdAt;

    public static StoreResponse from(Store store) {
        StoreResponse response = new StoreResponse();
        response.setId(store.getId());
        response.setName(store.getName());
        response.setAddress(store.getAddress());
        response.setPhone(store.getPhone());
        response.setBusinessHours(store.getBusinessHours());
        response.setCity(store.getCity());
        response.setDistrict(store.getDistrict());
        response.setServiceAreas(store.getServiceAreas());
        response.setPickupNotice(store.getPickupNotice());
        response.setStatus(store.getStatus());
        response.setSortNo(store.getSortNo());
        response.setCreatedAt(store.getCreatedAt());
        return response;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
