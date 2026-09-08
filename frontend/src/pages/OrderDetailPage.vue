<template>
<section class="data-panel">
        <div class="panel-head">
          <button class="ghost" @click="closeOrderDetail">← 返回</button>
          <span v-if="orderDetail.data" class="order-no-display">订单号：{{ orderDetail.data.orderNo }}</span>
        </div>
        <div v-if="orderDetail.loading" class="empty">加载中…</div>
        <div v-else-if="orderDetail.error" class="empty error">{{ orderDetail.error }}</div>
        <template v-else-if="orderDetail.data">
          <div class="order-summary">
            <div class="order-row"><span>订单状态</span><b>{{ formatOrderStatus(orderDetail.data.status) }}</b></div>
            <div class="order-row"><span>支付状态</span><b>{{ formatPaymentStatus(orderDetail.data.paymentStatus) }}</b></div>
            <div class="order-row"><span>商品小计</span><b>{{ money(orderDetail.data.totalAmount) }}</b></div>
            <div class="order-row" v-if="Number(orderDetail.data.freightAmount || 0) > 0"><span>运费</span><b>+ {{ money(orderDetail.data.freightAmount) }}</b></div>
            <div class="order-row" v-if="Number(orderDetail.data.discountAmount || 0) > 0"><span>优惠券</span><b style="color:var(--danger)">- {{ money(orderDetail.data.discountAmount) }}</b></div>
            <div class="order-row" v-if="Number(orderDetail.data.activityDiscount || 0) > 0"><span>活动优惠（{{ orderDetail.data.activityName }}）</span><b style="color:var(--danger)">- {{ money(orderDetail.data.activityDiscount) }}</b></div>
            <div class="order-row"><span>实付金额</span><b>{{ money(orderDetail.data.payAmount) }}</b></div>
            <div class="order-row"><span>收货人</span><b>{{ orderDetail.data.receiverName }} {{ orderDetail.data.receiverPhone }}</b></div>
            <div class="order-row"><span>收货地址</span><b>{{ orderDetail.data.receiverAddress }}</b></div>
            <div class="order-row" v-if="orderDetail.data.shipCompany || orderDetail.data.shipNo"><span>物流信息</span><b>{{ orderDetail.data.shipCompany }} {{ orderDetail.data.shipNo }}</b></div>
            <div class="order-row" v-if="orderDetail.data.remark"><span>备注</span><b>{{ orderDetail.data.remark }}</b></div>
          </div>
          <h3 class="items-title">购买商品（{{ (orderDetail.data.items || []).length }} 件）</h3>
          <div class="order-items">
            <div v-for="it in (orderDetail.data.items || [])" :key="it.id" class="order-item">
              <img v-if="it.productCoverUrl" :src="it.productCoverUrl" class="order-item-img" alt="商品图片" />
              <div class="order-item-info">
                <div class="order-item-name">{{ it.productName }}</div>
                <div v-if="it.skuSpec" class="order-item-spec">规格：{{ it.skuSpec }}</div>
                <div class="order-item-meta">
                  <span>{{ money(it.productPrice) }}</span>
                  <span class="order-item-qty">× {{ it.quantity }}</span>
                  <span class="subtotal">{{ money(it.subtotalAmount) }}</span>
                </div>
              </div>
            </div>
          </div>
        </template>
      </section>
</template>

<script>
import { inject } from 'vue';
export default {
  name: 'OrderDetailPage',
  setup() {
    const appCtx = inject('appCtx');
    return { ...appCtx };
  }
};
</script>
