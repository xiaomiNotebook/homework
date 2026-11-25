# 01 标注范围与阅读笔记

> 项目：开源软件「小米便签 / MiCode Notes」泛读与代码标注
> 标注对象根路径：`src/maintained/src/net/micode/notes/`
> 标注原则：**只添加注释，不修改任何程序代码与行为**

---

## 一、标注范围（文件清单）

本次标注覆盖 **数据层（data）**、**模型层（model）** 与 **工具层（tool）** 共 7 个 Java 文件。

| # | 文件 | 所在包 | 原始行数 | 标注后行数 | 新增注释行 | 删除行 |
|---|------|--------|---------:|----------:|----------:|------:|
| 1 | Contact.java | `net.micode.notes.data` | 73 | 113 | 40 | 0 |
| 2 | Notes.java | `net.micode.notes.data` | 279 | 322 | 43 | 0 |
| 3 | NotesDatabaseHelper.java | `net.micode.notes.data` | 362 | 452 | 90 | 0 |
| 4 | NotesProvider.java | `net.micode.notes.data` | 305 | 372 | 67 | 0 |
| 5 | Note.java | `net.micode.notes.model` | 253 | 307 | 58 | 4 |
| 6 | DataUtils.java | `net.micode.notes.tool` | 295 | 356 | 61 | 0 |
| 7 | ResourceParser.java | `net.micode.notes.tool` | 181 | 238 | 57 | 0 |
| — | **合计** | — | **1748** | **2160** | **416** | **4** |

> 行数经 `git diff --numstat` 与逐文件 `Get-Content` 计数双向核对。
> Note.java 的 4 处“删除”实为在 4 个字段行**行尾追加注释**后重新写回（代码文本未变），故其代码本体同样零改动；详见 [03-annotation-log.md](./03-annotation-log.md)。

---

## 二、模块职责与分层架构

被标注的 7 个文件构成便签应用的**数据支撑层**，自上而下分层清晰、依赖方向单向（上层依赖下层，下层不反向依赖）：

```
        ┌──────────────────────────────────────────────────────────────┐
  工具层 │  DataUtils（数据操作工具）   ResourceParser（资源映射）        │
        │  Contact（联系人反查）                                        │
        └───────────────▲──────────────────────────────────────────────┘
                        │ 复用
        ┌───────────────┴──────────────┐
  模型层 │  Note（内存缓冲 + 批量落库） │
        └───────────────▲──────────────┘
                        │ 通过 ContentResolver 访问
        ┌───────────────┴──────────────────────────────────────────────┐
  访问层 │  NotesProvider（ContentProvider：Uri 路由 / CRUD / 变更通知）  │
        └───────────────▲──────────────────────────────────────────────┘
                        │ 读写
        ┌───────────────┴──────────────────────────────────────────────┐
  存储层 │  NotesDatabaseHelper（建表 + 触发器 + 版本升级）              │
        └───────────────▲──────────────────────────────────────────────┘
                        │ 引用
        ┌───────────────┴──────────────────────────────────────────────┐
  契约层 │  Notes（AUTHORITY / Uri / 列名 / 常量 的唯一“事实来源”）      │
        └──────────────────────────────────────────────────────────────┘
```

| 层 | 文件 | 核心职责 | 关键设计要点 |
|----|------|----------|--------------|
| 契约层 | [Notes.java](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Notes.java) | 集中定义 AUTHORITY、Content Uri、类型常量、系统文件夹 ID 与两张表的列名 | 全模块唯一 schema 来源；列名以 interface 分组便于静态引用 |
| 存储层 | [NotesDatabaseHelper.java](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java) | 建 `note`/`data` 表、建索引、建触发器；管理数据库版本升级 | **把“计数、摘要”等派生数据下沉到 SQL 触发器维护** |
| 访问层 | [NotesProvider.java](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java) | 把 `content://micode_notes/...` 映射到表/行并完成 CRUD；变更后 `notifyChange`；维护 `version` 自增 | UriMatcher 区分集合/单行/搜索；集合与单行写法统一 |
| 模型层 | [Note.java](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java) | 内存聚合一次编辑的改动（note 字段 + data 数据体），`syncNote` 统一落库 | 用 `ContentValues` 做 diff 缓冲；`LOCAL_MODIFIED` 标记待同步 |
| 工具层 | [DataUtils.java](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java) | 批量删除/移动、存在性判断、重名校验、小部件收集、摘要读写 | 无状态静态方法；屏蔽 `ContentProviderOperation` 细节 |
| 工具层 | [ResourceParser.java](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java) | 把“颜色/字号编号”翻译成 drawable / style 资源 ID | 编号即数组下标；资源不落库、只存逻辑编号 |
| 工具层 | [Contact.java](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Contact.java) | 由号码反查联系人显示名，带进程内缓存 | 号码→姓名静态缓存；`min_match` 占位符替换实现模糊匹配 |

---

## 三、关键流程

