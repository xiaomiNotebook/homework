# 成员 5 代码阅读记录（01）

> 标注对象分支基线：`develop` HEAD = `e5b9a34d15fe62f237b8b70746b3a80021330421`（member-5 已合并 develop 后的工作基线）
> 团队计划记录的上游原始提交：`81a66024fe48eaa2206b854430856fe3875ff3ba`（小米便签官方仓库，本作业 fork 未包含其完整历史，仅作来源记录）
> 复核人：成员 6（成员 5 复核成员 4）
> 统计日期：2026-11-24

## 1. 分配范围

| 序号 | 文件（相对 `src/maintained/src/net/micode/notes/`） | 模块 | 基线行数 | 标注后行数 | 新增注释行 |
|---:|---|---|---:|---:|---:|
| 1 | `gtask/exception/ActionFailureException.java` | gtask 异常 | 33 | 65 | 32 |
| 2 | `gtask/exception/NetworkFailureException.java` | gtask 异常 | 33 | 65 | 32 |
| 3 | `gtask/remote/GTaskSyncService.java` | gtask 同步调度 | 128 | 207 | 79 |
| 4 | `gtask/remote/GTaskASyncTask.java` | gtask 异步任务 | 127 | 189 | 62 |
| 5 | `gtask/remote/GTaskClient.java` | gtask HTTP 客户端 | 585 | 810 | 225 |
| 6 | `gtask/remote/GTaskManager.java` | gtask 同步编排 | 800 | 1074 | 274 |
| **合计** | | | **1706** | **2410** | **704** |

> 注：`GTaskASyncTask.java` 的 maintain 基线与 `src/original` 不完全一致——develop 上已有提交 `61a2e31 fix(gtask): 替换新 SDK 已移除的 Notification API`，将原项目已废弃的 `setLatestEventInfo` 写法改为 `Notification.Builder`。该改动是已合并的构建修复，不属于本次标注。因此“未改变程序行为”的验证对照的是 develop HEAD 基线，而非 `src/original`。

## 2. 模块职责总览

本包负责“小米便签与 Google Tasks（云端）的双向同步”，是整个项目中与外部网络交互最密集的模块。六个文件形成一条清晰的调用链：

```
NotesPreferenceActivity（设置页，触发同步）
        │  startSync(Activity)
        ▼
GTaskSyncService  ──── 调度入口 Service：启动/取消同步、广播进度给界面
        │  new GTaskASyncTask().execute()
        ▼
GTaskASyncTask   ──── AsyncTask 封装：后台线程跑同步，主线程更新通知与广播
        │  mTaskManager.sync(...)
        ▼
GTaskManager     ──── 同步编排核心：本地数据库 ↔ 云端的逐节点比对与增删改
        │  调用 GTaskClient
        ▼
GTaskClient      ──── HTTP 客户端单例：登录鉴权、拉取列表、提交动作
        │
        ▼
   Google Tasks（mail.google.com/tasks 移动网页接口，非官方 REST）

   ActionFailureException / NetworkFailureException
        └── 两类异常在链路中传递：网络层失败（受检） vs 业务/解析失败（非受检）
```

### 2.1 各文件职责

- **`gtask/exception/`**：定义同步链路中的两类异常。
  - `ActionFailureException`（继承 `RuntimeException`，非受检）：业务/逻辑失败，如未登录、JSON 解析失败、未知同步动作。最外层由 `GTaskManager.sync` 统一捕获并映射为 `STATE_INTERNAL_ERROR`。
  - `NetworkFailureException`（继承 `Exception`，受检）：网络传输失败，如连接超时、IO 异常。方法签名显式声明抛出，最外层映射为 `STATE_NETWORK_ERROR`。
