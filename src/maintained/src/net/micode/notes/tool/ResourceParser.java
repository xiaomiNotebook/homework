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

import android.content.Context;
import android.preference.PreferenceManager;

import net.micode.notes.R;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * 资源解析器：把"逻辑取值"(颜色编号、字号编号)翻译成具体的 Android 资源 ID(drawable / style)。
 *
 * <p>设计意图：便签的颜色、字号等在数据库或 SharedPreferences 中只保存一个整型编号，
 * 而不是资源 ID(资源 ID 会随编译变化)。渲染时再由本类把编号映射回真正的资源。
 * 各数组的"下标即编号语义"，因此数组顺序不可随意调整。</p>
 *
 * <p>内部四组静态资源表分别服务：编辑页背景(NoteBgResources)、列表项背景(NoteItemBgResources)、
 * 桌面小部件背景(WidgetBgResources)与文字外观(TextAppearanceResources)。</p>
 *
 * <p>缺陷提示：
 * 1) 除 TextAppearanceResources 外，各 getXxx(int id) 均未做下标越界检查，
 *    传入非法编号会抛 ArrayIndexOutOfBoundsException；
 * 2) TextAppearanceResources.getTexAppearanceResource 越界时返回的是"字号编号常量"
 *    BG_DEFAULT_FONT_SIZE(=1)，但该方法返回的应是 style 资源 ID，二者语义不一致(应为 R.style.*)。</p>
 */
public class ResourceParser {

    // —— 便签背景色的逻辑编号(=各颜色资源数组的下标) ——
    public static final int YELLOW           = 0;
    public static final int BLUE             = 1;
    public static final int WHITE            = 2;
    public static final int GREEN            = 3;
    public static final int RED              = 4;

    // 新建便签的默认背景色编号(黄色)
    public static final int BG_DEFAULT_COLOR = YELLOW;

    // —— 文字字号(super/small/large/normal 对应下标的约定见各 style 定义) ——
    public static final int TEXT_SMALL       = 0;
    public static final int TEXT_MEDIUM      = 1;
    public static final int TEXT_LARGE       = 2;
    public static final int TEXT_SUPER       = 3;

    // 默认字号编号(中号)。注意：该常量同时被 getTexAppearanceResource 当作"兜底返回值"误用，见类注释缺陷 2
    public static final int BG_DEFAULT_FONT_SIZE = TEXT_MEDIUM;

    /**
     * 编辑页背景资源表：按下标(颜色编号)提供便签编辑界面的主背景与标题栏背景。
     */
    public static class NoteBgResources {
        private final static int [] BG_EDIT_RESOURCES = new int [] {
            R.drawable.edit_yellow,
            R.drawable.edit_blue,
            R.drawable.edit_white,
            R.drawable.edit_green,
            R.drawable.edit_red
        };

        private final static int [] BG_EDIT_TITLE_RESOURCES = new int [] {
            R.drawable.edit_title_yellow,
            R.drawable.edit_title_blue,
            R.drawable.edit_title_white,
            R.drawable.edit_title_green,
            R.drawable.edit_title_red
        };

        /** 取便签编辑区主背景资源；id 为颜色编号(未做越界校验)。 */
        public static int getNoteBgResource(int id) {
            return BG_EDIT_RESOURCES[id];
        }

        /** 取便签编辑区标题栏背景资源；id 为颜色编号。 */
        public static int getNoteTitleBgResource(int id) {
            return BG_EDIT_TITLE_RESOURCES[id];
        }
    }

