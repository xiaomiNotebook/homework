# 成员 6 逐文件标注日志与真实统计

## 1. 统计口径（先明确口径，再看数字）

| 项 | 说明 |
|---|---|
| 统计对象 | `member-6` 分支工作区中 `src/maintained/src/net/micode/notes/` 下 8 个 Java 文件 |
| 基线提交 | `8bd46b3`（与 `origin/member-6`、`origin/develop` 同一点） |
| 对照文件 | `src/original/src/net/micode/notes/` 下同名文件 |
| 统计方法 | `git diff --no-index -U0 <original> <maintained>`，统计以 `+` 开头且非 `+++` 的行 |
| “注释行”定义 | 去掉行首空白后以 `//`、`/*`、`/**`、`*` 开头的整行 |
| 是否含行尾注释 | 否。本次全部为整行注释块，没有添加行尾注释 |
| 是否含原有注释 | “新增注释行”不含；“标注后注释行总数”含原文件自带注释 |
| 统计时间 | 2026-11-22 21:46（Asia/Shanghai） |
| 说明 | 计划文档中的“约 1416 行”为工作量参考（有效代码行），与本表的物理行数口径不同，不可直接比较 |

## 2. 逐文件统计

| # | 文件 | 原始行数 | 新增注释行 | 标注后行数 | 标注后注释行总数 |
|---|---|---:|---:|---:|---:|
| 1 | `tool/BackupUtils.java` | 344 | 103 | 447 | 154 |
| 2 | `ui/AlarmAlertActivity.java` | 158 | 57 | 215 | 76 |
| 3 | `ui/AlarmInitReceiver.java` | 65 | 21 | 86 | 36 |
| 4 | `ui/AlarmReceiver.java` | 30 | 11 | 41 | 26 |
| 5 | `ui/DateTimePicker.java` | 485 | 107 | 592 | 203 |
| 6 | `ui/DateTimePickerDialog.java` | 90 | 44 | 134 | 59 |
| 7 | `ui/DropdownMenu.java` | 61 | 27 | 88 | 42 |
| 8 | `ui/NotesPreferenceActivity.java` | 388 | 109 | 497 | 135 |
| 合计 | 8 个文件 | 1621 | 479 | 2100 | 731 |

派生指标（同一口径）：

- 新增注释行占标注后总行数：479 / 2100 = **22.8%**
- 标注后注释行总数占比：731 / 2100 = **34.8%**
- 原文件自带注释行：731 − 479 = **252 行**

## 3. 代码一致性验证

因 `src/maintained/` 初始为空目录（仅有 `README.md`），8 个文件按 `src/maintained/README.md` 的约定从 `src/original/` 复制后加注。
为证明“只加注释、未改行为”，做了两项检查：

1. **删除行检查**：对 8 个文件执行 `git diff --no-index -U0`，统计以 `-` 开头（非 `---`）的行，结果为 **0**，即没有删除或改写任何原有行。
2. **剥离注释后逐行比对**：对每个文件去掉注释行与空行后与 `src/original` 同名文件做逐字符比较，8 个文件结果均为 **一致**。

结论：本次改动为纯注释新增，代码逻辑、导入、格式与原有注释均未改动。

## 4. 逐文件注释位置与目的

### 4.1 `tool/BackupUtils.java`（+103 行）

