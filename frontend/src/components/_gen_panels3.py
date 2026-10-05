"""生成 flashSales / activities / memberDays / passwordResets 四个面板组件。

props 类型是**手工核实的**，不是猜的：ref/reactive → Object，computed 数字 → Number，
字符串 ref → String，数组 ref → Array，函数 → Function。类型声明错不影响运行，
但会让「传错了」时报出误导性的错，所以值得逐个核。
"""
import re
from _panel_lib import load, extract_body, file_name

# key -> (中文面板名, utils 导入, 额外 import, props 列表)
SPEC = {
    'flashSales': ('限时秒杀', ['money'],
                   ["import ImageUpload from './ImageUpload.vue';"],
                   ['adminFlashSales', 'categories', 'closeFlashForm', 'deleteFlashSale',
                    'flashEditingId', 'flashForm', 'flashFormOpen', 'flashProductOptions',
                    'flashStateClass', 'flashStateLabel', 'openFlashForm', 'saveFlashSale',
                    'toggleFlashStatus']),
    'activities': ('营销活动', ['formatDate'], [],
                   ['activityDiscountLabel', 'activityForm', 'activityProducts',
                    'activityScopeLabel', 'activityTypeLabel', 'adminActivities',
                    'adminActivityJumpPage', 'adminActivityKeyword', 'adminActivityTotalPages',
                    'categories', 'changeAdminActivityPage', 'changeAdminActivityPageSize',
                    'deleteActivity', 'editActivity', 'fillActivityPeriod',
                    'onActivityScopeChange', 'resetActivityForm', 'resetAdminActivitySearch',
                    'saveActivity', 'searchAdminActivities', 'toggleActivity']),
    'memberDays': ('会员日', [], [],
                   ['MDC_WEEK', 'closeMemberDayForm', 'deleteMemberDay',
                    'memberDayCalendarCells', 'memberDayCalendarMonthText',
                    'memberDayDateLabel', 'memberDayDateTaken', 'memberDayEnabledCount',
                    'memberDayForm', 'memberDayFormOpen', 'memberDaySlogan', 'memberDays',
                    'openMemberDayForm', 'resetMemberDayCalendar', 'saveMemberDay',
                    'shiftMemberDayCalendar', 'toggleMemberDay']),
    'passwordResets': ('密码重置申请', ['formatDate'], [],
                       ['changePasswordResetPage', 'changePasswordResetPageSize',
                        'confirmRejectPasswordReset', 'confirmResetPassword',
                        'copyTempPassword', 'passwordResetJumpPage', 'passwordResetResult',
                        'passwordResetStatus', 'passwordResetStatusClass',
                        'passwordResetStatusLabel', 'passwordResetTotalPages',
                        'passwordResets', 'refreshCurrentAdminMenu']),
}

# 已核实的类型；未列出的一律 Function
TYPES = {
    'adminFlashSales': 'Array', 'categories': 'Array', 'flashEditingId': 'Object',
    'flashForm': 'Object', 'flashFormOpen': 'Boolean', 'flashProductOptions': 'Array',
    'adminActivities': 'Object', 'activityForm': 'Object', 'activityProducts': 'Array',
    'adminActivityJumpPage': 'Object', 'adminActivityKeyword': 'String',
    'adminActivityTotalPages': 'Number',
    'MDC_WEEK': 'Array', 'memberDayCalendarCells': 'Array',
    'memberDayCalendarMonthText': 'String', 'memberDayEnabledCount': 'Number',
    'memberDayForm': 'Object', 'memberDayFormOpen': 'Boolean', 'memberDaySlogan': 'String',
    'memberDays': 'Array',
    'passwordResetJumpPage': 'Object', 'passwordResetResult': 'Object',
    'passwordResetStatus': 'String', 'passwordResetTotalPages': 'Number',
    'passwordResets': 'Object',
}

HEADER = ('// 后台「{name}」面板：从 AdminPanel.vue 整块搬过来的。\n'
          '//\n'
          '// 依赖刻意走显式 props 而不是 adminCtx：这个面板只服务一个 tab，把真正用到的东西列全，\n'
          '// 一眼就能看出「它为什么需要这些」。接 adminCtx 会让依赖变成隐式的。\n')


def main():
    lines = load()
    for key, (name, utils, extra, props) in SPEC.items():
        body = extract_body(lines, key)
        out = '<script setup>\n'
        out += HEADER.format(name=name)
        if utils:
            out += "import { %s } from '../utils/format';\n" % ', '.join(utils)
        for e in extra:
            out += e + '\n'
        out += '\nconst props = defineProps({\n'
        out += '\n'.join('  %s: { type: %s, required: true },' % (n, TYPES.get(n, 'Function'))
                         for n in props)
        out += '\n});\n'
        out += 'const { %s } = props;\n' % ', '.join(props)
        out += '</script>\n\n<template>\n%s</template>\n' % body
        fn = file_name(key)
        open(fn, 'w', encoding='utf-8', newline='').write(out)
        print('%-28s %3d 行  props %2d' % (fn, out.count('\n'), len(props)))


if __name__ == '__main__':
    main()
