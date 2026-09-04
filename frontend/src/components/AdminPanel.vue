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

          <div v-if="adminMenu === 'dashboard'" class="data-panel">
            <div class="stat-grid">
              <div class="stat-card">
                <small>上架商品</small>
                <strong>{{ dashboardStats.productCount }}</strong>
              </div>
              <div class="stat-card">
                <small>库存总量</small>
                <strong>{{ dashboardStats.totalStock }}</strong>
              </div>
              <div class="stat-card">
                <small>订单数量</small>
                <strong>{{ dashboardStats.orderCount }}</strong>
              </div>
              <div class="stat-card">
                <small>成交金额</small>
                <strong>{{ money(dashboardStats.salesAmount) }}</strong>
              </div>
            </div>
            <div class="chart-grid">
              <div ref="productChartEl" class="chart-box"></div>
              <div ref="orderChartEl" class="chart-box"></div>
            </div>
          </div>

          <div v-if="adminMenu === 'orders'" class="data-panel">
            <div class="toolbar">
              <div class="search-box admin-search">
                <input v-model="adminOrderKeyword" placeholder="按订单号搜索" @keyup.enter="searchAdminOrders" />
                <button @click="searchAdminOrders">查询</button>
              </div>
              <select v-model="adminOrderStatus" class="filter-select" @change="searchAdminOrders">
                <option value="">全部状态</option>
                <option value="PENDING_PAYMENT">待付款</option>
                <option value="PAID">待发货</option>
                <option value="SHIPPED">待收货</option>
                <option value="COMPLETED">已完成</option>
                <option value="CANCELLED">已取消</option>
                <option value="CLOSED">已关闭</option>
              </select>
              <select v-model="adminOrders.size" class="filter-select" @change="changeAdminOrderPageSize">
                <option :value="10">10 条/页</option>
                <option :value="20">20 条/页</option>
                <option :value="50">50 条/页</option>
              </select>
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
                      <td><span class="cell-strong order-no-link" @click="openOrderDetail(order)">{{ order.orderNo }}</span><span v-if="order.remark" class="cell-sub">备注：{{ order.remark }}</span></td>
                      <td><span :class="['tag', orderStatusTag(order.status)]">{{ formatOrderStatus(order.status) }}</span></td>
                      <td>{{ formatPaymentStatus(order.paymentStatus) }}</td>
                      <td>{{ order.receiverName || '-' }}<span class="cell-sub">{{ order.receiverPhone || '' }}</span></td>
                      <td><span class="cell-strong">{{ money(order.payAmount) }}</span><span v-if="Number(order.discountAmount || 0) + Number(order.activityDiscount || 0)" class="cell-sub">已优惠 {{ money(Number(order.discountAmount || 0) + Number(order.activityDiscount || 0)) }}</span></td>
                      <td>
                        <template v-if="order.shipNo">{{ order.shipCompany }}<span class="cell-sub">{{ order.shipNo }}</span></template>
                        <span v-else class="cell-muted">未发货</span>
                      </td>
                      <td>{{ formatDate(order.createdAt) }}</td>
                      <td class="col-action">
                        <div class="row-actions">
                          <button v-if="order.status === 'PAID'" @click="openShipForm(order.id)">发货</button>
                          <button v-if="order.status === 'SHIPPED'" @click="completeAdminOrder(order.id)">完成</button>
                          <button v-if="['PENDING_PAYMENT', 'PAID'].includes(order.status)" class="ghost" @click="cancelAdminOrder(order.id)">取消</button>
                          <span v-if="!['PENDING_PAYMENT', 'PAID', 'SHIPPED'].includes(order.status)">-</span>
                        </div>
                      </td>
                    </tr>
                    <tr v-if="shipForm.orderId === order.id" class="row-extra-tr">
                      <td colspan="8">
                        <div class="row-extra">
                          <span class="extra-label">填写物流信息</span>
                          <input v-model="shipForm.shipCompany" placeholder="快递公司" />
                          <input v-model="shipForm.shipNo" placeholder="快递单号" />
                          <button @click="submitShip(order.id)">确认发货</button>
                          <button class="ghost" @click="shipForm.orderId = null">取消</button>
                        </div>
                      </td>
                    </tr>
                  </template>
                </tbody>
              </table>
              <div v-if="adminOrders.total > adminOrders.size" class="pagination">
                <button class="ghost" :disabled="adminOrders.page <= 1" @click="changeAdminOrderPage(-1)">上一页</button>
                <span class="page-info">第 {{ adminOrders.page }} / {{ adminOrderTotalPages }} 页</span>
                <button class="ghost" :disabled="adminOrders.page >= adminOrderTotalPages" @click="changeAdminOrderPage(1)">下一页</button>
                <span class="page-jump-wrap">跳至
                  <input type="number" min="1" :max="adminOrderTotalPages" v-model.number="adminOrderJumpPage" class="page-jump" @keyup.enter="goAdminOrderPage" />
                  页
                  <button class="ghost" @click="goAdminOrderPage">跳转</button>
                </span>
              </div>
            </div>
          </div>

          <div v-if="adminMenu === 'refunds'" class="data-panel">
            <div class="toolbar">
              <select v-model="refundStatusFilter" class="filter-select" @change="searchRefunds">
                <option value="APPLYING">申请中</option>
                <option value="APPROVED">已通过</option>
                <option value="REJECTED">已拒绝</option>
              </select>
              <select v-model="refundOrders.size" class="filter-select" @change="changeRefundPageSize">
                <option :value="10">10 条/页</option>
                <option :value="20">20 条/页</option>
                <option :value="50">50 条/页</option>
              </select>
              <button class="ghost" @click="resetRefundSearch">重置</button>
              <span class="result-count">共 {{ refundOrders.total }} 笔退款</span>
            </div>

            <div v-if="!refundOrders.items?.length" class="empty">暂无待处理的退款申请</div>
            <div v-else class="table-wrap">
              <table class="admin-table">
                <thead>
                  <tr>
                    <th>订单号</th>
                    <th>订单状态</th>
                    <th>退款金额</th>
                    <th>退款原因</th>
                    <th>申请时间</th>
                    <th class="col-action">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <template v-for="order in refundOrders.items" :key="order.id">
                    <tr>
                      <td><span class="cell-strong order-no-link" @click="openOrderDetail(order)">{{ order.orderNo }}</span></td>
                      <td><span :class="['tag', refundStatusTag(order.refundStatus)]">{{ formatRefundStatus(order.refundStatus) }}</span></td>
                      <td><span class="cell-strong">{{ money(order.payAmount) }}</span></td>
                      <td>{{ order.refundReason || '未填写' }}</td>
                      <td>{{ formatDate(order.createdAt) }}</td>
                      <td class="col-action">
                        <div class="row-actions">
                          <button v-if="order.refundStatus === 'APPLYING'" @click="reviewAdminRefund(order.id, true)">同意退款</button>
                          <button v-if="order.refundStatus === 'APPLYING'" class="danger" @click="reviewAdminRefund(order.id, false)">拒绝</button>
                          <span v-else>-</span>
                        </div>
                      </td>
                    </tr>
                    <tr v-if="refundReviewForm.orderId === order.id" class="row-extra-tr">
                      <td colspan="6">
                        <div class="row-extra">
                          <span class="extra-label">{{ refundReviewForm.approved ? '同意退款' : '拒绝退款' }}，处理意见</span>
                          <input v-model="refundReviewForm.remark" placeholder="处理意见（选填）" />
                          <button @click="submitRefundReview(order.id)">提交</button>
                          <button class="ghost" @click="refundReviewForm.orderId = null">取消</button>
                        </div>
                      </td>
                    </tr>
                  </template>
                </tbody>
              </table>
              <div v-if="refundOrders.total > refundOrders.size" class="pagination">
                <button class="ghost" :disabled="refundOrders.page <= 1" @click="changeRefundPage(-1)">上一页</button>
                <span class="page-info">第 {{ refundOrders.page }} / {{ refundTotalPages }} 页</span>
                <button class="ghost" :disabled="refundOrders.page >= refundTotalPages" @click="changeRefundPage(1)">下一页</button>
                <span class="page-jump-wrap">跳至
                  <input type="number" min="1" :max="refundTotalPages" v-model.number="refundJumpPage" class="page-jump" @keyup.enter="goRefundPage" />
                  页
                  <button class="ghost" @click="goRefundPage">跳转</button>
                </span>
              </div>
            </div>
          </div>

          <div v-if="adminMenu === 'stock'" class="data-panel">
            <div v-if="!stockAlerts.length" class="empty">库存充足，暂无预警</div>
            <template v-else>
              <div class="toolbar">
                <div class="search-box admin-search">
                  <input v-model="stockKeyword" placeholder="按商品名称 / 编号搜索" @keyup.enter="stockPage = 1" />
                  <button @click="stockPage = 1">查询</button>
                </div>
                <select v-model="stockSize" class="filter-select" @change="stockPage = 1">
                  <option :value="10">10 条/页</option>
                  <option :value="20">20 条/页</option>
                  <option :value="50">50 条/页</option>
                </select>
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
                      <tr v-if="stockForm.productId === alert.id" class="row-extra-tr">
                        <td colspan="6">
                          <div class="row-extra">
                            <span class="extra-label">补货数量</span>
                            <input v-model.number="stockForm.quantity" type="number" min="1" step="1" class="qty-input" />
                            <button type="button" class="chip" @click="stockForm.quantity = 10">+10</button>
                            <button type="button" class="chip" @click="stockForm.quantity = 50">+50</button>
                            <button type="button" class="chip" @click="stockForm.quantity = 100">+100</button>
                            <input v-model="stockForm.remark" placeholder="备注（选填，如：供应商补货）" class="remark-input" />
                            <button @click="submitStock(alert)">确认入库</button>
                            <button class="ghost" @click="stockForm.productId = null">取消</button>
                          </div>
                        </td>
                      </tr>
                    </template>
                  </tbody>
                </table>
                <div v-if="stockFiltered.length > stockSize" class="pagination">
                  <button class="ghost" :disabled="stockPage <= 1" @click="changeStockPage(-1)">上一页</button>
                  <span class="page-info">第 {{ Math.min(stockPage, stockTotalPages) }} / {{ stockTotalPages }} 页</span>
                  <button class="ghost" :disabled="stockPage >= stockTotalPages" @click="changeStockPage(1)">下一页</button>
                  <span class="page-jump-wrap">跳至
                    <input type="number" min="1" :max="stockTotalPages" v-model.number="stockJumpPage" class="page-jump" @keyup.enter="goStockPage" />
                    页
                    <button class="ghost" @click="goStockPage">跳转</button>
                  </span>
                </div>
              </div>
            </template>
          </div>

          <div v-if="adminMenu === 'products'" class="data-panel">
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
              <div class="search-box admin-search">
                <input v-model="adminProductKeyword" placeholder="按名称 / 编号搜索" @keyup.enter="searchAdminProducts" />
                <button @click="searchAdminProducts">查询</button>
              </div>
              <select v-model="adminProductStatus" class="filter-select" @change="searchAdminProducts">
                <option value="">全部状态</option>
                <option value="ON_SALE">上架</option>
                <option value="OFF_SALE">下架</option>
                <option value="DRAFT">草稿</option>
              </select>
              <select v-model="adminProducts.size" class="filter-select" @change="changeAdminPageSize">
                <option :value="10">10 条/页</option>
                <option :value="20">20 条/页</option>
                <option :value="50">50 条/页</option>
              </select>
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
                    <tr v-if="stockForm.productId === product.id" class="row-extra-tr">
                      <td colspan="8">
                        <div class="row-extra">
                          <span class="extra-label">入库数量</span>
                          <input v-model.number="stockForm.quantity" type="number" min="1" step="1" class="qty-input" />
                          <button type="button" class="chip" @click="stockForm.quantity = 10">+10</button>
                          <button type="button" class="chip" @click="stockForm.quantity = 50">+50</button>
                          <button type="button" class="chip" @click="stockForm.quantity = 100">+100</button>
                          <input v-model="stockForm.remark" placeholder="备注（选填，如：供应商补货）" class="remark-input" />
                          <button @click="submitStock(product)">确认入库</button>
                          <button class="ghost" @click="stockForm.productId = null">取消</button>
                        </div>
                      </td>
                    </tr>
                  </template>
                </tbody>
              </table>
            </div>
            <div v-if="adminProducts.total > adminProducts.size" class="pagination">
              <button class="ghost" :disabled="adminProducts.page <= 1" @click="changeAdminPage(-1)">上一页</button>
              <span class="page-info">第 {{ adminProducts.page }} / {{ adminProductTotalPages }} 页</span>
              <button class="ghost" :disabled="adminProducts.page >= adminProductTotalPages" @click="changeAdminPage(1)">下一页</button>
              <span class="page-jump-wrap">跳至
                <input type="number" min="1" :max="adminProductTotalPages" v-model.number="adminJumpPage" class="page-jump" @keyup.enter="goAdminPage" />
                页
                <button class="ghost" @click="goAdminPage">跳转</button>
              </span>
            </div>
          </div>

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
                <div class="field field-action">
                  <button type="submit">新增分类</button>
                </div>
              </form>
            </div>
            <div v-if="!categories.length" class="empty">暂无分类</div>
            <template v-else>
              <div class="toolbar">
                <div class="search-box admin-search">
                  <input v-model="categoryKeyword" placeholder="按分类名称搜索" @keyup.enter="categoryPage = 1" />
                  <button @click="categoryPage = 1">查询</button>
                </div>
                <select v-model="categorySize" class="filter-select" @change="categoryPage = 1">
                  <option :value="10">10 条/页</option>
                  <option :value="20">20 条/页</option>
                  <option :value="50">50 条/页</option>
                </select>
                <button class="ghost" @click="categoryKeyword = ''; categoryPage = 1">重置</button>
                <span class="result-count">共 {{ categoryFiltered.length }} 个分类</span>
              </div>
              <div v-if="!categoryPageItems.length" class="empty">没有匹配「{{ categoryKeyword }}」的分类</div>
              <div v-else class="table-wrap">
                <table class="admin-table">
                  <thead>
                    <tr>
                      <th>分类名称</th>
                      <th>上级分类</th>
                      <th>排序</th>
                      <th>层级</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="category in categoryPageItems" :key="category.id">
                      <td><span class="cell-strong">{{ category.name }}</span></td>
                      <td>{{ categoryName(category.parentId) }}</td>
                      <td>{{ category.sortNo }}</td>
                      <td><span :class="['tag', category.parentId ? 'muted' : '']">{{ category.parentId ? '二级分类' : '一级分类' }}</span></td>
                    </tr>
                  </tbody>
                </table>
                <div v-if="categoryFiltered.length > categorySize" class="pagination">
                  <button class="ghost" :disabled="categoryPage <= 1" @click="changeCategoryPage(-1)">上一页</button>
                  <span class="page-info">第 {{ Math.min(categoryPage, categoryTotalPages) }} / {{ categoryTotalPages }} 页</span>
                  <button class="ghost" :disabled="categoryPage >= categoryTotalPages" @click="changeCategoryPage(1)">下一页</button>
                  <span class="page-jump-wrap">跳至
                    <input type="number" min="1" :max="categoryTotalPages" v-model.number="categoryJumpPage" class="page-jump" @keyup.enter="goCategoryPage" />
                    页
                    <button class="ghost" @click="goCategoryPage">跳转</button>
                  </span>
                </div>
              </div>
            </template>
          </div>

          <div v-if="adminMenu === 'coupons'" class="data-panel">
            <div class="form-block">
              <div class="form-title">
                <span>新增优惠券</span>
                <small>满减券：订单达到「使用门槛」后立减「优惠金额」，优惠金额不能大于门槛；发放总量填 0 表示不限量</small>
                <button type="button" class="field-link title-link" @click="fillCouponPeriod(30)">一键填充：今天起 30 天</button>
              </div>
              <form class="compact-form form-bar" @submit.prevent="saveCoupon">
                <label class="field">
                  <span class="field-label">优惠券名称 <i class="req">*</i></span>
                  <input v-model="couponForm.name" placeholder="如：新用户满 50 减 10" />
                </label>
                <label class="field">
                  <span class="field-label">使用门槛（元）<i class="req">*</i></span>
                  <input v-model.number="couponForm.thresholdAmount" type="number" step="0.01" min="0" placeholder="满多少可用，如 50.00" />
                </label>
                <label class="field">
                  <span class="field-label">优惠金额（元）<i class="req">*</i></span>
                  <input v-model.number="couponForm.discountAmount" type="number" step="0.01" min="0" placeholder="立减多少，如 10.00" />
                </label>
                <label class="field">
                  <span class="field-label">发放总量（张）</span>
                  <input v-model.number="couponForm.totalCount" type="number" min="0" placeholder="0 表示不限量，如 100" />
                </label>
                <label class="field">
                  <span class="field-label">生效时间 <i class="req">*</i></span>
                  <input v-model="couponForm.startTime" type="datetime-local" />
                </label>
                <label class="field">
                  <span class="field-label">过期时间 <i class="req">*</i></span>
                  <input v-model="couponForm.endTime" type="datetime-local" />
                </label>
                <div class="field field-action">
                  <button type="submit">新增优惠券</button>
                </div>
              </form>
            </div>
            <div v-if="adminCoupons.total === 0" class="empty">暂无优惠券</div>
            <template v-else>
              <div class="toolbar">
                <div class="search-box admin-search">
                  <input v-model="adminCouponKeyword" placeholder="按优惠券名称搜索" @keyup.enter="searchAdminCoupons" />
                  <button @click="searchAdminCoupons">查询</button>
                </div>
                <select v-model="adminCoupons.size" class="filter-select" @change="changeAdminCouponPageSize">
                  <option :value="10">10 条/页</option>
                  <option :value="20">20 条/页</option>
                  <option :value="50">50 条/页</option>
                </select>
                <button class="ghost" @click="resetAdminCouponSearch">重置</button>
                <span class="result-count">共 {{ adminCoupons.total }} 张优惠券</span>
              </div>
              <div v-if="!adminCoupons.items?.length" class="empty">没有匹配「{{ adminCouponKeyword }}」的优惠券</div>
              <div v-else class="table-wrap">
                <table class="admin-table">
                  <thead>
                    <tr>
                      <th>优惠券名称</th>
                      <th>优惠力度</th>
                      <th>有效期</th>
                      <th>领取情况</th>
                      <th>状态</th>
                      <th class="col-action">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="coupon in adminCoupons.items" :key="coupon.id">
                      <td><span class="cell-strong">{{ coupon.name }}</span></td>
                      <td><span class="tag">满 {{ money(coupon.thresholdAmount) }} 减 {{ money(coupon.discountAmount) }}</span></td>
                      <td>{{ formatDate(coupon.startTime) }}<span class="cell-sub">至 {{ formatDate(coupon.endTime) }}</span></td>
                      <td>{{ coupon.receivedCount }}<span class="cell-sub">{{ coupon.totalCount ? `限量 ${coupon.totalCount} 张` : '不限量' }}</span></td>
                      <td><span :class="['tag', coupon.status === 1 ? 'ok' : 'muted']">{{ coupon.status === 1 ? '启用中' : '已停用' }}</span></td>
                      <td class="col-action">
                        <div class="row-actions">
                          <button class="ghost" @click="toggleCoupon(coupon)">{{ coupon.status === 1 ? '停用' : '启用' }}</button>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <div v-if="adminCoupons.total > adminCoupons.size" class="pagination">
                  <button class="ghost" :disabled="adminCoupons.page <= 1" @click="changeAdminCouponPage(-1)">上一页</button>
                  <span class="page-info">第 {{ adminCoupons.page }} / {{ adminCouponTotalPages }} 页</span>
                  <button class="ghost" :disabled="adminCoupons.page >= adminCouponTotalPages" @click="changeAdminCouponPage(1)">下一页</button>
                  <span class="page-jump-wrap">跳至
                    <input type="number" min="1" :max="adminCouponTotalPages" v-model.number="adminCouponJumpPage" class="page-jump" @keyup.enter="goAdminCouponPage" />
                    页
                    <button class="ghost" @click="goAdminCouponPage">跳转</button>
                  </span>
                </div>
              </div>
            </template>
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
                  </select>
                </label>
                <label class="field">
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
                <label class="field">
                  <span class="field-label">{{ activityForm.type === 'DISCOUNT' ? '最低消费（元）' : '满减门槛（元）' }} <i class="req">*</i></span>
                  <input v-model.number="activityForm.threshold" type="number" step="0.01" min="0" :placeholder="activityForm.type === 'DISCOUNT' ? '可选，0 表示无门槛' : '满多少可用，如 200.00'" />
                </label>
                <label class="field">
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
                <div class="search-box admin-search">
                  <input v-model="adminActivityKeyword" placeholder="按活动名称搜索" @keyup.enter="searchAdminActivities" />
                  <button @click="searchAdminActivities">查询</button>
                </div>
                <select v-model="adminActivities.size" class="filter-select" @change="changeAdminActivityPageSize">
                  <option :value="10">10 条/页</option>
                  <option :value="20">20 条/页</option>
                  <option :value="50">50 条/页</option>
                </select>
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
                <div v-if="adminActivities.total > adminActivities.size" class="pagination">
                  <button class="ghost" :disabled="adminActivities.page <= 1" @click="changeAdminActivityPage(-1)">上一页</button>
                  <span class="page-info">第 {{ adminActivities.page }} / {{ adminActivityTotalPages }} 页</span>
                  <button class="ghost" :disabled="adminActivities.page >= adminActivityTotalPages" @click="changeAdminActivityPage(1)">下一页</button>
                  <span class="page-jump-wrap">跳至
                    <input type="number" min="1" :max="adminActivityTotalPages" v-model.number="adminActivityJumpPage" class="page-jump" @keyup.enter="goAdminActivityPage" />
                    页
                    <button class="ghost" @click="goAdminActivityPage">跳转</button>
                  </span>
                </div>
              </div>
            </template>
          </div>

          <div v-if="adminMenu === 'users'" class="data-panel">
            <div class="toolbar">
              <div class="search-box admin-search">
                <input v-model="adminUserKeyword" placeholder="按用户名 / 昵称搜索" @keyup.enter="searchAdminUsers" />
                <button @click="searchAdminUsers">查询</button>
              </div>
              <select v-model="adminUserRole" class="filter-select" @change="searchAdminUsers">
                <option value="">全部角色</option>
                <option value="USER">普通用户</option>
                <option value="ADMIN">管理员</option>
              </select>
              <select v-model="adminUserStatus" class="filter-select" @change="searchAdminUsers">
                <option value="">全部状态</option>
                <option :value="1">启用</option>
                <option :value="0">禁用</option>
              </select>
              <select v-model="adminUsers.size" class="filter-select" @change="changeAdminUserPageSize">
                <option :value="10">10 条/页</option>
                <option :value="20">20 条/页</option>
                <option :value="50">50 条/页</option>
              </select>
              <button class="ghost" @click="resetAdminUserSearch">重置</button>
              <span class="result-count">共 {{ adminUsers.total }} 个用户</span>
            </div>

            <div v-if="!adminUsers.items?.length" class="empty">没有匹配的用户</div>
            <div v-else class="table-wrap">
              <table class="admin-table">
                <thead>
                  <tr>
                    <th>用户名</th>
                    <th>昵称</th>
                    <th>手机号</th>
                    <th>角色</th>
                    <th>钱包余额</th>
                    <th>状态</th>
                    <th>注册时间</th>
                    <th class="col-action">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="user in adminUsers.items" :key="user.id">
                    <td><span class="cell-strong">{{ user.username }}</span></td>
                    <td>{{ user.nickname || '-' }}</td>
                    <td>{{ user.phone || '-' }}</td>
                    <td><span :class="['tag', user.role === 'ADMIN' ? 'warn' : 'muted']">{{ formatRole(user.role) }}</span></td>
                    <td>{{ money(user.balance) }}</td>
                    <td><span :class="['tag', user.status === 1 ? 'ok' : 'muted']">{{ user.status === 1 ? '启用' : '禁用' }}</span></td>
                    <td>{{ formatDate(user.createdAt) }}</td>
                    <td class="col-action">
                      <div class="row-actions">
                        <button class="ghost" @click="toggleUser(user)">{{ user.status === 1 ? '禁用' : '启用' }}</button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
              <div v-if="adminUsers.total > adminUsers.size" class="pagination">
                <button class="ghost" :disabled="adminUsers.page <= 1" @click="changeAdminUserPage(-1)">上一页</button>
                <span class="page-info">第 {{ adminUsers.page }} / {{ adminUserTotalPages }} 页</span>
                <button class="ghost" :disabled="adminUsers.page >= adminUserTotalPages" @click="changeAdminUserPage(1)">下一页</button>
                <span class="page-jump-wrap">跳至
                  <input type="number" min="1" :max="adminUserTotalPages" v-model.number="adminUserJumpPage" class="page-jump" @keyup.enter="goAdminUserPage" />
                  页
                  <button class="ghost" @click="goAdminUserPage">跳转</button>
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>
</template>

