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

package net.micode.notes.model;

import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.tool.ResourceParser.NoteBgResources;


/**
 * “正在编辑的便签”在内存中的模型，夹在编辑页和数据库之间的中间层。
 *
 * 编辑页（NoteEditActivity）不直接操作数据库：用户的每一次修改（正文、背景色、提醒、清单模
 * 式等）都先记在这里，由内部的 Note 对象攒成一份“变更集”，等调用 saveNote() 时再一次性写入
 * ContentProvider。
 *
 * 创建方式只有两种：createEmptyNote() 新建、load() 打开已有便签。构造方法是私有的，避免外部
 * 直接 new 出状态不完整的对象。
 *
 * 设置发生变化时，通过 NoteSettingChangedListener 通知界面刷新，模型本身不依赖任何具体界面。
 */
public class WorkingNote {
    // Note for the working note
    // 真正负责攒变更并写库的对象：setXxx 系列方法只是把改动记到它里面，saveNote() 时才调用
    // syncNote 落盘。
    private Note mNote;
    // Note Id
    // 便签在 note 表中的 id。0 表示还没有写入数据库（新建且未保存），判断方法见
    // existInDatabase()。
    private long mNoteId;
    // Note content
    // 正文。清单模式下每行一个条目，并带有 √（已完成）或 □（未完成）前缀，由编辑页负责拼装
    // 和解析。
    private String mContent;
    // Note mode
    // 文本模式：0 为普通文本，TextNote.MODE_CHECK_LIST 为清单模式。
    private int mMode;

    // 提醒时间（毫秒时间戳），0 表示没有设置提醒。
    private long mAlertDate;

    // 最后修改时间，主要用于编辑页顶部的显示。
    private long mModifiedDate;

    // 背景色编号（ResourceParser 中的 YELLOW、RED 等常量），不是图片资源 id；真正的资源由
    // getBgColorResId() 转换得到。
    private int mBgColorId;

    // 该便签绑定的桌面小部件 id，未绑定时为 AppWidgetManager.INVALID_APPWIDGET_ID。
    private int mWidgetId;

    // 小部件类型（2x / 4x），Notes.TYPE_WIDGET_INVALIDE 表示没有绑定小部件。
    private int mWidgetType;

    // 便签所在文件夹的 id。
    private long mFolderId;

    private Context mContext;

    private static final String TAG = "WorkingNote";

    // 内存中的“已删除”标记。置位后 isWorthSaving() 返回 false，防止刚删除的便签在页面退出（
    // onPause）时又被保存回数据库。
    private boolean mIsDeleted;

    // 设置变化的回调对象，目前就是 NoteEditActivity。可能为 null，所以每次回调前都要判空。
    private NoteSettingChangedListener mNoteSettingStatusListener;

