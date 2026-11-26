/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.tool;

/**
 * 沉浸式阅读模式（M10）的显示偏好与纯计算逻辑。
 *
 * <p>职责边界：本类只定义“档位取值、档位归一化、阅读时长估算”等与界面无关的规则，
 * 不直接读写 SharedPreferences，也不引用 Android 类，因此可以脱离设备做单元测试。
 * 真正的持久化由 {@link ReadingPreferenceStore} 完成。
 *
 * <p>设计意图：把“档位 → 具体数值”的映射集中在一处，界面只按下标取值，
 * 后续要调整档位数或数值时只需改本类，避免散落在多个 Activity 中。
 */
public final class ReadingPrefs {

    /** 浅色主题：白底黑字，适合明亮环境。 */
    public static final int THEME_LIGHT = 0;

    /** 护眼主题：米黄底深棕字，降低强光刺激。 */
    public static final int THEME_EYE_CARE = 1;

    /** 深色主题：深灰底浅灰字，适合夜间阅读。 */
    public static final int THEME_DARK = 2;

    /** 三种主题的背景色（ARGB）。 */
    public static final int[] THEME_BACKGROUND_COLORS = {
            0xFFFFFFFF, 0xFFF6EFD9, 0xFF1E1E1E
    };

    /** 三种主题的正文颜色（ARGB）。 */
    public static final int[] THEME_TEXT_COLORS = {
            0xFF222222, 0xFF4A3B2A, 0xFFD6D6D6
    };

    /** 字号档位（单位 sp），至少三档以满足验收要求。 */
    public static final int[] FONT_SIZES_SP = {16, 19, 22};

    /** 行距档位（倍数），与字号档位独立组合。 */
    public static final float[] LINE_SPACING_MULTIPLIERS = {1.3f, 1.6f, 1.9f};

    /** 默认主题为浅色。 */
    public static final int DEFAULT_THEME = THEME_LIGHT;

    /** 默认字号取中间档。 */
    public static final int DEFAULT_FONT_LEVEL = 1;

    /** 默认行距取中间档。 */
    public static final int DEFAULT_LINE_SPACING_LEVEL = 1;

    /** 阅读速度估算基准：中文阅读约每分钟 300 字，仅用于给出量级参考。 */
    public static final int CHARACTERS_PER_MINUTE = 300;

    private ReadingPrefs() {
        // 工具类不允许实例化
    }

    /**
     * 把任意主题值归一到合法档位；非法值回落到默认主题。
     *
     * <p>这样做的原因：偏好值可能来自旧版本或被手工改写，界面不应因越界而崩溃。
     */
    public static int normalizeTheme(int theme) {
        if (theme < 0 || theme >= THEME_BACKGROUND_COLORS.length) {
            return DEFAULT_THEME;
        }
        return theme;
    }

    /**
     * 把任意字号档位归一到合法范围。
     */
    public static int normalizeFontLevel(int level) {
        return normalizeLevel(level, FONT_SIZES_SP.length, DEFAULT_FONT_LEVEL);
    }

    /**
     * 把任意行距档位归一到合法范围。
     */
    public static int normalizeLineSpacingLevel(int level) {
        return normalizeLevel(level, LINE_SPACING_MULTIPLIERS.length, DEFAULT_LINE_SPACING_LEVEL);
    }

    /**
     * 按字数估算阅读时长（分钟）。
     *
     * <p>边界约定：空文本或负数返回 0；非空文本至少返回 1 分钟，
     * 避免“1 个字显示约 0 分钟”这种无意义结果。
     */
    public static int estimateReadingMinutes(int characters) {
        if (characters <= 0) {
            return 0;
        }
        int minutes = (int) Math.ceil(characters / (double) CHARACTERS_PER_MINUTE);
        return Math.max(1, minutes);
    }

    /**
     * 按当前档位取字号（sp）。
     */
    public static int fontSizeSp(int fontLevel) {
        return FONT_SIZES_SP[normalizeFontLevel(fontLevel)];
    }

    /**
     * 按当前档位取行距倍数。
     */
    public static float lineSpacingMultiplier(int spacingLevel) {
        return LINE_SPACING_MULTIPLIERS[normalizeLineSpacingLevel(spacingLevel)];
    }

    /**
     * 按主题取背景色。
     */
    public static int backgroundColor(int theme) {
        return THEME_BACKGROUND_COLORS[normalizeTheme(theme)];
    }

    /**
     * 按主题取正文颜色。
     */
    public static int textColor(int theme) {
        return THEME_TEXT_COLORS[normalizeTheme(theme)];
    }

    private static int normalizeLevel(int level, int size, int fallback) {
        if (level < 0 || level >= size) {
            return fallback;
        }
        return level;
    }
}
