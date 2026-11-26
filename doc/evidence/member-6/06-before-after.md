# 成员 6 M09 / M10 修改前后对比

## 1. 采集环境与方式

| 项 | 值 |
|---|---|
| 设备 | Android 模拟器 `Medium_Phone_API_36.1`（Android 16 / API 36） |
| 启动参数 | `emulator -avd Medium_Phone_API_36.1 -gpu angle -no-snapshot-load -no-boot-anim -no-audio -no-window` |
| 系统语言 | 简体中文 |
| 对比基线 | `origin/develop`（提交 `1438da4`）构建的 debug APK |
| 对比新版 | `member-6`（提交 `f23ff92`）构建的 debug APK |
| 截图方式 | `adb shell screencap -p` + `adb pull`，未做任何裁剪或拼接 |
| 菜单操作 | 该应用沿用旧式菜单（无 ActionBar），通过 `adb shell input keyevent 82`（MENU 键）唤出 |

### 1.1 GPU 后端尝试记录

| 后端 / 参数 | 结果 |
|---|---|
| `-gpu swiftshader_indirect`（`-no-window`） | 可启动、可安装、Activity 可拉起，但 `screencap` 抓应用窗口全黑 |
| `-gpu off` | 可启动，系统设置页也为空白，无法用于截图 |
| `-gpu swiftshader_indirect -feature -Vulkan` | 模拟器进程启动即退出 |
| `-gpu host` | 进程在启动后退出；改为常驻进程后系统服务无响应（boot 卡住） |
| **`-gpu angle`（最终采用）** | 启动正常，`uiautomator` 可见界面节点，`screencap` 抓图正常，全部效果图均由该组合产出 |

## 2. 截图索引

### 2.1 M09 便签使用数据可视化

| 阶段 | 文件 | 说明 |
|---|---|---|
| 修改前 | `before/member6-M09-list-menu-before-20261126.png` | 基线版本列表页菜单，只有“新建文件夹 / 导出文本 / 同步 / 设置 / 搜索”，无统计入口 |
| 修改后 | `after/member6-M09-stats-entry-menu-20261126.png` | 新版本列表页菜单新增“使用统计” |
| 修改后 | `after/member6-M09-stats-7days-20261126.png` | 统计页近 7 天：便签总数、累计字符、近期新增、文件夹数量 + 趋势图 + 文件夹分布 |
| 修改后 | `after/member6-M09-stats-30days-20261126.png` | 切换到近 30 天后，趋势图按 30 天绘制，横轴标签抽稀为 11/1、11/6… |
| 修改后 | `after/member6-M09-stats-refresh-20261126.png` | 新增 1 条便签后重新进入统计页，总数 1→2、字符 221→237、今天柱 1→2，证明刷新有效 |
| 代码 | `after/member6-M09-code-calculator-20261126.png` | `NoteStatsCalculator` 汇总与趋势实现 |
| 代码 | `after/member6-M09-code-stats-activity-20261126.png` | `NoteStatsActivity` 后台查询与渲染实现 |

### 2.2 M10 沉浸式专注阅读模式

| 阶段 | 文件 | 说明 |
|---|---|---|
| 修改前 | `before/member6-M10-edit-menu-before-20261126.png` | 基线版本编辑页菜单，无“阅读模式” |
| 修改后 | `after/member6-M10-reading-entry-menu-20261126.png` | 新版本编辑页菜单新增“阅读模式” |
| 修改后 | `after/member6-M10-reading-light-20261126.png` | 阅读模式·浅色主题，顶部显示“约 1 分钟读完”，底部三个偏好按钮 |
| 修改后 | `after/member6-M10-reading-eye-care-20261126.png` | 阅读模式·护眼主题（米黄底深棕字） |
| 修改后 | `after/member6-M10-reading-dark-20261126.png` | 阅读模式·深色主题（深灰底浅色字） |
| 修改后 | `after/member6-M10-reading-large-loose-20261126.png` | 大字号 + 宽松行距组合效果 |
| 代码 | `after/member6-M10-code-reading-activity-20261126.png` | `NoteReadingActivity` 偏好应用与渲染实现 |
| 代码 | `after/member6-M10-code-reading-prefs-20261126.png` | `ReadingPrefs` 阅读时长估算与档位映射实现 |

## 3. 对比结论

1. **入口**：基线的列表菜单与编辑菜单均无新入口；新版本各新增一项，且未改变原有菜单项的顺序与文案。
2. **统计正确性**：新增 1 条 16 字符的便签后，总数、累计字符、近期新增、今日趋势柱全部同步变化，文件夹分布总数与便签总数一致。
3. **阅读模式**：三种主题、三档字号、三档行距均实际生效；页面无编辑控件；退出后便签内容不变。
4. **偏好持久化**：切到深色 + 大字号 + 宽松行距后重新进入阅读模式，设置仍然保持。
5. 所有截图均为真实运行画面，未使用占位图、示意图或外部借用图片。
