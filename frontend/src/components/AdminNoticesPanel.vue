<script setup>
import { formatDate } from '../utils/format';
// 后台「公告管理」面板：从 AdminPanel.vue 整块搬过来的。
//
// ⚠️ adminCtx 解构列表照抄父级：漏解构一个就是运行时 undefined，模板编译不报错、要跑起来才炸。

const props = defineProps({
  adminCtx: { type: Object, required: true },
  // 公告类型 → 文案/配色。这两个函数住在 useAdminCategoryStock 里（历史原因，与分类库存无关），
  // 所以只能从父级注入 —— 它不是 adminCtx 的一部分。
  noticeTypeLabel: { type: Function, required: true },
  noticeTypeClass: { type: Function, required: true },
});
const { noticeTypeLabel, noticeTypeClass } = props;
const { adminAnnouncements, announcementForm, announcementFormOpen, openAnnouncementForm, closeAnnouncementForm, saveAnnouncement, toggleAnnouncement, deleteAnnouncement } = props.adminCtx;
</script>

<template>
  <div class="data-panel">
    <div class="toolbar">
      <button @click="openAnnouncementForm(null)">发布公告</button>
      <span v-if="adminAnnouncements.length" class="tag muted">共 {{ adminAnnouncements.length }} 条</span>
    </div>

    <div v-if="announcementFormOpen" class="form-card admin-form-card">
      <div class="form-title">
        <span>{{ announcementForm.id ? '编辑公告' : '发布公告' }}</span>
        <small>启用的公告展示在前台「商城公告」栏；<b>类型选「促销」的标题还会滚动出现在首页顶部利益条</b>（如新人福利文案），可在此随时改文改停用</small>
      </div>
      <div class="admin-form-grid">
        <label class="field span-all">
          <span class="field-label">标题 <i class="req">*</i></span>
          <input v-model="announcementForm.title" maxlength="120" placeholder="如：国庆期间配送时效调整" />
        </label>
        <label class="field">
          <span class="field-label">类型</span>
          <select v-model="announcementForm.type">
            <option value="NOTICE">公告</option>
            <option value="PROMOTION">促销</option>
            <option value="ACTIVITY">活动</option>
            <option value="SERVICE">服务</option>
            <option value="WARNING">提醒</option>
          </select>
        </label>
        <label class="field">
          <span class="field-label">排序值</span>
          <input v-model.number="announcementForm.sortOrder" type="number" min="0" placeholder="数字越小越靠前，如 10" />
        </label>
        <div class="field">
          <span class="field-label">是否启用</span>
          <label class="check-line"><input type="checkbox" v-model="announcementForm.enabled" /> 启用（前台可见）</label>
        </div>
      </div>
      <label class="field span-all">
        <span class="field-label">公告内容 <i class="req">*</i></span>
        <textarea v-model="announcementForm.content" rows="4" maxlength="500" placeholder="公告正文，最多 500 字"></textarea>
      </label>
      <div class="admin-form-foot">
        <button class="ghost" @click="closeAnnouncementForm">取消</button>
        <button @click="saveAnnouncement">保存</button>
      </div>
    </div>

    <template v-else>
      <div class="admin-cards" v-if="adminAnnouncements.length">
        <div v-for="a in adminAnnouncements" :key="a.id" class="admin-card">
          <span :class="['tag', 'type-chip', noticeTypeClass(a.type)]">{{ noticeTypeLabel(a.type) }}</span>
          <div class="card-info">
            <p class="card-title"><span class="card-title-text">{{ a.title }}</span></p>
            <p v-if="a.content" class="card-content">{{ a.content }}</p>
            <p class="card-meta">
              <span>发布 {{ formatDate(a.publishTime) }}</span>
              <span>排序 {{ a.sortOrder }} · 越小越靠前</span>
              <span :class="Number(a.enabled) === 1 ? 'on-word' : 'off-word'">{{ Number(a.enabled) === 1 ? '启用中' : '已停用' }}</span>
            </p>
          </div>
          <div class="card-actions">
            <button class="ghost" @click="openAnnouncementForm(a)">编辑</button>
            <button class="ghost" @click="toggleAnnouncement(a)">{{ Number(a.enabled) === 1 ? '停用' : '启用' }}</button>
            <button class="ghost danger" @click="deleteAnnouncement(a)">删除</button>
          </div>
        </div>
      </div>
      <empty-state v-else icon="receipt" text="还没有公告，点上方「发布公告」发第一条" />
    </template>
  </div>
</template>
