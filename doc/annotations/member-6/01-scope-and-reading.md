# 成员 6 代码阅读与标注范围说明

## 1. 基本信息

| 项 | 内容 |
|---|---|
| 负责人 | 成员 6 |
| 工作分支 | `member-6` |
| 标注对象分支基线 | `8bd46b3`（`member-6` / `origin/member-6` / `origin/develop` 同一点） |
| 上游原始提交 | `81a66024fe48eaa2206b854430856fe3875ff3ba`（MiCode Notes） |
| 代码工作目录 | `src/maintained/` |
| 只读对照目录 | `src/original/` |
| 阅读与标注完成日期 | 2026-11-22 |

## 2. 文件清单

下表路径均相对于 `src/maintained/src/net/micode/notes/`，行数为本次标注后的实际总行数（含注释与空行）。

| # | 文件 | 标注后行数 | 所属层 |
|---|---|---:|---|
| 1 | `tool/BackupUtils.java` | 447 | 工具/数据导出 |
| 2 | `ui/AlarmAlertActivity.java` | 215 | 界面（提醒） |
| 3 | `ui/AlarmInitReceiver.java` | 86 | 广播接收（提醒重建） |
| 4 | `ui/AlarmReceiver.java` | 41 | 广播接收（提醒触发） |
| 5 | `ui/DateTimePicker.java` | 592 | 自定义控件 |
| 6 | `ui/DateTimePickerDialog.java` | 134 | 对话框控件 |
| 7 | `ui/DropdownMenu.java` | 88 | 自定义控件 |
| 8 | `ui/NotesPreferenceActivity.java` | 497 | 界面（设置/同步） |
| 合计 | 8 个文件 | 2100 | — |

### 2.1 关于代码来源的说明（重要）

开始本任务时，`src/maintained/` 下只有一个 `README.md`，没有可标注的 Java 源码；8 个目标文件仅存在于 `src/original/src/net/micode/notes/` 下。

依据 `src/maintained/README.md` 中“开始维护时应从 `src/original/` 复制所需代码”的约定，本次把上述 8 个文件从 `src/original/` 原样复制到 `src/maintained/` 对应路径后再添加注释：

- 复制过程中未修改任何代码行；
- `src/original/` 未做任何改动；
- 已用“剥离注释后逐行比对”的方式验证：8 个文件的代码与 `src/original` 完全一致，差异只有新增注释（详见 `03-annotation-log.md` 第 3 节）。

由于 `src/maintained/` 目前缺少 `AndroidManifest.xml` 与 `res/`，无法作为完整工程构建，因此本次以静态检查与代码比对代替构建验证。

## 3. 模块职责

### 3.1 备份导出（`tool/BackupUtils`）

- 对外只有一个业务入口 `exportToText()`，把整库便签导出为纯文本文件，写到外部存储（SD 卡）。
- 数据来源是 Provider 的两张表：`note` 表（便签与文件夹统一存储）与 `data` 表（正文/通话记录），通过 `note_id` 关联。
- 采用两层游标：先按 `note` 表找文件夹与根便签，再按 `note_id` 查 `data` 表取正文。
- 结果以 `STATE_*` 常量返回，调用方据此提示用户；本类不修改数据库。
- 单例实现，持有传入的 `Context`。

### 3.2 闹钟提醒链路（`AlarmInitReceiver` → `AlarmReceiver` → `AlarmAlertActivity`）

1. `AlarmInitReceiver` 在系统广播（开机等）后，查询 `alert_date > 当前时间` 且 `type = TYPE_NOTE` 的便签，逐条向 `AlarmManager` 注册 `RTC_WAKEUP` 闹钟。
2. `AlarmReceiver` 在闹钟到点时收到广播，仅把 Intent 的目标组件改为 `AlarmAlertActivity` 并加 `FLAG_ACTIVITY_NEW_TASK` 后启动界面。
3. `AlarmAlertActivity` 从 Intent 的 data 路径段解析便签 id，校验便签仍存在且不在回收站，然后弹出对话框并循环播放系统默认闹钟铃声；对话框消失时停止铃声并结束界面。

三个类之间只通过“Intent 的 data 是 `content://.../note/<id>`”这一约定耦合，没有直接引用关系。

### 3.3 时间选择控件（`DateTimePicker` + `DateTimePickerDialog`）

