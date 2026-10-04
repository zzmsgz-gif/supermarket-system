<template>
<section class="coupon-page">
        <div class="coupon-section">
          <div class="panel-head">
            <h2>可领取优惠券</h2>
          </div>
          <empty-state
            v-if="!coupons.length"
            icon="ticket"
            text="暂无可领取的优惠券，活动会不定期上线"
          />
          <div v-else class="coupon-grid">
            <CouponCard v-for="coupon in coupons" :key="coupon.id" :coupon="coupon" mode="claim" @receive="receiveCoupon" />
          </div>
        </div>
        <div class="coupon-section">
          <div class="panel-head">
            <h2>我的优惠券</h2>
          </div>
          <empty-state
            v-if="!myCoupons.items.length"
            icon="ticket"
            text="还没有优惠券，领一张下单更划算"
            action-text="去看看可领的券"
            @action="scrollToClaim"
          />
          <div v-else class="coupon-grid">
            <CouponCard v-for="coupon in myCoupons.items" :key="coupon.id" :coupon="coupon" mode="mine" />
          </div>
          <Pager
            :page="myCoupons.page"
            :total="myCoupons.total"
            :size="myCoupons.size"
            :loading="myCoupons.loading"
            @go="onMyCouponPage"
          />
        </div>
      </section>
</template>

<script>
import { inject } from 'vue';
export default {
  name: 'CouponsPage',
  setup() {
    const appCtx = inject('appCtx');
    const scrollToClaim = () => {
      document.querySelector('.coupon-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    };
    const onMyCouponPage = (page) => {
      appCtx.loadMyCouponsPage(page);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    };
    return { ...appCtx, scrollToClaim, onMyCouponPage };
  }
};
</script>
