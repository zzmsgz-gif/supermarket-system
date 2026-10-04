"""
批次2：从 App.vue 抽出 useAuth / useRecharge。

边界（用配平 + 精确锚点，不依赖「下一个标记」）：
- auth 域：openAuth 函数头 → onAvatarPick 闭合（配平）。中间的
  window.addEventListener('auth-expired'/'must-change-password') 块与 loadMe 一并搬进
  composable 的 registerAuthListeners()，App.vue 在原处调用它挂监听。
  ⚠️ loadWallet 紧跟在 onAvatarPick 之后但属于充值域，故 auth 区间止于 onAvatarPick 闭合。
- recharge 域：充值注释头 → resetRecharge 闭合（配平）。backToShop 是导航语义，留在 App.vue。
"""
import re, sys

P = 'App.vue'
src = open(P, encoding='utf-8').read()
lines = src.split('\n')
orig = len(lines)

def find(pred, start=0):
    for i in range(start, len(lines)):
        if pred(lines[i]):
            return i
    raise SystemExit('锚点未找到')

def block_end(start):
    depth, started = 0, False
    for j in range(start, len(lines)):
        depth += lines[j].count('{') - lines[j].count('}')
        if '{' in lines[j]:
            started = True
        if started and depth == 0:
            return j
    raise SystemExit('配平失败 start=%d' % start)

def eat_blank(i):
    while i + 1 < len(lines) and lines[i + 1].strip() == '':
        i += 1
    return i

# ---- auth 区间 ----
i_auth_fn = find(lambda l: re.match(r'^function openAuth\b', l))
i_auth_start = i_auth_fn
# 往上并入紧邻的注释行（若有）
k = i_auth_start - 1
while k >= 0 and lines[k].strip().startswith('//'):
    i_auth_start = k
    k -= 1
i_avatar = find(lambda l: re.match(r'^async function onAvatarPick\b', l))
i_auth_end = eat_blank(block_end(i_avatar))

# ---- recharge 区间 ----
# ⚠️ loadWallet(1893) 夹在 auth 区间与充值注释头之间，必须一并纳入：
#    它属充值域（拉钱包余额），留在原地会变成孤儿函数。
i_rec_head = find(lambda l: l.strip().startswith('/* ---------------- 充值'))
i_wallet = find(lambda l: re.match(r'^async function loadWallet\b', l))
assert i_wallet < i_rec_head, 'loadWallet 应在充值注释头之前，实际 %d >= %d' % (i_wallet, i_rec_head)
i_rec_start = i_wallet
# 往上并入紧邻的注释/空行区间起点（取 auth 区间之后第一个非空块的起点）
k = i_rec_start - 1
while k > i_auth_end and (lines[k].strip() == '' or lines[k].strip().startswith('//')):
    i_rec_start = k
    k -= 1
i_reset = find(lambda l: re.match(r'^function resetRecharge\b', l))
i_rec_end = eat_blank(block_end(i_reset))

spans = [(i_auth_start, i_auth_end, 'auth'), (i_rec_start, i_rec_end, 'recharge')]
spans.sort()
for a, b in zip(spans, spans[1:]):
    if a[1] >= b[0]:
        raise SystemExit('区间重叠: %s' % (spans,))

# 边界硬校验
assert re.match(r'^(function openAuth|//)', lines[i_auth_start]), 'auth 起点异常: %r' % lines[i_auth_start][:60]
k = i_auth_end
while k > i_auth_start and lines[k].strip() == '':
    k -= 1
assert lines[k].strip() == '}', 'auth 末行异常: %r' % lines[k][:60]
assert lines[i_rec_start].strip().startswith(('/* ---------------- 充值', 'async function loadWallet', '//')), \
    'recharge 起点异常: %r' % lines[i_rec_start][:60]
assert 'loadWallet' in '\n'.join(lines[i_rec_start:i_rec_end + 1]), 'recharge 区间未含 loadWallet'
k = i_rec_end
while k > i_rec_head and lines[k].strip() == '':
    k -= 1
assert lines[k].strip() == '}', 'recharge 末行异常: %r' % lines[k][:60]

