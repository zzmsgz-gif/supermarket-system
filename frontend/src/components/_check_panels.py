"""校验 Admin*Panel.vue：模板里用到的标识符是否在 script 里有来源。

背景（2026-10-05）：拆面板时接连漏了 noticeTypeClass / noticeTypeLabel / formatDate，
每次都是「vite build 通过、真机 console 报 xxx is not a function」。模板编译不检查
标识符来源，所以这个校验器能在落盘前把问题拦住。

思路（别再用正则抠标识符，那条路会走进回溯陷阱）：
  1. 把模板里所有「词」都收下来（词 = 字母/数字/下划线/中文）。
  2. 排除掉两类肯定不是变量的：
       - 前面紧跟 `.` 或 `[` 的  → 那是字段名（bannerForm.id）
       - 后面紧跟 `(` 的         → 那是**函数调用**，函数名本身就是要查的
  3. 剩下的和 script 头的词集合求差；差集里再排除全局注册组件、局部 v-for 变量、
     以及可选链 `xxx?.yyy` 保护的名字。

用法：python src/components/_check_panels.py
"""
import re
import glob
import os
import sys

# 全局注册组件 / 模板内置 / 常见全局对象
GLOBAL = {
    'Number', 'String', 'Boolean', 'Array', 'Object', 'Date', 'Math', 'JSON',
    'true', 'false', 'null', 'undefined', 'NaN', 'Infinity',
    'empty-state', 'EmptyState', 'window', 'document',
    # v-for 表达式的语法词（"item in list" 里的 in / of）
    'in', 'of',
    # :style="{ height: x + 'px' }" 这类样式对象的键
    'height', 'width', 'top', 'left', 'right', 'bottom', 'maxWidth', 'zIndex',
    # 内联箭头函数的形参：@upload-state="v => ..." 里的 v、
    # adminStores.filter((s) => ...) 里的 s —— 单字母都算
    'v', 'k', 'l', 'm', 'n', 's', 't', 'x', 'y', 'z', 'o', 'p', 'q', 'r', 'u', 'w', 'j', 'g', 'h', 'i', 'a', 'b', 'c', 'd', 'e', 'f',
    'on', 'off',
    # class 名（:class="['ghost', 'danger']" 里的 danger 会被当变量）
    'danger', 'ghost', 'muted', 'ok', 'warn', 'tag', 'chip', 'active',
    # :class="{ on: x, taken: y, today: z, past: w }" 里的键
    'on', 'taken', 'today', 'past', 'blank', 'is-expired', 'off-word', 'on-word',
    'off', 'disabled', 'selected', 'checked', 'open', 'hide', 'show', 'clear',
}
# HTML 标签与属性名
TAGLIKE = re.compile(r'^[a-z]+$')

WORD = re.compile(r'[A-Za-z_$][\w$\u4e00-\u9fff]*')


