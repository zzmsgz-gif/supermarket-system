<template>
  <view class="mine">
    <!-- 头部：头像 + 昵称 + 等级标签 -->
    <view class="header">
      <image class="avatar" :src="user.avatarUrl ? fullUrl(user.avatarUrl) : defaultAvatar" mode="aspectFill" />
      <view class="uinfo">
        <view class="uname">{{ user.username || '微信用户' }}</view>
        <view class="utag" v-if="profile">{{ profile.levelName || ('Lv.' + (profile.memberLevel || 1)) }}</view>
        <view class="utag utag-guest" v-else>普通用户</view>
      </view>
      <view class="uhead-ops">
        <view class="uop" @click="go('member')">会员中心 ›</view>
      </view>
    </view>

    <!-- 三宫格数据：积分 / 余额 / 优惠券 -->
    <view class="stats">
      <view class="stat" @click="go('points')">
        <text class="num">{{ profile ? (profile.points || 0) : 0 }}</text>
        <text class="lab">积分</text>
      </view>
      <view class="stat-div"></view>
      <view class="stat" @click="go('wallet')">
        <text class="num">{{ walletBalance }}</text>
        <text class="lab">余额（元）</text>
      </view>
      <view class="stat-div"></view>
      <view class="stat" @click="go('coupons')">
        <text class="num">{{ couponCount }}</text>
        <text class="lab">优惠券（张）</text>
      </view>
    </view>

    <!-- 我的订单：四宫格，带各状态待办数 -->
    <view class="card">
      <view class="card-head">
        <text class="card-t">我的订单</text>
        <text class="card-more" @click="go('orders')">全部订单 ›</text>
      </view>
      <view class="grid4">
        <view class="g4" @click="goOrderTab('ALL')">
          <text class="g4-ic">📦</text>
          <text class="g4-t">全部订单</text>
        </view>
        <view class="g4" @click="goOrderTab('SHIPPED')">
          <text class="g4-ic">🚚</text>
          <text class="g4-t">待收货</text>
          <text class="g4-badge" v-if="orderCounts.SHIPPED">{{ orderCounts.SHIPPED }}</text>
        </view>
        <view class="g4" @click="goOrderTab('PENDING_PAYMENT')">
          <text class="g4-ic">💳</text>
          <text class="g4-t">待付款</text>
          <text class="g4-badge" v-if="orderCounts.PENDING_PAYMENT">{{ orderCounts.PENDING_PAYMENT }}</text>
        </view>
        <view class="g4" @click="goOrderTab('COMPLETED')">
          <text class="g4-ic">✅</text>
          <text class="g4-t">已完成</text>
        </view>
      </view>
    </view>

    <!-- 功能网格：两列 -->
    <view class="card">
      <view class="grid2">
        <view class="g2" @click="go('returns')">
          <text class="g2-ic">🧾</text><text class="g2-t">我的售后</text><text class="g2-ar">›</text>
        </view>
        <view class="g2" @click="go('favorites')">
          <text class="g2-ic">❤</text><text class="g2-t">我的收藏</text><text class="g2-ar">›</text>
        </view>
        <view class="g2" @click="go('coupons')">
          <text class="g2-ic">🎫</text><text class="g2-t">优惠券</text><text class="g2-ar">›</text>
        </view>
        <view class="g2" @click="go('wallet')">
          <text class="g2-ic">💰</text><text class="g2-t">我的钱包</text><text class="g2-ar">›</text>
        </view>
        <view class="g2" @click="go('addresses')">
          <text class="g2-ic">📍</text><text class="g2-t">收货地址</text><text class="g2-ar">›</text>
        </view>
        <view class="g2" @click="go('messages')">
          <text class="g2-ic">🔔</text><text class="g2-t">消息中心</text>
          <text class="g2-badge" v-if="unread > 0">{{ unread }}</text>
          <text class="g2-ar">›</text>
        </view>
        <view class="g2" @click="openPwd">
          <text class="g2-ic">🔑</text><text class="g2-t">修改密码</text><text class="g2-ar">›</text>
        </view>
        <view class="g2" @click="goLegal('terms')">
          <text class="g2-ic">📄</text><text class="g2-t">用户协议</text><text class="g2-ar">›</text>
        </view>
        <view class="g2" @click="goLegal('privacy')">
          <text class="g2-ic">🔒</text><text class="g2-t">隐私政策</text><text class="g2-ar">›</text>
        </view>
      </view>
    </view>

    <view class="logout" @click="doLogout">退出登录</view>

    <!-- 2026-11-10 旧列表式菜单已重构为「我的订单四宫格 + 功能两列网格」，入口一个没少：
         订单/售后/收藏/优惠券/地址/会员(头部)/钱包/消息/协议/隐私/改密 -->

    <!-- 修改密码弹层 -->
    <view class="mask" v-if="pwdOpen" @click="pwdOpen = false">
      <view class="sheet" @click.stop>
        <text class="sheet-title">修改密码</text>
        <view class="field">
          <text class="f-label">原密码</text>
          <input class="f-input" password v-model="pwdForm.currentPassword" placeholder="请输入当前密码" />
        </view>
        <view class="field">
          <text class="f-label">新密码</text>
          <input class="f-input" password v-model="pwdForm.newPassword" placeholder="8 位以上，含字母和数字" />
        </view>
        <view class="field">
          <text class="f-label">确认新密码</text>
          <input class="f-input" password v-model="pwdForm.confirmPassword" placeholder="再次输入新密码" />
        </view>
        <view class="sheet-ops">
          <button class="sbtn ghost" @click="pwdOpen = false">取消</button>
          <button class="sbtn main" :disabled="pwdSubmitting" @click="submitPwd">
            {{ pwdSubmitting ? '提交中…' : '确认修改' }}
          </button>
        </view>
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
import { changePassword } from '@/api/auth'
import { listMyCoupons } from '@/api/coupon'
import { listOrders } from '@/api/order'
import { fullUrl } from '@/config'

