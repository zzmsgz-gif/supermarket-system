package com.example.supermarket.schedule;

import com.example.supermarket.repository.UserCouponRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 定时清理「已使用」的优惠券记录（条目5）。
 *
 * <p>已使用的券对用户已无价值，但还会长期占据「我的优惠券」列表。这里按策略保留 N 天
 * （默认 30 天，{@code app.coupon.cleanup-retention-days} 可调）后自动删除，
 * 删除口径是 {@code status = 'USED' 且 used_at < now - N 天}。
 *
 * <p>保留期是为了：用户刚用完券时还能在列表里看到「已使用」凭证（避免误以为没抵扣），
 * 过一阵子再清掉，列表不至于越积越脏。
 */
@Component
public class CouponCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CouponCleanupScheduler.class);
    private static final String USED = "USED";

    private final UserCouponRepository userCouponRepository;
    private final int retentionDays;

    public CouponCleanupScheduler(UserCouponRepository userCouponRepository,
                                  @Value("${app.coupon.cleanup-retention-days:30}") int retentionDays) {
        this.userCouponRepository = userCouponRepository;
        this.retentionDays = Math.max(retentionDays, 1);
    }

    @Scheduled(cron = "${app.coupon.cleanup-cron:0 30 3 * * ?}", zone = "Asia/Shanghai")
    @Transactional
    public void cleanupUsedCoupons() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            long removed = userCouponRepository.deleteByStatusAndUsedAtBefore(USED, cutoff);
            if (removed > 0) {
                log.info("清理已使用优惠券 {} 条（保留 {} 天，used_at < {}）", removed, retentionDays, cutoff);
            }
        } catch (Exception ex) {
            log.error("清理已使用优惠券失败", ex);
        }
    }
}
