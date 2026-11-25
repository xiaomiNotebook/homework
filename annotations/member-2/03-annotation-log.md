# 成员 2 逐文件标注日志与真实统计

## 1. 统计口径（先明确口径，再看数字）

| 项 | 说明 |
|---|---|
| 统计对象 | `src/maintained/src/net/micode/notes/ui/` 下成员 2 负责的 5 个 Java 文件 |
| 基线 | `develop` 分支 2026-11-24 工作区快照；上游原始提交 `81a66024fe48eaa2206b854430856fe3875ff3ba` |
| 对照文件 | `src/original/src/net/micode/notes/ui/` 下同名文件，标注前两者逐字节一致 |
| 统计方法 | Python `difflib.SequenceMatcher(U0 等价)` 逐行比对，统计 insert 操作中的行 |
| “注释行”定义 | 去掉行首空白后以 `//`、`/*`、`*` 开头的整行（含 `/**` 与 `*/`） |
| 是否含行尾注释 | 否。本次新增全部为整行注释块 |
| 是否含原有注释 | “新增注释行”不含；“标注后注释行总数”含原文件自带注释 |
| “有效代码行”定义 | 去掉空行、整行注释、块注释标记后的剩余行数；用于交叉验证代码未被改动 |
| 统计时间 | 2026-11-24 09:54（Asia/Shanghai） |
| 说明 | 计划文档中的“约 1382 行”为估算值，与本表口径不同，报告中以本表为准 |

## 2. 逐文件统计

| # | 文件 | 原始行数 | 新增注释行 | 标注后行数 | 标注后注释行总数 | 有效代码行（前后相同） |
|---|---|---:|---:|---:|---:|---:|
| 1 | `ui/FoldersListAdapter.java` | 80 | 29 | 109 | 45 | 50 |
| 2 | `ui/NoteItemData.java` | 224 | 31 | 255 | 46 | 174 |
| 3 | `ui/NotesListActivity.java` | 954 | 126 | 1080 | 171 | 811 |
| 4 | `ui/NotesListAdapter.java` | 184 | 42 | 226 | 60 | 143 |
| 5 | `ui/NotesListItem.java` | 122 | 23 | 145 | 38 | 95 |
| 合计 | 5 个文件 | 1564 | 251 | 1815 | 360 | 1273 |

派生指标（同一口径）：

- 新增注释行占标注后总行数：251 / 1815 = **13.8%**
- 标注后注释行总数占比：360 / 1815 = **19.8%**
- 原文件自带注释行：360 − 251 = **109 行**（主要为许可证头、HACKME/TODO 与原有说明）
- 5 个文件有效代码行合计 **1273 行**，与计划文档估算 1382 行相差 109 行；差异来自统计口径，不修改代码。

## 3. 代码一致性验证（证明“只加注释、未改行为”）

对 5 个文件逐一执行以下检查，结果如下：

| 检查项 | 方法 | 结果 |
|---|---|---|
| 删除/改写行检查 | `difflib` 对比 `src/original` 与 `src/maintained`，统计 `delete` 与 `replace` 操作 | **0 处** |
| 新增行类型检查 | 所有 insert 行去掉空白后必须匹配 `//`、`/*`、`*` 或为空 | **251/251 均为注释或空行** |
| 剥离注释逐行比对 | 去掉注释行、块注释与空行后逐字符比较 | **5/5 一致**（有效代码行 50/174/811/143/95 全部相同） |
| 改动范围检查 | 只列出 5 个目标 Java 文件与 `doc/annotations/member-2/` 的 4 份材料 | 无其他文件改动 |
| `src/original` 完整性 | 与标注前快照比较 | 未改动 |

结论：本轮 251 行改动全部是注释新增，代码语句、导入顺序、格式、原有注释与许可证声明均未改动。

## 4. 逐文件注释位置与目的

### 4.1 `ui/FoldersListAdapter.java`（+29 行）

| 注释位置 | 目的 |
|---|---|
| 类声明前 | 说明对话框适配器的职责、轻量投影的设计意图、Cursor 所有权边界与根目录名称替换的必要性 |
| `PROJECTION` | 强调投影顺序与下标常量的耦合关系 |
| `ID_COLUMN` / `NAME_COLUMN` | 说明下标到列的对应关系 |
| 构造器 | 说明 TODO 空桩无业务逻辑，保留以免改行为 |
| `newView` / `bindView` | 说明视图创建与绑定的分工、instanceof 守卫的防御意义 |
| `getFolderName` | 说明 position 前置条件，以及与 bindView 共用文案规则的原因 |
| `FolderListItem` / `bind` | 说明缓存 TextView 的复用设计与只更新文本的边界 |

### 4.2 `ui/NoteItemData.java`（+31 行）

| 注释位置 | 目的 |
|---|---|
| 类声明前 | 说明快照职责、与 Cursor 解耦的意图、构造前置条件、勾选标记剔除与游标移动的异常路径 |
| `PROJECTION` / 下标常量 | 说明顺序耦合 |
| 字段分组前 | 区分“数据快照字段”和“位置标记字段”两组语义 |
| 构造器 | 说明一次性读取与联系人降级逻辑；勾选标记处就地注释“只改内存副本” |
| `checkPostion` | 解释向前看一行的非显然算法、multi/one-following 的判定条件，以及游标必须复位的异常路径 |
| `moveToPrevious` 处 | 标注游标临时移动与恢复的意图 |
| getter 分组、`hasAlert`、`isCallRecord`、`getNoteType` | 说明快照 getter 无副作用、提醒判定阈值、通话记录判定条件与静态类型读取的前置条件 |

