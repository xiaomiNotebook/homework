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

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.data.MetaData;
import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.SqlNote;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;


/**
 * GTask 同步的编排核心（单例）。
 *
 * <p>职责：把“本地数据库（NotesProvider）”与“云端 Google Tasks”做一次双向同步。
 * 它不直接发 HTTP（那是 {@link GTaskClient} 的事），也不直接跑线程（那是
 * {@link GTaskASyncTask} 的事），而是负责：</p>
 * <ol>
 *   <li>登录并拉取云端列表到内存（{@link #initGTaskList}）；</li>
 *   <li>逐节点比较本地与云端，判定每个节点的同步动作（{@link #syncContent} / {@link #syncFolder}）；</li>
 *   <li>按动作类型执行增删改（{@link #doContentSync} 分派到 add/update/delete 的本地/远端方法）；</li>
 *   <li>维护云端 gid ↔ 本地 note id 的双向映射，供移动、父子关系换算使用；</li>
 *   <li>在各检查点响应取消请求，保证同步可中断且状态一致。</li>
 * </ol>
 *
 * <p>数据模型要点：</p>
 * <ul>
 *   <li>云端 TaskList 对应本地文件夹（folder），Task 对应便签（note）；</li>
 *   <li>另有一个特殊的“meta 列表”，里面存 {@link MetaData} 记录，保存本地 note 的原始 id 与内容，
 *       用于把云端任务还原成本地便签时保留本地 id、解决冲突；</li>
 *   <li>{@code mMetaHashMap} 以“被关联节点的 gid”为键索引 MetaData。</li>
 * </ul>
 *
 * <p>并发与线程：</p>
 * <ul>
 *   <li>{@link #sync} 在 {@link GTaskASyncTask} 的后台线程执行；</li>
 *   <li>{@link #cancelSync} 可能在主线程（用户点取消）或 {@code onLowMemory} 中被调用，
 *       通过 {@code mCancelled} 标志与同步线程协作；</li>
 *   <li><b>注意</b>：{@code mCancelled} / {@code mSyncing} 未声明 {@code volatile}，
 *       跨线程可见性依赖 JVM 时序，存在理论上的可见性问题（仅记录，标注阶段不修复）。</li>
 * </ul>
 *
 * <p>已知风险（仅记录）：单例长期持有 {@code mActivity}（Activity 上下文），
 * 在设置页退出后可能造成内存泄漏；冲突动作 {@code SYNC_ACTION_UPDATE_CONFLICT} 当前简单地
 * 以本地覆盖远端，未做真正合并。</p>
 */
public class GTaskManager {
    private static final String TAG = GTaskManager.class.getSimpleName();

    /** 同步结果码：成功完成。 */
    public static final int STATE_SUCCESS = 0;

    /** 同步结果码：网络错误（捕获 NetworkFailureException）。 */
    public static final int STATE_NETWORK_ERROR = 1;

    /** 同步结果码：内部错误（捕获 ActionFailureException 或其它异常）。 */
    public static final int STATE_INTERNAL_ERROR = 2;

    /** 同步结果码：已有同步正在进行，本次请求被拒绝。 */
    public static final int STATE_SYNC_IN_PROGRESS = 3;

    /** 同步结果码：被用户或系统取消。 */
    public static final int STATE_SYNC_CANCELLED = 4;

    private static GTaskManager mInstance = null;

    /** 触发同步的 Activity，用于取 auth token；由 setActivityContext 注入。 */
    private Activity mActivity;

    /** 同步运行时的上下文（实际是 GTaskSyncService）。 */
    private Context mContext;

    /** 内容解析器，用于读写本地数据库。 */
    private ContentResolver mContentResolver;

    /** 是否正在同步：防止并发重复同步。 */
    private boolean mSyncing;

    /** 是否已请求取消：在同步各检查点被轮询。 */
    private boolean mCancelled;

    /** 云端任务列表映射：gid → TaskList（不含 meta 列表）。 */
    private HashMap<String, TaskList> mGTaskListHashMap;

    /** 云端全部节点映射：gid → Node（含列表与任务，用于快速判断某 gid 是否在云端）。 */
    private HashMap<String, Node> mGTaskHashMap;

    /** 元数据映射：被关联节点的 gid → MetaData（保存本地原始内容）。 */
    private HashMap<String, MetaData> mMetaHashMap;

