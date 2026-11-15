<template>
  <view class="pf">
    <!-- 资料卡 -->
    <view class="card">
      <!-- 头像：点一下选图并上传（uni.chooseImage → POST files/upload?type=avatar） -->
      <view class="row" @click="pickAvatar">
        <text class="lbl">头像</text>
        <view class="right">
          <image class="av" :src="form.avatarUrl ? fullUrl(form.avatarUrl) : defaultAvatar" mode="aspectFill" />
          <text class="ar">›</text>
        </view>
      </view>

      <!-- 昵称：按视觉宽度校验（中文 2 列 / 英文 1 列，上限 12 列） -->
      <view class="row">
        <text class="lbl">昵称</text>
        <input
          class="inp"
          v-model="form.nickname"
          placeholder="给自己起个名字"
          maxlength="30"
        />
      </view>
      <view class="hint" :class="{ warn: nickOver }">{{ nickHint }}</view>

      <picker :range="genderOptions" :value="genderIndex" @change="onGender">
        <view class="row">
          <text class="lbl">性别</text>
          <view class="right">
            <text class="val">{{ genderText }}</text>
            <text class="ar">›</text>
          </view>
        </view>
      </picker>

      <picker mode="date" :value="form.birthday || defaultBirthday" :end="today" @change="onBirthday">
        <view class="row">
          <text class="lbl">生日</text>
          <view class="right">
            <text class="val" :class="{ muted: !form.birthday }">{{ form.birthday || '未设置' }}</text>
            <text class="ar">›</text>
          </view>
        </view>
      </picker>

      <view class="row">
        <text class="lbl">邮箱</text>
        <input class="inp" v-model="form.email" placeholder="用于接收通知" />
      </view>

      <view class="row last">
        <text class="lbl">手机号</text>
        <text class="val" :class="{ muted: !form.phone }">{{ form.phone || '未绑定' }}</text>
      </view>
    </view>

    <view class="tip">
      手机号是登录凭证，换绑需要短信验证码。本项目暂未接入短信通道，这里只能查看。
    </view>

    <button class="save" :disabled="saving || nickOver" @click="save">
      {{ saving ? '保存中…' : '保存' }}
    </button>

    <view class="acc">账号：{{ account }}（登录名，不可修改）</view>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchMe, updateProfile } from '@/api/auth'
import { uploadImage } from '@/api/upload'
import { updateStoredUser } from '@/store/auth'
import { fullUrl } from '@/config'
import { nicknameColumns, NICKNAME_MAX_COLUMNS, NICKNAME_TOO_WIDE } from '@/utils/format'

// 与 mine.vue 同款默认头像（内联 SVG，不额外占包体）
const defaultAvatar =
  'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIj48cmVjdCB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgZmlsbD0iI2RkZCIvPjx0ZXh0IHg9IjUwIiB5PSI1OCIgZm9udC1zaXplPSI1MCIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZmlsbD0iIzk5OSI+5Lq6PC90ZXh0Pjwvc3ZnPg=='

const account = ref('')
const saving = ref(false)
const uploading = ref(false)

const form = reactive({
  nickname: '',
  avatarUrl: '',
  gender: '',
  birthday: '',
  email: '',
  phone: ''
})

// —— 性别 ——
// 后端 ProfileUpdateRequest 的校验是 `@Pattern(regexp="^(MALE|FEMALE|SECRET)$")`：
// **空字符串会被拒（"性别取值不合法"）**，只有「不传（null）」才表示不改。
// 另外库里存着老数据用中文（男/女，共 98 条），web 新选项是 MALE/FEMALE，两边混着。
// 所以：展示兼容三种写法；保存永远发 MALE/FEMALE/**SECRET**（"保密"= SECRET，不能发空串）。
const genderOptions = ['男', '女', '保密']
const GENDER_KEYS = ['MALE', 'FEMALE', 'SECRET']

