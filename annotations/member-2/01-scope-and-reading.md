# 成员 2 代码阅读与标注范围说明

## 1. 基本信息

| 项 | 内容 |
|---|---|
| 负责人 | 成员 2 |
| 工作分支 | `member-2` |
| 标注基线 | `develop` 分支 2026-11-24 工作区快照（提交号需在已克隆仓库内用 `git rev-parse HEAD` 回填） |
| 上游原始提交 | `81a66024fe48eaa2206b854430856fe3875ff3ba`（MiCode Notes） |
| 代码工作目录 | `src/maintained/` |
| 只读对照目录 | `src/original/` |
| 阅读与标注日期 | 2026-11-24 |
| 统计口径 | 见 `03-annotation-log.md` 第 1 节 |

## 2. 文件清单

路径均相对于 `src/maintained/src/net/micode/notes/`。行数为本次从文件实际统计的结果。

| # | 文件 | 原始行数 | 标注后行数 | 剥离注释后的有效代码行 | 所属层 |
|---|---|---:|---:|---:|---|
| 1 | `ui/FoldersListAdapter.java` | 80 | 109 | 50 | 界面（文件夹选择对话框适配器） |
| 2 | `ui/NoteItemData.java` | 224 | 255 | 174 | 界面（列表项数据快照） |
| 3 | `ui/NotesListActivity.java` | 954 | 1080 | 811 | 界面（主 Activity） |
| 4 | `ui/NotesListAdapter.java` | 184 | 226 | 143 | 界面（列表适配器与多选状态） |
| 5 | `ui/NotesListItem.java` | 122 | 145 | 95 | 界面（列表行视图） |
| 合计 | 5 个文件 | 1564 | 1815 | 1273 | — |

说明：计划文档中的“约 1382 行”是分工时的估算值，与本表按最终文件重新统计的有效代码行数（1273）口径接近但不相同；报告中一律以本表为准，不照抄预估值。

## 3. 模块职责

### 3.1 `NotesListActivity`——主界面与流程编排

- 应用入口界面，用 `ListEditState` 枚举区分三种视图：根目录便签列表（NOTE_LIST）、子文件夹（SUB_FOLDER）、通话记录文件夹（CALL_RECORD_FOLDER）。
- 负责首次启动引导：从 `res/raw/introduction` 读取文本，创建一条介绍便签，并用 SharedPreferences 标记防止重复插入。
- 负责异步查询（`AsyncQueryHandler`）、新建/打开便签（跳转 `NoteEditActivity`）、文件夹查看/新建/重命名/删除、多选批量删除与移动、导出文本、同步开关和设置入口。
- 删除或移动后按受影响便签的 widgetId/widgetType 广播刷新桌面小部件。
- 同步模式下删除不是真删除，而是把便签/文件夹移入回收站，保留云端回滚可能。

### 3.2 `NotesListAdapter`——列表适配器与多选状态机

- 继承 `CursorAdapter`，用 `NoteItemData.PROJECTION` 消费 Provider 查询结果。
- 用 `HashMap<Integer, Boolean> mSelectedIndex` 保存“adapter 位置 → 是否选中”；退出选择模式即清空，不持久化。
- `mNotesCount` 在 `changeCursor` 与 `onContentChanged` 时重算，只统计 TYPE_NOTE 行，用于“全选/取消全选”判断。
- 对外提供选中 ID 集合与选中项 widget 属性集合，供批量操作使用；Cursor 所有权归 Activity，适配器不关闭。

### 3.3 `NoteItemData`——行数据快照

- 构造时一次性读取投影列到字段，随后与 Cursor 解耦，避免 ListView 滚动复用视图时依赖游标位置。
- 摘要中剔除任务勾选标记 `TAG_CHECKED` / `TAG_UNCHECKED`，只影响展示，不写回数据库。
- 通话记录文件夹下的便签通过 `DataUtils.getCallNumberByNoteId` 反查号码，再用 `Contact.getContact` 解析联系人；解析失败降级为号码或空串。
- `checkPostion`（原方法名拼写如此）向前看一行，判断当前便签是文件夹后单条还是多条，并临时移动游标；不能恢复原位时抛出 `IllegalStateException`。

### 3.4 `NotesListItem`——行视图渲染

- 继承 `LinearLayout`，构造时 inflate `note_item` 并缓存标题、时间、联系人、提醒图标、CheckBox。
- 三类展示分支：通话记录文件夹、通话记录便签、普通便签/文件夹；样式、图标与文本来源各不相同。
- 背景资源由行在分组中的位置决定（single / first / last / normal / folder），把同文件夹下的便签在视觉上拼成一组。
- 时间显示为相对时间（`DateUtils.getRelativeTimeSpanString`）。

### 3.5 `FoldersListAdapter`——移动目标选择

