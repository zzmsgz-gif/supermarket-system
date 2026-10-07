<script setup>
import { toRefs, onMounted } from 'vue';
// 后台「售后管理」面板：从 AdminPanel.vue 整块搬过来的（模板 56 行）。
//
// adminCtx 给数据与动作；分页/表单/审核这些由父级 composable 装配产出，
// 不在 adminCtx 里，所以按 props 注入。
//
// ⚠️ 解构列表照抄父级：漏一个就是运行时 undefined，模板编译不报错、
//    要跑起来才在 console 报 xxx is not a function。改完跑 _check_panels.py。
import { formatDate, formatRefundStatus, money, userOrderStatus, refundStatusTag, orderSavedTotal } from '../utils/format';
import AdminPageSize from './AdminPageSize.vue';
import AdminPager from './AdminPager.vue';

const props = defineProps({
  adminCtx: { type: Object, required: true },
  searchRefunds: { type: Function, required: true },
  changeRefundPage: { type: Function, required: true },
  changeRefundPageSize: { type: Function, required: true },
  goRefundPage: { type: Function, required: true },
  resetRefundSearch: { type: Function, required: true },
  reviewAdminRefund: { type: Function, required: true },
  submitRefundReview: { type: Function, required: true },
  refundTotalPages: { type: Number, required: true },
  refundReviewForm: { type: Object, required: true },
});

// ⚠️ 下面这几个用 defineModel 而不是 props —— 它们被 v-model 双向绑定，
// 而 props 只读，面板里赋值会**静默失败**：搜索/翻页看着能点但不动，
//    页面完全正常、控制台也没有报错。共用件（AdminSearchBox / AdminPageSize /
//    AdminPager）本身不用改，它们本来就正确 emit('update:*')。
const refundStatusFilter = defineModel('refundStatusFilter', { type: String, required: true });
const refundJumpPage = defineModel('refundJumpPage', { type: Number, required: true });
const { refundOrders, loadRefundOrders } = props.adminCtx;
const { searchRefunds, changeRefundPage, changeRefundPageSize, goRefundPage, resetRefundSearch, reviewAdminRefund, submitRefundReview, refundTotalPages, refundReviewForm } = toRefs(props);

// ⚠️ **openOrderDetail 也不在 props 里**（父组件从没传），它挂在 adminCtx 上。
// refundStatusTag / orderSavedTotal / userOrderStatus / formatDate / money 都是
// utils/format.js 的纯函数，直接 import —— 不依赖父级注入。
//
// 这里踩过三次同源的坑（2026-10-07「售后管理打不开」）：
// 组件把 X 声明成 required props，父组件**根本没传** → Vue 给 undefined →
// 模板 `X(...)` 变成 `undefined(...)` → TypeError → **整个组件渲染中断、空白一片，
// 且必须刷新页面才恢复**。而且「有时正常」：这些调用只在**列表有数据时**才执行，
// 默认筛选「申请中」库里 0 条走空态分支就永远碰不到，一切到「全部」就崩。
// 所以下面的 props 列表必须与 AdminPanel.vue 实际传入的**逐字对齐**，多一个都是雷。
const { openOrderDetail } = props.adminCtx;

// 面板是 `v-if="adminMenu === 'refunds'"` 挂的 —— 切走就卸载、切回来就重建。
// 父级只在进后台时加载一次，所以本面板**挂载时自己再拉一次**：
// 不依赖任何卸载前残留的 state（否则「请求在卸载后才返回」会让 state 停在半路）。
onMounted(() => {
  loadRefundOrders();
});

// 面板是 `v-if="adminMenu === 'refunds'"` 挂的 —— 切走就卸载、切回来就重建。
// 父级只在进后台时加载一次，本面板**不自己拉数据**，所以「重建后拿什么渲染」全靠
// 上一次卸载前残留的 state。若那次请求恰好在卸载后才回来（切换菜单很快时很常见），
// state 会停在半路（items 空 / total NaN），AdminPager 算不出 totalPages 直接抛错，
// 结果就是「售后管理打不开，只有刷新页面才恢复」（2026-10-07 用户报）。
//
// 这里挂载即拉一次：数据一定来自本次请求，不依赖任何残留。
// loadRefundOrders 内部有 isAdmin 守卫 + 出错不影响别的面板。
onMounted(() => {
  loadRefundOrders();
});
</script>

