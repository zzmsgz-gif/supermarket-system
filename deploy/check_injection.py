#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
依赖注入漏传体检（配合 useInjectionAudit 技能）

背景：App.vue 把状态/函数按名字注入给 composables（useXxx({ a, b, c })），
少传一个不会在构建期报错，只在用户点那个按钮时炸成「xxx is not a function」。
terser 压缩会把参数名 mangle 成单字母，报错就成了毫无线索的「S is not a function」
（2026-10-06 详情页「加入购物车」报错即此因：useQuickBuy 漏传 loadCart）。

本脚本做静态对账：把每个 composable 形参里「必须由调用方提供」的标识符
与 App.vue 实际传入的键求差集，有差集就非零退出。可挂进 CI 或提交前自查。

注意：形参默认值（如 `onAddedFeedback, // (v) => {}`）一律视为必传 ——
漏传了就是 undefined，与是否报错无关，报错只是早晚。

用法：
    python deploy/check_injection.py
    python deploy/check_injection.py --root "D:/supermarket system/frontend/src"
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

# 形参里这些名字由 composable 自己 import，不是调用方要传的
NEVER_INJECTED = {
    'api', 'ref', 'reactive', 'computed', 'watch', 'onMounted', 'onBeforeUnmount',
    'onUnmounted', 'defineAsyncComponent', 'nextTick', 'toRefs',
}


def split_top_level(text: str) -> list[str]:
    """按顶层逗号切分，跳过嵌套的 {} / [] / () 与字符串、注释里的逗号。"""
    parts: list[str] = []
    buf: list[str] = []
    depth = 0
    quote = None
    i = 0
    while i < len(text):
        ch = text[i]
        if quote:
            buf.append(ch)
            if ch == '\\':
                if i + 1 < len(text):
                    buf.append(text[i + 1])
                    i += 2
                    continue
            elif ch == quote:
                quote = None
            i += 1
            continue
        if ch in '"\'`':
            quote = ch
            buf.append(ch)
            i += 1
            continue
        if ch == '/' and i + 1 < len(text) and text[i + 1] == '/':
            while i < len(text) and text[i] != '\n':
                i += 1
            continue
        if ch == '/' and i + 1 < len(text) and text[i + 1] == '*':
            i += 2
            while i + 1 < len(text) and not (text[i] == '*' and text[i + 1] == '/'):
                i += 1
            i += 2
            continue
        if ch in '([{':
            depth += 1
        elif ch in ')]}':
            depth -= 1
        if ch == ',' and depth == 0:
            parts.append(''.join(buf))
            buf = []
            i += 1
            continue
        buf.append(ch)
        i += 1
    parts.append(''.join(buf))
    return [p.strip() for p in parts if p.strip()]


def parse_app_calls(app_src: str) -> dict[str, set[str]]:
    """收集 App.vue 中每个 useXxx({...}) 实际传入的键。"""
    calls: dict[str, set[str]] = {}
    for m in re.finditer(r'\buse([A-Z]\w*)\s*\(\{(.*?)\}\)', app_src, re.S):
        name = 'use' + m.group(1)
        keys: set[str] = set()
        for part in split_top_level(m.group(2)):
            key = part.split(':')[0].split('=')[0].strip()
            if re.fullmatch(r'[A-Za-z_$][\w$]*', key):
                keys.add(key)
        calls.setdefault(name, set()).update(keys)
    return calls


