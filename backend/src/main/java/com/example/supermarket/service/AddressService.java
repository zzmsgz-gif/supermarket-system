package com.example.supermarket.service;

import com.example.supermarket.dto.AddressRequest;
import com.example.supermarket.dto.AddressResponse;
import com.example.supermarket.entity.UserAddress;
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

    @Transactional
    public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request) {
        UserAddress address = getOwnedAddress(userId, addressId);
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