| 注释位置 | 目的 |
|---|---|
| 类声明前 | 说明导出职责边界、单例原因、同步阻塞与 Context 持有风险 |
| `getInstance` | 说明单例创建时机与参数含义 |
| `STATE_*` 常量块 | 说明结果码语义与返回顺序 |
| `externalStorageAvailable` | 说明只有 `MEDIA_MOUNTED` 可用 |
| `exportToText()`（外层） | 说明这是唯一业务入口 |
| `TextExport` 类声明前 | 解释 note/data 两表模型与两层游标设计 |
| `NOTE_PROJECTION` / `DATA_PROJECTION` | 说明投影列与下标常量的绑定关系、各列语义 |
| `TextExport` 构造方法 | 说明输出格式来自资源数组 |
| `exportFolderToText` | 说明只导出直接子节点、不递归；游标关闭与 null 处理 |
| `exportNoteToText` | 说明按 mime 分派、通话记录三行输出、分隔符的非常规写法 |
| `TextExport.exportToText` | 说明五步主流程与两个已知边界（无 try/finally、无数据仍成功） |
| 文件夹查询 / 根便签查询语句前 | 解释筛选条件为何要 OR 通话记录文件夹、两类便签为何不重复 |
| `getExportToTextPrintStream` | 说明成功时缓存文件名/目录的副作用 |
| `generateFileMountedOnSDcard` | 说明文件名规则、`mkdir` 与 `mkdirs` 的差异风险 |

### 4.2 `ui/AlarmAlertActivity.java`（+57 行）

| 注释位置 | 目的 |
|---|---|
| 类声明前 | 说明在提醒链路中的位置、Intent 前置条件、便签有效性校验、生命周期收口 |
| 字段 `mNoteId` / `mSnippet` / `SNIPPET_PREW_MAX_LEN` | 说明数据来源与截断阈值 |
| `onCreate` | 说明窗口标志意图、摘要截断、解析失败的异常路径 |
| `isScreenOn` | 说明该判断同时影响点亮屏幕与“进入便签”按钮 |
| `playAlarmSound` | 说明流类型选择的非常规实现、循环播放、四类异常各自吞掉的原因 |
| `showActionDialog` | 说明两个按钮的交互设计与锁屏差异 |
| `onClick` / `onDismiss` | 说明点击只负责跳转、铃声与结束界面统一在 onDismiss 收口 |
| `stopAlarmSound` | 说明置空引用保证幂等 |

### 4.3 `ui/AlarmInitReceiver.java`（+21 行）

| 注释位置 | 目的 |
|---|---|
| 类声明前 | 说明为什么需要开机重建、查询条件与“不修改数据库”的边界 |
| `PROJECTION` / 列下标常量 | 说明投影与下标对应关系 |
| `onReceive` | 说明主线程同步查询的代价、PendingIntent 身份由 data Uri 决定 |
| 循环体内 | 说明 data 中编入便签 id 的用途、`RTC_WAKEUP` 的语义 |

### 4.4 `ui/AlarmReceiver.java`（+11 行）

| 注释位置 | 目的 |
|---|---|
| 类声明前 | 说明这一层存在的原因、为何必须加 `FLAG_ACTIVITY_NEW_TASK`、data 不能被改写 |
| 方法体内 | 说明复用 Intent 只替换组件时会保留 data 与 flags |

### 4.5 `ui/DateTimePicker.java`（+107 行）

| 注释位置 | 目的 |
|---|---|
| 类声明前 | 说明为何自绘控件、mDate 为唯一事实来源、mInitialising 的作用 |
| 范围常量 | 说明 12 小时制小时范围是 1~12 而非 0~11 |
| `mDateDisplayValues` 等字段 | 说明 7 天窗口、mIsAm/mIs24HourView 只影响显示 |
| `mOnDateChangedListener` | 说明用 newVal − oldVal 差值平移日期，实现连续跨月 |
| `mOnHourChangedListener` | 说明 11↔12 翻转、AM/PM 与跨天的两种分支、最终写回 0~23 |
| `mOnMinuteChangedListener` | 说明 59↔0 环绕等价于进/退一小时，只处理 ±1 |
| `mOnAmPmChangedListener` | 说明直接加减 12 小时、与 mIsAm 共用状态 |
| `OnDateTimeChangedListener` 与三个构造方法 | 说明默认初值、制式来源、构造顺序（先制式后日期） |
| `setEnabled` | 说明必须同时禁用子滚轮 |
| `setCurrentDate(long)` | 说明逐字段写回会触发多次回调（保留原 `@param`） |
| `getCurrentHour` | 说明 12 小时制换算与 0 点显示为 12 |
| `setCurrentHour` | 说明先写真实小时再换算显示、并反向修订 mIsAm |
| `set24HourView` | 说明三步顺序不可颠倒 |
| `updateDateControl` | 说明 −(DAYS/2+1) 再逐次 +1 的窗口算法、下标 3 为中心、为何先清空显示值 |
| `updateAmPmControl` | 说明 Calendar.AM/PM 与滚轮 0~1 恰好对齐 |
| `updateHourControl` | 说明两种制式的范围 |
| `onDateTimeChanged` | 说明统一分发点与空监听保护 |

