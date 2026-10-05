<template>
      <section class="admin-layout">
        <aside class="admin-sidebar">
          <div class="sidebar-head">
            <span class="sidebar-eyebrow">管理后台</span>
            <strong>{{ session.user?.username || '管理员' }}</strong>
          </div>
          <nav class="sidebar-nav">
            <template v-for="group in adminMenuGroups" :key="group.name">
              <span class="sidebar-group">{{ group.name }}</span>
              <button
                v-for="item in group.items"
                :key="item.key"
                :class="['sidebar-item', { active: adminMenu === item.key }]"
                @click="selectAdminMenu(item.key)"
              >
                <span class="sidebar-icon" v-html="adminIcons[item.key]"></span>
                <span class="sidebar-label">{{ item.label }}</span>
                <span v-if="item.badge" :class="['sidebar-badge', { warn: item.warn }]">{{ item.badge }}</span>
              </button>
            </template>
          </nav>
          <div class="sidebar-foot">
            <button class="ghost" @click="refreshAdminData">刷新全部数据</button>
          </div>
        </aside>

        <div class="admin-main">
          <header class="admin-head">
            <div>
              <h2>{{ currentAdminMenu?.label }}</h2>
              <small>{{ currentAdminMenu?.desc }}</small>
            </div>
            <button @click="refreshCurrentAdminMenu">刷新</button>
          </header>

          <div v-if="adminMenu === 'orders'" class="data-panel">
            <div class="toolbar">
              <AdminSearchBox v-model="adminOrderKeyword" placeholder="按订单号搜索" @search="searchAdminOrders" />
              <select v-model="adminOrderStatus" class="filter-select" @change="searchAdminOrders">
                <option value="">全部状态</option>
                <option value="PENDING_PAYMENT">待付款</option>
                <option value="PAID">待发货</option>
                <option value="SHIPPED">待收货</option>
                <option value="COMPLETED">已完成</option>
                <option value="CANCELLED">已取消</option>
                <option value="CLOSED">已关闭</option>
              </select>
              <AdminPageSize v-model="adminOrders.size" @change="changeAdminOrderPageSize" />
              <button class="ghost" @click="resetAdminOrderSearch">重置</button>
              <span class="result-count">共 {{ adminOrders.total }} 笔订单</span>
            </div>

            <div v-if="!adminOrders.items?.length" class="empty">暂无订单</div>
            <div v-else class="table-wrap">
              <table class="admin-table">
                <thead>
                  <tr>
                    <th>订单号</th>
                    <th>订单状态</th>
                    <th>支付状态</th>
                    <th>收货人</th>
                    <th>实付金额</th>
                    <th>物流信息</th>
                    <th>下单时间</th>
                    <th class="col-action">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <template v-for="order in adminOrders.items" :key="order.id">
                    <tr>
                      <td><span class="cell-strong order-no-link" @click="openOrderDetail(order)">{{ order.orderNo }}</span><span class="cell-sub">{{ fulfillmentLabel(order) }}</span><span v-if="order.remark" class="cell-sub">备注：{{ order.remark }}</span></td>
                      <td><span :class="['tag', orderStatusTag(order.status)]">{{ orderStatusLabel(order) }}</span></td>
                      <td>{{ formatPaymentStatus(order.paymentStatus) }}</td>
                      <td>{{ order.receiverName || '-' }}<span class="cell-sub">{{ order.receiverPhone || '' }}</span></td>
                      <td><span class="cell-strong">{{ money(order.payAmount) }}</span><span v-if="orderSavedTotal(order) > 0" class="cell-sub">已优惠 {{ money(orderSavedTotal(order)) }}</span></td>
                      <td>
                        <template v-if="order.shipNo">{{ order.shipCompany }}<span class="cell-sub">{{ order.shipNo }}</span></template>
                        <span v-else class="cell-muted">未发货</span>
                      </td>
                      <td>{{ formatDate(order.createdAt) }}</td>
                      <td class="col-action">
                        <div class="row-actions">
                          <button v-if="order.status === 'PAID' && order.fulfillmentType === 'PICKUP'" @click="adminOrderReady(order)">备货完成</button>
                          <button v-else-if="order.status === 'PAID'" @click="openShipForm(order.id)">发货</button>
                          <button v-if="order.status === 'SHIPPED'" @click="completeAdminOrder(order.id)">完成</button>
                          <button v-if="['PENDING_PAYMENT', 'PAID'].includes(order.status)" class="ghost" @click="cancelAdminOrder(order.id)">取消</button>
                          <span v-if="!['PENDING_PAYMENT', 'PAID', 'SHIPPED'].includes(order.status)">-</span>
                        </div>
                      </td>
                    </tr>
                    <tr v-if="shipForm.orderId === order.id" class="row-extra-tr">
                      <td colspan="8">
                        <div class="row-extra">
                          <span class="extra-label">{{ order.fulfillmentType === 'EXPRESS' ? '填写快递信息' : '填写配送信息' }}</span>
                          <input v-model="shipForm.shipCompany" :placeholder="order.fulfillmentType === 'EXPRESS' ? '快递公司' : '配送方（如 美团配送）'" />
                          <input v-model="shipForm.shipNo" :placeholder="order.fulfillmentType === 'EXPRESS' ? '快递单号' : '运单号'" />
                          <button @click="submitShip(order.id)">确认发货</button>
                          <button class="ghost" @click="shipForm.orderId = null">取消</button>
                        </div>
                      </td>
                    </tr>
                  </template>
                </tbody>
              </table>
              <AdminPager :page="adminOrders.page" :total-pages="adminOrderTotalPages" v-model:jump-page="adminOrderJumpPage" @change="changeAdminOrderPage" @jump="goAdminOrderPage" />
            </div>
          </div>

          <AdminRefundsPanel v-if="adminMenu === 'refunds'" :admin-ctx="adminCtx" :search-refunds="searchRefunds" :change-refund-page="changeRefundPage" :change-refund-page-size="changeRefundPageSize" :go-refund-page="goRefundPage" :reset-refund-search="resetRefundSearch" :review-admin-refund="reviewAdminRefund" :submit-refund-review="submitRefundReview" :refund-review-form="refundReviewForm" :refund-total-pages="refundTotalPages"/>
          <div v-if="adminMenu === 'stock'" class="data-panel">
            <div v-if="!stockAlerts.length" class="empty">库存充足，暂无预警</div>
            <template v-else>
              <div class="toolbar">
                <AdminSearchBox v-model="stockKeyword" placeholder="按商品名称 / 编号搜索" @search="stockPage = 1" />
                <AdminPageSize v-model="stockSize" @change="stockPage = 1" />
                <button class="ghost" @click="stockKeyword = ''; stockPage = 1">重置</button>
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

          <AdminProductsPanel v-if="adminMenu === 'products'" :admin-ctx="adminCtx" :stock-form="stockForm" :open-stock-form="openStockForm" :submit-stock="submitStock" />

          <div v-if="adminMenu === 'categories'" class="data-panel">
            <div class="form-block">
              <div class="form-title">
                <span>新增分类</span>
                <small>选「顶级分类」创建一级导航，选其它分类则在其下创建二级分类</small>
              </div>
              <form class="compact-form form-bar" @submit.prevent="saveCategory">
                <label class="field">
                  <span class="field-label">分类名称 <i class="req">*</i></span>
                  <input v-model="categoryForm.name" placeholder="如：进口水果" />
                </label>
                <label class="field">
                  <span class="field-label">上级分类</span>
                  <select v-model.number="categoryForm.parentId">
                    <option :value="0">顶级分类（无上级）</option>
                    <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
                  </select>
                </label>
                <label class="field">
                  <span class="field-label">排序号</span>
                  <input v-model.number="categoryForm.sortNo" type="number" min="0" placeholder="数字越小越靠前，如 10" />
                </label>
                <label class="field">
                  <span class="field-label">分类图标</span>
                  <ImageUpload v-model="categoryForm.iconUrl" :multiple="false" :max="1" type="category" />
                </label>
                <div class="field field-action">
                  <button type="submit">新增分类</button>
                </div>
              </form>
            </div>
            <div v-if="!categories.length" class="empty">暂无分类</div>
            <template v-else>
              <div class="toolbar">
                <AdminSearchBox v-model="categoryKeyword" placeholder="按分类名称搜索" @search="categoryPage = 1" />
                <AdminPageSize v-model="categorySize" @change="categoryPage = 1" />
                <button class="ghost" @click="categoryKeyword = ''; categoryPage = 1">重置</button>
                <span class="result-count">共 {{ categoryFiltered.length }} 个分类</span>
              </div>
              <div v-if="!categoryPageItems.length" class="empty">没有匹配「{{ categoryKeyword }}」的分类</div>
              <div v-else class="table-wrap">
                <table class="admin-table">
                  <thead>
                    <tr>
                      <th>图标</th>
                      <th>分类名称</th>
                      <th>上级分类</th>
                      <th>排序</th>
                      <th>层级</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="category in categoryPageItems" :key="category.id">
                      <td>
                        <img v-if="category.iconUrl" :src="category.iconUrl" class="cat-icon-thumb" :alt="category.name"  loading="lazy" decoding="async"/>
                        <span v-else class="muted">—</span>
                      </td>
                      <td><span class="cell-strong">{{ category.name }}</span></td>
                      <td>{{ categoryName(category.parentId) }}</td>
                      <td>{{ category.sortNo }}</td>
                      <td><span :class="['tag', category.parentId ? 'muted' : '']">{{ category.parentId ? '二级分类' : '一级分类' }}</span></td>
                    </tr>
                  </tbody>
                </table>
                <AdminPager :page="categoryPage" :total-pages="categoryTotalPages" v-model:jump-page="categoryJumpPage" @change="changeCategoryPage" @jump="goCategoryPage" />
              </div>
            </template>
          </div>

          <AdminCouponsPanel v-if="adminMenu === 'coupons'" :admin-ctx="adminCtx" :fill-coupon-period="fillCouponPeriod" :save-coupon="saveCoupon" :search-admin-coupons="searchAdminCoupons" :change-admin-coupon-page-size="changeAdminCouponPageSize" :reset-admin-coupon-search="resetAdminCouponSearch" :toggle-coupon="toggleCoupon" :change-admin-coupon-page="changeAdminCouponPage" :go-admin-coupon-page="goAdminCouponPage" :admin-coupon-total-pages="adminCouponTotalPages" :coupon-form="couponForm"/>
          <AdminInsightsPanel v-if="adminMenu === 'insights'" ref="insightsPanelRef" :admin-ctx="adminCtx" :select-menu="selectAdminMenu" />

          <!-- ===== 限时秒杀管理 ===== -->
          <div v-if="adminMenu === 'flashSales'" class="data-panel">
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
                  <select v-model.number="flashForm.productId">
                    <option v-for="p in flashProductOptions" :key="p.id" :value="p.id">
                      {{ p.name }}（售价 {{ money(p.price) }}）
                    </option>
                  </select>
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
                    <span class="field-label">商品主图</span>
                    <ImageUpload v-model="flashForm.coverUrl" :multiple="false" :max="1" type="product" />
                  </label>
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

          <div v-if="adminMenu === 'activities'" class="data-panel">
            <div class="form-block">
              <div class="form-title">
                <span>{{ activityForm.id ? '编辑活动' : '新增营销活动' }}</span>
                <small>满减：订单达到「门槛金额」后立减「优惠金额」（减额不能大于门槛）；折扣：按「折扣率」打折（0.9 = 9 折，可设最低消费门槛）。全场活动无需选择类目/商品。</small>
                <button type="button" class="field-link title-link" @click="fillActivityPeriod(30)">一键填充：今天起 30 天</button>
              </div>
              <form class="compact-form form-bar" @submit.prevent="saveActivity">
                <label class="field">
                  <span class="field-label">活动名称 <i class="req">*</i></span>
                  <input v-model="activityForm.name" placeholder="如：全场满 200 减 30" />
                </label>
                <label class="field">
                  <span class="field-label">活动类型 <i class="req">*</i></span>
                  <select v-model="activityForm.type">
                    <option value="FULL_REDUCTION">满减</option>
                    <option value="DISCOUNT">折扣</option>
                    <option value="PROMOTION">促销文案（首页顶栏滚动展示，不参与计价）</option>
                  </select>
                </label>
                <label v-if="activityForm.type !== 'PROMOTION'" class="field">
                  <span class="field-label">作用范围 <i class="req">*</i></span>
                  <select v-model="activityForm.scope" @change="onActivityScopeChange">
                    <option value="ALL">全场</option>
                    <option value="CATEGORY">指定类目</option>
                    <option value="PRODUCT">指定商品</option>
                  </select>
                </label>
                <label v-if="activityForm.scope === 'CATEGORY'" class="field">
                  <span class="field-label">适用类目 <i class="req">*</i></span>
                  <select v-model.number="activityForm.categoryId">
                    <option :value="0" disabled>请选择类目</option>
                    <option v-for="cat in categories" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
                  </select>
                </label>
                <label v-if="activityForm.scope === 'PRODUCT'" class="field">
                  <span class="field-label">适用商品 <i class="req">*</i></span>
                  <select v-model.number="activityForm.productId">
                    <option :value="0" disabled>请选择商品</option>
                    <option v-for="p in activityProducts" :key="p.id" :value="p.id">{{ p.name }}</option>
                  </select>
                </label>
                <label v-if="activityForm.type !== 'PROMOTION'" class="field">
                  <span class="field-label">{{ activityForm.type === 'DISCOUNT' ? '最低消费（元）' : '满减门槛（元）' }} <i class="req">*</i></span>
                  <input v-model.number="activityForm.threshold" type="number" step="0.01" min="0" :placeholder="activityForm.type === 'DISCOUNT' ? '可选，0 表示无门槛' : '满多少可用，如 200.00'" />
                </label>
                <label v-if="activityForm.type !== 'PROMOTION'" class="field">
                  <span class="field-label">{{ activityForm.type === 'DISCOUNT' ? '折扣率（0.9=9折）' : '优惠金额（元）' }} <i class="req">*</i></span>
                  <input v-model.number="activityForm.discount" type="number" step="0.01" min="0" :placeholder="activityForm.type === 'DISCOUNT' ? '0.01~0.99，如 0.90' : '立减多少，如 30.00'" />
                </label>
                <label class="field">
                  <span class="field-label">生效时间 <i class="req">*</i></span>
                  <input v-model="activityForm.startTime" type="datetime-local" />
                </label>
                <label class="field">
                  <span class="field-label">过期时间 <i class="req">*</i></span>
                  <input v-model="activityForm.endTime" type="datetime-local" />
                </label>
                <label class="field">
                  <span class="field-label">优先级</span>
                  <input v-model.number="activityForm.priority" type="number" min="0" placeholder="数值越大越优先" />
                </label>
                <div class="field field-action">
                  <button type="submit">{{ activityForm.id ? '保存修改' : '新增活动' }}</button>
                  <button v-if="activityForm.id" type="button" class="ghost" @click="resetActivityForm">取消编辑</button>
                </div>
              </form>
            </div>
            <div v-if="adminActivities.total === 0 && !adminActivityKeyword" class="empty">暂无营销活动，使用上方表单创建第一个活动</div>
            <template v-else>
              <div class="toolbar">
                <AdminSearchBox v-model="adminActivityKeyword" placeholder="按活动名称搜索" @search="searchAdminActivities" />
                <AdminPageSize v-model="adminActivities.size" @change="changeAdminActivityPageSize" />
                <button class="ghost" @click="resetAdminActivitySearch">重置</button>
                <span class="result-count">共 {{ adminActivities.total }} 个活动</span>
              </div>
              <div v-if="!adminActivities.items?.length" class="empty">没有匹配「{{ adminActivityKeyword }}」的活动</div>
              <div v-else class="table-wrap">
                <table class="admin-table">
                  <thead>
                    <tr>
                      <th>活动名称</th>
                      <th>类型 / 范围</th>
                      <th>优惠力度</th>
                      <th>有效期</th>
                      <th>状态</th>
                      <th class="col-action">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="act in adminActivities.items" :key="act.id">
                      <td><span class="cell-strong">{{ act.name }}</span></td>
                      <td>
                        <span class="tag">{{ activityTypeLabel(act.type) }}</span>
                        <span class="tag muted">{{ activityScopeLabel(act.scope) }}</span>
                      </td>
                      <td><span class="tag">{{ activityDiscountLabel(act) }}</span></td>
                      <td>{{ formatDate(act.startTime) }}<span class="cell-sub">至 {{ formatDate(act.endTime) }}</span></td>
                      <td><span :class="['tag', act.status === 1 ? 'ok' : 'muted']">{{ act.status === 1 ? '进行中' : '已停用' }}</span></td>
                      <td class="col-action">
                        <div class="row-actions">
                          <button class="ghost" @click="editActivity(act)">编辑</button>
                          <button class="ghost" @click="toggleActivity(act)">{{ act.status === 1 ? '停用' : '启用' }}</button>
                          <button class="ghost danger" @click="deleteActivity(act)">删除</button>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <AdminPager :page="adminActivities.page" :total-pages="adminActivityTotalPages" v-model:jump-page="adminActivityJumpPage" @change="changeAdminActivityPage" @jump="goAdminActivityPage" />
              </div>
            </template>
          </div>

          <AdminNoticesPanel v-if="adminMenu === 'notices'" :admin-ctx="adminCtx" :notice-type-label="noticeTypeLabel" :notice-type-class="noticeTypeClass" />
          <AdminHotSearchesPanel v-if="adminMenu === 'hotSearches'" :admin-ctx="adminCtx" />

          <!-- 会员日：指定日期消费积分翻倍（从日历上挑具体日期，不再按「每月几号」循环）。
               原先「会员日 每月18号 双倍积分」只是公告里的一句话、后端没实现（假承诺）。
               公告文案由管理员自己维护，系统不改写。 -->
          <div v-if="adminMenu === 'memberDays'" class="data-panel">
            <div class="toolbar">
              <button @click="openMemberDayForm(null)">新增会员日</button>
              <span v-if="memberDayEnabledCount" class="tag muted">生效中 {{ memberDayEnabledCount }} 天 · {{ memberDaySlogan }}</span>
            </div>

            <div v-if="memberDayFormOpen" class="form-card admin-form-card">
              <div class="form-title">
                <span>{{ memberDayForm.id ? '编辑会员日' : '新增会员日' }}</span>
                <small>从日历上挑日期：<b>挑中的那天</b>消费积分按倍率翻倍（2 = 双倍）。
                  可以配多天（比如 10 月 1 日、11 月 11 日各配一条），不再按「每月几号」循环。
                  已配过的日期会划掉、今天之前的日期不可选。
                  公告栏那条「会员日…」是运营文案，<b>由你自己维护</b>，系统不会自动改它。</small>
              </div>
              <div class="admin-form-grid">
                <!-- 挑日期：真日历（可翻月），点一天就是选那个具体日期。
                     已配置的日期划掉不可点 / 已过去的日期不可选 —— 免得填完保存才报错。 -->
                <div class="field span-all">
                  <span class="field-label">会员日日期 <i class="req">*</i></span>
                  <div class="md-calendar">
                    <div class="mdc-head">
                      <button type="button" class="mdc-nav" aria-label="上个月" @click="shiftMemberDayCalendar(-1)">‹</button>
                      <span class="mdc-month">{{ memberDayCalendarMonthText }}</span>
                      <button type="button" class="mdc-nav" aria-label="下个月" @click="shiftMemberDayCalendar(1)">›</button>
                      <button type="button" class="mdc-today" @click="resetMemberDayCalendar">回到本月</button>
                    </div>
                    <div class="mdc-week">
                      <span v-for="w in MDC_WEEK" :key="w">{{ w }}</span>
                    </div>
                    <div class="mdc-grid">
                      <template v-for="(cell, ci) in memberDayCalendarCells" :key="ci">
                        <span v-if="!cell.day" class="mdc-day blank"></span>
                        <button v-else type="button" class="mdc-day"
                                :class="{ on: cell.iso === memberDayForm.memberDate, taken: cell.taken, today: cell.today, past: cell.past }"
                                :disabled="cell.taken || cell.past"
                                :title="cell.past ? '已经过去的日期不能配（那天不会再翻倍）'
                                        : (cell.taken ? `${cell.label} 已经配置过了` : `选 ${cell.label}`)"
                                @click="memberDayForm.memberDate = cell.iso">{{ cell.day }}</button>
                      </template>
                    </div>
                    <p class="mdc-hint">
                      已选：<b>{{ memberDayForm.memberDate ? memberDayDateLabel(memberDayForm.memberDate) : '还没选' }}</b>
                      <span v-if="memberDayDateTaken(memberDayForm.memberDate)" class="mdc-warn">这天已经配置过了，请另选</span>
                      <span v-else-if="memberDayForm.memberDate">· 当天消费积分按 {{ memberDayForm.multiplier || 2 }} 倍发放</span>
                    </p>
                  </div>
                </div>
                <label class="field">
                  <span class="field-label">积分倍率</span>
                  <input v-model.number="memberDayForm.multiplier" type="number" min="1" max="10" step="0.5" placeholder="2 = 双倍" />
                </label>
                <label class="field">
                  <span class="field-label">备注（仅后台可见）</span>
                  <input v-model="memberDayForm.remark" maxlength="60" placeholder="如：超级会员日" />
                </label>
                <div class="field">
                  <span class="field-label">是否启用</span>
                  <label class="check-line"><input type="checkbox" v-model="memberDayForm.enabled" /> 启用（当天消费积分翻倍）</label>
                </div>
              </div>
              <div class="admin-form-foot">
                <button class="ghost" @click="closeMemberDayForm">取消</button>
                <button @click="saveMemberDay">保存</button>
              </div>
            </div>

            <template v-else>
              <div class="admin-cards" v-if="memberDays.length">
                <div v-for="d in memberDays" :key="d.id" class="admin-card" :class="{ 'is-expired': d.expired }">
                  <span class="tag">{{ memberDayDateLabel(d.memberDate) }}</span>
                  <div class="card-info">
                    <p class="card-title"><span class="card-title-text">积分 ×{{ d.multiplier }}</span></p>
                    <p class="card-meta">
                      <span>{{ d.remark || '未填备注' }}</span>
                      <span v-if="d.expired" class="off-word">已过期</span>
                      <span v-else :class="Number(d.enabled) === 1 ? 'on-word' : 'off-word'">{{ Number(d.enabled) === 1 ? '生效中' : '已停用' }}</span>
                    </p>
                  </div>
                  <div class="card-actions">
                    <button class="ghost" @click="openMemberDayForm(d)">编辑</button>
                    <button class="ghost" @click="toggleMemberDay(d)">{{ Number(d.enabled) === 1 ? '停用' : '启用' }}</button>
                    <button class="ghost danger" @click="deleteMemberDay(d)">删除</button>
                  </div>
                </div>
              </div>
              <empty-state v-else icon="star" text="还没有会员日，点上方「新增会员日」从日历上挑一天（公告文案由你自己维护，系统不会代写）" />
            </template>
          </div>

          <AdminBannersPanel v-if="adminMenu === 'banners'" :admin-ctx="adminCtx" :admin-product-name="adminProductName" />
          <div v-if="adminMenu === 'stores'" class="data-panel">
            <div class="toolbar">
              <button class="primary" @click="openStoreForm(null)">+ 新增门店</button>
              <span class="result-count">共 {{ adminStores.length }} 家门店 · 营业 {{ adminStores.filter((s) => Number(s.status) === 1).length }} 家</span>
              <button class="ghost" @click="loadAdminStores">刷新</button>
            </div>

            <form v-if="storeFormOpen" class="compact-form form-bar" @submit.prevent="saveStore">
              <label class="field">
                <span class="field-label">门店名称 <i class="req">*</i></span>
                <input v-model="storeForm.name" maxlength="80" placeholder="如：南山科技园店" />
              </label>
              <label class="field field-wide">
                <span class="field-label">门店地址 <i class="req">*</i></span>
                <input v-model="storeForm.address" maxlength="255" placeholder="如：深圳市南山区科技园南区 8 栋 1 层" />
              </label>
              <label class="field">
                <span class="field-label">联系电话</span>
                <input v-model="storeForm.phone" maxlength="20" placeholder="选填" />
              </label>
              <label class="field">
                <span class="field-label">营业时间</span>
                <input v-model="storeForm.businessHours" maxlength="60" placeholder="如 08:00-22:00" />
              </label>
              <label class="field">
                <span class="field-label">城市</span>
                <input v-model="storeForm.city" maxlength="40" placeholder="选填" />
              </label>
              <label class="field">
                <span class="field-label">区县</span>
                <input v-model="storeForm.district" maxlength="40" placeholder="选填" />
              </label>
              <label class="field field-wide">
                <span class="field-label">即时配送服务区域</span>
                <input
                  v-model="storeForm.serviceAreas"
                  maxlength="255"
                  placeholder="如 深圳市/南山区,深圳市/福田区（留空 = 仅本店所在城市/区）"
                />
                <small class="field-hint">
                  逗号分隔的「城市/区县」；只写城市表示全城可达。即时配送可送达范围 = <b>所有营业中门店</b>的并集，因此门店停业会收缩配送范围；快递配送不受此限制。
                </small>
              </label>
              <label class="field">
                <span class="field-label">排序（越小越靠前）</span>
                <input v-model.number="storeForm.sortNo" type="number" min="0" />
              </label>
              <label class="field">
                <span class="field-label">状态</span>
                <select v-model.number="storeForm.status">
                  <option :value="1">营业（前台可选）</option>
                  <option :value="0">停业（前台不可选）</option>
                </select>
              </label>
              <label class="field field-wide">
                <span class="field-label">自提须知</span>
                <input v-model="storeForm.pickupNotice" maxlength="255" placeholder="显示在结算页门店下方，如：下单后约 1 小时可自提，凭自提码取货" />
              </label>
              <p class="field-hint field-wide" v-if="storeEditingId">正在编辑「{{ storeForm.name || '门店' }}」，保存后更新该门店</p>
              <div class="store-form-actions field-wide">
                <button class="primary" type="submit">保存门店</button>
                <button class="ghost" type="button" @click="closeStoreForm">取消</button>
              </div>
            </form>

            <empty-state v-if="!adminStores.length" icon="cart" text="还没有门店：新增后即可在结算页选择「门店自提」" />
            <div v-else class="table-wrap">
              <table class="admin-table">
                <thead>
                  <tr>
                    <th>门店名称</th>
                    <th>地址</th>
                    <th>电话</th>
                    <th>营业时间</th>
                    <th>排序</th>
                    <th>状态</th>
                    <th class="col-action">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="store in adminStores" :key="store.id">
                    <td>
                      <span class="cell-strong">{{ store.name }}</span>
                      <span v-if="store.city || store.district" class="cell-sub">{{ store.city }}{{ store.district }}</span>
                    </td>
                    <td>{{ store.address }}</td>
                    <td>{{ store.phone || '-' }}</td>
                    <td>{{ store.businessHours || '-' }}</td>
                    <td>{{ store.sortNo }}</td>
                    <td><span :class="['tag', Number(store.status) === 1 ? 'ok' : 'muted']">{{ Number(store.status) === 1 ? '营业' : '停业' }}</span></td>
                    <td class="col-action">
                      <div class="row-actions">
                        <button class="ghost" @click="openStoreForm(store)">编辑</button>
                        <button :class="Number(store.status) === 1 ? 'danger' : ''" @click="toggleStoreStatus(store)">{{ Number(store.status) === 1 ? '停业' : '恢复营业' }}</button>
                        <button class="danger ghost" @click="deleteStore(store)">删除</button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <AdminUsersPanel v-if="adminMenu === 'users'" :admin-ctx="adminCtx" :search-admin-users="searchAdminUsers" :change-admin-user-page="changeAdminUserPage" :change-admin-user-page-size="changeAdminUserPageSize" :go-admin-user-page="goAdminUserPage" :reset-admin-user-search="resetAdminUserSearch" :toggle-user="toggleUser" :member-level-name="memberLevelName" :admin-user-total-pages="adminUserTotalPages"/>
          <AdminReviewsPanel v-if="adminMenu === 'reviews'" :admin-ctx="adminCtx" :search-admin-reviews="searchAdminReviews" :change-admin-review-page="changeAdminReviewPage" :save-review-reply="saveReviewReply" :toggle-review-hidden="toggleReviewHidden" :admin-review-total-pages="adminReviewTotalPages" :admin-reviews="adminReviews" :admin-review-summary="adminReviewSummary" :admin-review-rating="adminReviewRating" :admin-review-replied="adminReviewReplied" :admin-review-keyword="adminReviewKeyword" :load-admin-reviews="loadAdminReviews" :load-admin-review-unreplied="loadAdminReviewUnreplied" :review-reply-draft="reviewReplyDraft"/>
          <div v-if="adminMenu === 'passwordResets'" class="data-panel">
            <!-- 临时密码：只在这一份响应里存在，关掉就再也拿不到（库里只有 BCrypt 哈希） -->
            <div v-if="passwordResetResult" class="form-card admin-form-card temp-pw-card">
              <div class="form-title">
                <span>临时密码已生成</span>
                <small>{{ passwordResetResult.message }}</small>
              </div>
              <div class="temp-pw-meta">
                <span>账号</span><strong>{{ passwordResetResult.username }}</strong>
                <span>昵称</span><strong>{{ passwordResetResult.nickname || '-' }}</strong>
                <span>手机号</span><strong>{{ passwordResetResult.phone || '未填写' }}</strong>
              </div>
              <div class="temp-pw-value">{{ passwordResetResult.tempPassword }}</div>
              <div class="row-actions">
                <button class="ghost" @click="copyTempPassword">复制临时密码</button>
                <button @click="passwordResetResult = null">我已记下，关闭</button>
              </div>
            </div>

            <div class="toolbar">
              <select v-model="passwordResetStatus" class="filter-select" @change="searchPasswordResets">
                <option value="PENDING">待处理</option>
                <option value="">全部状态</option>
                <option value="DONE">已重置</option>
                <option value="REJECTED">已驳回</option>
              </select>
              <AdminPageSize v-model="passwordResets.size" @change="changePasswordResetPageSize" />
              <button class="ghost" @click="refreshCurrentAdminMenu">刷新</button>
              <span class="result-count">共 {{ passwordResets.total }} 条申请</span>
            </div>

            <p class="admin-hint">
              系统没有开通邮件 / 短信，所以「忘记密码」不做自助重置——任何人都能填别人的用户名，那样等于把账号送出去。
              流程是：<strong>先电话核对身份</strong> → 点「重置密码」 → 把生成的一次性临时密码当场告知用户 →
              用户首次登录会被强制改密。
            </p>

            <div v-if="!passwordResets.items?.length" class="empty">没有匹配的找回密码申请</div>
            <div v-else class="table-wrap">
              <table class="admin-table">
                <thead>
                  <tr>
                    <th>提交账号</th>
                    <th>昵称</th>
                    <th>账号预留手机号</th>
                    <th>申请人联系方式</th>
                    <th>提交时间</th>
                    <th>状态</th>
                    <th class="col-action">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in passwordResets.items" :key="item.id">
                    <td><span class="cell-strong">{{ item.username }}</span></td>
                    <td>{{ item.nickname || '-' }}</td>
                    <td>{{ item.phone || '-' }}</td>
                    <td>{{ item.contact || '-' }}</td>
                    <td>{{ formatDate(item.createdAt) }}</td>
                    <td>
                      <span :class="['tag', passwordResetStatusClass(item.status)]">{{ passwordResetStatusLabel(item.status) }}</span>
                      <small v-if="item.remark" class="admin-hint">{{ item.remark }}</small>
                    </td>
                    <td class="col-action">
                      <div v-if="item.status === 'PENDING'" class="row-actions">
                        <button @click="confirmResetPassword(item)">重置密码</button>
                        <button class="ghost" @click="confirmRejectPasswordReset(item)">驳回</button>
                      </div>
                      <span v-else class="admin-hint">已处理</span>
                    </td>
                  </tr>
                </tbody>
              </table>
              <AdminPager :page="passwordResets.page" :total-pages="passwordResetTotalPages" v-model:jump-page="passwordResetJumpPage" @change="changePasswordResetPage" @jump="goPasswordResetPage" />
            </div>
          </div>
        </div>
      </section>
