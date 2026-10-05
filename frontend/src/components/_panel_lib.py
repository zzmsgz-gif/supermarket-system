"""把 AdminPanel.vue 里「一个 tab 一个 div」的面板整块搬成独立组件。

只处理「独占型」面板（依赖都在父级 composable 装配产物里，不与别的面板共享 state）。
共享 state 的面板（如 stock + categories 共用 useAdminCategoryStock）必须成对搬，不能用本脚本。

面板边界的定法（踩过坑，别改）：
- 起点：缩进 10 的 `<div v-if="adminMenu === 'xxx'"`，再向上吸收紧邻的 <!-- --> 注释（那些是面板说明）
- 终点：起点之后**第一个**「缩进 ≤10 且 strip()=='</div>'」的行
  ★ 不能用「下一个面板起点 - 1」—— 最后一个面板会一路吃到 </section></template>，构建直接报
    "Invalid end tag"，而且 Vue 的报错不指出行号，得自己回头数缩进。
"""
import re

PANEL_FILE = 'AdminPanel.vue'


def load():
    return open(PANEL_FILE, encoding='utf-8').read().split('\n')


def script_start(lines):
    return next(i for i, l in enumerate(lines) if l.strip() == '<script setup>')


def panel_range(lines, key):
    """返回 (start, end) 0-based 闭区间；找不到就抛。"""
    ts = script_start(lines)
    for i in range(1, ts):
        s = lines[i]
        if (len(s) - len(s.lstrip())) != 10:
            continue
        if not s.strip().startswith('<div v-if="adminMenu'):
            continue
        if ("adminMenu === '%s'" % key) not in s:
            continue
        start = i
        while start > 0 and lines[start - 1].strip().startswith('<!--'):
            start -= 1
        for j in range(i + 1, ts):
            t = lines[j]
            if t.strip() == '</div>' and (len(t) - len(t.lstrip())) <= 10:
                return start, j
    raise SystemExit('面板未找到: ' + key)


def extract_body(lines, key):
    """切出面板模板，去掉外层 div 的 v-if，缩进从 10 降到 2（组件模板里统一 2 起）。"""
    start, end = panel_range(lines, key)
    body = '\n'.join(lines[start:end + 1])
    pattern = (r'^\s*(?:<!--.*?-->\s*\n)*\s*'
               r'<div v-if="adminMenu === \'%s\'" class="data-panel">' % key)
    body = re.sub(pattern, '  <div class="data-panel">', body, flags=re.S)
    body = '\n'.join(l[8:] if l.startswith(' ' * 8) else l for l in body.split('\n'))
    body = body.rstrip() + '\n'
    # 硬断言：搬出来的块必须以 data-panel 的 </div> 收尾、以它开头
    nonempty = [l for l in body.split('\n') if l.strip()]
    assert nonempty[0].strip() == '<div class="data-panel">', \
        '%s 头部异常: %r' % (key, nonempty[0])
    assert nonempty[-1].strip() == '</div>', \
        '%s 尾部异常: %r' % (key, nonempty[-1])
    return body


def file_name(key):
    """面板组件的**文件名**（含 .vue）。"""
    return 'Admin' + key[0].upper() + key[1:] + 'Panel.vue'


def comp_name(key):
    """面板组件的**组件名**（不含 .vue）—— 模板里当标签用、import 里当变量名。"""
    return file_name(key)[:-len('.vue')]


def props_of(key):
    """从已生成的组件里读回 prop 名 —— 单一真相源，替换脚本不再重复抄一遍。"""
    t = open(file_name(key), encoding='utf-8').read()
    m = re.search(r'const props = defineProps\(\{\n(.*?)\n\}\);', t, re.S)
    assert m, key + ' 的 defineProps 未找到'
    def prop_name(line):
        mm = re.match(r'\s+(\w+): \{ type:', line)
        return mm.group(1) if mm else None

    plain = [n for n in (prop_name(l) for l in m.group(1).split('\n')) if n]
    models = re.findall(r"defineModel\('(\w+)'", t)
    return plain, models


def binding_of(key):
    plain, models = props_of(key)
    ms = set(models)
    return [(n, 'v-model' if n in ms else ':') for n in plain + models]