- **`GTaskSyncService`**：同步的调度入口与生命周期管理。静态字段 `mSyncTask` 保证全局同一时刻只有一个同步；通过 `START_STICKY` 在被杀后重建；`onLowMemory` 主动取消同步；进度通过自定义广播发给设置页。
- **`GTaskASyncTask`**：把同步主流程包进 `AsyncTask`。`doInBackground` 调 `sync`；`onProgressUpdate`（主线程）更新通知栏并转发广播；`onPostExecute` 按结果码显示终态通知并触发完成回调。取消不是强中断线程，而是置位 `GTaskManager.mCancelled` 让同步循环自行退出，保证状态一致。
- **`GTaskClient`**：单例 HTTP 客户端。对接的是 Google Tasks 早期移动网页接口（HTML 内嵌 `_setup(...)` JS），靠字符串截取解析 JSON，对服务端页面结构敏感。鉴权用 `AccountManager` 取 `goanna_mobile` token → 带 token GET 登录页拿 Cookie → 后续 POST 复用 Cookie。支持自定义域账号、攒批更新（满 10 项自动提交）、每个动作带递增 `actionId`。
- **`GTaskManager`**：同步编排核心。维护三组映射：云端节点集 `mGTaskHashMap`（工作集，处理完即移除，剩余即“云端有本地无”）、gid↔本地 note id 双向映射、元数据 `mMetaHashMap`（用特殊 meta 列表保存本地原始内容，用于还原和冲突判定）。同步顺序：本地删除 → 文件夹 → 普通便签 → 云端剩余 → 批量清理回收站 → 刷新本地 `sync_id`。

## 3. 关键流程

### 3.1 一次完整同步的执行路径

1. 设置页调用 `GTaskSyncService.startSync(activity)` → 启动 Service，`onStartCommand` 按 extra 分派到 `startSync()`。
2. `startSync()` 创建 `GTaskASyncTask`（带完成回调），先广播“同步中”，再 `execute()`。
3. `doInBackground`（后台线程）→ `GTaskManager.sync(context, asyncTask)`：
   - 互斥检查 `mSyncing`；清空所有缓存映射。
   - `client.login(mActivity)`：取 token、种 Cookie、解析客户端版本号。
   - `initGTaskList()`：拉取云端列表，先建 meta 列表与 `mMetaHashMap`，再建各任务列表及其任务。
   - `syncContent()`：按“本地删除 → `syncFolder` → 普通便签 → 云端剩余 → 批量删除 → `refreshLocalSyncId`”顺序处理。
4. 同步过程中 `GTaskManager` 调 `asyncTask.publishProgess(...)` → `onProgressUpdate`（主线程）→ 更新通知 + 发广播。
5. `sync` 返回结果码 → `onPostExecute`（主线程）→ 按结果显示通知，成功则记录上次同步时间；然后在新建子线程触发 `onComplete` 回调。
6. 回调里 `mSyncTask = null`、发空进度广播、`stopSelf()` 停掉 Service。

### 3.2 关键数据结构与不显然设计

- **meta 列表**：云端有一个名为 `MIUI_FOLDER_PREFFIX + FOLDER_META` 的特殊任务列表，里面存 `MetaData` 记录，每条 meta 关联一个真实任务，保存其本地原始 id 与内容。作用是把云端任务还原为本地便签时保留本地 id（避免主键冲突时丢弃原 id），并辅助冲突判定。
- **工作集 `mGTaskHashMap`**：同步开始时装入所有云端节点，每匹配到一个本地项就移除；循环结束后剩余的就是“云端有、本地没有”的项，统一 `ADD_LOCAL`。这是判定“云端新增”的核心技巧。
- **攒批更新 `mUpdateArray`**：`updateRemoteNode` 等不立即发请求，而是把动作 JSON 放入 `mUpdateArray`；超过 10 项或遇到需要立即拿结果的操作（create/move/delete/commitUpdate）时才真正提交。减少请求次数。
- **`sync_id` 列**：同步末尾 `refreshLocalSyncId` 重新拉取云端，把每个节点的 `lastModified` 写入本地 `SYNC_ID` 列，作为下次同步比较“谁更新”的基准。
- **冲突处理**：`SYNC_ACTION_UPDATE_CONFLICT` 当前直接以本地覆盖远端（`updateRemoteNode`），未做内容合并。

## 4. 阅读中确认的事实（来自源码）

