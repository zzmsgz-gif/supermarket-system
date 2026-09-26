<template>
  <view class="login">
    <view class="logo">超市购物</view>
    <button class="wx-btn" @click="onWechatLogin">微信一键登录</button>
    <view class="tip">登录后即用钱包余额下单（首期未接入真实微信支付）</view>
  </view>
</template>

<script setup>
import { wechatLogin } from '@/api/auth'

async function onWechatLogin() {
  uni.showLoading({ title: '登录中' })
  try {
    // uni.login 返回 Promise，code 用于后端换取 openid
    const { code } = await uni.login({ provider: 'weixin' })
    await wechatLogin(code)
    uni.hideLoading()
    uni.showToast({ title: '登录成功', icon: 'success' })
    uni.switchTab({ url: '/pages/index/index' })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '登录失败', icon: 'none' })
  }
}
</script>

<style scoped>
.login {
  padding: 120rpx 60rpx;
  text-align: center;
}
.logo {
  font-size: 48rpx;
  font-weight: bold;
  margin-bottom: 80rpx;
}
.wx-btn {
  background: #07c160;
  color: #fff;
  border-radius: 48rpx;
}
.tip {
  margin-top: 40rpx;
  color: #999;
  font-size: 24rpx;
}
</style>
