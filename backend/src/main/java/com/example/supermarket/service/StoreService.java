package com.example.supermarket.service;

import com.example.supermarket.dto.StoreRequest;
import com.example.supermarket.dto.StoreResponse;
import com.example.supermarket.entity.Store;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.StoreRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 门店 / 自提点：前台只能看营业中的，后台可增删改查 */
@Service
public class StoreService {

    private static final byte NOT_DELETED = 0;

    private final StoreRepository storeRepository;
    private final EntityManager entityManager;

    public StoreService(StoreRepository storeRepository, EntityManager entityManager) {
        this.storeRepository = storeRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> listOpen() {
        return storeRepository.findByDeletedAndStatusOrderBySortNoAscIdAsc(NOT_DELETED, Store.OPEN).stream()
                .map(StoreResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> listAll() {
        return storeRepository.findByDeletedOrderBySortNoAscIdAsc(NOT_DELETED).stream()
                .map(StoreResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public StoreResponse create(StoreRequest request) {
        String name = request.getName().trim();
        if (storeRepository.existsByNameAndDeleted(name, NOT_DELETED)) {
            throw new BusinessException(409, "门店名称已存在");
        }
        Store store = new Store();
        apply(store, request, name);
        store.setDeleted(NOT_DELETED);
        return reload(storeRepository.saveAndFlush(store));
    }

    @Transactional
    public StoreResponse update(Long id, StoreRequest request) {
        Store store = getActive(id);
        String name = request.getName().trim();
        if (storeRepository.existsByNameAndDeletedAndIdNot(name, NOT_DELETED, id)) {
            throw new BusinessException(409, "门店名称已存在");
        }
        apply(store, request, name);
        return reload(storeRepository.saveAndFlush(store));
    }

    @Transactional
    public StoreResponse updateStatus(Long id, Byte status) {
        Store store = getActive(id);
        store.setStatus(status != null && status == Store.OPEN ? Store.OPEN : Store.CLOSED);
        return reload(storeRepository.saveAndFlush(store));
    }

    @Transactional
    public void delete(Long id) {
        Store store = getActive(id);
        store.setDeleted((byte) 1);
        store.setStatus(Store.CLOSED);
        storeRepository.save(store);
    }

    private void apply(Store store, StoreRequest request, String name) {
        store.setName(name);
        store.setAddress(request.getAddress().trim());
        store.setPhone(trimToNull(request.getPhone()));
        store.setBusinessHours(trimToNull(request.getBusinessHours()));
        store.setCity(trimToNull(request.getCity()));
        store.setDistrict(trimToNull(request.getDistrict()));
        store.setServiceAreas(trimToNull(request.getServiceAreas()));
        store.setPickupNotice(trimToNull(request.getPickupNotice()));
        store.setStatus(request.getStatus() != null && request.getStatus() == Store.CLOSED ? Store.CLOSED : Store.OPEN);
        store.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
    }

    /**
     * 刷新托管实体，让 created_at / updated_at 这类「数据库生成、不参与 INSERT」的列出现在响应里。
     * 注意不能改用 findById：同一事务里实体已在持久化上下文，findById 命中一级缓存不会回读数据库。
     */
    private StoreResponse reload(Store store) {
        entityManager.refresh(store);
        return StoreResponse.from(store);
    }

    private Store getActive(Long id) {
        return storeRepository.findByIdAndDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
