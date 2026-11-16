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
      <!-- 省/市/区改成三级联动下拉：原来三个自由文本框，同一个地方能写出
           「广东 / 广州」和「广东省 / 广州市」好几种写法，配送范围校验、后台统计都会乱 -->
      <view class="f-row">
        <text class="f-lab">地区</text>
        <picker
          class="f-picker"
          mode="multiSelector"
          :range="regionRange"
          :value="regionIndex"
          @columnchange="onRegionColumn"
          @change="onRegionChange"
        >
          <view class="f-pick-box">
            <text v-if="regionText" class="f-pick-text">{{ regionText }}</text>
            <text v-else class="f-pick-ph">请选择省 / 市 / 区</text>
            <text class="f-pick-arrow">›</text>
          </view>
        </picker>
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
import { ref, reactive, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { listAddresses, createAddress, updateAddress, deleteAddress, setDefaultAddress } from '@/api/address'
import { regionData, citiesOf, districtsOf } from '@/utils/regions'

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

// ==================== 省 / 市 / 区 三级联动 ====================
// 数据来自 utils/regions.js（31 个省级，结构与 PC 端 frontend/src/utils/regions.js 同源）
const PROVINCES = Object.keys(regionData)

const regionIndex = ref([0, 0, 0])
const regionCities = ref([])
const regionDistricts = ref([])
const regionRange = computed(() => [PROVINCES, regionCities.value, regionDistricts.value])

/** 「市辖区」是直辖市数据里的虚拟市级，展示时省掉；但**存库要保留**（后端 city 是 @NotBlank） */
function visibleCity(city) {
  return city === '市辖区' ? '' : (city || '')
}

const regionText = computed(() => {
  const p = PROVINCES[regionIndex.value[0]] || ''
  const c = visibleCity(regionCities.value[regionIndex.value[1]])
  const d = regionDistricts.value[regionIndex.value[2]] || ''
  return [p, c, d].filter(Boolean).join(' ')
})

/** 把已存地址（可能是任意文本）反查成三列下标；匹配不到就落到第一项，不会白屏 */
function resetRegion(province, city, district) {
  let pi = PROVINCES.indexOf(province)
  if (pi < 0) pi = 0
  const cities = Object.keys(citiesOf(PROVINCES[pi]))
  let ci = cities.indexOf(city)
  if (ci < 0) ci = 0
  const dists = districtsOf(PROVINCES[pi], cities[ci]) || []
  let di = dists.indexOf(district)
  if (di < 0) di = 0
  regionCities.value = cities
  regionDistricts.value = dists
  regionIndex.value = [pi, ci, di]
}

function applyRegionToForm() {
  form.province = PROVINCES[regionIndex.value[0]] || ''
  form.city = regionCities.value[regionIndex.value[1]] || ''
  form.district = regionDistricts.value[regionIndex.value[2]] || ''
}

/** 滚动某一列时更新后级联的候选值（否则选了省，市列还是上一个省的城市）*/
function onRegionColumn(e) {
  const { column, value } = e.detail
  const idx = [...regionIndex.value]
  idx[column] = value
  if (column === 0) {
    const cities = Object.keys(citiesOf(PROVINCES[idx[0]]))
    regionCities.value = cities
    regionDistricts.value = districtsOf(PROVINCES[idx[0]], cities[0]) || []
    idx[1] = 0
    idx[2] = 0
  } else if (column === 1) {
    regionDistricts.value = districtsOf(PROVINCES[idx[0]], regionCities.value[idx[1]]) || []
    idx[2] = 0
  }
  regionIndex.value = idx
}

function onRegionChange(e) {
  regionIndex.value = [...e.detail.value]
  applyRegionToForm()
}

function full(a) {
  return (a.province || '') + visibleCity(a.city) + (a.district || '') + (a.detailAddress || '')
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
  // 三级联动从第一项（默认北京）开始；用户不选也能保存会被后端 @NotBlank 拦住，
  // 所以这里先按首项填进 form，避免「什么都没选就保存」时表单看起来已填、实际为空
  resetRegion('', '', '')
  applyRegionToForm()
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
  // 把库里存的省市区反查成三列下标，否则下拉会停在默认项、一保存就把地址改错
  resetRegion(a.province, a.city, a.district)
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
  color: #0F6E56;
  border: 1rpx solid #0F6E56;
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
  color: #C0392B;
}
.empty {
  text-align: center;
  color: #999;
  padding: 120rpx 0;
}
.add {
  margin: 24rpx;
  background: #0F6E56;
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
/* 省市区下拉：整体铺满剩余宽度，右侧箭头提示可点 */
.f-picker {
  flex: 1;
  min-width: 0;
}
.f-pick-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44rpx;
}
.f-pick-text {
  flex: 1;
  min-width: 0;
  font-size: 26rpx;
  color: #1F2937;
  word-break: break-word;
}
.f-pick-ph {
  flex: 1;
  font-size: 26rpx;
  color: #B8B2A6;
}
.f-pick-arrow {
  flex-shrink: 0;
  margin-left: 8rpx;
  font-size: 28rpx;
  color: #C4C0B6;
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
  background: #0F6E56;
  color: #fff;
  border-radius: 48rpx;
}
</style>
