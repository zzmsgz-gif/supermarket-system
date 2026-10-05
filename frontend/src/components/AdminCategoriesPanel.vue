<script setup>
import { toRefs } from 'vue';
// 后台「分类管理」面板：从 AdminPanel.vue 整块搬过来的。
//
// 依赖走显式 props（不接 adminCtx）：面板只服务一个 tab，列全依赖比藏起来好。
// 分页相关的「回到第 1 页」走 resetCategoryPage()：
// 模板里直接写 `categoryPage = 1` 搬进子组件后改的是解包副本，父级不动。
import AdminSearchBox from './AdminSearchBox.vue';
import AdminPageSize from './AdminPageSize.vue';
import AdminPager from './AdminPager.vue';
import ImageUpload from './ImageUpload.vue';

const props = defineProps({
  categories: { type: Array, required: true },
  categoryPage: { type: Number, required: true },
  categorySize: { type: Number, required: true },
  categoryFiltered: { type: Array, required: true },
  categoryTotalPages: { type: Number, required: true },
  categoryPageItems: { type: Array, required: true },
  changeCategoryPage: { type: Function, required: true },
  goCategoryPage: { type: Function, required: true },
  resetCategoryPage: { type: Function, required: true },
  resetCategorySearch: { type: Function, required: true },
  categoryForm: { type: Object, required: true },
  saveCategory: { type: Function, required: true },
  categoryName: { type: Function, required: true },
});

// ⚠️ 下面这几个用 defineModel 而不是 props —— 它们被 v-model 双向绑定，
// 而 props 只读，面板里赋值会**静默失败**：搜索/翻页看着能点但不动，
//    页面完全正常、控制台也没有报错。共用件（AdminSearchBox / AdminPageSize /
//    AdminPager）本身不用改，它们本来就正确 emit('update:*')。
const categoryJumpPage = defineModel('categoryJumpPage', { type: Number, required: true });
const categoryKeyword = defineModel('categoryKeyword', { type: String, required: true });
const { categories, categoryPage, categorySize, categoryFiltered, categoryTotalPages, categoryPageItems, changeCategoryPage, goCategoryPage, resetCategoryPage, resetCategorySearch, categoryForm, saveCategory, categoryName } = toRefs(props);
</script>

<template>
  <div class="data-panel">
    <div class="form-block">
      <div class="form-title">
        <span>新增分类</span>
        <small>选「顶级分类」创建一级导航，选其它分类则在其下创建二级分类</small>
      </div>
      <form class="compact-form form-bar" @submit.prevent="saveCategory">
        <label class="field">
          <span class="field-label">分类名称 <i class="req">*</i></span>
          <input v-model="categoryForm.name" placeholder="如：进口水果" />
        </label>
        <label class="field">
          <span class="field-label">上级分类</span>
          <select v-model.number="categoryForm.parentId">
            <option :value="0">顶级分类（无上级）</option>
            <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
          </select>
        </label>
        <label class="field">
          <span class="field-label">排序号</span>
          <input v-model.number="categoryForm.sortNo" type="number" min="0" placeholder="数字越小越靠前，如 10" />
        </label>
        <label class="field">
          <span class="field-label">分类图标</span>
          <ImageUpload v-model="categoryForm.iconUrl" :multiple="false" :max="1" type="category" />
        </label>
        <div class="field field-action">
          <button type="submit">新增分类</button>
        </div>
      </form>
    </div>
    <div v-if="!categories.length" class="empty">暂无分类</div>
    <template v-else>
      <div class="toolbar">
        <AdminSearchBox v-model="categoryKeyword" placeholder="按分类名称搜索" @search="resetCategoryPage" />
        <AdminPageSize v-model="categorySize" @change="resetCategoryPage" />
        <button class="ghost" @click="resetCategorySearch">重置</button>
        <span class="result-count">共 {{ categoryFiltered.length }} 个分类</span>
      </div>
      <div v-if="!categoryPageItems.length" class="empty">没有匹配「{{ categoryKeyword }}」的分类</div>
      <div v-else class="table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>图标</th>
              <th>分类名称</th>
              <th>上级分类</th>
              <th>排序</th>
              <th>层级</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="category in categoryPageItems" :key="category.id">
              <td>
                <img v-if="category.iconUrl" :src="category.iconUrl" class="cat-icon-thumb" :alt="category.name"  loading="lazy" decoding="async"/>
                <span v-else class="muted">—</span>
              </td>
              <td><span class="cell-strong">{{ category.name }}</span></td>
              <td>{{ categoryName(category.parentId) }}</td>
              <td>{{ category.sortNo }}</td>
              <td><span :class="['tag', category.parentId ? 'muted' : '']">{{ category.parentId ? '二级分类' : '一级分类' }}</span></td>
            </tr>
          </tbody>
        </table>
        <AdminPager :page="categoryPage" :total-pages="categoryTotalPages" v-model:jump-page="categoryJumpPage" @change="changeCategoryPage" @jump="goCategoryPage" />
      </div>
    </template>
  </div>
</template>
