<script setup>
// 后台「商品管理」面板：从 AdminPanel.vue 整块搬过来的（模板 185 行 + 商品表单/编辑/上下架逻辑）。
//
// ⚠️ 三样东西**刻意留在父级**、靠 props 传进来，因为「库存预警」面板也在用它们：
//    `stockForm`（补货表单状态）、`openStockForm`、`submitStock` —— 补货是跨模块共用的，
//    这也是这个面板此前一直没法单独拆出去的原因（09-22 先抽了 AdminStockFormRow 才解开）。
//
// ⚠️ adminCtx 解构列表照抄父级：漏解构一个就是运行时 undefined，模板编译不报错、要跑起来才炸。
import { ref, computed } from 'vue';
import { api } from '../api/client';
import { formatProductStatus, formatUnit, money, resolveUnit } from '../utils/format';
import AdminPageSize from './AdminPageSize.vue';
import AdminSearchBox from './AdminSearchBox.vue';
import AdminPager from './AdminPager.vue';
import AdminStockFormRow from './AdminStockFormRow.vue';

const props = defineProps({
  adminCtx: { type: Object, required: true },
  stockForm: { type: Object, required: true },
  openStockForm: { type: Function, required: true },
  submitStock: { type: Function, required: true },
});
const { adminChartProducts, adminCouponJumpPage, adminCouponKeyword, adminCoupons, adminJumpPage, adminMenu, adminOrderJumpPage, adminOrderKeyword, adminOrderStatus, adminOrders, adminProductKeyword, adminProductStatus, adminProducts, adminAnnouncements, adminBanners, adminStatsOverview, announcementForm, announcementFormOpen, bannerForm, bannerFormOpen, bannerUploading, adminUserJumpPage, adminUserKeyword, adminUserRole, adminUserStatus, adminUsers, alertDialog, askConfirm, categoryName, confirmDialog, coupons, error, fail, filters, loadAdminAnnouncements, loadAdminBanners, loadAdminCoupons, loadAdminOrders, loadAdminProducts, loadAdminStatsOverview, loadAdminUsers, loadCategories, loadProducts, loadRefundOrders, loadStockAlerts, notice, openAnnouncementForm, openBannerForm, openOrderDetail, orderDetail, orders, productForm, saveAnnouncement, products, refreshAdminData, refundJumpPage, refundOrders, refundStatusFilter, run, safeParseSpec, session, showAlert, stockAlerts, closeAnnouncementForm, closeBannerForm, saveBanner, toggleBanner, deleteBanner, toggleAnnouncement, deleteAnnouncement, adminHotSearches, hotSearchForm, hotSearchFormOpen, loadAdminHotSearches, openHotSearchForm, closeHotSearchForm, saveHotSearch, toggleHotSearch, deleteHotSearch } = props.adminCtx;
// 本组件只在后台挂载，与 AdminPanel 内的写法保持一致
const isAdmin = { value: true };

const editingProductId = ref(null);

const editingOpenedStock = ref(null);

const productUnits = [
  { value: 'piece', label: '件' },
  { value: 'bag', label: '袋' },
  { value: 'kg', label: '千克' },
  { value: 'bottle', label: '瓶' },
  { value: 'box', label: '盒' },
  { value: 'custom', label: '自定义' },
];

// 表单里的折扣提示：原来是 AdminPanel 的 computed，只依赖 productForm，属于本面板
// （搬迁时漏了它，导致模板读 formDiscount.save 时 undefined 报错 —— 靠 console 才发现）
const formDiscount = computed(() => {
  const price = Number(productForm.price) || 0;
  const original = Number(productForm.originalPrice) || 0;
  const save = original > price ? original - price : 0;
  const rate = (original > price && price > 0) ? (price / original * 10).toFixed(1) : '';
  return { save, rate };
});

const adminProductTotalPages = computed(() => Math.max(1, Math.ceil((adminProducts.total || 0) / (adminProducts.size || 10))));

async function searchAdminProducts() {
  adminProducts.page = 1;
  await loadAdminProducts();
}

async function changeAdminPage(delta) {
  const next = adminProducts.page + delta;
  if (next < 1 || next > adminProductTotalPages.value) return;
  adminProducts.page = next;
  await loadAdminProducts();
}

async function changeAdminPageSize() {
  adminProducts.page = 1;
  await loadAdminProducts();
}