const defaultAvatar = 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIj48cmVjdCB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgZmlsbD0iI2RkZCIvPjx0ZXh0IHg9IjUwIiB5PSI1OCIgZm9udC1zaXplPSI1MCIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZmlsbD0iIzk5OSI+5Lq6PC90ZXh0Pjwvc3ZnPg=='

const user = reactive(getStoredUser() || { username: '微信用户' })
const walletBalance = ref('0.00')
const profile = ref(null)
const unread = ref(0)

// tabBar 页面只能用 switchTab 打开：用 navigateTo 跳 tabBar 页会**静默失败**
// （不报错也不跳，用户表现为"点了没反应"）——「我的订单」就是这个坑（2026-11-08 用户反馈）
const TAB_PAGES = ['index', 'cart', 'orders', 'mine']

// —— 首页三宫格 / 订单四宫格需要的数据 ——
const couponCount = ref(0)
const orderCounts = ref({ PENDING_PAYMENT: 0, SHIPPED: 0 })

/**
 * 跳「我的订单」并预选 tab。
 * ⚠️ orders 是 tabBar 页，switchTab **不支持带参数**，
 * 所以用 Storage 中转，orders.vue 的 onShow 里取一次就清掉。
 */
function goOrderTab(status) {
  if (status && status !== 'ALL') {
    uni.setStorageSync('orders_pending_tab', status)
  } else {
    uni.removeStorageSync('orders_pending_tab')
  }
  uni.switchTab({ url: '/pages/orders/orders' })
}

// —— 修改密码 ——
const pwdOpen = ref(false)
const pwdSubmitting = ref(false)
const pwdForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

function openPwd() {
  pwdForm.currentPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdOpen.value = true
}

