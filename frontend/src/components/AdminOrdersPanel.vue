<script setup>
import { toRefs } from 'vue';
// 后台「订单管理」面板：从 AdminPanel.vue 整块搬过来的。
//
// 依赖走显式 props（不接 adminCtx）：面板只服务一个 tab，列全依赖比藏起来好。
import { money, formatDate, formatPaymentStatus, orderStatusTag, orderStatusLabel, fulfillmentLabel, orderSavedTotal } from '../utils/format';
import AdminSearchBox from './AdminSearchBox.vue';
import AdminPageSize from './AdminPageSize.vue';
import AdminPager from './AdminPager.vue';

const props = defineProps({
  adminOrders: { type: Object, required: true },
  adminOrderTotalPages: { type: Number, required: true },
  shipForm: { type: Object, required: true },
  searchAdminOrders: { type: Function, required: true },
  changeAdminOrderPage: { type: Function, required: true },
  changeAdminOrderPageSize: { type: Function, required: true },
  goAdminOrderPage: { type: Function, required: true },
  resetAdminOrderSearch: { type: Function, required: true },
  openShipForm: { type: Function, required: true },
  adminOrderReady: { type: Function, required: true },
  submitShip: { type: Function, required: true },
  completeAdminOrder: { type: Function, required: true },
  cancelAdminOrder: { type: Function, required: true },
  openOrderDetail: { type: Function, required: true },
});

// ⚠️ 下面这几个用 defineModel 而不是 props —— 它们被 v-model 双向绑定，
// 而 props 只读，面板里赋值会**静默失败**：搜索/翻页看着能点但不动，
//    页面完全正常、控制台也没有报错。共用件（AdminSearchBox / AdminPageSize /
//    AdminPager）本身不用改，它们本来就正确 emit('update:*')。
const adminOrderJumpPage = defineModel('adminOrderJumpPage', { type: Number, required: true });
const adminOrderKeyword = defineModel('adminOrderKeyword', { type: String, required: true });
// 2026-10-12 把订单状态从 <select> 下拉改成 tab：状态筛选是高频操作，
// tab 比下拉更直观；同时用 defineModel 修复「下拉选了但筛选不生效」的隐患
// （原 <select v-model> 写的是只读 prop，选中值根本回不到 adminOrderStatus，
//  searchAdminOrders 永远拿不到所选状态）。
const adminOrderStatus = defineModel('adminOrderStatus', { type: String, required: true });
const { adminOrders, adminOrderTotalPages, shipForm, searchAdminOrders, changeAdminOrderPage, changeAdminOrderPageSize, goAdminOrderPage, resetAdminOrderSearch, openShipForm, adminOrderReady, submitShip, completeAdminOrder, cancelAdminOrder, openOrderDetail } = toRefs(props);

// 订单状态 tab：与后端 orders.status 枚举一一对应。
const adminOrderStatusTabs = [
  { key: '', label: '全部' },
  { key: 'PENDING_PAYMENT', label: '待付款' },
  { key: 'PAID', label: '待发货' },
  { key: 'SHIPPED', label: '待收货' },
  { key: 'COMPLETED', label: '已完成' },
  { key: 'CANCELED', label: '已取消' },
  { key: 'CLOSED', label: '已关闭' },
];
function selectAdminOrderStatus(key) {
  adminOrderStatus.value = key;
  // ⚠️ searchAdminOrders 来自 toRefs(props) 的解构 —— 它是一个 Ref 包着的函数，
  // 直接 searchAdminOrders() 会抛 TypeError（点击被吞、tab 高亮但不发请求）。
  // 必须先 .value() 拿到真正的函数再调用（2026-10-12 用户反馈「点了 tab 不分类」的根因）。
  searchAdminOrders.value();
}
</script>

