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

          <div v-if="adminMenu === 'refunds'" class="data-panel">
            <div class="toolbar">
              <select v-model="refundStatusFilter" class="filter-select" @change="searchRefunds">
                <option value="APPLYING">申请中</option>
                <option value="APPROVED">已通过</option>
                <option value="REJECTED">已拒绝</option>
              </select>
              <AdminPageSize v-model="refundOrders.size" @change="changeRefundPageSize" />
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
                      <td><span class="cell-strong">{{ money(order.payAmount) }}</span><span v-if="orderSavedTotal(order) > 0" class="cell-sub">已优惠 {{ money(orderSavedTotal(order)) }}</span></td>
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
              <AdminPager :page="refundOrders.page" :total-pages="refundTotalPages" v-model:jump-page="refundJumpPage" @change="changeRefundPage" @jump="goRefundPage" />
            </div>
          </div>

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
                <AdminPager :page="categoryPage" :total-pages="categoryTotalPages" v-model:jump-page="categoryJumpPage" @change="changeCategoryPage" @jump="goCategoryPage" />
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
                <AdminSearchBox v-model="adminCouponKeyword" placeholder="按优惠券名称搜索" @search="searchAdminCoupons" />
                <AdminPageSize v-model="adminCoupons.size" @change="changeAdminCouponPageSize" />
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
                <AdminPager :page="adminCoupons.page" :total-pages="adminCouponTotalPages" v-model:jump-page="adminCouponJumpPage" @change="changeAdminCouponPage" @jump="goAdminCouponPage" />
              </div>
            </template>
          </div>

          <!-- ===== 经营看板：时间维度 + 环比 + 结构分析 ===== -->
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
                  <span class="field-label">秒杀商品<i class="req">*</i></span>
                  <select v-model.number="flashForm.productId">
                    <option v-for="p in flashProductOptions" :key="p.id" :value="p.id">
                      {{ p.name }}（售价 {{ money(p.price) }}）
                    </option>
                  </select>
                </label>
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
                  <td>{{ sale.productName }}</td>
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

          <div v-if="adminMenu === 'notices'" class="data-panel">
            <div class="toolbar">
              <button @click="openAnnouncementForm(null)">发布公告</button>
              <span v-if="adminAnnouncements.length" class="tag muted">共 {{ adminAnnouncements.length }} 条</span>
            </div>

            <div v-if="announcementFormOpen" class="form-card admin-form-card">
              <div class="form-title">
                <span>{{ announcementForm.id ? '编辑公告' : '发布公告' }}</span>
                <small>启用的公告展示在前台「商城公告」栏；<b>类型选「促销」的标题还会滚动出现在首页顶部利益条</b>（如新人福利文案），可在此随时改文改停用</small>
              </div>
              <div class="admin-form-grid">
                <label class="field span-all">
                  <span class="field-label">标题 <i class="req">*</i></span>
                  <input v-model="announcementForm.title" maxlength="120" placeholder="如：国庆期间配送时效调整" />
                </label>
                <label class="field">
                  <span class="field-label">类型</span>
                  <select v-model="announcementForm.type">
                    <option value="NOTICE">公告</option>
                    <option value="PROMOTION">促销</option>
                    <option value="ACTIVITY">活动</option>
                    <option value="SERVICE">服务</option>
                    <option value="WARNING">提醒</option>
                  </select>
                </label>
                <label class="field">
                  <span class="field-label">排序值</span>
                  <input v-model.number="announcementForm.sortOrder" type="number" min="0" placeholder="数字越小越靠前，如 10" />
                </label>
                <div class="field">
                  <span class="field-label">是否启用</span>
                  <label class="check-line"><input type="checkbox" v-model="announcementForm.enabled" /> 启用（前台可见）</label>
                </div>
              </div>
              <label class="field span-all">
                <span class="field-label">公告内容 <i class="req">*</i></span>
                <textarea v-model="announcementForm.content" rows="4" maxlength="500" placeholder="公告正文，最多 500 字"></textarea>
              </label>
              <div class="admin-form-foot">
                <button class="ghost" @click="closeAnnouncementForm">取消</button>
                <button @click="saveAnnouncement">保存</button>
              </div>
            </div>

            <template v-else>
              <div class="admin-cards" v-if="adminAnnouncements.length">
                <div v-for="a in adminAnnouncements" :key="a.id" class="admin-card">
                  <span :class="['tag', 'type-chip', noticeTypeClass(a.type)]">{{ noticeTypeLabel(a.type) }}</span>
                  <div class="card-info">
                    <p class="card-title"><span class="card-title-text">{{ a.title }}</span></p>
                    <p v-if="a.content" class="card-content">{{ a.content }}</p>
                    <p class="card-meta">
                      <span>发布 {{ formatDate(a.publishTime) }}</span>
                      <span>排序 {{ a.sortOrder }} · 越小越靠前</span>
                      <span :class="Number(a.enabled) === 1 ? 'on-word' : 'off-word'">{{ Number(a.enabled) === 1 ? '启用中' : '已停用' }}</span>
                    </p>
                  </div>
                  <div class="card-actions">
                    <button class="ghost" @click="openAnnouncementForm(a)">编辑</button>
                    <button class="ghost" @click="toggleAnnouncement(a)">{{ Number(a.enabled) === 1 ? '停用' : '启用' }}</button>
                    <button class="ghost danger" @click="deleteAnnouncement(a)">删除</button>
                  </div>
                </div>
              </div>
              <empty-state v-else icon="receipt" text="还没有公告，点上方「发布公告」发第一条" />
            </template>
          </div>

          <div v-if="adminMenu === 'hotSearches'" class="data-panel">
            <div class="toolbar">
              <button @click="openHotSearchForm(null)">新增热搜词</button>
              <span v-if="adminHotSearches.length" class="tag muted">共 {{ adminHotSearches.length }} 条</span>
            </div>

            <div v-if="hotSearchFormOpen" class="form-card admin-form-card">
              <div class="form-title">
                <span>{{ hotSearchForm.id ? '编辑热搜词' : '新增热搜词' }}</span>
                <small>这些词显示在首页头部搜索框下面的「热搜」那一排，<b>点击即按「搜索词」跳转搜索</b>；
                  「展示文案」留空就用搜索词本身（两者可以不同，例如显示「纯牛奶」而实际搜「牛奶」）</small>
              </div>
              <div class="admin-form-grid">
                <label class="field">
                  <span class="field-label">搜索词 <i class="req">*</i></span>
                  <input v-model="hotSearchForm.keyword" maxlength="30" placeholder="如：牛奶（点一下就是搜它）" />
                </label>
                <label class="field">
                  <span class="field-label">展示文案</span>
                  <input v-model="hotSearchForm.label" maxlength="30" placeholder="可留空；如显示「纯牛奶」" />
                </label>
                <label class="field">
                  <span class="field-label">排序值</span>
                  <input v-model.number="hotSearchForm.sortOrder" type="number" min="0" placeholder="数字越小越靠前，如 10" />
                </label>
                <div class="field">
                  <span class="field-label">是否启用</span>
                  <label class="check-line"><input type="checkbox" v-model="hotSearchForm.enabled" /> 启用（前台可见）</label>
                </div>
              </div>
              <div class="admin-form-foot">
                <button class="ghost" @click="closeHotSearchForm">取消</button>
                <button @click="saveHotSearch">保存</button>
              </div>
            </div>

            <template v-else>
              <div class="admin-cards" v-if="adminHotSearches.length">
                <div v-for="h in adminHotSearches" :key="h.id" class="admin-card">
                  <span class="tag">{{ h.label }}</span>
                  <div class="card-info">
                    <p class="card-title"><span class="card-title-text">点击后搜索「{{ h.keyword }}」</span></p>
                    <p class="card-meta">
                      <span>排序 {{ h.sortOrder }} · 越小越靠前</span>
                      <span :class="Number(h.enabled) === 1 ? 'on-word' : 'off-word'">{{ Number(h.enabled) === 1 ? '启用中' : '已停用' }}</span>
                    </p>
                  </div>
                  <div class="card-actions">
                    <button class="ghost" @click="openHotSearchForm(h)">编辑</button>
                    <button class="ghost" @click="toggleHotSearch(h)">{{ Number(h.enabled) === 1 ? '停用' : '启用' }}</button>
                    <button class="ghost danger" @click="deleteHotSearch(h)">删除</button>
                  </div>
                </div>
              </div>
              <empty-state v-else icon="star" text="还没有热搜词，点上方「新增热搜词」加一条（全部停用或删空时，前台那一排会整块隐藏）" />
            </template>
          </div>

          <div v-if="adminMenu === 'banners'" class="data-panel">
            <div class="toolbar">
              <button @click="openBannerForm(null)">新建轮播位</button>
              <span v-if="adminBanners.length" class="tag muted">共 {{ adminBanners.length }} 张</span>
            </div>

            <div v-if="bannerFormOpen" class="form-card admin-form-card">
              <div class="form-title">
                <span>{{ bannerForm.id ? '编辑轮播位' : '新建轮播位' }}</span>
                <small>建议 16:9 横图；宽超 1600px 或大 500KB 自动压缩，点击前台可跳转关联商品</small>
              </div>
              <div class="field">
                <span class="field-label">轮播图片 <i class="req">*</i></span>
                <ImageUpload v-model="bannerForm.imageUrl" :multiple="false" :max="1" type="banner" @upload-state="v => (bannerUploading = v)" />
              </div>
              <div class="admin-form-grid">
                <label class="field">
                  <span class="field-label">跳转商品（可选）</span>
                  <select v-model="bannerForm.linkProductId">
                    <option :value="null">不跳转，仅展示</option>
                    <option v-for="p in (adminProducts.items || [])" :key="p.id" :value="p.id">{{ p.name }}</option>
                  </select>
                </label>
                <label class="field">
                  <span class="field-label">排序值</span>
                  <input v-model.number="bannerForm.sortOrder" type="number" min="0" placeholder="数字越小越靠前，如 10" />
                </label>
                <p class="field-hint span-all" style="align-self: end;">是否展示用列表里的「停用 / 启用」控制；新建默认启用、排在最后。</p>
              </div>
              <div class="admin-form-foot">
                <button class="ghost" @click="closeBannerForm">取消</button>
                <button :disabled="bannerUploading" @click="saveBanner">{{ bannerUploading ? '图片上传中…' : '保存' }}</button>
              </div>
            </div>

            <template v-else>
              <div class="admin-cards" v-if="adminBanners.length">
                <div v-for="(b, bi) in adminBanners" :key="b.id" class="admin-card">
                  <span class="banner-pos" :title="'前台轮播第 ' + (bi + 1) + ' 张'">{{ bi + 1 }}</span>
                  <div class="banner-cover">
                    <img v-if="b.imageUrl" :src="b.imageUrl" alt="" />
                    <span v-else class="cover-empty">无图</span>
                  </div>
                  <div class="card-info">
                    <p class="card-title">
                      <span :class="['tag', Number(b.enabled) === 1 ? 'ok' : 'muted']">{{ Number(b.enabled) === 1 ? '启用中' : '已停用' }}</span>
                      <span class="card-title-text">{{ b.linkProductId ? (adminProductName(b.linkProductId) || ('跳转商品 #' + b.linkProductId)) : '仅展示，点击不跳转' }}</span>
                    </p>
                    <p class="card-meta">
                      <span>排序值 {{ b.sortOrder }} · 越小越靠前</span>
                      <span>前台轮播第 {{ bi + 1 }} 张</span>
                    </p>
                  </div>
                  <div class="card-actions">
                    <button class="ghost" @click="openBannerForm(b)">编辑</button>
                    <button class="ghost" @click="toggleBanner(b)">{{ Number(b.enabled) === 1 ? '停用' : '启用' }}</button>
                    <button class="ghost danger" @click="deleteBanner(b)">删除</button>
                  </div>
                </div>
              </div>
              <empty-state v-else icon="ticket" text="还没有轮播位：新建后前台轮播优先展示这里的内容" />
            </template>
          </div>

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

          <div v-if="adminMenu === 'users'" class="data-panel">
            <div class="toolbar">
              <AdminSearchBox v-model="adminUserKeyword" placeholder="按用户名 / 昵称搜索" @search="searchAdminUsers" />
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
              <AdminPageSize v-model="adminUsers.size" @change="changeAdminUserPageSize" />
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
                    <th>会员等级</th>
                    <th>积分</th>
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
                    <td><span :class="['tag', Number(user.memberLevel) > 0 ? 'warn' : 'muted']">{{ memberLevelName(user.memberLevel) }}</span></td>
                    <td>{{ user.points ?? 0 }}</td>
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
              <AdminPager :page="adminUsers.page" :total-pages="adminUserTotalPages" v-model:jump-page="adminUserJumpPage" @change="changeAdminUserPage" @jump="goAdminUserPage" />
            </div>
          </div>

          <!-- ===== 评价管理：看 / 回 / 藏 ===== -->
          <div v-if="adminMenu === 'reviews'" class="data-panel">
            <div v-if="adminReviewSummary" class="stat-grid">
              <div class="stat-card">
                <small>总评价</small>
                <strong>{{ adminReviewSummary.total }}</strong>
              </div>
              <div class="stat-card">
                <small>未回复</small>
                <strong :class="{ danger: adminReviewSummary.unrepliedCount > 0 }">{{ adminReviewSummary.unrepliedCount }}</strong>
                <span class="growth flat">待你回话</span>
              </div>
              <div class="stat-card">
                <small>平均分</small>
                <strong>{{ adminReviewSummary.avgRating == null ? '—' : adminReviewSummary.avgRating }}</strong>
                <span class="growth flat">含已隐藏</span>
              </div>
              <div class="stat-card">
                <small>已隐藏</small>
                <strong>{{ adminReviewSummary.hiddenCount }}</strong>
                <span class="growth flat">前台不展示</span>
              </div>
            </div>

            <div class="toolbar">
              <select v-model="adminReviewRating" @change="searchAdminReviews">
                <option value="">全部星级</option>
                <option v-for="n in [5, 4, 3, 2, 1]" :key="n" :value="String(n)">{{ n }} 星</option>
              </select>
              <select v-model="adminReviewReplied" @change="searchAdminReviews">
                <option value="">全部状态</option>
                <option value="no">未回复</option>
                <option value="yes">已回复</option>
              </select>
              <input v-model="adminReviewKeyword" placeholder="搜索评价内容" @keyup.enter="searchAdminReviews" />
              <button class="ghost" @click="searchAdminReviews">搜索</button>
              <button class="ghost" @click="adminReviewRating = ''; adminReviewReplied = ''; adminReviewKeyword = ''; searchAdminReviews()">重置</button>
              <span class="result-count">共 {{ adminReviews.total }} 条</span>
            </div>

            <empty-state v-if="!adminReviews.items.length" icon="ticket" text="没有符合条件的评价" />
            <div v-else class="review-admin-list">
              <div v-for="r in adminReviews.items" :key="r.id" class="review-admin-row">
                <div class="rar-head">
                  <span class="rar-stars">{{ '★'.repeat(r.rating || 0) }}{{ '☆'.repeat(5 - (r.rating || 0)) }}</span>
                  <b class="rar-product">{{ r.productName }}</b>
                  <span class="rar-user">{{ r.nickname || r.username || '匿名用户' }}</span>
                  <small>{{ formatDate(r.createdAt) }}</small>
                  <span class="rar-status" :class="r.hidden ? 'is-hidden' : (r.replyContent ? 'is-replied' : 'is-todo')">
                    {{ r.hidden ? '已隐藏' : (r.replyContent ? '已回复' : '未回复') }}
                  </span>
                </div>
                <p class="rar-content">{{ r.content || '（无文字评价，仅评分）' }}</p>
                <div v-if="r.imageUrls && r.imageUrls.length" class="rar-imgs">
                  <img v-for="(url, i) in r.imageUrls" :key="i" :src="url" alt="评价晒图" />
                </div>
                <div v-if="r.replyContent" class="rar-reply">
                  <b>商家回复</b><small v-if="r.replyAt">（{{ formatDate(r.replyAt) }}）</small>：{{ r.replyContent }}
                </div>
                <div class="rar-actions">
                  <input
                    v-model="reviewReplyDraft[r.id]"
                    maxlength="500"
                    :placeholder="r.replyContent ? '修改回复内容…' : '回复这条评价（会显示在商品详情页）…'"
                    @keyup.enter="saveReviewReply(r)"
                  />
                  <button @click="saveReviewReply(r)">{{ r.replyContent ? '更新回复' : '回复' }}</button>
                  <button v-if="r.replyContent" class="ghost" @click="reviewReplyDraft[r.id] = ''; saveReviewReply(r)">撤回</button>
                  <button class="ghost" @click="toggleReviewHidden(r)">{{ r.hidden ? '恢复展示' : '隐藏' }}</button>
                </div>
              </div>
            </div>

            <AdminPager :page="adminReviews.page" :total-pages="adminReviewTotalPages" @change="changeAdminReviewPage" />
          </div>

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
import { ref, reactive, computed, onMounted, toRef, nextTick, watch } from 'vue';
import { api } from '../api/client';
import { discountRate, discountSave, fulfillmentLabel, formatCouponStatus, formatDate, formatPaymentStatus, formatProductStatus, formatRefundStatus, formatRole, formatUnit, initials, itemOriginalSave, money, orderSavedTotal, orderStatusLabel, orderStatusTag, refundStatusTag, resolveUnit } from '../utils/format';
import ImageUpload from './ImageUpload.vue';
import AdminPager from './AdminPager.vue';
import AdminInsightsPanel from './AdminInsightsPanel.vue';
import AdminStockFormRow from './AdminStockFormRow.vue';
import AdminProductsPanel from './AdminProductsPanel.vue';
import AdminPageSize from './AdminPageSize.vue';
import AdminSearchBox from './AdminSearchBox.vue';

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
const MEMBER_LEVEL_NAMES = ['普通会员', '银卡会员', '金卡会员', '钻石会员'];
const memberLevelName = (level) => MEMBER_LEVEL_NAMES[Number(level) || 0] || '普通会员';