<script setup>
import { ref, reactive, computed, toRef, nextTick } from 'vue';
import { api } from '../api/client';
import { discountRate, discountSave, formatCouponStatus, formatDate, formatOrderStatus, formatPaymentStatus, formatProductStatus, formatRefundStatus, formatRole, formatUnit, initials, itemOriginalSave, money, orderStatusTag, refundStatusTag, resolveUnit } from '../utils/format';
import ImageUpload from './ImageUpload.vue';

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
const { adminChartProducts, adminCouponJumpPage, adminCouponKeyword, adminCoupons, adminJumpPage, adminMenu, adminOrderJumpPage, adminOrderKeyword, adminOrderStats, adminOrderStatus, adminOrders, adminProductKeyword, adminProductStatus, adminProducts, adminUserJumpPage, adminUserKeyword, adminUserRole, adminUserStatus, adminUsers, alertDialog, askConfirm, categoryName, confirmDialog, coupons, disposeCharts, error, fail, filters, loadAdminChartProducts, loadAdminCoupons, loadAdminOrderStats, loadAdminOrders, loadAdminProducts, loadAdminUsers, loadCategories, loadProducts, loadRefundOrders, loadStockAlerts, notice, openOrderDetail, orderChart, orderChartEl, orderChartOption, orderDetail, orders, productChart, productChartEl, productChartOption, productForm, products, refreshAdminData, refundJumpPage, refundOrders, refundStatusFilter, renderAdminCharts, run, safeParseSpec, session, showAlert, stockAlerts } = props.adminCtx;