<template>
  <div class="data-panel">
    <div class="toolbar">
      <!-- 「全部」放第一位且默认为空：原先只有三个具体状态、默认锁死「申请中」，
           一进来就显示「暂无待处理的退款申请」，像是页面坏了（线上 53 单 refund_status 全是 NONE）。
           加了「全部」至少能看到全量分布，再自己筛。 -->
      <select v-model="refundStatusFilter" class="filter-select" @change="searchRefunds">
        <option value="">全部</option>
        <option value="APPLYING">申请中</option>
        <option value="APPROVED">已通过</option>
        <option value="REJECTED">已拒绝</option>
      </select>
      <AdminPageSize v-model="refundOrders.size" @change="changeRefundPageSize" />
      <button class="ghost" @click="resetRefundSearch">重置</button>
      <span class="result-count">共 {{ refundOrders.total }} 笔{{ refundStatusFilter ? '（当前筛选）' : '' }}</span>
    </div>

    <div v-if="!refundOrders.items?.length" class="empty">
      {{ refundStatusFilter ? '没有该状态的售后记录' : '暂无售后申请。用户在订单列表/详情点「申请售后」后会出现在这里。' }}
    </div>
    <div v-else class="table-wrap">
      <table class="admin-table">
        <thead>
          <tr>
            <th>订单号</th>
            <!-- 2026-10-07 修正两处「列头与内容不符」：
                 ① 原「订单状态」列渲染的是 refundStatus（退款状态），已改名为「退款状态」，
                    并另加一列真正展示订单本身的 status（判断这单走到哪一步了）；
                 ② 原「申请时间」取的是 order.createdAt（下单时间），不是申请时间 ——
                    orders 表没有 refund_applied_at 字段，只能先显示下单时间并改名为
                    「下单时间」把话说准，别让审核人以为是申请时间（真要精确得加字段）。 -->
            <th>退款状态</th>
            <th>订单状态</th>
            <th>退款金额</th>
            <th>退款原因</th>
            <th>下单时间</th>
            <th class="col-action">操作</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="order in refundOrders.items" :key="order.id">
            <tr>
              <td><span class="cell-strong order-no-link" @click="openOrderDetail(order)">{{ order.orderNo }}</span></td>
              <td><span :class="['tag', refundStatusTag(order.refundStatus)]">{{ formatRefundStatus(order.refundStatus) }}</span></td>
              <td><span class="tag muted">{{ userOrderStatus(order).label }}</span></td>
              <td><span class="cell-strong">{{ money(order.payAmount) }}</span><span v-if="orderSavedTotal(order) > 0" class="cell-sub">已优惠 {{ money(orderSavedTotal(order)) }}</span></td>
              <td>{{ order.refundReason || '未填写' }}</td>
              <td>{{ formatDate(order.createdAt) }}</td>
              <td class="col-action">
                <div class="row-actions">
                  <button v-if="order.refundStatus === 'APPLYING'" @click="reviewAdminRefund(order.id, true)">同意退款</button>
                  <button v-if="order.refundStatus === 'APPLYING'" class="danger" @click="reviewAdminRefund(order.id, false)">拒绝</button>
                  <span v-else>-</span>
                </div>
              </td>
            </tr>
            <tr v-if="refundReviewForm.orderId === order.id" class="row-extra-tr">
              <td colspan="7">
                <div class="row-extra">
                  <span class="extra-label">{{ refundReviewForm.approved ? '同意退款' : '拒绝退款' }}，处理意见</span>
                  <input v-model="refundReviewForm.remark" placeholder="处理意见（选填）" />
                  <button @click="submitRefundReview(order.id)">提交</button>
                  <button class="ghost" @click="refundReviewForm.orderId = null">取消</button>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
      <AdminPager :page="refundOrders.page" :total-pages="refundTotalPages" v-model:jump-page="refundJumpPage" @change="changeRefundPage" @jump="goRefundPage" />
    </div>
  </div>
</template>
