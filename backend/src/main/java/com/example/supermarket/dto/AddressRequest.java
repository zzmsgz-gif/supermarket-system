package com.example.supermarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AddressRequest {

    @NotBlank(message = "请填写收货人姓名")
    @Size(max = 50, message = "收货人姓名不能超过 50 个字符")
    private String receiverName;

    @NotBlank(message = "请填写手机号码")
    @Size(max = 20, message = "手机号码不能超过 20 个字符")
    private String receiverPhone;

    @NotBlank(message = "请选择省份")
    @Size(max = 50, message = "省份不能超过 50 个字符")
    private String province;

    @NotBlank(message = "请选择城市")
    @Size(max = 50, message = "城市不能超过 50 个字符")
    private String city;

    @NotBlank(message = "请选择区/县")
    @Size(max = 50, message = "区/县不能超过 50 个字符")
    private String district;

    @NotBlank(message = "请填写详细地址")
    @Size(max = 255, message = "详细地址不能超过 255 个字符")
    private String detailAddress;

    private Boolean isDefault;

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
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

    public String getDetailAddress() {
        return detailAddress;
    }

    public void setDetailAddress(String detailAddress) {
        this.detailAddress = detailAddress;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

}