- 对接的是 `mail.google.com/tasks/ig` 移动网页接口，数据从 HTML 的 `_setup(...)}` 段中截取解析，不是官方 REST API（`GTaskClient.loginGtask` / `getTaskLists`）。
- 登录 Cookie 约定 5 分钟过期（`GTaskClient.login` 的 `interval`）。
- 非 gmail/googlemail 账号走自定义域 URL `/a/<domain>/ig`。
- 取消同步是协作式取消：置 `mCancelled` 标志，在登录前、各阶段之间、每个 `doContentSync` 入口检查。
- 便签↔云端任务、文件夹↔云端任务列表的映射，以及 meta 的关联，全部在 `GTaskManager` 内完成。

## 5. 发现的疑似缺陷（仅记录 Issue，不在标注阶段修复）

> 按计划要求：标注阶段发现缺陷只记录 Issue，留到维护阶段或追加门禁处理。以下均为阅读源码后确认的疑点，需在维护阶段复核复现。

| 编号 | 位置 | 描述 | 影响 |
|---|---|---|---|
| D1 | `GTaskManager.mCancelled` / `mSyncing` | 两个跨线程访问的布尔字段未声明 `volatile`，取消标志可能存在可见性问题 | 取消请求在极端情况下不被同步线程及时观察到 |
| D2 | `GTaskManager.mActivity` | 单例长期持有 Activity 上下文 | 设置页退出后可能内存泄漏 |
| D3 | `GTaskClient.getResponseContent` | gzip/deflate 分支先 `entity.getContent()` 赋给 input，又再次 `entity.getContent()` 构造解压流；第二次取流可能已读到 EOF | 响应体在压缩编码下可能解析为空，导致登录/拉取失败 |
| D4 | `GTaskClient.loginGoogleAccount` | `invalidateToken=true` 时递归取新 token，但递归返回值未赋回 `authToken`，本层仍返回旧 token | token 失效重试时可能仍拿旧 token 登录 |
| D5 | `GTaskManager.doContentSync` 的 `SYNC_ACTION_UPDATE_CONFLICT` | 冲突时直接以本地覆盖远端，未做合并 | 双方都修改时云端修改丢失 |
| D6 | `GTaskASyncTask.onPostExecute` | 完成回调在新建子线程执行，回调内调用 `stopSelf`/`sendBroadcast` | 线程安全与生命周期边界需关注 |
| D7 | `GTaskClient` 整体 | 使用已废弃的 `org.apache.http` / `DefaultHttpClient` | 新 Android 版本需额外配置；已由 develop 上的构建环境部分适配 |

## 6. 疑问与待确认项

1. **构建验证**：`src/maintained` 已含 `build.gradle`、`gradlew`（develop 合并自 `feature/gradle-build-env`），但本机尚未实际执行 `./gradlew assembleDebug` 验证可编译。需在推 PR 前或评审时补一次构建记录。
2. **成员 4 复核**：按闭环关系，成员 5 需复核成员 4 的标注。截至 2026-11-24，`doc/annotations/member-4/` 仅有 `README.md`，成员 4 尚未提交 01–03 材料与代码注释。需在成员 4 推送后补做复核并填写本人的 `04-peer-review.md`（针对成员 4）以及成员 6 对本人的复核记录。
3. **D3/D4 的复现**：疑似缺陷需在维护阶段用抓包或日志确认，避免误判。

## 7. 10 分制工作包自评（阅读标注维度）

| 组成 | 分值 | 说明 |
|---|---:|---|
| 有效代码规模 | 4 | 6 文件 1706 行，覆盖同步全链路（异常/调度/异步/网络/编排），无生成代码 |
| 逻辑与调用复杂度 | 3 | 含跨线程协作（mCancelled）、工作集增量判定、meta 还原、攒批提交、HTML 截取解析等非显然逻辑 |
| 架构说明与功能映射 | 2 | 本文 + 02 文档已给出模块职责、调用链、功能—类—方法映射 |
| 同伴复核 | 1 | 待成员 6 复核本人；本人待复核成员 4（成员 4 尚未提交） |
| 合计 | 10 | |
