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

package net.micode.notes.gtask.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;
import net.micode.notes.gtask.exception.ActionFailureException;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * 本地 {@code data} 表一行的内存缓存，代表便签的一条内容数据（如纯文本、通话记录）。
 *
 * <p>同步时，远端 JSON 只携带变化过的字段；本类用 {@link #mDiffDataValues}
 * 记录“与上次加载值不同”的列，{@link #commit(long, boolean, long)} 再把这些列
 * 写入 ContentProvider，避免整行覆盖。</p>
 *
 * <p>新建对象与从游标加载的对象行为不同：新建时所有字段都进入 diff，提交时执行
 * insert；从游标加载后只有真正变化的字段进入 diff，提交时执行 update。</p>
 */
public class SqlData {
    private static final String TAG = SqlData.class.getSimpleName();

    /** 新建对象尚未落库时的占位 ID。 */
    private static final int INVALID_ID = -99999;

    /** 查询 data 表时固定使用的列，下标与下面的常量一一对应。 */
    public static final String[] PROJECTION_DATA = new String[] {
            DataColumns.ID, DataColumns.MIME_TYPE, DataColumns.CONTENT, DataColumns.DATA1,
            DataColumns.DATA3
    };

    public static final int DATA_ID_COLUMN = 0;

    public static final int DATA_MIME_TYPE_COLUMN = 1;

    public static final int DATA_CONTENT_COLUMN = 2;

    public static final int DATA_CONTENT_DATA_1_COLUMN = 3;

    public static final int DATA_CONTENT_DATA_3_COLUMN = 4;

    private ContentResolver mContentResolver;

    /** true 表示这条 data 还没有插入数据库；提交后置为 false。 */
    private boolean mIsCreate;

    private long mDataId;

    private String mDataMimeType;

    private String mDataContent;

    private long mDataContentData1;

    private String mDataContentData3;

    /** 待写入的差异列；提交成功后清空。 */
    private ContentValues mDiffDataValues;

    /**
     * 创建一个尚未落库的新 data 行，使用纯文本便签的默认值。
     * 提交时走 insert 分支。
     */
    public SqlData(Context context) {
        mContentResolver = context.getContentResolver();
        mIsCreate = true;
        mDataId = INVALID_ID;
        mDataMimeType = DataConstants.NOTE;
        mDataContent = "";
        mDataContentData1 = 0;
        mDataContentData3 = "";
        mDiffDataValues = new ContentValues();
    }

    /**
     * 从已有的 data 查询游标加载一行。
     * 当前游标必须已定位到有效行；加载后视为已落库，提交时走 update 分支。
     */
    public SqlData(Context context, Cursor c) {
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(c);
        mDiffDataValues = new ContentValues();
    }

    /**
     * 按 {@link #PROJECTION_DATA} 的固定下标读取各列。
     * 调用方必须保证游标列顺序与本类常量一致。
     */
    private void loadFromCursor(Cursor c) {
        mDataId = c.getLong(DATA_ID_COLUMN);
        mDataMimeType = c.getString(DATA_MIME_TYPE_COLUMN);
        mDataContent = c.getString(DATA_CONTENT_COLUMN);
        mDataContentData1 = c.getLong(DATA_CONTENT_DATA_1_COLUMN);
        mDataContentData3 = c.getString(DATA_CONTENT_DATA_3_COLUMN);
    }

    /**
     * 用远端/本地 JSON 更新字段，并把发生变化的列记入差异集合。
     *
     * <p>字段缺失时使用默认值：ID 用 {@link #INVALID_ID}，MIME 用纯文本，
     * 内容用空串，DATA1 用 0，DATA3 用空串。新建对象时所有字段都视为变化，
     * 保证 insert 能写完整行。</p>
     *
     * @throws JSONException JSON 字段类型与预期不符时抛出，由调用方决定是否中止同步
     */
    public void setContent(JSONObject js) throws JSONException {
        long dataId = js.has(DataColumns.ID) ? js.getLong(DataColumns.ID) : INVALID_ID;
        if (mIsCreate || mDataId != dataId) {
            mDiffDataValues.put(DataColumns.ID, dataId);
        }
        mDataId = dataId;

        String dataMimeType = js.has(DataColumns.MIME_TYPE) ? js.getString(DataColumns.MIME_TYPE)
                : DataConstants.NOTE;
        if (mIsCreate || !mDataMimeType.equals(dataMimeType)) {
            mDiffDataValues.put(DataColumns.MIME_TYPE, dataMimeType);
        }
        mDataMimeType = dataMimeType;

        String dataContent = js.has(DataColumns.CONTENT) ? js.getString(DataColumns.CONTENT) : "";
        if (mIsCreate || !mDataContent.equals(dataContent)) {
            mDiffDataValues.put(DataColumns.CONTENT, dataContent);
        }
        mDataContent = dataContent;

        long dataContentData1 = js.has(DataColumns.DATA1) ? js.getLong(DataColumns.DATA1) : 0;
        if (mIsCreate || mDataContentData1 != dataContentData1) {
            mDiffDataValues.put(DataColumns.DATA1, dataContentData1);
        }
        mDataContentData1 = dataContentData1;

        String dataContentData3 = js.has(DataColumns.DATA3) ? js.getString(DataColumns.DATA3) : "";
        if (mIsCreate || !mDataContentData3.equals(dataContentData3)) {
            mDiffDataValues.put(DataColumns.DATA3, dataContentData3);
        }
        mDataContentData3 = dataContentData3;
    }

    /**
     * 导出当前行的完整 JSON。
     * 尚未插入数据库的对象没有稳定的 data ID，无法安全导出，因此返回 null。
     */
    public JSONObject getContent() throws JSONException {
        if (mIsCreate) {
            Log.e(TAG, "it seems that we haven't created this in database yet");
            return null;
        }
        JSONObject js = new JSONObject();
        js.put(DataColumns.ID, mDataId);
        js.put(DataColumns.MIME_TYPE, mDataMimeType);
        js.put(DataColumns.CONTENT, mDataContent);
        js.put(DataColumns.DATA1, mDataContentData1);
        js.put(DataColumns.DATA3, mDataContentData3);
        return js;
    }

    /**
     * 把差异写入本地数据库。
     *
     * <p>新建对象执行 insert：先去掉无效的占位 ID，再补上所属便签 ID，
     * 最后从返回 Uri 中解析真正的 data ID。</p>
     *
     * <p>已有对象执行 update：若 {@code validateVersion} 为 true，会附加
     * “便签 ID 匹配且版本号不大于当前版本”的条件，实现乐观并发控制，
     * 防止同步期间用户又修改便签导致覆盖新内容。更新影响 0 行时只记录警告，
     * 不抛异常，因为并发修改属于可恢复情况。</p>
     *
     * @param noteId          所属便签 ID，新建时写入外键
     * @param validateVersion 是否附带版本条件
     * @param version         当前同步批次使用的便签版本号
     */
    public void commit(long noteId, boolean validateVersion, long version) {

        if (mIsCreate) {
            if (mDataId == INVALID_ID && mDiffDataValues.containsKey(DataColumns.ID)) {
                mDiffDataValues.remove(DataColumns.ID);
            }

            mDiffDataValues.put(DataColumns.NOTE_ID, noteId);
            Uri uri = mContentResolver.insert(Notes.CONTENT_DATA_URI, mDiffDataValues);
            try {
                mDataId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
        } else {
            if (mDiffDataValues.size() > 0) {
                int result = 0;
                if (!validateVersion) {
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues, null, null);
                } else {
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues,
                            " ? in (SELECT " + NoteColumns.ID + " FROM " + TABLE.NOTE
                                    + " WHERE " + NoteColumns.VERSION + "=?)", new String[] {
                                    String.valueOf(noteId), String.valueOf(version)
                            });
                }
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }
        }

        mDiffDataValues.clear();
        mIsCreate = false;
    }

    public long getId() {
        return mDataId;
    }
}
