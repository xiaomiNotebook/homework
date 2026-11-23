# 成员 6 功能—包—类—方法映射

本表只记录本次通过阅读源码确认存在的功能、类与方法，不使用计划文档中的候选描述代替源码证据。
路径均相对于 `src/maintained/src/net/micode/notes/`。

## 1. 功能到类、方法的映射

| 功能 | 包 | 类 | 关键方法 / 成员 | 说明 |
|---|---|---|---|---|
| 便签导出为文本备份 | `tool` | `BackupUtils` | `getInstance(Context)`、`exportToText()`、`getExportedTextFileName()`、`getExportedTextFileDir()` | 单例入口与对外结果查询 |
| 外部存储可用性判断 | `tool` | `BackupUtils` | `externalStorageAvailable()` | 仅 `MEDIA_MOUNTED` 视为可用 |
| 生成备份文件 | `tool` | `BackupUtils` | `generateFileMountedOnSDcard(...)` | 按资源路径 + 日期生成文件名 |
| 打开输出流 | `tool` | `BackupUtils.TextExport` | `getExportToTextPrintStream()` | 缓存文件名与目录 |
| 导出文件夹与根便签 | `tool` | `BackupUtils.TextExport` | `exportToText()`、`exportFolderToText(...)`、`exportNoteToText(...)` | 两层游标：note 表 → data 表 |
| 闹钟提醒重建 | `ui` | `AlarmInitReceiver` | `onReceive(...)` | 开机后把未来提醒重新注册到 AlarmManager |
| 闹钟到点转发 | `ui` | `AlarmReceiver` | `onReceive(...)` | 广播转 Activity，附加 `NEW_TASK` |
| 提醒弹窗与铃声 | `ui` | `AlarmAlertActivity` | `onCreate(...)`、`showActionDialog()`、`playAlarmSound()`、`stopAlarmSound()`、`onDismiss(...)`、`onClick(...)` | 便签有效性校验 + 循环铃声 |
| 屏幕状态判断 | `ui` | `AlarmAlertActivity` | `isScreenOn()` | 决定是否点亮屏幕、是否显示“进入便签” |
| 时间选择控件 | `ui` | `DateTimePicker` | `setCurrentDate(...)`、`getCurrentDateInTimeMillis()`、`set24HourView(...)`、`setEnabled(...)` | 四个滚轮组合成一个时间轴 |
| 滚轮联动 | `ui` | `DateTimePicker` | `mOnDateChangedListener`、`mOnHourChangedListener`、`mOnMinuteChangedListener`、`mOnAmPmChangedListener` | 跨天、跨午时的自动修正 |
| 滚轮显示刷新 | `ui` | `DateTimePicker` | `updateDateControl()`、`updateHourControl()`、`updateAmPmControl()` | 7 天窗口、12/24 小时范围、AM/PM 可见性 |
| 时间变更通知 | `ui` | `DateTimePicker.OnDateTimeChangedListener` | `onDateTimeChanged(...)`、`onDateTimeChanged()` | 统一回调出口 |
| 日期时间对话框 | `ui` | `DateTimePickerDialog` | 构造方法、`setOnDateTimeSetListener(...)`、`updateTitle(...)`、`onClick(...)` | 滚动实时刷新标题，确定后回调毫秒值 |
| 下拉菜单封装 | `ui` | `DropdownMenu` | 构造方法、`setOnDropdownMenuItemClickListener(...)`、`findItem(...)`、`setTitle(...)` | Button + PopupMenu 绑定 |
| 设置页初始化 | `ui` | `NotesPreferenceActivity` | `onCreate(...)`、`onResume(...)`、`onDestroy(...)` | 加载偏好、注册/反注册广播 |
| 同步账号管理 | `ui` | `NotesPreferenceActivity` | `loadAccountPreference()`、`showSelectAccountAlertDialog()`、`showChangeAccountConfirmAlertDialog()`、`setSyncAccount(...)`、`removeSyncAccount()`、`getGoogleAccounts()` | 选择/更换/移除账号及风险确认 |
| 同步按钮与状态 | `ui` | `NotesPreferenceActivity` | `loadSyncButton()`、`refreshUI()` | 同步中显示取消与进度，否则显示上次同步时间 |
| 偏好读写 | `ui` | `NotesPreferenceActivity` | `getSyncAccountName(...)`、`setLastSyncTime(...)`、`getLastSyncTime(...)` | 静态方法供全应用调用 |
| 同步进度广播 | `ui` | `NotesPreferenceActivity.GTaskReceiver` | `onReceive(...)` | 刷新界面并覆盖进度文本 |
| 返回导航 | `ui` | `NotesPreferenceActivity` | `onOptionsItemSelected(...)` | 返回列表并清栈 |

## 2. 类之间的关系

```text
AlarmInitReceiver ──注册 AlarmManager──> AlarmReceiver ──启动──> AlarmAlertActivity
                                                                    │
                                                                    └─ DataUtils.getSnippetById / visibleInNoteDatabase

DateTimePickerDialog ──持有──> DateTimePicker ──回调──> OnDateTimeChangedListener
        │
        └─ OnDateTimeSetListener ──返回毫秒值──> 调用方（便签编辑页）

NotesPreferenceActivity ──监听广播──> GTaskSyncService（gtask.remote）
        │
        └─ 读写──> SharedPreferences(notes_preferences) 与 note 表(gtask_id / sync_id)

BackupUtils（单例） ──读取──> NotesProvider（note 表 / data 表） ──写入──> 外部存储文本文件

DropdownMenu ──组合──> Button + PopupMenu（菜单项语义由调用方注入）
```

## 3. 跨包依赖

| 本包类 | 依赖的外部包 / 类 | 用途 |
|---|---|---|
| `BackupUtils` | `data.Notes`、`data.Notes.DataColumns`、`data.Notes.NoteColumns` | 表名、列名、URI 常量 |
| `AlarmAlertActivity` | `data.Notes`、`tool.DataUtils` | 便签类型与有效性、摘要读取 |
| `AlarmInitReceiver` | `data.Notes`、`data.Notes.NoteColumns` | 查询未来提醒 |
| `NotesPreferenceActivity` | `data.Notes`、`data.Notes.NoteColumns`、`gtask.remote.GTaskSyncService` | 同步状态、广播常量、清理云端绑定字段 |
| `DateTimePicker` / `DateTimePickerDialog` / `DropdownMenu` | 仅 `net.micode.notes.R` | 资源与布局 |

## 4. 包职责小结

| 包 | 本次涉及类 | 职责 |
|---|---|---|
| `net.micode.notes.tool` | `BackupUtils` | 与具体界面无关的通用能力（导出备份） |
| `net.micode.notes.ui` | `AlarmAlertActivity`、`AlarmInitReceiver`、`AlarmReceiver`、`DateTimePicker`、`DateTimePickerDialog`、`DropdownMenu`、`NotesPreferenceActivity` | 界面、广播接收与自定义控件 |
