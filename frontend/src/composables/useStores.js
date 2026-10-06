/**
 * 履约域：同城即时配送 / 快递配送 / 门店自提
 *
 * 从 App.vue 抽出（原 1309-1344 / 1521-1538 行）。三种互斥的交付形态，
 * 与后端 OrderEntity.FULFILLMENT_* 一一对应；改造前的旧值 DELIVERY 已迁移为 INSTANT，
 * 这里不再产生该值。
 *
 * 依赖注入说明：
 * - getCartLocalTotal：expressFreight 判快递免邮门槛要用「商品小计」，
 *   而 cartLocalTotal 属于购物车域。为避免 composable 间循环依赖（购物车也要用履约），
 *   由 App.vue 以 getter 形式注入，不在本模块直接引用购物车。
 * - api 直接 import（无状态）。这里曾把 notice / fail 列为形参但函数体里一次都没用，
 *   App.vue 也从未传过 —— 留着只会让「依赖注入漏传」体检误报，已删除。
 */
import { computed, reactive, ref } from 'vue';
import { api } from '../api/client';

export function useStores({ getCartLocalTotal }) {
  const stores = ref([]);
  const deliverySlots = ref([]);
  const fulfillment = reactive({ type: 'INSTANT', storeId: null, slot: '' });

  // ⚠️ 必须与后端 OrderService.EXPRESS_FREE_THRESHOLD / EXPRESS_FREIGHT 保持一致
  const EXPRESS_FREE_THRESHOLD = 99;
  const EXPRESS_FREIGHT = 8;

  const isPickup = computed(() => fulfillment.type === 'PICKUP');
  const isExpress = computed(() => fulfillment.type === 'EXPRESS');

  // 快递运费：按**商品小计**判门槛（不含运费本身），即时配送与门店自提恒为 0
  const expressFreight = computed(() => {
    if (!isExpress.value) return 0;
    const subtotal = typeof getCartLocalTotal === 'function' ? getCartLocalTotal() : 0;
    return subtotal >= EXPRESS_FREE_THRESHOLD ? 0 : EXPRESS_FREIGHT;
  });

  // 生效门店：显式选过就用选的，否则回落到第一家营业门店（与 activeStoreId 保持一致）
  const selectedStore = computed(() => {
    const id = Number(fulfillment.storeId) || (stores.value.length ? Number(stores.value[0].id) : 0);
    return stores.value.find((s) => Number(s.id) === id) || null;
  });

  // 自提门店的默认选择：进结算页时若没选过，自动选中第一家营业门店
  const activeStoreId = computed(() => {
    if (fulfillment.storeId) return Number(fulfillment.storeId);
    return stores.value.length ? Number(stores.value[0].id) : null;
  });

  async function loadStores() {
    try { stores.value = (await api.get('/stores')) || []; } catch (e) { /* ignore */ }
  }

  async function loadDeliverySlots() {
    try { deliverySlots.value = (await api.get('/delivery-slots')) || []; } catch (e) { /* ignore */ }
  }

  // 三种履约互斥：切到自提要清掉时段，切到快递也要清时段（快递的时效由第三方决定，不自选时段）
  function selectFulfillment(type) {
    const next = ['PICKUP', 'EXPRESS'].includes(type) ? type : 'INSTANT';
    fulfillment.type = next;
    if (next !== 'INSTANT') fulfillment.slot = '';
    if (next === 'PICKUP' && !fulfillment.storeId && stores.value.length) {
      fulfillment.storeId = Number(stores.value[0].id);
    }
  }

  function selectStore(id) {
    fulfillment.storeId = id ? Number(id) : null;
  }

  function resetFulfillment() {
    fulfillment.type = 'INSTANT';
    fulfillment.slot = '';
  }

  return {
    stores, deliverySlots, fulfillment,
    EXPRESS_FREE_THRESHOLD, EXPRESS_FREIGHT,
    isPickup, isExpress, expressFreight, selectedStore, activeStoreId,
    loadStores, loadDeliverySlots,
    selectFulfillment, selectStore, resetFulfillment,
  };
}
