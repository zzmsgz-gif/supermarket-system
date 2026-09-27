<template>
  <view class="member">
    <view class="hero">
      <view class="lv">{{ profile ? profile.levelName : '普通会员' }}</view>
      <view class="pts">{{ profile ? profile.points : 0 }} 积分</view>
      <view class="disc" v-if="profile && profile.discountRate">
        会员折扣 {{ (profile.discountRate * 10).toFixed(1) }} 折
      </view>
    </view>

    <view class="card" v-if="profile">
      <view class="prog-t" v-if="profile.nextLevelName">
        距「{{ profile.nextLevelName }}」还需消费 ¥{{ profile.nextLevelThreshold }}
      </view>
      <view class="bar" v-if="profile.nextLevelName">
        <view class="bar-in" :style="{ width: progressPct + '%' }"></view>
      </view>
      <view class="kv"><text>累计消费</text><text>¥{{ profile.totalSpent || 0 }}</text></view>
      <view class="kv" v-if="profile.maxRedeemRatio != null">
        <text>积分抵现上限</text><text>{{ (profile.maxRedeemRatio * 100).toFixed(0) }}% 订单金额</text>
      </view>
    </view>

    <view class="card">
      <view class="ct">会员等级</view>
      <view v-for="l in levels" :key="l.level" class="lv-row">
        <text class="lv-name">{{ l.name }}</text>
        <text class="lv-th">满 ¥{{ l.threshold }}</text>
        <text class="lv-rate">{{ (l.rate * 10).toFixed(1) }} 折</text>
      </view>
    </view>

    <view class="card" v-if="memberDays.length">
      <view class="ct">会员日</view>
      <view v-for="m in memberDays" :key="m.id" class="md-row">
        <text class="md-date">{{ formatDate(m.memberDate) }}</text>
        <text class="md-mul">积分 ×{{ m.multiplier }}</text>
        <text class="md-remark" v-if="m.remark">{{ m.remark }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getMemberProfile, listMemberLevels, listMemberDays } from '@/api/member'
import { formatDate } from '@/utils/format'

const profile = ref(null)
const levels = ref([])
const memberDays = ref([])

const progressPct = computed(() => {
  const p = profile.value && profile.value.progressToNext
  if (typeof p !== 'number') return 0
  const v = p > 1 ? p : p * 100
  return Math.max(0, Math.min(100, Math.round(v)))
})

async function load() {
  try {
    profile.value = await getMemberProfile()
  } catch (e) {
    profile.value = null
  }
  try {
    levels.value = await listMemberLevels()
  } catch (e) {
    levels.value = []
  }
  try {
    const days = await listMemberDays()
    memberDays.value = (days || []).filter((d) => d.enabled !== false)
  } catch (e) {
    memberDays.value = []
  }
}

onShow(load)
</script>

<style scoped>
.member {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 24rpx;
}
.hero {
  background: linear-gradient(135deg, #07c160, #05a050);
  color: #fff;
  padding: 48rpx 32rpx;
  text-align: center;
}
.lv {
  font-size: 36rpx;
  font-weight: bold;
}
.pts {
  font-size: 28rpx;
  margin-top: 12rpx;
}
.disc {
  font-size: 24rpx;
  margin-top: 8rpx;
  opacity: 0.9;
}
.card {
  background: #fff;
  margin: 16rpx;
  border-radius: 12rpx;
  padding: 20rpx 24rpx;
}
.ct {
  font-size: 28rpx;
  font-weight: bold;
  padding: 8rpx 0 16rpx;
}
.prog-t {
  font-size: 24rpx;
  color: #666;
}
.bar {
  height: 14rpx;
  background: #f2f2f2;
  border-radius: 8rpx;
  margin: 12rpx 0 20rpx;
  overflow: hidden;
}
.bar-in {
  height: 100%;
  background: #07c160;
}
.kv {
  display: flex;
  justify-content: space-between;
  font-size: 26rpx;
  padding: 12rpx 0;
  border-bottom: 1rpx solid #f2f2f2;
}
.kv:last-child {
  border-bottom: none;
}
.lv-row {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f2f2f2;
}
.lv-row:last-child {
  border-bottom: none;
}
.lv-name {
  flex: 1;
  font-size: 26rpx;
  font-weight: bold;
}
.lv-th {
  font-size: 24rpx;
  color: #666;
  margin-right: 24rpx;
}
.lv-rate {
  font-size: 26rpx;
  color: #07c160;
}
.md-row {
  display: flex;
  align-items: center;
  padding: 14rpx 0;
  border-bottom: 1rpx solid #f2f2f2;
}
.md-row:last-child {
  border-bottom: none;
}
.md-date {
  font-size: 26rpx;
  font-weight: bold;
}
.md-mul {
  font-size: 24rpx;
  color: #e4393c;
  margin-left: 24rpx;
}
.md-remark {
  font-size: 22rpx;
  color: #999;
  margin-left: 24rpx;
  flex: 1;
  text-align: right;
}
</style>
