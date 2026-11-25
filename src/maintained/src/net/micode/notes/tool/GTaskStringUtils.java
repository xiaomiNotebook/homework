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

/**
 * Google Tasks 同步协议和 MIUI 便签元数据使用的字符串常量。
 *
 * <p>这些常量同时出现在请求 JSON、响应 JSON 和本地 meta JSON 中，集中定义可以
 * 避免各层手写字符串导致拼写不一致。修改任何 key 都会影响与远端的兼容性，
 * 不能随意重命名。</p>
 *
 * <p>本类只保存常量，不包含逻辑，也不应被实例化。</p>
 */
public class GTaskStringUtils {

    // ------------------------------------------------------------------
    // 动作协议：action_list 中每个动作的编号、类型，以及批量操作名称
    // ------------------------------------------------------------------

    public final static String GTASK_JSON_ACTION_ID = "action_id";

    public final static String GTASK_JSON_ACTION_LIST = "action_list";

    public final static String GTASK_JSON_ACTION_TYPE = "action_type";

    public final static String GTASK_JSON_ACTION_TYPE_CREATE = "create";

    public final static String GTASK_JSON_ACTION_TYPE_GETALL = "get_all";

    public final static String GTASK_JSON_ACTION_TYPE_MOVE = "move";

    public final static String GTASK_JSON_ACTION_TYPE_UPDATE = "update";

    // ------------------------------------------------------------------
    // 任务/分组的实体字段
    // ------------------------------------------------------------------

    public final static String GTASK_JSON_CREATOR_ID = "creator_id";

    public final static String GTASK_JSON_CHILD_ENTITY = "child_entity";

    public final static String GTASK_JSON_CLIENT_VERSION = "client_version";

    public final static String GTASK_JSON_COMPLETED = "completed";

    public final static String GTASK_JSON_CURRENT_LIST_ID = "current_list_id";

    public final static String GTASK_JSON_DEFAULT_LIST_ID = "default_list_id";

    public final static String GTASK_JSON_DELETED = "deleted";

    public final static String GTASK_JSON_DEST_LIST = "dest_list";

    public final static String GTASK_JSON_DEST_PARENT = "dest_parent";

    public final static String GTASK_JSON_DEST_PARENT_TYPE = "dest_parent_type";

    public final static String GTASK_JSON_ENTITY_DELTA = "entity_delta";

    public final static String GTASK_JSON_ENTITY_TYPE = "entity_type";

    public final static String GTASK_JSON_GET_DELETED = "get_deleted";

    public final static String GTASK_JSON_ID = "id";

    public final static String GTASK_JSON_INDEX = "index";

    public final static String GTASK_JSON_LAST_MODIFIED = "last_modified";

    public final static String GTASK_JSON_LATEST_SYNC_POINT = "latest_sync_point";

    public final static String GTASK_JSON_LIST_ID = "list_id";

    public final static String GTASK_JSON_LISTS = "lists";

    public final static String GTASK_JSON_NAME = "name";

    public final static String GTASK_JSON_NEW_ID = "new_id";

    public final static String GTASK_JSON_NOTES = "notes";

    public final static String GTASK_JSON_PARENT_ID = "parent_id";

    public final static String GTASK_JSON_PRIOR_SIBLING_ID = "prior_sibling_id";

    public final static String GTASK_JSON_RESULTS = "results";

    public final static String GTASK_JSON_SOURCE_LIST = "source_list";

    public final static String GTASK_JSON_TASKS = "tasks";

    public final static String GTASK_JSON_TYPE = "type";

    // 远端实体类型只有任务和分组两种；分组对应本地文件夹。
    public final static String GTASK_JSON_TYPE_GROUP = "GROUP";

    public final static String GTASK_JSON_TYPE_TASK = "TASK";

    public final static String GTASK_JSON_USER = "user";

    // ------------------------------------------------------------------
    // MIUI 便签与远端任务之间的命名约定
    // ------------------------------------------------------------------

    /** 普通文件夹同步到远端时统一加此前缀，避免与用户任务重名。 */
    public final static String MIUI_FOLDER_PREFFIX = "[MIUI_Notes]";

    /** 根文件夹在远端使用的固定名称（去掉前缀后的部分）。 */
    public final static String FOLDER_DEFAULT = "Default";

    /** 通话记录文件夹在远端使用的固定名称。 */
    public final static String FOLDER_CALL_NOTE = "Call_Note";

    /** 元数据任务的固定名称。 */
    public final static String FOLDER_META = "METADATA";

    // ------------------------------------------------------------------
    // 本地 meta JSON 的结构 key
    // ------------------------------------------------------------------

    /** 元数据中保存的、关联普通任务的 GID。 */
    public final static String META_HEAD_GTASK_ID = "meta_gid";

    /** 本地便签主记录的 JSON 对象 key。 */
    public final static String META_HEAD_NOTE = "meta_note";

    /** 本地便签内容数据数组的 JSON 对象 key。 */
    public final static String META_HEAD_DATA = "meta_data";

    /** 元数据任务的显示名称；明确提示用户和同步逻辑不要修改或删除。 */
    public final static String META_NOTE_NAME = "[META INFO] DON'T UPDATE AND DELETE";

}