const adminActivities = reactive({ items: [], page: 1, size: 10, total: 0 });

const adminActivityKeyword = ref('');

const adminActivityJumpPage = ref(1);

const adminActivityTotalPages = computed(() => Math.max(1, Math.ceil((adminActivities.total || 0) / (adminActivities.size || 10))));

const activityProducts = ref([]);

const activityProductsLoaded = ref(false);

const shipForm = reactive({ orderId: null, shipCompany: '', shipNo: '' });

const refundReviewForm = reactive({ orderId: null, approved: true, remark: '' });

const stockForm = reactive({ productId: null, quantity: 10, remark: '' });

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

const adminProductTotalPages = computed(() => Math.max(1, Math.ceil((adminProducts.total || 0) / (adminProducts.size || 10))));

const adminOrderTotalPages = computed(() => Math.max(1, Math.ceil((adminOrders.total || 0) / (adminOrders.size || 10))));

const refundTotalPages = computed(() => Math.max(1, Math.ceil((refundOrders.total || 0) / (refundOrders.size || 10))));

const adminUserTotalPages = computed(() => Math.max(1, Math.ceil((adminUsers.total || 0) / (adminUsers.size || 10))));

const adminCouponTotalPages = computed(() => Math.max(1, Math.ceil((adminCoupons.total || 0) / (adminCoupons.size || 10))));

