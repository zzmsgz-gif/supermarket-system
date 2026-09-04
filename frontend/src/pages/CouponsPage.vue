<template>
<section class="coupon-page">
        <div class="coupon-section">
          <div class="panel-head">
            <h2>可领取优惠券</h2>
            <button @click="loadCoupons">刷新</button>
          </div>
          <div v-if="!coupons.length" class="empty">暂无可领取的优惠券</div>
          <div v-else class="coupon-grid">
            <CouponCard v-for="coupon in coupons" :key="coupon.id" :coupon="coupon" mode="claim" @receive="receiveCoupon" />
          </div>
        </div>
        <div class="coupon-section">
          <div class="panel-head">
            <h2>我的优惠券</h2>
            <button @click="loadCoupons">刷新</button>
          </div>
          <div v-if="!myCoupons.length" class="empty">还没有优惠券，先去领取一张吧</div>
          <div v-else class="coupon-grid">
            <CouponCard v-for="coupon in myCoupons" :key="coupon.id" :coupon="coupon" mode="mine" />
          </div>
        </div>
      </section>
</template>

<script>
import { inject } from 'vue';
export default {
  name: 'CouponsPage',
  setup() {
    const appCtx = inject('appCtx');
    return { ...appCtx };
  }
};
</script>