print('auth 区间: %d-%d (%d行)' % (i_auth_start, i_auth_end, i_auth_end - i_auth_start + 1))
print('recharge 区间: %d-%d (%d行) 含 loadWallet@%d' % (
    i_rec_start, i_rec_end, i_rec_end - i_rec_start + 1, i_wallet))

drop = set()
for a, b, _ in spans:
    drop.update(range(a, b + 1))
keep = [l for i, l in enumerate(lines) if i not in drop]
out = '\n'.join(keep)
print('共删除 %d 行' % len(drop))

# ---- import ----
imp = "import { useMessages } from './composables/useMessages';"
assert out.count(imp) == 1, 'import 锚点异常 x%d' % out.count(imp)
out = out.replace(imp, imp + """
import { useAuth } from './composables/useAuth';
import { useRecharge } from './composables/useRecharge';""")
# 顺手给批次1 的三个 import 补 .js 后缀，与项目风格一致
out = out.replace("from './composables/useStores';", "from './composables/useStores.js';")
out = out.replace("from './composables/useLegalDoc';", "from './composables/useLegalDoc.js';")
out = out.replace("from './composables/useMessages';", "from './composables/useMessages.js';")

# ---- 装配 ----
asm = "const ROUTE_VIEWS = ["
assert out.count(asm) == 1, '装配锚点异常'
assembly = """/* ---------------- 认证 / 钱包充值：已抽为 composable ---------------- */
// 装配点必须在全部依赖之后：auth 需要 rememberUser/refreshForSession/loadCart/goLogin/
// closeAccountMenu，recharge 需要 askConfirm/run/wallet/recharge。
// 且被抽走的符号在本行之前无任何同步求值（无 immediate watch / watchEffect），无 TDZ 风险。
const { authOpen, authTab, authSubmitting, loginForm, registerForm, authErrors, changeForm, changeErrors, changeSubmitting, forcedChange, resetForm, resetErrors, resetSubmitting, openAuth, closeAuth, switchAuth, resetAuthErrors, authErrorText, submitLogin, validateRegisterForm, submitRegister, forgotPassword, openChangePassword, submitPasswordReset, validateChangeForm, submitChangePassword, logout, handleAuthExpired, registerAuthListeners, loadMe, onAvatarPick } = useAuth({ getSession: () => session, getRoute: () => route, navigate, goLogin, rememberUser, refreshForSession, loadCart, notice, error, fail, showAlert, closeAccountMenu });

// 全局监听（token 过期 / 待强制改密）由 composable 统一注册，行为与拆分前一致
registerAuthListeners();

const { loadWallet, methodLabel, selectRechargePreset, onCustomAmountInput, formatCountdown, buildQrSvg, qrSvg, startCountdown, clearRechargeTimer, confirmRecharge, payRechargeOrder, cancelRechargeOrder, handleRechargeExpired, closeRechargeModal, resetRecharge } = useRecharge({ getSession: () => session, isAdmin, rememberUser, wallet, recharge, notice, fail, askConfirm, run });

"""
out = out.replace(asm, assembly + asm)

# ---- 硬断言 ----
gone = [
    'function openAuth(', 'function submitLogin(', 'async function submitRegister(',
    'function openChangePassword(', 'function logout(', 'function handleAuthExpired(',
    'async function loadMe(', 'async function onAvatarPick(',
    'async function loadWallet(', 'function confirmRecharge(', 'function resetRecharge(',
    "window.addEventListener('auth-expired'",
    "window.addEventListener('must-change-password'",
]
for g in gone:
    assert out.count(g) == 0, '旧定义残留: %r x%d' % (g, out.count(g))
for s in ['useAuth({', 'useRecharge({', 'registerAuthListeners();']:
    assert out.count(s) == 1, '装配异常 %r x%d' % (s, out.count(s))
# appCtx/adminCtx 引用符号仍需存在
for s in ['authOpen','authTab','loginForm','registerForm','authErrors','changeForm',
          'forcedChange','wallet','recharge','qrSvg','loadWallet','loadMe','logout',
          'confirmRecharge','methodLabel','formatCountdown','submitLogin','submitRegister']:
    assert re.search(r'\b' + s + r'\b', out), '符号丢失: ' + s

open(P, 'w', encoding='utf-8').write(out)
print('写入完成: %d -> %d 行 (净减 %d)' % (orig, len(keep), orig - len(keep)))