### 3.1 新建并保存便签
1. UI 调用 `Note.getNewNoteId(context, folderId)` 在 `note` 表插入一条空便签行，返回自增 `noteId`（插入即置 `LOCAL_MODIFIED=1`）。
2. 本次插入会触发触发器 `increase_folder_count_on_insert`，自动把父文件夹 `NOTES_COUNT + 1`。
3. 编辑期间通过 `setNoteValue` / `setTextData` / `setCallData` 把改动缓存在内存 `ContentValues`。
4. `Note.syncNote(context, noteId)`：先 `update` note 行（字段改动），再交由内部类 `NoteData.pushIntoContentResolver` 写 data 表。
5. data 表的插入/更新会触发 `update_note_content_on_insert/update`，自动同步便签 `SNIPPET`（摘要）。

### 3.2 移动 / 删除便签（计数与级联）
- 移动：`DataUtils.batchMoveToFolder` / `moveNoteToFoler` 更新 `PARENT_ID` → 触发 `increase/decrease_folder_count_on_update` 调整两端文件夹计数。
- 删除：`DataUtils.batchDeleteNotes` → 触发 `decrease_folder_count_on_delete`、`delete_data_on_delete`、`folder_delete_notes_on_delete`。
- 移入回收站（`PARENT_ID = -3`）：触发 `folder_move_notes_on_trash` 把子级一并移入（**仅一级**）。

### 3.3 搜索
`NotesProvider.query` 命中 `URI_SEARCH` / `URI_SEARCH_SUGGEST` 时走专用 SQL `NOTES_SNIPPET_SEARCH_QUERY`：在 `SNIPPET` 上 `LIKE '%关键词%'`，并排除回收站中的便签、仅匹配普通便签；结果列按 `SearchManager.SUGGEST_COLUMN_*` 约定命名以对接系统搜索框。

### 3.4 颜色 / 字号资源解析
数据库/偏好中只保存整型编号；`ResourceParser` 的各静态数组以“编号 = 下标”把编号翻译为具体 drawable / style。`getDefaultBgId` 依据 `PREFERENCE_SET_BG_COLOR_KEY` 决定“随机色”或“默认黄色”。

### 3.5 联系人反查
`Contact.getContact` 先查进程内缓存，未命中再查系统通讯录（用 `PHONE_NUMBERS_EQUAL` + `phone_lookup.min_match` 占位符替换做号码模糊匹配），命中后回填缓存。

---

## 四、阅读中的疑问（待进一步确认）

1. **数据库升级逻辑自相矛盾**：`NotesDatabaseHelper.onUpgrade` 从 v1 升级时，`upgradeToV2` 会用“当前最新”建表 SQL 重建整表，随后把 `oldVersion` 置 2 并跳过 v3 分支，却未继续推进到 3，最终 `oldVersion(2) != newVersion(4)` 直接抛 `IllegalStateException`。这究竟是历史演进的遗留缺陷，还是对某个已不存在版本的临时补丁？
2. **`NotesProvider.getType` 恒返回 `null`**：是刻意省略（应用内不用 `getType`），还是应当按 Uri 返回 MIME 类型的遗漏？
3. **`Note.syncNote` 的返回值语义**：当数据体“只新增、无更新”时 `pushIntoContentResolver` 返回 `null`，导致 `syncNote` 误判失败；上层是否真的忽略了该返回值？这是否会掩盖真实写入失败？
4. **触发器级联深度**：`folder_delete_notes_on_delete` / `folder_move_notes_on_trash` 只处理直接子级。是产品上只允许“两层结构”的约束，还是多级文件夹场景下的数据一致性隐患？
5. **搜索串未做通配转义**：`searchString` 用 `String.format("%%%s%%", ...)` 拼接，用户输入的 `%`/`_` 会被当作 LIKE 通配符。是否需要转义？
6. **命名拼写错误的兼容性**：`ID_TEMPARAY_FOLDER`、`ID_TRASH_FOLER`、`moveNoteToFoler` 等拼写错误是否已被外部约定/持久化数据依赖，从而不可更正？
7. **`Contact` 缓存与游标生命周期**：静态缓存无淘汰策略且非线程安全，空结果分支还存在游标未关闭；在实际使用频度下是否会成为内存/句柄风险？

---

## 五、标注方式与约定

- **严格只增注释**：所有改动通过“在既有代码行前后插入注释行”实现；仅在极少数基础上以**行尾注释**追加（不改变代码文本）。全程未修改、未重排、未删除任何程序代码。
- **注释分层**：文件/类级（职责、设计意图、生命周期、线程、缺陷提示）→ 方法级（前置条件、边界、返回语义、异常路径）→ 行内（关键算法、触发器语义、易错点）。
- **缺陷只指出不修复**：以“缺陷提示：……”统一措辞记录，便于后续评审，不在标注过程中改动行为。
- **不逐句翻译**：注释聚焦“为什么/边界/风险”，而非逐行转述代码字面含义。
- **一致性**：术语与 `Notes` 中的 schema 命名保持一致（如 note 表、data 表、SNIPPET、PARENT_ID、系统文件夹等）。

> 各文件的注释位置、目的与真实统计见 [03-annotation-log.md](./03-annotation-log.md)；功能—包—类—方法映射见 [02-function-class-map.md](./02-function-class-map.md)。
