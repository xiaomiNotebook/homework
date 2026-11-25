# 成员 2 功能—包—类—方法映射

本文只映射本次标注范围内**经源码确认存在**的类与方法。未确认的类（如维护阶段新增的分享功能）不列入。

## 1. 标注范围内的类与职责

| 类 | 包 | 直接父类/接口 | 职责摘要 |
|---|---|---|---|
| `NotesListActivity` | `net.micode.notes.ui` | `Activity`，`OnClickListener`，`OnItemLongClickListener` | 主界面：查询、增删改、批量操作、导出、同步与设置入口 |
| `NotesListAdapter` | `net.micode.notes.ui` | `CursorAdapter` | 列表数据绑定与多选状态 |
| `NoteItemData` | `net.micode.notes.ui` | — | 单行数据快照与位置判定 |
| `NotesListItem` | `net.micode.notes.ui` | `LinearLayout` | 单行视图渲染与分组背景 |
| `FoldersListAdapter` | `net.micode.notes.ui` | `CursorAdapter` | 移动目标文件夹列表 |
| `NotesListActivity.ModeCallback` | 内部类 | `MultiChoiceModeListener`，`OnMenuItemClickListener` | 多选 ActionMode 生命周期与菜单 |
| `NotesListActivity.NewNoteOnTouchListener` | 内部类 | `OnTouchListener` | 新建按钮透明区域事件转发 |
| `NotesListActivity.BackgroundQueryHandler` | 内部类 | `AsyncQueryHandler` | 异步查询结果分派 |
| `NotesListActivity.OnListItemClickListener` | 内部类 | `OnItemClickListener` | 点击进入文件夹/编辑页或切换选中态 |
| `FoldersListAdapter.FolderListItem` | 私有内部类 | `LinearLayout` | 文件夹选择行视图 |

## 2. 功能到方法的映射

### 2.1 列表展示与数据加载

| 功能 | 包 | 类 | 方法/成员 | 说明 |
|---|---|---|---|---|
| 界面初始化 | `ui` | `NotesListActivity` | `onCreate`、`initResources` | 绑定布局、适配器、监听器、状态初值 |
| 首次引导便签 | `ui` | `NotesListActivity` | `setAppInfoFromRawRes` | 读取 raw 文本并创建一条便签，标记防重复 |
| 异步列表查询 | `ui` | `NotesListActivity` | `startAsyncNotesListQuery` | 根目录/子目录两套 selection，TYPE+时间排序 |
| 查询回调分派 | `ui` | `NotesListActivity.BackgroundQueryHandler` | `onQueryComplete` | token 区分列表与文件夹查询 |
| 游标换入与计数重算 | `ui` | `NotesListAdapter` | `changeCursor`、`onContentChanged`、`calcNotesCount` | 只统计 TYPE_NOTE |
| 行视图创建/绑定 | `ui` | `NotesListAdapter` | `newView`、`bindView` | 委托 `NotesListItem.bind` |
| 数据快照构造 | `ui` | `NoteItemData` | 构造器、`checkPostion` | 读投影列、剔除勾选标记、判定分组位置 |
| 行内容渲染 | `ui` | `NotesListItem` | `bind`、`setBackground`、`getItemData` | 三类分支 + 分组背景 |

### 2.2 便签与文件夹操作

| 功能 | 包 | 类 | 方法 | 说明 |
|---|---|---|---|---|
| 新建便签 | `ui` | `NotesListActivity` | `createNewNote`、`onClick` | `ACTION_INSERT_OR_EDIT` 携带当前文件夹 ID |
| 打开便签 | `ui` | `NotesListActivity` | `openNode` | `ACTION_VIEW` + `Intent.EXTRA_UID` |
| 打开文件夹 | `ui` | `NotesListActivity` | `openFolder` | 切换 mCurrentFolderId 并重查 |
| 返回上一级 | `ui` | `NotesListActivity` | `onBackPressed` | 按 mState 回根目录，再退出 |
| 创建/重命名文件夹 | `ui` | `NotesListActivity` | `showCreateOrModifyFolderDialog`、`showSoftInput`、`hideSoftInput` | 重名校验、空名禁用、LOCAL_MODIFIED 标记 |
| 删除文件夹 | `ui` | `NotesListActivity` | `deleteFolder` | 拒绝根目录；按同步模式真删或移入回收站 |
| 文件夹上下文菜单 | `ui` | `NotesListActivity` | `mFolderOnCreateContextMenuListener`、`onContextMenuClosed`、`onContextItemSelected` | 查看/删除/重命名 |
| 目标文件夹查询 | `ui` | `NotesListActivity` | `startQueryDestinationFolders`、`showFolderListMenu` | 排除回收站和当前目录 |
| 文件夹列表行 | `ui` | `FoldersListAdapter` | `newView`、`bindView`、`getFolderName`、`FolderListItem.bind` | 根目录名称替换 |

### 2.3 多选、批量操作与刷新