function normalizeGender(v) {
  const s = String(v || '')
  if (s === 'MALE' || s === '男') return 'MALE'
  if (s === 'FEMALE' || s === '女') return 'FEMALE'
  // 旧数据是空 / 或就是 SECRET → 统一按"保密"
  return 'SECRET'
}

const genderIndex = computed(() => {
  const g = normalizeGender(form.gender)
  return g === 'MALE' ? 0 : g === 'FEMALE' ? 1 : 2
})

const genderText = computed(() => {
  const g = normalizeGender(form.gender)
  return g === 'MALE' ? '男' : g === 'FEMALE' ? '女' : '保密'
})

function onGender(e) {
  form.gender = GENDER_KEYS[Number(e.detail.value)] || 'SECRET'
}

// —— 生日 ——
const today = new Date().toISOString().slice(0, 10)
const defaultBirthday = '1995-01-01'

function onBirthday(e) {
  form.birthday = e.detail.value
}

// —— 昵称 ——
// 后端建微信账号时会把昵称写成「微信用户」（AuthService.wechatLogin 的默认值），
// 那是系统占位词而不是用户起的名字。如果原样回填到输入框，用户点进去看到
// 「微信用户」杵在框里还得先删掉才能输入（用户反馈：「这种提示文字在点击后不应该还留在那里」）。
// 所以：**把默认昵称视为「未设置」→ 框里留空 + 显示 placeholder 提示**；
// 保存时如果用户没填，就不提交 nickname 字段（后端 hasText 才更新），保持原值不动。
const DEFAULT_NICKNAME = '微信用户'

// —— 昵称宽度 ——
const nickColumns = computed(() => nicknameColumns(form.nickname))
const nickOver = computed(() => nickColumns.value > NICKNAME_MAX_COLUMNS)
const nickHint = computed(() => {
  if (!String(form.nickname || '').trim()) return '留空则保持原昵称不变'
  return nickOver.value ? NICKNAME_TOO_WIDE : `已用 ${nickColumns.value}/${NICKNAME_MAX_COLUMNS} 列（中文算 2 列）`
})

// —— 加载 ——
// 记住服务端的原始昵称：用于判断"用户是不是把原本有的昵称清空了"
const originalNickname = ref('')

onLoad(async () => {
  try {
    const me = await fetchMe()
    if (!me) return
    account.value = me.username || ''
    originalNickname.value = me.nickname || ''
    // 系统默认昵称「微信用户」视作未设置 → 输入框留空，显示 placeholder
    form.nickname = (me.nickname && me.nickname !== DEFAULT_NICKNAME) ? me.nickname : ''
    form.avatarUrl = me.avatarUrl || ''
    form.gender = normalizeGender(me.gender)
    form.birthday = me.birthday || ''
    form.email = me.email || ''
    form.phone = me.phone || ''
    // 顺手把本地缓存对齐一次：别的设备改过资料，这里能看到最新值
    updateStoredUser({
      nickname: me.nickname,
      avatarUrl: me.avatarUrl,
      gender: me.gender,
      birthday: me.birthday,
      email: me.email,
      phone: me.phone
    })
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '加载资料失败', icon: 'none' })
  }
})

// —— 头像 ——
async function pickAvatar() {
  if (uploading.value) return
  let temp = ''
  try {
    const res = await uni.chooseImage({ count: 1, sizeType: ['compressed'], sourceType: ['album', 'camera'] })
    const paths = (res && res.tempFilePaths) || []
    temp = paths[0] || ''
  } catch (e) {
    return // 用户取消
  }
  if (!temp) return
  uploading.value = true
  uni.showLoading({ title: '上传中', mask: true })
  try {
    const url = await uploadImage(temp, 'avatar')
    form.avatarUrl = url
    uni.hideLoading()
    uni.showToast({ title: '头像已上传', icon: 'none' })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '上传失败', icon: 'none' })
  } finally {
    uploading.value = false
  }
}

