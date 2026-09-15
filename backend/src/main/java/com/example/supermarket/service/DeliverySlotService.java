package com.example.supermarket.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 配送时段：按「两小时一个窗口」生成今天（至少提前 1 小时）+ 明天 + 后天的可选时段。
 * 纯计算，不落库；订单只存选中的时段文本快照。
 */
@Service
public class DeliverySlotService {

    /** 配送窗口 [开始小时, 结束小时] */
    private static final int[][] WINDOWS = {
        {9, 11}, {11, 13}, {13, 15}, {15, 17}, {17, 19}, {19, 21}
    };
    private static final int DAYS_AHEAD = 3;
    /** 当天下单至少提前多久才能选该时段（小时） */
    private static final int MIN_LEAD_HOURS = 1;

    public List<Map<String, Object>> slots() {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (int offset = 0; offset < DAYS_AHEAD; offset++) {
            LocalDate date = now.toLocalDate().plusDays(offset);
            String dayLabel = offset == 0 ? "今天" : offset == 1 ? "明天" : "后天";
            for (int[] window : WINDOWS) {
                LocalDateTime start = date.atTime(window[0], 0);
                if (offset == 0 && start.isBefore(now.plusHours(MIN_LEAD_HOURS))) {
                    continue;
                }
                String timeLabel = String.format("%02d:00-%02d:00", window[0], window[1]);
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("value", date + " " + timeLabel);
                slot.put("date", date.toString());
                slot.put("dayLabel", dayLabel);
                slot.put("timeLabel", timeLabel);
                slot.put("label", dayLabel + " " + timeLabel);
                result.add(slot);
            }
        }
        return result;
    }
}
