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

package net.micode.notes.ui;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.tool.ReadingPrefs;
import net.micode.notes.tool.ReadingPreferenceStore;

/**
 * 沉浸式专注阅读模式（M10）：只读展示便签正文，并提供主题、字号、行距三类显示偏好。
 *
 * <p>职责边界：本类不修改便签内容，只读取正文并按偏好渲染；偏好保存到独立的
 * {@link ReadingPreferenceStore}，不触碰便签数据，因此退出阅读模式后原文案完全不变。
 *
 * <p>生命周期：进入时读取一次正文（只读快照），阅读过程中不再回查数据库；
 * 退出（返回键或返回按钮）即结束本页面，回到原编辑页面。
 *
 * <p>边界条件：便签 id 缺失或正文为空时显示“该便签暂无内容”，阅读时长显示为空便签；
 * 偏好档位越界时由 {@link ReadingPrefs} 归一化，不会导致崩溃。
 */
public class NoteReadingActivity extends Activity implements OnClickListener {

    /** Intent 中传递便签 id 的键，值为 long。 */
    public static final String EXTRA_NOTE_ID = "note_id";

    private LinearLayout mRootLayout;

    private TextView mContentView;

    private TextView mReadingTimeView;

    private Button mThemeButton;

    private Button mFontButton;

    private Button mSpacingButton;

    private ReadingPreferenceStore mStore;

    private String mContent = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_reading);

        mRootLayout = (LinearLayout) findViewById(R.id.layout_reading_root);
        mContentView = (TextView) findViewById(R.id.tv_reading_content);
        mReadingTimeView = (TextView) findViewById(R.id.tv_reading_time);
        mThemeButton = (Button) findViewById(R.id.btn_reading_theme);
        mFontButton = (Button) findViewById(R.id.btn_reading_font);
        mSpacingButton = (Button) findViewById(R.id.btn_reading_spacing);
        findViewById(R.id.btn_reading_back).setOnClickListener(this);
        mThemeButton.setOnClickListener(this);
        mFontButton.setOnClickListener(this);
        mSpacingButton.setOnClickListener(this);

        mStore = new ReadingPreferenceStore(this);
        mContent = loadNoteContent(getIntent().getLongExtra(EXTRA_NOTE_ID, -1));
        mContentView.setText(TextUtils.isEmpty(mContent) ? getString(R.string.reading_empty_note)
                : mContent);

        int minutes = ReadingPrefs.estimateReadingMinutes(mContent.length());
        mReadingTimeView.setText(minutes == 0 ? "" : getString(R.string.reading_time_cost, minutes));
        setTitle(R.string.reading_title);

        applyPreferences();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_reading_back:
                finish();
                break;
            case R.id.btn_reading_theme:
                mStore.setTheme((mStore.getTheme() + 1) % ReadingPrefs.THEME_BACKGROUND_COLORS.length);
                applyPreferences();
                break;
            case R.id.btn_reading_font:
                mStore.setFontLevel((mStore.getFontLevel() + 1) % ReadingPrefs.FONT_SIZES_SP.length);
                applyPreferences();
                break;
            case R.id.btn_reading_spacing:
                mStore.setLineSpacingLevel((mStore.getLineSpacingLevel() + 1)
                        % ReadingPrefs.LINE_SPACING_MULTIPLIERS.length);
                applyPreferences();
                break;
            default:
                break;
        }
    }

    /**
     * 按当前偏好刷新背景、文字颜色、字号、行距与三个按钮的文案。
     */
    private void applyPreferences() {
        int theme = mStore.getTheme();
        int background = ReadingPrefs.backgroundColor(theme);
        int textColor = ReadingPrefs.textColor(theme);

        mRootLayout.setBackgroundColor(background);
        mContentView.setTextColor(textColor);
        mContentView.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                ReadingPrefs.fontSizeSp(mStore.getFontLevel()));
        mContentView.setLineSpacing(0f,
                ReadingPrefs.lineSpacingMultiplier(mStore.getLineSpacingLevel()));
        mReadingTimeView.setTextColor(textColor);
        mThemeButton.setTextColor(textColor);
        mFontButton.setTextColor(textColor);
        mSpacingButton.setTextColor(textColor);
        ((Button) findViewById(R.id.btn_reading_back)).setTextColor(textColor);

        mThemeButton.setText(themeLabel(theme));
        mFontButton.setText(fontLabel(mStore.getFontLevel()));
        mSpacingButton.setText(spacingLabel(mStore.getLineSpacingLevel()));
    }

    /**
     * 只读取文本正文；便签不存在、不是文本便签或内容为空时返回空串。
     *
     * <p>使用只读查询，不修改任何数据；游标在 finally 中关闭。
     */
    private String loadNoteContent(long noteId) {
        if (noteId <= 0) {
            return "";
        }
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(Notes.CONTENT_DATA_URI,
                    new String[] { DataColumns.CONTENT },
                    DataColumns.NOTE_ID + "=? AND " + DataColumns.MIME_TYPE + "=?",
                    new String[] { String.valueOf(noteId), TextNote.CONTENT_ITEM_TYPE },
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                String content = cursor.getString(0);
                return content == null ? "" : content;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return "";
    }

    private int themeLabel(int theme) {
        switch (theme) {
            case ReadingPrefs.THEME_EYE_CARE:
                return R.string.reading_theme_eye_care;
            case ReadingPrefs.THEME_DARK:
                return R.string.reading_theme_dark;
            default:
                return R.string.reading_theme_light;
        }
    }

    private int fontLabel(int level) {
        switch (level) {
            case 0:
                return R.string.reading_font_small;
            case 2:
                return R.string.reading_font_large;
            default:
                return R.string.reading_font_normal;
        }
    }

    private int spacingLabel(int level) {
        switch (level) {
            case 0:
                return R.string.reading_spacing_tight;
            case 2:
                return R.string.reading_spacing_loose;
            default:
                return R.string.reading_spacing_normal;
        }
    }
}