    /**
     * 计算新建便签的默认背景色编号。
     * 逻辑：若用户在设置中开启了"每次随机背景色"开关，则返回 [0, 颜色数) 的随机编号；
     * 否则返回固定的默认色(黄)。
     * 注意：由于是随机，同一便签在不同时机多次调用本方法可能得到不同结果——调用方应只取一次并落库。
     */
    public static int getDefaultBgId(Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                NotesPreferenceActivity.PREFERENCE_SET_BG_COLOR_KEY, false)) {
            return (int) (Math.random() * NoteBgResources.BG_EDIT_RESOURCES.length);
        } else {
            return BG_DEFAULT_COLOR;
        }
    }

    /**
     * 便签列表项背景资源表：按颜色编号提供列表项的四种形态背景(首项/中间/末项/独立项)，
     * 使同一文件夹内的相邻便签在视觉上"连成一体"；另有固定的文件夹列表项背景。
     */
    public static class NoteItemBgResources {
        // 列表项四种形态(上/中/下/独立)对应的 drawable，下标=颜色编号
        private final static int [] BG_FIRST_RESOURCES = new int [] {
            R.drawable.list_yellow_up,
            R.drawable.list_blue_up,
            R.drawable.list_white_up,
            R.drawable.list_green_up,
            R.drawable.list_red_up
        };

        private final static int [] BG_NORMAL_RESOURCES = new int [] {
            R.drawable.list_yellow_middle,
            R.drawable.list_blue_middle,
            R.drawable.list_white_middle,
            R.drawable.list_green_middle,
            R.drawable.list_red_middle
        };

        private final static int [] BG_LAST_RESOURCES = new int [] {
            R.drawable.list_yellow_down,
            R.drawable.list_blue_down,
            R.drawable.list_white_down,
            R.drawable.list_green_down,
            R.drawable.list_red_down,
        };

        private final static int [] BG_SINGLE_RESOURCES = new int [] {
            R.drawable.list_yellow_single,
            R.drawable.list_blue_single,
            R.drawable.list_white_single,
            R.drawable.list_green_single,
            R.drawable.list_red_single
        };

        /** 列表项为"组首项"时的背景(上圆角)；id 为颜色编号。 */
        public static int getNoteBgFirstRes(int id) {
            return BG_FIRST_RESOURCES[id];
        }

        /** 列表项为"组末项"时的背景(下圆角)。 */
        public static int getNoteBgLastRes(int id) {
            return BG_LAST_RESOURCES[id];
        }

        /** 列表项为"组内单独一项"时的背景(全圆角)。 */
        public static int getNoteBgSingleRes(int id) {
            return BG_SINGLE_RESOURCES[id];
        }

        /** 列表项为"组中间项"时的背景(无圆角)。 */
        public static int getNoteBgNormalRes(int id) {
            return BG_NORMAL_RESOURCES[id];
        }

        /** 文件夹列表项背景(固定，不随颜色变化)。 */
        public static int getFolderBgRes() {
            return R.drawable.list_folder;
        }
    }

    /**
     * 桌面小部件背景资源表：按颜色编号分别提供 2x 与 4x 两种尺寸小部件的背景。
     */
    public static class WidgetBgResources {
        // 2x 尺寸小部件背景，下标=颜色编号
        private final static int [] BG_2X_RESOURCES = new int [] {
            R.drawable.widget_2x_yellow,
            R.drawable.widget_2x_blue,
            R.drawable.widget_2x_white,
            R.drawable.widget_2x_green,
            R.drawable.widget_2x_red,
        };

        /** 取 2x 小部件背景；id 为颜色编号。 */
        public static int getWidget2xBgResource(int id) {
            return BG_2X_RESOURCES[id];
        }

        private final static int [] BG_4X_RESOURCES = new int [] {
            R.drawable.widget_4x_yellow,
            R.drawable.widget_4x_blue,
            R.drawable.widget_4x_white,
            R.drawable.widget_4x_green,
            R.drawable.widget_4x_red
        };

        /** 取 4x 小部件背景；id 为颜色编号。 */
        public static int getWidget4xBgResource(int id) {
            return BG_4X_RESOURCES[id];
        }
    }

    /**
     * 文字外观(style)资源表：按字号编号提供文本便签的字体样式。
     */
    public static class TextAppearanceResources {
        private final static int [] TEXTAPPEARANCE_RESOURCES = new int [] {
            R.style.TextAppearanceNormal,
            R.style.TextAppearanceMedium,
            R.style.TextAppearanceLarge,
            R.style.TextAppearanceSuper
        };

        /**
         * 取文字外观 style 资源；id 为字号编号。
         * 兼容处理：历史版本曾误把"资源 ID"存进 SharedPreferences，故传入的 id 可能大于数组长度，越界时返回兜底值。
         * 缺陷提示：兜底返回的 BG_DEFAULT_FONT_SIZE(=1)是"字号编号"而非 style 资源 ID，语义错误
         * (应返回 R.style.TextAppearanceMedium 之类的资源 ID)；且只校验了上溢(>=length)，未校验负数下溢。
         */
        public static int getTexAppearanceResource(int id) {
            /**
             * HACKME: Fix bug of store the resource id in shared preference.
             * The id may larger than the length of resources, in this case,
             * return the {@link ResourceParser#BG_DEFAULT_FONT_SIZE}
             */
            if (id >= TEXTAPPEARANCE_RESOURCES.length) {
                return BG_DEFAULT_FONT_SIZE;
            }
            return TEXTAPPEARANCE_RESOURCES[id];
        }

        /** 可用的文字外观数量(字号档位数)。 */
        public static int getResourcesSize() {
            return TEXTAPPEARANCE_RESOURCES.length;
        }
    }
}
