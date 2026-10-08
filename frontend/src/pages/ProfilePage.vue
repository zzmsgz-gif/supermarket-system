<template>
  <section class="data-panel profile-page">
    <div class="panel-head">
      <h3>个人资料</h3>
      <small>昵称、性别、生日、邮箱可直接改；手机号换绑需要验证新号</small>
    </div>

    <!-- 头像：点图片**放大看大图**，改头像走下面的「更换头像」按钮（2026-10-09）。
         两个动作分开：点图片是想看清，点按钮是想换。混在一起会误触。 -->
    <div class="pf-avatar-row">
      <span
        class="pf-avatar"
        :class="{ 'is-clickable': !!form.avatarUrl }"
        :title="form.avatarUrl ? '点击看大图' : ''"
        @click="previewAvatar"
      >
        <img v-if="form.avatarUrl" :src="form.avatarUrl" alt="头像" />
        <span v-else class="avatar-default">{{ initial }}</span>
      </span>
      <div class="pf-avatar-ops">
        <button class="ghost sm" type="button" @click="pickAvatar">更换头像</button>
        <small>支持 jpg / png</small>
      </div>
    </div>

    <form class="pf-form" @submit.prevent="save">
      <!-- 必填项标红色 *：只标**真的会校验**的字段（昵称、手机号）。
           标了 * 却不校验，等于骗用户 —— 见 validate() 里的对应规则。 -->
      <label class="pf-row">
        <span><i class="req">*</i>昵称</span>
        <input v-model.trim="form.nickname" maxlength="40" placeholder="给自己起个名字" />
      </label>

      <label class="pf-row">
        <span>性别<em class="opt">选填</em></span>
        <select v-model="form.gender">
          <option value="">未设置</option>
          <option value="MALE">男</option>
          <option value="FEMALE">女</option>
          <option value="SECRET">保密</option>
        </select>
      </label>

      <label class="pf-row">
        <span>生日<em class="opt">选填</em></span>
        <input v-model="form.birthday" type="date" :max="today" />
      </label>

      <label class="pf-row">
        <span>邮箱<em class="opt">选填</em></span>
        <input v-model.trim="form.email" type="email" placeholder="用于接收通知" />
      </label>

      <!-- 手机号：换绑要验证新号（第 9 条的关键安全点） -->
      <div class="pf-row">
        <span><i class="req">*</i>手机号</span>
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
      const err = validate();
      if (err) {
        // 表单内红字 + 轻提示双保险：红字持续可见（用户改完字段还在），
        // 轻提示吸引注意（有些用户不会往下看表单）
        pfError.value = err;
        notify(err);
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
        notify('资料已保存');
      } catch (e) {
        const msg = e?.message || '保存失败';
        pfError.value = msg;
        notify(msg);          // 失败同样要提示，否则用户不知道到底存没存上
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
        notify(devCode.value ? `验证码：${devCode.value}` : '验证码已发送');
      } catch (e) {
        const msg = e?.message || '验证码发送失败';
        pfError.value = msg;
        notify(msg);
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
        notify('验证通过，别忘了点保存资料');
      } catch (e) {
        const msg = e?.message || '验证失败';
        pfError.value = msg;
        notify(msg);
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
        notify('密码已修改');
      } catch (e) {
        const msg = e?.message || '修改失败';
        pwError.value = msg;
        notify(msg);
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

    /**
     * 轻提示（toast）。
     *
     * <p>⚠️ 这里**不能写 `appCtx?.flash?.()`** —— appCtx 上压根没有 flash 这个方法，
     * 可选链会把它变成一次静默的空操作：代码看起来执行了、用户却什么都看不到
     * （2026-10-09「保存资料没提示」就是这个原因）。
     * 项目里真正的提示是 `notice`（ref，3.2 秒后自动消失）。
     */
    function notify(msg) {
      const n = appCtx?.notice;
      if (n && typeof n === 'object' && 'value' in n) n.value = msg;
    }

    /** 点头像看大图 —— 复用全局图片预览器 */
    function previewAvatar() {
      if (form.avatarUrl) appCtx?.openImageViewer?.(form.avatarUrl);
    }

    /**
     * 必填校验。
     *
     * <p>只校验**标了红色 ***的字段（昵称、手机号）。
     * 标了星号却不校验等于骗用户 —— 所以这里的规则必须和模板上的 * 一一对应。
     */
    function validate() {
      if (!form.nickname || !form.nickname.trim()) return '请填写昵称';
      const phone = (form.phone || '').trim();
      if (!phone) return '请填写手机号';
      if (!/^1[3-9]\d{9}$/.test(phone)) return '手机号格式不正确';
      if (phoneChanged.value && !verifyToken.value) return '更换手机号需要先验证新手机号';
      return '';
    }

    /** 复用导航栏那套头像上传（App.vue 的隐藏 input + onAvatarPick），
     *  不自己再实现一遍 —— 两处各写一套上传必然出现「一处能传一处不能传」。 */
    function pickAvatar() {
      appCtx?.avatarInput?.click?.();
    }

    // 导航栏上传完头像后 session.user.avatarUrl 会变，这里跟着刷新显示
    watch(() => session?.user?.avatarUrl, (v) => { if (v) form.avatarUrl = v; });

    /**
     * ⚠️ 这里必须 **等 loadMe() 回来再 fill 一次**（2026-10-09 修的真 bug）：
     * 首次 fill 用的是 localStorage 里的 user 快照，它可能缺字段（老版本缓存、
     * 或别处写入时没带 phone），于是表单一直是空的、用户一点保存就报「请填写手机号」。
     * 用服务端返回的权威数据再填一遍才靠谱。
     */
    onMounted(async () => {
      fill();
      await appCtx?.loadMe?.();
      fill();
    });

    return { form, pw, saving, pwSaving, pfError, pwError, sending, verifyCode, devCode,
             verifyToken, today, initial, phoneChanged,
             save, sendVerify, confirmVerify, savePassword, pickAvatar, previewAvatar };
  },
};
</script>