const adminActivities = reactive({ items: [], page: 1, size: 10, total: 0 });

const adminActivityKeyword = ref('');

const adminActivityJumpPage = ref(1);

const adminActivityTotalPages = computed(() => Math.max(1, Math.ceil((adminActivities.total || 0) / (adminActivities.size || 10))));

const activityProducts = ref([]);

const activityProductsLoaded = ref(false);

const shipForm = reactive({ orderId: null, shipCompany: '', shipNo: '' });

const refundReviewForm = reactive({ orderId: null, approved: true, remark: '' });

const stockForm = reactive({ productId: null, quantity: 10, remark: '' });



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

const categoryForm = reactive({ parentId: 0, name: '', sortNo: 10, status: 1 });

const couponForm = reactive({ name: '', thresholdAmount: 0, discountAmount: 0, totalCount: 0, startTime: '', endTime: '' });

const activityForm = reactive({ id: null, name: '', type: 'FULL_REDUCTION', scope: 'ALL', categoryId: 0, productId: 0, threshold: 0, discount: 0, startTime: '', endTime: '', priority: 0 });

const adminIcon = (paths) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`;

/* ---------------- 门店自提（后台） ---------------- */
const adminStores = ref([]);
const storeFormOpen = ref(false);
const storeEditingId = ref(null);
const storeForm = reactive({
  name: '', address: '', phone: '', businessHours: '', city: '', district: '', serviceAreas: '',
  pickupNotice: '', status: 1, sortNo: 0,
});

async function loadAdminStores() {
  if (!isAdmin.value) return;
  try {
    adminStores.value = (await api.get('/admin/stores')) || [];
  } catch (err) {
    fail(err?.message || '门店列表加载失败');
  }
}

function resetStoreForm() {
  storeEditingId.value = null;
  Object.assign(storeForm, {
    name: '', address: '', phone: '', businessHours: '', city: '', district: '', serviceAreas: '',
    pickupNotice: '', status: 1, sortNo: 0,
  });
}

function openStoreForm(store) {
  if (store) {
    storeEditingId.value = store.id;
    Object.assign(storeForm, {
      name: store.name || '',
      address: store.address || '',
      phone: store.phone || '',
      businessHours: store.businessHours || '',
      city: store.city || '',
      district: store.district || '',
      serviceAreas: store.serviceAreas || '',
      pickupNotice: store.pickupNotice || '',
      status: Number(store.status) === 0 ? 0 : 1,
      sortNo: Number(store.sortNo || 0),
    });
  } else {
    resetStoreForm();
  }
  storeFormOpen.value = true;
}

function closeStoreForm() {
  storeFormOpen.value = false;
  resetStoreForm();
}

function storePayload() {
  return {
    name: storeForm.name.trim(),
    address: storeForm.address.trim(),
    phone: storeForm.phone.trim(),
    businessHours: storeForm.businessHours.trim(),
    city: storeForm.city.trim(),
    district: storeForm.district.trim(),
    serviceAreas: storeForm.serviceAreas.trim(),
    pickupNotice: storeForm.pickupNotice.trim(),
    status: Number(storeForm.status) === 0 ? 0 : 1,
    sortNo: Number(storeForm.sortNo) || 0,
  };
}

async function saveStore() {
  if (!storeForm.name.trim()) { fail('请填写门店名称'); return; }
  if (!storeForm.address.trim()) { fail('请填写门店地址'); return; }
  const payload = storePayload();
  try {
    await run(async () => {
      if (storeEditingId.value) await api.put(`/admin/stores/${storeEditingId.value}`, payload);
      else await api.post('/admin/stores', payload);
      await loadAdminStores();
    }, storeEditingId.value ? '门店已更新' : '门店已新增');
    closeStoreForm();
  } catch (err) {
    // run() 已弹出错误提示；保持表单打开便于修正
  }
}

async function toggleStoreStatus(store) {
  const next = Number(store.status) === 1 ? 0 : 1;
  try {
    await run(async () => {
      await api.patch(`/admin/stores/${store.id}/status`, { status: next });
      await loadAdminStores();
    }, next === 1 ? `「${store.name}」已恢复营业` : `「${store.name}」已停业，前台不再可选`);
  } catch (err) {
    // 已在 run() 中提示
  }
}

async function deleteStore(store) {
  const confirmed = await askConfirm({
    title: '删除门店',
    message: `删除后「${store.name}」将从自提门店列表移除，已下单订单里的门店快照不受影响。`,
    confirmText: '确认删除',
    danger: true,
  });
  if (!confirmed) return;
  try {
    await run(async () => {
      await api.delete(`/admin/stores/${store.id}`);
      await loadAdminStores();
    }, '门店已删除');
  } catch (err) {
    // 已在 run() 中提示
  }
}

/* ===================== 限时秒杀管理 =====================
   状态与函数刻意放在本组件内（AdminPanel 已直接 import api），
   避免往 App.vue 那个超长 adminCtx 单行对象里塞键（改起来容易静默失败）。 */
const adminFlashSales = ref([]);
const flashFormOpen = ref(false);
const flashEditingId = ref(null);
const flashProductOptions = ref([]);
const flashForm = reactive({
  productId: 0, name: '', flashPrice: 0, totalQuota: 30, perUserLimit: 0,
  startTime: '', endTime: '', status: 1, sortNo: 0,
});

async function loadAdminFlashSales() {
  if (!isAdmin.value) return;
  try {
    adminFlashSales.value = (await api.get('/admin/flash-sales')) || [];
  } catch (err) {
    fail(err?.message || '秒杀场次加载失败');
  }
}

// 秒杀商品候选：走后台商品接口（含已下架商品，便于提前排期）
async function loadFlashProductOptions() {
  try {
    const page = await api.get('/admin/products?page=1&size=100');
    flashProductOptions.value = (page && page.items) || [];
  } catch (err) {
    flashProductOptions.value = [];
  }
}

function flashStateLabel(sale) {
  if (sale.state === 'RUNNING') return '进行中';
  if (sale.state === 'UPCOMING') return '未开始';
  return '已结束';
}

function flashStateClass(sale) {
  if (sale.state === 'RUNNING') return 'tag ok';
  if (sale.state === 'UPCOMING') return 'tag amber';
  return 'tag muted';
}

// 把后端返回的 ISO 时间转成 <input type="datetime-local"> 需要的 yyyy-MM-ddTHH:mm
function toDateTimeInput(value) {
  if (!value) return '';
  const text = String(value);
  return text.length >= 16 ? text.slice(0, 16) : text;
}

function openFlashForm(sale) {
  if (!flashProductOptions.value.length) loadFlashProductOptions();
  flashEditingId.value = sale ? sale.id : null;
  Object.assign(flashForm, sale
    ? {
        productId: Number(sale.productId),
        name: sale.name || '',
        flashPrice: Number(sale.flashPrice),
        totalQuota: Number(sale.totalQuota),
        perUserLimit: Number(sale.perUserLimit || 0),
        startTime: toDateTimeInput(sale.startTime),
        endTime: toDateTimeInput(sale.endTime),
        status: Number(sale.status),
        sortNo: Number(sale.sortNo || 0),
      }
    : {
        productId: flashProductOptions.value.length ? Number(flashProductOptions.value[0].id) : 0,
        name: '', flashPrice: 0, totalQuota: 30, perUserLimit: 0,
        startTime: '', endTime: '', status: 1, sortNo: 0,
      });
  flashFormOpen.value = true;
}

function closeFlashForm() {
  flashFormOpen.value = false;
  flashEditingId.value = null;
}

async function saveFlashSale() {
  if (!flashForm.productId) { fail('请选择秒杀商品'); return; }
  if (!(Number(flashForm.flashPrice) > 0)) { fail('请填写大于 0 的秒杀价'); return; }
  if (!(Number(flashForm.totalQuota) >= 1)) { fail('秒杀名额至少为 1'); return; }
  if (!flashForm.startTime || !flashForm.endTime) { fail('请选择开始与结束时间'); return; }
  if (flashForm.startTime >= flashForm.endTime) { fail('开始时间必须早于结束时间'); return; }
  const payload = {
    productId: Number(flashForm.productId),
    name: flashForm.name.trim() || null,
    flashPrice: Number(flashForm.flashPrice),
    totalQuota: Number(flashForm.totalQuota),
    perUserLimit: Number(flashForm.perUserLimit || 0),
    startTime: flashForm.startTime,
    endTime: flashForm.endTime,
    status: Number(flashForm.status),
    sortNo: Number(flashForm.sortNo || 0),
  };
  try {
    await run(async () => {
      if (flashEditingId.value) await api.put(`/admin/flash-sales/${flashEditingId.value}`, payload);
      else await api.post('/admin/flash-sales', payload);
      await loadAdminFlashSales();
    }, flashEditingId.value ? '秒杀场次已更新' : '秒杀场次已创建');
    closeFlashForm();
  } catch (err) {
    // run() 已提示错误（如"秒杀价必须低于商品售价"/"该商品已有未结束的场次"），保持表单打开
  }
}

async function toggleFlashStatus(sale) {
  const next = Number(sale.status) === 1 ? 0 : 1;
  try {
    await run(async () => {
      await api.patch(`/admin/flash-sales/${sale.id}/status`, { status: next });
      await loadAdminFlashSales();
    }, next === 1 ? `「${sale.name}」已启用` : `「${sale.name}」已停用，前台不再展示`);
  } catch (err) {
    // 已在 run() 中提示
  }
}

async function deleteFlashSale(sale) {
  const confirmed = await askConfirm({
    title: '删除秒杀场次',
    message: `删除后「${sale.name}」将从前台秒杀区移除。已下单订单里的秒杀价与名额快照不受影响。`,
    confirmText: '确认删除',
    danger: true,
  });
  if (!confirmed) return;
  try {
    await run(async () => {
      await api.delete(`/admin/flash-sales/${sale.id}`);
      await loadAdminFlashSales();
    }, '秒杀场次已删除');
  } catch (err) {
    // 已在 run() 中提示
  }
}


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

// 仪表盘 KPI：全部来自后端 /admin/stats/overview 全量聚合口径（旧实现用分页第一页凑数，订单/商品一多就不准）

function noticeTypeLabel(type) {
  if (type === 'ACTIVITY') return '活动';
  if (type === 'PROMOTION') return '促销';
  if (type === 'SERVICE') return '服务';
  if (type === 'WARNING') return '提醒';
  return '公告';
}

// 公告类型的配色（复用 .tag 变体：公告=蓝 / 活动=绿 / 服务=灰 / 提醒=红）
function noticeTypeClass(type) {
  if (type === 'ACTIVITY') return 'ok';
  if (type === 'WARNING') return 'warn';
  if (type === 'SERVICE') return 'muted';
  return 'info';
}

// 轮播卡上显示的跳转商品名（后台商品列表是分页的，不在当前页时回退成 #id）
function adminProductName(id) {
  if (!id) return '';
  const hit = (adminProducts.items || []).find((p) => String(p.id) === String(id));
  return hit ? hit.name : '';
}

// 近 7 天有任一成交才画趋势图，否则显示空状态（全 0 贴地直线很丑）
const trendHasData = computed(() =>
  ((adminStatsOverview.value && adminStatsOverview.value.salesTrend) || [])
    .some((p) => Number(p.orderCount || 0) > 0 || Number(p.salesAmount || 0) > 0));


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

// 门店自提「备货完成」：自提单没有物流，所以不弹快递单号表单，确认后直接推进到「待取货」。
// 这也是修掉「自提单被迫瞎填一个快递单号」的关键一步。
async function adminOrderReady(order) {
  const confirmed = await askConfirm({
    title: '标记备货完成',
    message: `「${order.orderNo}」是门店自提订单，标记后会给用户推送「凭自提码到店取货」的提醒，用户即可到店取货并在订单页确认。`,
    details: [
      { label: '自提门店', value: order.pickupStoreName || '-' },
      { label: '自提码', value: order.pickupCode || '-' },
    ],
    confirmText: '确认备货完成',
  });
  if (!confirmed) return;
  await run(async () => {
    await api.post(`/admin/orders/${order.id}/ready`, {});
    await Promise.all([loadAdminOrders(), loadRefundOrders()]);
  }, '已标记备货完成，等待用户到店取货');

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
          { label: '商品小计', value: money(order.totalAmount) },
          ...(orderSavedTotal(order) > 0 ? [{ label: '已优惠', value: `- ${money(orderSavedTotal(order))}` }] : []),
          { label: '退款金额（实付）', value: money(order.payAmount) },
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
  if (type === 'DISCOUNT') return '折扣';
  if (type === 'PROMOTION') return '促销文案';
  return '满减';
}

function activityScopeLabel(scope) {
  if (scope === 'CATEGORY') return '指定类目';
  if (scope === 'PRODUCT') return '指定商品';
  return '全场';
}

function activityDiscountLabel(act) {
  // 纯文案活动：只占首页顶栏一个展示位，没有门槛/优惠值
  if (act.type === 'PROMOTION') return '首页顶栏文案（不参与计价）';
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
  const promotion = activityForm.type === 'PROMOTION';
  if (!activityForm.name.trim()) { fail('请填写活动名称'); return; }
  if (!activityForm.startTime || !activityForm.endTime) { fail('请选择有效起止时间'); return; }
  if (new Date(activityForm.startTime) >= new Date(activityForm.endTime)) { fail('结束时间必须晚于开始时间'); return; }
  if (!promotion && activityForm.scope === 'CATEGORY' && !activityForm.categoryId) { fail('请选择适用类目'); return; }
  if (!promotion && activityForm.scope === 'PRODUCT' && !activityForm.productId) { fail('请选择适用商品'); return; }
  // 纯文案活动没有门槛/优惠值（不参与计价），跳过这些校验
  if (!promotion) {
    if (activityForm.type === 'FULL_REDUCTION') {
      if (!(activityForm.threshold > 0)) { fail('满减门槛必须大于 0'); return; }
      if (!(activityForm.discount > 0)) { fail('优惠金额必须大于 0'); return; }
      if (Number(activityForm.discount) > Number(activityForm.threshold)) { fail('优惠金额不能超过门槛金额'); return; }
    } else {
      if (!(activityForm.discount > 0) || !(activityForm.discount < 1)) { fail('折扣率需在 0~1 之间，例如 0.9 表示 9 折'); return; }
    }
  }
  const payload = {
    name: activityForm.name.trim(),
    type: activityForm.type,
    scope: promotion ? 'ALL' : activityForm.scope,
    categoryId: !promotion && activityForm.scope === 'CATEGORY' ? Number(activityForm.categoryId) : null,
    productId: !promotion && activityForm.scope === 'PRODUCT' ? Number(activityForm.productId) : null,
    threshold: promotion ? null : Number(activityForm.threshold || 0),
    discount: promotion ? null : Number(activityForm.discount),
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
          { label: '商品小计', value: money(order.totalAmount) },
          ...(orderSavedTotal(order) > 0 ? [{ label: '已优惠', value: `- ${money(orderSavedTotal(order))}` }] : []),
          { label: '订单金额（实付）', value: money(order.payAmount) },
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

/* ===== 找回密码申请（忘记密码的人工处理入口） =====
   状态全部本地化在 AdminPanel 内，不去动 App.vue 里那行超长的 adminCtx。 */
const passwordResets = reactive({ items: [], page: 1, size: 10, total: 0, pending: 0 });
const passwordResetStatus = ref('PENDING');
const passwordResetJumpPage = ref(1);
// 重置成功后拿到的临时密码：只存在于这一次响应里，关闭即销毁（库里只有 BCrypt 哈希）
const passwordResetResult = ref(null);

const passwordResetTotalPages = computed(() => Math.max(1, Math.ceil((passwordResets.total || 0) / (passwordResets.size || 10))));

const PASSWORD_RESET_STATUS_LABELS = { PENDING: '待处理', DONE: '已重置', REJECTED: '已驳回' };

function passwordResetStatusLabel(status) {
  return PASSWORD_RESET_STATUS_LABELS[status] || status || '-';
}

function passwordResetStatusClass(status) {
  if (status === 'PENDING') return 'warn';
  if (status === 'DONE') return 'ok';
  return 'muted';
}

async function loadPasswordResets() {
  const query = [`page=${passwordResets.page}`, `size=${passwordResets.size}`];
  if (passwordResetStatus.value) query.push(`status=${passwordResetStatus.value}`);
  const data = await api.get(`/admin/password-reset-requests?${query.join('&')}`);
  passwordResets.items = data?.items || [];
  passwordResets.total = Number(data?.total || 0);
  await loadPasswordResetPendingCount();
}

// 侧边菜单角标：待处理数量（拉失败不影响列表本身）
async function loadPasswordResetPendingCount() {
  try {
    const count = await api.get('/admin/password-reset-requests/pending-count');
    passwordResets.pending = Number(count || 0);
  } catch (err) {
    passwordResets.pending = 0;
  }
}

// 进后台就拉一次待处理数：否则菜单角标要等点进「找回密码」才显示，等于没提醒
// 侧边菜单角标：未回复评价数（"有人等你回话"）。拉失败就不显示角标，不打扰页面。
async function loadAdminReviewUnreplied() {
  try {
    adminReviewSummary.value = await api.get('/admin/reviews/summary');
  } catch (err) {
    adminReviewSummary.value = null;
  }
}

onMounted(() => {
  loadPasswordResetPendingCount();
  loadAdminReviewUnreplied();
  // 当前模块数据补一次：默认「经营看板」此前没有任何地方会触发 dashboard 接口（只有面板里的手动刷新按钮），
  // 深链 /admin?tab=notices 同理。挂载时兜住，保证「进来就有数据」。
  refreshCurrentAdminMenu();
});

async function searchPasswordResets() {
  passwordResets.page = 1;
  passwordResetJumpPage.value = 1;
  await run(() => loadPasswordResets());
}

async function changePasswordResetPage(delta) {
  const next = passwordResets.page + delta;
  if (next < 1 || next > passwordResetTotalPages.value) return;
  passwordResets.page = next;
  await run(() => loadPasswordResets());
}

async function changePasswordResetPageSize() {
  passwordResets.page = 1;
  await run(() => loadPasswordResets());
}

async function goPasswordResetPage() {
  const p = Number(passwordResetJumpPage.value);
  if (!Number.isInteger(p) || p < 1 || p > passwordResetTotalPages.value) {
    passwordResetJumpPage.value = passwordResets.page;
    return;
  }
  passwordResets.page = p;
  await run(() => loadPasswordResets());
}

async function confirmResetPassword(item) {
  const confirmed = await askConfirm({
    title: '重置该账号密码',
    message: `将为「${item.username}」生成一次性临时密码，并把该账号标记为「首次登录必须改密」。`
      + '临时密码只显示这一次，请当场电话告知用户，不要截图外发。',
    details: [
      { label: '账号', value: item.username },
      { label: '昵称', value: item.nickname || '-' },
      { label: '账号预留手机号', value: item.phone || '未填写' },
      { label: '申请人联系方式', value: item.contact || '未填写' },
    ],
    confirmText: '确认重置',
  });
  if (!confirmed) return;
  try {
    await run(async () => {
      passwordResetResult.value = await api.post(`/admin/password-reset-requests/${item.id}/reset`, {});
      await loadPasswordResets();
    }, '临时密码已生成，请立即转告用户');
  } catch (err) {
    fail(err?.message || '重置失败，请稍后重试');
  }
}

async function confirmRejectPasswordReset(item) {
  const confirmed = await askConfirm({
    title: '驳回找回申请',
    message: `确定驳回「${item.username}」的找回密码申请吗？驳回后用户可重新提交。`,
    confirmText: '确认驳回',
    danger: true,
  });
  if (!confirmed) return;
  try {
    await run(async () => {
      await api.post(`/admin/password-reset-requests/${item.id}/reject`, { remark: '身份核对未通过' });
      await loadPasswordResets();
    }, '申请已驳回');
  } catch (err) {
    fail(err?.message || '驳回失败，请稍后重试');
  }
}

async function copyTempPassword() {
  const text = passwordResetResult.value?.tempPassword;
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    notice.value = '临时密码已复制到剪贴板';
  } catch (err) {
    fail('复制失败，请手动选中复制');
  }
}

// ===== 评价管理 =====
// 补的是一条断掉的闭环：此前评价只能写（前台晒图评价），后台没有任何入口、也没有查询接口，
// 评价只在商品详情页出现 —— 商家看不到、回不了差评，等于用户说了话没人接。
// 只做三件商家真会做的事：看（含按星级/未回复筛选）、回（公开回复）、藏（违规隐藏）。
const adminReviews = reactive({ items: [], total: 0, page: 1, size: 10 });
const adminReviewSummary = ref(null);
const adminReviewRating = ref('');
const adminReviewReplied = ref('');
const adminReviewKeyword = ref('');
const reviewReplyDraft = reactive({});

const adminReviewTotalPages = computed(
  () => Math.max(1, Math.ceil(Number(adminReviews.total || 0) / Number(adminReviews.size || 10)))
);

async function loadAdminReviews() {
  const query = [`page=${adminReviews.page}`, `size=${adminReviews.size}`];
  if (adminReviewRating.value) query.push(`rating=${adminReviewRating.value}`);
  // 只有"未回复"才是待办，所以筛选值直接映射成后端的 replied 布尔
  if (adminReviewReplied.value) query.push(`replied=${adminReviewReplied.value === 'yes'}`);
  if (adminReviewKeyword.value.trim()) {
    query.push(`keyword=${encodeURIComponent(adminReviewKeyword.value.trim())}`);
  }
  const [data, summary] = await Promise.all([
    api.get(`/admin/reviews?${query.join('&')}`),
    api.get('/admin/reviews/summary'),
  ]);
  adminReviews.items = data?.items || [];
  adminReviews.total = Number(data?.total || 0);
  adminReviewSummary.value = summary || null;
  // 草稿用服务端已存的回复回填，便于"看现状再改"，而不是每次都从空开始
  adminReviews.items.forEach((row) => { reviewReplyDraft[row.id] = row.replyContent || ''; });
}

function searchAdminReviews() {
  adminReviews.page = 1;
  run(loadAdminReviews);
}

function changeAdminReviewPage(delta) {
  const next = Number(adminReviews.page) + delta;
  if (next < 1 || next > adminReviewTotalPages.value) return;
  adminReviews.page = next;
  run(loadAdminReviews);
}

async function saveReviewReply(review) {
  const content = (reviewReplyDraft[review.id] || '').trim();
  await run(async () => {
    await api.post(`/admin/reviews/${review.id}/reply`, { replyContent: content });
    await loadAdminReviews();
    showAlert({
      type: 'success',
      title: content ? '回复已发布' : '已撤回回复',
      message: content ? '该回复会立刻显示在商品详情页。' : '前台不再展示这条回复。',
    });
  });
}

async function toggleReviewHidden(review) {
  const hide = !review.hidden;
  const ok = await askConfirm({
    title: hide ? '隐藏这条评价？' : '恢复展示这条评价？',
    message: hide
      ? '隐藏后前台不再展示，但该订单仍算已评价（不会让用户重复评价）。'
      : '恢复后该评价会重新出现在商品详情页。',
    confirmText: hide ? '隐藏' : '恢复展示',
    danger: hide,
    details: [{ label: '商品', value: review.productName }, { label: '评价', value: review.content || '（无文字）' }],
  });
  if (!ok) return;
  await run(async () => {
    await api.post(`/admin/reviews/${review.id}/hidden`, { hidden: hide });
    await loadAdminReviews();
  });
}

const insightsPanelRef = ref(null);
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
