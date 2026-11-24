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

import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * 便签元数据节点。
 *
 * <p>同步时，每条便签除了对应的 Google Task 外，还需要一个隐藏的“元数据任务”来保存
 * 本地便签的完整 JSON（包括便签 ID、所属文件夹、背景色、提醒时间等）。这个任务在
 * Google Tasks 中表现为固定名称 {@link GTaskStringUtils#META_NOTE_NAME} 的一条任务，
 * 其 notes 字段就是元数据 JSON。</p>
 *
 * <p>本类继承 {@link Task} 复用远端动作和字段解析能力，但只允许“由远端读取”，
 * 不允许从本地内容反向生成。元数据的创建和更新由同步管理器直接控制，避免它被
 * 当成普通便签参与本地写入。</p>
 */
public class MetaData extends Task {
    private final static String TAG = MetaData.class.getSimpleName();

    /** 元数据中记录的、与之对应的 Google Task 的 GID。 */
    private String mRelatedGid = null;

    /**
     * 把“关联任务的 GID”写入元数据 JSON，并把当前节点设置为固定名称的元数据任务。
     *
     * @param gid      被描述的普通任务的远端 GID
     * @param metaInfo 便签完整内容的 JSON；方法会就地写入 {@code meta_gid} 字段
     */
    public void setMeta(String gid, JSONObject metaInfo) {
        try {
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid);
        } catch (JSONException e) {
            // JSONObject 的 key 为 null 等极端情况才会失败；此时仍保留其余元数据。
            Log.e(TAG, "failed to put related gid");
        }
        setNotes(metaInfo.toString());
        setName(GTaskStringUtils.META_NOTE_NAME);
    }

    /**
     * 返回元数据中记录的关联任务 GID。
     * 只有在 {@link #setContentByRemoteJSON(JSONObject)} 成功解析后才有值。
     */
    public String getRelatedGid() {
        return mRelatedGid;
    }

    /**
     * 元数据任务只要 notes 非空就值得保存。
     * 它没有用户可见的标题或正文，因此不能复用父类基于名称/notes 为空的判断。
     */
    @Override
    public boolean isWorthSaving() {
        return getNotes() != null;
    }

    /**
     * 从远端 JSON 读取元数据任务。
     *
     * <p>先让父类解析通用的 id/name/notes/last_modified 等字段，再从 notes 中取出
     * {@code meta_gid}。如果 notes 不是合法 JSON 或缺少该字段，只把关联 GID 置空，
     * 不抛出异常，以免单条损坏的元数据中断整轮同步。</p>
     */
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        super.setContentByRemoteJSON(js);
        if (getNotes() != null) {
            try {
                JSONObject metaInfo = new JSONObject(getNotes().trim());
                mRelatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID);
            } catch (JSONException e) {
                Log.w(TAG, "failed to get related gid");
                mRelatedGid = null;
            }
        }
    }

    /**
     * 元数据不从本地便签 JSON 生成。
     * 若被调用说明同步流程把元数据误当成了普通 Task，属于程序错误。
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        // this function should not be called
        throw new IllegalAccessError("MetaData:setContentByLocalJSON should not be called");
    }

    /**
     * 元数据不参与“本地内容转 JSON”的普通流程。
     * 它的 notes 由 {@link #setMeta(String, JSONObject)} 直接构造。
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        throw new IllegalAccessError("MetaData:getLocalJSONFromContent should not be called");
    }

    /**
     * 元数据没有独立的本地数据库记录，不能参与常规的冲突判定。
     * 其生命周期由同步管理器随关联任务一起维护。
     */
    @Override
    public int getSyncAction(Cursor c) {
        throw new IllegalAccessError("MetaData:getSyncAction should not be called");
    }

}
