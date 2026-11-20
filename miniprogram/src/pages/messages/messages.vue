<template>
  <view class="msg-page">
    <view class="top">
      <text class="t-title">消息中心</text>
      <text class="t-all" @click="allRead" v-if="messages.some((m) => !m.isRead)">全部已读</text>
    </view>

    <view
      v-for="m in messages"
      :key="m.id"
      class="msg"
      :class="{ unread: !m.isRead, important: isImportant(m) }"
      @click="open(m)"
    >
      <view class="m-top">
        <text class="m-type" :class="typeClass(m.type)">{{ typeLabel(m.type) }}</text>
        <text class="m-title">{{ m.title }}</text>
        <text class="m-dot" v-if="!m.isRead"></text>
      </view>
      <view class="m-content">{{ m.content }}</view>
      <view class="m-time">{{ formatTime(m.createdAt) }}</view>
    </view>

    <view class="empty" v-if="!loading && !messages.length">暂无消息</view>
    <view class="loading" v-if="loading">加载中…</view>
    <view class="more" v-else-if="hasMore" @click="loadMore">点击加载更多</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app'
import { listMessages, markAllRead, markRead } from '@/api/message'
import { formatTime } from '@/utils/format'

const messages = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const hasMore = ref(false)

/**
 * 消息类型中文名。类型以后端 UserMessage 常量为准：
 * ORDER / MEMBER / COUPON / SYSTEM（原来这里漏了 MEMBER、COUPON，
 * 那两类消息会直接把英文 "MEMBER" 显示出来 —— 2026-11-20 补齐）。
 * ACTIVITY / PROMOTION / PRICE_DROP 是更早的命名，保留映射兼容历史数据。
 */
function typeLabel(t) {
  const map = {
    ORDER: '订单',
    MEMBER: '会员',
    COUPON: '优惠券',
    SYSTEM: '系统',
    ACTIVITY: '活动',
    PROMOTION: '优惠',
    PRICE_DROP: '降价'
  }
  return map[t] || t || '通知'
}

/**
 * 需要用户特别留意的消息（涉及钱、需要处理）。
 *
 * 为什么按关键词而不是按 type 判：后端把「订单已发出」和「退款已到账 / 售后未通过」
 * 都发成 TYPE_ORDER，光看类型分不出轻重；而这几类漏看代价最大（钱的事）。
 * 命中后整条消息加红色左边条 + 浅红底，比默认模板显眼。
 */
const IMPORTANT_RE = /退款|售后|未通过|失败|异常|缺货/
function isImportant(m) {
  return IMPORTANT_RE.test((m.title || '') + ' ' + (m.content || ''))
}

/** 类型标签配色：系统=橙、会员=紫、优惠券=金，订单等其余保持主色绿 */
function typeClass(t) {
  if (t === 'SYSTEM') return 'sys'
  if (t === 'MEMBER') return 'member'
  if (t === 'COUPON') return 'coupon'
  return ''
}

async function load(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 0
    messages.value = []
  }
  loading.value = true
  try {
    const res = await listMessages({ page: page.value + 1, size: size.value })
    const items = (res && res.items) ? res.items : []
    total.value = (res && res.total) || 0
    messages.value = page.value === 0 ? items : messages.value.concat(items)
    page.value += 1
    hasMore.value = messages.value.length < total.value
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function open(m) {
  try {
    if (!m.isRead) await markRead(m.id)
  } catch (e) {}
  if (m.linkView === 'order' && m.linkRef) {
    uni.navigateTo({ url: '/pages/order-detail/order-detail?id=' + m.linkRef })
  }
  load(true)
}

async function allRead() {
  try {
    await markAllRead()
    messages.value.forEach((m) => (m.isRead = true))
    uni.showToast({ title: '已全部已读', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
  }
}

onShow(() => load(true))
onReachBottom(() => {
  if (hasMore.value) load()
})

// 下拉刷新：复用各页已有的 load（它们本身就是「重置到第一页」的语义）。
// 必须写在 finally 里收掉刷新动画，否则请求失败时下拉圈会一直转。
onPullDownRefresh(async () => {
  try {
    await load(true)
  } finally {
    uni.stopPullDownRefresh()
  }
})

</script>

<style scoped>
.msg-page {
  min-height: 100vh;
  background: #f5f5f5;
}
.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 24rpx;
  position: sticky;
  top: 0;
  z-index: 10;
}
.t-title {
  font-size: 30rpx;
  font-weight: bold;
}
.t-all {
  font-size: 24rpx;
  color: #0F6E56;
}
.msg {
  background: #fff;
  margin: 16rpx;
  border-radius: 12rpx;
  padding: 20rpx 24rpx;
  position: relative;
}
.msg.unread {
  background: #f0fff7;
}
/* 退款/售后这类「涉及钱」的消息：左边红色条 + 浅红底，比普通消息显眼 */
.msg.important {
  background: #FEF6F5;
  border-left: 8rpx solid #C0392B;
}
.msg.important .m-title {
  color: #C0392B;
}
.m-top {
  display: flex;
  align-items: center;
}
.m-type {
  font-size: 20rpx;
  color: #0F6E56;
  border: 1rpx solid #0F6E56;
  border-radius: 16rpx;
  padding: 2rpx 12rpx;
}
/* 类型分色：系统=橙（要读）、会员=紫（权益）、优惠券=金（好处） */
.m-type.sys {
  color: #B45309;
  border-color: #B45309;
  background: #FEF6E7;
}
.m-type.member {
  color: #7C3AED;
  border-color: #7C3AED;
  background: #F5F0FE;
}
.m-type.coupon {
  color: #B7791F;
  border-color: #B7791F;
  background: #FDF7E7;
}
/* 醒目消息的标签压成红色，和左边条呼应 */
.msg.important .m-type {
  color: #C0392B;
  border-color: #C0392B;
  background: #FDECEA;
}
.m-title {
  flex: 1;
  font-size: 28rpx;
  font-weight: bold;
  margin-left: 16rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.m-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #C0392B;
}
.m-content {
  font-size: 24rpx;
  color: #666;
  margin-top: 12rpx;
  line-height: 1.5;
}
.m-time {
  font-size: 22rpx;
  color: #999;
  margin-top: 10rpx;
}
.empty {
  text-align: center;
  color: #999;
  padding: 120rpx 0;
}
.loading,
.more {
  text-align: center;
  color: #999;
  font-size: 24rpx;
  padding: 24rpx 0;
}
</style>
