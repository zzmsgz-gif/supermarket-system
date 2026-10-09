package com.example.supermarket.service;

import com.example.supermarket.dto.AddressRequest;
import com.example.supermarket.dto.AddressResponse;
import com.example.supermarket.entity.UserAddress;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.UserAddressRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressService {

    private static final byte DEFAULT_ADDRESS = 1;
    private static final byte NOT_DEFAULT_ADDRESS = 0;

    private final UserAddressRepository addressRepository;

    public AddressService(UserAddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses(Long userId) {
        return addressRepository.findByUserIdOrderByIsDefaultDescUpdatedAtDesc(userId)
                .stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional
    public AddressResponse createAddress(Long userId, AddressRequest request) {
        rejectDuplicate(userId, request, null);
        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault()) || !addressRepository.existsByUserId(userId);
        if (makeDefault) {
            addressRepository.clearDefaultByUserId(userId);
        }
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        applyRequest(address, request);
        address.setIsDefault(makeDefault ? DEFAULT_ADDRESS : NOT_DEFAULT_ADDRESS);
        return AddressResponse.from(addressRepository.save(address));
    }

    /**
     * 拒绝完全重复的地址（2026-10-09 用户反馈「同一个地址能出现多次」）。
     *
     * <p><b>为什么不能只在前端查重</b>：前端拦得住正常操作，但多端登录、
     * 并发提交、或者直接调接口都能绕过 —— 重复数据一旦落库就脏了，
     * 还得写清理脚本。前端那份查重保留着，是为了**立刻给用户反馈**、少一次请求；
     * 后端这份才是保证数据不脏的那道闸。
     *
     * <p>判定口径：收货人 + 电话 + 省 + 市 + 区 + 详细地址，**全部相同**才算重复
     * （逐个字段 trim 并去掉内部空白，避免「张三 」和「张三」被判成两个）。
     *
     * @param excludeId 编辑场景要排除自己，否则改一下无关字段也会被误判成重复
     */
    private void rejectDuplicate(Long userId, AddressRequest request, Long excludeId) {
        boolean duplicated = addressRepository
                .findByUserIdOrderByIsDefaultDescUpdatedAtDesc(userId).stream()
                .filter(a -> excludeId == null || !excludeId.equals(a.getId()))
                .anyMatch(a -> sameAddress(a, request));
        if (duplicated) {
            throw new BusinessException(400, "这个地址已经存在了，无需重复添加");
        }
    }

    private boolean sameAddress(UserAddress a, AddressRequest r) {
        return norm(a.getReceiverName()).equals(norm(r.getReceiverName()))
                && norm(a.getReceiverPhone()).equals(norm(r.getReceiverPhone()))
                && norm(a.getProvince()).equals(norm(r.getProvince()))
                && norm(a.getCity()).equals(norm(r.getCity()))
                && norm(a.getDistrict()).equals(norm(r.getDistrict()))
                && norm(a.getDetailAddress()).equals(norm(r.getDetailAddress()));
    }

    /** 去首尾空白 + 压缩内部连续空白，让「XX 路 1 号」与「XX路1号」视为同一个 */
    private String norm(String v) {
        return v == null ? "" : v.trim().replaceAll("\\s+", "");
    }

    @Transactional
    public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request) {
        UserAddress address = getOwnedAddress(userId, addressId);
        rejectDuplicate(userId, request, addressId);
        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (makeDefault) {
            addressRepository.clearDefaultByUserId(userId);
        }
        applyRequest(address, request);
        address.setIsDefault(makeDefault ? DEFAULT_ADDRESS : NOT_DEFAULT_ADDRESS);
        return AddressResponse.from(addressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        UserAddress address = getOwnedAddress(userId, addressId);
        boolean wasDefault = address.getIsDefault() != null && address.getIsDefault() == DEFAULT_ADDRESS;
        addressRepository.delete(address);
        if (wasDefault) {
            addressRepository.findByUserIdOrderByIsDefaultDescUpdatedAtDesc(userId)
                    .stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setIsDefault(DEFAULT_ADDRESS);
                        addressRepository.save(next);
                    });
        }
    }

    @Transactional
    public AddressResponse setDefaultAddress(Long userId, Long addressId) {
        UserAddress address = getOwnedAddress(userId, addressId);
        addressRepository.clearDefaultByUserId(userId);
        address.setIsDefault(DEFAULT_ADDRESS);
        return AddressResponse.from(addressRepository.save(address));
    }

    private UserAddress getOwnedAddress(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
    }

    private void applyRequest(UserAddress address, AddressRequest request) {
        address.setReceiverName(request.getReceiverName().trim());
        address.setReceiverPhone(request.getReceiverPhone().trim());
        address.setProvince(request.getProvince().trim());
        address.setCity(request.getCity().trim());
        address.setDistrict(request.getDistrict().trim());
        address.setDetailAddress(request.getDetailAddress().trim());
    }
}