| 功能 | 包 | 类 | 方法 | 说明 |
|---|---|---|---|---|
| 进入多选模式 | `ui` | `NotesListActivity.ModeCallback` | `onCreateActionMode` | 按目录决定“移动”是否可见 |
| 选中态维护 | `ui` | `NotesListAdapter` | `setCheckedItem`、`isSelectedItem`、`isInChoiceMode`、`setChoiceMode` | 位置到布尔值的内存映射 |
| 全选/取消全选 | `ui` | `NotesListAdapter` | `selectAll`、`isAllSelected`、`getSelectedCount` | 只统计便签行 |
| 菜单文案更新 | `ui` | `NotesListActivity.ModeCallback` | `updateMenu` | 选中数量与全选文案 |
| 批量删除 | `ui` | `NotesListActivity` | `batchDelete` | 区分同步模式，结束后刷新小部件 |
| 批量移动 | `ui` | `NotesListActivity` | `ModeCallback.onMenuItemClick`、`showFolderListMenu` | 目标文件夹由对话框返回 |
| 选中项 ID 集合 | `ui` | `NotesListAdapter` | `getSelectedItemIds`、`getSelectedWidget` | 后者不关闭 Cursor |
| 小部件刷新 | `ui` | `NotesListActivity` | `updateWidget` | 2x/4x 分别广播，未知类型记日志 |
| 退出多选模式 | `ui` | `NotesListActivity.ModeCallback` | `onDestroyActionMode`、`finishActionMode` | 恢复长按与新建按钮 |
| 多选入口 | `ui` | `NotesListActivity` | `onItemLongClick`、`OnListItemClickListener.onItemClick` | 震动反馈、选择态点击 |

### 2.4 菜单、导出与设置

| 功能 | 包 | 类 | 方法 | 说明 |
|---|---|---|---|---|
| 状态相关菜单 | `ui` | `NotesListActivity` | `onPrepareOptionsMenu` | 三种状态加载不同 menu 资源 |
| 菜单分发 | `ui` | `NotesListActivity` | `onOptionsItemSelected` | 新建文件夹/导出/同步/设置/新建便签/搜索 |
| 导出文本 | `ui` | `NotesListActivity` | `exportNoteToText` | AsyncTask + 三类结果弹窗 |
| 同步状态判断 | `ui` | `NotesListActivity` | `isSyncMode` | 同步账号名为空即未开启 |
| 同步启停 | `ui` | `NotesListActivity` | `onOptionsItemSelected`（R.id.menu_sync） | `GTaskSyncService.startSync` / `cancelSync` |
| 跳转设置 | `ui` | `NotesListActivity` | `startPreferenceActivity` | 兼容父 Activity 场景 |
| 搜索 | `ui` | `NotesListActivity` | `onSearchRequested` | 调用系统搜索 |
| 触摸转发 | `ui` | `NotesListActivity.NewNoteOnTouchListener` | `onTouch` | 透明区域事件转给 ListView |

### 2.5 被标注代码调用的外部能力（非本次标注范围）

| 外部类 | 所属包 | 被调用方法/成员 | 用途 |
|---|---|---|---|
| `Notes` / `NoteColumns` | `data` | `CONTENT_NOTE_URI`、列常量、`TYPE_*`、`ID_*` | Provider 契约 |
| `DataUtils` | `tool` | `batchDeleteNotes`、`batchMoveToFolder`、`getCallNumberByNoteId`、`getFormattedSnippet`、`checkVisibleFolderName`、`getUserFolderCount`、`getFolderNoteWidget` | 批量数据操作与格式化 |
| `WorkingNote` | `model` | `createEmptyNote`、`setWorkingText`、`saveNote` | 创建引导便签 |
| `NoteEditActivity` | `ui` | `TAG_CHECKED`、`TAG_UNCHECKED` | 摘要剔除勾选标记 |
| `Contact` | `data` | `getContact` | 电话号码转联系人 |
| `GTaskSyncService` | `gtask.remote` | `isSyncing`、`startSync`、`cancelSync` | 同步状态与启停 |
| `BackupUtils` | `tool` | `getInstance`、`exportToText`、`STATE_*`、导出文件名/目录 | 文本导出 |
| `NotesPreferenceActivity` | `ui` | `getSyncAccountName` | 判断同步账号 |
| `NoteWidgetProvider_2x/4x` | `widget` | 广播更新 | 删除后刷新桌面小部件 |
| `ResourceParser.NoteItemBgResources` | `tool` | `getNoteBg*Res`、`getFolderBgRes` | 行背景资源 |

## 3. 关键流程的状态与边界

- `ListEditState` 三态决定：菜单资源（`note_list` / `sub_folder` / `call_record_folder`）、返回行为、新建按钮可见性。
- 异步查询 token 两值：`FOLDER_NOTE_LIST_QUERY_TOKEN` 换入列表游标；`FOLDER_LIST_QUERY_TOKEN` 弹文件夹选择对话框。
- 选择状态只存在于内存；`setChoiceMode(true/false)` 两端都清空，退出 ActionMode 不会残留选择。
- 删除策略由 `isSyncMode()` 决定：未同步直接删除；已同步移入 `Notes.ID_TRASH_FOLER`。
- 行背景由 `NoteItemData` 的位置标记决定，判定依赖游标相邻行，因此快照构造必须在游标有效位置时完成。

## 4. 数据来源声明

本文所有类名、方法名、常量名均来自 `src/maintained/src/net/micode/notes/ui/` 下 5 个文件的实际源码；未使用计划文档中的候选功能描述代替源码证据。
