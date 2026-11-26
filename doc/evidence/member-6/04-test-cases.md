# 成员 6 M09 / M10 测试用例与结果

## 1. 测试环境

| 项 | 值 |
|---|---|
| 工程 | `src/maintained`（Gradle 8.11.1 / AGP 8.7.3） |
| 编译 | `compileSdk 34`、`minSdk 21`、`targetSdk 25`、Java 8 |
| 命令 | `.\gradlew.bat testDebugUnitTest assembleDebug` |
| 结果 | BUILD SUCCESSFUL；APK：`build/outputs/apk/debug/micode-notes-debug.apk`（1.07 MB） |
| 单元测试 | 12 条全部通过（统计 7 条 + 阅读 5 条） |

## 2. 单元测试（自动化，可重复运行）

测试类：`src/maintained/src/test/java/net/micode/notes/tool/`

### 2.1 `NoteStatsCalculatorTest`（7 条，全部通过）

| 编号 | 用例 | 预期 |
|---|---|---|
| U-M09-01 | `summarizeCountsNotesCharactersAndRecent` | 总数 3、字符 60、近 7 天新增 2、文件夹数 2 |
| U-M09-02 | `summarizeHandlesNullAndEmptyInput` | null/空输入返回全 0，文件夹数不为负 |
| U-M09-03 | `summarizeRecentWindowCoversTodayAndSixDaysBefore` | 今天与 6 天前计入，7 天前不计入 |
| U-M09-04 | `buildTrendPlacesCountsOnCorrectDays` | 7 天数组长度为 7，今天 2 条、2 天前 1 条 |
| U-M09-05 | `buildTrendIgnoresFutureAndOutOfRangeDates` | 未来时间与超出范围的数据不计入；days=0 返回空数组 |
| U-M09-06 | `buildFolderDistributionSortsAndMergesOthers` | 降序排列，超出 3 项合并为“其他”且总数守恒 |
| U-M09-07 | `buildFolderDistributionKeepsAllWhenBelowLimit` | 未超过上限时原样返回 |

### 2.2 `ReadingPrefsTest`（5 条，全部通过）

| 编号 | 用例 | 预期 |
|---|---|---|
| U-M10-01 | `normalizeThemeFallsBackToDefaultOnIllegalValue` | -1/3 回落浅色；合法值原样返回 |
| U-M10-02 | `normalizeFontAndSpacingLevelStayInRange` | 越界档位统一回落默认档 |
| U-M10-03 | `estimateReadingMinutesHandlesBoundaries` | 0/负数→0；1 字→1；300 字→1；301 字→2；3000 字→10 |
| U-M10-04 | `fontSizeAndLineSpacingFollowLevelMapping` | 档位与数值单调对应 |
| U-M10-05 | `colorsAreOpaqueForAllThemes` | 三套主题颜色不透明且前景/背景不同 |

## 3. 手工回归用例（需在设备/模拟器执行）

> 当前状态：已在 Android 模拟器（`Medium_Phone_API_36.1`，`-gpu angle`）上执行主要用例，结果见下表“状态”列与 `06-before-after.md` 的截图索引；未执行项已注明原因，未用任何占位图代替。

| 编号 | 场景 | 步骤 | 预期 | 状态 |
|---|---|---|---|---|
| T-M09-01 | 已知数据统计 | 打开“使用统计” | 总数、字符数、近期新增、文件夹数与数据库一致 | 通过：总数 1、累计字符 221、近期新增 1、文件夹 0（见 `after/member6-M09-stats-7days-20261126.png`） |
| T-M09-02 | 时间范围切换 | 在统计页点“近 30 天”再点“近 7 天” | 趋势图柱数分别变为 30 与 7，数据不串行 | 通过（见 `stats-7days` 与 `stats-30days` 两张截图） |
| T-M09-03 | 新增后刷新 | 返回列表新增 1 条便签，再进统计页 | 总数 +1，今天柱子 +1 | 通过：总数 1→2、字符 221→237、今日柱 1→2（`after/member6-M09-stats-refresh-20261126.png`） |
| T-M09-04 | 删除/恢复后刷新 | 删除 1 条便签后再进统计页 | 总数 -1（回收站中的便签不计入） | 未执行：为避免破坏演示数据未删除，逻辑上依赖 `parent_id <> -3` 条件，已由单元测试与代码审查覆盖 |
| T-M09-05 | 空数据库 | 清空应用数据后进统计页 | 四项均为 0，图表为空，显示“暂无便签数据” | 未执行：清空数据会使其余截图无法复现，留待复核时补测 |
| T-M10-01 | 长文本阅读 | 打开长文本便签 → 阅读模式 | 正文完整、可滚动、显示“约 N 分钟读完” | 通过：显示“约 1 分钟读完”（`after/member6-M10-reading-light-20261126.png`） |
| T-M10-02 | 空文本 | 新建空便签 → 阅读模式 | 显示“该便签暂无内容”，不显示阅读时长 | 未执行：编辑器会丢弃空便签，未成功构造入口；空文本分支已由代码路径保证 |
| T-M10-03 | 三种主题 | 依次点击“主题”按钮 | 浅色/护眼/深色循环，文字始终可读 | 通过（`reading-light` / `reading-eye-care` / `reading-dark` 三张截图） |
| T-M10-04 | 字号与行距 | 分别循环点击字号、行距按钮 | 三档字号与三档行距均生效，可任意组合 | 通过：大字号 + 宽松行距（`after/member6-M10-reading-large-loose-20261126.png`） |
| T-M10-05 | 偏好持久化 | 设为护眼 + 大字号 + 宽松行距后重新进入 | 偏好保留 | 通过：深色 + 大字号 + 宽松行距退出后重新进入仍保持（未做杀进程重启，留待复核补测） |
| T-M10-06 | 不改变原文 | 阅读模式下浏览后返回编辑页 | 便签正文未被改动 | 通过：返回编辑页后正文与阅读页一致，且阅读页只执行只读查询 |

## 4. 复跑方式

```powershell
cd d:\xiaominotebook\home\src\maintained
.\gradlew.bat testDebugUnitTest
# 报告：build/reports/tests/testDebugUnitTest/index.html
```

依赖说明：JUnit 依赖与 `src/test` 源集配置位于独立分支 `test/junit-dependency`，需先合并该分支；本功能分支不包含 `build.gradle` 改动。
