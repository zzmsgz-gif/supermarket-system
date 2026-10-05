<script setup>
// 后台「门店自提」面板：从 AdminPanel.vue 整块搬过来的。
//
// 依赖走显式 props（不接 adminCtx）。这个面板没有 v-model 双向绑定 ——
// 表单字段全绑在 storeForm 上，那是 reactive 对象，传引用跨组件写安全，
// 所以不需要 defineModel。
import { toRefs } from 'vue';

const props = defineProps({
  adminStores: { type: Array, required: true },
  storeForm: { type: Object, required: true },
  storeFormOpen: { type: Boolean, required: true },
  storeEditingId: { type: Object, required: true },
  openStoreForm: { type: Function, required: true },
  closeStoreForm: { type: Function, required: true },
  saveStore: { type: Function, required: true },
  toggleStoreStatus: { type: Function, required: true },
  deleteStore: { type: Function, required: true },
  loadAdminStores: { type: Function, required: true },
});
const { adminStores, storeForm, storeFormOpen, storeEditingId, openStoreForm, closeStoreForm, saveStore, toggleStoreStatus, deleteStore, loadAdminStores } = toRefs(props);
</script>

<template>
  <div class="data-panel">
    <div class="toolbar">
      <button class="primary" @click="openStoreForm(null)">+ 新增门店</button>
      <span class="result-count">共 {{ adminStores.length }} 家门店 · 营业 {{ adminStores.filter((s) => Number(s.status) === 1).length }} 家</span>
      <button class="ghost" @click="loadAdminStores">刷新</button>
    </div>

    <form v-if="storeFormOpen" class="compact-form form-bar" @submit.prevent="saveStore">
      <label class="field">
        <span class="field-label">门店名称 <i class="req">*</i></span>
        <input v-model="storeForm.name" maxlength="80" placeholder="如：南山科技园店" />
      </label>
      <label class="field field-wide">
        <span class="field-label">门店地址 <i class="req">*</i></span>
        <input v-model="storeForm.address" maxlength="255" placeholder="如：深圳市南山区科技园南区 8 栋 1 层" />
      </label>
      <label class="field">
        <span class="field-label">联系电话</span>
        <input v-model="storeForm.phone" maxlength="20" placeholder="选填" />
      </label>
      <label class="field">
        <span class="field-label">营业时间</span>
        <input v-model="storeForm.businessHours" maxlength="60" placeholder="如 08:00-22:00" />
      </label>
      <label class="field">
        <span class="field-label">城市</span>
        <input v-model="storeForm.city" maxlength="40" placeholder="选填" />
      </label>
      <label class="field">
        <span class="field-label">区县</span>
        <input v-model="storeForm.district" maxlength="40" placeholder="选填" />
      </label>
      <label class="field field-wide">
        <span class="field-label">即时配送服务区域</span>
        <input
          v-model="storeForm.serviceAreas"
          maxlength="255"
          placeholder="如 深圳市/南山区,深圳市/福田区（留空 = 仅本店所在城市/区）"
        />
        <small class="field-hint">
          逗号分隔的「城市/区县」；只写城市表示全城可达。即时配送可送达范围 = <b>所有营业中门店</b>的并集，因此门店停业会收缩配送范围；快递配送不受此限制。
        </small>
      </label>
      <label class="field">
        <span class="field-label">排序（越小越靠前）</span>
        <input v-model.number="storeForm.sortNo" type="number" min="0" />
      </label>
      <label class="field">
        <span class="field-label">状态</span>
        <select v-model.number="storeForm.status">
          <option :value="1">营业（前台可选）</option>
          <option :value="0">停业（前台不可选）</option>
        </select>
      </label>
      <label class="field field-wide">
        <span class="field-label">自提须知</span>
        <input v-model="storeForm.pickupNotice" maxlength="255" placeholder="显示在结算页门店下方，如：下单后约 1 小时可自提，凭自提码取货" />
      </label>
      <p class="field-hint field-wide" v-if="storeEditingId">正在编辑「{{ storeForm.name || '门店' }}」，保存后更新该门店</p>
      <div class="store-form-actions field-wide">
        <button class="primary" type="submit">保存门店</button>
        <button class="ghost" type="button" @click="closeStoreForm">取消</button>
      </div>
    </form>

    <empty-state v-if="!adminStores.length" icon="cart" text="还没有门店：新增后即可在结算页选择「门店自提」" />
    <div v-else class="table-wrap">
      <table class="admin-table">
        <thead>
          <tr>
            <th>门店名称</th>
            <th>地址</th>
            <th>电话</th>
            <th>营业时间</th>
            <th>排序</th>
            <th>状态</th>
            <th class="col-action">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="store in adminStores" :key="store.id">
            <td>
              <span class="cell-strong">{{ store.name }}</span>
              <span v-if="store.city || store.district" class="cell-sub">{{ store.city }}{{ store.district }}</span>
            </td>
            <td>{{ store.address }}</td>
            <td>{{ store.phone || '-' }}</td>
            <td>{{ store.businessHours || '-' }}</td>
            <td>{{ store.sortNo }}</td>
            <td><span :class="['tag', Number(store.status) === 1 ? 'ok' : 'muted']">{{ Number(store.status) === 1 ? '营业' : '停业' }}</span></td>
            <td class="col-action">
              <div class="row-actions">
                <button class="ghost" @click="openStoreForm(store)">编辑</button>
                <button :class="Number(store.status) === 1 ? 'danger' : ''" @click="toggleStoreStatus(store)">{{ Number(store.status) === 1 ? '停业' : '恢复营业' }}</button>
                <button class="danger ghost" @click="deleteStore(store)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
