# 成员 4 - 功能—包—类—方法映射表

本模块对应“Google Tasks 同步”功能，涉及 `gtask.data` 和 `tool` 两个包。`gtask.data` 负责同步实体与本地数据库适配，`tool` 只提供协议常量；真正的网络请求和同步调度在 `gtask.remote.GTaskManager` 中完成。

## 1. 包职责

| 包 | 职责 | 本成员涉及的代表类 |
|---|---|---|
| `net.micode.notes.gtask.data` | 定义同步节点、完成远端 JSON 与本地 `note/data` 记录的转换，判断同步动作 | `Node`、`Task`、`TaskList`、`MetaData`、`SqlNote`、`SqlData` |
| `net.micode.notes.tool` | 集中定义 Google Tasks 协议字段、动作名、文件夹前缀和 meta JSON key | `GTaskStringUtils` |

## 2. 功能—类—方法映射

| 功能名称 | 涉及类 | 涉及核心方法 | 职责说明 |
|---|---|---|---|
| **同步节点公共契约** | `Node` | `getCreateAction()`<br>`getUpdateAction()`<br>`getSyncAction()`<br>`setContentByRemoteJSON()` | 定义远端节点的公共属性（GID、名称、修改时间、删除标记）和 9 个 `SYNC_ACTION_*` 常量；规定子类必须实现的 JSON 转换与同步判定接口。 |
| **远端任务同步** | `Task` | `setContentByRemoteJSON()`<br>`setContentByLocalJSON()`<br>`getLocalJSONFromContent()`<br>`getSyncAction()` | 把一条便签映射为 Google Task；任务名保存标题/摘要，`notes` 保存完整便签元数据；根据本地修改标记、`sync_id` 和远端 `last_modified` 判断同步方向与冲突。 |
| **远端文件分组同步** | `TaskList` | `getCreateAction()`<br>`setContentByLocalJSON()`<br>`getLocalJSONFromContent()`<br>`addChildTask()`<br>`removeChildTask()`<br>`moveChildTask()` | 把本地文件夹映射为 Google 任务分组；维护子任务的 `priorSibling` 顺序；处理 `[MIUI_Notes]` 前缀和系统文件夹固定名称；文件夹冲突采用本地优先。 |
| **元数据管理** | `MetaData` | `setMeta()`<br>`getRelatedGid()`<br>`isWorthSaving()`<br>`setContentByRemoteJSON()` | 维护“便签普通任务”和“隐藏元数据任务”之间的 GID 关联；保存完整便签 JSON；明确禁止把它当作普通本地便签转换。 |
| **便签主体操作** | `SqlNote` | `setContent(JSONObject)`<br>`getContent()`<br>`commit(boolean)`<br>`setGtaskId()`<br>`setSyncId()` | 封装本地 `note` 表；按 `TYPE_SYSTEM`、`TYPE_FOLDER`、`TYPE_NOTE` 分支更新；用差异列和 `version` 实现乐观并发控制；提交后重新加载，保持内存快照与数据库一致。 |
| **便签内容操作** | `SqlData` | `setContent(JSONObject)`<br>`getContent()`<br>`commit(long, boolean, long)` | 封装本地 `data` 表；记录变化列；新建时 insert、已有记录 update；在 `validateVersion` 为 true 时要求父便签版本精确匹配，防止并发覆盖。 |
| **协议常量定义** | `GTaskStringUtils` | 常量字段（无业务方法） | 定义动作名、实体字段、任务/分组类型、MIUI 文件夹前缀、meta JSON 结构等，保证各层使用同一套字符串。 |

## 3. 关键调用链

### 3.1 本地新建并上传

`SqlNote(Context)` → `SqlNote.setContent()` / `commit(false)` → `Task.setContentByLocalJSON()` → `Task.getCreateAction()` → `GTaskClient` 提交 → `SqlNote.setGtaskId()` 记录 GID → `MetaData.setMeta()` 保存完整元数据。

### 3.2 远端更新写入本地

`GTaskClient` 返回 JSON → `Task.setContentByRemoteJSON()` / `MetaData.setContentByRemoteJSON()` → `Task.getSyncAction(cursor)` → `GTaskManager.updateLocalNode()` → `SqlNote.setContent()` → `SqlNote.commit(true)` → `SqlData.commit()`。

### 3.3 文件夹同步

本地 `SqlNote`（`TYPE_FOLDER`/`TYPE_SYSTEM`）→ `TaskList.setContentByLocalJSON()` 加前缀/映射固定名称 → `TaskList.getCreateAction()` 或 `getUpdateAction()` 上传；反向由 `TaskList.getLocalJSONFromContent()` 去掉前缀并还原类型。

## 4. 同步动作判定

`Task.getSyncAction()` 和 `TaskList.getSyncAction()` 都读取 `local_modified`、`sync_id`、`gtask_id` 三个字段，但冲突策略不同：

| 本地状态 | 远端状态 | `Task` 的结果 | `TaskList` 的结果 |
|---|---|---|---|
| `local_modified == 0` | `sync_id == last_modified` | `SYNC_ACTION_NONE` | `SYNC_ACTION_NONE` |
| `local_modified == 0` | `sync_id != last_modified` | `SYNC_ACTION_UPDATE_LOCAL` | `SYNC_ACTION_UPDATE_LOCAL` |
| `local_modified != 0` | `gtask_id` 与远端 GID 不一致 | `SYNC_ACTION_ERROR` | `SYNC_ACTION_ERROR` |
| `local_modified != 0` | `sync_id == last_modified` | `SYNC_ACTION_UPDATE_REMOTE` | `SYNC_ACTION_UPDATE_REMOTE` |
| `local_modified != 0` | `sync_id != last_modified` | `SYNC_ACTION_UPDATE_CONFLICT` | `SYNC_ACTION_UPDATE_REMOTE`（本地优先） |

`Task` 还有两个特殊分支：元数据缺失时返回 `UPDATE_REMOTE`；元数据缺少便签 ID 或与本地游标 ID 不一致时返回 `UPDATE_LOCAL`。

## 5. 补充说明

`SqlNote` 和 `SqlData` 是本地数据提供者，`Task`、`TaskList`、`MetaData` 是远端同步节点，两者只通过 `JSONObject` 交换数据。这样本地表结构变化不会直接泄漏到远端协议层，远端 JSON 字段变化也只需在节点类中适配。
