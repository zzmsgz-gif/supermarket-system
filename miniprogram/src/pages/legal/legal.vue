<template>
  <view class="legal" v-if="doc">
    <view class="title">{{ doc.title }}</view>
    <view class="updated" v-if="doc.updatedAt">更新于 {{ formatTime(doc.updatedAt) }}</view>
    <!-- 正文按行解析成块渲染（不再塞进 rich-text）：
         rich-text 不认纯文本里的换行，整篇会挤成一坨；行内的 **粗体** 标记也会原样露出星号。 -->
    <view class="body">
      <view v-for="(b, i) in blocks" :key="i" :class="['blk', 'blk-' + b.type]">
        <text v-for="(s, j) in b.segs" :key="j" :class="{ bold: s.bold }">{{ s.text }}</text>
      </view>
    </view>
  </view>
  <view class="loading" v-else>加载中…</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getLegalDoc } from '@/api/legal'
import { formatTime } from '@/utils/format'

const doc = ref(null)

onLoad(async (opts) => {
  const docKey = opts.docKey || 'terms'
  try {
    doc.value = await getLegalDoc(docKey)
  } catch (e) {
    doc.value = { title: '协议', content: '加载失败' }
  }
})

/**
 * 把纯文本正文拆成可排版的结构块。
 *
 * 后台用 textarea 维护正文，格式约定是：
 *   - 「一、」「二、」开头 → 小节标题
 *   - 「1.1」「2.3」开头   → 条目（正文样式，只是跟上面拉开一点距离）
 *   - 空行                → 分段（不渲染）
 *   - 行内 **文字**       → 加粗（免责/敏感条款靠它强调）
 * 以前把整篇丢给 rich-text：换行不生效 + 星号裸露，读起来像一堆乱码。
 */
const blocks = computed(() => {
  const raw = (doc.value && doc.value.content) || ''
  const out = []
  raw.split(/\r?\n/).forEach((line) => {
    const text = line.trim()
    if (!text) return
    let type = 'p'
    if (/^[一二三四五六七八九十]+、/.test(text)) type = 'h'
    else if (/^\d+(\.\d+)+/.test(text)) type = 'item'
    out.push({ type, segs: splitBold(text) })
  })
  return out
})

/** 「**加粗**」按奇偶段切开，避免把星号显示出来 */
function splitBold(text) {
  const segs = []
  text.split('**').forEach((t, i) => {
    if (!t) return
    segs.push({ text: t, bold: i % 2 === 1 })
  })
  return segs.length ? segs : [{ text, bold: false }]
}
</script>

<style scoped>
.legal {
  padding: 32rpx 32rpx 80rpx;
  background: #fff;
  min-height: 100vh;
}
.title {
  font-size: 38rpx;
  font-weight: bold;
  text-align: center;
  color: #111827;
  line-height: 1.45;
}
.updated {
  font-size: 22rpx;
  color: #9CA3AF;
  text-align: center;
  margin: 14rpx 0 32rpx;
}
.body {
  /* 段落之间靠 .blk 的 margin 控制，这里不给额外间距 */
  padding-bottom: 24rpx;
}
.blk {
  font-size: 27rpx;
  line-height: 1.9;
  color: #374151;
  text-align: justify;
  word-break: break-word;
}
.blk + .blk {
  margin-top: 10rpx;
}
/* 小节标题：主色竖条 + 加粗，跟正文拉开档次 */
.blk-h {
  margin-top: 36rpx;
  margin-bottom: 6rpx;
  font-size: 30rpx;
  font-weight: bold;
  line-height: 1.5;
  color: #111827;
  padding-left: 16rpx;
  border-left: 6rpx solid #0F6E56;
}
/* 条目：编号已在文字里，这里只给一点点上间距，读起来成组 */
.blk-item {
  margin-top: 8rpx;
}
.bold {
  font-weight: bold;
  color: #111827;
}
.loading {
  text-align: center;
  color: #999;
  padding: 120rpx 0;
}
</style>