</template>

<script setup>
import { ref, reactive, computed, onMounted, toRef, nextTick, watch, defineAsyncComponent } from 'vue';
import { api } from '../api/client';
import { useAdminStores } from '../composables/useAdminStores.js';
import { useAdminFlash } from '../composables/useAdminFlash.js';
import { useAdminActivities } from '../composables/useAdminActivities.js';
import { useAdminPasswordResets } from '../composables/useAdminPasswordResets.js';
import { useAdminMemberDays } from '../composables/useAdminMemberDays.js';
import { useAdminReviews } from '../composables/useAdminReviews.js';
import { useAdminCategoryStockPaging } from '../composables/useAdminCategoryStockPaging.js';
import { useAdminCategoryStock } from '../composables/useAdminCategoryStock.js';
import { useAdminOrders } from '../composables/useAdminOrders.js';
import { useAdminRefunds } from '../composables/useAdminRefunds.js';
import { useAdminCoupons } from '../composables/useAdminCoupons.js';
import { useAdminUsers } from '../composables/useAdminUsers.js';
import { discountRate, discountSave, fulfillmentLabel, formatCouponStatus, formatDate, formatPaymentStatus, formatProductStatus, formatRefundStatus, formatRole, formatUnit, initials, itemOriginalSave, money, orderSavedTotal, orderStatusLabel, orderStatusTag, refundStatusTag, resolveUnit } from '../utils/format';
import ImageUpload from './ImageUpload.vue';
import AdminPager from './AdminPager.vue';
import AdminStockFormRow from './AdminStockFormRow.vue';
import AdminPageSize from './AdminPageSize.vue';
import AdminSearchBox from './AdminSearchBox.vue';
// 两个重面板改为懒加载：首次进入对应 tab 才下载该面板 chunk，不进 AdminPanel 主包
const AdminInsightsPanel = defineAsyncComponent(() => import('./AdminInsightsPanel.vue'));
const AdminProductsPanel = defineAsyncComponent(() => import('./AdminProductsPanel.vue'));
const AdminBannersPanel = defineAsyncComponent(() => import('./AdminBannersPanel.vue'));
const AdminNoticesPanel = defineAsyncComponent(() => import('./AdminNoticesPanel.vue'));
const AdminHotSearchesPanel = defineAsyncComponent(() => import('./AdminHotSearchesPanel.vue'));
const AdminRefundsPanel = defineAsyncComponent(() => import('./AdminRefundsPanel.vue'));
const AdminReviewsPanel = defineAsyncComponent(() => import('./AdminReviewsPanel.vue'));
const AdminUsersPanel = defineAsyncComponent(() => import('./AdminUsersPanel.vue'));
const AdminCouponsPanel = defineAsyncComponent(() => import('./AdminCouponsPanel.vue'));

