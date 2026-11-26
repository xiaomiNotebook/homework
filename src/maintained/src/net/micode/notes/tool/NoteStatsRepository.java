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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

/**
 * M09 统计数据的数据访问层：从备注 Provider 读取便签、正文字符数与文件夹信息。
 *
 * <p>职责边界：本类只负责“查询 + 组装 {@link NoteStatsCalculator.NoteRecord}”，
 * 不做任何统计运算，也不接触界面。统计规则集中在 {@link NoteStatsCalculator}。
 *
 * <p>口径说明（与验收要求一致）：
 * <ul>
 *   <li>排除回收站：所有查询都带 parent_id &lt;&gt; {@link Notes#ID_TRASH_FOLER} 条件；</li>
 *   <li>只统计普通便签：note 表 type = {@link Notes#TYPE_NOTE}；</li>
 *   <li>字符数只统计文本正文（data 表 mime_type 为 text_note），通话记录不计入；</li>
 *   <li>文件夹数量只统计用户可见文件夹（id &gt; 0），不含根目录、临时目录、通话记录和回收站。</li>
 * </ul>
 *
 * <p>异常路径：任一次 query 返回 null 时按空数据处理并立即返回当前已有结果，
 * 不抛异常；游标在 finally 中关闭，避免泄漏。
 */
public final class NoteStatsRepository {

    /** 一次统计所需的全量数据快照。 */
    public static final class Snapshot {
        public final List<NoteStatsCalculator.NoteRecord> notes;
        public final int folderCount;
        public final Map<Long, String> folderNames;

        Snapshot(List<NoteStatsCalculator.NoteRecord> notes, int folderCount,
                Map<Long, String> folderNames) {
            this.notes = notes;
            this.folderCount = folderCount;
            this.folderNames = folderNames;
        }
    }

    /**
     * 读取统计快照。该方法为同步查询，调用方应放在后台线程执行。
     *
     * @param context 用于获取 ContentResolver
     * @return 始终非 null 的快照；数据库为空时返回空列表
     */
    public Snapshot load(Context context) {
        Map<Long, String> folderNames = loadFolderNames(context);
        List<NoteStatsCalculator.NoteRecord> notes = loadNotes(context);
        int folderCount = countUserFolders(folderNames);
        return new Snapshot(notes, folderCount, folderNames);
    }

    /**
     * 读取用户可见文件夹的 id → 名称映射。
     *
     * <p>根目录与通话记录目录的显示名称属于界面文案，由统计页面按当前语言补入，
     * 本类不写死任何中文，避免影响多语言。
     */
    private Map<Long, String> loadFolderNames(Context context) {
        Map<Long, String> names = new HashMap<Long, String>();
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                    new String[] { NoteColumns.ID, NoteColumns.SNIPPET },
                    NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                    new String[] { String.valueOf(Notes.TYPE_FOLDER),
                            String.valueOf(Notes.ID_TRASH_FOLER) },
                    null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(1);
                    names.put(cursor.getLong(0), name == null ? "" : name);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return names;
    }

    /**
     * 读取所有非回收站普通便签，并统计每条便签的文本正文字符数。
     */
    private List<NoteStatsCalculator.NoteRecord> loadNotes(Context context) {
        List<NoteStatsCalculator.NoteRecord> notes = new ArrayList<NoteStatsCalculator.NoteRecord>();
        Map<Long, Integer> contentLengths = loadContentLengths(context);
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                    new String[] { NoteColumns.ID, NoteColumns.PARENT_ID, NoteColumns.CREATED_DATE },
                    NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                    new String[] { String.valueOf(Notes.TYPE_NOTE),
                            String.valueOf(Notes.ID_TRASH_FOLER) },
                    null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    Integer length = contentLengths.get(id);
                    notes.add(new NoteStatsCalculator.NoteRecord(id, cursor.getLong(1),
                            cursor.getLong(2), length == null ? 0 : length));
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return notes;
    }

    /**
     * 一次性读取全部文本正文的长度，按 note_id 聚合。
     *
     * <p>一条便签可能有多条 data 行，这里累加而不是覆盖，保证字符数不丢失。
     */
    private Map<Long, Integer> loadContentLengths(Context context) {
        Map<Long, Integer> lengths = new HashMap<Long, Integer>();
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(Notes.CONTENT_DATA_URI,
                    new String[] { DataColumns.NOTE_ID, DataColumns.CONTENT },
                    DataColumns.MIME_TYPE + "=?",
                    new String[] { TextNote.CONTENT_ITEM_TYPE }, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long noteId = cursor.getLong(0);
                    String content = cursor.getString(1);
                    int length = content == null ? 0 : content.length();
                    Integer old = lengths.get(noteId);
                    lengths.put(noteId, old == null ? length : old + length);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return lengths;
    }

    /**
     * 统计用户可见文件夹数量：id &gt; 0 的文件夹（不含根目录、临时目录、通话记录与回收站）。
     */
    private int countUserFolders(Map<Long, String> folderNames) {
        int count = 0;
        for (Long id : folderNames.keySet()) {
            if (id != null && id > 0) {
                count++;
            }
        }
        return count;
    }
}
