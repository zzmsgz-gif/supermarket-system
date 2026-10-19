<script setup>
import { toRefs, ref, reactive } from 'vue';
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
  createAdmin: { type: Function, required: true },
  adminUserTotalPages: { type: Number, required: true },
});

// ⚠️ 下面这几个用 defineModel 而不是 props —— 它们被 v-model 双向绑定，
// 而 props 只读，面板里赋值会**静默失败**：搜索/翻页看着能点但不动，
//    页面完全正常、控制台也没有报错。共用件（AdminSearchBox / AdminPageSize /
//    AdminPager）本身不用改，它们本来就正确 emit('update:*')。
const adminUserJumpPage = defineModel('adminUserJumpPage', { type: Number, required: true });
const adminUserKeyword = defineModel('adminUserKeyword', { type: String, required: true });
const adminUserStatus = defineModel('adminUserStatus', { type: String, required: true });
const adminUserRole = defineModel('adminUserRole', { type: String, required: true });
const adminUserMemberLevel = defineModel('adminUserMemberLevel', { type: String, required: true });
const { adminUsers, loadAdminUsers } = props.adminCtx;
const { searchAdminUsers, changeAdminUserPage, changeAdminUserPageSize, goAdminUserPage, resetAdminUserSearch, toggleUser, createAdmin, adminUserTotalPages } = toRefs(props);

// 新增管理员：内联表单（不引入额外弹窗依赖）。createAdmin 内部已用 run 处理错误提示，
// 成功返回 true 才收表单；用户名/密码长度由按钮 disabled 前置拦截，后端也有 @Size 兜底。
const newAdminOpen = ref(false);
const newAdmin = reactive({ username: '', password: '', nickname: '' });
const MEMBER_LEVEL_OPTIONS = ['普通用户', '银卡会员', '金卡会员', '钻石会员', '紫钻会员', '黑卡会员', '至尊会员'];
function openNewAdmin() {
  newAdmin.username = '';
  newAdmin.password = '';
  newAdmin.nickname = '';
  newAdminOpen.value = true;
}
async function submitNewAdmin() {
  const ok = await createAdmin.value({
    username: newAdmin.username, password: newAdmin.password, nickname: newAdmin.nickname,
  });
  if (ok) newAdminOpen.value = false;
}

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
      <select v-model="adminUserMemberLevel" class="filter-select" @change="searchAdminUsers">
        <option value="">全部等级</option>
        <option v-for="(name, i) in MEMBER_LEVEL_OPTIONS" :key="i" :value="String(i)">{{ name }}</option>
      </select>
      <AdminPageSize v-model="adminUsers.size" @change="changeAdminUserPageSize" />
      <button class="ghost" @click="resetAdminUserSearch">重置</button>
      <button class="primary sm" @click="openNewAdmin">+ 新增管理员</button>
      <span class="result-count">共 {{ adminUsers.total }} 个用户</span>
    </div>

    <div v-if="newAdminOpen" class="new-admin-form">
      <h4>新增管理员</h4>
      <!-- 用户名只做登录标识（字母/数字/下划线、3-20 位）；昵称按视觉宽度 ≤12 列（中文6字/英文12字母） -->
      <input v-model.trim="newAdmin.username" placeholder="用户名（字母/数字/下划线，3-20 位）" maxlength="20" />
      <input v-model.trim="newAdmin.password" type="password" placeholder="密码（至少 6 位）" maxlength="50" />
      <input v-model.trim="newAdmin.nickname" placeholder="昵称（选填，中文 ≤6 字 / 英文 ≤12 字母）" maxlength="12" />
      <button class="primary sm" :disabled="newAdmin.username.length < 3 || newAdmin.password.length < 6" @click="submitNewAdmin">创建</button>
      <button class="ghost sm" @click="newAdminOpen = false">取消</button>
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

<style scoped>
.new-admin-form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 12px 14px;
  margin: 0 0 12px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 10px;
  background: var(--chip-bg, #f6f7f9);
}
.new-admin-form h4 { margin: 0 6px 0 0; font-size: 14px; font-weight: 500; }
.new-admin-form input { flex: 1 1 150px; min-width: 120px; }
</style>
