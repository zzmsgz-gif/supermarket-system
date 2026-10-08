<template>
  <section class="data-panel profile-page">
    <div class="panel-head">
      <h3>个人资料</h3>
      <small>昵称、性别、生日、邮箱可直接改；手机号换绑需要验证新号</small>
    </div>

    <!-- 头像：单击放大（与导航栏一致），改头像走上传 -->
    <div class="pf-avatar-row">
      <span class="pf-avatar">
        <img v-if="form.avatarUrl" :src="form.avatarUrl" alt="头像" />
        <span v-else class="avatar-default">{{ initial }}</span>
      </span>
      <div class="pf-avatar-ops">
        <button class="ghost sm" type="button" @click="pickAvatar">更换头像</button>
        <small>支持 jpg / png，建议方形</small>
      </div>
    </div>

    <form class="pf-form" @submit.prevent="save">
      <label class="pf-row">
        <span>昵称</span>
        <input v-model.trim="form.nickname" maxlength="40" placeholder="给自己起个名字" />
      </label>

      <label class="pf-row">
        <span>性别</span>
        <select v-model="form.gender">
          <option value="">未设置</option>
          <option value="MALE">男</option>
          <option value="FEMALE">女</option>
          <option value="SECRET">保密</option>
        </select>
      </label>

      <label class="pf-row">
        <span>生日</span>
        <input v-model="form.birthday" type="date" :max="today" />
      </label>

      <label class="pf-row">
        <span>邮箱</span>
        <input v-model.trim="form.email" type="email" placeholder="用于接收通知" />
      </label>

      <!-- 手机号：换绑要验证新号（第 9 条的关键安全点） -->
      <div class="pf-row">
        <span>手机号</span>
        <div class="pf-phone">
          <input v-model.trim="form.phone" type="tel" placeholder="11 位手机号" />
          <template v-if="phoneChanged">
            <button class="ghost sm" type="button" :disabled="sending" @click="sendVerify">
              {{ sending ? '发送中…' : '获取验证码' }}
            </button>
            <input v-model.trim="verifyCode" class="pf-code" placeholder="验证码" maxlength="6" />
            <button class="ghost sm" type="button" :disabled="!verifyCode" @click="confirmVerify">
              验证
            </button>
          </template>
        </div>
        <small v-if="phoneChanged" class="pf-hint">
          换绑手机号需要先验证新号{{ verifyToken ? '，已验证通过' : '' }}
          <template v-if="devCode">（联调模式验证码：{{ devCode }}）</template>
        </small>
        <small v-else class="pf-hint">当前手机号</small>
      </div>

      <small v-if="pfError" class="pf-error">{{ pfError }}</small>
      <div class="pf-actions">
        <button class="primary" type="submit" :disabled="saving">
          {{ saving ? '保存中…' : '保存资料' }}
        </button>
      </div>
    </form>

    <!-- ===== 安全设置：修改密码（第 9 条要求整合进同一入口）=====
         不做成第二个页面：用户改资料与改密码都是「账号设置」，
         拆两处会让「我刚才改过了」变成一件需要记忆的事。 -->
    <div class="pf-security">
      <h4>修改密码</h4>
      <form class="pf-form" @submit.prevent="savePassword">
        <label class="pf-row">
          <span>原密码</span>
          <input v-model="pw.oldPassword" type="password" autocomplete="current-password" />
        </label>
        <label class="pf-row">
          <span>新密码</span>
          <input v-model="pw.newPassword" type="password" autocomplete="new-password" />
        </label>
        <label class="pf-row">
          <span>确认新密码</span>
          <input v-model="pw.confirmPassword" type="password" autocomplete="new-password" />
        </label>
        <small v-if="pwError" class="pf-error">{{ pwError }}</small>
        <div class="pf-actions">
          <button class="primary" type="submit" :disabled="pwSaving">
            {{ pwSaving ? '提交中…' : '修改密码' }}
          </button>
        </div>
      </form>
    </div>
  </section>
</template>

<script>
import { inject, ref, reactive, computed, onMounted, watch } from 'vue';
import { api } from '../api/client';

/**
 * 个人资料（第 9 条）。
 *
 * 手机号换绑必须验证新号 —— 手机号是登录/找回凭证，
 * 无验证改绑等于任何拿到会话的人都能换绑后用「忘记密码」拿走账号。
 * 项目暂无短信通道，验证码先走后端日志/联调返回，通道就绪后前端不用改。
 */