- “移动到文件夹”对话框的数据适配器，投影只需要 ID 与 SNIPPET 两列。
- 根目录项用本地化文案替代数据库名称，且 `getFolderName` 与 `bindView` 共用同一规则，保证列表项与提示语一致。
- 行视图 `FolderListItem` 为私有内部类，构造时缓存 TextView。

## 4. 关键流程与调用关系

```text
[首次启动] onCreate
    └─ setAppInfoFromRawRes → 读取 raw/introduction → WorkingNote.createEmptyNote → saveNote
         └─ 成功则 SharedPreferences 写入 introduction=true

[列表刷新] onStart / onActivityResult(编辑页成功返回)
    └─ startAsyncNotesListQuery → AsyncQueryHandler.startQuery
         └─ 根目录：ROOT_FOLDER_SELECTION；子目录：NORMAL_SELECTION
         └─ 排序：TYPE DESC（文件夹在前）+ MODIFIED_DATE DESC
              └─ onQueryComplete(FOLDER_NOTE_LIST_QUERY_TOKEN) → adapter.changeCursor

[点击列表项] OnListItemClickListener
    ├─ 多选模式 → 切换选中态（position 扣掉 header 数量）
    └─ 普通模式 → NOTE_LIST：文件夹/系统项 openFolder，便签 openNode
                → SUB_FOLDER / CALL_RECORD_FOLDER：只允许 openNode

[长按列表项] onItemLongClick
    ├─ 便签 → startActionMode(ModeCallback) → 震动反馈 → 批量删除/移动
    └─ 文件夹 → 设置上下文菜单监听 → 查看/删除/重命名

[批量删除] ModeCallback 删除 → 二次确认 → batchDelete
    ├─ 先取 getSelectedWidget（不得关闭 Cursor）
    ├─ 未开启同步 → DataUtils.batchDeleteNotes
    └─ 已开启同步 → DataUtils.batchMoveToFolder(..., ID_TRASH_FOLER)
         └─ onPostExecute → updateWidget 逐个刷新 → finishActionMode

[移动到文件夹] ModeCallback 移动 → startQueryDestinationFolders
    └─ FOLDER_LIST_QUERY_TOKEN → showFolderListMenu（FoldersListAdapter）
         └─ DataUtils.batchMoveToFolder → 提示 → finishActionMode
```

## 5. 阅读中的疑问

1. 仓库根目录只有 `README.md`、`doc/`、`src/`，没有 `build.xml`、`project.properties` 或 Gradle 脚本，无法在仓库内直接构建；构建验证方式需要组长确认。
2. 标注 PR 是否必须先关联 Issue？当前环境没有可用的 GitHub 凭证，无法创建 Issue 与 PR，本次只完成本地文件与材料，链接待回填。
3. 注释量统计口径（是否含原有注释、是否含空注释行）全组尚未统一；本材料采用与 `member-6` 相同的口径，见 `03-annotation-log.md`。
4. `onActivityResult` 中 `changeCursor(null)` 与实际重查之间的生命周期衔接，需要结合真机运行日志确认；标注阶段不改动该逻辑。
5. 计划文档中 UML 主责在 1 节/3.4 节写成员3、在 6.3 节写成员1，需组长确认；本报告的类关系以成员2实际代码为准。

## 6. 发现但未修复的疑似缺陷（只记录，不在本次改动中修复）

按“标注阶段不改变程序行为、缺陷只建 Issue”的约定，以下问题仅记录。因当前环境无 GitHub 权限，**尚未创建 Issue，也没有编号**；权限可用后需补建并回填链接。

| # | 位置 | 现象与影响 |
|---|---|---|
| D1 | `NotesListActivity.NewNoteOnTouchListener.onTouch` | 透明区域用硬编码直线 `y=-0.12x+94` 判定，与按钮背景强耦合；背景图变化即失效，且 `display.getHeight()` 已废弃 |
| D2 | `NotesListAdapter.selectAll` | 循环中对每个位置调用 `setCheckedItem`，每次都 `notifyDataSetChanged()`，列表较大时可能卡顿（与后续 M3 的“刷新频率”关注点同类） |
| D3 | `NoteItemData.checkPostion` | 游标 `moveToPrevious` 成功后若 `moveToNext` 失败会抛 `IllegalStateException`；异常发生在绑定路径，可能导致列表中断 |
| D4 | `NotesListActivity.setAppInfoFromRawRes` | 在主线程用 `commit()` 同步写 SharedPreferences，首次启动可能有短暂时延 |
| D5 | `NotesListActivity.updateWidget` | 只对 2x/4x 两种类型刷新，其他类型只记错误日志；若后续新增小部件尺寸需同步扩展 |

## 7. 本次标注的边界声明

- 只新增注释，未删除或改写任何代码行；剥离注释后与 `src/original` 同名文件逐行一致。
- 未修改 `src/original/` 下任何文件。
- 未修改其他成员负责的文件。
- 未删除任何原有英文注释，包括上游版权声明与 HACKME/TODO 注释。