async function goAdminPage() {
  const p = Number(adminJumpPage.value);
  if (!Number.isInteger(p) || p < 1 || p > adminProductTotalPages.value) {
    adminJumpPage.value = adminProducts.page;
    return;
  }
  adminProducts.page = p;
  await loadAdminProducts();
}

function resetAdminProductSearch() {
  adminProductKeyword.value = '';
  adminProductStatus.value = '';
  adminProducts.page = 1;
  loadAdminProducts();
}

async function saveProduct() {
  if (!productForm.name.trim()) { fail('请填写商品名称'); return; }
  if (!productForm.sku.trim()) { fail('请填写商品编号'); return; }
  if (!(productForm.price > 0)) { fail('售价必须大于 0'); return; }
  if (productForm.originalPrice && Number(productForm.originalPrice) < Number(productForm.price)) { fail('原价不能低于售价'); return; }
  if (productForm.memberPrice && Number(productForm.memberPrice) <= 0) { fail('会员价必须大于 0'); return; }
  if (productForm.memberPrice && Number(productForm.memberPrice) >= Number(productForm.price)) { fail('会员价需低于售价，否则不会生效'); return; }
  if (!(productForm.stock >= 0)) { fail('库存不能为负数'); return; }
  if (!productForm.categoryId) { fail('请选择所属分类'); return; }
  if (productForm.unit === 'custom' && !(productForm.customUnit || '').trim()) { fail('请填写自定义计价单位'); return; }
  const payload = {
    ...productForm,
    categoryId: Number(productForm.categoryId),
    coverUrl: productForm.coverUrl || '',
    originalPrice: productForm.originalPrice ? Number(productForm.originalPrice) : null,
    memberPrice: productForm.memberPrice ? Number(productForm.memberPrice) : null,
    unit: productForm.unit === 'custom' ? (productForm.customUnit || '').trim() : (productForm.unit || 'piece'),
    brand: productForm.brand || '',
    isHot: productForm.isHot ? 1 : 0,
    isNew: productForm.isNew ? 1 : 0,
    tags: productForm.tags || '',
    images: (productForm.images || []).map((url, i) => ({ url, sortNo: i })),
    skus: (productForm.skus || []).map((s, i) => ({
      specJson: JSON.stringify(Object.fromEntries((s.specItems || []).map((x) => [x.name, x.value]).filter(([n]) => n))),
      skuCode: s.skuCode || '',
      image: s.image || '',
      sortNo: i,
    })),
    attributes: (productForm.attributes || []).map((a, i) => ({
      attrName: a.attrName || '',
      attrValue: a.attrValue || '',
      sortNo: i,
    })),
  };
  await run(async () => {
    // 编辑商品时：仅当库存字段相对打开时发生变化才提交，避免用过期库存覆盖真实库存
    if (editingProductId.value && Number(productForm.stock) === Number(editingOpenedStock.value)) {
      delete payload.stock;
    }
    if (editingProductId.value) {
      await api.put(`/admin/products/${editingProductId.value}`, payload);
      editingProductId.value = null;
      await loadAdminProducts();
      await loadProducts();
      resetProductForm();
      return '商品已更新';
    }
    payload.status = 'ON_SALE';
    await api.post('/admin/products', payload);
    resetProductForm();
    await loadProducts();
    await loadAdminProducts();
    return '商品已新增';
  }, editingProductId.value ? '商品已更新' : '商品已新增');

}

function resetProductForm() {
  Object.assign(productForm, { categoryId: '', sku: '', name: '', subtitle: '', description: '', coverUrl: '', price: 0, originalPrice: '', memberPrice: '', stock: 0, unit: 'piece', customUnit: '', brand: '', isHot: false, isNew: false, tags: '', images: [], skus: [], attributes: [] });
}

