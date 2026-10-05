"""把面板里的「v-model 双向绑定 state」从 props 解构改成 defineModel。

## 为什么必须改（这是拆面板最容易漏的一类 bug，且完全静默）

面板是从 AdminPanel 拆出来的。搜索关键词 / 每页条数 / 跳页号这类 UI 状态住在**父级**的 ref 里，
面板通过 props 拿到它。但共用件全都是 `v-model` 接的：

```vue
<AdminSearchBox v-model="categoryKeyword" @search="resetCategoryPage" />
```

展开成 `:model-value="categoryKeyword"` + `@update:model-value="$event => categoryKeyword = $event"`。
**props 是只读的**，Vue 在只读 prop 上赋值 **静默失败** —— 不报错、不警告、不进 console，
表现是「搜索框输了字没反应 / 下一页点了不动 / 跳页框回车无效」，页面看着完全正常。

三个共用件（AdminSearchBox / AdminPageSize / AdminPager）**本身不用改**，它们都正确
`emit('update:*)`。要改的是面板：把这些 state 用 `defineModel` 声明，数据流变成
`child --emit--> parent --ref--> child`，闭环。

## 判据：只处理真的被 v-model 双向绑定的

只读的（page / totalPages / filtered / items）保持 props —— 它们本来就不该被面板写。
"""
import re
import glob

BINDABLE = {
    # 搜索关键词
    'adminOrderKeyword': 'String', 'adminCouponKeyword': 'String',
    'adminUserKeyword': 'String', 'adminActivityKeyword': 'String',
    'adminProductKeyword': 'String', 'refundStatusFilter': 'String',
    'adminUserRole': 'String', 'adminUserStatus': 'String',
    'categoryKeyword': 'String', 'stockKeyword': 'String',
    # 跳页输入框（AdminPager 的 v-model:jump-page）
    'adminOrderJumpPage': 'Number', 'adminCouponJumpPage': 'Number',
    'adminUserJumpPage': 'Number', 'adminActivityJumpPage': 'Number',
    'adminJumpPage': 'Number', 'refundJumpPage': 'Number',
    'passwordResetJumpPage': 'Number', 'categoryJumpPage': 'Number',
    'stockJumpPage': 'Number',
}

NOTE = ('// ⚠️ 下面这几个用 defineModel 而不是 props —— 它们被 v-model 双向绑定，\n'
        '// 而 props 只读，面板里赋值会**静默失败**：搜索/翻页看着能点但不动，\n'
        '//    页面完全正常、控制台也没有报错。共用件（AdminSearchBox / AdminPageSize /\n'
        '//    AdminPager）本身不用改，它们本来就正确 emit(\'update:*\')。')


def main():
    for fn in sorted(glob.glob('Admin*Panel.vue')):
        if fn == 'AdminPanel.vue':
            continue
        src = open(fn, encoding='utf-8').read()
        if '<template>' not in src:
            continue

        # 只挑「模板里真的被 v-model 双向绑定」的名字
        bound = {m.group(1) for m in
                 re.finditer(r'v-model(?::[\w-]+)?="([A-Za-z_$][\w$]*)"', src)}
        moved = sorted(bound & set(BINDABLE), key=len, reverse=True)
        if not moved:
            continue

        tpl_at = src.index('<template>')
        head, tpl = src[:tpl_at], src[tpl_at:]

        # ① 从 defineProps 的声明列表里删掉（逐行精确匹配，不用正则拼 alternation）
        m = re.search(r'(const props = defineProps\(\{\n)(.*?)(\n\}\);)', head, re.S)
        assert m, '%s 的 defineProps 未找到' % fn
        drop = set(moved)
        def prop_name(line):
            # 只认真正的属性行（注释行、空行都要跳过）
            mm = re.match(r'\s{2}(\w+):', line)
            return mm.group(1) if mm else None

        kept = [l for l in m.group(2).split('\n')
                if l.strip() and (prop_name(l) is None or prop_name(l) not in drop)]
        head = head[:m.start(2)] + '\n'.join(kept) + head[m.end(2):]

        # ② 从解构行里删掉。两批面板的解构来源不同：
        #    早期几个（coupons/refunds/reviews/users/products）从 props.adminCtx 解构，
        #    后来的从 props 解构。两种都要处理，否则 defineModel 与解构同名重复声明。
        for src in ('props.adminCtx', 'props'):
            d = re.search(r'const \{([^}]*)\} = %s;' % re.escape(src), head)
            if not d:
                continue
            names = [x.strip() for x in d.group(1).split(',') if x.strip()]
            left = [x for x in names if x not in drop]
            if len(left) == len(names):
                continue
            repl = 'const { %s } = %s;' % (', '.join(left), src)
            head = head.replace(d.group(0), repl)
            if not left:
                head = head.replace(repl + '\n', '')

        # ③ defineModel 声明插在 defineProps 之后
        lines = head.split('\n')
        at = next(i for i, l in enumerate(lines) if l.rstrip() == '});') + 1
        block = ['', NOTE]
        for name in moved:
            block.append("const %s = defineModel('%s', { type: %s, required: true });"
                         % (name, name, BINDABLE[name]))
        lines[at:at] = block

        out = '\n'.join(lines) + tpl
        # 硬断言：单文件组件必须有且仅有一对 template/script 标签。
        # 实测踩过：拼接时多出一个 </template>，构建照样通过（Vue 容忍），
        # 但运行时整个面板**不渲染**、页面上什么都没有，console 也是空的。
        # 只数**根级**标签（行首无缩进）。面板模板内部本来就有原生 <template v-if>，
        # 直接 count('<template') 会误报。
        for pat in (r'^<template>', r'^</template>', r'^<script setup>', r'^</script>'):
            n = len(re.findall(pat, out, re.M))
            assert n == 1, '%s: %s 出现 %d 次（应为 1）' % (fn, pat, n)
        open(fn, 'w', encoding='utf-8', newline='').write(out)
        print('%-30s defineModel %d 个: %s' % (fn, len(moved), ', '.join(moved)))


if __name__ == '__main__':
    main()
