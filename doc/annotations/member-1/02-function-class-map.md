# 02 功能—包—类—方法映射

> 项目：开源软件「小米便签 / MiCode Notes」泛读与代码标注
> 标注对象根路径：`src/maintained/src/net/micode/notes/`
> 说明：本表把**功能**逐级落到**包 → 类 → 方法/成员**，用于快速定位某能力由谁提供、在哪里实现。
> 行号均为**标注后**文件中的行号（含新增注释）。

---

## 一、功能总览

| # | 功能域 | 包 | 主类 | 一句话职责 |
|---|--------|----|------|------------|
| F1 | 数据契约与常量 | `net.micode.notes.data` | [Notes](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Notes.java) | AUTHORITY / Uri / 列名 / 系统文件夹 ID 的唯一来源 |
| F2 | 数据库结构、触发器与版本升级 | `net.micode.notes.data` | [NotesDatabaseHelper](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java) | 建表、建索引、建触发器、系统文件夹、upgrade |
| F3 | 数据访问与变更通知 | `net.micode.notes.data` | [NotesProvider](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java) | Uri 路由 + CRUD + 搜索 + notifyChange + version 自增 |
| F4 | 便签领域模型与落库 | `net.micode.notes.model` | [Note](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java) | 内存缓冲一次编辑改动，`syncNote` 统一提交 |
| F5 | 数据操作工具 | `net.micode.notes.tool` | [DataUtils](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java) | 批量增删移、存在性/重名校验、小部件、摘要 |
| F6 | 颜色/字号资源解析 | `net.micode.notes.tool` | [ResourceParser](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java) | 逻辑编号 → drawable / style 资源 ID |
| F7 | 联系人号码反查 | `net.micode.notes.data` | [Contact](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Contact.java) | 号码 → 联系人姓名（带进程内缓存） |

---

## 二、功能—类—方法映射明细

### F1 数据契约与常量 —— `data/Notes.java`