async function openEditProduct(product) {
  if (isAdmin.value !== true) return;
  const detail = await api.get(`/admin/products/${product.id}`);
  const d = detail.data || detail;
  Object.assign(productForm, {
    categoryId: d.categoryId != null ? String(d.categoryId) : '',
    sku: d.sku || '',
    name: d.name || '',
    subtitle: d.subtitle || '',
    description: d.description || '',
    coverUrl: d.coverUrl || '',
    price: d.price != null ? Number(d.price) : 0,
    originalPrice: d.originalPrice != null ? Number(d.originalPrice) : '',
    memberPrice: d.memberPrice != null ? Number(d.memberPrice) : '',
    stock: d.stock != null ? Number(d.stock) : 0,
    brand: d.brand || '',
    isHot: Number(d.isHot) === 1,
    isNew: Number(d.isNew) === 1,
    tags: d.tags || '',
    images: Array.isArray(d.images) ? d.images.map((it) => it.url).filter(Boolean) : [],
    skus: Array.isArray(d.skus) ? d.skus.map((it) => ({
      specItems: Object.entries(safeParseSpec(it.specJson)).map(([name, value]) => ({ name, value })),
      skuCode: it.skuCode || '',
      image: it.image || '',
    })) : [],
    attributes: Array.isArray(d.attributes) ? d.attributes.map((it) => ({
      attrName: it.attrName || '',
      attrValue: it.attrValue || '',
    })) : [],
    ...resolveUnit(d.unit),
  });
  editingProductId.value = product.id;
  editingOpenedStock.value = Number(d.stock != null ? d.stock : 0);
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function cancelEditProduct() {
  editingProductId.value = null;
  resetProductForm();
}

async function toggleProductStatus(product) {
  const takingOffline = product.status === 'ON_SALE';
  const confirmed = await askConfirm({
    title: takingOffline ? '下架商品' : '上架商品',
    message: takingOffline
      ? '下架后买家在商城里看不到这件商品，也无法下单；已产生的订单不受影响，可以随时重新上架。'
      : '上架后买家可以在商城中看到并购买这件商品。',
    confirmText: takingOffline ? '确认下架' : '确认上架',
    danger: takingOffline,
    details: [
      { label: '商品', value: `${product.name}（${product.sku}）` },
      { label: '售价', value: money(product.price) },
      { label: '当前库存', value: `${product.stock} ${formatUnit(product.unit)}` },
    ],
  });
  if (!confirmed) return;
  await run(async () => {
    await api.patch(`/admin/products/${product.id}/status`, { status: takingOffline ? 'OFF_SALE' : 'ON_SALE' });
    await Promise.all([loadAdminProducts(), loadProducts()]);
  }, takingOffline ? '商品已下架' : '商品已上架');

}

async function deleteProduct(product) {
  const hasOrders = (product.sales ?? 0) > 0;
  const confirmed = await askConfirm({
    title: '删除商品',
    message: hasOrders
      ? '该商品已有销售记录，删除后将不再出现在商品管理与商城中，但历史订单仍然保留、不受影响。此操作不可恢复。'
      : '删除后该商品将不再出现在商品管理与商城中，此操作不可恢复。',
    confirmText: '确认删除',
    danger: true,
    details: [
      { label: '商品', value: `${product.name}（${product.sku}）` },
      { label: '售价', value: money(product.price) },
      { label: '当前库存', value: `${product.stock} ${formatUnit(product.unit)}` },
    ],
  });
  if (!confirmed) return;
  await run(async () => {
    if (stockForm.productId === product.id) stockForm.productId = null;
    await api.delete(`/admin/products/${product.id}`);
    await Promise.all([loadAdminProducts(), loadProducts()]);
  }, '商品已删除');
}
</script>

<template>
          <div class="data-panel">
            <div class="form-block">
              <div class="form-title">
                <span>{{ editingProductId ? '编辑商品' : '新增商品' }}</span>
                <small v-if="editingProductId">正在编辑「{{ productForm.name }}」，保存后更新该商品</small>
                <small v-else>带 <i class="req">*</i> 为必填项，新增后默认直接上架</small>
              </div>
              <form class="compact-form form-bar" @submit.prevent="saveProduct">
                <label class="field">
                  <span class="field-label">商品名称 <i class="req">*</i></span>
                  <input v-model="productForm.name" placeholder="如：海南香蕉 500g/份" />
                </label>
                <label class="field">
                  <span class="field-label">商品编号 <i class="req">*</i></span>
                  <input v-model="productForm.sku" placeholder="如：FRU-1001，不可重复" />
                </label>
                <label class="field">
                  <span class="field-label">售价（元）<i class="req">*</i></span>
                  <input v-model.number="productForm.price" type="number" step="0.01" min="0" placeholder="如：9.90" />
                </label>
                <label class="field">
                  <span class="field-label">原价（划线价，选填）</span>
                  <input v-model.number="productForm.originalPrice" type="number" step="0.01" min="0" placeholder="高于售价时显示划线优惠" />
                </label>
                <label class="field">
                  <span class="field-label">会员价（选填）</span>
                  <input v-model.number="productForm.memberPrice" type="number" step="0.01" min="0" placeholder="低于售价时，会员按此价结算" />
                </label>
                <p v-if="Number(formDiscount.save) > 0" class="field-hint discount-hint">优惠提示：省 {{ money(formDiscount.save) }}，约 {{ formDiscount.rate }} 折</p>
                <label class="field">
                  <span class="field-label">{{ editingProductId ? '库存（设定值）' : '初始库存' }} <i class="req">*</i></span>
                  <input v-model.number="productForm.stock" type="number" min="0" placeholder="如：100" />
                </label>
                <label class="field">
                  <span class="field-label">所属分类 <i class="req">*</i></span>
                  <select v-model="productForm.categoryId">
                    <option value="">请选择分类</option>
                    <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
                  </select>
                </label>
                <label class="field">
                  <span class="field-label">计价单位</span>
                  <select v-model="productForm.unit">
                    <option v-for="u in productUnits" :key="u.value" :value="u.value">{{ u.label }}</option>
                  </select>
                </label>
                <label v-if="productForm.unit === 'custom'" class="field">
                  <span class="field-label">自定义单位 <i class="req">*</i></span>
                  <input v-model="productForm.customUnit" maxlength="10" placeholder="如：箱、包、提、捆" />
                </label>
                <label class="field field-wide">
                  <span class="field-label">副标题</span>
                  <input v-model="productForm.subtitle" placeholder="如：新鲜直采，甜糯可口" />
                </label>
                <label class="field">
                  <span class="field-label">品牌</span>
                  <input v-model="productForm.brand" placeholder="如：农家果园" />
                </label>
                <div class="field-inline-group">
                  <label class="field-inline">
                    <input type="checkbox" v-model="productForm.isHot" />
                    <span>设为热门</span>
                  </label>
                  <label class="field-inline">
                    <input type="checkbox" v-model="productForm.isNew" />
                    <span>设为新品</span>
                  </label>
                </div>
                <label class="field field-wide">
                  <span class="field-label">营销标签（逗号分隔）</span>
                  <input v-model="productForm.tags" placeholder="如：限时特惠,包邮" />
                </label>
                <label class="field field-wide">
                  <span class="field-label">商品封面</span>
                  <image-upload v-model="productForm.coverUrl" type="product" />
                </label>
                <label class="field field-wide">
                  <span class="field-label">商品图集（多图）</span>
                  <image-upload v-model="productForm.images" type="product" multiple :max="9" />
                </label>
                <label class="field field-wide">
                  <span class="field-label">多规格 SKU（选填）</span>
                  <div class="sku-list">
                    <div v-for="(sku, si) in productForm.skus" :key="si" class="sku-card">
                      <div class="sku-specs">
                        <div v-for="(item, ii) in sku.specItems" :key="ii" class="spec-row">
                          <input v-model="item.name" placeholder="规格名，如 颜色" />
                          <input v-model="item.value" placeholder="规格值，如 红色" />
                          <button type="button" class="ghost mini danger" @click="sku.specItems.splice(ii, 1)">删</button>
                        </div>
                      </div>
                      <button type="button" class="ghost mini" @click="sku.specItems.push({ name: '', value: '' })">+ 规格项</button>
                      <div class="sku-meta">
                        <input v-model="sku.skuCode" placeholder="SKU 编码（选填）" />
                        <input v-model="sku.image" placeholder="SKU 图片 URL（选填）" />
                      </div>
                      <button type="button" class="ghost mini danger" @click="productForm.skus.splice(si, 1)">删除该 SKU</button>
                    </div>
                    <button type="button" class="ghost mini" @click="productForm.skus.push({ specItems: [{ name: '', value: '' }], skuCode: '', image: '' })">+ 新增 SKU</button>
                  </div>
                </label>
                <label class="field field-wide">
                  <span class="field-label">参数属性（选填）</span>
                  <div class="attr-list">
                    <div v-for="(attr, ai) in productForm.attributes" :key="ai" class="spec-row">
                      <input v-model="attr.attrName" placeholder="属性名，如 产地" />
                      <input v-model="attr.attrValue" placeholder="属性值，如 海南" />
                      <button type="button" class="ghost mini danger" @click="productForm.attributes.splice(ai, 1)">删</button>
                    </div>
                    <button type="button" class="ghost mini" @click="productForm.attributes.push({ attrName: '', attrValue: '' })">+ 参数项</button>
                  </div>
                </label>
                <label class="field field-wide">
                  <span class="field-label">商品描述</span>
                  <textarea v-model="productForm.description" rows="3" placeholder="介绍规格、产地、卖点等，帮助用户了解商品"></textarea>
                </label>
                <div class="field field-action">
                  <button type="submit">{{ editingProductId ? '保存修改' : '新增商品' }}</button>
                  <button v-if="editingProductId" type="button" class="ghost" @click="cancelEditProduct">取消编辑</button>
                </div>
              </form>
            </div>

            <div class="toolbar">
              <AdminSearchBox v-model="adminProductKeyword" placeholder="按名称 / 编号搜索" @search="searchAdminProducts" />
              <select v-model="adminProductStatus" class="filter-select" @change="searchAdminProducts">
                <option value="">全部状态</option>
                <option value="ON_SALE">上架</option>
                <option value="OFF_SALE">下架</option>
                <option value="DRAFT">草稿</option>
              </select>
              <AdminPageSize v-model="adminProducts.size" @change="changeAdminPageSize" />
              <button class="ghost" @click="resetAdminProductSearch">重置</button>
              <span class="result-count">共 {{ adminProducts.total }} 件商品</span>
            </div>

            <div v-if="!adminProducts.items?.length" class="empty">暂无商品</div>
            <div v-else class="table-wrap">
              <table class="admin-table">
                <thead>
                  <tr>
                    <th>商品名称</th>
                    <th>商品编号</th>
                    <th>所属分类</th>
                    <th>售价</th>
                    <th>会员价</th>
                    <th>库存</th>
                    <th>累计销量</th>
                    <th>状态</th>
                    <th class="col-action">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <template v-for="product in adminProducts.items" :key="product.id">
                    <tr>
                      <td><span class="cell-strong">{{ product.name }}</span><span v-if="product.subtitle" class="cell-sub">{{ product.subtitle }}</span></td>
                      <td>{{ product.sku }}</td>
                      <td>{{ categoryName(product.categoryId) }}</td>
                      <td>
                        <span class="cell-strong">{{ money(product.price) }}</span>
                        <span v-if="Number(product.originalPrice) > 0" class="cell-sub origin-sub">原价 {{ money(product.originalPrice) }}</span>
                        <span class="cell-sub">/ {{ formatUnit(product.unit) || '件' }}</span>
                      </td>
                      <td><span v-if="Number(product.memberPrice) > 0" class="cell-strong member-price-cell">{{ money(product.memberPrice) }}</span><span v-else class="cell-sub">—</span></td>
                      <td><span :class="['tag', Number(product.stock) <= 10 ? 'warn' : '']">{{ product.stock }}</span></td>
                      <td>{{ product.sales ?? 0 }}</td>
                      <td><span :class="['tag', product.status === 'ON_SALE' ? 'ok' : 'muted']">{{ formatProductStatus(product.status) }}</span></td>
                      <td class="col-action">
                        <div class="row-actions">
                          <button class="ghost" @click="openEditProduct(product)">编辑</button>
                          <button class="ghost" @click="openStockForm(product)">入库</button>
                          <button :class="product.status === 'ON_SALE' ? 'danger' : ''" @click="toggleProductStatus(product)">
                            {{ product.status === 'ON_SALE' ? '下架' : '上架' }}
                          </button>
                          <button class="danger ghost" @click="deleteProduct(product)">删除</button>
                        </div>
                      </td>
                    </tr>
                    <AdminStockFormRow :form="stockForm" :target="product" :colspan="8" label="入库数量" @submit="submitStock(product)" @cancel="stockForm.productId = null" />
                  </template>
                </tbody>
              </table>
            </div>
            <AdminPager :page="adminProducts.page" :total-pages="adminProductTotalPages" v-model:jump-page="adminJumpPage" @change="changeAdminPage" @jump="goAdminPage" />
          </div>
</template>
