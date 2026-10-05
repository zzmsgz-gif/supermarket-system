<script setup>
import { toRefs } from 'vue';
// 后台「营销活动」面板：从 AdminPanel.vue 整块搬过来的。
//
// 依赖刻意走显式 props 而不是 adminCtx：这个面板只服务一个 tab，把真正用到的东西列全，
// 一眼就能看出「它为什么需要这些」。接 adminCtx 会让依赖变成隐式的。
import { formatDate } from '../utils/format';

const props = defineProps({
  activityDiscountLabel: { type: Function, required: true },
  activityForm: { type: Object, required: true },
  activityProducts: { type: Array, required: true },
  activityScopeLabel: { type: Function, required: true },
  activityTypeLabel: { type: Function, required: true },
  adminActivities: { type: Object, required: true },
  adminActivityTotalPages: { type: Number, required: true },
  categories: { type: Array, required: true },
  changeAdminActivityPage: { type: Function, required: true },
  changeAdminActivityPageSize: { type: Function, required: true },
  deleteActivity: { type: Function, required: true },
  editActivity: { type: Function, required: true },
  fillActivityPeriod: { type: Function, required: true },
  onActivityScopeChange: { type: Function, required: true },
  resetActivityForm: { type: Function, required: true },
  resetAdminActivitySearch: { type: Function, required: true },
  saveActivity: { type: Function, required: true },
  searchAdminActivities: { type: Function, required: true },
  toggleActivity: { type: Function, required: true },
});

// ⚠️ 下面这几个用 defineModel 而不是 props —— 它们被 v-model 双向绑定，
// 而 props 只读，面板里赋值会**静默失败**：搜索/翻页看着能点但不动，
//    页面完全正常、控制台也没有报错。共用件（AdminSearchBox / AdminPageSize /
//    AdminPager）本身不用改，它们本来就正确 emit('update:*')。
const adminActivityJumpPage = defineModel('adminActivityJumpPage', { type: Number, required: true });
const adminActivityKeyword = defineModel('adminActivityKeyword', { type: String, required: true });
const { activityDiscountLabel, activityForm, activityProducts, activityScopeLabel, activityTypeLabel, adminActivities, adminActivityTotalPages, categories, changeAdminActivityPage, changeAdminActivityPageSize, deleteActivity, editActivity, fillActivityPeriod, onActivityScopeChange, resetActivityForm, resetAdminActivitySearch, saveActivity, searchAdminActivities, toggleActivity } = toRefs(props);
</script>