async function submitPwd() {
  const cur = (pwdForm.currentPassword || '').trim()
  const nw = (pwdForm.newPassword || '').trim()
  const cf = (pwdForm.confirmPassword || '').trim()
  if (!cur) {
    uni.showToast({ title: '请输入原密码', icon: 'none' })
    return
  }
  if (nw.length < 6) {
    uni.showToast({ title: '新密码至少 6 位', icon: 'none' })
    return
  }
  if (nw === cur) {
    uni.showToast({ title: '新密码不能与原密码相同', icon: 'none' })
    return
  }
  if (nw !== cf) {
    uni.showToast({ title: '两次输入的新密码不一致', icon: 'none' })
    return
  }
  pwdSubmitting.value = true
  try {
    await changePassword({
      currentPassword: cur,
      newPassword: nw,
      confirmPassword: cf
    })
    pwdOpen.value = false
    uni.showToast({ title: '密码已修改', icon: 'success' })
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '修改失败', icon: 'none' })
  } finally {
    pwdSubmitting.value = false
  }
}

function go(page) {
  const url = '/pages/' + page + '/' + page
  if (TAB_PAGES.indexOf(page) !== -1) {
    uni.switchTab({ url })
    return
  }
  uni.navigateTo({ url })
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
  // 可用券数量（只数 UNUSED，已用/过期的没意义）
  try {
    const cs = await listMyCoupons()
    couponCount.value = (cs || []).filter((c) => c.status === 'UNUSED').length
  } catch (e) {
    couponCount.value = 0
  }
  // 订单待办数：只查「待付款 / 待收货」两个 tab 的总数（各 1 条即可拿 total），失败不影响页面
  try {
    const [pending, shipped] = await Promise.all([
      listOrders({ status: 'PENDING_PAYMENT', page: 1, size: 1 }).catch(() => null),
      listOrders({ status: 'SHIPPED', page: 1, size: 1 }).catch(() => null)
    ])
    orderCounts.value = {
      PENDING_PAYMENT: (pending && pending.total) || 0,
      SHIPPED: (shipped && shipped.total) || 0
    }
  } catch (e) {
    orderCounts.value = { PENDING_PAYMENT: 0, SHIPPED: 0 }
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
  background: #0F6E56;
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
  background: #C0392B;
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

/* ---------- 修改密码弹层 ---------- */
.mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
  z-index: 999;
}
.sheet {
  width: 100%;
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 30rpx 28rpx calc(30rpx + env(safe-area-inset-bottom));
}
.sheet-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1F2937;
}
.field {
  margin-top: 24rpx;
}
.f-label {
  font-size: 24rpx;
  color: #8A8578;
}
.f-input {
  margin-top: 10rpx;
  height: 80rpx;
  background: #F7F5F0;
  border-radius: 12rpx;
  padding: 0 20rpx;
  font-size: 26rpx;
  color: #1F2937;
}
.sheet-ops {
  display: flex;
  gap: 16rpx;
  margin-top: 30rpx;
}
.sbtn {
  flex: 1;
  height: 84rpx;
  line-height: 84rpx;
  text-align: center;
  border-radius: 42rpx;
  font-size: 28rpx;
  font-weight: 600;
  margin: 0;
}
.sbtn.ghost {
  background: #fff;
  color: #666;
  border: 1rpx solid #ddd;
}
.sbtn.main {
  background: #0F6E56;
  color: #fff;
}
.sbtn.main[disabled] {
  background: #C9D5D0;
}
/* ===== 2026-11-10 个人中心重构（头部+三宫格数据 / 订单四宫格 / 功能两列网格） =====
   旧列表式样式（.menu/.m-item/.mi-*）已无引用，保留在下方不影响渲染；
   同名类在这里重定义，后写覆盖前面的。 */