    // 查询 data 表时要取的列。下面的 *_COLUMN 常量是这些列在游标中的下标，两边顺序必须保持
    // 一致，改动一处就要同步修改另一处。
    public static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID,
            DataColumns.CONTENT,
            DataColumns.MIME_TYPE,
            DataColumns.DATA1,
            DataColumns.DATA2,
            DataColumns.DATA3,
            DataColumns.DATA4,
    };

    // 查询 note 表时要取的列，同样与下面的下标常量一一对应。
    public static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.PARENT_ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
            NoteColumns.MODIFIED_DATE
    };

    // 以下是游标列下标，分别对应上面两个 PROJECTION 数组中的顺序。
    private static final int DATA_ID_COLUMN = 0;

    private static final int DATA_CONTENT_COLUMN = 1;

    private static final int DATA_MIME_TYPE_COLUMN = 2;

    // 文本便签中 DATA1 列存放的是清单模式标志（TextNote.MODE），所以这里叫 MODE。
    private static final int DATA_MODE_COLUMN = 3;

    private static final int NOTE_PARENT_ID_COLUMN = 0;

    private static final int NOTE_ALERTED_DATE_COLUMN = 1;

    private static final int NOTE_BG_COLOR_ID_COLUMN = 2;

    private static final int NOTE_WIDGET_ID_COLUMN = 3;

    private static final int NOTE_WIDGET_TYPE_COLUMN = 4;

    private static final int NOTE_MODIFIED_DATE_COLUMN = 5;

    // New note construct
    /**
     * 新建便签用的构造方法。此时数据库里还没有对应记录：id 为 0，修改时间取当前时间，小部件
     * 类型先置为无效值，等 createEmptyNote() 再补充设置。
     */
    private WorkingNote(Context context, long folderId) {
        mContext = context;
        mAlertDate = 0;
        mModifiedDate = System.currentTimeMillis();
        mFolderId = folderId;
        mNote = new Note();
        mNoteId = 0;
        mIsDeleted = false;
        mMode = 0;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;
    }

    // Existing note construct
    /**
     * 打开已有便签用的构造方法，会立即从数据库加载数据，找不到便签时抛出
     * IllegalArgumentException。
     * 这里的 folderId 只是占位，加载成功后会被数据库中的 parent_id 覆盖。
     */
    private WorkingNote(Context context, long noteId, long folderId) {
        mContext = context;
        mNoteId = noteId;
        mFolderId = folderId;
        mIsDeleted = false;
        mNote = new Note();
        loadNote();
    }

    /**
     * 从 note 表读取便签的属性（所属文件夹、背景色、小部件、提醒时间、修改时间），随后调用
     * loadNoteData() 读取正文。
     *
     * 注意：只有 query 返回 null 时才会抛异常；如果游标不为空但没有任何记录（moveToFirst 为
     * false），这里不会报错，字段会保持默认值。这是一个潜在隐患，标注阶段不改，可记入 Issue。
     */
    private void loadNote() {
        Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mNoteId), NOTE_PROJECTION, null,
                null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                mFolderId = cursor.getLong(NOTE_PARENT_ID_COLUMN);
                mBgColorId = cursor.getInt(NOTE_BG_COLOR_ID_COLUMN);
                mWidgetId = cursor.getInt(NOTE_WIDGET_ID_COLUMN);
                mWidgetType = cursor.getInt(NOTE_WIDGET_TYPE_COLUMN);
                mAlertDate = cursor.getLong(NOTE_ALERTED_DATE_COLUMN);
                mModifiedDate = cursor.getLong(NOTE_MODIFIED_DATE_COLUMN);
            }
            cursor.close();
        } else {
            Log.e(TAG, "No note with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note with id " + mNoteId);
        }
        loadNoteData();
    }

    /**
     * 从 data 表读取正文。一条便签在 data 表中可能对应多行：mime 类型为 NOTE 的是文字内容，
     * CALL_NOTE 是通话记录的附加信息。
     * 这里除了取出内容，还要记下各行的 data id，后续 syncNote 更新时要靠这些 id 定位到具体
     * 该改哪一行。
     */
    private void loadNoteData() {
        Cursor cursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI, DATA_PROJECTION,
                DataColumns.NOTE_ID + "=?", new String[] {
                        String.valueOf(mNoteId)
                }, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(DATA_MIME_TYPE_COLUMN);
                    if (DataConstants.NOTE.equals(type)) {
                        mContent = cursor.getString(DATA_CONTENT_COLUMN);
                        mMode = cursor.getInt(DATA_MODE_COLUMN);
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else if (DataConstants.CALL_NOTE.equals(type)) {
                        mNote.setCallDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else {
                        Log.d(TAG, "Wrong note type with type:" + type);
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        } else {
            Log.e(TAG, "No data with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note's data with id " + mNoteId);
        }
    }

    /**
     * 工厂方法：创建一条空白便签。folderId 指定新便签放在哪个文件夹；widgetId、widgetType
     * 用于“从桌面小部件新建便签”的场景；defaultBgColorId 是默认背景色。
     * 返回的对象只存在于内存中，只有调用 saveNote() 且内容非空时才会真正入库。
     */
    public static WorkingNote createEmptyNote(Context context, long folderId, int widgetId,
                                              int widgetType, int defaultBgColorId) {
        WorkingNote note = new WorkingNote(context, folderId);
        note.setBgColorId(defaultBgColorId);
        note.setWidgetId(widgetId);
        note.setWidgetType(widgetType);
        return note;
    }

    // 工厂方法：按 id 加载已有便签。folderId 传 0 只是占位，真实值由 loadNote() 从数据库读
    // 取。
    public static WorkingNote load(Context context, long id) {
        return new WorkingNote(context, id, 0);
    }

    /**
     * 把当前的修改保存到数据库，返回值表示是否真的执行了保存。流程如下：
     * 1) 用 isWorthSaving() 判断有没有必要保存；
     * 2) 如果是新便签，先向数据库申请一个新 id，申请失败则直接返回 false；
     * 3) 调用 Note.syncNote 把累积的变更写入；
     * 4) 如果便签绑定了小部件，回调 onWidgetChanged 让小部件刷新。
     * 加 synchronized 是为了避免多处几乎同时触发保存（例如 onPause 和返回键）时重复创建记录。
     */
    public synchronized boolean saveNote() {
        if (isWorthSaving()) {
            if (!existInDatabase()) {
                if ((mNoteId = Note.getNewNoteId(mContext, mFolderId)) == 0) {
                    Log.e(TAG, "Create new note fail with id:" + mNoteId);
                    return false;
                }
            }

            mNote.syncNote(mContext, mNoteId);

            /**
             * Update widget content if there exist any widget of this note
             */
            if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && mWidgetType != Notes.TYPE_WIDGET_INVALIDE
                    && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
            }
            return true;
        } else {
            return false;
        }
    }

    // 约定：id 大于 0 才表示已经入库；新建未保存的便签 id 为 0。
    public boolean existInDatabase() {
        return mNoteId > 0;
    }

    /**
     * 判断是否有必要保存。以下三种情况都不保存：
     * 1) 已被标记为删除；
     * 2) 新便签但内容为空（用户点进来又退出，不应该留下空便签）；
     * 3) 已入库的便签但没有任何本地修改（避免无意义的写库）。
     */
    private boolean isWorthSaving() {
        if (mIsDeleted || (!existInDatabase() && TextUtils.isEmpty(mContent))
                || (existInDatabase() && !mNote.isLocalModified())) {
            return false;
        } else {
            return true;
        }
    }

    // 注册设置变化的监听器，NoteEditActivity 在 initActivityState() 中把自己传进来。
    public void setOnSettingStatusChangedListener(NoteSettingChangedListener l) {
        mNoteSettingStatusListener = l;
    }

    /**
     * 设置或取消提醒。date 是提醒时间，set 为 true 表示设置、false 表示取消（此时 date 传 0）。
     * 提醒时间变了才记录变更；但无论是否变化，都会回调 onClockAlertChanged，由界面负责向
     * AlarmManager 登记或取消闹钟。
     */
    public void setAlertDate(long date, boolean set) {
        if (date != mAlertDate) {
            mAlertDate = date;
            mNote.setNoteValue(NoteColumns.ALERTED_DATE, String.valueOf(mAlertDate));
        }
        if (mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onClockAlertChanged(date, set);
        }
    }

    /**
     * 标记便签已删除，只改内存状态，数据库中的删除由 DataUtils 完成。
     * 如果便签绑定了桌面小部件，需要通知小部件刷新。
     */
    public void markDeleted(boolean mark) {
        mIsDeleted = mark;
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onWidgetChanged();
        }
    }

    // 修改背景色：颜色不变则什么都不做；变化时先通知界面刷新，再把变更记入 Note。
    public void setBgColorId(int id) {
        if (id != mBgColorId) {
            mBgColorId = id;
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onBackgroundColorChanged();
            }
            mNote.setNoteValue(NoteColumns.BG_COLOR_ID, String.valueOf(id));
        }
    }

    /**
     * 切换普通文本 / 清单模式。
     * 注意顺序：先回调 onCheckListModeChanged(旧模式, 新模式)，让界面完成内容转换，之后才更
     * 新 mMode。界面在回调里需要依据“旧模式”把当前内容取回（见
     * NoteEditActivity.getWorkingText），所以回调时 mMode 必须还是旧值，这个顺序不能随意调
     * 换。
     */
    public void setCheckListMode(int mode) {
        if (mMode != mode) {
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onCheckListModeChanged(mMode, mode);
            }
            mMode = mode;
            mNote.setTextData(TextNote.MODE, String.valueOf(mMode));
        }
    }

    // setWidgetType 与下面的 setWidgetId 在新建“小部件便签”时由 createEmptyNote 调用，同样
    // 只在值发生变化时才记录变更。
    public void setWidgetType(int type) {
        if (type != mWidgetType) {
            mWidgetType = type;
            mNote.setNoteValue(NoteColumns.WIDGET_TYPE, String.valueOf(mWidgetType));
        }
    }

    public void setWidgetId(int id) {
        if (id != mWidgetId) {
            mWidgetId = id;
            mNote.setNoteValue(NoteColumns.WIDGET_ID, String.valueOf(mWidgetId));
        }
    }

    // 更新正文。内容相同则忽略，这样没有真正修改时不会被判定为“有改动”，也就不会触发保存（
    // 参见 isWorthSaving）。
    public void setWorkingText(String text) {
        if (!TextUtils.equals(mContent, text)) {
            mContent = text;
            mNote.setTextData(DataColumns.CONTENT, mContent);
        }
    }

    // 把便签转换为通话记录便签：写入通话时间和电话号码，并把它移动到通话记录文件夹。
    public void convertToCallNote(String phoneNumber, long callDate) {
        mNote.setCallData(CallNote.CALL_DATE, String.valueOf(callDate));
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber);
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(Notes.ID_CALL_RECORD_FOLDER));
    }

    // 提醒时间大于 0 就认为已设置提醒。
    public boolean hasClockAlert() {
        return (mAlertDate > 0 ? true : false);
    }

    public String getContent() {
        return mContent;
    }

    public long getAlertDate() {
        return mAlertDate;
    }

    public long getModifiedDate() {
        return mModifiedDate;
    }

    // 以下几个 get 方法把背景色编号翻译成 drawable 资源 id，界面可以直接使用。
    public int getBgColorResId() {
        return NoteBgResources.getNoteBgResource(mBgColorId);
    }

    public int getBgColorId() {
        return mBgColorId;
    }

    public int getTitleBgResId() {
        return NoteBgResources.getNoteTitleBgResource(mBgColorId);
    }

    public int getCheckListMode() {
        return mMode;
    }

    public long getNoteId() {
        return mNoteId;
    }

    public long getFolderId() {
        return mFolderId;
    }

    public int getWidgetId() {
        return mWidgetId;
    }

    public int getWidgetType() {
        return mWidgetType;
    }

    /**
     * 便签设置变化的回调接口，由编辑页实现。
     * 它把“数据模型的变化”和“界面刷新”解耦：模型只负责通知，不需要知道界面具体怎么刷新。
     */
    public interface NoteSettingChangedListener {
        /**
         * Called when the background color of current note has just changed
         */
        void onBackgroundColorChanged();

        /**
         * Called when user set clock
         */
        void onClockAlertChanged(long date, boolean set);

        /**
         * Call when user create note from widget
         */
        void onWidgetChanged();

        /**
         * Call when switch between check list mode and normal mode
         * @param oldMode is previous mode before change
         * @param newMode is new mode
         */
        void onCheckListModeChanged(int oldMode, int newMode);
    }
}