### 4.3 `ui/NotesListActivity.java`（+126 行）

| 注释位置 | 目的 |
|---|---|
| 类声明前 | 概括主界面职责、三态设计意图、生命周期要点与异常路径边界 |
| 查询 token / 状态枚举 / `mState` / `mCurrentFolderId` | 说明分派依据与状态语义 |
| `NORMAL_SELECTION` / `ROOT_FOLDER_SELECTION` | 解释两套筛选条件，尤其根目录为何要 OR 通话记录文件夹 |
| 请求码 | 说明区分编辑页返回的两类请求 |
| `onCreate` / `onActivityResult` / `setAppInfoFromRawRes` / `onStart` | 说明引导、游标丢弃、流关闭、`commit()` 同步写入等关键点 |
| `initResources` | 说明 footer 与透明区域触摸转发的设计背景 |
| `ModeCallback` 及 `onCreateActionMode`/`updateMenu`/`onDestroyActionMode`/`finishActionMode` | 说明多选模式的进入条件、菜单同步、退出恢复 |
| 空选择判断处 | 标注“未选任何项只提示”的边界 |
| `NewNoteOnTouchListener` 及 DOWN/MOVE/默认分支 | 解释 `y=-0.12x+94` 透明区公式与坐标换算，明确该公式与背景图强耦合的脆弱性 |
| `startAsyncNotesListQuery` / `BackgroundQueryHandler` / `onQueryComplete` | 说明排序规则、token 分派与游标所有权 |
| `showFolderListMenu` / `createNewNote` / `batchDelete` / `deleteFolder` / `openNode` / `openFolder` | 说明批量移动、同步与非同步删除策略、widget 刷新、打开路径 |
| `showCreateOrModifyFolderDialog` 与对话框 show 处 | 解释手动接管确定按钮校验、重名与空名边界、LOCAL_MODIFIED 含义 |
| `onBackPressed` / `updateWidget` / 上下文菜单三个方法 | 说明状态回退、widget 类型分派、菜单焦点依赖与监听清理 |
| `onPrepareOptionsMenu` / `onOptionsItemSelected` / `exportNoteToText` / `isSyncMode` / `startPreferenceActivity` | 说明状态相关菜单、同步按钮依赖标题文字的判断、导出结果三类弹窗、父 Activity 兼容 |
| `OnListItemClickListener` 与 header 换算处 | 说明多选与普通模式的分发、adapter 位置换算 |
| `startQueryDestinationFolders` / `onItemLongClick` | 说明目标文件夹过滤规则与长按两种入口 |

### 4.4 `ui/NotesListAdapter.java`（+42 行）

| 注释位置 | 目的 |
|---|---|
| 类声明前 | 说明多选状态的内存边界、Cursor 所有权、计数重算时机与无效游标处理 |
| `mSelectedIndex` / `mNotesCount` / `AppWidgetAttribute` | 解释选择表键含义、计数用途与 widget 属性用途 |
| 构造器 / `newView` / `bindView` | 说明游标延后注入与绑定时的快照构造 |
| `setCheckedItem` / `setChoiceMode` / `selectAll` | 说明刷新机制、模式切换清空历史、`TYPE_NOTE` 过滤与多次 notify 的性能边界 |
| `getSelectedItemIds` / `getSelectedWidget` | 说明根目录 ID 防御、返回 null 的异常语义、不得关闭游标 |
| `getSelectedCount` / `isAllSelected` / `isSelectedItem` | 说明空列表不算全选、未记录即未选中 |
| `onContentChanged` / `changeCursor` / `calcNotesCount` | 说明重算时机与提前返回时的计数安全 |

### 4.5 `ui/NotesListItem.java`（+23 行）

| 注释位置 | 目的 |
|---|---|
| 类声明前 | 说明三类展示分支、分组背景设计、视图复用生命周期与 CheckBox 边界 |
| 构造器 | 说明一次 inflate + 缓存子视图 |
| `bind` 与 `mItemData` 赋值处、相对时间处 | 说明前置条件、快照缓存供点击取用、相对时间的显示意图 |
| `setBackground` | 解释 single/last/first/normal/folder 的选择顺序与视觉分组算法 |
| `getItemData` | 说明点击事件依赖该快照判断行类型与 ID |

## 5. 10 分工作包自评（复核分待成员 3 确认）

| 组成 | 满分 | 自评 | 依据 |
|---|---:|---:|---|
| 有效代码规模 | 4 | 4 | 5 个文件、剥离注释后 1273 行有效代码，其中 `NotesListActivity` 811 行 |
| 逻辑与调用复杂度 | 3 | 3 | 三态状态机、异步查询 token、硬编码触摸转发、多选状态机、游标相对位置算法、同步/非同步删除双路径 |
| 架构说明与功能映射 | 2 | 2 | `01-scope-and-reading.md` 模块职责与流程图，`02-function-class-map.md` 完整映射表 |
| 同伴复核 | 1 | 待定 | 按闭环由成员 3 复核；完成后在 `04-peer-review.md` 回填，未复核前不计分 |
| 合计 | 10 | 9 + 待复核 1 | 不提前给自己计复核分 |
