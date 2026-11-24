# 成员 3：代码阅读范围与阅读记录

- 负责人：成员 3
- 分支：`member-3`
- 代码基线：官方提交 `81a6602`（`src/original` 为只读快照，实际标注在 `src/maintained`）
- 阅读方式：静态阅读源码；因项目构建环境尚未配通，以下结论均未在运行环境中验证（见第 5 节）

## 1. 阅读范围与文件清单

路径均相对于 `src/maintained/src/net/micode/notes/`，行数为标注前的原始行数。

| 序号 | 文件 | 包 | 原始行数 | 角色 |
|---:|---|---|---:|---|
| 1 | `model/WorkingNote.java` | `net.micode.notes.model` | 368 | 正在编辑的便签的内存模型 |
| 2 | `ui/NoteEditActivity.java` | `net.micode.notes.ui` | 873 | 便签编辑页（核心界面） |
| 3 | `ui/NoteEditText.java` | `net.micode.notes.ui` | 217 | 清单条目使用的自定义输入框 |
| 4 | `widget/NoteWidgetProvider.java` | `net.micode.notes.widget` | 132 | 桌面小部件公共抽象基类 |
| 5 | `widget/NoteWidgetProvider_2x.java` | `net.micode.notes.widget` | 47 | 2x 小部件 |
| 6 | `widget/NoteWidgetProvider_4x.java` | `net.micode.notes.widget` | 46 | 4x 小部件 |
| | **合计** | | **1683** | |

## 2. 各文件职责

### 2.1 `WorkingNote`（model）

“正在编辑的那一条便签”的内存模型，位于编辑页和数据库之间：

- 编辑页的所有修改（正文、背景色、提醒、清单模式、小部件绑定）先记在它里面，内部的 `Note` 对象把这些修改攒成一份变更集；
- 调用 `saveNote()` 时才一次性写入数据库；
- 只有两种创建方式：`createEmptyNote()` 新建、`load()` 打开已有便签，构造方法私有；
- 通过内部接口 `NoteSettingChangedListener` 把背景色、提醒、小部件、清单模式的变化回调给界面，模型本身不依赖具体界面。

### 2.2 `NoteEditActivity`（ui）

应用最核心的界面，职责有四类：

- 入口解析：根据 Intent 的 action 区分“打开已有便签”（`ACTION_VIEW`）和“新建 / 编辑”（`ACTION_INSERT_OR_EDIT`），来源包括列表、搜索结果、桌面小部件、桌面快捷方式、通话记录；
- 编辑：普通文本模式和清单模式切换，背景色、字号、搜索关键词高亮；
- 菜单操作：新建、删除、分享、发送到桌面、设置 / 取消提醒；
- 保存与状态恢复：`onPause`、返回键自动保存，被系统回收后按便签 id 恢复。

实现了三个接口：`OnClickListener`、`NoteSettingChangedListener`、`OnTextViewChangeListener`。

### 2.3 `NoteEditText`（ui）

继承 `EditText` 的自定义控件，清单模式中每个条目就是一个它：

- 触摸时手动换算坐标并设置光标；
- 拦截回车和退格，通过 `OnTextViewChangeListener` 通知 Activity 新增、合并或删除条目；
- 焦点变化时通知 Activity 显示或隐藏本行的复选框；
- 长按菜单中为文本里的电话、网址、邮箱链接增加快捷入口。

### 2.4 `NoteWidgetProvider` 及 2x / 4x（widget）

- `NoteWidgetProvider`：抽象基类，继承 `AppWidgetProvider`，实现查询绑定便签、构造 `RemoteViews`、设置点击跳转、小部件被删除时解除绑定；
- `NoteWidgetProvider_2x`、`NoteWidgetProvider_4x`：只提供各自的布局、背景图和小部件类型，其余复用父类（模板方法模式）。

## 3. 关键流程

以下流程均来自对源码的阅读，可作为 UML 顺序图的素材（提供给负责画图的成员）。

### 3.1 打开已有便签

```text
列表 / 搜索结果 / 快捷方式 → Intent(ACTION_VIEW, EXTRA_UID)
 → NoteEditActivity.onCreate
 → initActivityState
     ├─ （搜索来源）读取 EXTRA_DATA_KEY 和 USER_QUERY
     ├─ DataUtils.visibleInNoteDatabase 检查便签是否存在
     │     └─ 不存在：跳转 NotesListActivity 并提示，finish
     └─ WorkingNote.load → loadNote → loadNoteData
 → setOnSettingStatusChangedListener(this)
 → initResources（绑定控件、读取字号偏好）
 → onResume → initNoteScreen（渲染正文、背景、提醒信息）
```

### 3.2 新建便签

```text
Intent(ACTION_INSERT_OR_EDIT, 文件夹 / 小部件 / 背景色 / 通话信息)
 → initActivityState
     ├─ 有通话号码和时间：按号码和时间查找已有通话便签
     │     ├─ 找到：WorkingNote.load
     │     └─ 没找到：createEmptyNote + convertToCallNote
     └─ 其他：WorkingNote.createEmptyNote
 → 软键盘设为直接弹出
```

