<script setup>
import { toRefs, computed } from 'vue';
// 后台「限时秒杀」面板：从 AdminPanel.vue 整块搬过来的。
//
// 依赖刻意走显式 props 而不是 adminCtx：这个面板只服务一个 tab，把真正用到的东西列全，
// 一眼就能看出「它为什么需要这些」。接 adminCtx 会让依赖变成隐式的。
import { money } from '../utils/format';
import ProductPicker from './ProductPicker.vue';
import ImageUpload from './ImageUpload.vue';

const props = defineProps({
  adminFlashSales: { type: Array, required: true },
  categories: { type: Array, required: true },
  closeFlashForm: { type: Function, required: true },
  deleteFlashSale: { type: Function, required: true },
  flashEditingId: { type: Object, required: true },
  flashForm: { type: Object, required: true },
  flashFormOpen: { type: Boolean, required: true },
  flashProductOptions: { type: Array, required: true },
  flashStateClass: { type: Function, required: true },
  flashStateLabel: { type: Function, required: true },
  openFlashForm: { type: Function, required: true },
  saveFlashSale: { type: Function, required: true },
  toggleFlashStatus: { type: Function, required: true },
  /** ProductPicker 自己调接口做模糊搜索（2026-10-10） */
  api: { type: Object, required: true },
});
/** 当前选中的秒杀商品（给 ProductPicker 显示已选标签） */
const selectedFlashProduct = computed(() => {
  const id = Number(flashForm.value?.productId || 0);
  if (!id) return null;
  return (flashProductOptions.value || []).find((x) => Number(x.id) === id) || null;
});

const { adminFlashSales, categories, closeFlashForm, deleteFlashSale, flashEditingId, flashForm, flashFormOpen, flashProductOptions, flashStateClass, flashStateLabel, openFlashForm, saveFlashSale, toggleFlashStatus } = toRefs(props);
</script>

