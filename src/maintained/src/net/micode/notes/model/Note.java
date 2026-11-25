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
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;


/**
 * 便签领域模型：在内存中聚合并缓冲一次编辑产生的改动，最后统一落库。
 *
 * <p>职责：把"便签行(note 表)的字段改动"与"便签数据体(data 表，文本/通话)的改动"
 * 暂存在 {@link ContentValues} 中，直到 {@link #syncNote} 时才通过 ContentResolver 批量提交。</p>
 *
 * <p>设计意图：编辑界面会频繁小改，逐次写库代价高；因此先攒在内存(diff)，一次性同步，
 * 并用 LOCAL_MODIFIED / MODIFIED_DATE 标记本地改动，供后续同步模块识别待同步项。</p>
 *
 * <p>生命周期：由编辑界面创建，随编辑会话存活；{@link #syncNote} 成功后缓冲被清空以便复用。
 * 非线程安全，应在 UI 线程串行使用。</p>
 */
public class Note {
    private ContentValues mNoteDiffValues;   // note 表待提交的字段改动
    private NoteData mNoteData;              // data 表(文本/通话)待提交的改动
    private static final String TAG = "Note";
    /**
     * Create a new note id for adding a new note to databases
     *
     * 在数据库中创建一条空便签行并返回其自增 id。前置条件：folderId 应为合法文件夹 id
     * (通常为当前文件夹或 ID_TEMPARAY_FOLDER)。插入时即标记 LOCAL_MODIFIED=1，使新便签处于"待同步"。
     * 缺陷提示：
     * 1) 异常分支将 noteId 置 0，紧随其后的 "noteId == -1" 判错分支实际永不命中(非法场景应是 0 或非正)，
     *    该校验形同虚设；
     * 2) uri.getPathSegments().get(1) 在 insert 失败返回异常 Uri 时也可能抛异常，且未被捕获。
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        // Create a new note in the database
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();
        values.put(NoteColumns.CREATED_DATE, createdTime);
        values.put(NoteColumns.MODIFIED_DATE, createdTime);
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        values.put(NoteColumns.PARENT_ID, folderId);
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            noteId = Long.valueOf(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Get note id error :" + e.toString());
            noteId = 0;
        }
        if (noteId == -1) {
            throw new IllegalStateException("Wrong note id:" + noteId);
        }
        return noteId;
    }

    /** 构造：初始化两个空的改动缓冲。 */
    public Note() {
        mNoteDiffValues = new ContentValues();
        mNoteData = new NoteData();
    }

    /** 写入 note 表某字段，并自动附带 LOCAL_MODIFIED=1 与当前 MODIFIED_DATE(故所有编辑都会刷新修改时间)。 */
    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
    }

    // 以下 setXxx/getXxx 把对"数据体"的读写转发给内部 NoteData(文本数据与通话数据的 id/字段)
    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    /** 是否存在未落库的改动(note 字段或数据体任一有改动即为 true)，供 syncNote 判断是否需要写库。 */
    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    /**
     * 将缓冲的改动写入数据库。
     * 流程：先更新 note 行(字段改动)，再交由 NoteData 落实数据体改动。
     * 契约：noteId 必须 >0，否则抛 IllegalArgumentException；无改动时直接返回 true。
     * 返回值：true 表示同步成功；false 表示数据体同步失败。
     * 注意：即使 note 行 update 影响行数为 0(理论上不该发生)也不会中断，仍继续写数据体。
     * 缺陷提示：当数据体"只有新增、没有更新"时，pushIntoContentResolver 会因操作列表为空而返回 null，
     * 使本方法误判为失败(返回 false)——尽管新增其实已经成功写入。
     */
    public boolean syncNote(Context context, long noteId) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId);
        }

        if (!isLocalModified()) {
            return true;
        }

        /**
         * In theory, once data changed, the note should be updated on {@link NoteColumns#LOCAL_MODIFIED} and
         * {@link NoteColumns#MODIFIED_DATE}. For data safety, though update note fails, we also update the
         * note data info
         */
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues, null,
                null) == 0) {
            Log.e(TAG, "Update note error, should not happen");
            // Do not return, fall through
        }
        mNoteDiffValues.clear();

        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false;
        }

        return true;
    }

    /**
     * 内部类：负责 note 的"数据体"部分(data 表)。一个便签至多一条文本数据与一条通话数据，
     * 各自缓存待写入的字段与已存在的 data 行 id(0 表示尚未插入)。
     */
    private class NoteData {
        private long mTextDataId;      // 文本数据在 data 表中的 id，0 表示新数据待插入

        private ContentValues mTextDataValues;

        private long mCallDataId;      // 通话数据在 data 表中的 id，0 表示新数据待插入

        private ContentValues mCallDataValues;

        private static final String TAG = "NoteData";

        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        /** 文本或通话数据任一有未落库改动即为 true。 */
        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        /** 记录已存在的文本数据行 id；前置条件：id>0(否则抛 IllegalArgumentException)。 */
        void setTextDataId(long id) {
            if(id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            mTextDataId = id;
        }

        /** 记录已存在的通话数据行 id；前置条件：id>0(否则抛 IllegalArgumentException)。 */
        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            mCallDataId = id;
        }

        // 以下两个 set 除缓存数据体字段外，还会同步打上 note 的本地修改标记与修改时间，
        // 以保证"改了数据体"也能被外层 isLocalModified 感知到。
        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 把文本/通话数据体的改动写入 data 表。
         * 策略：
         * - 对应数据行 id 为 0(新数据)：直接 insert，成功后就地记录新 id；
         * - 已有 id(已存在)：构造 update 的 ContentProviderOperation，最后 applyBatch 批量提交。
         * 前置条件：noteId>0(否则抛 IllegalArgumentException)。
         * 缺陷提示：当只发生 insert(无 update)时 operationList 为空，方法返回 null，
         * 调用方(syncNote)会把"新增成功"误判为失败——返回值语义因此不可靠。
         */
        Uri pushIntoContentResolver(Context context, long noteId) {
            /**
             * Check for safety
             */
            if (noteId <= 0) {
                throw new IllegalArgumentException("Wrong note id:" + noteId);
            }

            ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;

            // 处理文本数据体：新增走 insert，已存在则加入批量 update
            if(mTextDataValues.size() > 0) {
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mTextDataId == 0) {
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mTextDataValues);
                    try {
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new text data fail with noteId" + noteId);
                        mTextDataValues.clear();
                        return null;
                    }
                } else {
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                mTextDataValues.clear();
            }

            // 处理通话数据体：同上
            if(mCallDataValues.size() > 0) {
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mCallDataId == 0) {
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new call data fail with noteId" + noteId);
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear();
            }

            // 仅当存在 update 操作时才批量提交；提交成功返回 note 的 Uri，否则返回 null。
            // 注意：只有 insert 而无需 update 时此处不进入，从而返回 null(见上方缺陷提示)。
            if (operationList.size() > 0) {
                try {
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    return (results == null || results.length == 0 || results[0] == null) ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                } catch (OperationApplicationException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            return null;
        }
    }
}