def check_appctx_exports(src: Path) -> list[str]:
    """查「模板用 appCtx.X 调函数，但 appCtx 上没挂 X」。

    这类漏挂**注入体检查不出来**：composable 形参都传全了，只是没人把返回值挂到 provide 的对象上。
    症状极具误导性 —— 页面一切正常，直到用户点那��按钮：
      appCtx.refreshProductDetail is not a function
    抛错发生在 await 链中间时，会把**外层整个流程**（如支付后的跳转、以及转圈的时长保证）一起吞掉，
    表现为「支付成功了却停在原地 / 转圈只闪一下」，与真正的报错点隔着好几层。

    两道检查：
      ① 所有 .vue / .js 里出现的 `appCtx.<ident>`，必须在 App.vue 里有对应的挂载语句
      ② App.vue 里从 composable 解构出来的标识符，凡是出现在 appCtx.* 的，必须真的被挂上
    """
    problems: list[str] = []
    app_src = (src / 'App.vue').read_text(encoding='utf-8')

    # App.vue 上「实际挂载了哪些键」：appCtx.x = ... 以及 appCtx 对象字面量里的 x
    mounted: set[str] = set()
    mounted.update(re.findall(r'appCtx\.([A-Za-z_$][\w$]*)\s*=', app_src))
    obj_match = re.search(r'const appCtx\s*=\s*\{(.*?)\};', app_src, re.S)
    if obj_match:
        for part in split_top_level(obj_match.group(1)):
            key = part.split(':')[0].split('=')[0].strip()
            if re.fullmatch(r'[A-Za-z_$][\w$]*', key):
                mounted.add(key)

    # 全站实际被当作函数调用的 appCtx.X
    called: dict[str, list[str]] = {}
    for f in list(src.rglob('*.vue')) + list(src.rglob('*.js')):
        text = f.read_text(encoding='utf-8', errors='ignore')
        # 只看「调用」形态：appCtx.x( 或 appCtx.x.await —— 纯读取不算错
        for name in re.findall(r'appCtx\.([A-Za-z_$][\w$]*)\s*\(', text):
            called.setdefault(name, []).append(str(f.relative_to(src)))

    for name, where in sorted(called.items()):
        if name not in mounted:
            uniq = ', '.join(sorted(set(where))[:4])
            problems.append(f'  appCtx.{name} 被调用但未挂载   ← {uniq}')

    # 反向：解构出来、又被当作 appCtx.X 调用，却没挂载的（覆盖情况更全）
    for m in re.finditer(r'const\s*\{([^}]*)\}\s*=\s*use[A-Z]\w*\(', app_src):
        for part in split_top_level(m.group(1)):
            key = part.split(':')[0].split('=')[0].strip()
            if re.fullmatch(r'[A-Za-z_$][\w$]*', key) and key in called and key not in mounted:
                problems.append(f'  appCtx.{key} 被调用但未挂载（已从 composable 解构）')

    # App.vue 上「真正有定义」的标识符：import / const / let / function / 解构
    defined: set[str] = set()
    for group in re.findall(r'import\s*\{([^}]*)\}', app_src):
        for piece in group.split(','):
            piece = piece.strip()
            if piece:
                defined.add(piece.split(' as ')[-1].strip())
    defined |= set(re.findall(r'(?:const|let|var|function)\s+([A-Za-z_$][\w$]*)', app_src))
    for group in re.findall(r'const\s*\{([^}]*)\}\s*=', app_src):
        for part in split_top_level(group):
            key = part.split(':')[0].split('=')[0].strip()
            if re.fullmatch(r'[A-Za-z_$][\w$]*', key):
                defined.add(key)
    # Vue 生命周期钩子与宏
    defined |= {'defineProps', 'defineEmits', 'defineModel', 'defineExpose', 'defineOptions'}

    # ⚠️ appCtx 上挂了「App.vue 里根本没定义」的符号 —— 页面直接白屏。
    # 实例（2026-10-07）：给 appCtx 加了 openImageViewer 却忘了 import，
    # 运行时报 `openImageViewer is not defined`、整页正文长度为 0。
    # 这与「声明了但没传」正好相反：前者是 appCtx 有、定义无；后者是定义有、appCtx 无。
    ghosts = sorted(m for m in mounted if m not in defined)
    for g in ghosts:
        problems.append(f'  appCtx 挂了 {g}，但 App.vue 里没有它的定义（import/声明）')

    return sorted(set(problems))


