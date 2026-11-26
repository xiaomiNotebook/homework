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
import android.content.SharedPreferences;

/**
 * M10 阅读偏好的持久化封装：主题、字号档位、行距档位。
 *
 * <p>职责边界：只负责读写 SharedPreferences，不做任何数值换算；
 * 档位合法性由 {@link ReadingPrefs} 在读取时统一归一化，因此即使偏好文件被写坏也不会崩溃。
 *
 * <p>独立性：使用单独的文件名 {@value #PREFERENCE_NAME}，与设置页的 notes_preferences 分开，
 * 避免影响现有设置项，也便于后续清理。
 */
public final class ReadingPreferenceStore {

    /** 阅读偏好使用的独立偏好文件名。 */
    public static final String PREFERENCE_NAME = "note_reading_preferences";

    private static final String KEY_THEME = "reading_theme";

    private static final String KEY_FONT_LEVEL = "reading_font_level";

    private static final String KEY_LINE_SPACING_LEVEL = "reading_line_spacing_level";

    private final SharedPreferences mPreferences;

    public ReadingPreferenceStore(Context context) {
        mPreferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 读取主题档位，越界值由 {@link ReadingPrefs#normalizeTheme(int)} 归一化。
     */
    public int getTheme() {
        return ReadingPrefs.normalizeTheme(
                mPreferences.getInt(KEY_THEME, ReadingPrefs.DEFAULT_THEME));
    }

    /**
     * 写入主题档位；非法值先归一化再保存，避免把脏数据扩散。
     */
    public void setTheme(int theme) {
        mPreferences.edit().putInt(KEY_THEME, ReadingPrefs.normalizeTheme(theme)).apply();
    }

    public int getFontLevel() {
        return ReadingPrefs.normalizeFontLevel(
                mPreferences.getInt(KEY_FONT_LEVEL, ReadingPrefs.DEFAULT_FONT_LEVEL));
    }

    public void setFontLevel(int level) {
        mPreferences.edit()
                .putInt(KEY_FONT_LEVEL, ReadingPrefs.normalizeFontLevel(level)).apply();
    }

    public int getLineSpacingLevel() {
        return ReadingPrefs.normalizeLineSpacingLevel(
                mPreferences.getInt(KEY_LINE_SPACING_LEVEL,
                        ReadingPrefs.DEFAULT_LINE_SPACING_LEVEL));
    }

    public void setLineSpacingLevel(int level) {
        mPreferences.edit()
                .putInt(KEY_LINE_SPACING_LEVEL, ReadingPrefs.normalizeLineSpacingLevel(level))
                .apply();
    }
}
