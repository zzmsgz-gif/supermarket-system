<template>
<section class="addr-page">
        <div v-if="fromCheckout" class="addr-return-hint">
          📦 添加收货地址后，将自动返回结算页继续下单
        </div>
        <form class="addr-form" @submit.prevent="saveAddress">
          <div class="panel-head"><h2>新增 / 编辑地址</h2></div>
          <div class="addr-fields">
            <input v-model="addressForm.receiverName" placeholder="收货人姓名" />
            <input v-model="addressForm.receiverPhone" placeholder="手机号码" />
            <div class="region-selects">
              <select v-model="addressForm.province" @change="onRegionProvince" class="region-sel">
                <option value="">省份</option>
                <option v-for="(cities, p) in regionData" :key="p" :value="p">{{ p }}</option>
              </select>
              <select v-model="addressForm.city" @change="onRegionCity" :disabled="!addressForm.province" class="region-sel">
                <option value="">城市</option>
                <option v-for="(districts, c) in citiesOf(addressForm.province)" :key="c" :value="c">{{ c }}</option>
              </select>
              <select v-model="addressForm.district" :disabled="!addressForm.city" class="region-sel">
                <option value="">区 / 县</option>
                <option v-for="d in districtsOf(addressForm.province, addressForm.city)" :key="d" :value="d">{{ d }}</option>
              </select>
            </div>
            <input v-model="addressForm.detailAddress" placeholder="详细地址（街道、门牌号）" />
          </div>
          <label class="check-line"><input type="checkbox" v-model="addressForm.isDefault" /> 设为默认收货地址</label>
          <button class="addr-submit">保存地址</button>
        </form>
        <div class="addr-list-wrap">
          <div class="panel-head">
            <h2>我的收货地址</h2>
            <span class="addr-count">{{ addresses.length }} 个</span>
          </div>
          <div v-if="!addresses.length" class="empty">还没有收货地址，左侧填写后保存吧</div>
          <div v-else class="addr-grid">
            <AddressCard
              v-for="address in addresses"
              :key="address.id"
              :address="address"
              :selected="address.id === selectedAddressId"
              @select="useAddress"
            />
          </div>
        </div>
      </section>
</template>

<script>
import { inject, computed } from 'vue';
import { useRoute } from 'vue-router';
import { regionData, citiesOf, districtsOf } from '../utils/regions';
export default {
  name: 'AddressesPage',
  setup() {
    const appCtx = inject('appCtx');
    // 从结算页补地址过来的：展示提示，并在保存/选择后自动返回结算（由 appCtx.saveAddress/useAddress 读取同一 query）
    const route = useRoute();
    const fromCheckout = computed(() => route.query.redirect === 'checkout');
    // 切换省：清空市/区；切换市：清空区（避免残留不匹配的下级）
    const onRegionProvince = () => { appCtx.addressForm.city = ''; appCtx.addressForm.district = ''; };
    const onRegionCity = () => { appCtx.addressForm.district = ''; };
    return { ...appCtx, regionData, citiesOf, districtsOf, onRegionProvince, onRegionCity, fromCheckout };
  }
};
</script>
