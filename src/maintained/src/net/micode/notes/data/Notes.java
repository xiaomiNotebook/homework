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

package net.micode.notes.data;

import android.net.Uri;
/**
 * 便签应用的数据契约(契约/常量中心)。
 *
 * <p>职责：集中定义 AUTHORITY、Content Uri、类型常量、系统文件夹 ID，
 * 以及 note/data 两张表的列名(schema)。{@link NotesProvider}、{@link NotesDatabaseHelper}
 * 与上层 UI 都依赖这里的定义，是全模块唯一的"事实来源"。</p>
 *
 * <p>设计意图：把 Uri/列名/常量集中于此，避免各处硬编码字符串导致不一致；
 * 列名以接口(interface)形式分组，便于静态引用(如 Notes.NoteColumns.ID)。</p>
 *
 * <p>注意：这些列名必须与 {@link NotesDatabaseHelper} 中的建表 SQL 严格一致，
 * 修改任何一方都必须同步。</p>
 */
public class Notes {
    public static final String AUTHORITY = "micode_notes";
    public static final String TAG = "Notes";
    // note 行的类型：普通便签 / 用户文件夹 / 系统文件夹
    public static final int TYPE_NOTE     = 0;
    public static final int TYPE_FOLDER   = 1;
    public static final int TYPE_SYSTEM   = 2;

    /**
     * Following IDs are system folders' identifiers
     * {@link Notes#ID_ROOT_FOLDER } is default folder
     * {@link Notes#ID_TEMPARAY_FOLDER } is for notes belonging no folder
     * {@link Notes#ID_CALL_RECORD_FOLDER} is to store call records
     */
    // 系统文件夹使用固定的非正数 ID；负数一律表示"系统保留"，用户不可见亦不可删除。
    // 缺陷提示：命名存在拼写错误——ID_TEMPARAY_FOLDER(应为 TEMPORARY)、ID_TRASH_FOLER(应为 FOLDER)，
    // 已扩散到多处调用，改动需全局同步。
    public static final int ID_ROOT_FOLDER = 0;
    public static final int ID_TEMPARAY_FOLDER = -1;
    public static final int ID_CALL_RECORD_FOLDER = -2;
    public static final int ID_TRASH_FOLER = -3;

    // 通过 Intent 在 Activity/小部件之间传递数据时使用的 extra key，统一加包名前缀避免冲突
    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id";
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";

    // 桌面小部件类型：-1 表示无效/未设置，0/1 分别对应 2x 与 4x 尺寸
    public static final int TYPE_WIDGET_INVALIDE      = -1;
    public static final int TYPE_WIDGET_2X            = 0;
    public static final int TYPE_WIDGET_4X            = 1;

    /**
     * data 表中行的 MIME 类型常量，用于区分"文本便签"与"通话记录便签"两类数据。
     * 与 {@link NotesDatabaseHelper} 中的触发器条件以及 {@link NotesProvider} 的查询/插入逻辑相呼应。
     */
    public static class DataConstants {
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE;
    }

    /**
     * Uri to query all notes and folders
     */
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");

    /**
     * Uri to query data
     */
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    /**
     * note 表的列名契约。该表每一行既可能是"便签"也可能是"文件夹"(由 {@link #TYPE} 区分)，
     * 因此两类实体复用同一套列：文件夹主要用 SNIPPET/NOTES_COUNT/TYPE，
     * 便签额外使用 BG_COLOR_ID/ALERTED_DATE/WIDGET_ID 等。
     * 列名必须与 NotesDatabaseHelper 的建表 SQL 及触发器保持一致。
     */
    public interface NoteColumns {
        /**
         * The unique ID for a row
         * <P> Type: INTEGER (long) </P>
         */
        public static final String ID = "_id";

        /**
         * The parent's id for note or folder
         * <P> Type: INTEGER (long) </P>
         */
        public static final String PARENT_ID = "parent_id";

        /**
         * Created data for note or folder
         * <P> Type: INTEGER (long) </P>
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * Latest modified date
         * <P> Type: INTEGER (long) </P>
         */
        public static final String MODIFIED_DATE = "modified_date";


        /**
         * Alert date
         * <P> Type: INTEGER (long) </P>
         */
        public static final String ALERTED_DATE = "alert_date";

        /**
         * Folder's name or text content of note
         * <P> Type: TEXT </P>
         */
        public static final String SNIPPET = "snippet";

        /**
         * Note's widget id
         * <P> Type: INTEGER (long) </P>
         */
        public static final String WIDGET_ID = "widget_id";

