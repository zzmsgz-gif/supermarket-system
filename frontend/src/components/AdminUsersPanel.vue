<script setup>
// 后台「用户管理」面板：从 AdminPanel.vue 整块搬过来的（模板 58 行）。
//
// adminCtx 给数据与动作；分页/表单/审核这些由父级 composable 装配产出，
// 不在 adminCtx 里，所以按 props 注入。
//
// ⚠️ 解构列表照抄父级：漏一个就是运行时 undefined，模板编译不报错、
//    要跑起来才在 console 报 xxx is not a function。改完跑 _check_panels.py。
import { money, formatRole, formatDate, initials } from '../utils/format';
import AdminSearchBox from './AdminSearchBox.vue';
import AdminPageSize from './AdminPageSize.vue';
import AdminPager from './AdminPager.vue';

const props = defineProps({
  adminCtx: { type: Object, required: true },
  searchAdminUsers: { type: Function, required: true },
  memberLevelName: { type: Function, required: true },
  changeAdminUserPage: { type: Function, required: true },
  changeAdminUserPageSize: { type: Function, required: true },
  goAdminUserPage: { type: Function, required: true },
  resetAdminUserSearch: { type: Function, required: true },
  toggleUser: { type: Function, required: true },
  adminUserTotalPages: { type: Number, required: true },
});
const { adminUsers, adminUserKeyword, adminUserRole, adminUserStatus, adminUserJumpPage, loadAdminUsers } = props.adminCtx;
const { searchAdminUsers, changeAdminUserPage, changeAdminUserPageSize, goAdminUserPage, resetAdminUserSearch, toggleUser, adminUserTotalPages } = props;
</script>

<template>
  <div class="data-panel">
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
</template>
