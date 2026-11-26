# 文档目录导航

- `annotations/`：六人的代码阅读、功能映射、标注日志和同伴复核材料。
- `evidence/`：M1—M6 维护任务的功能介绍、详细设计、测试、质量检查和前后截图。
- `report-reading-maintenance/`：《开源代码阅读、标注和维护报告》及中间材料。
- `report-quality/`：《开源软件质量分析报告》、工具配置、命令和原始输出。
- `ppt/`：成果展示 PPT、演示脚本、答辩题库和彩排记录。
- `UML/`：UML 可编辑源文件、导出图和图例说明。
- `screenshots/`：最终报告和 PPT 使用的公共截图及索引。
- `testing/`：全组测试计划、汇总表、回归结果和最终测试报告。

代码标注材料与维护证据必须分开保存。所有报告数字、图和截图都应能追溯到冻结提交、测试记录或个人证据包。

## 界面入口与资源命名约定（组内共享）

本节供全组新增功能页时复用，避免各自造一套入口与资源命名。最早由成员 6 在 M09 / M10 中落地，后续成员按同一规则挂载入口；完整示例见 `doc/evidence/member-6/03-detail.md` 第 6 节。

1. **入口挂载**：功能入口只通过在既有菜单文件（`res/menu/note_list.xml`、`res/menu/note_edit.xml` 等）新增一个 `item` 实现，不改动既有菜单项的顺序、id 与文案；在对应 Activity 的 `onOptionsItemSelected` 中新增一个 `case`。
2. **菜单项 id**：统一 `menu_<功能>`，如 `menu_stats`、`menu_reading_mode`；字符串资源同名，且必须同时加入 `res/values/strings.xml` 与 `res/values-zh-rCN/strings.xml`。
3. **跳转方式**：一律显式 `Intent(context, XxxActivity.class)`；需要“最新正文/最新数据”时，先调用来源页面的保存方法再跳转；页面间只传 id 等小参数，正文由目标页面只读查询。
4. **Activity 注册**：在 `AndroidManifest.xml` 以 `.ui.XxxActivity` 注册；沉浸式/全屏页用 `Theme.Holo.Light.NoActionBar` 并声明 `configChanges="keyboardHidden|orientation|screenSize"`，普通页沿用 `Theme.Holo.Light`；不修改其他 Activity 的 `launchMode` 与 `exported`。
5. **命名避让**：以下已被 M09 / M10 占用，请勿复用——菜单 `menu_stats`、`menu_reading_mode`；字符串前缀 `stats_*`、`reading_*`；Activity `NoteStatsActivity`、`NoteReadingActivity`；View `StatsBarChartView`；布局 `activity_note_stats`、`activity_note_reading`；drawable `stats_card_bg`；偏好文件 `note_reading_preferences`；工具类 `NoteStatsCalculator`、`NoteStatsRepository`、`ReadingPrefs`、`ReadingPreferenceStore`。

