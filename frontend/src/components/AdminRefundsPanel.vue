<script setup>
import { toRefs, onMounted } from 'vue';
// 后台「售后管理」面板（2026-10-14 扶正 return_request 新链路）。
// 数据来自 /admin/return-requests；审核动作（同意/驳回，是否寄回+运费由管理员在弹窗手动选择）、
// 「确认收货并退款」由父级 useAdminRefunds 装配，经 props 注入。
//
// ⚠️ props 必须与 AdminPanel.vue 实际传入逐字对齐，漏一个=运行时 undefined=白屏。
import { formatDate, money } from '../utils/format';
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
  confirmReturnReceived: { type: Function, required: true },
  reviewSubmitDisabled: { type: Function, required: true },
  refundTotalPages: { type: Number, required: true },
  refundReviewForm: { type: Object, required: true },
});

// ⚠️ 这两个用 defineModel 而不是 props —— v-model 双向绑定，写 prop 会静默失败。
const refundStatusFilter = defineModel('refundStatusFilter', { type: String, required: true });
const refundJumpPage = defineModel('refundJumpPage', { type: Number, required: true });
const { refundOrders, loadRefundOrders } = props.adminCtx;
const { searchRefunds, changeRefundPage, changeRefundPageSize, goRefundPage, resetRefundSearch, reviewAdminRefund, submitRefundReview, confirmReturnReceived, reviewSubmitDisabled, refundTotalPages, refundReviewForm } = toRefs(props);

// return 五态 → 徽标配色（与用户端 ReturnsPage 同源）
const RETURN_STATUS_TAG = { APPLYING: 'warn', WAITING_SHIP: 'warn', SHIPPED_BACK: 'info', APPROVED: 'ok', REJECTED: 'muted', PARTIAL: 'warn' };
const returnStatusCls = (s) => RETURN_STATUS_TAG[s] || 'muted';

// 售后类型标签：仅退款 / 退货退款（type 缺省按 REFUND→仅退款）
const RETURN_TYPE_LABEL = { REFUND: '仅退款', RETURN: '退货退款' };
const returnTypeLabel = (t) => RETURN_TYPE_LABEL[t] || '仅退款';
const returnTypeCls = (t) => (t === 'RETURN' ? 'info' : 'muted');

// 面板是 v-if 挂载：切走卸载、切回重建。挂载即拉一次，不依赖卸载前残留 state
//（否则「请求在卸载后才返回」会让 state 停在半路，AdminPager 算不出 totalPages 崩 → 面板打不开）。
onMounted(() => {
  loadRefundOrders();
});
</script>

