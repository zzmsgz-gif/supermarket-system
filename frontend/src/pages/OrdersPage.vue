<template>
<section class="data-panel">
        <div class="panel-head">
          <button @click="loadOrders">刷新</button>
        </div>
        <div v-if="!orders.items?.length" class="empty">暂无订单</div>
        <div v-for="order in orders.items" :key="order.id" class="list-row tall wrap">
          <div>
            <div class="order-head">
              <strong class="order-no-link" @click="openOrderDetail(order)">{{ order.orderNo }}</strong>
              <span :class="['tag', shipStatusOf(order).cls]">{{ shipStatusOf(order).label }}</span>
              <span class="order-amount">{{ money(order.payAmount) }}</span>
            </div>
            <small>{{ formatPaymentStatus(order.paymentStatus) }}<template v-if="Number(order.discountAmount || 0) + Number(order.activityDiscount || 0) > 0"> · 已优惠 -{{ money(Number(order.discountAmount || 0) + Number(order.activityDiscount || 0)) }}<template v-if="order.activityName">（{{ order.activityName }}）</template></template></small>
            <small v-if="order.status === 'PAID'" class="ship-hint">商家尚未发货，请耐心等待</small>
            <small v-if="order.shipNo" class="ship-line">
              <span class="ship-flag">已发货</span>{{ order.shipCompany }} {{ order.shipNo }}
            </small>
            <small v-if="order.refundStatus && order.refundStatus !== 'NONE'" class="tag-warn">
              {{ formatRefundStatus(order.refundStatus) }}<template v-if="order.refundReason"> · {{ order.refundReason }}</template><template v-if="order.refundRemark"> · 处理意见：{{ order.refundRemark }}</template>
            </small>
          </div>
          <div class="row-actions">
            <button v-if="order.status === 'PENDING_PAYMENT'" @click="payOrder(order.id)">余额支付</button>
            <button v-if="order.status === 'SHIPPED'" @click="confirmReceipt(order.id)">确认收货</button>
            <button
              v-if="['PAID', 'SHIPPED'].includes(order.status) && order.refundStatus !== 'APPLYING'"
              class="ghost"
              @click="openRefundForm(order.id)"
            >申请退款</button>
            <button v-if="order.status === 'PENDING_PAYMENT'" class="ghost" @click="cancelOrder(order.id)">取消订单</button>
            <button v-if="order.status === 'COMPLETED' && !reviewedMap[order.id]" class="ghost" @click="openReviewForm(order.id)">评价</button>
          </div>
          <div v-if="refundForm.orderId === order.id" class="row-extra">
            <input v-model="refundForm.reason" placeholder="请填写退款原因" />
            <button @click="submitRefund(order.id)">提交申请</button>
            <button class="ghost" @click="refundForm.orderId = null">取消</button>
          </div>
          <div v-if="reviewForm.orderId === order.id" class="row-extra">
            <select v-model.number="reviewForm.rating" class="rating-select">
              <option :value="5">★★★★★ 非常满意</option>
              <option :value="4">★★★★ 满意</option>
              <option :value="3">★★★ 一般</option>
              <option :value="2">★★ 不满意</option>
              <option :value="1">★ 很差</option>
            </select>
            <input v-model="reviewForm.content" placeholder="写下你的评价（选填）" />
            <image-upload v-model="reviewForm.images" type="review" multiple :max="9" />
            <button @click="submitReview(order.id)">提交评价</button>
            <button class="ghost" @click="reviewForm.orderId = null">取消</button>
          </div>
        </div>
      </section>
</template>

<script>
import { inject } from 'vue';
export default {
  name: 'OrdersPage',
  setup() {
    const appCtx = inject('appCtx');
    return { ...appCtx };
  }
};
</script>
