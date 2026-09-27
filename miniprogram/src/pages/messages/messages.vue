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
      :class="{ unread: !m.isRead }"
      @click="open(m)"
    >
      <view class="m-top">
        <text class="m-type">{{ typeLabel(m.type) }}</text>
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
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { listMessages, markAllRead, markRead } from '@/api/message'
import { formatTime } from '@/utils/format'

const messages = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const hasMore = ref(false)

function typeLabel(t) {
  const map = {
    ORDER: '订单',
    ACTIVITY: '活动',
    SYSTEM: '系统',
    PROMOTION: '优惠',
    PRICE_DROP: '降价'
  }
  return map[t] || t || '通知'
}

async function load(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 0
    messages.value = []
  }
  loading.value = true
  try {
    const res = await listMessages({ page: page.value, size: size.value })
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
  color: #07c160;
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
.m-top {
  display: flex;
  align-items: center;
}
.m-type {
  font-size: 20rpx;
  color: #07c160;
  border: 1rpx solid #07c160;
  border-radius: 16rpx;
  padding: 2rpx 12rpx;
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
  background: #e4393c;
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