    /** 云端 meta 列表（特殊任务列表），同步开始时查找或创建。 */
    private TaskList mMetaList;

    /** 本次同步中需要物理删除的本地便签 id 集合（回收站内容），最后批量删除。 */
    private HashSet<Long> mLocalDeleteIdMap;

    /** 云端 gid → 本地 note id 的映射，同步过程中逐步建立。 */
    private HashMap<String, Long> mGidToNid;

    /** 本地 note id → 云端 gid 的映射，与 mGidToNid 互为反向。 */
    private HashMap<Long, String> mNidToGid;

    /** 私有构造：单例。初始化所有映射集合。 */
    private GTaskManager() {
        mSyncing = false;
        mCancelled = false;
        mGTaskListHashMap = new HashMap<String, TaskList>();
        mGTaskHashMap = new HashMap<String, Node>();
        mMetaHashMap = new HashMap<String, MetaData>();
        mMetaList = null;
        mLocalDeleteIdMap = new HashSet<Long>();
        mGidToNid = new HashMap<String, Long>();
        mNidToGid = new HashMap<Long, String>();
    }

    /** 获取单例，同步方法保证多线程下只创建一次。 */
    public static synchronized GTaskManager getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskManager();
        }
        return mInstance;
    }

    /**
     * 注入 Activity 上下文，供登录时取 auth token。
     * 由 {@link GTaskSyncService#startSync(Activity)} 在启动同步前调用。
     */
    public synchronized void setActivityContext(Activity activity) {
        // used for getting authtoken
        mActivity = activity;
    }

    /**
     * 执行一次完整同步：登录 → 拉取云端列表 → 双向同步内容 → 收尾。
     *
     * <p>这是同步的唯一对外入口，运行在 {@link GTaskASyncTask} 后台线程。</p>
     *
     * <p>流程：</p>
     * <ol>
     *   <li>若已有同步在跑，直接返回 {@code STATE_SYNC_IN_PROGRESS}（互斥）；</li>
     *   <li>初始化上下文、内容解析器，清空所有缓存映射，置位 {@code mSyncing}；</li>
     *   <li>重置 {@code GTaskClient} 的攒批数组；</li>
     *   <li>登录 Google Tasks（取消则跳过）；</li>
     *   <li>{@link #initGTaskList}：拉取并构建云端列表/任务/元数据的内存模型；</li>
     *   <li>{@link #syncContent}：逐节点比较并执行同步；</li>
     *   <li>异常映射：NetworkFailure → 网络错误，ActionFailure/其它 → 内部错误；</li>
     *   <li>finally 中清理缓存并复位 {@code mSyncing}，保证即使异常也能释放同步锁；</li>
     *   <li>正常返回时，若期间被取消则返回取消，否则成功。</li>
     * </ol>
     *
     * @param context   同步运行上下文
     * @param asyncTask 用于发布进度的 AsyncTask
     * @return 同步结果码（STATE_*）
     */
    public int sync(Context context, GTaskASyncTask asyncTask) {
        // 互斥：已有同步在跑则拒绝本次请求
        if (mSyncing) {
            Log.d(TAG, "Sync is in progress");
            return STATE_SYNC_IN_PROGRESS;
        }
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mSyncing = true;
        mCancelled = false;
        // 清空上次同步可能残留的全部缓存
        mGTaskListHashMap.clear();
        mGTaskHashMap.clear();
        mMetaHashMap.clear();
        mLocalDeleteIdMap.clear();
        mGidToNid.clear();
        mNidToGid.clear();

        try {
            GTaskClient client = GTaskClient.getInstance();
            client.resetUpdateArray();

            // login google task
            // 登录前检查取消标志，避免已取消仍发起网络请求
            if (!mCancelled) {
                if (!client.login(mActivity)) {
                    throw new NetworkFailureException("login google task failed");
                }
            }

            // get the task list from google
            // 发布“初始化列表”进度后拉取云端数据到内存
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_init_list));
            initGTaskList();

            // do content sync work
            // 发布“同步中”进度后执行内容同步
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_syncing));
            syncContent();
        } catch (NetworkFailureException e) {
            Log.e(TAG, e.toString());
            return STATE_NETWORK_ERROR;
        } catch (ActionFailureException e) {
            Log.e(TAG, e.toString());
            return STATE_INTERNAL_ERROR;
        } catch (Exception e) {
            // 兜底：任何未预期异常都按内部错误处理
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return STATE_INTERNAL_ERROR;
        } finally {
            // 无论成功/失败/取消，都清理缓存并释放同步锁
            mGTaskListHashMap.clear();
            mGTaskHashMap.clear();
            mMetaHashMap.clear();
            mLocalDeleteIdMap.clear();
            mGidToNid.clear();
            mNidToGid.clear();
            mSyncing = false;
        }

        // 走到这里说明未抛异常；若期间被取消则返回取消，否则成功
        return mCancelled ? STATE_SYNC_CANCELLED : STATE_SUCCESS;
    }

    /**
     * 拉取云端全部列表并在内存中构建同步模型：meta 列表 + 各任务列表及其任务。
     *
     * <p>分两步：</p>
     * <ol>
     *   <li>先处理 meta 列表（名字等于 {@code MIUI_FOLDER_PREFFIX + FOLDER_META}）：
     *       加载其中的 MetaData 记录到 {@code mMetaHashMap}；若云端不存在则新建一个；</li>
     *   <li>再处理普通任务列表（名字以 {@code MIUI_FOLDER_PREFFIX} 开头但不是 meta）：
     *       逐个列表加载其下任务，并把任务关联的 MetaData 注入。</li>
     * </ol>
     *
     * <p>只有 {@code isWorthSaving()} 为真的节点才会进入内存模型（过滤掉无意义的占位/已彻底删除项）。</p>
     *
     * @throws NetworkFailureException 网络失败
     */
    private void initGTaskList() throws NetworkFailureException {
        if (mCancelled)
            return;
        GTaskClient client = GTaskClient.getInstance();
        try {
            JSONArray jsTaskLists = client.getTaskLists();

            // init meta list first
            // 第一遍：扫描所有列表，找出 meta 列表并加载其 MetaData
            mMetaList = null;
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                if (name
                        .equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                    // 找到 meta 列表，加载其中每条 MetaData（保存本地原始内容，用于还原/冲突）
                    mMetaList = new TaskList();
                    mMetaList.setContentByRemoteJSON(object);

                    // load meta data
                    JSONArray jsMetas = client.getTaskList(gid);
                    for (int j = 0; j < jsMetas.length(); j++) {
                        object = (JSONObject) jsMetas.getJSONObject(j);
                        MetaData metaData = new MetaData();
                        metaData.setContentByRemoteJSON(object);
                        if (metaData.isWorthSaving()) {
                            mMetaList.addChildTask(metaData);
                            if (metaData.getGid() != null) {
                                // 以“被关联任务的 gid”为键索引，供后续任务查 meta
                                mMetaHashMap.put(metaData.getRelatedGid(), metaData);
                            }
                        }
                    }
                }
            }

            // create meta list if not existed
            // 云端没有 meta 列表时主动创建一个（首次同步场景）
            if (mMetaList == null) {
                mMetaList = new TaskList();
                mMetaList.setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                        + GTaskStringUtils.FOLDER_META);
                GTaskClient.getInstance().createTaskList(mMetaList);
            }

            // init task list
            // 第二遍：处理普通任务列表（排除 meta 列表），加载每个列表下的任务
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                if (name.startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX)
                        && !name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                + GTaskStringUtils.FOLDER_META)) {
                    TaskList tasklist = new TaskList();
                    tasklist.setContentByRemoteJSON(object);
                    mGTaskListHashMap.put(gid, tasklist);
                    mGTaskHashMap.put(gid, tasklist);

                    // load tasks
                    // 拉取该列表下的任务，注入关联的 meta，加入模型
                    JSONArray jsTasks = client.getTaskList(gid);
                    for (int j = 0; j < jsTasks.length(); j++) {
                        object = (JSONObject) jsTasks.getJSONObject(j);
                        gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                        Task task = new Task();
                        task.setContentByRemoteJSON(object);
                        if (task.isWorthSaving()) {
                            // 关联 meta（含本地原始 id/内容），用于本地还原
                            task.setMetaInfo(mMetaHashMap.get(gid));
                            tasklist.addChildTask(task);
                            mGTaskHashMap.put(gid, task);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("initGTaskList: handing JSONObject failed");
        }
    }

    /**
     * 同步内容主循环：按“本地删除 → 文件夹 → 普通便签 → 云端剩余 → 清理与刷新”的顺序处理。
     *
     * <p>核心思想：维护 {@code mGTaskHashMap} 作为“云端尚未处理节点”的工作集，
     * 每处理完一个本地匹配项就从集合中移除；最后集合里剩下的就是“云端有而本地没有”的项，
     * 统一按“新增到本地”处理。</p>
     *
     * <p>各阶段：</p>
     * <ol>
     *   <li><b>本地删除</b>：查回收站（trash）里的便签，若云端仍存在则删云端；
     *       同时把这些本地 id 记入 {@code mLocalDeleteIdMap}，最后批量物理删除；</li>
     *   <li><b>文件夹</b>：先同步根文件夹、通话记录文件夹、普通文件夹（{@link #syncFolder}）；</li>
     *   <li><b>普通便签</b>：遍历本地未删除便签，与云端比对判定同步动作
     *       （{@code node.getSyncAction(c)} 比较修改时间决定更新方向，或判定为本地新增/云端删除）；</li>
     *   <li><b>云端剩余</b>：{@code mGTaskHashMap} 中未被匹配的节点 → 新增到本地；</li>
     *   <li><b>清理与刷新</b>：批量删除回收站、提交攒批更新、刷新本地 sync_id。</li>
     * </ol>
     *
     * <p>取消协作：{@code mCancelled} 可被其它线程置位，因此在关键阶段后都要检查，
     * 避免已取消后仍写库或发请求。</p>
     *
     * @throws NetworkFailureException 网络失败
     */
    private void syncContent() throws NetworkFailureException {
        int syncType;
        Cursor c = null;
        String gid;
        Node node;

        mLocalDeleteIdMap.clear();

        if (mCancelled) {
            return;
        }

        // for local deleted note
        // 阶段 1：处理本地已删除（在回收站）的便签
        try {
            // 查回收站中、非系统类型的所有节点
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id=?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, null);
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        // 云端仍存在 → 删除云端对应节点，并从工作集移除
                        mGTaskHashMap.remove(gid);
                        doContentSync(Node.SYNC_ACTION_DEL_REMOTE, node, c);
                    }

                    // 无论云端是否存在，都记入待物理删除集合（最后统一清空回收站）
                    mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                }
            } else {
                Log.w(TAG, "failed to query trash folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // sync folder first
        // 阶段 2：先同步文件夹（便签的父子关系依赖文件夹先建立映射）
        syncFolder();

        // for note existing in database
        // 阶段 3：遍历本地未删除的普通便签，与云端比对
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        // 本地与云端都存在 → 从工作集移除、建立映射、按修改时间判定更新方向
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                    } else {
                        // 云端没有：本地 gtask_id 为空说明是本地新增 → 推到云端；
                        // 否则说明云端已删 → 删除本地
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // local add
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // remote delete
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing note in database");
            }

        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // go through remaining items
        // 阶段 4：工作集中剩余的（云端有、本地没有）→ 全部新增到本地
        Iterator<Map.Entry<String, Node>> iter = mGTaskHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Node> entry = iter.next();
            node = entry.getValue();
            doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
        }

        // mCancelled can be set by another thread, so we neet to check one by
        // one
        // mCancelled 可由其它线程置位，写操作前都要再检查一次
        // clear local delete table
        // 阶段 5：批量物理删除回收站中的本地便签
        if (!mCancelled) {
            if (!DataUtils.batchDeleteNotes(mContentResolver, mLocalDeleteIdMap)) {
                throw new ActionFailureException("failed to batch-delete local deleted notes");
            }
        }

        // refresh local sync id
        // 阶段 6：提交攒批更新，再拉取一次云端并把最新修改时间写回本地 sync_id 列
        if (!mCancelled) {
            GTaskClient.getInstance().commitUpdate();
            refreshLocalSyncId();
        }

    }

    /**
     * 同步文件夹：根文件夹 → 通话记录文件夹 → 本地普通文件夹 → 云端新增文件夹。
     *
     * <p>系统文件夹（根、通话记录）的处理与普通文件夹不同：它们在本地有固定 id，
     * 只在云端名字不对时更新远端名字，不会反向被云端删除或改名。普通文件夹则走与
     * 便签相同的“比对 → 判定动作”逻辑。最后把云端存在但本地没有的列表新增到本地，
     * 并提交一次攒批更新。</p>
     *
     * @throws NetworkFailureException 网络失败
     */
    private void syncFolder() throws NetworkFailureException {
        Cursor c = null;
        String gid;
        Node node;
        int syncType;

        if (mCancelled) {
            return;
        }

        // for root folder
        // 根文件夹：本地固定 id = ID_ROOT_FOLDER
        try {
            c = mContentResolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                    Notes.ID_ROOT_FOLDER), SqlNote.PROJECTION_NOTE, null, null, null);
            if (c != null) {
                c.moveToNext();
                gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                node = mGTaskHashMap.get(gid);
                if (node != null) {
                    // 云端有对应根列表 → 建立映射；仅当名字不是默认名时才更新远端
                    mGTaskHashMap.remove(gid);
                    mGidToNid.put(gid, (long) Notes.ID_ROOT_FOLDER);
                    mNidToGid.put((long) Notes.ID_ROOT_FOLDER, gid);
                    // for system folder, only update remote name if necessary
                    if (!node.getName().equals(
                            GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT))
                        doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                } else {
                    // 云端没有 → 把本地根文件夹推到云端
                    doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                }
            } else {
                Log.w(TAG, "failed to query root folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // for call-note folder
        // 通话记录文件夹：本地固定 id = ID_CALL_RECORD_FOLDER，处理逻辑同根文件夹
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE, "(_id=?)",
                    new String[] {
                        String.valueOf(Notes.ID_CALL_RECORD_FOLDER)
                    }, null);
            if (c != null) {
                if (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, (long) Notes.ID_CALL_RECORD_FOLDER);
                        mNidToGid.put((long) Notes.ID_CALL_RECORD_FOLDER, gid);
                        // for system folder, only update remote name if
                        // necessary
                        if (!node.getName().equals(
                                GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                        + GTaskStringUtils.FOLDER_CALL_NOTE))
                            doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                    } else {
                        doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                    }
                }
            } else {
                Log.w(TAG, "failed to query call note folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // for local existing folders
        // 本地普通文件夹：遍历比对，判定更新方向（与便签逻辑一致）
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        // 本地与云端都有 → 建立映射，按修改时间判定更新方向
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                    } else {
                        // 云端没有：gtask_id 空 → 本地新增推云端；否则 → 云端已删删本地
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // local add
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // remote delete
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // for remote add folders
        // 云端有、本地没有的列表（仍在 mGTaskHashMap 中）→ 新增到本地
        Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, TaskList> entry = iter.next();
            gid = entry.getKey();
            node = entry.getValue();
            if (mGTaskHashMap.containsKey(gid)) {
                mGTaskHashMap.remove(gid);
                doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
            }
        }

        // 文件夹同步完成后提交一次攒批更新
        if (!mCancelled)
            GTaskClient.getInstance().commitUpdate();
    }

    /**
     * 按同步动作类型分派到具体执行方法。所有本地/远端增删改都经这里统一入口。
     *
     * <p>删除时会连带删除关联的 MetaData（因为 meta 记录依附于被关联节点而存在）；
     * 冲突动作 {@code SYNC_ACTION_UPDATE_CONFLICT} 当前简单以本地覆盖远端，未做合并。</p>
     *
     * @param syncType 同步动作类型（Node.SYNC_ACTION_*）
     * @param node     涉及的云端节点；本地新增/删除类动作可能为 null
     * @param c        本地游标，提供本地数据；纯云端→本地动作时为 null
     * @throws NetworkFailureException 网络失败
     */
    private void doContentSync(int syncType, Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        MetaData meta;
        switch (syncType) {
            case Node.SYNC_ACTION_ADD_LOCAL:
                // 云端有、本地没有 → 新增到本地
                addLocalNode(node);
                break;
            case Node.SYNC_ACTION_ADD_REMOTE:
                // 本地有、云端没有 → 推到云端
                addRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_DEL_LOCAL:
                // 云端已删 → 删除本地（先删关联 meta，再把本地 id 记入待删除集合）
                meta = mMetaHashMap.get(c.getString(SqlNote.GTASK_ID_COLUMN));
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                break;
            case Node.SYNC_ACTION_DEL_REMOTE:
                // 本地已删 → 删除云端（先删关联 meta，再删节点本身）
                meta = mMetaHashMap.get(node.getGid());
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                GTaskClient.getInstance().deleteNode(node);
                break;
            case Node.SYNC_ACTION_UPDATE_LOCAL:
                // 云端较新 → 用云端覆盖本地
                updateLocalNode(node, c);
                break;
            case Node.SYNC_ACTION_UPDATE_REMOTE:
                // 本地较新 → 用本地覆盖云端
                updateRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_UPDATE_CONFLICT:
                // merging both modifications maybe a good idea
                // right now just use local update simply
                // 冲突：暂不做合并，直接以本地覆盖远端
                updateRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_NONE:
                // 无需同步
                break;
            case Node.SYNC_ACTION_ERROR:
            default:
                throw new ActionFailureException("unkown sync action type");
        }
    }

    /**
     * 把一个云端节点新增到本地数据库。
     *
     * <p>区分 TaskList 与 Task：</p>
     * <ul>
     *   <li>TaskList（文件夹）：默认文件夹/通话记录文件夹复用本地固定 id；
     *       普通文件夹用云端内容创建，挂到根文件夹下；</li>
     *   <li>Task（便签）：从云端内容还原本地 JSON，但如果 meta 中保存的本地 id/data id
     *       已被占用，则移除这些 id 让数据库重新分配，避免主键冲突；父文件夹通过
     *       {@code mGidToNid} 映射得到。</li>
     * </ul>
     * 创建后写回 gid、更新双向映射，并把本地内容同步到云端 meta 记录。
     *
     * @param node 云端节点
     * @throws NetworkFailureException 网络失败
     */
    private void addLocalNode(Node node) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote;
        if (node instanceof TaskList) {
            // 文件夹：系统文件夹复用固定 id，普通文件夹新建并挂到根下
            if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT)) {
                sqlNote = new SqlNote(mContext, Notes.ID_ROOT_FOLDER);
            } else if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE)) {
                sqlNote = new SqlNote(mContext, Notes.ID_CALL_RECORD_FOLDER);
            } else {
                sqlNote = new SqlNote(mContext);
                sqlNote.setContent(node.getLocalJSONFromContent());
                sqlNote.setParentId(Notes.ID_ROOT_FOLDER);
            }
        } else {
            // 便签：还原云端内容为本地 JSON
            sqlNote = new SqlNote(mContext);
            JSONObject js = node.getLocalJSONFromContent();
            try {
                // 若 meta 中保存的本地 note id 已被占用，移除它让数据库重新分配
                if (js.has(GTaskStringUtils.META_HEAD_NOTE)) {
                    JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                    if (note.has(NoteColumns.ID)) {
                        long id = note.getLong(NoteColumns.ID);
                        if (DataUtils.existInNoteDatabase(mContentResolver, id)) {
                            // the id is not available, have to create a new one
                            note.remove(NoteColumns.ID);
                        }
                    }
                }

                // 同理：data id 已被占用则移除
                if (js.has(GTaskStringUtils.META_HEAD_DATA)) {
                    JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject data = dataArray.getJSONObject(i);
                        if (data.has(DataColumns.ID)) {
                            long dataId = data.getLong(DataColumns.ID);
                            if (DataUtils.existInDataDatabase(mContentResolver, dataId)) {
                                // the data id is not available, have to create
                                // a new one
                                data.remove(DataColumns.ID);
                            }
                        }
                    }

                }
            } catch (JSONException e) {
                // meta 解析失败不阻断，继续用现有内容创建
                Log.w(TAG, e.toString());
                e.printStackTrace();
            }
            sqlNote.setContent(js);

            // 通过 gid→nid 映射找到父文件夹的本地 id
            Long parentId = mGidToNid.get(((Task) node).getParent().getGid());
            if (parentId == null) {
                Log.e(TAG, "cannot find task's parent id locally");
                throw new ActionFailureException("cannot add local node");
            }
            sqlNote.setParentId(parentId.longValue());
        }

        // create the local node
        // 写入云端 gid 并提交（commit(false) 表示新建）
        sqlNote.setGtaskId(node.getGid());
        sqlNote.commit(false);

        // update gid-nid mapping
        // 建立双向映射，供后续移动/父子换算使用
        mGidToNid.put(node.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), node.getGid());

        // update meta
        // 把本地内容同步到云端 meta 记录，便于下次还原
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /**
     * 用云端内容覆盖本地节点（云端较新）。
     * 文件夹的父固定为根；便签的父通过 gid→nid 映射取得。提交后同步更新 meta。
     *
     * @param node 云端节点
     * @param c    本地游标，定位要更新的本地行
     * @throws NetworkFailureException 网络失败
     */
    private void updateLocalNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote;
        // update the note locally
        // 用游标定位本地行，再用云端内容覆盖
        sqlNote = new SqlNote(mContext, c);
        sqlNote.setContent(node.getLocalJSONFromContent());

        // 父文件夹：便签查映射，文件夹默认为根
        Long parentId = (node instanceof Task) ? mGidToNid.get(((Task) node).getParent().getGid())
                : new Long(Notes.ID_ROOT_FOLDER);
        if (parentId == null) {
            Log.e(TAG, "cannot find task's parent id locally");
            throw new ActionFailureException("cannot update local node");
        }
        sqlNote.setParentId(parentId.longValue());
        sqlNote.commit(true);

        // update meta info
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /**
     * 把本地节点新增到云端。
     *
     * <p>便签：创建云端 Task，加入对应父列表，写回 gid；文件夹：先按名字在云端列表中
     * 查找是否已存在（避免重复创建），存在则复用，不存在才新建。最后把云端 gid 写回本地，
     * 清除“本地已修改”标志并建立双向映射。</p>
     *
     * @param node 传 null（此动作用本地游标数据，不依赖云端节点对象）
     * @param c    本地游标，提供要上传的本地数据
     * @throws NetworkFailureException 网络失败
     */
    private void addRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);
        Node n;

        // update remotely
        if (sqlNote.isNoteType()) {
            // 便签：从本地内容构造 Task，找到父列表后创建
            Task task = new Task();
            task.setContentByLocalJSON(sqlNote.getContent());

            // 父列表通过 nid→gid 映射找到
            String parentGid = mNidToGid.get(sqlNote.getParentId());
            if (parentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot add remote task");
            }
            mGTaskListHashMap.get(parentGid).addChildTask(task);

            GTaskClient.getInstance().createTask(task);
            n = (Node) task;

            // add meta
            updateRemoteMeta(task.getGid(), sqlNote);
        } else {
            TaskList tasklist = null;

            // we need to skip folder if it has already existed
            // 文件夹：先按名字在云端现有列表中查找，避免重复创建
            String folderName = GTaskStringUtils.MIUI_FOLDER_PREFFIX;
            if (sqlNote.getId() == Notes.ID_ROOT_FOLDER)
                folderName += GTaskStringUtils.FOLDER_DEFAULT;
            else if (sqlNote.getId() == Notes.ID_CALL_RECORD_FOLDER)
                folderName += GTaskStringUtils.FOLDER_CALL_NOTE;
            else
                folderName += sqlNote.getSnippet();

            Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, TaskList> entry = iter.next();
                String gid = entry.getKey();
                TaskList list = entry.getValue();

                if (list.getName().equals(folderName)) {
                    // 云端已存在同名列表 → 复用，并从工作集移除（避免被当作“云端新增”）
                    tasklist = list;
                    if (mGTaskHashMap.containsKey(gid)) {
                        mGTaskHashMap.remove(gid);
                    }
                    break;
                }
            }

            // no match we can add now
            // 云端不存在 → 新建列表
            if (tasklist == null) {
                tasklist = new TaskList();
                tasklist.setContentByLocalJSON(sqlNote.getContent());
                GTaskClient.getInstance().createTaskList(tasklist);
                mGTaskListHashMap.put(tasklist.getGid(), tasklist);
            }
            n = (Node) tasklist;
        }

        // update local note
        // 把云端分配的 gid 写回本地，清除本地修改标志
        sqlNote.setGtaskId(n.getGid());
        sqlNote.commit(false);
        sqlNote.resetLocalModified();
        sqlNote.commit(true);

        // gid-id mapping
        mGidToNid.put(n.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), n.getGid());
    }

    /**
     * 用本地内容覆盖云端节点（本地较新），并处理便签可能发生的跨文件夹移动。
     * 若父列表发生变化，先在内存模型中移动，再调用云端 moveTask。
     *
     * @param node 云端节点
     * @param c    本地游标
     * @throws NetworkFailureException 网络失败
     */
    private void updateRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);

        // update remotely
        // 用本地内容更新云端节点（加入攒批，稍后统一提交）
        node.setContentByLocalJSON(sqlNote.getContent());
        GTaskClient.getInstance().addUpdateNode(node);

        // update meta
        updateRemoteMeta(node.getGid(), sqlNote);

        // move task if necessary
        // 便签可能更换了文件夹：比较移动前后父列表，不一致则调用 moveTask
        if (sqlNote.isNoteType()) {
            Task task = (Task) node;
            TaskList preParentList = task.getParent();

            String curParentGid = mNidToGid.get(sqlNote.getParentId());
            if (curParentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot update remote task");
            }
            TaskList curParentList = mGTaskListHashMap.get(curParentGid);

            if (preParentList != curParentList) {
                // 先更新内存模型中的父子关系，再通知云端
                preParentList.removeChildTask(task);
                curParentList.addChildTask(task);
                GTaskClient.getInstance().moveTask(task, preParentList, curParentList);
            }
        }

        // clear local modified flag
        // 同步完成，清除“本地已修改”标志
        sqlNote.resetLocalModified();
        sqlNote.commit(true);
    }

    /**
     * 把本地便签内容写入云端 meta 记录：meta 存在则更新，不存在则在 meta 列表下新建。
     * 只对便签类型生效（文件夹不需要 meta）。meta 用于把云端任务还原为本地便签时
     * 保留本地原始 id 与内容、辅助冲突判定。
     *
     * @param gid     被关联任务的云端 id
     * @param sqlNote 本地便签
     * @throws NetworkFailureException 网络失败
     */
    private void updateRemoteMeta(String gid, SqlNote sqlNote) throws NetworkFailureException {
        if (sqlNote != null && sqlNote.isNoteType()) {
            MetaData metaData = mMetaHashMap.get(gid);
            if (metaData != null) {
                // meta 已存在 → 更新并加入攒批
                metaData.setMeta(gid, sqlNote.getContent());
                GTaskClient.getInstance().addUpdateNode(metaData);
            } else {
                // meta 不存在 → 新建并挂到 meta 列表下
                metaData = new MetaData();
                metaData.setMeta(gid, sqlNote.getContent());
                mMetaList.addChildTask(metaData);
                mMetaHashMap.put(gid, metaData);
                GTaskClient.getInstance().createTask(metaData);
            }
        }
    }

    /**
     * 同步末尾刷新本地 {@code sync_id} 列：重新拉取一次云端最新数据，
     * 把每个本地便签对应云端节点的 {@code lastModified} 写入本地 {@code SYNC_ID} 列，
     * 作为下次同步时判定“谁更新”的基准。
     *
     * <p>若发现某个本地便签在最新云端数据中找不到对应 gid，视为同步不一致，抛内部错误。</p>
     *
     * @throws NetworkFailureException 网络失败
     */
    private void refreshLocalSyncId() throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        // get the latest gtask list
        // 重新拉取云端最新状态，覆盖到内存模型
        mGTaskHashMap.clear();
        mGTaskListHashMap.clear();
        mMetaHashMap.clear();
        initGTaskList();

        Cursor c = null;
        try {
            // 遍历本地所有非系统、非回收站的便签与文件夹
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    Node node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        // 找到对应云端节点 → 把 lastModified 写入本地 sync_id 列
                        mGTaskHashMap.remove(gid);
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SYNC_ID, node.getLastModified());
                        mContentResolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                                c.getLong(SqlNote.ID_COLUMN)), values, null, null);
                    } else {
                        // 本地有 gid 但云端最新数据里找不到 → 同步不一致，报错
                        Log.e(TAG, "something is missed");
                        throw new ActionFailureException(
                                "some local items don't have gid after sync");
                    }
                }
            } else {
                Log.w(TAG, "failed to query local note to refresh sync id");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
    }

    /** 返回当前同步账号名（成功通知等处展示用）。 */
    public String getSyncAccount() {
        return GTaskClient.getInstance().getSyncAccount().name;
    }

    /** 请求取消同步：只置位 {@code mCancelled}，由同步循环在各检查点退出。 */
    public void cancelSync() {
        mCancelled = true;
    }
}
