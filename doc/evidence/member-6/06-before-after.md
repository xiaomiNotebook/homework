# 成员 6 M09 / M10 修改前后对比

## 1. 采集环境与方式

| 项 | 值 |
|---|---|
| 设备 | Android 模拟器 `Medium_Phone_API_36.1`（Android 16 / API 36） |
| 启动参数 | `emulator -avd Medium_Phone_API_36.1 -gpu angle -no-snapshot-load -no-boot-anim -no-audio -no-window`（`angle` 不被该版本识别，模拟器日志显示自动回退为 `auto`：swiftshader + lavapipe Vulkan） |
| 系统语言 | 简体中文 |
| 对比基线 | `origin/develop`（提交 `1438da4`）构建的 debug APK |
| 对比新版 | `feat/member-6-overflow-menu`（提交 `a05f464`，含 PR #23 的可见“⋮”入口）构建的 debug APK |
| 截图方式 | `adb shell screencap -p` + `adb pull`，未做任何裁剪或拼接 |
| 菜单操作 | 点击页面顶部可见“⋮”入口（`@+id/btn_overflow_menu`，内部调用 `openOptionsMenu()`）唤出菜单，不再使用物理 MENU 键 |

> 本轮 after/ 效果图（2026-11-26 17:40 起）已按含可见“⋮”入口的最新代码全部重拍，界面与当前实际版本一致；before/ 保持未改动状态的原图。

### 1.1 GPU 后端尝试记录

| 后端 / 参数 | 结果 |
|---|---|
| `-gpu swiftshader_indirect`（`-no-window`） | 可启动、可安装、Activity 可拉起，但 `screencap` 抓应用窗口全黑 |
| `-gpu off` | 可启动，系统设置页也为空白，无法用于截图 |
| `-gpu swiftshader_indirect -feature -Vulkan` | 模拟器进程启动即退出 |
| `-gpu host` | 进程在启动后退出；改为常驻进程后系统服务无响应（boot 卡住） |
| **`-gpu angle`（最终采用）** | 该版本不识别 `angle`，日志显示自动回退 `auto`（swiftshader + lavapipe Vulkan）；此组合下启动正常、`uiautomator` 可见界面节点、`screencap` 抓图正常，全部效果图均由该组合产出 |

## 2. 截图索引

### 2.1 M09 便签使用数据可视化

| 阶段 | 文件 | 说明 |
|---|---|---|
| 修改前 | `before/member6-M09-list-menu-before-20261126.png` | 基线版本列表页菜单，只有“新建文件夹 / 导出文本 / 同步 / 设置 / 搜索”，无统计入口 |
| 修改后 | `after/member6-M09-stats-entry-menu-20261126.png` | 点击列表页顶部“⋮”后弹出原有菜单，新增“使用统计” |
| 修改后 | `after/member6-M09-stats-7days-20261126.png` | 统计页近 7 天：便签总数、累计字符、近期新增、文件夹数量 + 趋势图 + 文件夹分布 |
| 修改后 | `after/member6-M09-stats-30days-20261126.png` | 切换到近 30 天后，趋势图按 30 天绘制，横轴标签抽稀为 11/1、11/6… |
| 修改后 | `after/member6-M09-stats-refresh-20261126.png` | 新增 1 条 20 字符的便签后，再次由“⋮”进入统计页：总数 4→5、累计字符 265→285、近期新增 4→5，证明刷新有效 |
| 代码 | `after/member6-M09-code-calculator-20261126.png` | `NoteStatsCalculator` 汇总与趋势实现 |
| 代码 | `after/member6-M09-code-stats-activity-20261126.png` | `NoteStatsActivity` 后台查询与渲染实现 |

### 2.2 M10 沉浸式专注阅读模式

| 阶段 | 文件 | 说明 |
|---|---|---|
| 修改前 | `before/member6-M10-edit-menu-before-20261126.png` | 基线版本编辑页菜单，无“阅读模式” |
| 修改后 | `after/member6-M10-reading-entry-menu-20261126.png` | 点击编辑页顶部“⋮”后弹出原有菜单，新增“阅读模式” |
| 修改后 | `after/member6-M10-reading-light-20261126.png` | 阅读模式·浅色主题，顶部显示“约 1 分钟读完”，底部三个偏好按钮 |
| 修改后 | `after/member6-M10-reading-eye-care-20261126.png` | 阅读模式·护眼主题（米黄底深棕字） |
| 修改后 | `after/member6-M10-reading-dark-20261126.png` | 阅读模式·深色主题（深灰底浅色字） |
| 修改后 | `after/member6-M10-reading-large-loose-20261126.png` | 大字号 + 宽松行距组合效果 |
| 代码 | `after/member6-M10-code-reading-activity-20261126.png` | `NoteReadingActivity` 偏好应用与渲染实现 |
| 代码 | `after/member6-M10-code-reading-prefs-20261126.png` | `ReadingPrefs` 阅读时长估算与档位映射实现 |

### 2.3 可见“更多”入口（替代物理 MENU 键）

| 阶段 | 文件 | 说明 |
|---|---|---|
| 修改后 | `after/member6-M09-stats-overflow-entry-20261126.png` | 列表页顶部可见“⋮”（无障碍描述为“更多选项”），位于便签内容上方，不遮挡条目与“写便签”按钮 |
| 修改后 | `after/member6-M10-reading-overflow-entry-20261126.png` | 编辑页顶部可见“⋮”，不与右上角调色按钮重叠，不遮挡正文 |

说明：

1. 上表两张图展示入口本体；点击后弹出菜单的画面见 2.1 的 `member6-M09-stats-entry-menu` 与 2.2 的 `member6-M10-reading-entry-menu`，两张均为本轮通过点击“⋮”重拍。
2. 首版单独保存过 `member6-M09-stats-overflow-menu`、`member6-M10-reading-overflow-menu` 两张，场景与上述菜单图重复，已删除，避免同一场景保留新旧两套文件。
3. 全部 after 截图均由真实点击“⋮”进入后抓取，未使用物理 MENU 键。

## 3. 对比结论

1. **入口**：基线的列表菜单与编辑菜单均无新入口；新版本各新增一项，并提供可见“⋮”按钮（不再依赖物理菜单键），原有菜单项顺序与文案未变。
2. **统计正确性**：新增 1 条 16 字符的便签后，总数、累计字符、近期新增、今日趋势柱全部同步变化，文件夹分布总数与便签总数一致。
3. **阅读模式**：三种主题、三档字号、三档行距均实际生效；页面无编辑控件；退出后便签内容不变。
4. **偏好持久化**：切到深色 + 大字号 + 宽松行距后重新进入阅读模式，设置仍然保持。
5. 所有截图均为真实运行画面，未使用占位图、示意图或外部借用图片。