const categoryKeyword = ref('');

const categoryPage = ref(1);

const categorySize = ref(10);

const categoryFiltered = computed(() => {
  const kw = categoryKeyword.value.trim().toLowerCase();
  const list = categories.value || [];
  if (!kw) return list;
  return list.filter((c) => (c.name || '').toLowerCase().includes(kw));
});

const categoryTotalPages = computed(() => Math.max(1, Math.ceil(categoryFiltered.value.length / (categorySize.value || 10))));

const categoryPageItems = computed(() => {
  const total = categoryTotalPages.value;
  const page = Math.min(categoryPage.value, total);
  const start = (page - 1) * categorySize.value;
  return categoryFiltered.value.slice(start, start + categorySize.value);
});

const stockKeyword = ref('');

const stockPage = ref(1);

const stockSize = ref(10);

const stockFiltered = computed(() => {
  const kw = stockKeyword.value.trim().toLowerCase();
  const list = stockAlerts.value || [];
  if (!kw) return list;
  return list.filter((a) => (a.name || '').toLowerCase().includes(kw) || (a.sku || '').toLowerCase().includes(kw));
});

const stockTotalPages = computed(() => Math.max(1, Math.ceil(stockFiltered.value.length / (stockSize.value || 10))));

const stockPageItems = computed(() => {
  const total = stockTotalPages.value;
  const page = Math.min(stockPage.value, total);
  const start = (page - 1) * stockSize.value;
  return stockFiltered.value.slice(start, start + stockSize.value);
});

