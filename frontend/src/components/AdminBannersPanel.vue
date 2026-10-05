<script setup>
import { toRefs } from 'vue';
// 后台「轮播管理」面板：从 AdminPanel.vue 整块搬过来的。
//
// ⚠️ adminCtx 解构列表照抄父级：漏解构一个就是运行时 undefined，模板编译不报错、要跑起来才炸。
import ImageUpload from './ImageUpload.vue';
import { imgFallback } from '../utils/format';

const props = defineProps({
  adminCtx: { type: Object, required: true },
  adminProductName: { type: Function, required: true },
});
const { adminBanners, bannerForm, bannerFormOpen, bannerUploading, openBannerForm, closeBannerForm, saveBanner, toggleBanner, deleteBanner, adminProducts } = props.adminCtx;
const { adminProductName } = toRefs(props);
</script>

<template>
  <div class="data-panel">
    <div class="toolbar">
      <button @click="openBannerForm(null)">新建轮播位</button>
      <span v-if="adminBanners.length" class="tag muted">共 {{ adminBanners.length }} 张</span>
    </div>

    <div v-if="bannerFormOpen" class="form-card admin-form-card">
      <div class="form-title">
        <span>{{ bannerForm.id ? '编辑轮播位' : '新建轮播位' }}</span>
        <small>建议 16:9 横图；宽超 1600px 或大 500KB 自动压缩，点击前台可跳转关联商品</small>
      </div>
      <div class="field">
        <span class="field-label">轮播图片 <i class="req">*</i></span>
        <ImageUpload v-model="bannerForm.imageUrl" :multiple="false" :max="1" type="banner" @upload-state="v => (bannerUploading = v)" />
      </div>
      <div class="admin-form-grid">
        <label class="field">
          <span class="field-label">跳转商品（可选）</span>
          <select v-model="bannerForm.linkProductId">
            <option :value="null">不跳转，仅展示</option>
            <option v-for="p in (adminProducts.items || [])" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
        </label>
        <label class="field">
          <span class="field-label">排序值</span>
          <input v-model.number="bannerForm.sortOrder" type="number" min="0" placeholder="数字越小越靠前，如 10" />
        </label>
        <p class="field-hint span-all" style="align-self: end;">是否展示用列表里的「停用 / 启用」控制；新建默认启用、排在最后。</p>
      </div>
      <div class="admin-form-foot">
        <button class="ghost" @click="closeBannerForm">取消</button>
        <button :disabled="bannerUploading" @click="saveBanner">{{ bannerUploading ? '图片上传中…' : '保存' }}</button>
      </div>
    </div>

    <template v-else>
      <div class="admin-cards" v-if="adminBanners.length">
        <div v-for="(b, bi) in adminBanners" :key="b.id" class="admin-card">
          <span class="banner-pos" :title="'前台轮播第 ' + (bi + 1) + ' 张'">{{ bi + 1 }}</span>
          <div class="banner-cover">
            <img v-if="b.imageUrl" :src="b.imageUrl" alt=""  loading="lazy" decoding="async" @error="imgFallback($event, b.name)" />
            <span v-else class="cover-empty">无图</span>
          </div>
          <div class="card-info">
            <p class="card-title">
              <span :class="['tag', Number(b.enabled) === 1 ? 'ok' : 'muted']">{{ Number(b.enabled) === 1 ? '启用中' : '已停用' }}</span>
              <span class="card-title-text">{{ b.linkProductId ? (adminProductName(b.linkProductId) || ('跳转商品 #' + b.linkProductId)) : '仅展示，点击不跳转' }}</span>
            </p>
            <p class="card-meta">
              <span>排序值 {{ b.sortOrder }} · 越小越靠前</span>
              <span>前台轮播第 {{ bi + 1 }} 张</span>
            </p>
          </div>
          <div class="card-actions">
            <button class="ghost" @click="openBannerForm(b)">编辑</button>
            <button class="ghost" @click="toggleBanner(b)">{{ Number(b.enabled) === 1 ? '停用' : '启用' }}</button>
            <button class="ghost danger" @click="deleteBanner(b)">删除</button>
          </div>
        </div>
      </div>
      <empty-state v-else icon="ticket" text="还没有轮播位：新建后前台轮播优先展示这里的内容" />
    </template>
  </div>
</template>