export default {
  name: 'ProfilePage',
  setup() {
    const appCtx = inject('appCtx');
    const session = appCtx?.session;

    const form = reactive({
      nickname: '', gender: '', birthday: '', email: '', phone: '', avatarUrl: '',
    });
    const pw = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });
    const saving = ref(false);
    const pwSaving = ref(false);
    const pfError = ref('');
    const pwError = ref('');
    const sending = ref(false);
    const verifyCode = ref('');
    const devCode = ref('');
    const verifyToken = ref('');
    const avatarInput = ref(null);

    const today = new Date().toISOString().slice(0, 10);
    const initial = computed(() => (form.nickname || session?.user?.username || '?').charAt(0));
    const phoneChanged = computed(
      () => !!form.phone && form.phone !== (session?.user?.phone || ''));

    function fill() {
      const u = session?.user;
      if (!u) return;
      form.nickname = u.nickname || '';
      form.gender = u.gender || '';
      form.birthday = u.birthday || '';
      form.email = u.email || '';
      form.phone = u.phone || '';
      form.avatarUrl = u.avatarUrl || '';
      // 资料页要显示最新等级，必须重新拉 /auth/me（本地缓存可能是旧的）
      appCtx?.loadMe?.();
    }

    async function save() {
      pfError.value = '';
      if (phoneChanged.value && !verifyToken.value) {
        pfError.value = '更换手机号需要先验证新手机号';
        return;
      }
      saving.value = true;
      try {
        const payload = {
          nickname: form.nickname,
          email: form.email,
          gender: form.gender || null,
          birthday: form.birthday || null,
        };
        if (phoneChanged.value) {
          payload.phone = form.phone;
          payload.verifyToken = verifyToken.value;
        }
        if (form.avatarUrl) payload.avatarUrl = form.avatarUrl;
        const res = await api.put('/auth/me', payload);
        applyUser(res);
        // 换绑成功后清掉验证码状态
        if (phoneChanged.value) {
          verifyCode.value = '';
          verifyToken.value = '';
          devCode.value = '';
        }
        appCtx?.flash?.('资料已保存');
      } catch (e) {
        pfError.value = e?.message || '保存失败';
      } finally {
        saving.value = false;
      }
    }

    async function sendVerify() {
      sending.value = true;
      devCode.value = '';
      try {
        const res = await api.post('/auth/phone-verify/request', { phone: form.phone });
        // 未接短信通道时后端会回传验证码（联调），接上通道后为 null
        devCode.value = res?.devCode || '';
        appCtx?.flash?.(devCode.value ? `验证码：${devCode.value}` : '验证码已发送');
      } catch (e) {
        pfError.value = e?.message || '发送失败';
      } finally {
        sending.value = false;
      }
    }

    async function confirmVerify() {
      try {
        const res = await api.post('/auth/phone-verify/confirm',
          { phone: form.phone, code: verifyCode.value });
        verifyToken.value = res?.verifyToken || '';
        pfError.value = '';
        appCtx?.flash?.('验证通过，别忘了点保存资料');
      } catch (e) {
        pfError.value = e?.message || '验证失败';
      }
    }

    async function savePassword() {
      pwError.value = '';
      if (!pw.oldPassword) { pwError.value = '请输入原密码'; return; }
      if (!pw.newPassword || pw.newPassword.length < 6) { pwError.value = '新密码至少 6 位'; return; }
      if (pw.newPassword !== pw.confirmPassword) { pwError.value = '两次输入的新密码不一致'; return; }
      pwSaving.value = true;
      try {
        await appCtx.submitChangePassword({
          oldPassword: pw.oldPassword,
          newPassword: pw.newPassword,
          confirmPassword: pw.confirmPassword,
        });
        pw.oldPassword = pw.newPassword = pw.confirmPassword = '';
        appCtx?.flash?.('密码已修改');
      } catch (e) {
        pwError.value = e?.message || '修改失败';
      } finally {
        pwSaving.value = false;
      }
    }

    /** 写入 session.user 并强制刷新 localStorage —— 头像/昵称要立刻生效 */
    function applyUser(res) {
      if (!res || !session) return;
      Object.assign(session.user, res);
      localStorage.setItem('supermarket_user', JSON.stringify(session.user));
    }

    /** 复用导航栏那套头像上传（App.vue 的隐藏 input + onAvatarPick），
     *  不自己再实现一遍 —— 两处各写一套上传必然出现「一处能传一处不能传」。 */
    function pickAvatar() {
      appCtx?.avatarInput?.click?.();
    }

    // 导航栏上传完头像后 session.user.avatarUrl 会变，这里跟着刷新显示
    watch(() => session?.user?.avatarUrl, (v) => { if (v) form.avatarUrl = v; });

    onMounted(fill);

    return { form, pw, saving, pwSaving, pfError, pwError, sending, verifyCode, devCode,
             verifyToken, today, initial, phoneChanged,
             save, sendVerify, confirmVerify, savePassword, pickAvatar };
  },
};
</script>
