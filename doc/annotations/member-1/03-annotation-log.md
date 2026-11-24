# 03 标注日志（逐文件注释位置·目的·真实统计）

> 项目：开源软件「小米便签 / MiCode Notes」泛读与代码标注
> 标注对象根路径：`src/maintained/src/net/micode/notes/`
> 标注原则：**只添加注释，不修改任何程序代码与行为**
> 行号为**标注后**文件中的行号（含新增注释）；“缺陷”一律**只指出不修复**。

---

## 一、注释分层约定

| 层级 | 载体 | 目的 |
|------|------|------|
| 类级 | 类声明上方的 Javadoc（`/** ... */`） | 职责、设计意图、生命周期、线程模型、整体缺陷 |
| 方法级 | 方法上方的 Javadoc 或 `//` | 前置条件、边界、返回语义、异常路径 |
| 行内 | 关键代码上方/右侧的 `//` | 不易读的算法、触发器语义、易错点 |

缺陷统一以「**缺陷提示：**」措辞记录，便于评审检索；不逐句翻译代码。

---

## 二、逐文件注释位置与目的

### 1. `data/Contact.java`（联系人号码反查）

| 行号 | 层级 | 目的与要点 | 缺陷 |
|------|------|------------|:----:|
| [L28-L44](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Contact.java#L28-L44) | 类级 | 职责（号码→姓名）、设计意图（进程内缓存）、生命周期/线程、3 条整体缺陷 | ✔ |
| [L46](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Contact.java#L46-L47) | 行内 | `sContactCache` 语义（null=未初始化） | |
| [L50-L56](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Contact.java#L50-L56) | 字段 | `CALLER_ID_SELECTION`：`min_match` 为计算列，需用 `toCallerIDMinMatch` 替换 `+` 占位符 | |
| [L64-L73](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Contact.java#L64-L73) | 方法级 | `getContact` 前置条件、返回语义、缓存回填算法 | |
| [L75](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Contact.java#L75-L88) | 行内 | 惰性初始化、缓存命中、占位符替换三步 | |
| [L107-L108](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Contact.java#L107-L108) | 行内 | 空结果分支**游标未关闭**（泄漏） | ✔ |

### 2. `data/Notes.java`（数据契约与常量）

| 行号 | 层级 | 目的与要点 | 缺陷 |
|------|------|------------|:----:|
| [L20-L32](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Notes.java#L20-L32) | 类级 | 契约中心定位、设计意图、与建表 SQL 的一致性约束 | |
| [L36-L39](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Notes.java#L36-L39) | 行内 | `TYPE_*` 行类型语义 | |
| [L41-L49](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Notes.java#L41-L49) | 行内 | 系统文件夹负 ID 约定 | ✔ |
| [L55](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Notes.java#L55-L61) | 行内 | `INTENT_EXTRA_*` 前缀约定 | |
| [L63-L66](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Notes.java#L63-L66) | 行内 | `TYPE_WIDGET_*` 取值 | |
| [L68-L71](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Notes.java#L68-L71) | 类级 | `DataConstants` 与触发器/查询呼应 | |
| [L87-L92](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Notes.java#L87-L92) | 接口 | `NoteColumns`：便签/文件夹复用同套列 | |
| [L199-L203](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Notes.java#L199-L203) | 接口 | `DataColumns`：DATA1~5 按 MIME 语义复用 | |
| [L278-L282](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Notes.java#L278-L282) | 类 | `TextNote`：MODE 复用 DATA1；`implements` 仅为继承常量 | |
| [L299-L302](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Notes.java#L299-L302) | 类 | `CallNote`：CALL_DATE=DATA1、PHONE_NUMBER=DATA3 | |

### 3. `data/NotesDatabaseHelper.java`（建表、触发器、升级）

| 行号 | 层级 | 目的与要点 | 缺陷 |
|------|------|------------|:----:|
| [L30-L50](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L30-L50) | 类级 | 职责、设计意图（派生数据下沉 SQL）、生命周期、3 条整体缺陷 | ✔ |
| [L52-L56](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L52-L56) | 行内 | 库名/版本，提版本需补 upgrade 分支 | |
| [L58-L63](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L58-L63) | 行内 | `TABLE.NOTE/DATA` 常量 | |
| [L67](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L67-L70) | 行内 | 单例字段与默认时间戳算法 | |
| [L107-L110](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L107-L110) | 行内 | note_id 索引用途 | |
| [L112-L120](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L112-L120) | 区块 | **触发器设计总说明**（为何用触发器、AFTER 语义） | |
| [L222-L226](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L222-L226) | 字段 | 文件夹删除级联：仅直接子级、无递归 | ✔ |
| [L235-L240](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L235-L240) | 字段 | 移入回收站级联：无法区分文件夹/便签、无递归 | ✔ |
| [L251-L253](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L251-L253) | 方法级 | 构造器与单例使用建议 | |
| [L258-L261](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L258-L261) | 方法级 | `createNoteTable` 顺序依赖 | |
| [L269-L272](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L269-L272) | 方法级 | `reCreateNoteTableTriggers` 用重建保证与最新 SQL 一致 | |
| [L291-L294](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L291-L294) | 方法级 | `createSystemFolder` 四个系统文件夹 | |
| [L330-L332](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L330-L332) | 方法级 | `createDataTable` | |
| [L340-L342](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L340-L342) | 方法级 | `reCreateDataTableTriggers` | |
| [L353-L356](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L353-L356) | 方法级 | `getInstance` 的 context 泄漏提醒 | ✔ |
| [L364-L366](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L364-L366) | 方法级 | `onCreate` 生命周期 | |
| [L373-L382](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L373-L382) | 方法级 | `onUpgrade`：v1 升级路径版本推进不完整→抛异常；v3→v4 不重建触发器 | ✔ |
| [L416-L419](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L416-L419) | 方法级 | `upgradeToV2` 整表重建、丢数据、结构为最新版 | |
| [L427-L429](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L427-L429) | 方法级 | `upgradeToV3` 加列 + 补建回收站 | |
| [L445-L447](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L445-L447) | 方法级 | `upgradeToV4` 加 version 列 | |

### 4. `data/NotesProvider.java`（ContentProvider）

| 行号 | 层级 | 目的与要点 | 缺陷 |
|------|------|------------|:----:|
| [L38-L55](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L38-L55) | 类级 | 职责、设计意图、生命周期、3 条整体缺陷 | ✔ |
| [L57-L72](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L57-L72) | 行内 | `mMatcher`/`mHelper` 与 URI 编码含义 | |
| [L74](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L74-L84) | 行内 | `"#"` 匹配单行形态的注册说明 | |
| [L86-L98](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L86-L98) | 字段 | 搜索建议投影须符合 `SUGGEST_COLUMN_*` 约定 | |
| [L100-L102](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L100-L102) | 字段 | 搜索 SQL 语义（LIKE、排除回收站） | ✔ |
| [L108-L110](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L108-L110) | 方法级 | `onCreate` 注入 helper | |
| [L117-L125](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L117-L125) | 方法级 | `query` 四类分流、搜索分支的参数拒绝、通知 Uri | |
| [L171-L174](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L171-L174) | 行内 | `String.format` 拼 LIKE，`%`/`_` 未转义 | ✔ |
| [L190-L194](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L190-L194) | 方法级 | `insert` 双 Uri 通知、触发器自动计数 | |
| [L229-L235](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L229-L235) | 方法级 | `delete` 保护系统文件夹；selection=null 拼出 `(null)` | ✔ |
| [L282-L285](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L282-L285) | 方法级 | `update` 先自增 version，data 更新额外通知 note | |
| [L326](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L326-L329) | 方法级 | `parseSelection` 空串返回约定 | |
| [L331-L335](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L331-L335) | 方法级 | `increaseNoteVersion` args 字符串替换注入/转义风险 | ✔ |
| [L361-L365](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L361-L365) | 方法级 | `getType` 恒返回 null | ✔ |

### 5. `model/Note.java`（领域模型与落库）

| 行号 | 层级 | 目的与要点 | 缺陷 |
|------|------|------------|:----:|
| [L37-L48](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L37-L48) | 类级 | 职责、设计意图（diff 缓冲 + 一次落库）、生命周期/线程 | |
| [L50-L51](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L50-L52) | 行内 | 两个改动缓冲（行尾注释） | |
| [L53-L62](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L53-L62) | 方法级 | `getNewNoteId` 语义 + `== -1` 死校验、uri 解析异常 | ✔ |
| [L87](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L87-L91) | 方法级 | 构造器初始化空缓冲 | |
| [L93](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L93-L98) | 方法级 | `setNoteValue` 附带 LOCAL_MODIFIED/MODIFIED_DATE | |
| [L100](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L100-L119) | 行内 | setXxx/getXxx 转发给 `NoteData` | |
| [L121](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L121-L124) | 方法级 | `isLocalModified` 判定 | |
| [L126-L134](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L126-L134) | 方法级 | `syncNote` 流程/契约；仅新增无更新时误判失败 | ✔ |
| [L144-L148](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L144-L154) | 行内 | 原注释保留 + “update 影响 0 行也继续”的说明 | |
| [L165-L168](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L165-L168) | 类级 | 内部类 `NoteData` 职责 | |
| [L170-L178](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L170-L184) | 行内 | 文本/通话数据体字段（行尾注释，0=待插入） | |
| [L187-L230](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L187-L230) | 方法级 | `isLocalModified`/`setXxxId`/`setXxxData` 的契约与联动标记 | |
| [L222-L229](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L222-L229) | 方法级 | `pushIntoContentResolver` 策略；仅 insert 时返回 null | ✔ |
| [L242-L289](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L242-L289) | 行内 | insert/update 分支、applyBatch 触发条件说明 | |

### 6. `tool/DataUtils.java`（数据操作工具）

| 行号 | 层级 | 目的与要点 | 缺陷 |
|------|------|------------|:----:|
| [L38-L49](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L38-L49) | 类级 | 职责、设计意图、无状态静态方法说明 | |
| [L52-L58](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L52-L58) | 方法级 | `batchDeleteNotes` 边界/保护/返回语义 | |
| [L94-L98](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L94-L98) | 方法级 | `moveNoteToFoler` 语义 + 方法名拼写错误 | ✔ |
| [L107-L110](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L107-L110) | 方法级 | `batchMoveToFolder` 边界/返回语义 | |
| [L142-L147](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L142-L147) | 方法级 | `getUserFolderCount` 统计口径 | |
| [L170-L173](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L170-L173) | 方法级 | `visibleInNoteDatabase` 可见性定义 | |
| [L191](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L191-L204) | 方法级 | `existInNoteDatabase` / `existInDataDatabase` | |
| [L221-L224](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L221-L224) | 方法级 | `checkVisibleFolderName` 重名校验口径 | |
| [L241-L244](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L241-L244) | 方法级 | `getFolderNoteWidget` 返回集语义 | |
| [L272-L274](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L272-L274) | 方法级 | `getCallNumberByNoteId` 查不到返回 "" | |
| [L294-L297](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L294-L297) | 方法级 | `getNoteIdByPhoneNumberAndCallDate` 号码等价比较 | |
| [L319-L323](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L319-L323) | 方法级 | `getSnippetById` 两种“查不到”处理不一致 | ✔ |
| [L342-L345](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L342-L345) | 方法级 | `getFormattedSnippet` 单行化规则 | |

### 7. `tool/ResourceParser.java`（资源解析）

| 行号 | 层级 | 目的与要点 | 缺陷 |
|------|------|------------|:----:|
| [L25-L40](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L25-L40) | 类级 | 设计意图（为何存编号而非资源 ID）、四组资源表、2 条整体缺陷 | ✔ |
| [L43-L60](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L43-L60) | 行内 | 颜色/字号编号常量（编号即下标） | |
| [L62-L64](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L62-L64) | 类级 | `NoteBgResources` 资源表说明 | |
| [L82-L90](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L82-L90) | 方法级 | 编辑页 getter（无越界校验） | ✔ |
| [L93-L98](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L93-L98) | 方法级 | `getDefaultBgId` 随机/默认逻辑与副作用提醒 | |
| [L108-L111](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L108-L111) | 类级 | `NoteItemBgResources` 四形态说明 | |
| [L146-L169](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L146-L169) | 方法级 | 列表项首/末/单/中 getter 语义 | |
| [L172-L174](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L172-L174) | 类级 | `WidgetBgResources` 2x/4x 说明 | |
| [L185-L201](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L185-L201) | 方法级 | 小部件背景 getter | |
| [L204-L206](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L204-L206) | 类级 | `TextAppearanceResources` 说明 | |
| [L215-L220](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L215-L220) | 方法级 | 兼容旧数据的兜底；返回字号编号而非 style 资源 ID；未校验负数 | ✔ |
| [L233-L236](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L233-L236) | 方法级 | `getResourcesSize` 字号档位数 | |

---

## 三、真实统计

### 3.1 权威数据（来源：`git diff --numstat`）

| # | 文件 | 新增行(+) | 删除行(−) | 说明 |
|---|------|----------:|----------:|------|
| 1 | data/Contact.java | 40 | 0 | 全部为新增注释 |
| 2 | data/Notes.java | 43 | 0 | 全部为新增注释 |
| 3 | data/NotesDatabaseHelper.java | 90 | 0 | 全部为新增注释 |
| 4 | data/NotesProvider.java | 67 | 0 | 全部为新增注释 |
| 5 | model/Note.java | 58 | 4 | 4 处“删除”= 字段行行尾追加注释后重写（代码文本未变） |
| 6 | tool/DataUtils.java | 61 | 0 | 全部为新增注释 |
| 7 | tool/ResourceParser.java | 57 | 0 | 全部为新增注释 |
| — | **合计** | **416** | **4** | 见 3.3 对 4 处删除的说明 |

### 3.2 行数变化

| 文件 | 原始行数 | 标注后行数 | 净增 |
|------|---------:|----------:|-----:|
| Contact.java | 73 | 113 | +40 |
| Notes.java | 279 | 322 | +43 |
| NotesDatabaseHelper.java | 362 | 452 | +90 |
| NotesProvider.java | 305 | 372 | +67 |
| Note.java | 253 | 307 | +54 |
| DataUtils.java | 295 | 356 | +61 |
| ResourceParser.java | 181 | 238 | +57 |
| **合计** | **1748** | **2160** | **+412** |

> 净增合计(+412) = 新增 416 − 删除 4；与 §3.1 的双向核对结果一致。
> 行数经 `git diff --numstat`（权威增减）与逐文件 `Get-Content`（总行数）双向核对。

### 3.3 对 Note.java 的 4 处“删除”的说明

`git diff --numstat` 报 Note.java **−4**，并非删除了代码。原因是：在 4 个字段声明行**行尾**追加注释时，这些行被整体重写，diff 记作“删旧行 + 加新行”。被重写的行是 [L50-L51](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L50-L52) 与 [L170-L178](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L170-L184) 处的字段行，其**代码文本逐字未变**，仅行尾多了 `// ...` 注释。

### 3.4 一致性验证

- **仅注释、无代码改动**：用 PowerShell 脚本剥离每个文件的 `//` 与 `/* */` 注释后，与 `HEAD` 版本逐行比对，7 个文件全部通过（代码字节级零改动）。
- **改动范围**：`git status --short` 仅列出这 7 个源文件被修改。
- **静态检查**：`GetDiagnostics` 在这 7 个文件上返回空（无语法/编译错误）。

---

## 四、缺陷清单汇总（只指出，不修复）

| # | 文件 | 位置 | 缺陷摘要 |
|---|------|------|----------|
| D1 | Contact.java | 类级 / [L107](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Contact.java#L107-L108) | 缓存无淘汰且非线程安全；空结果分支游标泄漏；未命中不缓存 |
| D2 | Notes.java | [L47](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/Notes.java#L47-L49) | 常量命名拼写错误（`TEMPARAY`/`FOLER`）已扩散 |
| D3 | NotesDatabaseHelper.java | [L373](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L373-L382) | v1 升级版本推进不完整→抛 `IllegalStateException` |
| D4 | NotesDatabaseHelper.java | [L227](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L227-L249) | 文件夹级联触发器仅直接子级、无递归；回收站触发器无法区分文件夹/便签 |
| D5 | NotesDatabaseHelper.java | [L357](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesDatabaseHelper.java#L357-L362) | `mInstance` 非 volatile（可见性依赖同步） |
| D6 | NotesProvider.java | [L100](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L100-L106) | 搜索 SQL 字段未声明 final |
| D7 | NotesProvider.java | [L174](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L174) | 搜索串含 `%`/`_` 未做 LIKE 转义 |
| D8 | NotesProvider.java | [L244](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L244) | `delete` 的 URI_NOTE 分支 selection=null 时拼出 `(null)` |
| D9 | NotesProvider.java | [L336](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L336-L359) | `increaseNoteVersion` 字符串替换 args，注入/转义风险 |
| D10 | NotesProvider.java | [L367](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/data/NotesProvider.java#L367-L370) | `getType` 恒返回 null（未实现） |
| D11 | Note.java | [L81](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L81-L84) | `getNewNoteId` 的 `== -1` 为死校验 |
| D12 | Note.java | [L135](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/model/Note.java#L135-L163) | `syncNote`/`pushIntoContentResolver`：仅新增无更新时误判失败 |
| D13 | DataUtils.java | [L99](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L99) | 方法名拼写错误 `moveNoteToFoler` |
| D14 | DataUtils.java | [L324](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/DataUtils.java#L324-L340) | `getSnippetById` 两种“查不到”处理不一致 |
| D15 | ResourceParser.java | [L83](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L83-L90) | 各 getter 无越界校验 |
| D16 | ResourceParser.java | [L221](file:///e:/code/Source/homework/src/maintained/src/net/micode/notes/tool/ResourceParser.java#L221-L231) | 兜底返回字号编号而非 style 资源 ID；未校验负数 |

> 以上缺陷在标注中一律**仅指出不修复**。功能映射见 [02-function-class-map.md](./02-function-class-map.md)；范围与阅读笔记见 [01-scope-and-reading.md](./01-scope-and-reading.md)。
