# 成员 4 - 代码阅读范围与模块职责

## 1. 负责的文件清单

根据组内分工，本成员负责阅读并标注 `src/maintained/src/net/micode/notes/` 下的 **7 个 Java 文件**。原始版本共 **1684 个物理行**，其中非空行 **1347 行**，与分工计划中的“约 1347 行”一致。

| 文件 | 物理行 | 非空行 | 结构概况 |
|---|---:|---:|---|
| `gtask/data/MetaData.java` | 82 | 67 | 元数据任务节点，7 个方法，无显式构造方法 |
| `gtask/data/Node.java` | 101 | 69 | 抽象基类，9 个同步状态常量、6 个抽象方法、8 个访问器、1 个构造方法 |
| `gtask/data/SqlData.java` | 189 | 154 | 本地 `data` 表适配，2 个构造方法、5 个方法 |
| `gtask/data/SqlNote.java` | 505 | 419 | 本地 `note` 表适配，3 个构造方法、14 个方法 |
| `gtask/data/Task.java` | 351 | 289 | 远端任务节点，1 个构造方法、16 个方法 |
| `gtask/data/TaskList.java` | 343 | 285 | 远端任务分组节点，1 个构造方法、18 个方法 |
| `tool/GTaskStringUtils.java` | 113 | 64 | 同步协议常量类，无业务方法 |
| **合计** | **1684** | **1347** | **7 个类** |

本次标注新增注释 **443 行**，删除 **0 行**，没有改动任何代码语句、方法签名或变量名。

## 2. 模块职责概述

`gtask.data` 是同步子系统中的数据模型与本地数据库适配层，位于本地 `data` 层和 `gtask.remote` 业务层之间：向上为 `GTaskManager` 提供可生成远端动作的 `Node` 对象，向下通过 `SqlNote`、`SqlData` 访问 ContentProvider。具体职责可概括为：

1. **定义同步实体**：`Node` 定义公共状态和同步动作；`Task` 表示一条便签对应的 Google Task；`TaskList` 表示文件夹对应的任务分组；`MetaData` 保存便签的隐藏元数据。
2. **适配本地数据库**：`SqlNote` 封装 `note` 表及其 `data` 子记录，`SqlData` 封装单条内容数据；两者都用差异列（Diff）方式提交，避免整行覆盖。
3. **完成 JSON 双向映射**：把本地 `SqlNote` 快照转换为远端 JSON（`getCreateAction()`、`getUpdateAction()`），也把远端 JSON 解析回本地对象（`setContentByRemoteJSON()`、`getLocalJSONFromContent()`）。
4. **判断同步方向与冲突**：通过 `local_modified`、`sync_id` 和远端 `last_modified` 判断 `SYNC_ACTION_*`；通过 `version` 做乐观并发控制，防止同步期间覆盖用户刚做的修改。

## 3. 关键流程梳理

### 3.1 本地新建便签并上传

`SqlNote(Context)` 创建内存对象 → `SqlNote.setContent()` / `commit()` 写入本地 `note` 与 `data` → `GTaskManager` 调用 `Task.setContentByLocalJSON()` 填充任务名 → `Task.getCreateAction()` 生成创建动作 → `GTaskClient` 提交远端 → 返回 GID 后由 `SqlNote.setGtaskId()` 记录，并由 `MetaData.setMeta()` 保存完整元数据。

### 3.2 拉取远端更新

远端 JSON → `Node.setContentByRemoteJSON()` 更新缓存 → `Task.getSyncAction(cursor)` 比较本地修改标记和时间戳 → `GTaskManager.updateLocalNode()` → `SqlNote.setContent()` 写入差异 → `SqlNote.commit(true)` 带版本校验落库。

### 3.3 文件夹同步

普通文件夹走 `TaskList`：本地文件夹名称加 `[MIUI_Notes]` 前缀后上传；远端分组去掉前缀后还原为 `TYPE_FOLDER`。根目录和通话记录文件夹使用 `Default`、`Call_Note` 固定名称，映射为 `TYPE_SYSTEM`。文件夹冲突时 `TaskList.getSyncAction()` 直接采用本地版本（`UPDATE_REMOTE`），不进入 `UPDATE_CONFLICT`。

### 3.4 元数据同步

每条便签对应一个隐藏的 `MetaData` 任务，其 `notes` 保存完整便签 JSON，`meta_gid` 指向普通任务。远端拉取时 `GTaskManager` 以 `getRelatedGid()` 为 key 建立 `mMetaHashMap`；本地更新时 `updateRemoteMeta()` 通过该 map 查找并更新元数据，找不到才新建。

## 4. 阅读疑问与核查结论

### 4.1 `MetaData` 解析失败后关联 GID 为空

原疑问：`MetaData.setContentByRemoteJSON()` 解析失败时只把 `mRelatedGid` 置空，不抛异常，是否会导致后续同步找不到关联任务？

核查结论：不会立即崩溃，但确实存在关联丢失风险。`GTaskManager` 在建立元数据 map 时只检查了 `metaData.getGid() != null`，随后直接执行 `mMetaHashMap.put(metaData.getRelatedGid(), metaData)`；当 `mRelatedGid` 为 null 时会写入 null key。后续用真实 GID 查找必然 miss，`updateRemoteMeta()` 可能为同一条便签再创建一个元数据任务，形成重复或孤儿元数据。更稳妥的做法是在 put 前同时判断 `getRelatedGid() != null`，并对损坏元数据记录 Issue。该问题只记录，不在标注 PR 中修改。

### 4.2 `SqlData` 与 `SqlNote` 的版本比较方式

原疑问中的表述需要更正：`SqlData.commit()` 实际使用的是 **`=`**（子表更新要求父便签版本恰好等于本次同步版本），而 `SqlNote.commit()` 使用的是 **`<=`**（父表更新允许数据库版本不高于本次预期版本）。

核查结论：这是两级乐观并发控制，不是简单的“兼容旧版本”。`SqlNote.commit()` 先把 `mVersion` 自增，再用 `VERSION <= mVersion` 更新父表；更新成功后数据库版本应为 `mVersion`。随后 `SqlData.commit()` 用 `=` 要求父表仍处于这个精确版本，确保子内容不会在父记录已被并发修改后继续写入。若同步期间用户修改了便签，父表版本会继续增大，父表和子表的更新都会影响 0 行，从而避免半覆盖。这一设计应在维护阶段补充更明确的注释和并发测试。