def collect(s):
    head, tpl = s[:s.index('<template>')], s[s.index('<template>'):]
    have = set(WORD.findall(head)) | GLOBAL

    # v-for 的局部变量：v-for="(item, i) in list" / v-for="row of rows"
    for m in re.finditer(r'v-for="\(?([\w\s,$]+?)\)?\s+(?:in|of)\s', tpl):
        have |= {x.strip() for x in m.group(1).split(',') if x.strip()}
    for m in re.finditer(r'v-for="[\w\s,$]*\s+(?:in|of)\s+([\w$]+)', tpl):
        have.add(m.group(1))
    # 子组件标签当已有
    for m in re.finditer(r'<([A-Z][\w]*)', tpl):
        have.add(m.group(1))
    # 可选链保护：xxx?.yyy —— 缺了不炸，只是显示空
    optional = set(re.findall(r'([A-Za-z_$][\w$]*)\s*\?\.', tpl))
    # ★ 只看「表达式」：插值 {{ }} 与指令/绑定的属性值。
    #   直接扫整段模板会把 div/span/button/@click/v-for 这些标签和指令名全捞进来。
    exprs = []
    for m in re.finditer(r'\{\{(.*?)\}\}', tpl, re.S):
        exprs.append(m.group(1))
    for m in re.finditer(
            r'(?::[\w.-]+|v-[\w-]+(?::[\w.-]+)?|@[\w.-]+)="([^"]*)"', tpl):
        exprs.append(m.group(1))
    # 表达式里的字符串字面量（'ok' / 'in' / 'tag'）不是变量，剥掉
    tpl_nostr = re.sub(r'["\'][^"\']*["\']', ' ', ' '.join(exprs))

    missing = []
    for m in WORD.finditer(tpl_nostr):
        w = m.group(0)
        if w in have or w in optional or w in GLOBAL:
            continue
        s_, e_ = m.start(), m.end()
        prev = tpl_nostr[s_ - 1] if s_ > 0 else ''
        nxt = tpl_nostr[e_] if e_ < len(tpl_nostr) else ''
        if prev in '.[':          # 字段名 bannerForm.id / arr[0]
            continue
        if nxt == '(':            # 函数调用 —— 正是要查的
            missing.append(w)
            continue
        if nxt in '.[(':           # xxx.yyy / xxx[0] / xxx(
            continue
        if TAGLIKE.match(w) and w not in missing:
            missing.append(w)
    return sorted(set(missing))


def parent_symbols():
    """AdminPanel.vue 顶层能拿到的一切：adminCtx 解构 + composable 装配 + import + utils。"""
    here = os.path.dirname(os.path.abspath(__file__))
    src = open(os.path.join(here, 'AdminPanel.vue'), encoding='utf-8').read()
    syms = set()
    for m in re.finditer(r'const \{([^}]*)\} = ', src):
        syms |= {x.strip() for x in m.group(1).split(',') if x.strip()}
    for m in re.finditer(r'^(?:async )?function (\w+)\(', src, re.M):
        syms.add(m.group(1))
    for m in re.finditer(r'const (\w+) = ', src):
        syms.add(m.group(1))
    for m in re.finditer(r'import \{([^}]*)\} from', src):
        syms |= {x.strip() for x in m.group(1).split(',') if x.strip()}
    for m in re.finditer(r'import (\w+) from', src):
        syms.add(m.group(1))
    # 面板组件自己 import 的 utils / 子组件也算可用
    return syms


def main():
    files = [f for f in sorted(glob.glob(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'Admin*Panel.vue')))
             if not f.endswith('AdminPanel.vue')]
    bad = 0
    parent = parent_symbols()
    for f in files:
        s = open(f, encoding='utf-8').read()
        if '<template>' not in s:
            continue
        missing = collect(s)
        # ★ 面板 props 里的每个名字，父级必须真的有 —— 否则父组件模板传不进来，
        #   面板里 props.xxx 是 undefined，报错发生在子组件内部、堆栈指向 Vue 内部，很难查。
        head = s[:s.index('<template>')]
        # kebab-case 的 prop 名要转回 camelCase 再比（父级模板写 :select-menu="selectAdminMenu"）
        def camel(n):
            parts = n.split('-')
            return parts[0] + ''.join(x[:1].upper() + x[1:] for x in parts[1:])
        for m in re.finditer(r'^  ([\w-]+): \{ type:', head, re.M):
            pn = camel(m.group(1))
            if pn == 'adminCtx' or pn in parent:
                continue
            # 面板里写了别名（const selectAdminMenu = props.selectMenu）→ 认这个别名
            if re.search(r'=\s*props\.' + re.escape(pn) + r'\b', head):
                continue
            missing.append('%s(父级没有)' % pn)
        name = os.path.basename(f)
        if missing:
            print('  ★ %-34s 缺: %s' % (name, ', '.join(missing)))
            bad += 1
        else:
            print('  ✓ %-34s OK' % name)
    print()
    print('检查 %d 个面板组件，%d 个有问题' % (len(files), bad))
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
