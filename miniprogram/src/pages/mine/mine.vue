<template>
  <view class="mine">
    <view class="header">
      <image class="avatar" :src="user.avatarUrl ? fullUrl(user.avatarUrl) : defaultAvatar" mode="aspectFill" />
      <view class="uinfo">
        <view class="uname">{{ user.username || '微信用户' }}</view>
        <view class="ulevel" v-if="profile">{{ profile.levelName || ('Lv.' + (profile.memberLevel || 1)) }}</view>
      </view>
      <view class="logout" @click="doLogout">退出</view>
    </view>

    <view class="stats">
      <view class="stat" @click="go('wallet')">
        <text class="num">¥{{ walletBalance }}</text>
        <text class="lab">账户余额</text>
      </view>
      <view class="stat" @click="go('member')">
        <text class="num">{{ profile ? profile.points : '-' }}</text>
        <text class="lab">积分</text>
      </view>
      <view class="stat" @click="go('member')">
        <text class="num">{{ profile && profile.discountRate ? (profile.discountRate * 10).toFixed(1) + '折' : '-' }}</text>
        <text class="lab">会员折扣</text>
      </view>
    </view>

    <view class="menu">
      <view class="m-item" @click="go('orders')">
        <text class="mi-ic">📦</text><text class="mi-t">我的订单</text><text class="mi-ar">›</text>
      </view>
      <view class="m-item" @click="go('favorites')">
        <text class="mi-ic">❤</text><text class="mi-t">我的收藏</text><text class="mi-ar">›</text>
      </view>
      <view class="m-item" @click="go('coupons')">
        <text class="mi-ic">🎫</text><text class="mi-t">优惠券</text><text class="mi-ar">›</text>
      </view>
      <view class="m-item" @click="go('addresses')">
        <text class="mi-ic">📍</text><text class="mi-t">收货地址</text><text class="mi-ar">›</text>
      </view>
      <view class="m-item" @click="go('member')">
        <text class="mi-ic">👑</text><text class="mi-t">会员中心</text><text class="mi-ar">›</text>
      </view>
      <view class="m-item" @click="go('wallet')">
        <text class="mi-ic">💰</text><text class="mi-t">我的钱包</text><text class="mi-ar">›</text>
      </view>
      <view class="m-item" @click="go('messages')">
        <text class="mi-ic">🔔</text>
        <text class="mi-t">消息中心</text>
        <text class="mi-badge" v-if="unread > 0">{{ unread }}</text>
        <text class="mi-ar">›</text>
      </view>
      <view class="m-item" @click="goLegal('terms')">
        <text class="mi-ic">📄</text><text class="mi-t">用户协议</text><text class="mi-ar">›</text>
      </view>
      <view class="m-item" @click="goLegal('privacy')">
        <text class="mi-ic">🔒</text><text class="mi-t">隐私政策</text><text class="mi-ar">›</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getStoredUser, clearAuth, isLoggedIn } from '@/store/auth'
import { getWallet } from '@/api/wallet'
import { getMemberProfile } from '@/api/member'
import { unreadCount } from '@/api/message'
import { fullUrl } from '@/config'

const defaultAvatar = 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIj48cmVjdCB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgZmlsbD0iI2RkZCIvPjx0ZXh0IHg9IjUwIiB5PSI1OCIgZm9udC1zaXplPSI1MCIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZmlsbD0iIzk5OSI+5Lq6PC90ZXh0Pjwvc3ZnPg=='

const user = reactive(getStoredUser() || { username: '微信用户' })
const walletBalance = ref('0.00')
const profile = ref(null)
const unread = ref(0)

function go(page) {
  uni.navigateTo({ url: '/pages/' + page + '/' + page })
}
function goLegal(docKey) {
  uni.navigateTo({ url: '/pages/legal/legal?docKey=' + docKey })
}

async function load() {
  try {
    const w = await getWallet()
    walletBalance.value = Number((w && w.balance) || 0).toFixed(2)
  } catch (e) {
    walletBalance.value = '0.00'
  }
  try {
    profile.value = await getMemberProfile()
  } catch (e) {
    profile.value = null
  }
  try {
    const c = await unreadCount()
    unread.value = (c && c.count) || 0
  } catch (e) {
    unread.value = 0
  }
}

function doLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定退出当前账号？',
    success: (r) => {
      if (!r.confirm) return
      clearAuth()
      uni.reLaunch({ url: '/pages/index/index' })
    }
  })
}

onShow(() => {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  if (!user.username || user.username === '微信用户') {
    const u = getStoredUser()
    if (u) Object.assign(user, u)
  }
  load()
})
</script>

<style scoped>
.mine {
  min-height: 100vh;
  background: #f5f5f5;
}
.header {
  display: flex;
  align-items: center;
  background: #07c160;
  padding: 40rpx 32rpx;
}
.avatar {
  width: 110rpx;
  height: 110rpx;
  border-radius: 50%;
  background: #fff;
}
.uinfo {
  flex: 1;
  margin-left: 24rpx;
  color: #fff;
}
.uname {
  font-size: 34rpx;
  font-weight: bold;
}
.ulevel {
  font-size: 24rpx;
  margin-top: 8rpx;
  background: rgba(255, 255, 255, 0.25);
  display: inline-block;
  padding: 2rpx 16rpx;
  border-radius: 24rpx;
}
.logout {
  color: #fff;
  font-size: 26rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.6);
  padding: 8rpx 24rpx;
  border-radius: 32rpx;
}
.stats {
  display: flex;
  background: #fff;
  margin: -20rpx 16rpx 0;
  border-radius: 16rpx;
  padding: 24rpx 0;
  position: relative;
  z-index: 2;
}
.stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.num {
  font-size: 32rpx;
  font-weight: bold;
  color: #333;
}
.lab {
  font-size: 22rpx;
  color: #999;
  margin-top: 6rpx;
}
.menu {
  background: #fff;
  margin: 16rpx;
  border-radius: 16rpx;
  padding: 0 24rpx;
}
.m-item {
  display: flex;
  align-items: center;
  padding: 28rpx 0;
  border-bottom: 1rpx solid #f2f2f2;
}
.m-item:last-child {
  border-bottom: none;
}
.mi-ic {
  font-size: 36rpx;
  width: 56rpx;
}
.mi-t {
  flex: 1;
  font-size: 28rpx;
  color: #333;
}
.mi-badge {
  background: #e4393c;
  color: #fff;
  font-size: 20rpx;
  border-radius: 20rpx;
  padding: 2rpx 12rpx;
  margin-right: 12rpx;
}
.mi-ar {
  color: #ccc;
  font-size: 32rpx;
}
</style>