        /**
         * Note's widget type
         * <P> Type: INTEGER (long) </P>
         */
        public static final String WIDGET_TYPE = "widget_type";

        /**
         * Note's background color's id
         * <P> Type: INTEGER (long) </P>
         */
        public static final String BG_COLOR_ID = "bg_color_id";

        /**
         * For text note, it doesn't has attachment, for multi-media
         * note, it has at least one attachment
         * <P> Type: INTEGER </P>
         */
        public static final String HAS_ATTACHMENT = "has_attachment";

        /**
         * Folder's count of notes
         * <P> Type: INTEGER (long) </P>
         */
        public static final String NOTES_COUNT = "notes_count";

        /**
         * The file type: folder or note
         * <P> Type: INTEGER </P>
         */
        public static final String TYPE = "type";

        /**
         * The last sync id
         * <P> Type: INTEGER (long) </P>
         */
        public static final String SYNC_ID = "sync_id";

        /**
         * Sign to indicate local modified or not
         * <P> Type: INTEGER </P>
         */
        public static final String LOCAL_MODIFIED = "local_modified";

        /**
         * Original parent id before moving into temporary folder
         * <P> Type : INTEGER </P>
         */
        public static final String ORIGIN_PARENT_ID = "origin_parent_id";

        /**
         * The gtask id
         * <P> Type : TEXT </P>
         */
        public static final String GTASK_ID = "gtask_id";

        /**
         * The version code
         * <P> Type : INTEGER (long) </P>
         */
        public static final String VERSION = "version";
    }

    /**
     * data 表的列名契约，存放便签的"数据体"(文本内容或通话记录)。
     * 通过 {@link #MIME_TYPE} 区分子类型；DATA1~DATA5 是按 MIME 语义复用的通用列，
     * 例如 TextNote 用 DATA1 存清单模式，CallNote 用 DATA1 存通话时间、DATA3 存号码。
     */
    public interface DataColumns {
        /**
         * The unique ID for a row
         * <P> Type: INTEGER (long) </P>
         */
        public static final String ID = "_id";

        /**
         * The MIME type of the item represented by this row.
         * <P> Type: Text </P>
         */
        public static final String MIME_TYPE = "mime_type";

        /**
         * The reference id to note that this data belongs to
         * <P> Type: INTEGER (long) </P>
         */
        public static final String NOTE_ID = "note_id";

        /**
         * Created data for note or folder
         * <P> Type: INTEGER (long) </P>
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * Latest modified date
         * <P> Type: INTEGER (long) </P>
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * Data's content
         * <P> Type: TEXT </P>
         */
        public static final String CONTENT = "content";


        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * integer data type
         * <P> Type: INTEGER </P>
         */
        public static final String DATA1 = "data1";

        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * integer data type
         * <P> Type: INTEGER </P>
         */
        public static final String DATA2 = "data2";

        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * TEXT data type
         * <P> Type: TEXT </P>
         */
        public static final String DATA3 = "data3";

        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * TEXT data type
         * <P> Type: TEXT </P>
         */
        public static final String DATA4 = "data4";

        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * TEXT data type
         * <P> Type: TEXT </P>
         */
        public static final String DATA5 = "data5";
    }

    /**
     * 文本便签的数据子类型。data 表中 MIME_TYPE 等于其 CONTENT_ITEM_TYPE 的行即为文本内容。
     * MODE 复用通用列 DATA1，用于标记是否处于清单(checkbox)模式。
     * (注：这里 implements DataColumns 只是为继承其常量，并无实例意义。)
     */
    public static final class TextNote implements DataColumns {
        /**
         * Mode to indicate the text in check list mode or not
         * <P> Type: Integer 1:check list mode 0: normal mode </P>
         */
        public static final String MODE = DATA1;

        public static final int MODE_CHECK_LIST = 1;

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note";

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");
    }

    /**
     * 通话记录便签的数据子类型，用于把"某次通话"关联到便签。
     * CALL_DATE 复用 DATA1(通话时间)，PHONE_NUMBER 复用 DATA3(号码)。
     */
    public static final class CallNote implements DataColumns {
        /**
         * Call date for this record
         * <P> Type: INTEGER (long) </P>
         */
        public static final String CALL_DATE = DATA1;

        /**
         * Phone number for this record
         * <P> Type: TEXT </P>
         */
        public static final String PHONE_NUMBER = DATA3;

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note";

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");
    }
}
