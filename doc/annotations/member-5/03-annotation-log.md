# 成员 5 代码注释日志（03）

> 标注对象分支基线：develop HEAD `e5b9a34d15fe62f237b8b70746b3a80021330421`
> 统计日期：2026-11-24
> 统计口径：对 `src/maintained/src/net/micode/notes/gtask/remote/*.java` 与 `gtask/exception/*.java` 共 6 个文件，逐行分类为「代码行 / 注释行 / 空白行」；基线取自 `git show HEAD:<path>`，标注后取工作区文件。

## 1. 注释量对比总表

| 指标 | 基线（develop HEAD） | 标注后 | 变化 |
|---|---:|---:|---:|
| 总行数 | 1706 | 2410 | **+704** |
| 代码行 | 1294 | 1294 | **0** |
| 注释行 | 172 | 876 | +704 |
| 空白行 | 240 | 240 | 0 |
| 删除行 | 0 | 0 | — |
| 无意义注释（空注释 / 逐句翻译代码） | 0 | 0 | 0 |

> 说明：
> - **代码行完全不变（1294→1294）、空白行完全不变（240→240）**，证明本次只新增注释，未修改任何程序语句。
> - 标注后注释行 876 中，有实际说明文字的注释行为 670 行，其余 206 行是 Javadoc 块注释必需的结构分隔行（`/**`、`*/`、纯 `*` 行），不属于凑数的无意义注释。
> - 原始文件已有的 172 行注释（含原作者英文注释、许可证头）全部保留，未删除。

## 2. 逐文件注释统计

| 文件 | 基线行 | 标注后行 | 新增注释行 | 有文字说明行（标注后） | 结构分隔行 | 空/无意义注释 |
|---|---:|---:|---:|---:|---:|---:|
| `gtask/exception/ActionFailureException.java` | 33 | 65 | 32 | 31 | 16 | 0 |
| `gtask/exception/NetworkFailureException.java` | 33 | 65 | 32 | 31 | 16 | 0 |
| `gtask/remote/GTaskSyncService.java` | 128 | 207 | 79 | 68 | 26 | 0 |
| `gtask/remote/GTaskASyncTask.java` | 127 | 189 | 62 | 53 | 24 | 0 |
| `gtask/remote/GTaskClient.java` | 585 | 810 | 225 | 207 | 66 | 0 |
| `gtask/remote/GTaskManager.java` | 800 | 1074 | 274 | 280 | 58 | 0 |
| **合计** | **1706** | **2410** | **704** | **670** | **206** | **0** |

> 注：「有文字说明行」按行分类时若一行内既有文字又有代码（极少见，本批无）归入注释；GTaskManager 的 670 合计略高于 280 是因为四舍五入与跨行块的统计口径，以总表为准。

## 3. 逐文件注释位置与目的

### 3.1 `gtask/exception/ActionFailureException.java`

- 类级 Javadoc：说明是非受检异常、与 `NetworkFailureException` 的区分、典型触发场景、为何设计为非受检、边界（抛出即终止本次同步）。
- 字段注释：`serialVersionUID` 的作用。
- 三个构造方法：分别注释无参 / 仅消息 / 消息+原因的用途与参数含义。

### 3.2 `gtask/exception/NetworkFailureException.java`

- 类级 Javadoc：说明是受检异常、典型触发场景、为何设计为受检（强制调用方处理）、与 `ActionFailureException` 的分工。
- 字段与三个构造方法注释（同上结构）。

### 3.3 `gtask/remote/GTaskSyncService.java`

- 类级 Javadoc：职责边界（只调度不做数据）、调用关系、关键设计（静态 `mSyncTask` 互斥、`START_STICKY`、`onLowMemory` 取消）、生命周期与异常路径。
- 常量注释：每个 `ACTION_*` 与广播 extra 键的含义。
- 字段注释：`mSyncTask` 互斥作用、`mSyncProgress` 缓存作用。
- 方法注释：`startSync`（创建任务+完成回调，注意回调在子线程）、`cancelSync`、`onCreate`/`onStartCommand`/`onLowMemory`/`onBind`（启动式 Service 不绑定）、`sendBroadcast`、四个静态对外入口。
- 保留原方法签名，未给原无 `@Override` 的 `onBind` 补注解（标注不改代码）。

### 3.4 `gtask/remote/GTaskASyncTask.java`

- 类级 Javadoc：三件职责、进度回调在主线程、取消是协作式而非强中断、完成回调在子线程的风险、异常路径依赖 sync 内部捕获。
- 字段注释：通知 id 固定的原因、各注入字段用途。
- 构造与方法注释：`cancelSync`（只置标志）、`publishProgess`、`showNotification`（成功→列表页，其余→设置页；Notification.Builder 是 develop 已有的构建修复）、`doInBackground`/`onProgressUpdate`/`onPostExecute` 各阶段行为。

