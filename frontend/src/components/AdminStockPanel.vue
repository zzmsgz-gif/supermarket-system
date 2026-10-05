<script setup>
import { toRefs } from 'vue';
// 后台「库存预警」面板：从 AdminPanel.vue 整块搬过来的。
//
// 依赖走显式 props（不接 adminCtx）：面板只服务一个 tab，列全依赖比藏起来好。
// stockForm 还被 products 面板（AdminProductsPanel）共用做「入库」——
// 它是 reactive 对象，传引用跨组件写安全，所以这里可以照常改它的字段。
import AdminSearchBox from './AdminSearchBox.vue';
import AdminPageSize from './AdminPageSize.vue';
import AdminPager from './AdminPager.vue';
import AdminStockFormRow from './AdminStockFormRow.vue';

const props = defineProps({
  stockAlerts: { type: Array, required: true },
  stockPage: { type: Number, required: true },
  stockSize: { type: Number, required: true },
  stockFiltered: { type: Array, required: true },
  stockTotalPages: { type: Number, required: true },
  stockPageItems: { type: Array, required: true },
  changeStockPage: { type: Function, required: true },
  goStockPage: { type: Function, required: true },
  resetStockPage: { type: Function, required: true },
  stockForm: { type: Object, required: true },
  openStockForm: { type: Function, required: true },
  submitStock: { type: Function, required: true },
  resetStockSearch: { type: Function, required: true },
});

// ⚠️ 下面这几个用 defineModel 而不是 props —— 它们被 v-model 双向绑定，
// 而 props 只读，面板里赋值会**静默失败**：搜索/翻页看着能点但不动，
//    页面完全正常、控制台也没有报错。共用件（AdminSearchBox / AdminPageSize /
//    AdminPager）本身不用改，它们本来就正确 emit('update:*')。
const stockJumpPage = defineModel('stockJumpPage', { type: Number, required: true });
const stockKeyword = defineModel('stockKeyword', { type: String, required: true });
const { stockAlerts, stockPage, stockSize, stockFiltered, stockTotalPages, stockPageItems, changeStockPage, goStockPage, resetStockPage, stockForm, openStockForm, submitStock, resetStockSearch } = toRefs(props);
</script>

<template>
  <div class="data-panel">
    <div v-if="!stockAlerts.length" class="empty">库存充足，暂无预警</div>
    <template v-else>
      <div class="toolbar">
        <AdminSearchBox v-model="stockKeyword" placeholder="按商品名称 / 编号搜索" @search="resetStockPage" />
        <AdminPageSize v-model="stockSize" @change="resetStockPage" />
        <button class="ghost" @click="resetStockSearch">重置</button>
        <span class="result-count">共 {{ stockFiltered.length }} 条预警</span>
      </div>
      <div v-if="!stockPageItems.length" class="empty">没有匹配「{{ stockKeyword }}」的预警商品</div>
      <div v-else class="table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>商品名称</th>
              <th>商品编号</th>
              <th>当前库存</th>
              <th>预警阈值</th>
              <th>缺口</th>
              <th class="col-action">操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="alert in stockPageItems" :key="alert.id">
              <tr>
                <td><span class="cell-strong">{{ alert.name }}</span></td>
                <td>{{ alert.sku }}</td>
                <td><span class="tag warn">{{ alert.stock }}</span></td>
                <td>{{ alert.lowStockThreshold }}</td>
                <td>{{ Math.max(alert.lowStockThreshold - alert.stock, 0) }}</td>
                <td class="col-action">
                  <div class="row-actions">
                    <button class="ghost" @click="openStockForm(alert, Math.max(alert.lowStockThreshold - alert.stock, 10))">补货</button>
                  </div>
                </td>
              </tr>
              <AdminStockFormRow :form="stockForm" :target="alert" :colspan="6" label="补货数量" @submit="submitStock(alert)" @cancel="stockForm.productId = null" />
            </template>
          </tbody>
        </table>
        <AdminPager :page="stockPage" :total-pages="stockTotalPages" v-model:jump-page="stockJumpPage" @change="changeStockPage" @jump="goStockPage" />
      </div>
    </template>
  </div>
</template>
