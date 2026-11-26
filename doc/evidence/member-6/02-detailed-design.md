# 成员 6 M09 / M10 详细设计

## 1. 总体结构

两项功能都遵循同一原则：**纯逻辑与平台代码分离**。

```text
界面层（Activity + 自定义 View）
   ├─ NoteStatsActivity  ──> StatsBarChartView
   └─ NoteReadingActivity
            │
逻辑层（纯 Java，可单测）
   ├─ NoteStatsCalculator（汇总 / 趋势 / 分布）
   └─ ReadingPrefs（主题、字号、行距、阅读时长）
            │
数据层
   ├─ NoteStatsRepository（读 note / data 表）
   └─ ReadingPreferenceStore（SharedPreferences）
```

这样划分的原因：

1. 统计规则和阅读时长估算是最容易出错、也最需要回归的部分，放在纯 Java 类中可以直接用 JUnit 在 JVM 上跑，不需要模拟器。
2. 数据读取集中在一处，统计页面只负责展示，避免 Activity 里堆 SQL 和格式换算。
3. 后续若其他页面也要展示统计或复用阅读偏好，可以直接复用逻辑层与数据层，不必复制代码。

## 2. M09 类设计

### 2.1 `tool.NoteStatsCalculator`

纯计算类，无 Android 依赖。

| 方法 | 职责 |
|---|---|
| `summarize(notes, folderCount, now, recentDays)` | 返回便签总数、累计字符数、近期新增数、文件夹数 |
| `buildTrend(notes, days, now)` | 返回按天的新增数量数组，末位为今天 |
| `buildFolderDistribution(notes, folderNames, maxSlices, unknownLabel, otherLabel)` | 按文件夹聚合，降序排列，超出合并为“其他” |

关键设计点：

- `NoteRecord` 只保存 `id / folderId / createdDate / contentLength`，避免把正文读进内存。
- 日界计算使用 `Calendar` 逐日回退，而不是减去固定 86400000 毫秒，规避时区/夏令时导致的偏移。
- 所有方法对 `null` 入参按空列表处理，空输入返回全 0，不抛异常。

### 2.2 `tool.NoteStatsRepository`

数据访问类，返回 `Snapshot`（便签列表 + 文件夹数量 + 文件夹名称映射）。

查询：

| 数据 | URI | 条件 |
|---|---|---|
| 便签 | `Notes.CONTENT_NOTE_URI` | `type = TYPE_NOTE AND parent_id <> -3` |
| 正文长度 | `Notes.CONTENT_DATA_URI` | `mime_type = text_note`，按 `note_id` 累加 |
| 文件夹 | `Notes.CONTENT_NOTE_URI` | `type = TYPE_FOLDER AND parent_id <> -3` |

异常路径：查询返回 null 时按空数据处理；游标在 `finally` 中关闭；不做任何写操作。

### 2.3 `ui.NoteStatsActivity`

- 生命周期：`onCreate` 绑定控件，`onResume` 触发统计（保证新增/删除/恢复便签后返回本页能刷新）。
- 线程：后台线程查询 + `runOnUiThread` 渲染；`isFinishing()` 时丢弃结果。
- 状态：当前时间范围 `mRangeDays`（7 或 30），切换按钮置灰表示选中。
- 空数据：状态行显示“暂无便签数据”，图表为空。

### 2.4 `ui.StatsBarChartView`

自定义 `View`，只负责绘制：

- 柱高按最大值等比缩放；最大值为 0 时仍画基线与标签。
- 柱子数 > 10 时每 5 个绘制一次横轴标签，避免重叠。
- 标签超过 5 个字符截断加省略号。

## 3. M10 类设计

### 3.1 `tool.ReadingPrefs`

纯 Java 常量与换算：

| 项 | 取值 |
|---|---|
| 主题 | 浅色 / 护眼 / 深色（背景色与正文色各三套） |
| 字号 | 16 / 19 / 22 sp |
| 行距 | 1.3 / 1.6 / 1.9 倍 |
| 阅读时长 | `ceil(字符数 / 300)`，非空至少 1 分钟，空文本 0 |

越界档位一律回落到默认档，不做截断，避免偏好被写坏后界面异常。

### 3.2 `tool.ReadingPreferenceStore`

独立偏好文件 `note_reading_preferences`，三个键：主题、字号档位、行距档位。读时归一化、写时也归一化。

### 3.3 `ui.NoteReadingActivity`

- 入参：`EXTRA_NOTE_ID`（long）。
- 进入时只读查询一次正文（`data` 表 `mime_type = text_note`），不修改数据。
- 渲染：背景色、正文颜色、字号、行距、按钮文字颜色全部由偏好驱动。
- 交互：返回、主题、字号、行距四个按钮；每次修改立即保存并重绘。
- 空文本：正文显示“该便签暂无内容”，阅读时长留空。

### 3.4 入口挂载（对既有文件的少量改动）

| 文件 | 改动 |
|---|---|
| `res/menu/note_list.xml` | 新增 `menu_stats` 菜单项 |
| `res/menu/note_edit.xml` | 新增 `menu_reading_mode` 菜单项 |
| `NotesListActivity` | 菜单点击后启动 `NoteStatsActivity` |
| `NoteEditActivity` | 菜单点击后先 `saveNote()` 再启动 `NoteReadingActivity` |
| `AndroidManifest.xml` | 注册两个新 Activity |
| `res/values/strings.xml`、`res/values-zh-rCN/strings.xml` | 新增中英文案 |

## 4. 关键时序

### 4.1 打开统计页

```text
列表菜单点击
  → startActivity(NoteStatsActivity)
  → onCreate 绑定控件
  → onResume: 后台线程 NoteStatsRepository.load()
  → runOnUiThread: summarize / buildTrend / buildFolderDistribution
  → 填充卡片与两个 StatsBarChartView
```

### 4.2 进入阅读模式

```text
编辑菜单点击
  → saveNote()（把当前编辑内容同步到数据库）
  → startActivity(NoteReadingActivity, note_id)
  → onCreate: 只读查询文本正文 → 读取偏好 → applyPreferences()
  → 用户切换主题/字号/行距 → 写入偏好 → 立即重绘
  → 返回 → finish() → 回到编辑页
```

## 5. 线程与异常策略

- 统计查询在后台线程执行，主线程只做 UI 渲染；页面销毁后丢弃结果。
- 阅读页只读查询在主线程执行（单条便签，数据量小），游标在 `finally` 关闭。
- 两者都不捕获并吞掉数据库异常：若 Provider 不可用，按空数据展示，不崩溃。
- 偏好读写使用 `apply()` 异步落盘，不阻塞主线程。

## 6. 兼容性

- 应用 `targetSdk 25`、`minSdk 21`，新代码只使用 API 21 及以上可用的接口（`Canvas`、`SharedPreferences`、`Activity`），未引入 AndroidX 或第三方库。
- 未使用 `java.time` 等需要 API 26 或 desugaring 的接口，时间计算统一使用 `Calendar`。
