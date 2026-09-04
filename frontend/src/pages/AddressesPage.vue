<template>
<section class="addr-page">
        <form class="addr-form" @submit.prevent="saveAddress">
          <div class="panel-head"><h2>新增 / 编辑地址</h2></div>
          <div class="addr-fields">
            <input v-model="addressForm.receiverName" placeholder="收货人姓名" />
            <input v-model="addressForm.receiverPhone" placeholder="手机号码" />
            <input v-model="addressForm.province" placeholder="省份，如：广东省" />
            <input v-model="addressForm.city" placeholder="城市，如：深圳市" />
            <input v-model="addressForm.district" placeholder="区 / 县，如：南山区" />
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
import { inject } from 'vue';
export default {
  name: 'AddressesPage',
  setup() {
    const appCtx = inject('appCtx');
    return { ...appCtx };
  }
};
</script>