### 3.3 保存

```text
onPause / onBackPressed / createNewNote / onSaveInstanceState / 设置提醒前 / 发送到桌面前
 → NoteEditActivity.saveNote
 → getWorkingText（界面内容同步回 WorkingNote）
 → WorkingNote.saveNote
     ├─ isWorthSaving？（已删除 / 空白新便签 / 无修改 → 不保存）
     ├─ 新便签：Note.getNewNoteId 申请 id
     ├─ Note.syncNote 写库
     └─ 绑定了小部件：onWidgetChanged → updateWidget（发送广播刷新小部件）
 → 保存成功：setResult(RESULT_OK)
```

### 3.4 清单模式

```text
菜单“清单模式” → WorkingNote.setCheckListMode(新模式)
 → 先回调 onCheckListModeChanged(旧, 新)，之后才更新 mMode
     ├─ 切到清单：switchToListMode（正文按行拆成条目，getListItem 解析 √ / □ 前缀）
     └─ 切回普通：getWorkingText 汇总；若无已勾选项则去掉 “□ ” 标记；文本放回输入框

条目内按键（NoteEditText → OnTextViewChangeListener → Activity）
 ├─ 回车：onKeyUp 以光标切分文本 → onEditTextEnter 插入新条目
 ├─ 行首退格：onEditTextDelete 把本条并入上一条并移除
 └─ 焦点变化：onTextChange 控制复选框显示 / 隐藏
```

### 3.5 设置提醒

```text
菜单“设置提醒” → setReminder → DateTimePickerDialog
 → WorkingNote.setAlertDate(时间, true)
 → 回调 onClockAlertChanged
     ├─ 新便签先 saveNote 以获得 id（内容为空则提示并放弃）
     └─ AlarmManager 以 RTC_WAKEUP 登记（取消时 cancel）→ 到点广播给 AlarmReceiver
```

### 3.6 删除

```text
菜单“删除” → 确认对话框 → deleteCurrentNote
 ├─ 未开启同步（无同步账号）：DataUtils.batchDeleteNotes 直接删除
 └─ 开启同步：DataUtils.batchMoveToFolder 移到回收站文件夹
 → WorkingNote.markDeleted(true)，防止退出时被重新保存 → finish
```

### 3.7 桌面小部件

```text
系统 → NoteWidgetProvider_2x/4x.onUpdate → NoteWidgetProvider.update
 → getNoteWidgetInfo 按 widget id 查询绑定的便签（排除回收站）
     ├─ 有便签：显示摘要和背景色，点击 → NoteEditActivity(ACTION_VIEW)
     └─ 无便签：显示“暂无内容”，点击 → NoteEditActivity(ACTION_INSERT_OR_EDIT)

用户移除小部件 → onDeleted：把便签上的 widget_id 重置为无效，解除绑定
```

## 4. 阅读中发现的问题（待在 GitHub 建 Issue）

按组内规则，标注 PR 不修复缺陷，仅记录。Issue 链接建好后补在下表。“状态”一栏说明该问题是否已通过交叉阅读相关源码确认。

| 编号 | 位置 | 现象 | 影响判断 | 状态 | Issue 链接 |
|---|---|---|---|---|---|
| P1 | `NoteEditActivity.onClick` | 续行处有多余负号 `-View.VISIBLE` | `View.VISIBLE` 为 0，结果不变，属代码瑕疵 | 已确认无害 | 待补 |
| P2 | `NoteEditActivity.getHighlightQueryResult` | 搜索关键词直接 `Pattern.compile`，不做转义 | 含正则特殊字符（如 `(`）时可能抛 `PatternSyntaxException` | 静态确认，未运行复现 | 待补 |
| P3 | `WorkingNote.loadNote` | 仅在游标为 null 时抛异常，空结果集时静默保留默认值 | 错误被掩盖 | 已确认 | 待补 |
| P4 | `NoteWidgetProvider.update` | 遇到同一 widget id 多条记录时 `return` | 会中断本批次后面的小部件 | 已确认 | 待补 |
| P5 | `NoteEditActivity.sendToDesktop` | 使用 `INSTALL_SHORTCUT` 广播 | 新版 Android 上可能不再生效 | 未验证，需实机 | 待补 |
| P6 | `NoteWidgetProvider.update` | `privacyMode` 分支没有任何调用者传入 `true` | 未被使用的代码 | 已确认（全项目无其他调用） | 待补 |
| P7 | `WorkingNote.saveNote` | 忽略 `Note.syncNote` 的返回值；`syncNote` 在写 data 行之前已清空变更集 | data 写失败时 `saveNote()` 仍返回 true，且无法重试 | 已确认 | 待补 |
| P8 | `NoteEditActivity.onNewIntent` | 只调用 `initActivityState`：不保存、不刷新界面、忽略其返回值 | 见第 5 节第 5 条 | 机制确认，具体路径需实机 | 待补 |
| P9 | `NoteEditActivity.onClockAlertChanged` | `PendingIntent.getBroadcast` 的 flags 为 0，没有 `FLAG_IMMUTABLE` / `FLAG_MUTABLE` | `targetSdkVersion >= 31` 时在 Android 12+ 上会抛异常；目前 manifest 只声明了 `minSdkVersion=14` | 需看构建配置中的 targetSdk | 待补 |
| P10 | `NoteEditActivity.deleteCurrentNote` | 删除便签时不取消已登记的闹钟，也不清 `ALERTED_DATE` | 到点仍会触发广播；`AlarmAlertActivity` 有存在性检查兜底，不会崩溃，只是多一次无效触发 | 已确认，低优先级 | 待补 |

