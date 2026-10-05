"""生成 AdminStoresPanel.vue —— 最后一个没拆的面板。

它是最干净的一个，恰好用来验证这套工具链：
- **无 v-model 双向绑定**：`v-model` 全绑在 `storeForm`（reactive 对象），传引用跨组件写安全，
  不需要 defineModel
- **无局部共用件**：没用 AdminSearchBox / AdminPager / AdminPageSize
- **无 utils 函数**：纯展示 + 几个操作按钮
"""
import re
from _panel_lib import load, panel_range, file_name

KEY = 'stores'
PROPS = ['adminStores', 'storeForm', 'storeFormOpen', 'storeEditingId',
         'openStoreForm', 'closeStoreForm', 'saveStore', 'toggleStoreStatus',
         'deleteStore', 'loadAdminStores']
TYPES = {
    'adminStores': 'Array', 'storeForm': 'Object', 'storeFormOpen': 'Boolean',
    'storeEditingId': 'Object',
}

HEADER = ('// 后台「门店自提」面板：从 AdminPanel.vue 整块搬过来的。\n'
          '//\n'
          '// 依赖走显式 props（不接 adminCtx）。这个面板没有 v-model 双向绑定 ——\n'
          '// 表单字段全绑在 storeForm 上，那是 reactive 对象，传引用跨组件写安全，\n'
          '// 所以不需要 defineModel。\n')


def main():
    lines = load()
    start, end = panel_range(lines, KEY)
    body = '\n'.join(lines[start:end + 1])
    body = re.sub(r'^\s*<div v-if="adminMenu === \'%s\'" class="data-panel">' % KEY,
                  '  <div class="data-panel">', body)
    body = '\n'.join(l[8:] if l.startswith(' ' * 8) else l for l in body.split('\n'))
    body = body.rstrip() + '\n'

    # 硬断言：结构与边界
    nonempty = [l for l in body.split('\n') if l.strip()]
    assert nonempty[0].strip() == '<div class="data-panel">', nonempty[0]
    assert nonempty[-1].strip() == '</div>', nonempty[-1]
    assert not re.search(r'v-model(?::[\w-]+)?="(?!storeForm)', body), \
        'stores 面板不该有双向绑定（若判据变了要改成 defineModel）'

    out = '<script setup>\n' + HEADER
    out += "import { toRefs } from 'vue';\n"
    out += '\nconst props = defineProps({\n'
    out += '\n'.join('  %s: { type: %s, required: true },' % (n, TYPES.get(n, 'Function'))
                     for n in PROPS)
    out += '\n});\n'
    # toRefs：解构 props 会丢响应性（首屏正常、之后永不更新）—— 这个坑踩过 13 个面板
    out += 'const { %s } = toRefs(props);\n' % ', '.join(PROPS)
    out += '</script>\n\n<template>\n%s</template>\n' % body

    fn = file_name(KEY)
    open(fn, 'w', encoding='utf-8', newline='').write(out)
    for pat in (r'^<template>', r'^</template>', r'^<script setup>', r'^</script>'):
        n = len(re.findall(pat, out, re.M))
        assert n == 1, '%s 出现 %d 次' % (pat, n)
    print('%-26s 源 %d 行 → %d 行  props %d' % (fn, end - start + 1, out.count('\n'), len(PROPS)))


if __name__ == '__main__':
    main()
