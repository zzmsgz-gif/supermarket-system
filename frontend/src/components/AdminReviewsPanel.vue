<script setup>
import { toRefs } from 'vue';
// 后台「评价管理」面板：从 AdminPanel.vue 整块搬过来的（模板 73 行）。
//
// adminCtx 给数据与动作；分页/表单/审核这些由父级 composable 装配产出，
// 不在 adminCtx 里，所以按 props 注入。
//
// ⚠️ 解构列表照抄父级：漏一个就是运行时 undefined，模板编译不报错、
//    要跑起来才在 console 报 xxx is not a function。改完跑 _check_panels.py。
import { formatDate } from '../utils/format';
import AdminPager from './AdminPager.vue';

const props = defineProps({
  adminCtx: { type: Object, required: true },
  // 评价数据与加载动作来自 useAdminReviews（父级装配），不在 adminCtx 里
  adminReviews: { type: Object, required: true },
  adminReviewSummary: { type: Object, required: true },
  adminReviewRating: { type: Object, required: true },
  adminReviewReplied: { type: Object, required: true },
  adminReviewKeyword: { type: Object, required: true },
  loadAdminReviews: { type: Function, required: true },
  loadAdminReviewUnreplied: { type: Function, required: true },
  searchAdminReviews: { type: Function, required: true },
  changeAdminReviewPage: { type: Function, required: true },
  saveReviewReply: { type: Function, required: true },
  toggleReviewHidden: { type: Function, required: true },
  adminReviewTotalPages: { type: Number, required: true },
  // 回复草稿（按评价 id 存）也来自 useAdminReviews
  reviewReplyDraft: { type: Object, required: true },
});
const { adminReviews, adminReviewSummary, adminReviewRating, adminReviewReplied, adminReviewKeyword, loadAdminReviews, loadAdminReviewUnreplied, reviewReplyDraft } = toRefs(props);
const { searchAdminReviews, changeAdminReviewPage, saveReviewReply, toggleReviewHidden, adminReviewTotalPages } = props;
</script>

<template>
  <div class="data-panel">
    <div v-if="adminReviewSummary" class="stat-grid">
      <div class="stat-card">
        <small>总评价</small>
        <strong>{{ adminReviewSummary.total }}</strong>
      </div>
      <div class="stat-card">
        <small>未回复</small>
        <strong :class="{ danger: adminReviewSummary.unrepliedCount > 0 }">{{ adminReviewSummary.unrepliedCount }}</strong>
        <span class="growth flat">待你回话</span>
      </div>
      <div class="stat-card">
        <small>平均分</small>
        <strong>{{ adminReviewSummary.avgRating == null ? '—' : adminReviewSummary.avgRating }}</strong>
        <span class="growth flat">含已隐藏</span>
      </div>
      <div class="stat-card">
        <small>已隐藏</small>
        <strong>{{ adminReviewSummary.hiddenCount }}</strong>
        <span class="growth flat">前台不展示</span>
      </div>
    </div>

    <div class="toolbar">
      <select v-model="adminReviewRating" @change="searchAdminReviews">
        <option value="">全部星级</option>
        <option v-for="n in [5, 4, 3, 2, 1]" :key="n" :value="String(n)">{{ n }} 星</option>
      </select>
      <select v-model="adminReviewReplied" @change="searchAdminReviews">
        <option value="">全部状态</option>
        <option value="no">未回复</option>
        <option value="yes">已回复</option>
      </select>
      <input v-model="adminReviewKeyword" placeholder="搜索评价内容" @keyup.enter="searchAdminReviews" />
      <button class="ghost" @click="searchAdminReviews">搜索</button>
      <button class="ghost" @click="adminReviewRating = ''; adminReviewReplied = ''; adminReviewKeyword = ''; searchAdminReviews()">重置</button>
      <span class="result-count">共 {{ adminReviews.total }} 条</span>
    </div>

    <empty-state v-if="!adminReviews.items.length" icon="ticket" text="没有符合条件的评价" />
    <div v-else class="review-admin-list">
      <div v-for="r in adminReviews.items" :key="r.id" class="review-admin-row">
        <div class="rar-head">
          <span class="rar-stars">{{ '★'.repeat(r.rating || 0) }}{{ '☆'.repeat(5 - (r.rating || 0)) }}</span>
          <b class="rar-product">{{ r.productName }}</b>
          <span class="rar-user">{{ r.nickname || r.username || '匿名用户' }}</span>
          <small>{{ formatDate(r.createdAt) }}</small>
          <span class="rar-status" :class="r.hidden ? 'is-hidden' : (r.replyContent ? 'is-replied' : 'is-todo')">
            {{ r.hidden ? '已隐藏' : (r.replyContent ? '已回复' : '未回复') }}
          </span>
        </div>
        <p class="rar-content">{{ r.content || '（无文字评价，仅评分）' }}</p>
        <div v-if="r.imageUrls && r.imageUrls.length" class="rar-imgs">
          <img v-for="(url, i) in r.imageUrls" :key="i" :src="url" alt="评价晒图"  loading="lazy" decoding="async"/>
        </div>
        <div v-if="r.replyContent" class="rar-reply">
          <b>商家回复</b><small v-if="r.replyAt">（{{ formatDate(r.replyAt) }}）</small>：{{ r.replyContent }}
        </div>
        <div class="rar-actions">
          <input
            v-model="reviewReplyDraft[r.id]"
            maxlength="500"
            :placeholder="r.replyContent ? '修改回复内容…' : '回复这条评价（会显示在商品详情页）…'"
            @keyup.enter="saveReviewReply(r)"
          />
          <button @click="saveReviewReply(r)">{{ r.replyContent ? '更新回复' : '回复' }}</button>
          <button v-if="r.replyContent" class="ghost" @click="reviewReplyDraft[r.id] = ''; saveReviewReply(r)">撤回</button>
          <button class="ghost" @click="toggleReviewHidden(r)">{{ r.hidden ? '恢复展示' : '隐藏' }}</button>
        </div>
      </div>
    </div>

    <AdminPager :page="adminReviews.page" :total-pages="adminReviewTotalPages" @change="changeAdminReviewPage" />
  </div>
</template>