<template>
  <div class="data-panel">
    <div class="toolbar">
      <select v-model="refundStatusFilter" class="filter-select" @change="searchRefunds">
        <!-- 2026-10-15 后台售后「进行中」汇总：合并 待审核(APPLYING)+待寄回(WAITING_SHIP)+已寄回(SHIPPED_BACK)，
             后端 listAll 按逗号多状态命中。放最前方便管理员一眼看到所有待办。
             2026-10-17 标签注明包含关系（用户曾误以为与「待审核」重复）：进行中是三态汇总，待审核只是其中一态。 -->
        <option value="APPLYING,WAITING_SHIP,SHIPPED_BACK">进行中（待审核+待寄回+已寄回）</option>
        <option value="APPLYING">待审核</option>
        <option value="">全部</option>
        <option value="WAITING_SHIP">待寄回</option>
        <option value="SHIPPED_BACK">已寄回待确认</option>
        <option value="APPROVED">退款完成</option>
        <option value="REJECTED">已驳回</option>
      </select>
      <AdminPageSize v-model="refundOrders.size" @change="changeRefundPageSize" />
      <button class="ghost" @click="resetRefundSearch">重置</button>
      <span class="result-count">共 {{ refundOrders.total }} 笔</span>
    </div>

    <div v-if="!refundOrders.items?.length" class="empty">
      暂无售后申请。用户在订单（已发货 / 已收货）点「申请退款 / 申请售后」后会出现在这里。
    </div>
    <div v-else class="table-wrap">
      <table class="admin-table">
        <thead>
          <tr>
            <th>售后单</th>
            <th>订单号</th>
            <th>商品</th>
            <th>状态</th>
            <th>类型</th>
            <th>退款金额</th>
            <th>申请原因</th>
            <th>运费</th>
            <th>运单号</th>
            <th>申请时间</th>
            <th class="col-action">操作</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="r in refundOrders.items" :key="r.id">
            <tr>
              <td><span class="cell-strong">#{{ r.id }}</span></td>
              <td>{{ r.orderNo || '-' }}</td>
              <td>
                <template v-if="r.itemProductName">
                  <div class="rp-item-cell">
                    <img v-if="r.itemCoverUrl" :src="r.itemCoverUrl" class="rp-item-img" alt="商品" loading="lazy" decoding="async" />
                    <div class="rp-item-text">
                      <div class="rp-item-name">{{ r.itemProductName }}</div>
                      <small>{{ money(r.itemUnitPrice) }} × {{ r.itemQuantity }}</small>
                    </div>
                  </div>
                </template>
                <template v-else>整单</template>
              </td>
              <td><span :class="['tag', returnStatusCls(r.status)]">{{ r.statusLabel || r.status }}</span></td>
              <td><span :class="['tag', returnTypeCls(r.type)]">{{ returnTypeLabel(r.type) }}</span></td>
              <td><span class="cell-strong">{{ money(r.refundAmount) }}</span></td>
              <td>{{ r.reason || '未填写' }}</td>
              <td>{{ r.freightBorneBy ? r.freightLabel : '待判定' }}</td>
              <td>{{ r.trackingNo || '-' }}</td>
              <td>{{ formatDate(r.createdAt) }}</td>
              <td class="col-action">
                <div class="row-actions">
                  <button v-if="r.status === 'APPLYING'" @click="reviewAdminRefund(r.id, true)">同意退款</button>
                  <button v-if="r.status === 'APPLYING'" class="danger" @click="reviewAdminRefund(r.id, false)">驳回</button>
                  <button v-else-if="r.status === 'SHIPPED_BACK'" @click="confirmReturnReceived(r.id)">确认收货并退款</button>
                  <span v-else-if="r.status === 'WAITING_SHIP'" class="cell-sub">待用户寄回</span>
                  <span v-else>-</span>
                </div>
              </td>
            </tr>
            <tr v-if="refundReviewForm.id === r.id" class="row-extra-tr">
              <td colspan="11">
                <div class="row-extra">
                  <span class="extra-label">{{ refundReviewForm.approved ? '同意退款' : '驳回申请' }}</span>
                  <template v-if="refundReviewForm.approved">
                    <label class="extra-inline">
                      是否寄回
                      <select v-model="refundReviewForm.needReturnMode">
                        <option value="auto">自动(按订单状态)</option>
                        <option value="yes">是，需寄回</option>
                        <option value="no">否，免寄回直接退</option>
                      </select>
                    </label>
                    <label v-if="refundReviewForm.needReturnMode !== 'no'" class="extra-inline">
                      运费承担方
                      <select v-model="refundReviewForm.freightBorneBy">
                        <option value="BUYER">用户承担</option>
                        <option value="SELLER">商家承担</option>
                      </select>
                    </label>
                  </template>
                  <input v-model="refundReviewForm.remark" :placeholder="refundReviewForm.approved ? '处理意见（选填）' : '驳回理由（必填）'" />
                  <button :disabled="reviewSubmitDisabled()" @click="submitRefundReview(r.id)">提交</button>
                  <button class="ghost" @click="refundReviewForm.id = null">取消</button>
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

<style scoped>
.extra-inline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #6b7280;
  white-space: nowrap;
}
.extra-inline select {
  padding: 5px 8px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #1f2937;
  font-size: 13px;
}
</style>
