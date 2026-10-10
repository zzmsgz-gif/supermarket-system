<script setup>
import { toRefs } from 'vue';
// 后台「优惠券管理」面板：从 AdminPanel.vue 整块搬过来的（模板 78 行）。
//
// adminCtx 给数据与动作；分页/表单/审核这些由父级 composable 装配产出，
// 不在 adminCtx 里，所以按 props 注入。
//
// ⚠️ 解构列表照抄父级：漏一个就是运行时 undefined，模板编译不报错、
//    要跑起来才在 console 报 xxx is not a function。改完跑 _check_panels.py。
import { money, formatDate } from '../utils/format';
import AdminSearchBox from './AdminSearchBox.vue';
import AdminPageSize from './AdminPageSize.vue';
import AdminPager from './AdminPager.vue';

const props = defineProps({
  adminCtx: { type: Object, required: true },
  // 券表单来自 useAdminCoupons（父级装配），不在 adminCtx 里
  couponForm: { type: Object, required: true },
  fillCouponPeriod: { type: Function, required: true },
  saveCoupon: { type: Function, required: true },
  searchAdminCoupons: { type: Function, required: true },
  changeAdminCouponPageSize: { type: Function, required: true },
  resetAdminCouponSearch: { type: Function, required: true },
  toggleCoupon: { type: Function, required: true },
  changeAdminCouponPage: { type: Function, required: true },
  goAdminCouponPage: { type: Function, required: true },
  adminCouponTotalPages: { type: Number, required: true },
});

// ⚠️ 下面这几个用 defineModel 而不是 props —— 它们被 v-model 双向绑定，
// 而 props 只读，面板里赋值会**静默失败**：搜索/翻页看着能点但不动，
//    页面完全正常、控制台也没有报错。共用件（AdminSearchBox / AdminPageSize /
//    AdminPager）本身不用改，它们本来就正确 emit('update:*')。
const adminCouponJumpPage = defineModel('adminCouponJumpPage', { type: Number, required: true });
const adminCouponKeyword = defineModel('adminCouponKeyword', { type: String, required: true });
const { adminCoupons, loadAdminCoupons } = props.adminCtx;
const { fillCouponPeriod, saveCoupon, searchAdminCoupons, changeAdminCouponPageSize, resetAdminCouponSearch, toggleCoupon, changeAdminCouponPage, goAdminCouponPage, adminCouponTotalPages, couponForm } = toRefs(props);
</script>

<template>
  <div class="data-panel">
    <div class="form-block">
      <div class="form-title">
        <span>新增优惠券</span>
        <small>满减券：订单达到「使用门槛」后立减「优惠金额」，优惠金额不能大于门槛；发放总量填 0 表示不限量</small>
        <button type="button" class="field-link title-link" @click="fillCouponPeriod(30)">一键填充：今天起 30 天</button>
      </div>
      <form class="compact-form form-bar" @submit.prevent="saveCoupon">
        <label class="field">
          <span class="field-label">优惠券名称 <i class="req">*</i></span>
          <input v-model="couponForm.name" placeholder="如：新用户满 50 减 10" />
        </label>
        <label class="field">
          <span class="field-label">使用门槛（元）<i class="req">*</i></span>
          <input v-model.number="couponForm.thresholdAmount" type="number" step="0.01" min="0" placeholder="满多少可用，如 50.00" />
        </label>
        <label class="field">
          <span class="field-label">优惠金额（元）<i class="req">*</i></span>
          <input v-model.number="couponForm.discountAmount" type="number" step="0.01" min="0" placeholder="立减多少，如 10.00" />
        </label>
        <!-- 领取方式（2026-10-10 新增）：默认「每人限领一次」= 改动前的行为，
             存量券不会因为加了这个字段就变成可重复领。 -->
        <label class="field">
          <span class="field-label">领取方式</span>
          <select v-model.number="couponForm.claimType">
            <option :value="0">每人限领一次</option>
            <option :value="1">每天可领一次</option>
          </select>
        </label>
        <label class="field">
          <span class="field-label">发放总量（张）</span>
          <input v-model.number="couponForm.totalCount" type="number" min="0" placeholder="0 表示不限量，如 100" />
        </label>
        <label class="field">
          <span class="field-label">生效时间 <i class="req">*</i></span>
          <input v-model="couponForm.startTime" type="datetime-local" />
        </label>
        <label class="field">
          <span class="field-label">过期时间 <i class="req">*</i></span>
          <input v-model="couponForm.endTime" type="datetime-local" />
        </label>
        <div class="field field-action">
          <button type="submit">新增优惠券</button>
        </div>
      </form>
    </div>
    <div v-if="adminCoupons.total === 0" class="empty">暂无优惠券</div>
    <template v-else>
      <div class="toolbar">
        <AdminSearchBox v-model="adminCouponKeyword" placeholder="按优惠券名称搜索" @search="searchAdminCoupons" />
        <AdminPageSize v-model="adminCoupons.size" @change="changeAdminCouponPageSize" />
        <button class="ghost" @click="resetAdminCouponSearch">重置</button>
        <span class="result-count">共 {{ adminCoupons.total }} 张优惠券</span>
      </div>
      <div v-if="!adminCoupons.items?.length" class="empty">没有匹配「{{ adminCouponKeyword }}」的优惠券</div>
      <div v-else class="table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>优惠券名称</th>
              <th>优惠力度</th>
              <th>有效期</th>
              <th>领取情况</th>
              <th>状态</th>
              <th class="col-action">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="coupon in adminCoupons.items" :key="coupon.id">
              <td><span class="cell-strong">{{ coupon.name }}</span></td>
              <td><span class="tag">满 {{ money(coupon.thresholdAmount) }} 减 {{ money(coupon.discountAmount) }}</span></td>
              <td>{{ formatDate(coupon.startTime) }}<span class="cell-sub">至 {{ formatDate(coupon.endTime) }}</span></td>
              <td>{{ coupon.receivedCount }}<span class="cell-sub">{{ coupon.totalCount ? `限量 ${coupon.totalCount} 张` : '不限量' }}</span></td>
              <td><span :class="['tag', coupon.status === 1 ? 'ok' : 'muted']">{{ coupon.status === 1 ? '启用中' : '已停用' }}</span></td>
              <td class="col-action">
                <div class="row-actions">
                  <button class="ghost" @click="toggleCoupon(coupon)">{{ coupon.status === 1 ? '停用' : '启用' }}</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <AdminPager :page="adminCoupons.page" :total-pages="adminCouponTotalPages" v-model:jump-page="adminCouponJumpPage" @change="changeAdminCouponPage" @jump="goAdminCouponPage" />
      </div>
    </template>
  </div>

  <!-- ===== 经营看板：时间维度 + 环比 + 结构分析 ===== -->
</template>
