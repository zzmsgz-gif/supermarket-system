<template>
  <view class="login">
    <view class="logo">超市购物</view>
    <button class="wx-btn" @click="onWechatLogin">微信一键登录</button>
    <!-- 仅浏览器(H5)环境：微信登录在普通浏览器不可用，这里给账号密码登录，
         用自己的线上账号登进来预览（H5 预览可经 MINI_API_TARGET 连线上后端）。
         小程序运行时没有 window，isBrowser=false，这块不渲染，小程序只有微信登录。 -->
    <view v-if="isBrowser" class="pwd-box">
      <input v-model="pwdForm.username" class="pwd-input" placeholder="用户名" :disabled="pwdLoading" />
      <input v-model="pwdForm.password" class="pwd-input" type="password" placeholder="密码" :disabled="pwdLoading" @confirm="onPasswordLogin" />
      <button class="pwd-btn" :disabled="pwdLoading" @click="onPasswordLogin">{{ pwdLoading ? '登录中…' : '账号密码登录' }}</button>
    </view>
    <view class="tip">登录后即用钱包余额下单（首期未接入真实微信支付）</view>
    <view v-if="isBrowser" class="tip dev-tip">微信一键登录仅在微信客户端/开发者工具内可用；浏览器预览请用上方账号密码登录。</view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { wechatLogin } from '@/api/auth'
import { request } from '@/api/request'
import { setAuth } from '@/store/auth'

// 仅浏览器(H5)环境才有 window；小程序运行时无 window，isBrowser=false，账号密码登录块不渲染
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

// H5 账号密码登录：直接走后端 /auth/login（2026-11-07 替换掉原先写死 dev_preview 的「预览登录」——
// 那个会自动注册固定手机号 13800000000，一撞就登录不了，还会往目标库里塞测试账号）
const pwdForm = reactive({ username: '', password: '' })
const pwdLoading = ref(false)

async function onPasswordLogin() {
  if (pwdLoading.value) return
  const username = pwdForm.username.trim()
  if (!username || !pwdForm.password) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }
  pwdLoading.value = true
  uni.showLoading({ title: '登录中' })
  try {
    const res = await request('/auth/login', {
      method: 'POST',
      data: { username, password: pwdForm.password },
      auth: false
    })
    setAuth(res.token, res.user)
    uni.hideLoading()
    uni.showToast({ title: '登录成功', icon: 'success' })
    uni.switchTab({ url: '/pages/index/index' })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '登录失败', icon: 'none' })
  } finally {
    pwdLoading.value = false
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
  /* 微信一键登录按钮保持微信品牌绿 —— 这是微信侧的视觉规范，
     不要跟项目主色 #0F6E56 统一（2026-11-08 批量改色时差点误伤） */
  background: #07c160;
  color: #fff;
  border-radius: 48rpx;
}
.tip {
  margin-top: 40rpx;
  color: #999;
  font-size: 24rpx;
}
.pwd-box {
  margin-top: 40rpx;
  padding: 30rpx 28rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
}
.pwd-input {
  height: 84rpx;
  padding: 0 24rpx;
  margin-bottom: 20rpx;
  background: #faf8f3;
  border: 1rpx solid #e8e4da;
  border-radius: 12rpx;
  font-size: 28rpx;
  text-align: left;
}
.pwd-btn {
  margin-top: 6rpx;
  background: #0f6e56;
  color: #fff;
  border-radius: 48rpx;
}
.pwd-btn[disabled] {
  opacity: 0.6;
}
.dev-tip {
  margin-top: 16rpx;
  color: #8a8378;
}
</style>