.mine {
  min-height: 100vh;
  background: #FAF8F3;
  padding-bottom: 40rpx;
}
.header {
  display: flex;
  align-items: center;
  background: linear-gradient(135deg, #12725C 0%, #0F6E56 60%, #0B5A46 100%);
  padding: 40rpx 32rpx 52rpx;
}
.avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: #fff;
  flex-shrink: 0;
}
.uinfo {
  flex: 1;
  min-width: 0;
  margin-left: 24rpx;
  color: #fff;
}
.uname {
  font-size: 34rpx;
  font-weight: 700;
}
.utag {
  display: inline-block;
  margin-top: 10rpx;
  font-size: 21rpx;
  color: #A9822B;
  background: #FDF3DC;
  border-radius: 20rpx;
  padding: 3rpx 16rpx;
}
.utag-guest {
  color: #E6F4EF;
  background: rgba(255, 255, 255, 0.18);
}
.uhead-ops {
  flex-shrink: 0;
}
.uop {
  font-size: 22rpx;
  color: #E6F4EF;
  border: 1rpx solid rgba(255, 255, 255, 0.35);
  border-radius: 24rpx;
  padding: 6rpx 16rpx;
}

/* 三宫格数据：负 margin 上移，压住头部下沿 */
.stats {
  display: flex;
  align-items: center;
  margin: -30rpx 24rpx 0;
  background: #fff;
  border-radius: 20rpx;
  padding: 26rpx 0;
  box-shadow: 0 4rpx 16rpx rgba(31, 41, 55, 0.06);
}
.stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}
.stat-div {
  width: 1rpx;
  height: 56rpx;
  background: #F0EDE6;
}
.num {
  font-size: 38rpx;
  font-weight: 700;
  color: #1F2937;
}
.lab {
  font-size: 22rpx;
  color: #9CA3AF;
}

.card {
  margin: 20rpx 24rpx 0;
  background: #fff;
  border-radius: 20rpx;
  padding: 8rpx 24rpx 20rpx;
  box-shadow: 0 2rpx 10rpx rgba(31, 41, 55, 0.03);
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 0 12rpx;
}
.card-t {
  font-size: 28rpx;
  font-weight: 600;
  color: #1F2937;
}
.card-more {
  font-size: 23rpx;
  color: #9CA3AF;
}

/* 订单四宫格 */
.grid4 {
  display: flex;
  padding-top: 6rpx;
}
.g4 {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 14rpx 0;
}
.g4-ic {
  font-size: 42rpx;
  line-height: 1;
}
.g4-t {
  font-size: 23rpx;
  color: #4B5563;
}
.g4-badge {
  position: absolute;
  top: 4rpx;
  right: 50%;
  margin-right: -46rpx;
  min-width: 30rpx;
  height: 30rpx;
  line-height: 30rpx;
  text-align: center;
  border-radius: 15rpx;
  background: #C0392B;
  color: #fff;
  font-size: 18rpx;
  padding: 0 6rpx;
}

/* 功能两列网格 */
.grid2 {
  display: flex;
  flex-wrap: wrap;
}
.g2 {
  width: 50%;
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #F7F5F0;
  box-sizing: border-box;
}
.g2:nth-child(odd) {
  padding-right: 12rpx;
}
.g2:nth-child(even) {
  padding-left: 12rpx;
  border-left: 1rpx solid #F7F5F0;
}
.g2-ic {
  font-size: 34rpx;
  line-height: 1;
  flex-shrink: 0;
}
.g2-t {
  flex: 1;
  min-width: 0;
  font-size: 26rpx;
  color: #1F2937;
}
.g2-badge {
  min-width: 30rpx;
  height: 30rpx;
  line-height: 30rpx;
  text-align: center;
  border-radius: 15rpx;
  background: #C0392B;
  color: #fff;
  font-size: 18rpx;
  padding: 0 6rpx;
}
.g2-ar {
  font-size: 26rpx;
  color: #C4C0B6;
  flex-shrink: 0;
}

/* 退出登录 */
.logout {
  margin: 30rpx 24rpx 0;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  background: #fff;
  color: #C0392B;
  border-radius: 20rpx;
  font-size: 28rpx;
  font-weight: 600;
}
</style>
