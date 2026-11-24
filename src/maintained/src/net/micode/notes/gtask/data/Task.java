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

import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * Google Tasks 中的“任务”节点，对应小米便签中的一条便签。
 *
 * <p>远端任务的 {@code name} 保存便签标题或正文摘要，{@code notes} 保存完整的
 * 便签元数据 JSON；本类负责在这两种表示之间转换，并根据本地游标、远端修改时间
 * 和 {@code local_modified} 标记决定同步方向。</p>
 *
 * <p>任务在列表中的位置由前驱兄弟节点 {@link #mPriorSibling} 和父分组
 * {@link #mParent} 共同描述，新建任务时需要把这两个关系一起写入远端动作。</p>
 */
public class Task extends Node {
    private static final String TAG = Task.class.getSimpleName();

    /** 远端任务的完成状态；便签本身不使用该字段，但需要原样保留。 */
    private boolean mCompleted;

    /** 完整的便签元数据 JSON 字符串，对应远端任务的 notes 字段。 */
    private String mNotes;

    /** 从 MetaData 节点解析出的便签 JSON，供冲突判断和回写本地使用。 */
    private JSONObject mMetaInfo;

    /** 同一分组中的前一个任务；用于维持远端列表顺序。 */
    private Task mPriorSibling;

    /** 所属的任务分组（对应本地文件夹）。 */
    private TaskList mParent;

    public Task() {
        super();
        mCompleted = false;
        mNotes = null;
        mPriorSibling = null;
        mParent = null;
        mMetaInfo = null;
    }

    /**
     * 构造“新建任务”动作。
     *
     * <p>除名称和 notes 外，还需要写入父分组 GID、目标父类型、所在列表 ID、
     * 在兄弟节点中的序号以及前驱兄弟 GID。调用前必须已通过
     * {@link #setParent(TaskList)} 和 {@link #setPriorSibling(Task)} 建立关系，
     * 否则会在访问父分组时抛出空指针异常。</p>
     */
    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // action_id
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // index
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mParent.getChildTaskIndex(this));

            // entity_delta
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_TASK);
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

            // parent_id
            js.put(GTaskStringUtils.GTASK_JSON_PARENT_ID, mParent.getGid());

            // dest_parent_type
            js.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);

            // list_id
            js.put(GTaskStringUtils.GTASK_JSON_LIST_ID, mParent.getGid());

            // prior_sibling_id
            if (mPriorSibling != null) {
                js.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, mPriorSibling.getGid());
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate task-create jsonobject");
        }

        return js;
    }

    /**
     * 构造“更新任务”动作。
     *
     * <p>只提交名称、notes 和删除标记；任务的位置变化由移动动作单独处理。
     * 若 notes 为 null 则不提交该字段，避免把远端已有内容清空。</p>
     */
    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            // action_id
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // id
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // entity_delta
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate task-update jsonobject");
        }

        return js;
    }

    /**
     * 用 Google Tasks 返回的 JSON 填充任务字段。
     *
     * <p>远端字段是可选的，只有存在时才覆盖本地缓存；解析失败会抛出
     * {@link ActionFailureException}，由同步管理器终止本轮同步并提示网络/协议错误。</p>
     */
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                // id
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }

                // last_modified
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }

                // name
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }

                // notes
                if (js.has(GTaskStringUtils.GTASK_JSON_NOTES)) {
                    setNotes(js.getString(GTaskStringUtils.GTASK_JSON_NOTES));
                }

                // deleted
                if (js.has(GTaskStringUtils.GTASK_JSON_DELETED)) {
                    setDeleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_DELETED));
                }

                // completed
                if (js.has(GTaskStringUtils.GTASK_JSON_COMPLETED)) {
                    setCompleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_COMPLETED));
                }
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get task content from jsonobject");
            }
        }
    }

    /**
     * 用本地便签 JSON 填充任务名称。
     *
     * <p>只在 {@code meta_note.type == TYPE_NOTE} 时处理，并优先取
     * {@code meta_data} 中 MIME 类型为纯文本的第一条内容作为任务名。
     * 若 JSON 结构不完整只记录日志，不抛出异常，因为该路径常用于容错同步。</p>
     */
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)
                || !js.has(GTaskStringUtils.META_HEAD_DATA)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }

        try {
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

            if (note.getInt(NoteColumns.TYPE) != Notes.TYPE_NOTE) {
                Log.e(TAG, "invalid type");
                return;
            }

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject data = dataArray.getJSONObject(i);
                if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                    setName(data.getString(DataColumns.CONTENT));
                    break;
                }
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 把当前任务还原成小米便签可识别的 JSON。
     *
     * <p>分两种情况：</p>
     * <ul>
     *   <li>没有 meta 信息：说明是远端新建、本地尚未建库的任务，只构造最小可用的
     *       note/data 结构，内容取任务名称；</li>
     *   <li>已有 meta 信息：在原始元数据上只替换纯文本 data 的 content，
     *       保留提醒、背景色、文件夹等本地属性。</li>
     * </ul>
     *
     * <p>任务名称为 null 时视为空便签，返回 null 让上层跳过创建。</p>
     */
    public JSONObject getLocalJSONFromContent() {
        String name = getName();
        try {
            if (mMetaInfo == null) {
                // new task created from web
                if (name == null) {
                    Log.w(TAG, "the note seems to be an empty one");
                    return null;
                }

                JSONObject js = new JSONObject();
                JSONObject note = new JSONObject();
                JSONArray dataArray = new JSONArray();
                JSONObject data = new JSONObject();
                data.put(DataColumns.CONTENT, name);
                dataArray.put(data);
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
                return js;
            } else {
                // synced task
                JSONObject note = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                JSONArray dataArray = mMetaInfo.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                        data.put(DataColumns.CONTENT, getName());
                        break;
                    }
                }

                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                return mMetaInfo;
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 关联从 MetaData 节点解析出的便签元数据。
     * 元数据 notes 不是合法 JSON 时只清空缓存并记录警告，不中断同步。
     */
    public void setMetaInfo(MetaData metaData) {
        if (metaData != null && metaData.getNotes() != null) {
            try {
                mMetaInfo = new JSONObject(metaData.getNotes());
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                mMetaInfo = null;
            }
        }
    }

    /**
     * 判断单条便签的同步方向。
     *
     * <p>判定依据是本地 {@code local_modified}、上次同步时间 {@code sync_id}
     * 与远端 {@code last_modified} 的关系：</p>
     * <ul>
     *   <li>本地未改且远端时间未变：{@link #SYNC_ACTION_NONE}；</li>
     *   <li>本地未改但远端已变：{@link #SYNC_ACTION_UPDATE_LOCAL}；</li>
     *   <li>本地已改且远端未变：{@link #SYNC_ACTION_UPDATE_REMOTE}；</li>
     *   <li>两侧都改：{@link #SYNC_ACTION_UPDATE_CONFLICT}；</li>
     *   <li>远端元数据缺失、便签 ID 不匹配或 GID 不匹配：按可恢复程度返回
     *       {@link #SYNC_ACTION_UPDATE_REMOTE}/{@link #SYNC_ACTION_UPDATE_LOCAL}/
     *       {@link #SYNC_ACTION_ERROR}。</li>
     * </ul>
     *
     * <p>任何解析异常都会兜底为 {@link #SYNC_ACTION_ERROR}，避免在状态不明时
     * 误删或误覆盖用户数据。</p>
     */
    public int getSyncAction(Cursor c) {
        try {
            JSONObject noteInfo = null;
            if (mMetaInfo != null && mMetaInfo.has(GTaskStringUtils.META_HEAD_NOTE)) {
                noteInfo = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            }

            if (noteInfo == null) {
                Log.w(TAG, "it seems that note meta has been deleted");
                return SYNC_ACTION_UPDATE_REMOTE;
            }

            if (!noteInfo.has(NoteColumns.ID)) {
                Log.w(TAG, "remote note id seems to be deleted");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            // validate the note id now
            if (c.getLong(SqlNote.ID_COLUMN) != noteInfo.getLong(NoteColumns.ID)) {
                Log.w(TAG, "note id doesn't match");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // there is no local update
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // no update both side
                    return SYNC_ACTION_NONE;
                } else {
                    // apply remote to local
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // validate gtask id
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // local modification only
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    return SYNC_ACTION_UPDATE_CONFLICT;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    /**
     * 判断任务是否包含可保存的内容。
     * 有元数据、非空名称或非空 notes 三者满足其一即可；全空任务会被跳过，
     * 防止在远端产生无意义的空记录。
     */
    public boolean isWorthSaving() {
        return mMetaInfo != null || (getName() != null && getName().trim().length() > 0)
                || (getNotes() != null && getNotes().trim().length() > 0);
    }

    public void setCompleted(boolean completed) {
        this.mCompleted = completed;
    }

    public void setNotes(String notes) {
        this.mNotes = notes;
    }

    public void setPriorSibling(Task priorSibling) {
        this.mPriorSibling = priorSibling;
    }

    public void setParent(TaskList parent) {
        this.mParent = parent;
    }

    public boolean getCompleted() {
        return this.mCompleted;
    }

    public String getNotes() {
        return this.mNotes;
    }

    public Task getPriorSibling() {
        return this.mPriorSibling;
    }

    public TaskList getParent() {
        return this.mParent;
    }

}
