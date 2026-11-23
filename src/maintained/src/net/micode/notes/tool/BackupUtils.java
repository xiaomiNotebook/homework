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
import android.database.Cursor;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;


/**
 * 备份工具类：负责把本机便签导出为“用户可读”的纯文本文件，写到外部存储（SD 卡）上。
 *
 * <p>职责边界：
 * <ul>
 *   <li>只做“导出/备份”方向，不负责导入；</li>
 *   <li>不改变数据库中的任何数据，只读取便签与数据表并写文件；</li>
 *   <li>导出结果由 {@link #exportToText()} 的返回值（STATE_* 常量）告知调用方成败原因。</li>
 * </ul>
 *
 * <p>设计意图：本类采用单例，是因为导出过程需要贯穿整个便签库，反复创建没有意义；
 * 但注意单例持有传入的 {@link Context}，如果传入的是 Activity，会延长其生命周期，
 * 实际使用时应尽量传 Application Context。
 *
 * <p>生命周期与线程约束：本类的方法都是同步阻塞的（会遍历整库并做文件 IO），
 * 由调用方（设置界面）自行决定是否放到后台线程执行，本类不做线程切换。
 */
public class BackupUtils {
    private static final String TAG = "BackupUtils";
    // Singleton stuff
    private static BackupUtils sInstance;