<template>
  <div class="data-panel">
    <div class="toolbar">
      <AdminSearchBox v-model="adminOrderKeyword" placeholder="按订单号搜索" @search="searchAdminOrders" />
      <AdminPageSize v-model="adminOrders.size" @change="changeAdminOrderPageSize" />
      <button class="ghost" @click="resetAdminOrderSearch">重置</button>
      <span class="result-count">共 {{ adminOrders.total }} 笔订单</span>
    </div>

    <!-- 2026-10-12 订单状态 tab（替代原 <select> 下拉） -->
    <div class="order-tabs">
      <button
        v-for="t in adminOrderStatusTabs"
        :key="t.key"
        type="button"
        class="order-tab"
        :class="{ on: adminOrderStatus === t.key }"
        @click="selectAdminOrderStatus(t.key)"
      >{{ t.label }}</button>
    </div>

    <div v-if="!adminOrders.items?.length" class="empty">暂无订单</div>
    <div v-else class="table-wrap">
      <table class="admin-table">
        <thead>
          <tr>
            <th>订单号</th>
            <th>订单状态</th>
            <th>支付状态</th>
            <th>收货人</th>
            <th>实付金额</th>
            <th>物流信息</th>
            <th>下单时间</th>
            <th class="col-action">操作</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="order in adminOrders.items" :key="order.id">
            <tr>
              <td><span class="cell-strong order-no-link" @click="openOrderDetail(order)">{{ order.orderNo }}</span><span class="cell-sub">{{ fulfillmentLabel(order) }}</span><span v-if="order.remark" class="cell-sub">备注：{{ order.remark }}</span></td>
              <td><span :class="['tag', orderStatusTag(order.status)]">{{ orderStatusLabel(order) }}</span></td>
              <td>{{ formatPaymentStatus(order.paymentStatus) }}</td>
              <td>{{ order.receiverName || '-' }}<span class="cell-sub">{{ order.receiverPhone || '' }}</span></td>
              <td><span class="cell-strong">{{ money(order.payAmount) }}</span><span v-if="orderSavedTotal(order) > 0" class="cell-sub">已优惠 {{ money(orderSavedTotal(order)) }}</span></td>
              <td>
                <template v-if="order.shipNo">{{ order.shipCompany }}<span class="cell-sub">{{ order.shipNo }}</span></template>
                <!-- 已取消/已关闭的订单根本没有物流，显示 "-" 而不是误导性的「未发货」 -->
                <span v-else class="cell-muted">{{ ['CANCELED', 'CLOSED'].includes(order.status) ? '-' : '未发货' }}</span>
              </td>
              <td>{{ formatDate(order.createdAt) }}</td>
              <td class="col-action">
                <div class="row-actions">
                  <button v-if="order.status === 'PAID' && order.fulfillmentType === 'PICKUP'" @click="adminOrderReady(order)">备货完成</button>
                  <button v-else-if="order.status === 'PAID'" @click="openShipForm(order.id)">发货</button>
                  <button v-if="order.status === 'SHIPPED'" @click="completeAdminOrder(order.id)">完成</button>
                  <button v-if="['PENDING_PAYMENT', 'PAID'].includes(order.status)" class="ghost" @click="cancelAdminOrder(order.id)">取消</button>
                  <span v-if="!['PENDING_PAYMENT', 'PAID', 'SHIPPED'].includes(order.status)">-</span>
                </div>
              </td>
            </tr>
            <tr v-if="shipForm.orderId === order.id" class="row-extra-tr">
              <td colspan="8">
                <div class="row-extra">
                  <span class="extra-label">{{ order.fulfillmentType === 'EXPRESS' ? '填写快递信息' : '填写配送信息' }}</span>
                  <input v-model="shipForm.shipCompany" :placeholder="order.fulfillmentType === 'EXPRESS' ? '快递公司' : '配送方（如 美团配送）'" />
                  <input v-model="shipForm.shipNo" :placeholder="order.fulfillmentType === 'EXPRESS' ? '快递单号' : '运单号'" />
                  <button @click="submitShip(order.id)">确认发货</button>
                  <button class="ghost" @click="shipForm.orderId = null">取消</button>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
      <AdminPager :page="adminOrders.page" :total-pages="adminOrderTotalPages" v-model:jump-page="adminOrderJumpPage" @change="changeAdminOrderPage" @jump="goAdminOrderPage" />
    </div>
  </div>
</template>
