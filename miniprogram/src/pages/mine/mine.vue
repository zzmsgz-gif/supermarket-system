<template>
  <view class="mine">
    <!-- 头部：头像 + 昵称 + 等级标签。未登录时整块变成登录入口。 -->
    <view class="header">
      <image class="avatar" :src="user.avatarUrl ? fullUrl(user.avatarUrl) : defaultAvatar" mode="aspectFill" @click="onUserClick" />
      <view class="uinfo" @click="onUserClick">
        <view class="uname">{{ loggedIn ? (user.nickname || user.username || '微信用户') : '点击登录' }}</view>
        <view class="utag" v-if="loggedIn && profile">{{ profile.levelName || ('Lv.' + (profile.memberLevel || 1)) }}</view>
        <view class="utag utag-guest" v-else-if="!loggedIn">未登录</view>
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

    <!-- 2026-11-15 用户要求删除整张「我的订单」卡片：
         底部 tabBar 已有「订单」入口，个人中心再来一整块订单四宫格属于重复。
         订单相关入口统一走底部 tab（页面内的各状态 tab 也都还在），
         所以 orderCounts / goOrderTab / 待办数查询一并移除，少 3 次请求。 -->

    <!-- 功能网格：两列 -->
    <view class="card">
      <view class="grid2">
        <view class="g2" @click="go('profile')">
          <text class="g2-ic">👤</text><text class="g2-t">个人资料</text><text class="g2-ar">›</text>
        </view>
        <view class="g2" @click="go('returns')">
          <text class="g2-ic">🧾</text><text class="g2-t">我的售后</text><text class="g2-ar">›</text>
        </view>
        <view class="g2" @click="go('favorites')">
          <text class="g2-ic">❤</text><text class="g2-t">我的收藏</text><text class="g2-ar">›</text>
        </view>
        <view class="g2" @click="go('coupons')">
          <text class="g2-ic">🎫</text><text class="g2-t">优惠券</text>
          <!-- 有券可领时直接把张数写在入口上，用户不必点进去才知道。
               用 .g2-tag（文字标签）而不是 .g2-badge：后者是未读数用的圆形数字徽标 -->
          <text class="g2-tag" v-if="claimableCount > 0">可领 {{ claimableCount }} 张</text>
          <text class="g2-ar">›</text>
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
        <view class="g2" v-if="canChangePassword" @click="openPwd">
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

    <view class="logout" v-if="loggedIn" @click="doLogout">退出登录</view>

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
import { ref, reactive, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getStoredUser, clearAuth, isLoggedIn } from '@/store/auth'
import { getWallet } from '@/api/wallet'
import { getMemberProfile } from '@/api/member'
import { unreadCount } from '@/api/message'
import { changePassword } from '@/api/auth'
import { listMyCoupons } from '@/api/coupon'
import { syncCartBadge, clearCartBadge } from '@/utils/cartBadge'
import { syncCouponDot, clearCouponDot } from '@/utils/couponBadge'
import { requireLogin, goLogin } from '@/utils/authGuard'
import { fullUrl } from '@/config'

const defaultAvatar = 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIj48cmVjdCB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgZmlsbD0iI2RkZCIvPjx0ZXh0IHg9IjUwIiB5PSI1OCIgZm9udC1zaXplPSI1MCIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZmlsbD0iIzk5OSI+5Lq6PC90ZXh0Pjwvc3ZnPg=='

const user = reactive(getStoredUser() || { username: '微信用户' })
// 是否已登录：未登录时整页显示登录引导，而不是把人弹去登录页
const loggedIn = ref(false)
const walletBalance = ref('0.00')
const profile = ref(null)

/**
 * 「修改密码」只在账号密码用户里显示。
 *
 * 微信一键登录的账号 username 是后端自动生成的 `wx_<openid>`，
 * 密码写的是随机 UUID（见 AuthService.wechatLogin）—— 用户根本不知道原密码是什么，
 * 点进去输任何值都会被「原密码错误」拒掉，是个死入口。
 * 而账号密码注册的用户仍然需要改密，所以不能一刀切删掉，只能按账号类型条件显示。
 */
const canChangePassword = computed(() => {
  const u = String(user.username || '')
  return !!u && u !== '微信用户' && !u.startsWith('wx_')
})
const unread = ref(0)