def check_props_declared_vs_passed(src: Path) -> list[str]:
    """查「组件声明了某 prop，但父组件根本没传」。

    **这是注入漏项里最阴的一种**：Vue 对未传的 prop 给 `undefined`，
    组件里 `required: true` **不会报错**（只在 devtools 里提示），
    模板里 `someFn(...)` 就成了 `undefined(...)` → TypeError →
    **整个组件渲染中断、页面一片空白，且必须刷新才恢复**。

    为什么难查（2026-10-07「管理端售后管理打不开」连踩三次同源的）：
    - 控制台 errors 有记录但堆栈指向压缩产物，看不出是哪个 prop；
    - **「有时正常」**：这些调用只在**列表有数据时**才执行。
      默认筛选库里 0 条 → 走空态分支 → 永远碰不到；一切换筛选拿到数据就崩。
    - 原体检只查 `appCtx.X(` 调用点 vs App.vue 挂载，**查不到组件 props 这一层**。

    做法：解析子组件的 `defineProps({...})` / `defineModel('x')` 声明，
    与父组件模板里该组件标签上实际写的 `:prop` / `v-model:prop` 求差集。
    ⚠️ 这条**不会误报** —— props 是显式声明的字面量，不涉及跨文件语义猜测。
    """
    problems: list[str] = []
    vue_files = list(src.rglob('*.vue'))

    def camel(kebab: str) -> str:
        parts = kebab.split('-')
        return parts[0] + ''.join(p.capitalize() for p in parts[1:])

    for child in vue_files:
        ctext = child.read_text(encoding='utf-8', errors='ignore')
        m = re.search(r'defineProps\(\{(.*?)\}\)\s*;', ctext, re.S)
        # ⚠️ 只报 `required: true` 的：非 required 的 prop 本来就允许不传
        # （Vue 给 undefined + 组件内应有默认值处理），把它们报出来全是噪音。
        # 2026-10-07 首版把整段 props 都当必传，误报了 7 个（withDefaults 的可选 prop、
        # defineProps(['x']) 简写形式等）→ 信噪比太差，等于没检查。
        required: set[str] = set()
        if m:
            body = m.group(1)
            for pm in re.finditer(r'^\s*([A-Za-z_$][\w$]*)\s*:\s*\{(.*?)\}',
                                 body, re.M | re.S):
                if re.search(r'required\s*:\s*true', pm.group(2)):
                    required.add(pm.group(1))
        # defineModel('x', { required: true }) 同理
        for dm in re.finditer(r"defineModel\(\s*'([A-Za-z_$][\w$]*)'\s*,?\s*(\{[^}]*\})?", ctext):
            if dm.group(2) and re.search(r'required\s*:\s*true', dm.group(2)):
                required.add(dm.group(1))
        if not required:
            continue

        tag = child.stem
        for parent in vue_files:
            if parent == child:
                continue
            ptext = parent.read_text(encoding='utf-8', errors='ignore')
            tm = re.search(r'<' + re.escape(tag) + r'[\s/>]', ptext)
            if not tm:
                continue
            end = ptext.find('/>', tm.start())
            if end < 0:
                end = ptext.find('>', tm.start())
            if end < 0:
                continue
            seg = ptext[tm.start():end]
            passed = {camel(x) for x in re.findall(r'(?<![\w-]):([A-Za-z][\w-]*)\s*=', seg)}
            passed |= {camel(x) for x in re.findall(r'v-model:([A-Za-z][\w-]*)', seg)}
            # ⚠️ 排掉「连字符逐字母拆写」：Vue 会把 :m-d-c-_-w-e-e-k 归一化成 MDC_WEEK，
            #    这类写法（依赖 kebab→camel 归一化）是**能正常工作**的，不能报。
            #    比对时忽略大小写 —— 模板里通常是全小写、声明里是驼峰或全大写缩写。
            lower_required = {r.lower() for r in required}
            for kebab in re.findall(r'(?<![\w-]):([A-Za-z][\w-]*)\s*=', seg):
                flat = kebab.replace('-', '').lower()
                if flat in lower_required:
                    passed.add(flat)
            missing = sorted(r for r in (required - passed)
                             if r.lower() not in {camel(x).lower() for x in
                                                 re.findall(r'(?<![\w-]):([A-Za-z][\w-]*)\s*=', seg)})
            if missing:
                problems.append(
                    f'  {child.name:26s} required 但 {parent.name} 未传: {missing}')
            break   # 每个父组件只报一次
    return sorted(set(problems))


def parse_composable_params(src: str, fname: str) -> list[str] | None:
    m = re.search(r'export\s+function\s+' + re.escape(fname) + r'\s*\(\{(.*?)\}\)\s*\{',
                  src, re.S)
    if not m:
        return None
    out: list[str] = []
    for part in split_top_level(m.group(1)):
        key = part.split(':')[0].split('=')[0].strip()
        if re.fullmatch(r'[A-Za-z_$][\w$]*', key) and key not in NEVER_INJECTED:
            out.append(key)
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument('--root', default='D:/supermarket system/frontend/src',
                    help='前端 src 目录')
    args = ap.parse_args()
    src = Path(args.root)
    app_file = src / 'App.vue'
    if not app_file.is_file():
        print(f'找不到 {app_file}', file=sys.stderr)
        return 2

    calls = parse_app_calls(app_file.read_text(encoding='utf-8'))
    problems: list[str] = []

    for f in sorted((src / 'composables').glob('*.js')):
        file_src = f.read_text(encoding='utf-8')
        for m in re.finditer(r'export\s+function\s+(use\w+)\s*\(', file_src):
            fname = m.group(1)
            if fname not in calls:
                continue
            need = parse_composable_params(file_src, fname)
            if need is None:
                continue
            missing = [p for p in need if p not in calls[fname]]
            if missing:
                problems.append(f'  {f.name:24s} {fname:22s} 漏传: {missing}')

    appctx_problems = check_appctx_exports(src)
    if appctx_problems:
        print('appCtx 挂载漏项（页面会报 "appCtx.X is not a function"）：')
        print('\n'.join(appctx_problems))
        problems.append('')

    props_problems = check_props_declared_vs_passed(src)
    if props_problems:
        print('组件 props 声明了但父组件没传（undefined(...) → 整块面板空白且刷新才恢复）：')
        print('\n'.join(props_problems))
        problems.append('')

    if problems:
        if appctx_problems or props_problems:
            print('另：以上问题之外的注入漏传检查——')
        else:
            print('依赖注入漏传（会在运行时炸成 "xxx is not a function"）：')
        for p in problems:
            if p:
                print(p)
        return 1
    print('体检通过：composable 注入无漏传，appCtx 挂载无漏项，组件 props 声明与父组件一致。')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())