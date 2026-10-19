package com.example.supermarket.util;

/**
 * 昵称「视觉宽度」计算 —— 让中英文昵称按**实际占宽**受同一个限制约束。
 *
 * <p>背景：昵称如果用「字符数」限制，中文和英文的实际占宽差一倍 ——
 * 12 个汉字（占 24 列）能把评价列表、订单行、后台表格整个撑折行，
 * 而 12 个字母还很宽松。按字符数定两套规则（中文 ≤6、英文 ≤12）又会让混合昵称（"小明Tom"）没处判定。
 *
 * <p>约定：汉字/日文假名/全角标点 = 2 列，其余（英文字母、数字、半角符号）= 1 列。
 * 前端 utils/format.js 的 nicknameColumns 是同一套规则，两边必须保持一致，
 * 否则会出现「前端放行、后端拒」或反之。
 */
public final class TextWidth {

    /** 昵称最大视觉宽度：中文 6 个字（12 列）= 英文 12 个字母。 */
    public static final int NICKNAME_MAX_COLUMNS = 12;

    private TextWidth() {
    }

    /** 字符串的视觉列数（宽字符计 2，窄字符计 1）。 */
    public static int columns(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int width = 0;
        int i = 0;
        while (i < value.length()) {
            int codePoint = value.codePointAt(i);
            width += isWide(codePoint) ? 2 : 1;
            i += Character.charCount(codePoint);
        }
        return width;
    }

    /**
     * 判定是否为「宽字符」（占两列）。覆盖汉字、CJK 标点、日文假名、
     * 韩文音节、全角字母数字等常见情形。emoji 按窄字符算 1 列（够用，不过度设计）。
     */
    private static boolean isWide(int codePoint) {
        return (codePoint >= 0x1100 && codePoint <= 0x115F)      // Hangul Jamo（韩文初声/中声/终声）
            || (codePoint >= 0x2E80 && codePoint <= 0xA4CF)      // CJK 部首 ~ 汉字 ~ 中日韩标点 ~ 假名 ~ 彝文
            || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)      // Hangul 音节
            || (codePoint >= 0xF900 && codePoint <= 0xFAFF)      // CJK 兼容表意文字
            || (codePoint >= 0xFE30 && codePoint <= 0xFE4F)      // CJK 兼容形式
            || (codePoint >= 0xFF00 && codePoint <= 0xFF60)      // 全角拉丁/标点
            || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6);     // 全角符号（￠￡￥等）
    }

    /** 昵称超限时抛出的业务异常信息（前后端文案保持一致）。 */
    public static final String NICKNAME_TOO_WIDE = "昵称过长：中文不超过 6 个字，英文不超过 12 个字母";

    /**
     * 校验昵称宽度，超限抛 400 业务异常。
     *
     * <p>空值一律放行：昵称不是必填（不填就默认等于用户名），空值由调用方决定怎么兜。
     */
    public static void assertNicknameWidth(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return;
        }
        if (columns(nickname.trim()) > NICKNAME_MAX_COLUMNS) {
            throw new com.example.supermarket.exception.BusinessException(400, NICKNAME_TOO_WIDE);
        }
    }
}