### 4.6 `ui/DateTimePickerDialog.java`（+44 行）

| 注释位置 | 目的 |
|---|---|
| 类声明前 | 说明职责边界、标题与返回值同源的数据流 |
| 字段与回调接口 | 说明 mDate 为唯一数据源、回调由宿主保存 |
| 构造方法 | 说明先注册监听再设初值、秒清零、取消按钮传 null 的含义 |
| `set24HourView` / `setOnDateTimeSetListener` | 说明只记录标志、不主动刷新 |
| `updateTitle` | 说明 24/12 分支取值相同导致切换无效（D1） |
| `onClick` | 说明确定按钮回调与空监听保护 |

### 4.7 `ui/DropdownMenu.java`（+27 行）

| 注释位置 | 目的 |
|---|---|
| 类声明前 | 说明封装目的、职责边界、生命周期 |
| 构造方法 | 说明构造即完成绑定、button 同时是锚点、menuId 来源 |
| `setOnDropdownMenuItemClickListener` | 说明允许 null 与空判断原因 |
| `findItem` / `setTitle` | 说明供调用方动态改菜单项与按钮文案 |

### 4.8 `ui/NotesPreferenceActivity.java`（+109 行）

| 注释位置 | 目的 |
|---|---|
| 类声明前 | 说明设置页职责边界、界面结构、生命周期与广播反注册 |
| 各 Preference 键常量 | 说明每个键的用途与“必须保持稳定”的约束 |
| `onCreate` | 说明加载偏好、注册广播、插入 header，以及只在 onDestroy 反注册的既有行为 |
| `onResume` | 说明新增账号的差集自动选中算法与 mOriAccounts 为 null 的边界 |
| `onDestroy` | 说明反注册防止泄漏 |
| `loadAccountPreference` | 说明三种点击分支的优先级不可调换 |
| `loadSyncButton` | 说明同步中/非同步的状态机与按钮禁用条件 |
| `refreshUI` | 说明两类内容为何总是一起重构 |
| `showSelectAccountAlertDialog` | 说明账号快照与 mHasAddedAccount 的作用、空列表与 checkedItem=-1 |
| `showChangeAccountConfirmAlertDialog` | 说明破坏性操作需二次确认、取消项行为 |
| `getGoogleAccounts` | 说明只按账号类型过滤 |
| `setSyncAccount` | 说明三步副作用、后台线程清理与异步时序风险、幂等性 |
| `removeSyncAccount` | 说明与 setSyncAccount 的差异 |
| `getSyncAccountName` / `setLastSyncTime` / `getLastSyncTime` | 说明空串约定、0 值语义 |
| `GTaskReceiver.onReceive` | 说明整体刷新 + 进度文本覆盖的条件 |
| `onOptionsItemSelected` | 说明返回列表并 `CLEAR_TOP` 清栈 |

## 5. 未完成事项

1. `04-peer-review.md` 待成员 1 复核后补齐（复核意见、修订结果、构建结果、PR 链接）。
2. 第 6 节所列疑似缺陷（D1–D6）尚未创建 Issue：当前环境无 `gh` CLI 与可用 Token，未获得 Issue 编号，不伪造编号。
3. 因 `src/maintained/` 缺 `AndroidManifest.xml` 与 `res/`，本次未做真实构建；构建验证需在工程补齐后补做。
