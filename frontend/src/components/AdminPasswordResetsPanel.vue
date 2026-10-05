<script setup>
// 后台「密码重置申请」面板：从 AdminPanel.vue 整块搬过来的。
//
// 依赖刻意走显式 props 而不是 adminCtx：这个面板只服务一个 tab，把真正用到的东西列全，
// 一眼就能看出「它为什么需要这些」。接 adminCtx 会让依赖变成隐式的。
import { formatDate } from '../utils/format';

const props = defineProps({
  changePasswordResetPage: { type: Function, required: true },
  changePasswordResetPageSize: { type: Function, required: true },
  confirmRejectPasswordReset: { type: Function, required: true },
  confirmResetPassword: { type: Function, required: true },
  copyTempPassword: { type: Function, required: true },
  passwordResetJumpPage: { type: Object, required: true },
  passwordResetResult: { type: Object, required: true },
  passwordResetStatus: { type: String, required: true },
  passwordResetStatusClass: { type: Function, required: true },
  passwordResetStatusLabel: { type: Function, required: true },
  passwordResetTotalPages: { type: Number, required: true },
  passwordResets: { type: Object, required: true },
  refreshCurrentAdminMenu: { type: Function, required: true },
});
const { changePasswordResetPage, changePasswordResetPageSize, confirmRejectPasswordReset, confirmResetPassword, copyTempPassword, passwordResetJumpPage, passwordResetResult, passwordResetStatus, passwordResetStatusClass, passwordResetStatusLabel, passwordResetTotalPages, passwordResets, refreshCurrentAdminMenu } = props;
</script>

<template>
  <div class="data-panel">
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
</template>