// tabBar 页面只能用 switchTab 打开：用 navigateTo 跳 tabBar 页会**静默失败**
// （不报错也不跳，用户表现为"点了没反应"）——「我的订单」就是这个坑（2026-11-08 用户反馈）
const TAB_PAGES = ['index', 'cart', 'orders', 'mine']

// —— 三宫格数据 ——
// 2026-11-15 删掉「我的订单」卡片后，orderCounts / goOrderTab 一并移除（订单走底部 tab）
const couponCount = ref(0)
// 可领取张数（领券中心里尚未领的券）：>0 时 tabBar 显示红点，入口上显示「可领 N」
const claimableCount = ref(0)

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

// 协议类页面游客也能看，不需要登录
const NO_LOGIN_PAGES = ['legal']

function go(page) {
  // 未登录点功能入口 → 弹「需要登录」，取消就留在原地（不再一进个人中心就被踢去登录页）
  if (NO_LOGIN_PAGES.indexOf(page) === -1 && !requireLogin('使用该功能')) return
  const url = '/pages/' + page + '/' + page
  if (TAB_PAGES.indexOf(page) !== -1) {
    uni.switchTab({ url })
    return
  }
  uni.navigateTo({ url })
}

/** 头部点击：未登录去登录页，已登录去个人资料 */
function onUserClick() {
  if (!loggedIn.value) {
    goLogin()
    return
  }
  go('profile')
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
    // ⚠️ /coupons/mine 返回**分页对象** { items, page, size, total, pages }，不是数组。
    // 原来直接对返回值 .filter()，对象上没有 filter → TypeError → 被 catch 成 0，
    // 表现是「明明有券，我的页却显示优惠券 0 张」（与「我的券」页空白同一个根因）。
    const mineList = Array.isArray(cs) ? cs : (cs && cs.items) || []
    couponCount.value = mineList.filter((c) => c.status === 'UNUSED').length
  } catch (e) {
    couponCount.value = 0
  }
  // 有券可领 → tabBar「我的」红点 + 入口上的张数（syncCouponDot 内部有节流）
  try {
    claimableCount.value = await syncCouponDot()
  } catch (e) {
    claimableCount.value = 0
  }
}

function doLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定退出当前账号？',
    success: (r) => {
      if (!r.confirm) return
      clearAuth()
      // 退出后角标必须清掉，否则下一个登录的账号会先看到上一个账号的购物车数字
      clearCartBadge()
      // 券红点同理：不清的话下一个账号会看到"有券可领"的假提示
      clearCouponDot()
      claimableCount.value = 0
      uni.reLaunch({ url: '/pages/index/index' })
    }
  })
}

onShow(() => {
  loggedIn.value = isLoggedIn()
  // 游客也能进个人中心（tabBar 页不该把人弹走），整页显示登录引导
  if (!loggedIn.value) {
    profile.value = null
    walletBalance.value = '0.00'
    couponCount.value = 0
    claimableCount.value = 0
    unread.value = 0
    return
  }
  // 每次进入都从本地缓存同步一次资料字段。
  // 原来只在「username 为空或等于微信用户」时才同步 —— 结果在「个人资料」页改完昵称/头像后
  // 返回这里仍显示旧值（user 是 reactive 快照，页面没重建就不会变），看着像没保存成功。
  const cached = getStoredUser()
  if (cached) {
    user.username = cached.username || user.username
    user.nickname = cached.nickname || ''
    user.avatarUrl = cached.avatarUrl || ''
  }
  load()
  // tab 页每次切回来都对一次角标（内部有 1.2s 节流，不会因为连点 tab 反复请求）
  syncCartBadge()
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

/* 2026-11-15 「我的订单」整卡已删除，.card-head/.card-t/.card-more 与
   .grid4/.g4/.g4-ic/.g4-t/.g4-badge 一并移除（订单入口走底部 tabBar） */

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
/* 「可领 N 张」文字标签：比圆形数字徽标更能表达含义 */
.g2-tag {
  flex-shrink: 0;
  padding: 2rpx 10rpx;
  border-radius: 10rpx;
  background: #FDECEA;
  color: #C0392B;
  font-size: 20rpx;
  font-weight: 600;
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