const props = defineProps({
  // 响应式：admin 内会读取 view / categories
  view: { type: Object, required: true },
  categories: { type: Array, required: true },
  // 共享上下文：内含 App.vue 的 ref / reactive / 函数（整体传入，避免 Vue 对顶层 ref 自动解包）
  adminCtx: { type: Object, required: true },
});

// AdminPanel only mounts when isAdmin && view==='admin', so isAdmin is always true here.
const isAdmin = { value: true };
const view = toRef(props, 'view');
const categories = toRef(props, 'categories');
const { adminChartProducts, adminCouponJumpPage, adminCouponKeyword, adminCoupons, adminJumpPage, adminMenu, adminOrderJumpPage, adminOrderKeyword, adminOrderStatus, adminOrders, adminProductKeyword, adminProductStatus, adminProducts, adminAnnouncements, adminBanners, adminStatsOverview, announcementForm, announcementFormOpen, bannerForm, bannerFormOpen, bannerUploading, adminUserJumpPage, adminUserKeyword, adminUserRole, adminUserStatus, adminUsers, alertDialog, askConfirm, categoryName, confirmDialog, coupons, error, fail, filters, loadAdminAnnouncements, loadAdminBanners, loadAdminCoupons, loadAdminOrders, loadAdminProducts, loadAdminStatsOverview, loadAdminUsers, loadCategories, loadProducts, loadRefundOrders, loadStockAlerts, notice, openAnnouncementForm, openBannerForm, openOrderDetail, orderDetail, orders, productForm, saveAnnouncement, products, refreshAdminData, refundJumpPage, refundOrders, refundStatusFilter, run, safeParseSpec, session, showAlert, stockAlerts, closeAnnouncementForm, closeBannerForm, saveBanner, toggleBanner, deleteBanner, toggleAnnouncement, deleteAnnouncement, adminHotSearches, hotSearchForm, hotSearchFormOpen, loadAdminHotSearches, openHotSearchForm, closeHotSearchForm, saveHotSearch, toggleHotSearch, deleteHotSearch, } = props.adminCtx;

