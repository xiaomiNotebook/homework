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
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.model.WorkingNote.NoteSettingChangedListener;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.tool.ResourceParser.TextAppearanceResources;
import net.micode.notes.ui.DateTimePickerDialog.OnDateTimeSetListener;
import net.micode.notes.ui.NoteEditText.OnTextViewChangeListener;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 便签编辑页，应用中最核心的界面。主要职责：
 * 1) 根据 Intent 的 action 决定是打开已有便签（ACTION_VIEW）还是新建 / 编辑便签（
 *    ACTION_INSERT_OR_EDIT）。入口包括便签列表、搜索结果、桌面小部件、桌面快捷方式和通话记
 *    录；
 * 2) 在普通文本模式与清单模式之间切换；
 * 3) 处理背景色、字号、提醒、分享、发送到桌面、删除等菜单操作；
 * 4) 在 onPause 和返回键时自动保存。
 *
 * 同时实现了三个接口：OnClickListener（处理背景色 / 字号按钮点击）、
 * NoteSettingChangedListener（接收 WorkingNote 的设置变化回调）、OnTextViewChangeListener（
 * 接收清单条目的回车 / 删除回调）。
 * 数据读写主要借助 WorkingNote 完成，本类只负责界面和流程控制。
 */
public class NoteEditActivity extends Activity implements OnClickListener,
        NoteSettingChangedListener, OnTextViewChangeListener {
    // 标题栏中几个控件的持有者（ViewHolder），集中保存引用，避免重复调用 findViewById。四个
    // 字段依次是：修改时间、提醒图标、提醒时间、背景色按钮。
    private class HeadViewHolder {
        public TextView tvModified;

        public ImageView ivAlertIcon;

        public TextView tvAlertDate;

        public ImageView ibSetBgColor;
    }

    // 背景色选择：颜色按钮的 id → 颜色编号。
    private static final Map<Integer, Integer> sBgSelectorBtnsMap = new HashMap<Integer, Integer>();
    static {
        sBgSelectorBtnsMap.put(R.id.iv_bg_yellow, ResourceParser.YELLOW);
        sBgSelectorBtnsMap.put(R.id.iv_bg_red, ResourceParser.RED);
        sBgSelectorBtnsMap.put(R.id.iv_bg_blue, ResourceParser.BLUE);
        sBgSelectorBtnsMap.put(R.id.iv_bg_green, ResourceParser.GREEN);
        sBgSelectorBtnsMap.put(R.id.iv_bg_white, ResourceParser.WHITE);
    }

    // 背景色选择：颜色编号 → “当前选中”勾选标记的控件 id。
    private static final Map<Integer, Integer> sBgSelectorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sBgSelectorSelectionMap.put(ResourceParser.YELLOW, R.id.iv_bg_yellow_select);
        sBgSelectorSelectionMap.put(ResourceParser.RED, R.id.iv_bg_red_select);
        sBgSelectorSelectionMap.put(ResourceParser.BLUE, R.id.iv_bg_blue_select);
        sBgSelectorSelectionMap.put(ResourceParser.GREEN, R.id.iv_bg_green_select);
        sBgSelectorSelectionMap.put(ResourceParser.WHITE, R.id.iv_bg_white_select);
    }

    // 字号选择：字号按钮的 id → 字号编号。
    private static final Map<Integer, Integer> sFontSizeBtnsMap = new HashMap<Integer, Integer>();
    static {
        sFontSizeBtnsMap.put(R.id.ll_font_large, ResourceParser.TEXT_LARGE);
        sFontSizeBtnsMap.put(R.id.ll_font_small, ResourceParser.TEXT_SMALL);
        sFontSizeBtnsMap.put(R.id.ll_font_normal, ResourceParser.TEXT_MEDIUM);
        sFontSizeBtnsMap.put(R.id.ll_font_super, ResourceParser.TEXT_SUPER);
    }

    // 字号选择：字号编号 → “当前选中”标记的控件 id。
    private static final Map<Integer, Integer> sFontSelectorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_LARGE, R.id.iv_large_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SMALL, R.id.iv_small_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_MEDIUM, R.id.iv_medium_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SUPER, R.id.iv_super_select);
    }

    private static final String TAG = "NoteEditActivity";

    // 以下是界面控件的引用，在 initResources() 中统一获取。
    private HeadViewHolder mNoteHeaderHolder;

    private View mHeadViewPanel;

    private View mNoteBgColorSelector;

    private View mFontSizeSelector;

    // 普通文本模式下的整块输入框。
    private EditText mNoteEditor;

    private View mNoteEditorPanel;

    // 当前正在编辑的便签数据模型，几乎所有数据操作都通过它完成。
    private WorkingNote mWorkingNote;

    // 字号偏好保存在 SharedPreferences 中，对所有便签全局生效，而不是每条便签单独设置。
    private SharedPreferences mSharedPrefs;
    private int mFontSizeId;

    private static final String PREFERENCE_FONT_SIZE = "pref_font_size";

    // 桌面快捷方式的名称最多取正文的前 10 个字符。
    private static final int SHORTCUT_ICON_TITLE_MAX_LEN = 10;

    // 清单模式保存时使用的前缀标记：√ 表示已完成，□ 表示未完成。数据库里的正文直接带着这些
    // 符号（形如“√ 买牛奶”），读取时再解析回复选框状态。
    public static final String TAG_CHECKED = String.valueOf('\u221A');
    public static final String TAG_UNCHECKED = String.valueOf('\u25A1');

    // 清单模式下的条目容器，每个条目是一个“复选框 + NoteEditText”。
    private LinearLayout mEditTextList;

    // 从搜索结果进入时的搜索关键词，用于在正文中高亮显示；其他入口为空串。
    private String mUserQuery;
    private Pattern mPattern;

    /**
     * 创建界面。savedInstanceState 为空说明是全新启动，需要根据 Intent 初始化数据（
     * initActivityState），失败就直接关闭页面；不为空说明是被系统回收后重建，数据恢复交给
     * onRestoreInstanceState。最后绑定控件。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.note_edit);

        if (savedInstanceState == null && !initActivityState(getIntent())) {
            finish();
            return;
        }
        initResources();
    }

    /**
     * Current activity may be killed when the memory is low. Once it is killed, for another time
     * user load this activity, we should restore the former state
     */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // 被回收前只保存了便签 id，所以这里构造一个 ACTION_VIEW 的 Intent，按 id 重新加载便
        // 签。
        if (savedInstanceState != null && savedInstanceState.containsKey(Intent.EXTRA_UID)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.putExtra(Intent.EXTRA_UID, savedInstanceState.getLong(Intent.EXTRA_UID));
            if (!initActivityState(intent)) {
                finish();
                return;
            }
            Log.d(TAG, "Restoring from killed activity");
        }
    }

    /**
     * 根据 Intent 初始化 mWorkingNote，返回 false 表示初始化失败（调用方应当关闭页面）。分
     * 三种情况：
     * 1) ACTION_VIEW：打开已有便签，id 来自 EXTRA_UID。如果是从搜索结果进来，则 id 和关
     *    键词来自搜索相关的 extra。便签不存在时跳回列表页并提示；
     * 2) ACTION_INSERT_OR_EDIT：新建便签，可能带有文件夹、小部件、背景色信息。如果是通话
     *    记录（有电话号码和通话时间），先查找是否已存在对应的通话便签，有就打开，没有就新建
     *    并转为通话便签；
     * 3) 其他 action：不支持，直接关闭。
     */
    private boolean initActivityState(Intent intent) {
        /**
         * If the user specified the {@link Intent#ACTION_VIEW} but not provided with id,
         * then jump to the NotesListActivity
         */
        mWorkingNote = null;
        if (TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
            long noteId = intent.getLongExtra(Intent.EXTRA_UID, 0);
            mUserQuery = "";

            /**
             * Starting from the searched result
             */
            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY)) {
                noteId = Long.parseLong(intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));
                mUserQuery = intent.getStringExtra(SearchManager.USER_QUERY);
            }

            // 便签不存在或不可见（例如已经被删除）：跳回列表页并给出提示。
            if (!DataUtils.visibleInNoteDatabase(getContentResolver(), noteId, Notes.TYPE_NOTE)) {
                Intent jump = new Intent(this, NotesListActivity.class);
                startActivity(jump);
                showToast(R.string.error_note_not_exist);
                finish();
                return false;
            } else {
                mWorkingNote = WorkingNote.load(this, noteId);
                if (mWorkingNote == null) {
                    Log.e(TAG, "load note failed with note id" + noteId);
                    finish();
                    return false;
                }
            }
            // 打开已有便签时默认隐藏软键盘，避免一进来就遮挡内容。
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        } else if(TextUtils.equals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction())) {
            // New note
            long folderId = intent.getLongExtra(Notes.INTENT_EXTRA_FOLDER_ID, 0);
            int widgetId = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            int widgetType = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_TYPE,
                    Notes.TYPE_WIDGET_INVALIDE);
            int bgResId = intent.getIntExtra(Notes.INTENT_EXTRA_BACKGROUND_ID,
                    ResourceParser.getDefaultBgId(this));

            // Parse call-record note
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            long callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0);
            if (callDate != 0 && phoneNumber != null) {
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.w(TAG, "The call record number is null");
                }
                long noteId = 0;
                if ((noteId = DataUtils.getNoteIdByPhoneNumberAndCallDate(getContentResolver(),
                        phoneNumber, callDate)) > 0) {
                    mWorkingNote = WorkingNote.load(this, noteId);
                    if (mWorkingNote == null) {
                        Log.e(TAG, "load call note failed with note id" + noteId);
                        finish();
                        return false;
                    }
                } else {
                    mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId,
                            widgetType, bgResId);
                    mWorkingNote.convertToCallNote(phoneNumber, callDate);
                }
            } else {
                mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType,
                        bgResId);
            }

            // 新建便签时直接弹出软键盘，方便用户马上输入。
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } else {
            Log.e(TAG, "Intent not specified action, should not support");
            finish();
            return false;
        }
        // 注册监听，之后 WorkingNote 的背景色、提醒、清单模式等变化都会回调到本 Activity。
        mWorkingNote.setOnSettingStatusChangedListener(this);
        return true;
    }

    // 每次回到前台都重新渲染界面：首次进入和从其他页面返回时都会走到这里。
    @Override
    protected void onResume() {
        super.onResume();
        initNoteScreen();
    }

    /**
     * 把 mWorkingNote 的数据渲染到界面上：
     * 1) 设置字号；
     * 2) 清单模式则重建条目列表，普通模式则显示正文（带搜索高亮）并把光标移到末尾；
     * 3) 先隐藏所有背景色的“选中”标记，再设置标题栏和编辑区背景；
     * 4) 显示最后修改时间，并刷新提醒信息。
     */
    private void initNoteScreen() {
        mNoteEditor.setTextAppearance(this, TextAppearanceResources
                .getTexAppearanceResource(mFontSizeId));
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mWorkingNote.getContent());
        } else {
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mNoteEditor.setSelection(mNoteEditor.getText().length());
        }
        for (Integer id : sBgSelectorSelectionMap.keySet()) {
            findViewById(sBgSelectorSelectionMap.get(id)).setVisibility(View.GONE);
        }
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());

        mNoteHeaderHolder.tvModified.setText(DateUtils.formatDateTime(this,
                mWorkingNote.getModifiedDate(), DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_SHOW_YEAR));

        /**
         * TODO: Add the menu for setting alert. Currently disable it because the DateTimePicker
         * is not ready
         */
        showAlertHeader();
    }

    /**
     * 刷新标题栏中的提醒显示：已设置提醒时，如果提醒时间已过则显示“已过期”，否则显示相对时
     * 间（例如“30 分钟后”）；没有设置提醒则隐藏图标和文字。
     */
    private void showAlertHeader() {
        if (mWorkingNote.hasClockAlert()) {
            long time = System.currentTimeMillis();
            if (time > mWorkingNote.getAlertDate()) {
                mNoteHeaderHolder.tvAlertDate.setText(R.string.note_alert_expired);
            } else {
                mNoteHeaderHolder.tvAlertDate.setText(DateUtils.getRelativeTimeSpanString(
                        mWorkingNote.getAlertDate(), time, DateUtils.MINUTE_IN_MILLIS));
            }
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.VISIBLE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.VISIBLE);
        } else {
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.GONE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.GONE);
        };
    }

    // Activity 已经存在又收到新的 Intent 时（例如点击小部件，启动标志为 SINGLE_TOP）会走到
    // 这里，需要按新 Intent 重新初始化数据。
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        initActivityState(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        /**
         * For new note without note id, we should firstly save it to
         * generate a id. If the editing note is not worth saving, there
         * is no id which is equivalent to create new note
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        // 只保存便签 id，恢复时按 id 从数据库重新加载（见 onRestoreInstanceState）。
        outState.putLong(Intent.EXTRA_UID, mWorkingNote.getNoteId());
        Log.d(TAG, "Save working note id: " + mWorkingNote.getNoteId() + " onSaveInstanceState");
    }

    /**
     * 触摸事件分发的第一道关口。如果背景色面板或字号面板正在显示，而这次触摸落在面板之外，
     * 就只关闭面板并吞掉这次事件（返回 true），避免用户点空白处关面板的同时误触到下面的输入
     * 框。其余情况按默认流程分发。
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mNoteBgColorSelector, ev)) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        }

        if (mFontSizeSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mFontSizeSelector, ev)) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    // 判断触摸点是否落在某个控件范围内：用控件在屏幕上的位置和宽高，与触摸点坐标比较，在范
    // 围内返回 true。
    private boolean inRangeOfView(View view, MotionEvent ev) {
        int []location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        if (ev.getX() < x
                || ev.getX() > (x + view.getWidth())
                || ev.getY() < y
                || ev.getY() > (y + view.getHeight())) {
            return false;
        }
        return true;
    }

    // 绑定控件、设置点击监听，并读取字号偏好。只在 onCreate 中调用一次。
    private void initResources() {
        mHeadViewPanel = findViewById(R.id.note_title);
        mNoteHeaderHolder = new HeadViewHolder();
        mNoteHeaderHolder.tvModified = (TextView) findViewById(R.id.tv_modified_date);
        mNoteHeaderHolder.ivAlertIcon = (ImageView) findViewById(R.id.iv_alert_icon);
        mNoteHeaderHolder.tvAlertDate = (TextView) findViewById(R.id.tv_alert_date);
        mNoteHeaderHolder.ibSetBgColor = (ImageView) findViewById(R.id.btn_set_bg_color);
        mNoteHeaderHolder.ibSetBgColor.setOnClickListener(this);
        mNoteEditor = (EditText) findViewById(R.id.note_edit_view);
        mNoteEditorPanel = findViewById(R.id.sv_note_edit);
        mNoteBgColorSelector = findViewById(R.id.note_bg_color_selector);
        for (int id : sBgSelectorBtnsMap.keySet()) {
            ImageView iv = (ImageView) findViewById(id);
            iv.setOnClickListener(this);
        }

        mFontSizeSelector = findViewById(R.id.font_size_selector);
        for (int id : sFontSizeBtnsMap.keySet()) {
            View view = findViewById(id);
            view.setOnClickListener(this);
        };
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mFontSizeId = mSharedPrefs.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE);
        /**
         * HACKME: Fix bug of store the resource id in shared preference.
         * The id may larger than the length of resources, in this case,
         * return the {@link ResourceParser#BG_DEFAULT_FONT_SIZE}
         */
        if(mFontSizeId >= TextAppearanceResources.getResourcesSize()) {
            mFontSizeId = ResourceParser.BG_DEFAULT_FONT_SIZE;
        }
        mEditTextList = (LinearLayout) findViewById(R.id.note_edit_list);
    }

    // 离开界面（切到后台、按返回、被其他页面覆盖）时自动保存，用户不需要手动点保存；随后收
    // 起可能还开着的选择面板。
    @Override
    protected void onPause() {
        super.onPause();
        if(saveNote()) {
            Log.d(TAG, "Note data was saved with length:" + mWorkingNote.getContent().length());
        }
        clearSettingState();
    }

    /**
     * 通知桌面小部件刷新：根据小部件类型选择 2x 或 4x 的 Provider，发送
     * ACTION_APPWIDGET_UPDATE 广播并带上 widget id；类型不支持则记录错误并返回。同时通过
     * setResult 把结果交给调用方。
     */
    private void updateWidget() {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {
                mWorkingNote.getWidgetId()
        });

        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    /**
     * 统一处理本页的按钮点击，按 id 分三类：
     * 1) 标题栏的“背景色”按钮：展开选色面板，并显示当前颜色的选中标记；
     * 2) 某个颜色按钮：隐藏旧标记、设置新颜色（界面刷新由 onBackgroundColorChanged 回调
     *    完成），并收起面板；
     * 3) 某个字号按钮：把偏好保存到 SharedPreferences 并更新选中标记。清单模式下要先取回
     *    文本再重建列表，字号才会生效；普通模式则直接修改输入框样式。
     */
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_set_bg_color) {
            mNoteBgColorSelector.setVisibility(View.VISIBLE);
            /**
             * 注：下一行续行处有一个多余的负号（-View.VISIBLE）。View.VISIBLE 的值是 0，-0
             * 仍为 0，所以运行结果没有问题，但属于代码瑕疵。标注阶段不修改，可记入 Issue。
             */
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                    -                    View.VISIBLE);
        } else if (sBgSelectorBtnsMap.containsKey(id)) {
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                    View.GONE);
            mWorkingNote.setBgColorId(sBgSelectorBtnsMap.get(id));
            mNoteBgColorSelector.setVisibility(View.GONE);
        } else if (sFontSizeBtnsMap.containsKey(id)) {
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.GONE);
            mFontSizeId = sFontSizeBtnsMap.get(id);
            mSharedPrefs.edit().putInt(PREFERENCE_FONT_SIZE, mFontSizeId).commit();
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
            if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                getWorkingText();
                switchToListMode(mWorkingNote.getContent());
            } else {
                mNoteEditor.setTextAppearance(this,
                        TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
            }
            mFontSizeSelector.setVisibility(View.GONE);
        }
    }

    // 返回键：优先收起已展开的面板（clearSettingState 返回 true 表示收起了面板，本次返回键
    // 到此为止）；否则先保存，再交给系统正常返回。
    @Override
    public void onBackPressed() {
        if(clearSettingState()) {
            return;
        }

        saveNote();
        super.onBackPressed();
    }

    // 收起背景色 / 字号选择面板，返回 true 表示确实收起了某个面板。
    private boolean clearSettingState() {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        } else if (mFontSizeSelector.getVisibility() == View.VISIBLE) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    // WorkingNote 回调：背景色变了。显示新颜色的选中标记，并刷新编辑区和标题栏的背景。
    public void onBackgroundColorChanged() {
        findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                View.VISIBLE);
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
    }

    /**
     * 每次菜单即将显示时动态调整：
     * 1) 页面正在关闭时不必再构建菜单；
     * 2) 通话记录便签使用专用菜单；
     * 3) 清单模式下，“清单模式”菜单项显示为“普通模式”，反之亦然；
     * 4) 已设置提醒时隐藏“设置提醒”、显示“删除提醒”，没设置时则相反。
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isFinishing()) {
            return true;
        }
        clearSettingState();
        menu.clear();
        if (mWorkingNote.getFolderId() == Notes.ID_CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_note_edit, menu);
        } else {
            getMenuInflater().inflate(R.menu.note_edit, menu);
        }
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_normal_mode);
        } else {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_list_mode);
        }
        if (mWorkingNote.hasClockAlert()) {
            menu.findItem(R.id.menu_alert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_delete_remind).setVisible(false);
        }
        return true;
    }

    // 菜单项点击分发：新建便签、删除（先弹确认框）、字号、清单 / 普通模式切换、分享、发送到
    // 桌面、设置 / 取消提醒。
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_new_note:
                createNewNote();
                break;
            case R.id.menu_delete:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.alert_title_delete));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(getString(R.string.alert_message_delete_note));
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                deleteCurrentNote();
                                finish();
                            }
                        });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
                break;
            case R.id.menu_font_size:
                mFontSizeSelector.setVisibility(View.VISIBLE);
                findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
                break;
            // 在 0（普通）和 MODE_CHECK_LIST 之间切换，真正的界面转换在
            // onCheckListModeChanged 回调里完成。
            case R.id.menu_list_mode:
                mWorkingNote.setCheckListMode(mWorkingNote.getCheckListMode() == 0 ?
                        TextNote.MODE_CHECK_LIST : 0);
                break;
            case R.id.menu_reading_mode:
                openReadingMode();
                break;
            case R.id.menu_share:
                getWorkingText();
                sendTo(this, mWorkingNote.getContent());
                break;
            case R.id.menu_send_to_desktop:
                sendToDesktop();
                break;
            case R.id.menu_alert:
                setReminder();
                break;
            case R.id.menu_delete_remind:
                mWorkingNote.setAlertDate(0, false);
                break;
            default:
                break;
        }
        return true;
    }

    // 弹出日期时间选择对话框，用户确认后设置提醒；对话框的初始时间为当前时间。
    private void setReminder() {
        DateTimePickerDialog d = new DateTimePickerDialog(this, System.currentTimeMillis());
        d.setOnDateTimeSetListener(new OnDateTimeSetListener() {
            public void OnDateTimeSet(AlertDialog dialog, long date) {
                mWorkingNote.setAlertDate(date	, true);
            }
        });
        d.show();
    }

    /**
     * Share note to apps that support {@link Intent#ACTION_SEND} action
     * and {@text/plain} type
     */
    private void sendTo(Context context, String info) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, info);
        intent.setType("text/plain");
        context.startActivity(intent);
    }

    // 基于当前文件夹新建便签：先保存当前内容，再关闭本页并启动一个全新的编辑页。选择重新启
    // 动而不是原地清空，是为了避免旧状态残留。
    private void createNewNote() {
        // Firstly, save current editing notes
        saveNote();

        // For safety, start a new NoteEditActivity
        finish();
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mWorkingNote.getFolderId());
        startActivity(intent);
    }

    /**
     * 删除当前便签，只有便签已入库时才需要处理数据库：
     * 1) 没有开启同步：直接批量删除；
     * 2) 开启了同步：不直接删除，而是移动到回收站文件夹，这样同步模块才能感知并处理这次
     *    删除。
     * 最后把 WorkingNote 标记为已删除，防止页面退出时又被重新保存。
     */
    private void deleteCurrentNote() {
        if (mWorkingNote.existInDatabase()) {
            HashSet<Long> ids = new HashSet<Long>();
            long id = mWorkingNote.getNoteId();
            if (id != Notes.ID_ROOT_FOLDER) {
                ids.add(id);
            } else {
                Log.d(TAG, "Wrong note id, should not happen");
            }
            if (!isSyncMode()) {
                if (!DataUtils.batchDeleteNotes(getContentResolver(), ids)) {
                    Log.e(TAG, "Delete Note error");
                }
            } else {
                if (!DataUtils.batchMoveToFolder(getContentResolver(), ids, Notes.ID_TRASH_FOLER)) {
                    Log.e(TAG, "Move notes to trash folder error, should not happens");
                }
            }
        }
        mWorkingNote.markDeleted(true);
    }

    // 以“是否配置了同步账号”作为是否开启同步的判断依据。
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    /**
     * WorkingNote 回调：提醒设置发生变化。闹钟的实际登记在这里完成：通过 AlarmManager，以
     * RTC_WAKEUP 方式在指定时间向 AlarmReceiver 发送广播，Intent 的 data 携带便签 URI；取消
     * 时用同样的 PendingIntent 调用 cancel。
     * 新便签没有 id 就无法绑定闹钟，所以要先保存；如果内容为空保存不了，就提示用户先输入内
     * 容。
     */
    public void onClockAlertChanged(long date, boolean set) {
        /**
         * User could set clock to an unsaved note, so before setting the
         * alert clock, we should save the note first
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        if (mWorkingNote.getNoteId() > 0) {
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mWorkingNote.getNoteId()));
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
            AlarmManager alarmManager = ((AlarmManager) getSystemService(ALARM_SERVICE));
            showAlertHeader();
            if(!set) {
                alarmManager.cancel(pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, date, pendingIntent);
            }
        } else {
            /**
             * There is the condition that user has input nothing (the note is
             * not worthy saving), we have no note id, remind the user that he
             * should input something
             */
            Log.e(TAG, "Clock alert setting error");
            showToast(R.string.error_note_empty_for_clock);
        }
    }

    // WorkingNote 回调：便签内容发生变化，需要刷新桌面小部件。
    public void onWidgetChanged() {
        updateWidget();
    }

    /**
     * OnTextViewChangeListener 回调：清单模式下，用户在某个条目的行首按退格，表示要把这一条
     * 合并到上一条。步骤：
     * 1) 只剩一个条目时不处理；
     * 2) 把后面条目的序号整体前移一位；
     * 3) 移除当前条目；
     * 4) 把被删条目的文字追加到上一条末尾（删除的是第 0 条时则追加到新的第 0 条），并把
     *    光标放在拼接处。
     */
    public void onEditTextDelete(int index, String text) {
        int childCount = mEditTextList.getChildCount();
        if (childCount == 1) {
            return;
        }

        for (int i = index + 1; i < childCount; i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i - 1);
        }

        mEditTextList.removeViewAt(index);
        NoteEditText edit = null;
        if(index == 0) {
            edit = (NoteEditText) mEditTextList.getChildAt(0).findViewById(
                    R.id.et_edit_text);
        } else {
            edit = (NoteEditText) mEditTextList.getChildAt(index - 1).findViewById(
                    R.id.et_edit_text);
        }
        int length = edit.length();
        edit.append(text);
        edit.requestFocus();
        edit.setSelection(length);
    }

    /**
     * 回调：在清单条目中按回车。NoteEditText 已经把光标之后的文字切出来传入 text，这里在
     * index 位置插入一个新条目来承接这段文字，把焦点移到新条目开头，并重新给其后的条目编号。
     */
    public void onEditTextEnter(int index, String text) {
        /**
         * Should not happen, check for debug
         */
        if(index > mEditTextList.getChildCount()) {
            Log.e(TAG, "Index out of mEditTextList boundrary, should not happen");
        }

        View view = getListItem(text, index);
        mEditTextList.addView(view, index);
        NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.requestFocus();
        edit.setSelection(0);
        for (int i = index + 1; i < mEditTextList.getChildCount(); i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i);
        }
    }

    /**
     * 切换到清单模式：把正文按换行拆成多个条目，空行丢弃，每条生成一个列表项（getListItem
     * 会解析 √ / □ 前缀），末尾再追加一个空条目方便继续输入。然后隐藏普通输入框，显示条目容
     * 器。
     */
    private void switchToListMode(String text) {
        mEditTextList.removeAllViews();
        String[] items = text.split("\n");
        int index = 0;
        for (String item : items) {
            if(!TextUtils.isEmpty(item)) {
                mEditTextList.addView(getListItem(item, index));
                index++;
            }
        }
        mEditTextList.addView(getListItem("", index));
        mEditTextList.getChildAt(index).findViewById(R.id.et_edit_text).requestFocus();

        mNoteEditor.setVisibility(View.GONE);
        mEditTextList.setVisibility(View.VISIBLE);
    }

    /**
     * 返回带高亮的文本：把所有匹配搜索关键词的片段设置背景色；没有关键词时返回原文（null 当
     * 作空串处理）。
     * 注意：关键词被直接当作正则表达式编译，如果用户搜索的内容含有正则特殊字符（例如“(”），
     * 可能抛出 PatternSyntaxException，属于潜在隐患，可记入 Issue。
     */
    private Spannable getHighlightQueryResult(String fullText, String userQuery) {
        SpannableString spannable = new SpannableString(fullText == null ? "" : fullText);
        if (!TextUtils.isEmpty(userQuery)) {
            mPattern = Pattern.compile(userQuery);
            Matcher m = mPattern.matcher(fullText);
            int start = 0;
            while (m.find(start)) {
                spannable.setSpan(
                        new BackgroundColorSpan(this.getResources().getColor(
                                R.color.user_query_highlight)), m.start(), m.end(),
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                start = m.end();
            }
        }
        return spannable;
    }

    /**
     * 创建一个清单条目视图（复选框 + NoteEditText）。会根据正文前缀还原勾选状态：以 √ 开头
     * 为已完成并加删除线，以 □ 开头为未完成，去掉前缀后才显示文字。
     * 复选框状态改变时同步切换删除线；最后把当前 Activity 注册为条目的监听器并设置序号。
     */
    private View getListItem(String item, int index) {
        View view = LayoutInflater.from(this).inflate(R.layout.note_edit_list_item, null);
        final NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
        CheckBox cb = ((CheckBox) view.findViewById(R.id.cb_edit_item));
        cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
                }
            }
        });

        if (item.startsWith(TAG_CHECKED)) {
            cb.setChecked(true);
            edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            item = item.substring(TAG_CHECKED.length(), item.length()).trim();
        } else if (item.startsWith(TAG_UNCHECKED)) {
            cb.setChecked(false);
            edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            item = item.substring(TAG_UNCHECKED.length(), item.length()).trim();
        }

        edit.setOnTextViewChangeListener(this);
        edit.setIndex(index);
        edit.setText(getHighlightQueryResult(item, mUserQuery));
        return view;
    }

    // 回调：条目有没有文字发生变化时，显示或隐藏该条目前面的复选框（空行不显示复选框）。
    public void onTextChange(int index, boolean hasText) {
        if (index >= mEditTextList.getChildCount()) {
            Log.e(TAG, "Wrong index, should not happen");
            return;
        }
        if(hasText) {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.VISIBLE);
        } else {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.GONE);
        }
    }

    /**
     * WorkingNote 回调：模式切换。
     * 1) 切到清单模式：用普通输入框中的文本重建列表；
     * 2) 切回普通模式：先把列表内容汇总回 WorkingNote（getWorkingText 的返回值表示是否有
     *    已勾选项）。如果一项都没勾选，说明文本里只有 □ 标记，需要把它们去掉，让普通文本保
     *    持干净；有已勾选项时保留标记，避免丢失完成状态。再把文本放回输入框并切换可见性。
     * 回调发生时 WorkingNote 的 mMode 还是旧值，所以 getWorkingText 仍会按清单模式读取列表。
     */
    public void onCheckListModeChanged(int oldMode, int newMode) {
        if (newMode == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mNoteEditor.getText().toString());
        } else {
            if (!getWorkingText()) {
                mWorkingNote.setWorkingText(mWorkingNote.getContent().replace(TAG_UNCHECKED + " ",
                        ""));
            }
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mEditTextList.setVisibility(View.GONE);
            mNoteEditor.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 把界面上的内容同步回 WorkingNote，返回值表示是否存在已勾选的条目。
     * 清单模式下逐个读取条目，跳过空条目，按“√ 文字”或“□ 文字”拼接，每条一行；普通模式直接
     * 取输入框文本。每次保存或切换模式之前都要先调用它，保证数据模型是最新的。
     */
    private boolean getWorkingText() {
        boolean hasChecked = false;
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                View view = mEditTextList.getChildAt(i);
                NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
                if (!TextUtils.isEmpty(edit.getText())) {
                    if (((CheckBox) view.findViewById(R.id.cb_edit_item)).isChecked()) {
                        sb.append(TAG_CHECKED).append(" ").append(edit.getText()).append("\n");
                        hasChecked = true;
                    } else {
                        sb.append(TAG_UNCHECKED).append(" ").append(edit.getText()).append("\n");
                    }
                }
            }
            mWorkingNote.setWorkingText(sb.toString());
        } else {
            mWorkingNote.setWorkingText(mNoteEditor.getText().toString());
        }
        return hasChecked;
    }

    // 保存便签：先把界面内容同步到 WorkingNote，再交给它写库。只有保存成功才设置 RESULT_OK。
    private boolean saveNote() {
        getWorkingText();
        boolean saved = mWorkingNote.saveNote();
        if (saved) {
            /**
             * There are two modes from List view to edit view, open one note,
             * create/edit a node. Opening node requires to the original
             * position in the list when back from edit view, while creating a
             * new node requires to the top of the list. This code
             * {@link #RESULT_OK} is used to identify the create/edit state
             */
            setResult(RESULT_OK);
        }
        return saved;
    }

    /**
     * 把当前便签以快捷方式的形式发送到桌面：构造一个指向本页的 ACTION_VIEW Intent（携带便签
     * id）作为快捷方式的目标，名称取正文的前 10 个字，再通过 INSTALL_SHORTCUT 广播通知桌面
     * 启动器创建。
     */
    /**
     * M10：进入沉浸式阅读模式。
     *
     * <p>先同步保存当前编辑内容，保证阅读页读到的是最新正文，而不是上次保存的旧内容；
     * 只传递便签 id，阅读页自己做只读查询，因此阅读模式不会改动任何便签数据。
     */
    private void openReadingMode() {
        saveNote();
        Intent intent = new Intent(this, NoteReadingActivity.class);
        intent.putExtra(NoteReadingActivity.EXTRA_NOTE_ID, mWorkingNote.getNoteId());
        startActivity(intent);
    }

    private void sendToDesktop() {
        /**
         * Before send message to home, we should make sure that current
         * editing note is exists in databases. So, for new note, firstly
         * save it
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }

        if (mWorkingNote.getNoteId() > 0) {
            Intent sender = new Intent();
            Intent shortcutIntent = new Intent(this, NoteEditActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra(Intent.EXTRA_UID, mWorkingNote.getNoteId());
            sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            sender.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                    makeShortcutIconTitle(mWorkingNote.getContent()));
            sender.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app));
            sender.putExtra("duplicate", true);
            sender.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            showToast(R.string.info_note_enter_desktop);
            sendBroadcast(sender);
        } else {
            /**
             * There is the condition that user has input nothing (the note is
             * not worthy saving), we have no note id, remind the user that he
             * should input something
             */
            Log.e(TAG, "Send to desktop error");
            showToast(R.string.error_note_empty_for_send_to_desktop);
        }
    }

    // 生成快捷方式名称：先去掉清单标记符号，再截取前 10 个字符。
    private String makeShortcutIconTitle(String content) {
        content = content.replace(TAG_CHECKED, "");
        content = content.replace(TAG_UNCHECKED, "");
        return content.length() > SHORTCUT_ICON_TITLE_MAX_LEN ? content.substring(0,
                SHORTCUT_ICON_TITLE_MAX_LEN) : content;
    }

    // 弹出默认时长（短）的提示；下面的重载可以指定时长。
    private void showToast(int resId) {
        showToast(resId, Toast.LENGTH_SHORT);
    }

    private void showToast(int resId, int duration) {
        Toast.makeText(this, resId, duration).show();
    }
}