<template>
  <div class="data-panel">
    <div class="form-block">
      <div class="form-title">
        <span>{{ activityForm.id ? '编辑活动' : '新增营销活动' }}</span>
        <small>满减：订单达到「门槛金额」后立减「优惠金额」（减额不能大于门槛）；折扣：按「折扣率」打折（0.9 = 9 折，可设最低消费门槛）。全场活动无需选择类目/商品。</small>
        <button type="button" class="field-link title-link" @click="fillActivityPeriod(30)">一键填充：今天起 30 天</button>
      </div>
      <form class="compact-form form-bar" @submit.prevent="saveActivity">
        <label class="field">
          <span class="field-label">活动名称 <i class="req">*</i></span>
          <input v-model="activityForm.name" placeholder="如：全场满 200 减 30" />
        </label>
        <label class="field">
          <span class="field-label">活动类型 <i class="req">*</i></span>
          <select v-model="activityForm.type">
            <option value="FULL_REDUCTION">满减</option>
            <option value="DISCOUNT">折扣</option>
            <option value="PROMOTION">促销文案（首页顶栏滚动展示，不参与计价）</option>
          </select>
        </label>
        <label v-if="activityForm.type !== 'PROMOTION'" class="field">
          <span class="field-label">作用范围 <i class="req">*</i></span>
          <select v-model="activityForm.scope" @change="onActivityScopeChange">
            <option value="ALL">全场</option>
            <option value="CATEGORY">指定类目</option>
            <option value="PRODUCT">指定商品</option>
          </select>
        </label>
        <label v-if="activityForm.scope === 'CATEGORY'" class="field">
          <span class="field-label">适用类目 <i class="req">*</i></span>
          <select v-model.number="activityForm.categoryId">
            <option :value="0" disabled>请选择类目</option>
            <option v-for="cat in categories" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
          </select>
        </label>
        <label v-if="activityForm.scope === 'PRODUCT'" class="field">
          <span class="field-label">适用商品 <i class="req">*</i></span>
          <select v-model.number="activityForm.productId">
            <option :value="0" disabled>请选择商品</option>
            <option v-for="p in activityProducts" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
        </label>
        <label v-if="activityForm.type !== 'PROMOTION'" class="field">
          <span class="field-label">{{ activityForm.type === 'DISCOUNT' ? '最低消费（元）' : '满减门槛（元）' }} <i class="req">*</i></span>
          <input v-model.number="activityForm.threshold" type="number" step="0.01" min="0" :placeholder="activityForm.type === 'DISCOUNT' ? '可选，0 表示无门槛' : '满多少可用，如 200.00'" />
        </label>
        <label v-if="activityForm.type !== 'PROMOTION'" class="field">
          <span class="field-label">{{ activityForm.type === 'DISCOUNT' ? '折扣率（0.9=9折）' : '优惠金额（元）' }} <i class="req">*</i></span>
          <input v-model.number="activityForm.discount" type="number" step="0.01" min="0" :placeholder="activityForm.type === 'DISCOUNT' ? '0.01~0.99，如 0.90' : '立减多少，如 30.00'" />
        </label>
        <label class="field">
          <span class="field-label">生效时间 <i class="req">*</i></span>
          <input v-model="activityForm.startTime" type="datetime-local" />
        </label>
        <label class="field">
          <span class="field-label">过期时间 <i class="req">*</i></span>
          <input v-model="activityForm.endTime" type="datetime-local" />
        </label>
        <label class="field">
          <span class="field-label">优先级</span>
          <input v-model.number="activityForm.priority" type="number" min="0" placeholder="数值越大越优先" />
        </label>
        <div class="field field-action">
          <button type="submit">{{ activityForm.id ? '保存修改' : '新增活动' }}</button>
          <button v-if="activityForm.id" type="button" class="ghost" @click="resetActivityForm">取消编辑</button>
        </div>
      </form>
    </div>
    <div v-if="adminActivities.total === 0 && !adminActivityKeyword" class="empty">暂无营销活动，使用上方表单创建第一个活动</div>
    <template v-else>
      <div class="toolbar">
        <AdminSearchBox v-model="adminActivityKeyword" placeholder="按活动名称搜索" @search="searchAdminActivities" />
        <AdminPageSize v-model="adminActivities.size" @change="changeAdminActivityPageSize" />
        <button class="ghost" @click="resetAdminActivitySearch">重置</button>
        <span class="result-count">共 {{ adminActivities.total }} 个活动</span>
      </div>
      <div v-if="!adminActivities.items?.length" class="empty">没有匹配「{{ adminActivityKeyword }}」的活动</div>
      <div v-else class="table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>活动名称</th>
              <th>类型 / 范围</th>
              <th>优惠力度</th>
              <th>有效期</th>
              <th>状态</th>
              <th class="col-action">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="act in adminActivities.items" :key="act.id">
              <td><span class="cell-strong">{{ act.name }}</span></td>
              <td>
                <span class="tag">{{ activityTypeLabel(act.type) }}</span>
                <span class="tag muted">{{ activityScopeLabel(act.scope) }}</span>
              </td>
              <td><span class="tag">{{ activityDiscountLabel(act) }}</span></td>
              <td>{{ formatDate(act.startTime) }}<span class="cell-sub">至 {{ formatDate(act.endTime) }}</span></td>
              <td><span :class="['tag', act.status === 1 ? 'ok' : 'muted']">{{ act.status === 1 ? '进行中' : '已停用' }}</span></td>
              <td class="col-action">
                <div class="row-actions">
                  <button class="ghost" @click="editActivity(act)">编辑</button>
                  <button class="ghost" @click="toggleActivity(act)">{{ act.status === 1 ? '停用' : '启用' }}</button>
                  <button class="ghost danger" @click="deleteActivity(act)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <AdminPager :page="adminActivities.page" :total-pages="adminActivityTotalPages" v-model:jump-page="adminActivityJumpPage" @change="changeAdminActivityPage" @jump="goAdminActivityPage" />
      </div>
    </template>
  </div>
</template>
