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

    if problems:
        print('依赖注入漏传（会在运行时炸成 "xxx is not a function"）：')
        print('\n'.join(problems))
        return 1
    print('依赖注入体检通过：未发现漏传。')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())