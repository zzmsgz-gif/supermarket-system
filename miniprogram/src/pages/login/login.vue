<template>
  <view class="login">
    <view class="logo-wrap">
      <image class="logo-img" src="/static/logo-icon.png" mode="aspectFit" />
      <text class="logo-name">{{ BRAND_NAME }}</text>
    </view>
    <!-- H5（浏览器部署版）没有微信登录能力，整块隐藏，只留下面的账号密码登录。
         这样发给别人看的网页版不会出现一个点了必然失败的绿色按钮。 -->
    <button v-if="!isBrowser" class="wx-btn" @click="onWechatLogin">微信一键登录</button>
    <!-- 合规要求：登录动作前必须由用户主动勾选同意协议（2026-11-22 用户要求）。
         勾选框自绘 —— 小程序原生 checkbox 的尺寸/取色跟主色对不齐，H5 与小程序表现也差得多。
         协议名可点，跳 legal 页看全文（后端 docKey 会 toUpperCase，传小写即可）。 -->
    <view class="agree-row">
      <view class="agree-box" :class="{ on: agreed }" @click="agreed = !agreed">
        <text v-if="agreed" class="agree-tick">✓</text>
      </view>
      <text class="agree-text" @click="agreed = !agreed">我已阅读并同意</text>
      <text class="agree-link" @click.stop="openDoc('terms')">《用户协议》</text>
      <text class="agree-text">和</text>
      <text class="agree-link" @click.stop="openDoc('privacy')">《隐私政策》</text>
    </view>
    <!-- 仅浏览器(H5)环境：微信登录在普通浏览器不可用，这里给账号密码登录，
         用自己的线上账号登进来预览（H5 预览可经 MINI_API_TARGET 连线上后端）。
         小程序运行时没有 window，isBrowser=false，这块不渲染，小程序只有微信登录。 -->
    <view v-if="isBrowser" class="pwd-box">
      <input v-model="pwdForm.username" class="pwd-input" placeholder="用户名" :disabled="pwdLoading" />
      <input v-model="pwdForm.password" class="pwd-input" type="password" placeholder="密码" :disabled="pwdLoading" @confirm="onPasswordLogin" />
      <button class="pwd-btn" :disabled="pwdLoading" @click="onPasswordLogin">{{ pwdLoading ? '登录中…' : '账号密码登录' }}</button>
    </view>
    <!-- 2026-11-08 按用户要求移除：「（首期未接入真实微信支付）」这类开发期说明不该出现在用户界面。
         2026-11-26 同样移除「浏览器预览请用账号密码登录」那条 —— H5 版是要给别人看的成品，
         不该出现"预览""开发者工具"这类字眼。 -->
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { wechatLogin } from '@/api/auth'
import { request } from '@/api/request'
import { setAuth } from '@/store/auth'
import { BRAND_NAME } from '@/config'

// 仅浏览器(H5)环境才有 window；小程序运行时无 window，isBrowser=false，账号密码登录块不渲染
const isBrowser = typeof window !== 'undefined'

// ===== 协议勾选（登录必经）=====
// 每次进登录页都要重新勾：不做本地记忆，避免"上次勾过"变成变相默认同意。
const agreed = ref(false)

function openDoc(docKey) {
  uni.navigateTo({ url: '/pages/legal/legal?docKey=' + docKey })
}

/** 两个登录入口共用这道闸：没勾就不发请求，直接提示 */
function ensureAgreed() {
  if (agreed.value) return true
  uni.showToast({ title: '请先阅读并同意用户协议和隐私政策', icon: 'none' })
  return false
}

async function onWechatLogin() {
  if (!ensureAgreed()) return
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
  if (!ensureAgreed()) return
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
.logo-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 80rpx;
}
.logo-img {
  width: 140rpx;
  height: 140rpx;
  border-radius: 32rpx;
}
.logo-name {
  margin-top: 24rpx;
  font-size: 40rpx;
  font-weight: 600;
  color: #1F2937;
  letter-spacing: 2rpx;
}
.wx-btn {
  /* 微信一键登录按钮保持微信品牌绿 —— 这是微信侧的视觉规范，
     不要跟项目主色 #0F6E56 统一（2026-11-08 批量改色时差点误伤） */
  background: #07c160;
  color: #fff;
  border-radius: 48rpx;
}
/* 协议勾选行：紧贴登录按钮下方，两行内自动换行居中，不挤在按钮里 */
.agree-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  margin-top: 28rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #6B7280;
}
.agree-box {
  width: 30rpx;
  height: 30rpx;
  margin-right: 10rpx;
  border: 2rpx solid #C7CDD6;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  box-sizing: border-box;
}
.agree-box.on {
  background: #0F6E56;
  border-color: #0F6E56;
}
.agree-tick {
  color: #fff;
  font-size: 22rpx;
  line-height: 1;
}
.agree-link {
  color: #0F6E56;
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
