<template>
  <view class="addr-page">
    <view
      v-for="a in addresses"
      :key="a.id"
      class="addr"
      @click="onPick(a)"
    >
      <view class="a-top">
        <text class="a-name">{{ a.receiverName }}</text>
        <text class="a-phone">{{ a.receiverPhone }}</text>
        <text class="a-def" v-if="a.isDefault">默认</text>
      </view>
      <view class="a-detail">{{ full(a) }}</view>
      <view class="a-ops" @click.stop>
        <text class="op" v-if="!a.isDefault" @click="setDefault(a)">设为默认</text>
        <text class="op" @click="edit(a)">编辑</text>
        <text class="op del" @click="remove(a)">删除</text>
      </view>
    </view>

    <view class="empty" v-if="!addresses.length && !editing">还没有收货地址</view>

    <button class="add" @click="startAdd">+ 新增收货地址</button>

    <!-- 编辑/新增表单 -->
    <view class="form" v-if="editing">
      <view class="f-title">{{ form.id ? '编辑地址' : '新增地址' }}</view>
      <view class="f-row">
        <text class="f-lab">收货人</text>
        <input class="f-in" v-model="form.receiverName" placeholder="姓名" />
      </view>
      <view class="f-row">
        <text class="f-lab">手机号</text>
        <input class="f-in" v-model="form.receiverPhone" placeholder="11 位手机号" />
      </view>
      <view class="f-row">
        <text class="f-lab">省</text>
        <input class="f-in" v-model="form.province" placeholder="省份" />
      </view>
      <view class="f-row">
        <text class="f-lab">市</text>
        <input class="f-in" v-model="form.city" placeholder="城市" />
      </view>
      <view class="f-row">
        <text class="f-lab">区/县</text>
        <input class="f-in" v-model="form.district" placeholder="区/县" />
      </view>
      <view class="f-row">
        <text class="f-lab">详细</text>
        <input class="f-in" v-model="form.detailAddress" placeholder="街道门牌" />
      </view>
      <view class="f-row">
        <text class="f-lab">设默认</text>
        <switch :checked="!!form.isDefault" @change="(e) => (form.isDefault = e.detail.value)" />
      </view>
      <view class="f-actions">
        <button class="f-cancel" @click="editing = false">取消</button>
        <button class="f-save" @click="save">保存</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { listAddresses, createAddress, updateAddress, deleteAddress, setDefaultAddress } from '@/api/address'

const addresses = ref([])
const editing = ref(false)
const from = ref('')
const form = reactive({
  id: null,
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  isDefault: false
})

function full(a) {
  return (a.province || '') + (a.city || '') + (a.district || '') + (a.detailAddress || '')
}

async function load() {
  try {
    addresses.value = await listAddresses()
  } catch (e) {
    addresses.value = []
  }
}

function onPick(a) {
  if (from.value === 'checkout') {
    // 选它为默认并返回结算页（结算页 onShow 会读取默认地址）
    if (!a.isDefault) {
      setDefault(a)
    } else {
      uni.navigateBack()
    }
  }
}

async function setDefault(a) {
  try {
    await setDefaultAddress(a.id)
    uni.showToast({ title: '已设为默认', icon: 'none' })
    await load()
    if (from.value === 'checkout') uni.navigateBack()
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
  }
}

function startAdd() {
  Object.assign(form, {
    id: null,
    receiverName: '',
    receiverPhone: '',
    province: '',
    city: '',
    district: '',
    detailAddress: '',
    isDefault: false
  })
  editing.value = true
}

function edit(a) {
  Object.assign(form, {
    id: a.id,
    receiverName: a.receiverName,
    receiverPhone: a.receiverPhone,
    province: a.province,
    city: a.city,
    district: a.district,
    detailAddress: a.detailAddress,
    isDefault: !!a.isDefault
  })
  editing.value = true
}

function remove(a) {
  uni.showModal({
    title: '删除地址',
    content: '确定删除该收货地址？',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await deleteAddress(a.id)
        uni.showToast({ title: '已删除', icon: 'none' })
        load()
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '删除失败', icon: 'none' })
      }
    }
  })
}

async function save() {
  if (!form.receiverName || !form.receiverPhone || !form.detailAddress) {
    uni.showToast({ title: '请填写完整信息', icon: 'none' })
    return
  }
  const payload = {
    receiverName: form.receiverName,
    receiverPhone: form.receiverPhone,
    province: form.province,
    city: form.city,
    district: form.district,
    detailAddress: form.detailAddress,
    isDefault: !!form.isDefault
  }
  uni.showLoading({ title: '保存中' })
  try {
    if (form.id) await updateAddress(form.id, payload)
    else await createAddress(payload)
    uni.hideLoading()
    editing.value = false
    uni.showToast({ title: '已保存', icon: 'success' })
    load()
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '保存失败', icon: 'none' })
  }
}

onLoad((opts) => {
  from.value = opts.from || ''
})
onShow(load)
</script>

<style scoped>
.addr-page {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 24rpx;
}
.addr {
  background: #fff;
  margin: 16rpx;
  border-radius: 12rpx;
  padding: 20rpx 24rpx;
}
.a-top {
  display: flex;
  align-items: center;
}
.a-name {
  font-size: 28rpx;
  font-weight: bold;
}
.a-phone {
  font-size: 24rpx;
  color: #666;
  margin-left: 16rpx;
}
.a-def {
  font-size: 20rpx;
  color: #07c160;
  border: 1rpx solid #07c160;
  border-radius: 20rpx;
  padding: 2rpx 12rpx;
  margin-left: 16rpx;
}
.a-detail {
  font-size: 24rpx;
  color: #444;
  margin-top: 10rpx;
}
.a-ops {
  display: flex;
  justify-content: flex-end;
  margin-top: 12rpx;
}
.op {
  font-size: 24rpx;
  color: #666;
  margin-left: 24rpx;
}
.op.del {
  color: #e4393c;
}
.empty {
  text-align: center;
  color: #999;
  padding: 120rpx 0;
}
.add {
  margin: 24rpx;
  background: #07c160;
  color: #fff;
  border-radius: 48rpx;
}
.form {
  background: #fff;
  margin: 16rpx;
  border-radius: 12rpx;
  padding: 24rpx;
}
.f-title {
  font-size: 30rpx;
  font-weight: bold;
  margin-bottom: 16rpx;
}
.f-row {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f2f2f2;
}
.f-lab {
  width: 140rpx;
  font-size: 26rpx;
  color: #666;
}
.f-in {
  flex: 1;
  font-size: 26rpx;
}
.f-actions {
  display: flex;
  margin-top: 24rpx;
}
.f-cancel {
  flex: 1;
  background: #fff;
  color: #666;
  border: 1rpx solid #ddd;
  border-radius: 48rpx;
  margin-right: 16rpx;
}
.f-save {
  flex: 1;
  background: #07c160;
  color: #fff;
  border-radius: 48rpx;
}
</style>
