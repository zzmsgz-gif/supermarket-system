<script setup>
import { toRefs } from 'vue';
// 后台「售后管理」面板：从 AdminPanel.vue 整块搬过来的（模板 56 行）。
//
// adminCtx 给数据与动作；分页/表单/审核这些由父级 composable 装配产出，
// 不在 adminCtx 里，所以按 props 注入。
//
// ⚠️ 解构列表照抄父级：漏一个就是运行时 undefined，模板编译不报错、
//    要跑起来才在 console 报 xxx is not a function。改完跑 _check_panels.py。
import { formatDate, formatRefundStatus, money } from '../utils/format';
import AdminPageSize from './AdminPageSize.vue';
import AdminPager from './AdminPager.vue';

const props = defineProps({
  adminCtx: { type: Object, required: true },
  searchRefunds: { type: Function, required: true },
  openOrderDetail: { type: Function, required: true },
  orderSavedTotal: { type: Function, required: true },
  refundStatusTag: { type: Function, required: true },
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
</script>

<template>
  <div class="data-panel">
    <div class="toolbar">
      <select v-model="refundStatusFilter" class="filter-select" @change="searchRefunds">
        <option value="APPLYING">申请中</option>
        <option value="APPROVED">已通过</option>
        <option value="REJECTED">已拒绝</option>
      </select>
      <AdminPageSize v-model="refundOrders.size" @change="changeRefundPageSize" />
      <button class="ghost" @click="resetRefundSearch">重置</button>
      <span class="result-count">共 {{ refundOrders.total }} 笔退款</span>
    </div>

    <div v-if="!refundOrders.items?.length" class="empty">暂无待处理的退款申请</div>
    <div v-else class="table-wrap">
      <table class="admin-table">
        <thead>
          <tr>
            <th>订单号</th>
            <th>订单状态</th>
            <th>退款金额</th>
            <th>退款原因</th>
            <th>申请时间</th>
            <th class="col-action">操作</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="order in refundOrders.items" :key="order.id">
            <tr>
              <td><span class="cell-strong order-no-link" @click="openOrderDetail(order)">{{ order.orderNo }}</span></td>
              <td><span :class="['tag', refundStatusTag(order.refundStatus)]">{{ formatRefundStatus(order.refundStatus) }}</span></td>
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
              <td colspan="6">
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