| 功能点 | 类/成员 | 方法/字段 | 说明与标注要点 |
|--------|---------|-----------|----------------|
| 权威标识与 Uri | `Notes` | `AUTHORITY`、`CONTENT_NOTE_URI`、`CONTENT_DATA_URI` | 全模块 Uri 基址；[L80](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Notes.java#L80-L85) |
| 行类型 | `Notes` | `TYPE_NOTE=0` / `TYPE_FOLDER=1` / `TYPE_SYSTEM=2` | note 行归一化存储便签/文件夹/系统文件夹 |
| 系统文件夹 ID | `Notes` | `ID_ROOT_FOLDER=0`、`ID_TEMPARAY_FOLDER=-1`、`ID_CALL_RECORD_FOLDER=-2`、`ID_TRASH_FOLER=-3` | 负数=系统保留；**缺陷提示**：`TEMPARAY`/`FOLER` 拼写错误，已扩散多处 |
| Intent 传参 | `Notes` | `INTENT_EXTRA_*` | Activity/小部件间传参 key，统一加包名前缀 |
| 小部件类型 | `Notes` | `TYPE_WIDGET_INVALIDE=-1` / `_2X=0` / `_4X=1` | -1 表示未设置 |
| 数据体 MIME | `Notes.DataConstants` | `NOTE`、`CALL_NOTE` | 与触发器/查询条件呼应 |
| note 表列名 | `Notes.NoteColumns` | `ID`、`PARENT_ID`、`SNIPPET`、`NOTES_COUNT`、`TYPE`、`VERSION`… | 与建表 SQL、触发器严格一致 |
| data 表列名 | `Notes.DataColumns` | `MIME_TYPE`、`NOTE_ID`、`CONTENT`、`DATA1~DATA5` | DATA1~5 按 MIME 语义复用 |
| 文本便签子类型 | `Notes.TextNote` | `MODE=DATA1`、`MODE_CHECK_LIST`、`CONTENT_ITEM_TYPE` | 清单模式标记 |
| 通话便签子类型 | `Notes.CallNote` | `CALL_DATE=DATA1`、`PHONE_NUMBER=DATA3` | 关联通话记录 |

### F2 数据库结构、触发器与版本升级 —— `data/NotesDatabaseHelper.java`

| 功能点 | 方法/字段 | 行号 | 说明与标注要点 |
|--------|-----------|------|----------------|
| 库名与版本 | `DB_NAME`、`DB_VERSION=4` | [L52-L56](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L52-L56) | 提版本需同步补 upgrade 分支 |
| 表名常量 | `TABLE.NOTE` / `TABLE.DATA` | [L58-L63](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L58-L63) | |
| 建表 SQL | `CREATE_NOTE_TABLE_SQL`、`CREATE_DATA_TABLE_SQL` | [L71-L105](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L71-L105) | 时间戳默认值 `strftime('%s','now')*1000` |
| 索引 | `CREATE_DATA_NOTE_ID_INDEX_SQL` | [L107-L110](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L107-L110) | 加速 data.note_id 关联 |
| 触发器（计数） | `NOTE_INCREASE/DECREASE_FOLDER_COUNT_ON_UPDATE/INSERT/DELETE_TRIGGER` | L125-L170 | 维护文件夹 `NOTES_COUNT` |
| 触发器（摘要） | `DATA_UPDATE_NOTE_CONTENT_ON_INSERT/UPDATE/DELETE_TRIGGER` | L175-L209 | 维护便签 `SNIPPET` |
| 触发器（级联） | `NOTE_DELETE_DATA_ON_DELETE_TRIGGER` | L214-L220 | 删便签连带删 data |
| 触发器（级联） | `FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER` | L227-L233 | **缺陷提示**：仅直接子级、无递归 |
| 触发器（回收站） | `FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER` | L241-L249 | **缺陷提示**：条件无法区分文件夹/便签，且无递归 |
| 触发器总说明 | 注释块 | [L112-L120](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L112-L120) | 说明"派生数据下沉数据库"的设计动机 |
| 建 note 表 | `createNoteTable()` | [L262](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L262-L267) | 建表 → 重建触发器 → 建系统文件夹 |
| 重建 note 触发器 | `reCreateNoteTableTriggers()` | [L273](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L273-L289) | 先 DROP 再建，保证与最新 SQL 一致 |
| 系统文件夹 | `createSystemFolder()` | [L295](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L295-L328) | 写 4 个系统文件夹行 |
| 建 data 表 | `createDataTable()` | [L333](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L333-L338) | |
| 重建 data 触发器 | `reCreateDataTableTriggers()` | [L343](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L343-L351) | |
| 单例 | `getInstance(Context)` | [L357](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L357-L362) | **缺陷提示**：`mInstance` 非 volatile |
| 生命周期 | `onCreate(db)` | [L368](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L368-L371) | 首建库调用一次 |
| 版本升级 | `onUpgrade(db,old,new)` | [L384](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L384-L414) | **缺陷提示**：v1 升级路径版本推进不完整，最终抛 `IllegalStateException` |
| v1→v2 | `upgradeToV2()` | [L420](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L420-L425) | 整表重建、丢数据、结构为"最新版" |
| v2→v3 | `upgradeToV3()` | [L430](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L430-L443) | 加 `gtask_id` 列、补建回收站 |
| v3→v4 | `upgradeToV4()` | [L448](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L448-L451) | 加 `version` 列 |

### F3 数据访问与变更通知 —— `data/NotesProvider.java`

| 功能点 | 方法/字段 | 行号 | 说明与标注要点 |
|--------|-----------|------|----------------|
| Uri 匹配器 | `mMatcher`、`URI_NOTE(_ITEM)`、`URI_DATA(_ITEM)`、`URI_SEARCH(_SUGGEST)` | [L58-L84](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L58-L84) | 静态块注册模板，`#` 匹配单行 |
| 搜索投影 | `NOTES_SEARCH_PROJECTION` | [L92-L98](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L92-L98) | 别名须符合 `SUGGEST_COLUMN_*` 约定 |
| 搜索 SQL | `NOTES_SNIPPET_SEARCH_QUERY` | [L102-L106](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L102-L106) | **缺陷提示**：应为 final 常量 |
| 创建 | `onCreate()` | [L112](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L112-L115) | 初始化 helper 单例 |
| 查询 | `query(...)` | [L127](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L127-L188) | 按 Uri 形态分流；搜索分支拒绝外部 projection/sortOrder；**缺陷提示**：LIKE 未转义 |
| 插入 | `insert(uri,values)` | [L196](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L196-L227) | 通知 note/data 两个 Uri |
| 删除 | `delete(...)` | [L237](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L237-L280) | 保护 `id<=0` 系统文件夹；**缺陷提示**：selection=null 时拼出 `(null)` |
| 更新 | `update(...)` | [L287](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L287-L324) | note 更新前自增 version；data 更新额外通知 note 集合 |
| selection 拼接 | `parseSelection(selection)` | [L327](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L327-L329) | 空则返回空串 |
| 版本自增 | `increaseNoteVersion(id,sel,args)` | [L336](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L336-L359) | **缺陷提示**：args 字符串替换注入/转义风险 |
| MIME 类型 | `getType(uri)` | [L367](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L367-L370) | 恒返回 null（未实现） |

### F4 便签领域模型与落库 —— `model/Note.java`

| 功能点 | 方法/成员 | 行号 | 说明与标注要点 |
|--------|-----------|------|----------------|
| 字段缓冲 | `mNoteDiffValues`、`mNoteData` | [L50-L51](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L50-L51) | note 字段 diff 与数据体缓冲 |
| 新建便签 | `getNewNoteId(ctx,folderId)` | [L63](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L63-L85) | **缺陷提示**：`== -1` 为死校验 |
| 构造 | `Note()` | [L88](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L88-L91) | 初始化空缓冲 |
| 写 note 字段 | `setNoteValue(k,v)` | [L94](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L94-L98) | 附带 LOCAL_MODIFIED=1 与 MODIFIED_DATE |
| 数据体转发 | `setTextData/getTextDataId/setCallDataId/setCallData` | L101-L119 | 转发给内部 `NoteData` |
| 改动检测 | `isLocalModified()` | [L122](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L122-L124) | note 字段或数据体任一有改动 |
| 落库 | `syncNote(ctx,noteId)` | [L135](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L135-L163) | **缺陷提示**：仅新增无更新时误判失败 |
| 内部数据体 | `NoteData` | [L169](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L169-L306) | 维护文本/通话两条数据体与各自 id |
| 数据体落库 | `NoteData.pushIntoContentResolver(ctx,noteId)` | [L231](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L231-L305) | insert 直写 / update 走 applyBatch；**缺陷提示**：返回 null 语义不可靠 |

### F5 数据操作工具 —— `tool/DataUtils.java`

| 功能点 | 方法 | 行号 | 说明与标注要点 |
|--------|------|------|----------------|
| 批量删除 | `batchDeleteNotes(resolver,ids)` | [L59](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L59-L92) | 跳过根文件夹；空集视作成功 |
| 单条移动 | `moveNoteToFoler(resolver,id,src,des)` | [L99](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L99-L105) | **缺陷提示**：方法名拼写 `Foler` 应为 `Folder` |
| 批量移动 | `batchMoveToFolder(resolver,ids,folderId)` | [L111](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L111-L140) | 走 applyBatch |
| 用户文件夹数 | `getUserFolderCount(resolver)` | [L148](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L148-L168) | 排除系统文件夹与回收站 |
| 可见性判断 | `visibleInNoteDatabase(...)` | [L174](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L174-L189) | 类型匹配且不在回收站 |
| 存在性判断 | `existInNoteDatabase` / `existInDataDatabase` | L192 / L207 | 只判是否存在 |
| 重名校验 | `checkVisibleFolderName(resolver,name)` | [L225](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L225-L239) | 仅比可见文件夹名 |
| 小部件收集 | `getFolderNoteWidget(resolver,folderId)` | [L245](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L245-L270) | 返回去重集合，无记录返回 null |
| 反查号码 | `getCallNumberByNoteId(resolver,noteId)` | [L275](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L275-L292) | 查不到返回 "" |
| 反查便签 | `getNoteIdByPhoneNumberAndCallDate(...)` | [L298](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L298-L317) | 用 `PHONE_NUMBERS_EQUAL` 等价比较 |
| 读摘要 | `getSnippetById(resolver,noteId)` | [L324](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L324-L340) | **缺陷提示**：两种"查不到"处理不一致 |
| 摘要格式化 | `getFormattedSnippet(snippet)` | [L346](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L346-L355) | 去空白、只留首行 |

### F6 颜色/字号资源解析 —— `tool/ResourceParser.java`

| 功能点 | 类/方法 | 行号 | 说明与标注要点 |
|--------|---------|------|----------------|
| 编号常量 | `YELLOW..RED`、`TEXT_SMALL..SUPER`、`BG_DEFAULT_FONT_SIZE` | [L43-L60](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L43-L60) | 编号即数组下标 |
| 默认背景色 | `getDefaultBgId(context)` | [L99](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L99-L106) | 随机色或默认黄 |
| 编辑页背景 | `NoteBgResources.getNoteBgResource/getNoteTitleBgResource` | [L83](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L83-L90) | **缺陷提示**：无越界校验 |
| 列表项背景 | `NoteItemBgResources.getNoteBgFirst/Last/Single/NormalRes`、`getFolderBgRes` | [L147-L169](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L147-L169) | 组首/中/末/独立四形态 |
| 小部件背景 | `WidgetBgResources.getWidget2xBgResource/getWidget4xBgResource` | [L186](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L186-L201) | 2x/4x 两种尺寸 |
| 文字外观 | `TextAppearanceResources.getTexAppearanceResource` | [L221](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L221-L231) | **缺陷提示**：兜底返回字号编号而非 style 资源 ID |
| 字号数量 | `TextAppearanceResources.getResourcesSize()` | [L234](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L234-L236) | 字号档位数 |

### F7 联系人号码反查 —— `data/Contact.java`

| 功能点 | 方法/字段 | 行号 | 说明与标注要点 |
|--------|-----------|------|----------------|
| 进程内缓存 | `sContactCache` | [L47](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Contact.java#L47) | 静态、惰性初始化、永不清理 |
| 查询模板 | `CALLER_ID_SELECTION` | [L57](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Contact.java#L57-L62) | `min_match` 为计算列，用占位符替换而非绑定 |
| 号码反查 | `getContact(context,phoneNumber)` | [L74](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Contact.java#L74-L112) | **缺陷提示**：空结果分支游标泄漏；未命中不缓存 |

---

## 三、反向索引（类 → 功能域）

| 类 | 所在包 | 功能域 | 对外主要入口 |
|----|--------|--------|--------------|
| `Notes` | `data` | F1 | 静态常量/Uri/列名 |
| `NotesDatabaseHelper` | `data` | F2 | `getInstance`、`onCreate`、`onUpgrade` |
| `NotesProvider` | `data` | F3 | `query`/`insert`/`delete`/`update` |
| `Note` | `model` | F4 | `getNewNoteId`、`setXxx`、`syncNote` |
| `DataUtils` | `tool` | F5 | 各静态数据操作方法 |
| `ResourceParser` | `tool` | F6 | 各 `getXxxResource` |
| `Contact` | `data` | F7 | `getContact` |

> 说明：F1 为其余功能域共同依赖的契约层；F3 依赖 F2 与 F1；F4 依赖 F1（经 ContentResolver 间接触达 F3/F2）；F5、F6、F7 为被 UI/同步层复用的工具层。
> 各文件注释位置、目的与真实统计见 [03-annotation-log.md](./03-annotation-log.md)；范围与阅读笔记见 [01-scope-and-reading.md](./01-scope-and-reading.md)。