    /**
     * 获取单例。首次调用时才创建，且创建过程对外部不可见。
     *
     * @param context 用于访问资源与 ContentResolver；持有引用，建议传 Application Context
     * @return 全局唯一的 BackupUtils 实例
     */
    public static synchronized BackupUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BackupUtils(context);
        }
        return sInstance;
    }

    /**
     * Following states are signs to represents backup or restore
     * status
     *
     * 这些常量是导出操作的“结果码”，调用方据此决定提示文案。
     * 约定：只有 {@link #STATE_SUCCESS} 表示文件确实生成，其余都表示失败，
     * 且失败原因互斥、按“环境 → 文件 → 数据 → 运行时异常”的检查顺序返回第一个命中的原因。
     */
    // Currently, the sdcard is not mounted
    public static final int STATE_SD_CARD_UNMOUONTED           = 0;
    // The backup file not exist
    public static final int STATE_BACKUP_FILE_NOT_EXIST        = 1;
    // The data is not well formated, may be changed by other programs
    public static final int STATE_DATA_DESTROIED               = 2;
    // Some run-time exception which causes restore or backup fails
    public static final int STATE_SYSTEM_ERROR                 = 3;
    // Backup or restore success
    public static final int STATE_SUCCESS                      = 4;

    private TextExport mTextExport;

    private BackupUtils(Context context) {
        mTextExport = new TextExport(context);
    }

    /**
     * 前置条件判断：外部存储是否处于“已挂载且可读写”状态。
     * 只有 MEDIA_MOUNTED 才允许写入；MEDIA_MOUNTED_READ_ONLY、MEDIA_SHARED 等都视为不可用。
     */
    private static boolean externalStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * 对外唯一入口：把整个便签库导出为文本文件。
     *
     * @return 结果码，取值见本类的 STATE_* 常量
     */
    public int exportToText() {
        return mTextExport.exportToText();
    }

    public String getExportedTextFileName() {
        return mTextExport.mFileName;
    }

    public String getExportedTextFileDir() {
        return mTextExport.mFileDirectory;
    }

    /**
     * 纯文本导出的实际实现，内部类是因为它只服务于 BackupUtils，不对外暴露。
     *
     * <p>导出的数据模型理解要点：Micode 便签把“便签/文件夹”统一存在 note 表，
     * 把“便签内容/通话记录”存在 data 表，两者通过 note_id 关联。
     * 因此导出流程是两层游标：先按 note 表找到文件夹/根便签，再按 note_id 查 data 表取正文。
     */
    private static class TextExport {
        /**
         * note 表投影：只取导出需要的四列，避免一次读出大字段。
         * 下方 NOTE_COLUMN_* 是该投影的列下标，必须与数组顺序保持一致。
         */
        private static final String[] NOTE_PROJECTION = {
                NoteColumns.ID,
                NoteColumns.MODIFIED_DATE,
                NoteColumns.SNIPPET,
                NoteColumns.TYPE
        };

        private static final int NOTE_COLUMN_ID = 0;

        private static final int NOTE_COLUMN_MODIFIED_DATE = 1;

        private static final int NOTE_COLUMN_SNIPPET = 2;

        /**
         * data 表投影：CONTENT 存正文或通话地点，MIME_TYPE 区分普通便签与通话记录，
         * DATA1/DATA2 在通话记录里分别是通话时间与号码；下标常量同样与数组顺序绑定。
         */
        private static final String[] DATA_PROJECTION = {
                DataColumns.CONTENT,
                DataColumns.MIME_TYPE,
                DataColumns.DATA1,
                DataColumns.DATA2,
                DataColumns.DATA3,
                DataColumns.DATA4,
        };

        private static final int DATA_COLUMN_CONTENT = 0;

        private static final int DATA_COLUMN_MIME_TYPE = 1;

        private static final int DATA_COLUMN_CALL_DATE = 2;

        private static final int DATA_COLUMN_PHONE_NUMBER = 4;

        private final String [] TEXT_FORMAT;
        private static final int FORMAT_FOLDER_NAME          = 0;
        private static final int FORMAT_NOTE_DATE            = 1;
        private static final int FORMAT_NOTE_CONTENT         = 2;

        private Context mContext;
        private String mFileName;
        private String mFileDirectory;

        /**
         * 从资源数组读取三类输出行模板（文件夹名 / 便签时间 / 便签正文），
         * 这样导出格式由资源文件决定，不必在代码里硬编码换行和缩进。
         */
        public TextExport(Context context) {
            TEXT_FORMAT = context.getResources().getStringArray(R.array.format_for_exported_note);
            mContext = context;
            mFileName = "";
            mFileDirectory = "";
        }

        private String getFormat(int id) {
            return TEXT_FORMAT[id];
        }

        /**
         * Export the folder identified by folder id to text
         *
         * 导出一个文件夹下的便签。
         *
         * <p>边界条件：这里只按 parent_id 取“直接子节点”，不会递归导出子文件夹，
         * 因此嵌套文件夹里的便签在本实现中不会被输出；调用方只把顶层文件夹传进来。
         *
         * <p>异常/空数据处理：query 返回 null（例如 provider 不可用）时静默跳过；
         * 游标一定 close，避免泄漏。
         */
        private void exportFolderToText(String folderId, PrintStream ps) {
            // Query notes belong to this folder
            Cursor notesCursor = mContext.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION, NoteColumns.PARENT_ID + "=?", new String[] {
                        folderId
                    }, null);

            if (notesCursor != null) {
                if (notesCursor.moveToFirst()) {
                    do {
                        // Print note's last modified date
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                notesCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // Query data belong to this note
                        String noteId = notesCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (notesCursor.moveToNext());
                }
                notesCursor.close();
            }
        }

        /**
         * Export note identified by id to a print stream
         *
         * 导出一条便签的全部 data 行。一条便签可能有多行 data（例如通话记录），
         * 因此用 do-while 遍历，而不是只取第一条。
         *
         * <p>分派规则：根据 mime_type 走不同分支——CALL_NOTE 输出号码、通话时间、地点；
         * NOTE 只输出正文。其他 mime 类型被忽略，属于设计上的“只导出可读文本”取舍。
         *
         * <p>非显然实现：结尾用 ps.write 直接写两个 char 值作为分隔符，
         * 而不是 println，目的是在字节层面插入固定 2 字节，避免平台换行差异；
         * 写失败只记日志，不吃掉主流程（此时文件可能已部分写出）。
         */
        private void exportNoteToText(String noteId, PrintStream ps) {
            Cursor dataCursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI,
                    DATA_PROJECTION, DataColumns.NOTE_ID + "=?", new String[] {
                        noteId
                    }, null);

            if (dataCursor != null) {
                if (dataCursor.moveToFirst()) {
                    do {
                        String mimeType = dataCursor.getString(DATA_COLUMN_MIME_TYPE);
                        if (DataConstants.CALL_NOTE.equals(mimeType)) {
                            // Print phone number
                            String phoneNumber = dataCursor.getString(DATA_COLUMN_PHONE_NUMBER);
                            long callDate = dataCursor.getLong(DATA_COLUMN_CALL_DATE);
                            String location = dataCursor.getString(DATA_COLUMN_CONTENT);

                            if (!TextUtils.isEmpty(phoneNumber)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        phoneNumber));
                            }
                            // Print call date
                            ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), DateFormat
                                    .format(mContext.getString(R.string.format_datetime_mdhm),
                                            callDate)));
                            // Print call attachment location
                            if (!TextUtils.isEmpty(location)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        location));
                            }
                        } else if (DataConstants.NOTE.equals(mimeType)) {
                            String content = dataCursor.getString(DATA_COLUMN_CONTENT);
                            if (!TextUtils.isEmpty(content)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        content));
                            }
                        }
                    } while (dataCursor.moveToNext());
                }
                dataCursor.close();
            }
            // print a line separator between note
            try {
                ps.write(new byte[] {
                        Character.LINE_SEPARATOR, Character.LETTER_NUMBER
                });
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        /**
         * Note will be exported as text which is user readable
         *
         * 导出主流程，顺序不可随意调换：
         * <ol>
         *   <li>先判外部存储是否可用，不可用直接返回，避免在只读/未挂载状态下创建文件；</li>
         *   <li>再打开输出流（此步失败归为系统错误）；</li>
         *   <li>导出所有顶层文件夹（不含回收站，但单独包含通话记录文件夹）；</li>
         *   <li>导出 parent_id=0 的根便签；</li>
         *   <li>关闭流并返回成功。</li>
         * </ol>
         *
         * <p>已知边界：若中途出现 IO 异常，本方法没有 try/finally 保护，
         * PrintStream 可能不会被关闭；且只要前面没返回错误码就会返回 SUCCESS，
         * 即使实际没有任何便签被写出。这两点属于既有实现行为，标注阶段不做修改。
         */
        public int exportToText() {
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            PrintStream ps = getExportToTextPrintStream();
            if (ps == null) {
                Log.e(TAG, "get print stream error");
                return STATE_SYSTEM_ERROR;
            }
            // First export folder and its notes
            // 文件夹筛选条件：普通文件夹且不在回收站内；通话记录文件夹是系统内置的特殊目录，
            // 它的 parent 关系不满足上面的条件，所以用 OR 单独补上。
            Cursor folderCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    "(" + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER + " AND "
                            + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER + ") OR "
                            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER, null, null);

            if (folderCursor != null) {
                if (folderCursor.moveToFirst()) {
                    do {
                        // Print folder's name
                        String folderName = "";
                        if(folderCursor.getLong(NOTE_COLUMN_ID) == Notes.ID_CALL_RECORD_FOLDER) {
                            folderName = mContext.getString(R.string.call_record_folder_name);
                        } else {
                            folderName = folderCursor.getString(NOTE_COLUMN_SNIPPET);
                        }
                        if (!TextUtils.isEmpty(folderName)) {
                            ps.println(String.format(getFormat(FORMAT_FOLDER_NAME), folderName));
                        }
                        String folderId = folderCursor.getString(NOTE_COLUMN_ID);
                        exportFolderToText(folderId, ps);
                    } while (folderCursor.moveToNext());
                }
                folderCursor.close();
            }

            // Export notes in root's folder
            // 只导出直接挂在根（parent_id=0）下的便签，文件夹内的便签已在上一步按文件夹处理，
            // 两者互不重叠，避免同一条便签被导出两次。
            Cursor noteCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    NoteColumns.TYPE + "=" + +Notes.TYPE_NOTE + " AND " + NoteColumns.PARENT_ID
                            + "=0", null, null);

            if (noteCursor != null) {
                if (noteCursor.moveToFirst()) {
                    do {
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                noteCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // Query data belong to this note
                        String noteId = noteCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (noteCursor.moveToNext());
                }
                noteCursor.close();
            }
            ps.close();

            return STATE_SUCCESS;
        }

        /**
         * Get a print stream pointed to the file {@generateExportedTextFile}
         *
         * 创建目标文件并打开打印流。
         *
         * <p>副作用：成功时把文件名和目录缓存到 mFileName / mFileDirectory，
         * 供 {@link BackupUtils#getExportedTextFileName()} 等在导出结束后查询；
         * 失败时这两个字段保持旧值（首次为空串），调用方应结合结果码判断。
         */
        private PrintStream getExportToTextPrintStream() {
            File file = generateFileMountedOnSDcard(mContext, R.string.file_path,
                    R.string.file_name_txt_format);
            if (file == null) {
                Log.e(TAG, "create file to exported failed");
                return null;
            }
            mFileName = file.getName();
            mFileDirectory = mContext.getString(R.string.file_path);
            PrintStream ps = null;
            try {
                FileOutputStream fos = new FileOutputStream(file);
                ps = new PrintStream(fos);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            } catch (NullPointerException e) {
                e.printStackTrace();
                return null;
            }
            return ps;
        }
    }

    /**
     * Generate the text file to store imported data
     *
     * 在外部存储上生成备份文件，文件名为“路径 + 日期格式化模板”拼出的字符串，
     * 同一天重复导出会复用同一个文件（覆盖写），不会无限堆积。
     *
     * <p>非显然点：这里用的是 mkdir 而不是 mkdirs，只创建一级目录，
     * 若资源里的路径含多级目录且上级不存在，创建会失败；失败不抛异常，
     * 随后 createNewFile 也可能失败，方法最终返回 null，由调用方转为系统错误。
     */
    private static File generateFileMountedOnSDcard(Context context, int filePathResId, int fileNameFormatResId) {
        StringBuilder sb = new StringBuilder();
        sb.append(Environment.getExternalStorageDirectory());
        sb.append(context.getString(filePathResId));
        File filedir = new File(sb.toString());
        sb.append(context.getString(
                fileNameFormatResId,
                DateFormat.format(context.getString(R.string.format_date_ymd),
                        System.currentTimeMillis())));
        File file = new File(sb.toString());

        try {
            if (!filedir.exists()) {
                filedir.mkdir();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
            return file;
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}