<template>
  <div class="data-panel">
    <div class="form-block">
      <div class="form-title">
        <span>{{ flashEditingId ? '编辑秒杀场次' : '新增秒杀场次' }}</span>
        <button v-if="!flashFormOpen" class="ghost mini" @click="openFlashForm(null)">＋ 新增场次</button>
        <button v-else class="ghost mini" @click="closeFlashForm">收起</button>
      </div>

      <div v-if="flashFormOpen" class="compact-form form-bar">
        <label class="field">
          <span class="field-label">商品来源<i class="req">*</i></span>
          <select v-model="flashForm.sourceMode">
            <option value="existing">克隆现有商品（原商品保持不变）</option>
            <option value="new">新建独立秒杀商品（无原商品）</option>
          </select>
        </label>
        <label v-if="flashForm.sourceMode === 'existing'" class="field">
          <span class="field-label">秒杀商品<i class="req">*</i></span>
          <!-- 2026-10-10 同营销活动：原生 select 装全量商品 → 可搜索选择器 -->
          <ProductPicker
            v-model="flashForm.productId"
            :api="api"
            :selected-items="[selectedFlashProduct].filter(Boolean)"
            placeholder="输入商品名 / SKU 搜索"
          />
        </label>
        <template v-if="flashForm.sourceMode === 'new'">
          <label class="field">
            <span class="field-label">商品名称<i class="req">*</i></span>
            <input v-model="flashForm.productName" placeholder="如「中秋月饼礼盒」" />
          </label>
          <label class="field">
            <span class="field-label">商品分类<i class="req">*</i></span>
            <select v-model.number="flashForm.categoryId">
              <option v-for="cat in categories" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
            </select>
          </label>
          <label class="field">
            <span class="field-label">商品售价<i class="req">*</i></span>
            <input v-model.number="flashForm.price" type="number" step="0.01" min="0.01" placeholder="秒杀价必须低于它" />
          </label>
          <label class="field">
            <span class="field-label">划线原价</span>
            <input v-model.number="flashForm.originalPrice" type="number" step="0.01" min="0" placeholder="可选，用于展示划线价" />
          </label>
          <label class="field">
            <span class="field-label">销售单位</span>
            <input v-model="flashForm.unit" placeholder="默认「件」" />
          </label>
          <label class="field">
            <span class="field-label">副标题</span>
            <input v-model="flashForm.subtitle" placeholder="可选，如「限时直降」" />
          </label>
          <label class="field">
            <span class="field-label">品牌</span>
            <input v-model="flashForm.brand" placeholder="可选" />
          </label>
          <label class="field">
            <span class="field-label">标签</span>
            <input v-model="flashForm.tags" placeholder="可选，逗号分隔，如：生鲜,热销" />
          </label>
          <label class="field field-wide">
            <span class="field-label">商品描述</span>
            <textarea v-model="flashForm.description" rows="3" placeholder="可选，商品卖点、规格、产地等"></textarea>
          </label>
          <label class="field">
            <span class="field-label">商品主图</span>
            <ImageUpload v-model="flashForm.coverUrl" :multiple="false" :max="1" type="product" />
          </label>
          <div class="field field-wide">
            <span class="field-label">多规格 SKU（选填，与商品管理新增商品一致）</span>
            <div class="sku-list">
              <div v-for="(sku, si) in flashForm.skus" :key="si" class="sku-card">
                <div class="sku-specs">
                  <div v-for="(item, ii) in sku.specItems" :key="ii" class="spec-row">
                    <input v-model="item.name" placeholder="规格名，如 颜色" />
                    <input v-model="item.value" placeholder="规格值，如 红" />
                    <button type="button" class="ghost mini danger" @click="sku.specItems.splice(ii, 1)">删</button>
                  </div>
                  <button type="button" class="ghost mini" @click="sku.specItems.push({ name: '', value: '' })">+ 规格项</button>
                  <span class="sku-price-tag" v-if="sku.price !== '' && sku.price != null">规格价 ¥{{ sku.price }}</span>
                </div>
                <div class="sku-meta">
                  <input v-model="sku.skuCode" placeholder="SKU 编码（选填）" />
                  <input v-model="sku.price" placeholder="规格价（选填，留空=跟随商品价）" inputmode="decimal" />
                  <input v-model="sku.originalPrice" placeholder="规格原价（选填，留空=跟随商品原价）" inputmode="decimal" />
                </div>
                <div class="sku-image">
                  <ImageUpload v-model="sku.image" type="product" :multiple="false" :max="1" />
                </div>
                <button type="button" class="ghost mini danger" @click="flashForm.skus.splice(si, 1)">删除该 SKU</button>
              </div>
              <button type="button" class="ghost mini" @click="flashForm.skus.push({ specItems: [{ name: '', value: '' }], skuCode: '', image: '', originalPrice: '' })">+ 新增 SKU</button>
            </div>
          </div>
        </template>
        <label class="field">
          <span class="field-label">场次名</span>
          <input v-model="flashForm.name" placeholder="留空自动生成，如「早市秒杀」" />
        </label>
        <label class="field">
          <span class="field-label">秒杀价<i class="req">*</i></span>
          <input v-model.number="flashForm.flashPrice" type="number" step="0.01" min="0.01" placeholder="必须低于商品售价" />
        </label>
        <label class="field">
          <span class="field-label">秒杀名额<i class="req">*</i></span>
          <input v-model.number="flashForm.totalQuota" type="number" min="1" placeholder="如 30" />
        </label>
        <label class="field">
          <span class="field-label">每人限购</span>
          <input v-model.number="flashForm.perUserLimit" type="number" min="0" placeholder="0 表示不限购" />
        </label>
        <label class="field">
          <span class="field-label">排序</span>
          <input v-model.number="flashForm.sortNo" type="number" placeholder="越小越靠前" />
        </label>
        <label class="field">
          <span class="field-label">开始时间<i class="req">*</i></span>
          <input v-model="flashForm.startTime" type="datetime-local" />
        </label>
        <label class="field">
          <span class="field-label">结束时间<i class="req">*</i></span>
          <input v-model="flashForm.endTime" type="datetime-local" />
        </label>
        <label class="field">
          <span class="field-label">状态</span>
          <select v-model.number="flashForm.status">
            <option :value="1">启用（到时间自动开抢）</option>
            <option :value="0">停用（前台不展示）</option>
          </select>
        </label>
        <div class="store-form-actions">
          <button class="primary" @click="saveFlashSale">{{ flashEditingId ? '保存修改' : '创建场次' }}</button>
          <button class="ghost" @click="closeFlashForm">取消</button>
        </div>
      </div>
    </div>

    <table class="admin-table">
      <thead>
        <tr><th>场次</th><th>商品</th><th>秒杀价 / 售价</th><th>名额</th><th>限购</th><th>档期</th><th>状态</th><th class="col-action">操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="sale in adminFlashSales" :key="sale.id">
          <td><span class="cell-strong">{{ sale.name }}</span></td>
          <td>
            {{ sale.productName }}
            <span v-if="!sale.sourceProductId" class="tag ok">独立商品</span>
            <span v-else class="cell-sub">来自原商品</span>
          </td>
          <td>
            <span class="cell-strong">{{ money(sale.flashPrice) }}</span>
            <span class="cell-sub">售价 {{ money(sale.price) }}</span>
          </td>
          <td>
            {{ sale.soldQuota }}/{{ sale.totalQuota }}
            <span class="cell-sub">剩 {{ sale.remainingQuota }} 件</span>
          </td>
          <td>{{ sale.perUserLimit > 0 ? sale.perUserLimit + ' 件' : '不限' }}</td>
          <td>
            {{ (sale.startTime || '').slice(0, 16).replace('T', ' ') }}
            <span class="cell-sub">至 {{ (sale.endTime || '').slice(0, 16).replace('T', ' ') }}</span>
          </td>
          <td>
            <span :class="flashStateClass(sale)">{{ flashStateLabel(sale) }}</span>
            <span v-if="Number(sale.status) === 0" class="cell-sub">已停用</span>
          </td>
          <td class="col-action">
            <button class="ghost mini" @click="openFlashForm(sale)">编辑</button>
            <button class="ghost mini" @click="toggleFlashStatus(sale)">{{ Number(sale.status) === 1 ? '停用' : '启用' }}</button>
            <button class="ghost mini danger" @click="deleteFlashSale(sale)">删除</button>
          </td>
        </tr>
        <tr v-if="!adminFlashSales.length"><td colspan="8" class="cell-muted">暂无秒杀场次，点右上角「＋ 新增场次」创建</td></tr>
      </tbody>
    </table>
  </div>
</template>
