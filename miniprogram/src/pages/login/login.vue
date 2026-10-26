<template>
  <view class="login">
    <view class="logo">超市购物</view>
    <button class="wx-btn" @click="onWechatLogin">微信一键登录</button>
    <!-- 仅浏览器(H5)预览用：微信登录在普通浏览器不可用，提供账号密码旁路以便查看页面；小程序运行时无 window，按钮不显示 -->
    <button v-if="isBrowser" class="dev-btn" @click="onDevPreviewLogin">预览登录（浏览器专用）</button>
    <view class="tip">登录后即用钱包余额下单（首期未接入真实微信支付）</view>
    <view v-if="isBrowser" class="tip dev-tip">微信登录仅在微信客户端/开发者工具内可用，浏览器预览请点上方「预览登录」。</view>
  </view>
</template>

<script setup>
import { wechatLogin } from '@/api/auth'
import { request } from '@/api/request'
import { setAuth } from '@/store/auth'

// 仅浏览器(H5)环境才有 window；小程序运行时无 window，isBrowser=false 按钮不渲染
const isBrowser = typeof window !== 'undefined'

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

// 浏览器预览登录：用固定测试账号走后端 /auth/login（不存在则先 /auth/register），拿到 JWT 后本地落库
async function onDevPreviewLogin() {
  const acc = { username: 'dev_preview', password: 'Dev123456' }
  uni.showLoading({ title: '登录中' })
  try {
    let res
    try {
      res = await request('/auth/login', { method: 'POST', data: acc, auth: false })
    } catch (e) {
      // 账号不存在则先注册（本地库无该账号时）
      await request('/auth/register', {
        method: 'POST',
        data: { username: acc.username, password: acc.password, nickname: '预览账号', phone: '13800000000' },
        auth: false
      }).catch(() => {})
      res = await request('/auth/login', { method: 'POST', data: acc, auth: false })
    }
    setAuth(res.token, res.user)
    uni.hideLoading()
    uni.showToast({ title: '登录成功', icon: 'success' })
    uni.switchTab({ url: '/pages/index/index' })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '预览登录失败', icon: 'none' })
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
.dev-btn {
  margin-top: 30rpx;
  background: #1989fa;
  color: #fff;
  border-radius: 48rpx;
}
.dev-tip {
  margin-top: 16rpx;
  color: #fa5151;
}
</style>