const categoryJumpPage = ref(1);

const stockJumpPage = ref(1);

function changeCategoryPage(delta) {
  const next = categoryPage.value + delta;
  if (next < 1 || next > categoryTotalPages.value) return;
  categoryPage.value = next;
}

function goCategoryPage() {
  const p = Number(categoryJumpPage.value);
  if (!Number.isInteger(p) || p < 1 || p > categoryTotalPages.value) {
    categoryJumpPage.value = categoryPage.value;
    return;
  }
  categoryPage.value = p;
}

function changeStockPage(delta) {
  const next = stockPage.value + delta;
  if (next < 1 || next > stockTotalPages.value) return;
  stockPage.value = next;
}

function goStockPage() {
  const p = Number(stockJumpPage.value);
  if (!Number.isInteger(p) || p < 1 || p > stockTotalPages.value) {
    stockJumpPage.value = stockPage.value;
    return;
  }
  stockPage.value = p;
}

const formDiscount = computed(() => {
  const price = Number(productForm.price) || 0;
  const original = Number(productForm.originalPrice) || 0;
  const save = original > price ? original - price : 0;
  const rate = (original > price && price > 0) ? (price / original * 10).toFixed(1) : '';
  return { save, rate };
});

const categoryForm = reactive({ parentId: 0, name: '', sortNo: 10, status: 1 });

const couponForm = reactive({ name: '', thresholdAmount: 0, discountAmount: 0, totalCount: 0, startTime: '', endTime: '' });

const activityForm = reactive({ id: null, name: '', type: 'FULL_REDUCTION', scope: 'ALL', categoryId: 0, productId: 0, threshold: 0, discount: 0, startTime: '', endTime: '', priority: 0 });

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
};