// 会员等级名称（与后端 MemberService 档位一致，后台仅展示用）
const MEMBER_LEVEL_NAMES = ['普通用户', '银卡会员', '金卡会员', '钻石会员', '紫钻会员', '黑卡会员', '至尊会员'];
const memberLevelName = (level) => MEMBER_LEVEL_NAMES[Number(level) || 0] || '普通用户';

const adminIcon = (paths) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`;

const adminIcons = {
  dashboard: adminIcon('<rect x="3.5" y="11" width="4.5" height="9.5" rx="1"/><rect x="9.75" y="3.5" width="4.5" height="17" rx="1"/><rect x="16" y="7.5" width="4.5" height="13" rx="1"/>'),
  orders: adminIcon('<rect x="4" y="3.5" width="16" height="17" rx="2.5"/><path d="M8.5 9h7M8.5 13h7M8.5 17h4"/>'),
  refunds: adminIcon('<path d="M9.5 14.5 4.5 9.5l5-5"/><path d="M4.5 9.5H15a5.5 5.5 0 0 1 0 11h-4"/>'),
  stock: adminIcon('<path d="M12 4 2.8 20h18.4L12 4z"/><path d="M12 10v4.2M12 17.2h.01"/>'),
  products: adminIcon('<path d="M3.5 7.5 12 3.5l8.5 4-8.5 4-8.5-4z"/><path d="M3.5 7.5v9L12 20.5l8.5-4v-9"/><path d="M12 11.5v9"/>'),
  categories: adminIcon('<rect x="3.5" y="3.5" width="7" height="7" rx="1.5"/><rect x="13.5" y="3.5" width="7" height="7" rx="1.5"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.5"/><rect x="13.5" y="13.5" width="7" height="7" rx="1.5"/>'),
  coupons: adminIcon('<path d="M3.5 8.5A2 2 0 0 1 5.5 6.5h13a2 2 0 0 1 2 2v1.6a2.4 2.4 0 0 0 0 3.8v1.6a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2v-1.6a2.4 2.4 0 0 0 0-3.8V8.5z"/><path d="M12 8v8"/>'),
  users: adminIcon('<circle cx="9" cy="8" r="3.4"/><path d="M3 20.2c0-3.3 2.7-5.2 6-5.2s6 1.9 6 5.2"/><path d="M16.2 5.2a3 3 0 0 1 0 5.6"/><path d="M18.4 14.9c1.9.7 3 2.4 3 4.4"/>'),
  activities: adminIcon('<path d="M3.5 16.5 9 11l3.5 3.5L20.5 7"/><path d="M15.5 7H20.5V12"/>'),
  stores: adminIcon('<path d="M4 9.5 5.2 4.5h13.6L20 9.5"/><path d="M4 9.5h16v10a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-10z"/><path d="M9.5 20.5v-5h5v5"/>'),
  insights: adminIcon('<path d="M4 19.5h16"/><path d="M7 16.5V10M12 16.5V5.5M17 16.5v-4.5"/>'),
  flashSales: adminIcon('<path d="M13.5 2.5 5 13.5h5.5l-1 8 9-11h-5.5l.5-8z"/>'),
  passwordResets: adminIcon('<circle cx="7.5" cy="15.5" r="3.5"/><path d="M10 13 19.5 3.5"/><path d="M16.5 3.5H20V7"/>'),
  memberDays: adminIcon('<rect x="3.5" y="5" width="17" height="15.5" rx="2.5"/><path d="M8 3v4M16 3v4M3.5 10h17"/><path d="M12 12.6l1.1 2.2 2.4.35-1.75 1.7.42 2.4-2.17-1.14-2.17 1.14.42-2.4-1.75-1.7 2.4-.35z"/>'),
};

const adminMenuItems = computed(() => [
  { key: 'insights', label: '经营看板', desc: '全量概览 + 按时间维度看成交、客单价、复购与品类结构（含环比）', group: '经营' },
  { key: 'orders', label: '订单管理', desc: '查询订单、录入快递单号发货、完成或取消订单', group: '经营', badge: (adminOrders.total || 0) || '' },
  { key: 'refunds', label: '售后管理', desc: '审核用户的退款申请，同意后款项退回用户钱包', group: '经营', badge: (refundOrders.total || 0) || '', warn: true },
  { key: 'stock', label: '库存预警', desc: '低于预警阈值的商品列表，支持一键补货', group: '经营', badge: stockAlerts.value.length || '', warn: true },
  { key: 'reviews', label: '评价管理', desc: '查看、回复与隐藏用户评价；角标是未回复数（有人等你回话）', group: '经营', badge: (adminReviewSummary.value?.unrepliedCount || 0) || '', warn: true },
  { key: 'products', label: '商品管理', desc: '新增商品、查看上架状态、手动入库', group: '管理', badge: (adminProducts.total || 0) || '' },
  { key: 'categories', label: '分类管理', desc: '维护商品分类与排序', group: '管理', badge: categories.value.length || '' },
  { key: 'coupons', label: '优惠券管理', desc: '创建满减券、发放与停用', group: '管理', badge: (adminCoupons.total || 0) || '' },
  { key: 'activities', label: '营销活动', desc: '创建满减/折扣活动，按全场、类目或商品精准投放', group: '管理', badge: (adminActivities.total || 0) || '' },
  { key: 'flashSales', label: '限时秒杀', desc: '按商品开秒杀场次：秒杀价、独立名额、每人限购与档期', group: '管理', badge: adminFlashSales.value.filter((f) => f.state === 'RUNNING').length || '' },
  { key: 'notices', label: '公告管理', desc: '发布商城公告：类型分类（促销类标题会进首页顶栏）、排序、随时停用', group: '管理', badge: adminAnnouncements.value.length || '' },
  { key: 'hotSearches', label: '热搜词', desc: '维护首页头部搜索框下方的「热搜」那排词：搜索词、展示文案、排序与启停（点击即跳转搜索）', group: '管理', badge: adminHotSearches.value.length || '' },
  { key: 'memberDays', label: '会员日', desc: '从日历上挑日期：挑中的那天消费积分翻倍（可配多天、可调倍率）；公告文案由你自己维护', group: '管理', badge: memberDays.value.filter((d) => Number(d.enabled) === 1 && !d.expired).length || '' },
  { key: 'stores', label: '门店自提', desc: '维护门店/自提点：名称、地址、营业时间与自提须知，停用后前台不可选', group: '管理', badge: adminStores.value.filter((s) => s.status === 1).length || '' },
  { key: 'banners', label: '轮播管理', desc: '维护首页轮播位：图片、文案、跳转商品与排序', group: '管理', badge: adminBanners.value.length || '' },
  { key: 'users', label: '用户管理', desc: '查看账号余额，启用或禁用账号', group: '管理', badge: (adminUsers.total || 0) || '' },
  { key: 'passwordResets', label: '找回密码', desc: '核对身份后重置为一次性临时密码，用户首次登录强制改密', group: '管理', badge: passwordResets.pending || '', warn: true },
]);

const adminMenuGroups = computed(() => {
  const groups = [];
  for (const item of adminMenuItems.value) {
    let group = groups.find((entry) => entry.name === item.group);
    if (!group) {
      group = { name: item.group, items: [] };
      groups.push(group);
    }
    group.items.push(item);
  }
  return groups;
});

const currentAdminMenu = computed(() => adminMenuItems.value.find((item) => item.key === adminMenu.value) || adminMenuItems.value[0]);

// 进后台就拉一次待处理数：否则菜单角标要等点进「找回密码」才显示，等于没提醒
onMounted(() => {
  loadPasswordResetPendingCount();
  loadAdminReviewUnreplied();
  // 当前模块数据补一次：默认「经营看板」此前没有任何地方会触发 dashboard 接口（只有面板里的手动刷新按钮），
  // 深链 /admin?tab=notices 同理。挂载时兜住，保证「进来就有数据」。
  refreshCurrentAdminMenu();
});

const insightsPanelRef = ref(null);
/* ---------------- 门店 / 秒杀：已抽为 composable ---------------- */
// 装配点必须在 isAdmin / fail / run / askConfirm 之后（adminCtx 已解构出它们）。
const { adminStores, storeFormOpen, storeEditingId, storeForm, loadAdminStores, resetStoreForm, openStoreForm, closeStoreForm, storePayload, saveStore, toggleStoreStatus, deleteStore } = useAdminStores({ isAdmin, fail, run, askConfirm });

const { adminFlashSales, flashFormOpen, flashEditingId, flashProductOptions, flashForm, loadAdminFlashSales, loadFlashProductOptions, flashStateLabel, flashStateClass, toDateTimeInput, openFlashForm, closeFlashForm, saveFlashSale, toggleFlashStatus, deleteFlashSale } = useAdminFlash({ isAdmin, fail, run, askConfirm });

const { adminActivities, adminActivityKeyword, adminActivityJumpPage, adminActivityTotalPages, activityProducts, activityProductsLoaded, activityForm, loadActivityProducts, loadAdminActivities, searchAdminActivities, changeAdminActivityPage, changeAdminActivityPageSize, goAdminActivityPage, resetAdminActivitySearch, resetActivityForm, onActivityScopeChange, fillActivityPeriod, activityTypeLabel, activityScopeLabel, activityDiscountLabel, editActivity, saveActivity, toggleActivity, deleteActivity } = useAdminActivities({ isAdmin, fail, run, askConfirm, categories });

const { passwordResets, passwordResetStatus, passwordResetJumpPage, passwordResetResult, passwordResetTotalPages, PASSWORD_RESET_STATUS_LABELS, passwordResetStatusLabel, passwordResetStatusClass, loadPasswordResets, loadPasswordResetPendingCount, searchPasswordResets, changePasswordResetPage, changePasswordResetPageSize, goPasswordResetPage, confirmResetPassword, confirmRejectPasswordReset, copyTempPassword } = useAdminPasswordResets({ run, fail, askConfirm, notice });

const { memberDays, memberDayFormOpen, memberDayForm, memberDayCalendarCursor, memberDayEnabledCount, memberDaySlogan, MDC_WEEK, isoDateOf, memberDayTodayIso, memberDayCalendarMonthText, memberDayCalendarCells, memberDayDateTaken, memberDayDateLabel, memberDayShortLabel, firstFreeMemberDayDate, shiftMemberDayCalendar, resetMemberDayCalendar, loadMemberDays, openMemberDayForm, closeMemberDayForm, saveMemberDay, toggleMemberDay, deleteMemberDay } = useAdminMemberDays({ isAdmin, fail, run, askConfirm, showAlert });

const { adminReviews, adminReviewSummary, adminReviewRating, adminReviewReplied, adminReviewKeyword, reviewReplyDraft, adminReviewTotalPages, loadAdminReviews, searchAdminReviews, changeAdminReviewPage, loadAdminReviewUnreplied, saveReviewReply, toggleReviewHidden } = useAdminReviews({ run, askConfirm, showAlert });

// ===== 分类 / 库存 / 订单 / 退款 / 优惠券 / 用户：已抽为 composable =====
const { categoryKeyword, categoryPage, categorySize, categoryJumpPage, categoryFiltered, categoryTotalPages, categoryPageItems, changeCategoryPage, goCategoryPage, stockKeyword, stockPage, stockSize, stockJumpPage, stockFiltered, stockTotalPages, stockPageItems, changeStockPage, goStockPage } = useAdminCategoryStockPaging({ categories, stockAlerts });

const { categoryForm, stockForm, noticeTypeLabel, noticeTypeClass, adminProductName, trendHasData, openStockForm, submitStock, saveCategory } = useAdminCategoryStock({ run, fail, askConfirm, loadAdminProducts, loadStockAlerts, loadProducts, loadCategories, adminProducts, adminStatsOverview });

const { shipForm, adminOrderTotalPages, searchAdminOrders, changeAdminOrderPage, changeAdminOrderPageSize, goAdminOrderPage, resetAdminOrderSearch, openShipForm, adminOrderReady, submitShip, completeAdminOrder, cancelAdminOrder } = useAdminOrders({ api, run, fail, askConfirm, money, orderSavedTotal, formatRole, adminOrders, adminOrderKeyword, adminOrderStatus, adminOrderJumpPage, loadAdminOrders, loadRefundOrders, loadAdminUsers });

const { refundReviewForm, refundTotalPages, searchRefunds, changeRefundPage, changeRefundPageSize, goRefundPage, resetRefundSearch, reviewAdminRefund, submitRefundReview } = useAdminRefunds({ api, run, askConfirm, money, orderSavedTotal, refundOrders, refundJumpPage, refundStatusFilter, loadRefundOrders, loadAdminOrders });

const { couponForm, adminCouponTotalPages, searchAdminCoupons, changeAdminCouponPage, changeAdminCouponPageSize, goAdminCouponPage, resetAdminCouponSearch, fillCouponPeriod, saveCoupon, toggleCoupon } = useAdminCoupons({ api, run, fail, askConfirm, money, adminCoupons, adminCouponKeyword, adminCouponJumpPage, loadAdminCoupons });

const { adminUserTotalPages, searchAdminUsers, changeAdminUserPage, changeAdminUserPageSize, goAdminUserPage, resetAdminUserSearch, toggleUser } = useAdminUsers({ api, run, askConfirm, money, formatRole, adminUsers, adminUserKeyword, adminUserRole, adminUserStatus, adminUserJumpPage, loadAdminUsers });

const adminMenuLoaders = {
  insights: () => insightsPanelRef.value?.load(),
  orders: () => loadAdminOrders(),
  refunds: () => loadRefundOrders(),
  reviews: () => loadAdminReviews(),
  stock: () => loadStockAlerts(),
  products: () => loadAdminProducts(),
  categories: () => loadCategories(),
  coupons: () => loadAdminCoupons(),
  activities: () => loadAdminActivities(),
  flashSales: () => loadAdminFlashSales(),
  notices: () => loadAdminAnnouncements(),
  hotSearches: () => loadAdminHotSearches(),
  memberDays: () => loadMemberDays(),
  stores: () => loadAdminStores(),
  banners: () => loadAdminBanners(),
  users: () => loadAdminUsers(),
  passwordResets: () => loadPasswordResets(),
};

// 只负责改 adminMenu。数据加载统一交给下面的 watch —— 「点侧边菜单」和「URL 回填（深链/返回键）」
// 因此走同一条加载路径；写 URL 由 App.vue 的 syncAdminQuery 负责，这里不碰路由。
async function selectAdminMenu(key) {
  if (adminMenu.value === key) {
    await refreshCurrentAdminMenu();   // 值没变 → watch 不触发，这里手动刷新
    return;
  }
  adminMenu.value = key;
}

async function refreshCurrentAdminMenu() {
  const loader = adminMenuLoaders[adminMenu.value];
  if (loader) await loader();

}

// adminMenu 一变就加载对应模块数据：触发源无论是「点侧边菜单」还是「URL 回填（深链/刷新/返回键）」都走这里，
// 不再两处各管一半。挂载时也要跑一次 —— 深链进来时 App.vue 已在 AdminPanel 挂载前就把 adminMenu 改成目标模块，
// watch 看不到这次变化（初值不算变更），只能靠 onMounted 兜住。
watch(adminMenu, () => { refreshCurrentAdminMenu(); });
</script>
