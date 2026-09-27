<template>
  <view class="legal" v-if="doc">
    <view class="title">{{ doc.title }}</view>
    <view class="updated" v-if="doc.updatedAt">更新于 {{ formatTime(doc.updatedAt) }}</view>
    <rich-text class="content" :nodes="doc.content || ''"></rich-text>
  </view>
  <view class="loading" v-else>加载中…</view>
</template>

<script setup>
import { ref } from 'vue'
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
</script>

<style scoped>
.legal {
  padding: 32rpx;
  background: #fff;
  min-height: 100vh;
}
.title {
  font-size: 36rpx;
  font-weight: bold;
  text-align: center;
}
.updated {
  font-size: 22rpx;
  color: #999;
  text-align: center;
  margin: 12rpx 0 24rpx;
}
.content {
  font-size: 28rpx;
  line-height: 1.8;
  color: #333;
}
.loading {
  text-align: center;
  color: #999;
  padding: 120rpx 0;
}
</style>
