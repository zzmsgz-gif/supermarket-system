package com.example.supermarket.service;

import com.example.supermarket.entity.Store;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.repository.StoreRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 即时配送范围校验。
 *
 * <p><b>模型：可送达范围 = 所有「营业中」门店的 {@code service_areas} 并集。</b>
 * 选这个模型的原因：门店本身已经带结构化的 city / district，不需要引入经纬度、也不需要地理编码服务；
 * 而且配送范围本就应该跟着门店走 —— 门店停业/下线，其区域自动退出即时配送范围。
 *
 * <p>门店的 {@code service_areas} 留空时回退为「仅本店 city + district」，所以开箱即有效，
 * 后台再按需扩展到相邻区县（格式：逗号分隔的「城市/区县」，区县可省略表示全城）。
 *
 * <p>快递配送**不走这个校验**：全国可达，成本已经由运费承担。
 */
@Service
public class DeliveryRangeService {

    private static final byte NOT_DELETED = 0;

    private final StoreRepository storeRepository;

    public DeliveryRangeService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    /** 归一化后的可送达区域（形如 {@code 深圳/南山}），用于匹配 */
    @Transactional(readOnly = true)
    public List<String> servedAreas() {
        Set<String> areas = new LinkedHashSet<>();
        for (Store store : openStores()) {
            areas.addAll(normalizedAreas(store));
        }
        return new ArrayList<>(areas);
    }

    /** 收货地址是否在即时配送范围内 */
    @Transactional(readOnly = true)
    public boolean deliverable(String city, String district) {
        String targetCity = normalize(city);
        if (targetCity.isEmpty()) return false;
        String targetDistrict = normalize(district);
        for (String area : servedAreas()) {
            String[] parts = area.split("/");
            if (!parts[0].equals(targetCity)) continue;
            // 只配到城市的条目视为「全城可达」；配到区县的必须区县也一致
            if (parts.length < 2 || parts[1].isEmpty() || parts[1].equals(targetDistrict)) return true;
        }
        return false;
    }

    /**
     * 下单前的硬拦。文案要一次说清三件事：**哪个地址送不了、现在覆盖哪里、可以改用什么** ——
     * 只说"超出配送范围"会让用户不知道该改地址还是改履约方式。
     */
    @Transactional(readOnly = true)
    public void assertDeliverable(String city, String district) {
        if (deliverable(city, district)) return;
        // 不需要为「只有城市、没有区县」单独写分支：地址接口把 district 定为必填
        // （AddressRequest 上的 @NotBlank），库里也不存在空区县的行，那条分支走不到。
        throw new BusinessException(409, "收货地址「" + display(city, district)
                + "」不在同城即时配送范围内（当前覆盖：" + coverageLabel() + "）。可改用「快递配送」下单。");
    }

    /** 人工可读的覆盖范围，用于提示文案 */
    @Transactional(readOnly = true)
    public String coverageLabel() {
        Set<String> labels = new LinkedHashSet<>();
        for (Store store : openStores()) {
            if (StringUtils.hasText(store.getServiceAreas())) {
                for (String raw : store.getServiceAreas().split(",")) {
                    String label = raw.trim();
                    if (!label.isEmpty()) labels.add(label);
                }
            } else {
                String city = trim(store.getCity());
                String district = trim(store.getDistrict());
                if (!city.isEmpty()) labels.add(district.isEmpty() ? city : city + " " + district);
            }
        }
        return labels.isEmpty() ? "暂无营业中的门店" : String.join("、", labels);
    }

    private List<Store> openStores() {
        return storeRepository.findByDeletedAndStatusOrderBySortNoAscIdAsc(NOT_DELETED, Store.OPEN);
    }

    /**
     * 某门店的服务区域（归一化）。未配置时回退为「本店 city + district」。
     *
     * <p>⚠️ 必须**先按 {@code /} 拆开再分别归一化**：条目 {@code 深圳市/南山区} 若整串去后缀会变成
     * {@code 深圳市/南山}，而收货地址的 city 会归一化成 {@code 深圳} —— 两边永远对不上。
     */
    private List<String> normalizedAreas(Store store) {
        List<String> result = new ArrayList<>();
        if (StringUtils.hasText(store.getServiceAreas())) {
            for (String raw : store.getServiceAreas().split(",")) {
                String[] parts = raw.split("/");
                String city = normalize(parts[0]);
                if (city.isEmpty()) continue;
                String district = parts.length > 1 ? normalize(parts[1]) : "";
                result.add(district.isEmpty() ? city : city + "/" + district);
            }
        }
        if (result.isEmpty()) {
            String city = normalize(store.getCity());
            String district = normalize(store.getDistrict());
            if (!city.isEmpty()) result.add(district.isEmpty() ? city : city + "/" + district);
        }
        return result;
    }

    private String display(String city, String district) {
        return (trim(city) + " " + trim(district)).trim();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 归一化：去空白、转小写、去掉行政区划后缀，让「深圳」与「深圳市」、「南山」与「南山区」视为同一处。
     *
     * <p>⚠️ 只做行政区划后缀层面的归一，**不做拼音或中英互转** —— 像历史数据里的
     * {@code Shenzhen / Nanshan} 这类写法不会被自动匹配，需要后台把该写法显式写进门店的服务区域。
     */
    static String normalize(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase().replaceAll("\\s+", "");
        while (value.length() > 1 && "省市区县".indexOf(value.charAt(value.length() - 1)) >= 0) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