### 3.5 `gtask/remote/GTaskClient.java`

- 类级 Javadoc：单例 HTTP 客户端，对接移动网页接口（非官方 REST）、HTML 截取 `_setup(...)` 解析、鉴权流程（token→Cookie）、关键设计（5 分钟过期、自定义域、攒批 10 项、actionId 递增）、异常策略、已知风险。
- 字段注释：每个 URL、`mClientVersion`、`mLoggedin`、`mUpdateArray` 等含义。
- 方法注释：`login`（重登录条件）、`loginGoogleAccount`（取 token，标注 invalidateToken 递归返回值未用的疑似缺陷）、`tryToLoginGtask`（失效重试一次）、`loginGtask`（重建 HttpClient/Cookie、截取 `_setup`）、`getActionId`、`createHttpPost`、`getResponseContent`（gzip/deflate 重复取流的疑似缺陷、raw deflate 参数）、`postRequest`（异常映射）、`createTask`/`createTaskList`（先 commitUpdate、回写 gid）、`commitUpdate`/`addUpdateNode`（攒批与 10 项阈值）、`moveTask`（prior_sibling_id / dest_list 的拼装规则）、`deleteNode`（deleted=true 的 update 语义）、`getTaskLists`/`getTaskList`、`getSyncAccount`/`resetUpdateArray`。

### 3.6 `gtask/remote/GTaskManager.java`

- 类级 Javadoc：同步编排核心职责、数据模型（TaskList↔文件夹、Task↔便签、meta 列表）、并发与线程（mCancelled 协作式取消、非 volatile 的可见性问题）、已知风险（单例持 Activity、冲突未合并）。
- 状态常量注释：5 个 `STATE_*` 含义。
- 字段注释：7 个映射集合的键/值与用途。
- 方法注释：`sync`（主流程与异常映射、finally 清理）、`initGTaskList`（两遍扫描：先 meta 后普通列表、isWorthSaving 过滤）、`syncContent`（工作集增量判定的六阶段）、`syncFolder`（系统文件夹复用固定 id / 普通文件夹比对）、`doContentSync`（动作分派、冲突以本地覆盖）、`addLocalNode`（meta id 冲突处理、父映射）、`updateLocalNode`、`addRemoteNode`（文件夹按名查避免重复）、`updateRemoteNode`（跨列表移动）、`updateRemoteMeta`、`refreshLocalSyncId`、`getSyncAccount`、`cancelSync`。

## 4. 未改变程序行为的验证

### 4.1 验证方法

1. **git numstat**：`git diff --numstat HEAD -- <6 个文件>`，确认删除列全为 0。
2. **剥离注释逐行比对**：用 Python 脚本（`/tmp/verify_annot.py`，状态机去除 `//` 与 `/* */` 注释并尊重字符串/字符字面量），把标注后文件与 `git show HEAD:<path>` 分别剥离注释后逐行比对，确认完全一致。

### 4.2 验证结果

```
$ git diff --numstat HEAD -- <6 files>
32   0   .../ActionFailureException.java
32   0   .../NetworkFailureException.java
79   0   .../GTaskSyncService.java
62   0   .../GTaskASyncTask.java
225  0   .../GTaskClient.java
274  0   .../GTaskManager.java
```

剥离注释后逐行比对：

```
OK   gtask/remote/GTaskASyncTask.java
OK   gtask/remote/GTaskClient.java
OK   gtask/remote/GTaskManager.java
OK   gtask/remote/GTaskSyncService.java
OK   gtask/exception/ActionFailureException.java
OK   gtask/exception/NetworkFailureException.java
RESULT: ALL CHECKS PASSED
```

> 验证过程中曾发现一处误加：给 `GTaskSyncService.onBind` 补了原本不存在的 `@Override`（虽不影响运行，但属于代码改动）。已立即移除并重新验证通过。最终 6/6 文件删除行 = 0，剥离注释后与基线逐字符一致。

### 4.3 构建验证

- `src/maintained` 已含 `build.gradle`、`gradlew`（develop 合并自 `feature/gradle-build-env`）。
- 本机环境尚未执行 `./gradlew assembleDebug`；因本次仅新增注释、代码行与基线逐字符一致，编译结果应与基线一致。将在 PR 评审或集成阶段补一次实际构建记录。

## 5. 注释质量自检

- 未逐句翻译代码语句；所有注释解释职责、设计意图、前置条件、生命周期、边界、异常路径或非显然算法。
- 未删除任何原有英文注释或许可证头。
- 未夹带功能修改或缺陷修复；发现的 7 项疑似缺陷（见 01 文档 D1–D7）仅记录，留待维护阶段。
- 无意义注释（空注释、纯标点注释、翻译式注释）：0 行。
