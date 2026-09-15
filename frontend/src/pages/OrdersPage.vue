<template>
<section class="data-panel">
        <empty-state
          v-if="!orders.items?.length"
          icon="receipt"
          text="暂无订单，下单后可在这里跟踪发货和售后"
          action-text="去逛逛"
          @action="navigate('shop')"
        />
        <div v-for="order in orders.items" :key="order.id" class="list-row tall wrap">
          <div>
            <div class="order-head">
              <strong class="order-no-link" @click="openOrderDetail(order)">{{ order.orderNo }}</strong>
              <span :class="['tag', shipStatusOf(order).cls]">{{ shipStatusOf(order).label }}</span>
              <span v-if="order.fulfillmentType === 'PICKUP'" class="tag ok">门店自提</span>
              <span class="order-amount">{{ money(order.payAmount) }}</span>
            </div>
            <small>{{ formatPaymentStatus(order.paymentStatus) }}<template v-if="orderSavedTotal(order) > 0"> · 已优惠 -{{ money(orderSavedTotal(order)) }}</template></small>
            <small v-if="order.status === 'PAID'" class="ship-hint">{{ order.fulfillmentType === 'PICKUP' ? '门店备货中，备好后凭自提码到店取货' : '商家尚未发货，请耐心等待' }}</small>
            <small v-if="order.fulfillmentType === 'PICKUP'" class="ship-line">
              <span class="ship-flag">自提码</span>{{ order.pickupCode }} · {{ order.pickupStoreName }}
            </small>
            <small v-else-if="order.deliverySlot" class="ship-line">
              <span class="ship-flag">配送时段</span>{{ order.deliverySlot }}
            </small>
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
import { orderSavedTotal } from '../utils/format';
export default {
  name: 'OrdersPage',
  setup() {
    const appCtx = inject('appCtx');
    return { ...appCtx, orderSavedTotal };
  }
};
</script>
