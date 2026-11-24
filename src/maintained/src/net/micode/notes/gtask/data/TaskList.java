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
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;


/**
 * Google Tasks 中的“任务分组”节点，对应小米便签中的文件夹。
 *
 * <p>分组本身只保存名称和顺序；其子任务通过 {@link #mChildren} 维护，并依赖
 * 每个 {@link Task} 的 priorSibling 指针维持远端列表顺序。系统文件夹（根目录、
 * 通话记录）使用固定名称与远端约定对应，普通文件夹则以
 * {@link GTaskStringUtils#MIUI_FOLDER_PREFFIX} 作为前缀。</p>
 *
 * <p>文件夹冲突的策略与便签不同：两侧都修改时直接采用本地版本，因为文件夹只
 * 承载名称和归属关系，本地操作通常更符合用户预期。</p>
 */
public class TaskList extends Node {
    private static final String TAG = TaskList.class.getSimpleName();

    /** 分组在远端列表中的序号，用于新建分组时定位。 */
    private int mIndex;

    /** 分组下的任务，顺序即远端展示顺序。 */
    private ArrayList<Task> mChildren;

    public TaskList() {
        super();
        mChildren = new ArrayList<Task>();
        mIndex = 1;
    }

    /**
     * 构造“新建任务分组”动作。
     * 分组只提交名称、顺序和实体类型，不包含子任务；子任务会在之后按序创建。
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
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mIndex);

            // entity_delta
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-create jsonobject");
        }

        return js;
    }

    /**
     * 构造“更新任务分组”动作。
     * 只允许修改名称和删除标记；分组内任务的增删移动由任务自身的动作完成。
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
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-update jsonobject");
        }

        return js;
    }

    /**
     * 从远端 JSON 读取分组字段。
     * 分组只关心 GID、最后修改时间和名称；字段缺失时保留原值。
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

            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get tasklist content from jsonobject");
            }
        }
    }

    /**
     * 用本地文件夹 JSON 设置分组名称。
     *
     * <p>普通文件夹加统一前缀；根目录和通话记录这两个系统文件夹分别映射为
     * “Default”和“Call_Note”，以保证不同设备上的固定文件夹能互相对应。
     * 非法类型或未知系统文件夹只记录错误，不抛异常。</p>
     */
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }

        try {
            JSONObject folder = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                String name = folder.getString(NoteColumns.SNIPPET);
                setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + name);
            } else if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                if (folder.getLong(NoteColumns.ID) == Notes.ID_ROOT_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT);
                else if (folder.getLong(NoteColumns.ID) == Notes.ID_CALL_RECORD_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                            + GTaskStringUtils.FOLDER_CALL_NOTE);
                else
                    Log.e(TAG, "invalid system folder");
            } else {
                Log.e(TAG, "error type");
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 把分组还原为本地文件夹 JSON。
     *
     * <p>去掉 MIUI 前缀后作为文件夹 snippet；名称恰为 Default 或 Call_Note 时
     * 标记为系统文件夹，否则按普通文件夹写入。返回值只包含 meta_note，
     * 不包含 data 数组，因为文件夹没有正文。</p>
     */
    public JSONObject getLocalJSONFromContent() {
        try {
            JSONObject js = new JSONObject();
            JSONObject folder = new JSONObject();

            String folderName = getName();
            if (getName().startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX))
                folderName = folderName.substring(GTaskStringUtils.MIUI_FOLDER_PREFFIX.length(),
                        folderName.length());
            folder.put(NoteColumns.SNIPPET, folderName);
            if (folderName.equals(GTaskStringUtils.FOLDER_DEFAULT)
                    || folderName.equals(GTaskStringUtils.FOLDER_CALL_NOTE))
                folder.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
            else
                folder.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);

            js.put(GTaskStringUtils.META_HEAD_NOTE, folder);

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 判断文件夹的同步方向。
     *
     * <p>与便签不同：当两侧都修改时不做冲突标记，而是直接采用本地修改
     * （{@link #SYNC_ACTION_UPDATE_REMOTE}），因为文件夹冲突通常只是名称或
     * 归属差异，保留用户刚做的本地操作更合理。</p>
     */
    public int getSyncAction(Cursor c) {
        try {
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
                    // for folder conflicts, just apply local modification
                    return SYNC_ACTION_UPDATE_REMOTE;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    public int getChildTaskCount() {
        return mChildren.size();
    }

    /**
     * 追加子任务到分组末尾，并同步维护其 priorSibling 和 parent 指针。
     * 重复任务或 null 会被拒绝。
     */
    public boolean addChildTask(Task task) {
        boolean ret = false;
        if (task != null && !mChildren.contains(task)) {
            ret = mChildren.add(task);
            if (ret) {
                // need to set prior sibling and parent
                task.setPriorSibling(mChildren.isEmpty() ? null : mChildren
                        .get(mChildren.size() - 1));
                task.setParent(this);
            }
        }
        return ret;
    }

    /**
     * 在指定下标插入子任务。
     * 除插入元素外，还要修正前驱任务的 priorSibling 和插入点后一个任务的
     * priorSibling，否则远端顺序会错乱。
     */
    public boolean addChildTask(Task task, int index) {
        if (index < 0 || index > mChildren.size()) {
            Log.e(TAG, "add child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (task != null && pos == -1) {
            mChildren.add(index, task);

            // update the task list
            Task preTask = null;
            Task afterTask = null;
            if (index != 0)
                preTask = mChildren.get(index - 1);
            if (index != mChildren.size() - 1)
                afterTask = mChildren.get(index + 1);

            task.setPriorSibling(preTask);
            if (afterTask != null)
                afterTask.setPriorSibling(task);
        }

        return true;
    }

    /**
     * 从分组中移除子任务，并把被移除任务的后继节点的 priorSibling 接到前驱上。
     * 同时清空被移除任务的 parent 和 priorSibling，避免留下悬空引用。
     */
    public boolean removeChildTask(Task task) {
        boolean ret = false;
        int index = mChildren.indexOf(task);
        if (index != -1) {
            ret = mChildren.remove(task);

            if (ret) {
                // reset prior sibling and parent
                task.setPriorSibling(null);
                task.setParent(null);

                // update the task list
                if (index != mChildren.size()) {
                    mChildren.get(index).setPriorSibling(
                            index == 0 ? null : mChildren.get(index - 1));
                }
            }
        }
        return ret;
    }

    /**
     * 把任务移动到分组内的新下标。
     * 先移除再插入，因此下标按“移除后的列表”解释；非法下标或任务不在本组时返回 false。
     */
    public boolean moveChildTask(Task task, int index) {

        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "move child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (pos == -1) {
            Log.e(TAG, "move child task: the task should in the list");
            return false;
        }

        if (pos == index)
            return true;
        return (removeChildTask(task) && addChildTask(task, index));
    }

    /**
     * 按远端 GID 在子任务中查找；找不到返回 null。
     * 时间复杂度 O(n)，任务量通常很小，因此未额外建索引。
     */
    public Task findChildTaskByGid(String gid) {
        for (int i = 0; i < mChildren.size(); i++) {
            Task t = mChildren.get(i);
            if (t.getGid().equals(gid)) {
                return t;
            }
        }
        return null;
    }

    public int getChildTaskIndex(Task task) {
        return mChildren.indexOf(task);
    }

    public Task getChildTaskByIndex(int index) {
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "getTaskByIndex: invalid index");
            return null;
        }
        return mChildren.get(index);
    }

    public Task getChilTaskByGid(String gid) {
        for (Task task : mChildren) {
            if (task.getGid().equals(gid))
                return task;
        }
        return null;
    }

    /**
     * 返回分组内部使用的任务列表，调用方可以直接修改。
     * 该方法是包内同步逻辑的便捷入口，外部若需要保持 priorSibling 一致，
     * 应优先使用 add/remove/move 系列方法。
     */
    public ArrayList<Task> getChildTaskList() {
        return this.mChildren;
    }

    public void setIndex(int index) {
        this.mIndex = index;
    }

    public int getIndex() {
        return this.mIndex;
    }
}