const adminMenuItems = computed(() => [
  { key: 'dashboard', label: '数据统计', desc: '平台经营概览：商品、库存、订单与成交额', group: '经营' },
  { key: 'orders', label: '订单管理', desc: '查询订单、录入快递单号发货、完成或取消订单', group: '经营', badge: (adminOrders.total || 0) || '' },
  { key: 'refunds', label: '售后管理', desc: '审核用户的退款申请，同意后款项退回用户钱包', group: '经营', badge: (refundOrders.total || 0) || '', warn: true },
  { key: 'stock', label: '库存预警', desc: '低于预警阈值的商品列表，支持一键补货', group: '经营', badge: stockAlerts.value.length || '', warn: true },
  { key: 'products', label: '商品管理', desc: '新增商品、查看上架状态、手动入库', group: '管理', badge: (adminProducts.total || 0) || '' },
  { key: 'categories', label: '分类管理', desc: '维护商品分类与排序', group: '管理', badge: categories.value.length || '' },
  { key: 'coupons', label: '优惠券管理', desc: '创建满减券、发放与停用', group: '管理', badge: (adminCoupons.total || 0) || '' },
  { key: 'activities', label: '营销活动', desc: '创建满减/折扣活动，按全场、类目或商品精准投放', group: '管理', badge: (adminActivities.total || 0) || '' },
  { key: 'users', label: '用户管理', desc: '查看账号余额，启用或禁用账号', group: '管理', badge: (adminUsers.total || 0) || '' },
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

const dashboardStats = computed(() => {
  const productItems = adminProducts.items || [];
  const orderItems = adminOrders.items || [];
  const paidOrders = orderItems.filter((order) => ['PAID', 'SHIPPED', 'COMPLETED'].includes(order.status));
  return {
    productCount: productItems.length,
    totalStock: productItems.reduce((sum, product) => sum + Number(product.stock || 0), 0),
    orderCount: orderItems.length,
    userCount: (adminUsers.items || []).length,
    salesAmount: paidOrders.reduce((sum, order) => sum + Number(order.payAmount || 0), 0),
  };

});

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
  if (!(productForm.stock >= 0)) { fail('库存不能为负数'); return; }
  if (!productForm.categoryId) { fail('请选择所属分类'); return; }
  if (productForm.unit === 'custom' && !(productForm.customUnit || '').trim()) { fail('请填写自定义计价单位'); return; }
  const payload = {
    ...productForm,
    categoryId: Number(productForm.categoryId),
    coverUrl: productForm.coverUrl || '',
    originalPrice: productForm.originalPrice ? Number(productForm.originalPrice) : null,
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
  Object.assign(productForm, { categoryId: '', sku: '', name: '', subtitle: '', description: '', coverUrl: '', price: 0, originalPrice: '', stock: 0, unit: 'piece', customUnit: '', brand: '', isHot: false, isNew: false, tags: '', images: [], skus: [], attributes: [] });
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

function openStockForm(product, quantity = 10) {
  stockForm.productId = product.id;
  stockForm.quantity = quantity;
  stockForm.remark = '';

}

async function submitStock(product) {
  const quantity = Number(stockForm.quantity);
  if (!Number.isInteger(quantity) || quantity <= 0) {
    fail('入库数量必须是不小于 1 的整数');
    return;
  }
  const confirmed = await askConfirm({
    title: '确认入库',
    message: `将为「${product.name}」增加 ${quantity} 件库存，操作会立即生效。`,
    confirmText: '确认入库',
    details: [
      { label: '商品', value: `${product.name}（${product.sku || '-'}）` },
      { label: '当前库存', value: product.stock ?? 0 },
      { label: '入库后', value: Number(product.stock || 0) + quantity },
    ],
  });
  if (!confirmed) return;
  await run(async () => {
    await api.post(`/admin/products/${product.id}/stock-adjustments`, {
      changeQuantity: quantity,
      bizType: 'PURCHASE',
      remark: stockForm.remark.trim() || '后台手动入库',
    });
    stockForm.productId = null;
    await Promise.all([loadAdminProducts(), loadStockAlerts(), loadProducts()]);
  }, `已入库 ${quantity} 件`);

}

async function searchAdminOrders() {
  adminOrders.page = 1;
  await loadAdminOrders();
}

async function changeAdminOrderPage(delta) {
  const next = adminOrders.page + delta;
  if (next < 1 || next > adminOrderTotalPages.value) return;
  adminOrders.page = next;
  await loadAdminOrders();
}

async function changeAdminOrderPageSize() {
  adminOrders.page = 1;
  await loadAdminOrders();
}

async function goAdminOrderPage() {
  const p = Number(adminOrderJumpPage.value);
  if (!Number.isInteger(p) || p < 1 || p > adminOrderTotalPages.value) {
    adminOrderJumpPage.value = adminOrders.page;
    return;
  }
  adminOrders.page = p;
  await loadAdminOrders();
}

function resetAdminOrderSearch() {
  adminOrderKeyword.value = '';
  adminOrderStatus.value = '';
  adminOrders.page = 1;
  loadAdminOrders();
}

function openShipForm(orderId) {
  shipForm.orderId = orderId;
  shipForm.shipCompany = '';
  shipForm.shipNo = '';

}

async function submitShip(id) {
  if (!shipForm.shipCompany.trim() || !shipForm.shipNo.trim()) { fail('请填写快递公司和快递单号'); return; }
  const confirmed = await askConfirm({
    title: '确认发货',
    message: '发货后订单进入待收货状态，物流信息将展示给买家，请核对单号无误。',
    confirmText: '确认发货',
    details: [
      { label: '快递公司', value: shipForm.shipCompany.trim() },
      { label: '快递单号', value: shipForm.shipNo.trim() },
    ],
  });
  if (!confirmed) return;
  await run(async () => {
    await api.post(`/admin/orders/${id}/ship`, { shipCompany: shipForm.shipCompany.trim(), shipNo: shipForm.shipNo.trim() });
    shipForm.orderId = null;
    await Promise.all([loadAdminOrders(), loadRefundOrders()]);
  }, '订单已发货');

}

async function searchRefunds() {
  refundOrders.page = 1;
  await loadRefundOrders();
}

async function changeRefundPage(delta) {
  const next = refundOrders.page + delta;
  if (next < 1 || next > refundTotalPages.value) return;
  refundOrders.page = next;
  await loadRefundOrders();
}

async function changeRefundPageSize() {
  refundOrders.page = 1;
  await loadRefundOrders();
}

async function goRefundPage() {
  const p = Number(refundJumpPage.value);
  if (!Number.isInteger(p) || p < 1 || p > refundTotalPages.value) {
    refundJumpPage.value = refundOrders.page;
    return;
  }
  refundOrders.page = p;
  await loadRefundOrders();
}

function resetRefundSearch() {
  refundStatusFilter.value = 'APPLYING';
  refundOrders.page = 1;
  loadRefundOrders();
}

function reviewAdminRefund(orderId, approved) {
  refundReviewForm.orderId = orderId;
  refundReviewForm.approved = approved;
  refundReviewForm.remark = '';

}

async function submitRefundReview(id) {
  const approved = refundReviewForm.approved;
  const order = refundOrders.items.find((item) => item.id === id);
  const confirmed = await askConfirm({
    title: approved ? '同意退款' : '拒绝退款',
    message: approved
      ? '同意后款项将立即退回买家钱包，订单关闭并回滚库存，该操作不可撤销。'
      : '拒绝后买家可以重新提交申请，你也可以稍后再次处理。',
    confirmText: approved ? '确认退款' : '确认拒绝',
    danger: approved,
    details: order
      ? [
          { label: '订单号', value: order.orderNo },
          { label: '退款金额', value: money(order.payAmount) },
          { label: '退款原因', value: order.refundReason || '未填写' },
        ]
      : [],
  });
  if (!confirmed) return;
  await run(async () => {
    await api.post(`/admin/orders/${id}/refund-review`, {
      approved: refundReviewForm.approved,
      remark: refundReviewForm.remark.trim() || null,
    });
    refundReviewForm.orderId = null;
    await Promise.all([loadRefundOrders(), loadAdminOrders()]);
  }, refundReviewForm.approved ? '已同意退款，款项已退回用户钱包' : '已拒绝该退款申请');

}

async function searchAdminCoupons() {
  adminCoupons.page = 1;
  await loadAdminCoupons();
}

async function changeAdminCouponPage(delta) {
  const next = adminCoupons.page + delta;
  if (next < 1 || next > adminCouponTotalPages.value) return;
  adminCoupons.page = next;
  await loadAdminCoupons();
}

async function changeAdminCouponPageSize() {
  adminCoupons.page = 1;
  await loadAdminCoupons();
}

async function goAdminCouponPage() {
  const p = Number(adminCouponJumpPage.value);
  if (!Number.isInteger(p) || p < 1 || p > adminCouponTotalPages.value) {
    adminCouponJumpPage.value = adminCoupons.page;
    return;
  }
  adminCoupons.page = p;
  await loadAdminCoupons();
}

function resetAdminCouponSearch() {
  adminCouponKeyword.value = '';
  adminCoupons.page = 1;
  loadAdminCoupons();
}

function fillCouponPeriod(days) {
  const pad = (num) => String(num).padStart(2, '0');
  const toLocalInput = (date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  const start = new Date();
  const end = new Date(start.getTime() + days * 24 * 60 * 60 * 1000);
  couponForm.startTime = toLocalInput(start);
  couponForm.endTime = toLocalInput(end);

}

async function saveCoupon() {
  if (!couponForm.name.trim()) { fail('请填写优惠券名称'); return; }
  if (!(couponForm.discountAmount > 0)) { fail('优惠金额必须大于 0'); return; }
  if (couponForm.discountAmount > couponForm.thresholdAmount) { fail('优惠金额不能超过使用门槛'); return; }
  if (!couponForm.startTime || !couponForm.endTime) { fail('请选择有效起止时间'); return; }
  await run(async () => {
    await api.post('/admin/coupons', {
      name: couponForm.name.trim(),
      thresholdAmount: Number(couponForm.thresholdAmount),
      discountAmount: Number(couponForm.discountAmount),
      totalCount: Number(couponForm.totalCount || 0),
      startTime: couponForm.startTime,
      endTime: couponForm.endTime,
    });
    Object.assign(couponForm, { name: '', thresholdAmount: 0, discountAmount: 0, totalCount: 0, startTime: '', endTime: '' });
    await loadAdminCoupons();
  }, '优惠券已创建');

}

async function toggleCoupon(coupon) {
  const disabling = coupon.status === 1;
  const confirmed = await askConfirm({
    title: disabling ? '停用优惠券' : '启用优惠券',
    message: disabling
      ? '停用后用户无法再领取这张券，已领取的券仍可在有效期内使用。'
      : '启用后用户可以重新领取这张券。',
    confirmText: disabling ? '确认停用' : '确认启用',
    danger: disabling,
    details: [
      { label: '优惠券', value: coupon.name },
      { label: '优惠力度', value: `满 ${money(coupon.thresholdAmount)} 减 ${money(coupon.discountAmount)}` },
      { label: '已领取', value: `${coupon.receivedCount} 张` },
    ],
  });
  if (!confirmed) return;
  await run(() => api.patch(`/admin/coupons/${coupon.id}/status`, { status: disabling ? 0 : 1 }).then(loadAdminCoupons), disabling ? '优惠券已停用' : '优惠券已启用');

}

async function loadActivityProducts() {
  if (activityProductsLoaded.value) return;
  try {
    const data = await api.get('/products?page=1&size=500');
    activityProducts.value = (data && data.items) || [];
    activityProductsLoaded.value = true;
  } catch (e) {
    activityProducts.value = [];
  }
}

async function loadAdminActivities() {
  if (!isAdmin.value) return;
  const params = new URLSearchParams({
    page: String(adminActivities.page),
    size: String(adminActivities.size),
  });
  if (adminActivityKeyword.value.trim()) params.set('keyword', adminActivityKeyword.value.trim());
  const data = await api.get(`/admin/activities?${params}`);
  Object.assign(adminActivities, data);
  if (adminActivities.items.length === 0 && adminActivities.page > 1) {
    adminActivities.page -= 1;
    await loadAdminActivities();
    return;
  }
  adminActivityJumpPage.value = adminActivities.page;
}

async function searchAdminActivities() {
  adminActivities.page = 1;
  await loadAdminActivities();
}

async function changeAdminActivityPage(delta) {
  const next = adminActivities.page + delta;
  if (next < 1 || next > adminActivityTotalPages.value) return;
  adminActivities.page = next;
  await loadAdminActivities();
}

async function changeAdminActivityPageSize() {
  adminActivities.page = 1;
  await loadAdminActivities();
}

async function goAdminActivityPage() {
  const p = Number(adminActivityJumpPage.value);
  if (!Number.isInteger(p) || p < 1 || p > adminActivityTotalPages.value) {
    adminActivityJumpPage.value = adminActivities.page;
    return;
  }
  adminActivities.page = p;
  await loadAdminActivities();
}

function resetAdminActivitySearch() {
  adminActivityKeyword.value = '';
  adminActivities.page = 1;
  loadAdminActivities();
}

function resetActivityForm() {
  Object.assign(activityForm, { id: null, name: '', type: 'FULL_REDUCTION', scope: 'ALL', categoryId: 0, productId: 0, threshold: 0, discount: 0, startTime: '', endTime: '', priority: 0 });
}

function onActivityScopeChange() {
  activityForm.categoryId = 0;
  activityForm.productId = 0;
  if (activityForm.scope === 'PRODUCT') loadActivityProducts();
}

function fillActivityPeriod(days) {
  const pad = (num) => String(num).padStart(2, '0');
  const toLocalInput = (date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  const start = new Date();
  const end = new Date(start.getTime() + days * 24 * 60 * 60 * 1000);
  activityForm.startTime = toLocalInput(start);
  activityForm.endTime = toLocalInput(end);
}

function activityTypeLabel(type) {
  return type === 'DISCOUNT' ? '折扣' : '满减';
}

function activityScopeLabel(scope) {
  if (scope === 'CATEGORY') return '指定类目';
  if (scope === 'PRODUCT') return '指定商品';
  return '全场';
}

function activityDiscountLabel(act) {
  const threshold = Number(act.threshold || 0);
  if (act.type === 'DISCOUNT') {
    const rate = Number(act.discount || 0);
    const zhe = Math.round(rate * 100) / 10;
    const zheStr = Number.isInteger(zhe) ? String(zhe) : zhe.toFixed(1);
    return threshold > 0 ? `${zheStr}折（满${money(threshold)}）` : `${zheStr}折`;
  }
  return `满 ${money(threshold)} 减 ${money(act.discount)}`;
}

function editActivity(act) {
  const toLocal = (value) => {
    if (!value) return '';
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return '';
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  };
  Object.assign(activityForm, {
    id: act.id,
    name: act.name,
    type: act.type,
    scope: act.scope,
    categoryId: act.categoryId || 0,
    productId: act.productId || 0,
    threshold: Number(act.threshold || 0),
    discount: Number(act.discount || 0),
    startTime: toLocal(act.startTime),
    endTime: toLocal(act.endTime),
    priority: Number(act.priority || 0),
  });
  if (act.scope === 'PRODUCT') loadActivityProducts();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

async function saveActivity() {
  if (!activityForm.name.trim()) { fail('请填写活动名称'); return; }
  if (!activityForm.startTime || !activityForm.endTime) { fail('请选择有效起止时间'); return; }
  if (new Date(activityForm.startTime) >= new Date(activityForm.endTime)) { fail('结束时间必须晚于开始时间'); return; }
  if (activityForm.scope === 'CATEGORY' && !activityForm.categoryId) { fail('请选择适用类目'); return; }
  if (activityForm.scope === 'PRODUCT' && !activityForm.productId) { fail('请选择适用商品'); return; }
  if (activityForm.type === 'FULL_REDUCTION') {
    if (!(activityForm.threshold > 0)) { fail('满减门槛必须大于 0'); return; }
    if (!(activityForm.discount > 0)) { fail('优惠金额必须大于 0'); return; }
    if (Number(activityForm.discount) > Number(activityForm.threshold)) { fail('优惠金额不能超过门槛金额'); return; }
  } else {
    if (!(activityForm.discount > 0) || !(activityForm.discount < 1)) { fail('折扣率需在 0~1 之间，例如 0.9 表示 9 折'); return; }
  }
  const payload = {
    name: activityForm.name.trim(),
    type: activityForm.type,
    scope: activityForm.scope,
    categoryId: activityForm.scope === 'CATEGORY' ? Number(activityForm.categoryId) : null,
    productId: activityForm.scope === 'PRODUCT' ? Number(activityForm.productId) : null,
    threshold: Number(activityForm.threshold || 0),
    discount: Number(activityForm.discount),
    startTime: activityForm.startTime,
    endTime: activityForm.endTime,
    priority: Number(activityForm.priority || 0),
  };
  await run(async () => {
    if (activityForm.id) {
      await api.put(`/admin/activities/${activityForm.id}`, payload);
    } else {
      await api.post('/admin/activities', payload);
    }
    resetActivityForm();
    await loadAdminActivities();
  }, activityForm.id ? '活动已更新' : '活动已创建');
}

async function toggleActivity(act) {
  const disabling = act.status === 1;
  const confirmed = await askConfirm({
    title: disabling ? '停用活动' : '启用活动',
    message: disabling
      ? '停用后该活动不再参与订单优惠计算，已下单的订单不受影响。'
      : '启用后该活动将重新参与订单优惠计算。',
    confirmText: disabling ? '确认停用' : '确认启用',
    danger: disabling,
    details: [
      { label: '活动', value: act.name },
      { label: '优惠力度', value: activityDiscountLabel(act) },
    ],
  });
  if (!confirmed) return;
  await run(() => api.patch(`/admin/activities/${act.id}/status`, { status: disabling ? 0 : 1 }).then(loadAdminActivities), disabling ? '活动已停用' : '活动已启用');
}

async function deleteActivity(act) {
  const confirmed = await askConfirm({
    title: '删除活动',
    message: '删除后该活动立即失效且不可恢复，已下单的订单不受影响。',
    confirmText: '确认删除',
    danger: true,
    details: [
      { label: '活动', value: act.name },
      { label: '优惠力度', value: activityDiscountLabel(act) },
    ],
  });
  if (!confirmed) return;
  await run(() => api.delete(`/admin/activities/${act.id}`).then(loadAdminActivities), '活动已删除');
}

async function completeAdminOrder(id) {
  const order = (adminOrders.items || []).find((item) => item.id === id);
  const confirmed = await askConfirm({
    title: '确认完成订单',
    message: '完成后订单交易结束，买家可以对商品进行评价。',
    confirmText: '确认完成',
    details: order ? [{ label: '订单号', value: order.orderNo }, { label: '金额', value: money(order.payAmount) }] : [],
  });
  if (!confirmed) return;
  await run(() => api.post(`/admin/orders/${id}/complete`).then(loadAdminOrders), '订单已完成');

}

async function cancelAdminOrder(id) {
  const order = (adminOrders.items || []).find((item) => item.id === id);
  const paid = order?.paymentStatus === 'PAID';
  const confirmed = await askConfirm({
    title: '取消订单',
    message: paid
      ? '该订单已支付，取消后款项将退回买家钱包并回滚库存，操作不可撤销。'
      : '取消后订单关闭并释放占用的库存，操作不可撤销。',
    confirmText: '确认取消订单',
    danger: true,
    details: order
      ? [
          { label: '订单号', value: order.orderNo },
          { label: '订单金额', value: money(order.payAmount) },
          { label: '是否已付款', value: paid ? '已付款，将原路退回钱包' : '未付款' },
        ]
      : [],
  });
  if (!confirmed) return;
  await run(async () => {
    await api.post(`/admin/orders/${id}/cancel`);
    await Promise.all([loadAdminOrders(), loadAdminUsers()]);
  }, '后台订单已取消');

}

async function saveCategory() {
  if (!categoryForm.name.trim()) { fail('请填写分类名称'); return; }
  await run(async () => {
    await api.post('/admin/categories', categoryForm);
    Object.assign(categoryForm, { parentId: 0, name: '', sortNo: 10, status: 1 });
    await loadCategories();
  }, '分类已新增');

}

async function searchAdminUsers() {
  adminUsers.page = 1;
  await loadAdminUsers();
}

async function changeAdminUserPage(delta) {
  const next = adminUsers.page + delta;
  if (next < 1 || next > adminUserTotalPages.value) return;
  adminUsers.page = next;
  await loadAdminUsers();
}

async function changeAdminUserPageSize() {
  adminUsers.page = 1;
  await loadAdminUsers();
}

async function goAdminUserPage() {
  const p = Number(adminUserJumpPage.value);
  if (!Number.isInteger(p) || p < 1 || p > adminUserTotalPages.value) {
    adminUserJumpPage.value = adminUsers.page;
    return;
  }
  adminUsers.page = p;
  await loadAdminUsers();
}

function resetAdminUserSearch() {
  adminUserKeyword.value = '';
  adminUserRole.value = '';
  adminUserStatus.value = '';
  adminUsers.page = 1;
  loadAdminUsers();
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

async function toggleUser(user) {
  const disabling = user.status === 1;
  const confirmed = await askConfirm({
    title: disabling ? '禁用账号' : '启用账号',
    message: disabling
      ? '禁用后该账号无法登录，其钱包余额与历史订单不受影响。'
      : '启用后该账号可以重新登录。',
    confirmText: disabling ? '确认禁用' : '确认启用',
    danger: disabling,
    details: [
      { label: '账号', value: user.username },
      { label: '角色', value: formatRole(user.role) },
      { label: '钱包余额', value: money(user.balance) },
    ],
  });
  if (!confirmed) return;
  await run(() => api.patch(`/admin/users/${user.id}/status`, { status: disabling ? 0 : 1 }).then(loadAdminUsers), disabling ? '账号已禁用' : '账号已启用');

}

const adminMenuLoaders = {
  dashboard: () => refreshAdminData(),
  orders: () => loadAdminOrders(),
  refunds: () => loadRefundOrders(),
  stock: () => loadStockAlerts(),
  products: () => loadAdminProducts(),
  categories: () => loadCategories(),
  coupons: () => loadAdminCoupons(),
  activities: () => loadAdminActivities(),
  users: () => loadAdminUsers(),
};

async function selectAdminMenu(key) {
  if (adminMenu.value === key) {
    await refreshCurrentAdminMenu();
    return;
  }
  adminMenu.value = key;
  await refreshCurrentAdminMenu();

}

async function refreshCurrentAdminMenu() {
  const loader = adminMenuLoaders[adminMenu.value];
  if (loader) await loader();
  if (adminMenu.value === 'dashboard') await renderAdminCharts();

}
</script>
