<template>
<section class="addr-page">
        <div v-if="fromCheckout" class="addr-return-hint">
          📦 添加收货地址后，将自动返回结算页继续下单
        </div>

        <!-- 表单：新增与编辑共用，靠 editingAddressId 区分（走 POST / PUT） -->
        <form class="addr-form" @submit.prevent="saveAddress">
          <div class="panel-head">
            <h2>{{ editingAddressId ? '编辑地址' : '新增地址' }}</h2>
            <button v-if="editingAddressId" type="button" class="ghost sm" @click="cancelEditAddress">
              取消编辑
            </button>
          </div>
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
          <button class="addr-submit">{{ editingAddressId ? '保存修改' : '保存地址' }}</button>
        </form>

        <div class="addr-list-wrap">
          <div class="panel-head">
            <h2>我的收货地址</h2>
            <span class="addr-count">共 {{ addresses.length }} 个</span>
          </div>
          <div v-if="!addresses.length" class="empty">还没有收货地址，左侧填写后保存吧</div>
          <div v-else class="addr-grid">
            <AddressCard
              v-for="address in pagedAddresses"
              :key="address.id"
              :address="address"
              :selected="address.id === selectedAddressId"
              :editing="address.id === editingAddressId"
              @select="useAddress"
              @edit="startEditAddress(address)"
              @remove="removeAddress(address)"
              @set-default="setDefaultAddress(address)"
            />
          </div>

          <!-- 地址多了要翻页：每页 5 条（原来一次全铺，10 条就撑屏了） -->
          <div v-if="addrTotalPages > 1" class="addr-pager">
            <button class="ghost sm" type="button" :disabled="addrPage <= 1" @click="changeAddrPage(-1)">
              上一页
            </button>
            <span class="addr-page-info">第 {{ addrPage }} / {{ addrTotalPages }} 页</span>
            <button class="ghost sm" type="button" :disabled="addrPage >= addrTotalPages"
                    @click="changeAddrPage(1)">
              下一页
            </button>
          </div>
          <small v-if="addresses.length > ADDR_PAGE_SIZE" class="addr-page-hint">
            共 {{ addresses.length }} 个地址，每页 {{ ADDR_PAGE_SIZE }} 个
          </small>
        </div>
      </section>
</template>

<script>
import { inject, computed } from 'vue';
import { useRoute } from 'vue-router';
import { regionData, citiesOf, districtsOf } from '../utils/regions';
import AddressCard from '../components/AddressCard.vue';
export default {
  name: 'AddressesPage',
  // AddressCard 之前只在 App.vue 里 import 却没注册到 components，
  // 模板里写 <AddressCard> 被当成未知自定义元素 —— 地址列表渲染成 0 张卡（2026-10-09）
  components: { AddressCard },
  setup() {
    const appCtx = inject('appCtx');
    // 从结算页补地址过来的：展示提示，并在保存/选择后自动返回结算（由 appCtx.saveAddress/useAddress 读取同一 query）
    const route = useRoute();
    const fromCheckout = computed(() => route.query.redirect === 'checkout');
    // 切换省：清空市/区；切换市：清空区（避免残留不匹配的下级）
    const onRegionProvince = () => { appCtx.addressForm.city = ''; appCtx.addressForm.district = ''; };
    const onRegionCity = () => { appCtx.addressForm.district = ''; };

    /**
     * 删除要二次确认 —— 但**用系统统一的确认对话框**（askConfirm），
     * 不要 window.confirm：原生框和系统风格完全脱节（2026-10-09 用户反馈）。
     * askConfirm 是 Promise 式，resolve(true/false)。
     */
    async function removeAddress(address) {
      const ok = await appCtx.askConfirm({
        title: '删除地址',
        message: '确认删除该地址吗？',
        confirmText: '删除',
        danger: true,
      });
      if (!ok) return;
      appCtx.deleteAddress(address);
    }

    /**
     * 分页切片。
     *
     * <p>⚠️ 这里**必须用 `appCtx.addresses.value`**（2026-10-09 踩坑）：
     * 模板里写 `addresses.length` 能取到 9，是因为模板编译产物会把 ref 重写成 `.value`；
     * 但**普通 JS 里的 `appCtx.addresses` 就是那个 ref 对象**，
     * `.length` 是 undefined → 分页算出 0 页 → 列表渲染成空。
     * 「模板里能用、JS 里不能用」是同一个坑第三次出现，前两次是 pickAvatar 和这次。
     */
    const addrList = computed(() => appCtx.addresses?.value || appCtx.addresses || []);
    const addrPageNo = computed(() => appCtx.addrPage?.value ?? 1);
    const pageSize = computed(() => appCtx.ADDR_PAGE_SIZE || 8);

    const addrTotalPages = computed(() =>
      Math.max(1, Math.ceil((addrList.value.length || 0) / pageSize.value)));
    const pagedAddresses = computed(() => {
      const list = addrList.value;
      const start = (addrPageNo.value - 1) * pageSize.value;
      return list.slice(start, start + pageSize.value);
    });

    return {
      ...appCtx, regionData, citiesOf, districtsOf, onRegionProvince, onRegionCity, fromCheckout,
      removeAddress, pagedAddresses, addrTotalPages,
    };
  }
};
</script>