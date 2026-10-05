<script setup>
// 后台「热搜词」面板：从 AdminPanel.vue 整块搬过来的。
//
// ⚠️ adminCtx 解构列表照抄父级：漏解构一个就是运行时 undefined，模板编译不报错、要跑起来才炸。

const props = defineProps({
  adminCtx: { type: Object, required: true },
});
const { adminHotSearches, hotSearchForm, hotSearchFormOpen, openHotSearchForm, closeHotSearchForm, saveHotSearch, toggleHotSearch, deleteHotSearch } = props.adminCtx;
</script>

<template>
  <div class="data-panel">
    <div class="toolbar">
      <button @click="openHotSearchForm(null)">新增热搜词</button>
      <span v-if="adminHotSearches.length" class="tag muted">共 {{ adminHotSearches.length }} 条</span>
    </div>

    <div v-if="hotSearchFormOpen" class="form-card admin-form-card">
      <div class="form-title">
        <span>{{ hotSearchForm.id ? '编辑热搜词' : '新增热搜词' }}</span>
        <small>这些词显示在首页头部搜索框下面的「热搜」那一排，<b>点击即按「搜索词」跳转搜索</b>；
          「展示文案」留空就用搜索词本身（两者可以不同，例如显示「纯牛奶」而实际搜「牛奶」）</small>
      </div>
      <div class="admin-form-grid">
        <label class="field">
          <span class="field-label">搜索词 <i class="req">*</i></span>
          <input v-model="hotSearchForm.keyword" maxlength="30" placeholder="如：牛奶（点一下就是搜它）" />
        </label>
        <label class="field">
          <span class="field-label">展示文案</span>
          <input v-model="hotSearchForm.label" maxlength="30" placeholder="可留空；如显示「纯牛奶」" />
        </label>
        <label class="field">
          <span class="field-label">排序值</span>
          <input v-model.number="hotSearchForm.sortOrder" type="number" min="0" placeholder="数字越小越靠前，如 10" />
        </label>
        <div class="field">
          <span class="field-label">是否启用</span>
          <label class="check-line"><input type="checkbox" v-model="hotSearchForm.enabled" /> 启用（前台可见）</label>
        </div>
      </div>
      <div class="admin-form-foot">
        <button class="ghost" @click="closeHotSearchForm">取消</button>
        <button @click="saveHotSearch">保存</button>
      </div>
    </div>

    <template v-else>
      <div class="admin-cards" v-if="adminHotSearches.length">
        <div v-for="h in adminHotSearches" :key="h.id" class="admin-card">
          <span class="tag">{{ h.label }}</span>
          <div class="card-info">
            <p class="card-title"><span class="card-title-text">点击后搜索「{{ h.keyword }}」</span></p>
            <p class="card-meta">
              <span>排序 {{ h.sortOrder }} · 越小越靠前</span>
              <span :class="Number(h.enabled) === 1 ? 'on-word' : 'off-word'">{{ Number(h.enabled) === 1 ? '启用中' : '已停用' }}</span>
            </p>
          </div>
          <div class="card-actions">
            <button class="ghost" @click="openHotSearchForm(h)">编辑</button>
            <button class="ghost" @click="toggleHotSearch(h)">{{ Number(h.enabled) === 1 ? '停用' : '启用' }}</button>
            <button class="ghost danger" @click="deleteHotSearch(h)">删除</button>
          </div>
        </div>
      </div>
      <empty-state v-else icon="star" text="还没有热搜词，点上方「新增热搜词」加一条（全部停用或删空时，前台那一排会整块隐藏）" />
    </template>
  </div>
</template>