## 5. 待确认事项的核对结果

以下结论来自对相关源码的交叉阅读（涉及 `Note.java`、`Notes.java`、`DataUtils.java`、`GTaskManager.java`、`NotesDatabaseHelper.java`、`AlarmInitReceiver.java`、`AlarmAlertActivity.java`、`note_edit.xml`、`AndroidManifest.xml`）。均为静态阅读，尚未运行验证。

### 5.1 已确认

1. **`Note.setNoteValue` / `syncNote` 的行为**
   - `setNoteValue` 和 `NoteData.setTextData` 在写入字段的同时，都会设置 `LOCAL_MODIFIED=1` 和 `MODIFIED_DATE=当前时间`，所以修改正文也会更新修改时间。
   - `isLocalModified()` 的依据是“属性变更集非空，或正文/通话数据变更集非空”。
   - `syncNote` 的顺序：无修改直接返回 true → 更新 note 行 → 清空变更集 → 写 data 行；data 写失败时返回 false（由此引出 P7）。
2. **`TextNote.MODE` 对应 `DATA1`**：`TextNote.MODE = DATA1`，`MODE_CHECK_LIST = 1`；`WorkingNote` 以第 3 列（DATA1）读取模式，写入时也使用 `TextNote.MODE`。模式保存在 data 表的文本行上，而不是 note 表。
3. **普通模式的输入框确实是 `NoteEditText`，但回车、退格的特殊逻辑不生效**：`note_edit.xml` 中 `note_edit_view` 就是 `NoteEditText`；但监听器只在 `getListItem` 里注册，普通模式的输入框没有注册，所以回车、退格都走系统默认行为。特殊逻辑只在清单条目里生效。
4. **开启同步时删除改为“移到回收站”的原因**：回收站（`ID_TRASH_FOLER`）是“待同步删除”的标记区。`GTaskManager.syncContent` 会查询回收站中的记录，先删除远端对应节点，同步结束时再物理删除这些本地记录。另外有触发器保证文件夹进回收站时，其子便签一并进入；`NotesProvider` 的查询会排除回收站，所以界面上看不到。
   - 推论：开着同步删除后又取消同步账号，这些便签会一直留在回收站里，无人清理（静态推断）。
5. **闹钟的 `PendingIntent` 靠 data 区分便签**：`PendingIntent` 的身份由 requestCode 加 `Intent.filterEquals()`（action、data、type、component、categories）决定，extras 不参与。每条便签的 data 里带有各自的 id，所以 requestCode 固定为 0 也不会互相覆盖；重设提醒会覆盖旧闹钟，取消也能取消到。`AlarmInitReceiver` 开机重建闹钟使用同样的约定。

### 5.2 部分确认

6. **`onNewIntent` 之后是否一定经过 `onResume`：不一定。**
   - 确认的部分：`onNewIntent` 只调用 `initActivityState`，渲染完全依赖 `onResume`；manifest 为 `singleTop`，小部件的 PendingIntent 也带 `FLAG_ACTIVITY_SINGLE_TOP`。
   - 典型入口（点小部件或快捷方式）会先回到桌面，Activity 已处于 paused，会走 `onPause`（会保存）→ `onNewIntent` → `onResume`，所以界面会刷新，数据也不会丢。
   - 未确认的部分：如果新 Intent 到达时 Activity 正处于 resumed 状态，系统只回调 `onNewIntent`，不回调 `onResume`，此时模型已换而界面还是旧的，旧便签未保存的内容也会丢。能否真的触发这条路径，需要实机验证。
   - 另外 `onNewIntent` 忽略了 `initActivityState` 的返回值，失败时已 `finish()`，之后的 `onResume` 仍有可能访问为 null 的 `mWorkingNote`。

### 5.3 仍未验证

7. P5（`INSTALL_SHORTCUT` 在新版 Android 上是否失效）需要实机。
8. P9 是否真的触发，取决于构建配置中的 `targetSdkVersion`，目前工程里没有 build.gradle，无法判断。
9. 项目尚未在本地配通构建，以上所有结论均来自静态阅读，没有任何一条经过运行验证。报告中引用时请写“通过阅读源码确认”，不要写成“实测”。
