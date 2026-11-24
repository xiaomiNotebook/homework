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

import org.json.JSONObject;

/**
 * 同步节点的抽象基类。
 *
 * <p>小米便签把本地“便签/文件夹”与 Google Tasks 中的“任务/任务分组”做双向同步。
 * 本类保存两侧都需要的公共状态（远端 GID、显示名称、远端修改时间、删除标记），
 * 并把“生成远端动作、读取远端 JSON、生成本地 JSON、判断同步动作”抽象为模板方法，
 * 由 {@link Task}（任务）和 {@link TaskList}（任务分组，对应便签文件夹）分别实现。</p>
 *
 * <p>子类只负责各自 JSON 结构的差异；真正的网络提交和数据库写入由
 * {@code GTaskManager} 统一调度。</p>
 */
public abstract class Node {
    /** 本地与远端均未变化，本次无需同步。 */
    public static final int SYNC_ACTION_NONE = 0;

    /** 本地新增了节点，需要把本地内容上传到远端。 */
    public static final int SYNC_ACTION_ADD_REMOTE = 1;

    /** 远端新增了节点，需要在本地创建对应记录。 */
    public static final int SYNC_ACTION_ADD_LOCAL = 2;

    /** 本地已删除节点，需要删除远端对应记录。 */
    public static final int SYNC_ACTION_DEL_REMOTE = 3;

    /** 远端已删除节点，需要清理本地对应记录。 */
    public static final int SYNC_ACTION_DEL_LOCAL = 4;

    /** 本地内容更新，需要覆盖远端。 */
    public static final int SYNC_ACTION_UPDATE_REMOTE = 5;

    /** 远端内容更新，需要覆盖本地。 */
    public static final int SYNC_ACTION_UPDATE_LOCAL = 6;

    /** 两侧都修改过，产生冲突，需要由上层决定合并策略。 */
    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7;

    /** 数据不完整或状态异常，无法安全同步。 */
    public static final int SYNC_ACTION_ERROR = 8;

    /** 远端全局唯一 ID；本地新建、尚未上传时可能为 null。 */
    private String mGid;

    /** 节点显示名称。对任务来说是标题/摘要，对分组来说是文件夹名。 */
    private String mName;

    /** 远端记录的最后修改时间，用于判断同步方向。 */
    private long mLastModified;

    /** 远端删除标记，更新动作中会带给服务端。 */
    private boolean mDeleted;

    public Node() {
        mGid = null;
        mName = "";
        mLastModified = 0;
        mDeleted = false;
    }

    /**
     * 生成“新建节点”的远端动作 JSON。
     *
     * @param actionId 本次同步批次内唯一的动作编号
     * @return 可直接加入 action_list 的 JSON 对象
     */
    public abstract JSONObject getCreateAction(int actionId);

    /**
     * 生成“更新节点”的远端动作 JSON。
     *
     * @param actionId 本次同步批次内唯一的动作编号
     * @return 可直接加入 action_list 的 JSON 对象
     */
    public abstract JSONObject getUpdateAction(int actionId);

    /**
     * 用远端返回的 JSON 填充节点状态。
     * 子类需要按自身的 JSON 字段约定解析，并处理字段缺失的情况。
     */
    public abstract void setContentByRemoteJSON(JSONObject js);

    /**
     * 用本地便签/文件夹导出的 JSON 填充节点状态。
     * 该 JSON 由 {@code SqlNote#getContent()} 生成，字段结构与远端 JSON 不同。
     */
    public abstract void setContentByLocalJSON(JSONObject js);

    /**
     * 把当前节点转换成可写回本地数据库的 JSON。
     * 注意：本方法只做结构转换，不直接写数据库。
     */
    public abstract JSONObject getLocalJSONFromContent();

    /**
     * 根据本地数据库游标和远端修改时间判断本次同步动作。
     *
     * @param c 至少包含 {@code SqlNote.PROJECTION_NOTE} 列的查询结果，当前行即对应本地记录
     * @return {@link #SYNC_ACTION_NONE} 等同步动作常量
     */
    public abstract int getSyncAction(Cursor c);

    public void setGid(String gid) {
        this.mGid = gid;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    public String getGid() {
        return this.mGid;
    }

    public String getName() {
        return this.mName;
    }

    public long getLastModified() {
        return this.mLastModified;
    }

    public boolean getDeleted() {
        return this.mDeleted;
    }

}
