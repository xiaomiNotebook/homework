# 成员 6 M09 / M10 实现细节与交接说明

## 1. 新增文件清单

| 文件 | 类型 | 说明 |
|---|---|---|
| `src/maintained/src/net/micode/notes/tool/NoteStatsCalculator.java` | 新增 | 统计数据换算（纯 Java） |
| `src/maintained/src/net/micode/notes/tool/NoteStatsRepository.java` | 新增 | 统计数据的 Provider 查询 |
| `src/maintained/src/net/micode/notes/ui/NoteStatsActivity.java` | 新增 | 统计页面 |
| `src/maintained/src/net/micode/notes/ui/StatsBarChartView.java` | 新增 | 柱状图自定义 View |
| `src/maintained/src/net/micode/notes/tool/ReadingPrefs.java` | 新增 | 阅读偏好换算（纯 Java） |
| `src/maintained/src/net/micode/notes/tool/ReadingPreferenceStore.java` | 新增 | 阅读偏好持久化 |
| `src/maintained/src/net/micode/notes/ui/NoteReadingActivity.java` | 新增 | 阅读模式页面 |
| `src/maintained/res/layout/activity_note_stats.xml` | 新增 | 统计页布局 |
| `src/maintained/res/layout/activity_note_reading.xml` | 新增 | 阅读页布局 |
| `src/maintained/res/drawable/stats_card_bg.xml` | 新增 | 统计卡片背景 |
| `src/maintained/src/test/java/net/micode/notes/tool/NoteStatsCalculatorTest.java` | 新增 | 统计逻辑单元测试（7 条） |
| `src/maintained/src/test/java/net/micode/notes/tool/ReadingPrefsTest.java` | 新增 | 阅读偏好单元测试（5 条） |

## 2. 修改文件清单

| 文件 | 改动内容 | 风险控制 |
|---|---|---|
| `res/menu/note_list.xml` | 新增 `menu_stats` 项 | 只加一项，不改动既有项 |
| `res/menu/note_edit.xml` | 新增 `menu_reading_mode` 项 | 只加一项，不改动既有项 |
| `NotesListActivity.java` | `onOptionsItemSelected` 新增一个 `case` | 只新增分支，不改动既有分支 |
| `NoteEditActivity.java` | 新增 `openReadingMode()` 与一个 `case` | 只新增方法/分支，不改动既有逻辑 |
| `AndroidManifest.xml` | 注册两个新 Activity | 只新增节点 |
| `res/values/strings.xml`、`res/values-zh-rCN/strings.xml` | 新增 `stats_*`、`reading_*` 文案 | 只新增字符串，不改既有键 |

> 测试依赖（`build.gradle` 的 `testImplementation junit` 与 `java.exclude 'test/**'`）**不在本分支**，见独立分支 `test/junit-dependency` 与独立 PR，避免与功能 PR 混合。

## 3. 关键实现点与边界

### 3.1 统计

- 字符数按 `content.length()` 累加；一条便签有多条 data 行时累加而不是取第一条。
- 趋势数组下标 0 为最早一天、末位为今天；30 天视图横轴每 5 天显示一次标签。
- 文件夹分布最多展示 6 项，其余合并为“其他”；未知文件夹归入“未分类”；根目录与通话记录目录的名称由界面按语言补入，仓库层不写死中文。
- 统计页面在 `onResume` 重新统计，覆盖“新增/删除/从回收站恢复后返回统计页”的场景。

### 3.2 阅读

- 阅读页只读，进入前由编辑页 `saveNote()` 落盘，确保读到最新内容。
- 空文本显示“该便签暂无内容”，阅读时长留空。
- 偏好越界统一回落到默认档；偏好文件独立，不影响设置页。

### 3.3 已知限制

- 统计页为一次性全量查询，便签量极大时首次加载会变慢；当前数据规模可接受，若后续需要可加分页或缓存。
- 柱状图不支持横向滚动，30 天视图靠抽稀标签保证可读性。
- 阅读页不解析富文本/清单结构，按纯文本展示（与现有便签正文存储方式一致）。

## 4. 构建与运行

```powershell
cd d:\xiaominotebook\home\src\maintained
.\gradlew.bat testDebugUnitTest assembleDebug
# 产物：build/outputs/apk/debug/micode-notes-debug.apk
```

要求：JDK 17+、Android SDK（`local.properties` 指向本机 SDK）、可用的 `compileSdk 34` 平台。

## 5. 提交与 PR 安排