- `DateTimePicker` 是自绘的 FrameLayout，由“日期 / 小时 / 分钟 / 上午下午”四个 `NumberPicker` 组成，对外只暴露一个毫秒时间值。
- 日期滚轮不是整月日历，而是“以选中日为中心的 7 天窗口”，靠滚轮值差值平移 `Calendar` 实现连续跨月。
- 12 小时制下，11↔12 翻转会同时影响 AM/PM 标志与日期（跨天），这是本控件最不显然的逻辑。
- `DateTimePickerDialog` 继承 `AlertDialog`，内部承载 `DateTimePicker`，滚动时实时刷新标题，点“确定”后把毫秒值回调给宿主。

### 3.4 下拉菜单（`DropdownMenu`）

- 把 `Button` 与 `PopupMenu` 绑成“点按钮弹菜单”的轻量组件，只负责展示、标题显示和点击回调转发，不处理菜单项语义。

### 3.5 设置与同步（`NotesPreferenceActivity`）

- 管理 Google 账号与 GTask 同步：选择/更换/移除同步账号、手动触发或取消同步、展示上次同步时间与实时进度。
- 通过广播接收 `GTaskSyncService` 的同步状态，在 `onDestroy` 反注册。
- 更换或移除账号时会在后台线程清空所有便签的 `gtask_id` / `sync_id`，断开与旧账号云端数据的绑定。

## 4. 关键流程与调用关系

```text
[设置页面 NotesPreferenceActivity]
    └─ 选择账号 → setSyncAccount() → 清 lastSyncTime → 后台清空 gtask_id/sync_id
    └─ 立即同步 → GTaskSyncService.startSync()
    └─ GTaskReceiver.onReceive → refreshUI() → loadSyncButton()

[便签编辑页设定提醒] → 写入 note.alert_date
    └─ AlarmInitReceiver.onReceive（开机/系统广播）
         └─ 查询未来提醒 → AlarmManager.set(RTC_WAKEUP)
              └─ AlarmReceiver.onReceive → 启动 AlarmAlertActivity
                   └─ 校验便签有效性 → 弹窗 + 播放铃声
                        └─ onDismiss → stopAlarmSound() + finish()

[设置页导出备份] → BackupUtils.getInstance(ctx).exportToText()
    └─ externalStorageAvailable() → 打开输出流 → 遍历文件夹与根便签 → 写 data 正文
```

## 5. 阅读中的疑问

1. `src/maintained/` 目前没有 `AndroidManifest.xml` 与 `res/`，无法完成真实构建；后续由谁、在哪个分支补齐，需要组长确认。
2. 标注 PR 是否必须先关联 Issue？当前环境没有 `gh` CLI 与可用 Token，无法创建 Issue，也未获得现成编号。
3. 注释量与注释行数的统一统计口径（是否统计空注释行、是否统计原有注释）全组尚未统一，本材料采用的口径见 `03-annotation-log.md`。
4. `BackupUtils` 单例持有 `Context`，实际调用方传入的是 Activity 还是 Application Context，需在维护阶段核实。

## 6. 发现但未修复的疑似缺陷（只记录，不在本次改动中修复）

按“标注阶段不改变程序行为、缺陷只记 Issue”的约定，以下问题仅在注释中说明，代码保持原样。因当前环境无 GitHub 权限，**尚未创建 Issue，也未获得编号**；待权限可用后补建并回填链接。

| # | 位置 | 现象 |
|---|---|---|
| D1 | `DateTimePickerDialog.updateTitle` | 三元表达式两个分支都取 `DateUtils.FORMAT_24HOUR`，导致 12/24 小时制切换对标题无效 |
| D2 | `AlarmAlertActivity.playAlarmSound` | 把 `MODE_RINGER_STREAMS_AFFECTED` 的位图值直接当作 `AudioManager` 流类型传入，与流类型语义不符 |
| D3 | `BackupUtils.exportNoteToText` | 便签分隔符使用 `Character.LINE_SEPARATOR` / `Character.LETTER_NUMBER` 两个 char 写入，并非预期换行 |
| D4 | `BackupUtils.exportToText` | 没有 `try/finally` 保证关闭输出流；且没有任何便签写出时仍返回 `STATE_SUCCESS` |
| D5 | `BackupUtils.generateFileMountedOnSDcard` | 使用 `mkdir()` 而非 `mkdirs()`，资源路径为多级目录且上级不存在时会创建失败 |
| D6 | `AlarmAlertActivity.stopAlarmSound` | 若铃声未能开始播放（`playAlarmSound` 异常），`stop()` 可能抛 `IllegalStateException` |

## 7. 本次标注的边界声明

- 只添加注释，未改动任何代码语句、导入顺序或格式。
- 未修改 `src/original/` 下任何文件。
- 未修改其他成员负责的文件。
- 未删除任何原有英文注释。
