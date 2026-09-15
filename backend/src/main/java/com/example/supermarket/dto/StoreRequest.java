package com.example.supermarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 后台新增/编辑门店 */
public class StoreRequest {

    @NotBlank(message = "门店名称不能为空")
    @Size(max = 80, message = "门店名称最多 80 字")
    private String name;

    @NotBlank(message = "门店地址不能为空")
    @Size(max = 255, message = "门店地址最多 255 字")
    private String address;

    @Size(max = 20, message = "联系电话最多 20 字")
    private String phone;

    @Size(max = 60, message = "营业时间最多 60 字")
    private String businessHours;

    @Size(max = 40, message = "城市最多 40 字")
    private String city;

    @Size(max = 40, message = "区县最多 40 字")
    private String district;

    @Size(max = 255, message = "自提须知最多 255 字")
    private String pickupNotice;

    /** 1 营业 0 停业；为空按营业处理 */
    private Byte status;

    private Integer sortNo;

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
}