// —— 保存 ——
async function save() {
  if (saving.value) return
  // 前端先拦一次，别让后端 400 直接糊在用户脸上
  if (nickOver.value) {
    uni.showToast({ title: NICKNAME_TOO_WIDE, icon: 'none' })
    return
  }
  const trimmedNick = String(form.nickname || '').trim()
  // 昵称为空允许提交（用于"原本就是系统默认昵称、只想改别的字段"），
  // 但如果原本有一个真实昵称、用户却把它清空了，要拦住，避免误清。
  if (!trimmedNick && originalNickname.value && originalNickname.value !== DEFAULT_NICKNAME) {
    uni.showToast({ title: '昵称不能为空', icon: 'none' })
    return
  }
  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    uni.showToast({ title: '邮箱格式不正确', icon: 'none' })
    return
  }
  saving.value = true
  uni.showLoading({ title: '保存中', mask: true })
  try {
    // 手机号不传：后端对 phone 有"必须带 verifyToken 才能换绑"的硬限制，
    // 项目暂无短信通道，所以这一项做成只读、不参与提交。
    const payload = {
      avatarUrl: form.avatarUrl || '',
      // gender 不能发空串（后端 @Pattern 只认 MALE/FEMALE/SECRET），"保密"就是 SECRET
      gender: form.gender || 'SECRET',
      birthday: form.birthday || '',
      email: String(form.email || '').trim()
    }
    // 昵称留空就不传 —— 后端 hasText 才更新，等于保持原值（不会把「微信用户」变成空）
    if (trimmedNick) payload.nickname = trimmedNick
    const updated = await updateProfile(payload)
    // 记下新的原始值，供本次页面内后续保存判断
    originalNickname.value = updated.nickname || ''
    // 同步本地缓存 —— 否则返回个人中心还显示旧昵称，用户会以为没保存成功
    updateStoredUser({
      nickname: updated.nickname,
      avatarUrl: updated.avatarUrl,
      gender: updated.gender,
      birthday: updated.birthday,
      email: updated.email
    })
    uni.hideLoading()
    uni.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 600)
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.pf {
  padding: 20rpx 24rpx 60rpx;
}
.card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 4rpx 26rpx;
  box-shadow: 0 2rpx 10rpx rgba(31, 41, 55, 0.03);
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
  min-height: 100rpx;
  border-bottom: 1rpx solid #f3f0e9;
}
.row.last {
  border-bottom: none;
}
.lbl {
  flex-shrink: 0;
  width: 140rpx;
  font-size: 28rpx;
  color: #8A8578;
}
.right {
  display: flex;
  align-items: center;
  gap: 10rpx;
  flex: 1;
  min-width: 0;
  justify-content: flex-end;
}
.val {
  font-size: 28rpx;
  color: #1F2937;
  text-align: right;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.val.muted {
  color: #B3AFA4;
}
.inp {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  color: #1F2937;
  text-align: right;
}
.ar {
  flex-shrink: 0;
  font-size: 30rpx;
  color: #C9C4B8;
}
.av {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: #F1EDE5;
}
.hint {
  font-size: 22rpx;
  color: #B3AFA4;
  padding: 0 0 16rpx;
}
.hint.warn {
  color: #C0392B;
}
.tip {
  font-size: 22rpx;
  color: #B3AFA4;
  line-height: 1.6;
  padding: 18rpx 8rpx 0;
}
.save {
  margin-top: 40rpx;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 46rpx;
  background: #0F6E56;
  color: #ffffff;
  font-size: 31rpx;
  font-weight: 600;
  border: none;
}
/* 禁用态三件套：:disabled + hover 同写 + 文案/透明度，避免"看着能点、点了没反应" */
.save[disabled],
.save:disabled {
  background: #9DBFB5;
  color: #ffffff;
  opacity: 0.9;
}
.save::after {
  border: none;
}
.acc {
  margin-top: 24rpx;
  text-align: center;
  font-size: 22rpx;
  color: #B3AFA4;
}
</style>
