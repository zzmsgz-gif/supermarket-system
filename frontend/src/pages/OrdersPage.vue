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
              <span :class="['tag', order.fulfillmentType === 'PICKUP' ? 'ok' : 'muted']">{{ fulfillmentLabel(order) }}</span>
              <span class="order-amount">{{ money(order.payAmount) }}</span>
            </div>
            <!-- 找订单的关键信息（2026-10-07 用户反馈：「光看一个订单编号很难找到想找的订单」）。
                 编号对人无意义，用户真正记得住的是「什么时候买的」「买了什么」。
                 最多列 3 件 —— 再多会把卡片撑高，反而更难扫。 -->
            <div class="order-goods" v-if="(order.items || []).length">
              <span v-for="it in (order.items || []).slice(0, 3)" :key="it.id" class="order-goods-item">
                <img v-if="it.productCoverUrl" :src="it.productCoverUrl" :alt="it.productName" loading="lazy" />
                <em>{{ it.productName }}</em><i>×{{ it.quantity }}</i>
              </span>
              <small v-if="order.items.length > 3" class="og-more">另有 {{ order.items.length - 3 }} 件</small>
            </div>
            <small class="order-time">下单 {{ formatDate(order.createdAt) }}</small>
            <small>{{ formatPaymentStatus(order.paymentStatus) }}<template v-if="orderSavedTotal(order) > 0"> · 已优惠 -{{ money(orderSavedTotal(order)) }}</template></small>
            <small v-if="order.status === 'PAID'" class="ship-hint">{{ order.fulfillmentType === 'PICKUP'
              ? '门店备货中，备好后凭自提码到店取货'
              : order.fulfillmentType === 'EXPRESS'
                ? '商家尚未发货，交由快递后可在订单详情查看单号'
                : '商家备货中，即将开始配送' }}</small>
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
            <!-- 付款一律先进收银台（/pay/:id）看一眼倒计时再决定，不再这里直接扣款。
                 订单详情里同样有入口，两条路都不会让人进入「退出后就找不到付款页」的死角。 -->
            <button v-if="order.status === 'PENDING_PAYMENT'" @click="navigate('pay', { id: order.id })">去付款</button>
            <button v-if="order.status === 'SHIPPED'" @click="confirmReceipt(order.id)">确认收货</button>
            <!-- 售后入口：仅「已收货(COMPLETED)」可申请。
                 原先 PAID/SHIPPED 都能申请，与常理相反 —— 货还没发就能退单，
                 而真收到货有问题反而没处申诉。条件与后端 applyRefund 一致（2026-10-07 改）。
                 未发货想退用「取消订单」，那条路径不受影响。 -->
            <button
              v-if="order.status === 'COMPLETED' && order.refundStatus !== 'APPLYING' && order.refundStatus !== 'APPROVED'"
              class="ghost"
              @click="openRefundForm(order.id)"
            >申请售后</button>
            <button v-if="order.status === 'PENDING_PAYMENT'" class="ghost" @click="cancelOrder(order.id)">取消订单</button>
            <button v-if="order.status === 'COMPLETED' && !reviewedMap[order.id]" class="ghost" @click="openReviewForm(order.id)">评价</button>
            <!-- 复购入口：生鲜的核心就是周期性买同样的东西。已付款之后的订单都该能一键回填购物车 -->
            <button v-if="order.status !== 'PENDING_PAYMENT'" class="ghost" @click="reorder(order)">再来一单</button>
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
      <Pager
        :page="orders.page"
        :total="orders.total"
        :size="orders.size"
        :loading="orders.loading"
        @go="onOrderPage"
      />
</template>

<script>
import { inject } from 'vue';
import { orderSavedTotal } from '../utils/format';
export default {
  name: 'OrdersPage',
  setup() {
    const appCtx = inject('appCtx');
    const onOrderPage = (page) => {
      appCtx.loadOrdersPage(page);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    };
    return { ...appCtx, orderSavedTotal, onOrderPage };
  }
};
</script>