| 分支 | 内容 | 目标 | 说明 |
|---|---|---|---|
| `test/junit-dependency` | JUnit 依赖 + `src/test` 从主源集排除 | `develop` | 独立小 PR，需**先合并** |
| `feature/m09-m10-insights-reading` | M09 / M10 代码、资源、测试、本证据包 | `develop` | 功能 PR，基于 JUnit 分支 |

- 已提交：`feat(m09-m10): 新增便签使用统计与沉浸式阅读模式`、`test(m09-m10): 增加统计计算与阅读偏好单元测试`。
- 功能 PR 的描述中同步本文件第 6 节的入口约定。
- 截图补齐后追加 `docs(m06): 补充 M09/M10 证据与截图` 提交。

## 6. 入口约定 / 交接说明（供其他成员复用）

> 同一份内容已写入 `doc/README.md` 的「界面入口与资源命名约定」一节；本节为成员材料中的镜像，二者保持一致。

### 6.1 入口挂载位置

| 功能 | 页面 | 菜单文件 | 菜单项 id | 字符串资源 |
|---|---|---|---|---|
| M09 使用统计 | 便签列表页 | `res/menu/note_list.xml` | `@+id/menu_stats` | `@string/menu_stats` |
| M10 阅读模式 | 便签编辑页 | `res/menu/note_edit.xml` | `@+id/menu_reading_mode` | `@string/menu_reading_mode` |

约定：

1. 入口只通过菜单项挂载，不修改既有菜单项的顺序、id 或文案。
2. 新增菜单项放在同类项之前/之后均可，但必须在 PR 描述里注明，便于冲突时快速定位。
3. 字符串必须同时在 `res/values/strings.xml`（英文）与 `res/values-zh-rCN/strings.xml`（中文）中添加，避免缺翻译回退到英文。

### 6.2 跳转方式与参数

```java
// 列表页 → 统计页：无参数
startActivity(new Intent(this, NoteStatsActivity.class));

// 编辑页 → 阅读页：先保存，再传便签 id
saveNote();
Intent intent = new Intent(this, NoteReadingActivity.class);
intent.putExtra(NoteReadingActivity.EXTRA_NOTE_ID, mWorkingNote.getNoteId());
startActivity(intent);
```

约定：

- 新页面一律用显式 `Intent(context, XxxActivity.class)`，不使用隐式 action，避免被其他应用拦截。
- 需要“最新数据”的新页面，先调用当前页面的保存方法，再跳转。
- 页面之间只传 id 等小参数，正文由目标页面自己只读查询，避免 Intent 体积过大。

### 6.3 Activity 注册方式

```xml
<activity
    android:name=".ui.NoteStatsActivity"
    android:label="@string/stats_title"
    android:theme="@android:style/Theme.Holo.Light" />

<activity
    android:name=".ui.NoteReadingActivity"
    android:configChanges="keyboardHidden|orientation|screenSize"
    android:label="@string/reading_title"
    android:theme="@android:style/Theme.Holo.Light.NoActionBar" />
```

约定：

- 沉浸式/全屏类页面使用 `Theme.Holo.Light.NoActionBar` 并声明 `configChanges`，避免旋转时重建丢失阅读状态。
- 普通列表/统计类页面沿用 `Theme.Holo.Light`。
- 不修改其他 Activity 的 `launchMode` 与 `exported` 属性。

### 6.4 复用做法

其他成员新增功能页时建议：

1. 新建独立 Activity，不改写列表页/编辑页的主体逻辑。
2. 入口只在对应菜单文件加一项，并在 `onOptionsItemSelected` 加一个 `case`。
3. 数据读取与计算尽量拆成“纯逻辑类 + 数据访问类”，便于写 JVM 单元测试。
4. 新增字符串统一前缀化，避免与他人键名冲突。

### 6.5 需避开的命名与资源

以下命名已被 M09 / M10 占用，请勿复用：

| 类型 | 已占用 |
|---|---|
| 菜单项 id | `menu_stats`、`menu_reading_mode` |
| 字符串前缀 | `stats_*`、`reading_*` |
| Activity | `NoteStatsActivity`、`NoteReadingActivity` |
| 自定义 View | `StatsBarChartView` |
| 布局 | `activity_note_stats`、`activity_note_reading` |
| drawable | `stats_card_bg` |
| 偏好文件 | `note_reading_preferences` |
| 工具类 | `NoteStatsCalculator`、`NoteStatsRepository`、`ReadingPrefs`、`ReadingPreferenceStore` |

另外请避开既有菜单 id：`menu_setting`、`menu_share`、`menu_search`、`menu_delete`、`menu_move`、`menu_font_size` 等，新增项统一使用 `menu_<功能>` 命名。
