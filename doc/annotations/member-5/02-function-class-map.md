# 成员 5 功能—类—方法映射（02）

> 范围：`gtask/remote/*.java`、`gtask/exception/*.java`
> 基线：develop HEAD `e5b9a34`

## 1. 功能到类的映射总览

| 功能 | 涉及类 | 入口/触发 |
|---|---|---|
| 启动一次云端同步 | `GTaskSyncService` | 设置页点击“立即同步” |
| 取消正在进行的同步 | `GTaskSyncService`、`GTaskASyncTask`、`GTaskManager` | 用户取消 / 系统低内存 |
| 同步进度通知与广播 | `GTaskASyncTask`、`GTaskSyncService` | 同步过程中 publishProgress |
| 登录 Google Tasks（鉴权） | `GTaskClient` | 每次 `sync` 开始时 |
| 拉取云端任务列表/任务 | `GTaskClient` | `initGTaskList` / `refreshLocalSyncId` |
| 同步流程编排（比对与分发） | `GTaskManager` | `sync` 主入口 |
| 节点新增（本地↔云端） | `GTaskManager`、`GTaskClient` | `syncContent` 判定为 ADD |
| 节点更新（本地↔云端） | `GTaskManager`、`GTaskClient` | `syncContent` 判定为 UPDATE |
| 节点删除（本地↔云端） | `GTaskManager`、`GTaskClient` | `syncContent` 判定为 DEL / 回收站 |
| 便签跨文件夹移动 | `GTaskManager`、`GTaskClient` | `updateRemoteNode` 检测父列表变化 |
| meta 元数据维护 | `GTaskManager`、`GTaskClient` | 增改便签时同步写入 |
| 刷新本地 sync_id | `GTaskManager` | `syncContent` 末尾 |
| 同步异常分类与传递 | `ActionFailureException`、`NetworkFailureException` | 全链路 |

## 2. 详细映射

### 2.1 启动一次云端同步

| 项 | 内容 |
|---|---|
| 功能描述 | 用户在设置页触发，把本地便签与 Google Tasks 云端做一次双向同步 |
| 涉及包/类 | `gtask/remote/GTaskSyncService` |
| 入口方法 | `GTaskSyncService.startSync(Activity)`（静态）→ `startService` → `onStartCommand` → 私有 `startSync()` |
| 关键方法 | `static startSync(Activity)`、`onStartCommand(Intent,int,int)`、`private startSync()` |
| 触发与数据 | 设置页 `NotesPreferenceActivity` 调用；先把 Activity 注入 `GTaskManager` 用于取 token |
| 备注 | 静态 `mSyncTask` 保证互斥，已有同步时不重复创建 |

### 2.2 取消同步

| 项 | 内容 |
|---|---|
| 功能描述 | 用户取消或系统低内存时终止正在进行的同步，保证状态一致 |
| 涉及包/类 | `GTaskSyncService`、`GTaskASyncTask`、`GTaskManager` |
| 入口方法 | `GTaskSyncService.cancelSync(Context)`（静态）→ Service `cancelSync()` → `GTaskASyncTask.cancelSync()` → `GTaskManager.cancelSync()` |
| 关键方法 | `static cancelSync(Context)`、`onLowMemory()`、`GTaskASyncTask.cancelSync()`、`GTaskManager.cancelSync()` |
| 触发与数据 | 用户点取消按钮 / `onLowMemory` 回调；置位 `GTaskManager.mCancelled` |
| 备注 | 协作式取消，不强制中断线程；同步在各检查点退出，最后返回 `STATE_SYNC_CANCELLED` |

### 2.3 同步进度通知与广播

| 项 | 内容 |
|---|---|
| 功能描述 | 同步过程中把进度以通知栏通知 + 自定义广播的形式告知界面 |
| 涉及包/类 | `GTaskASyncTask`、`GTaskSyncService` |
| 入口方法 | `GTaskManager` 调 `GTaskASyncTask.publishProgess(String)` → `onProgressUpdate` |
| 关键方法 | `GTaskASyncTask.publishProgess(String)`、`onProgressUpdate(String...)`、`showNotification(int,String)`、`GTaskSyncService.sendBroadcast(String)` |
| 触发与数据 | `onProgressUpdate` 在主线程执行；广播 Action 为 `net.micode.notes.gtask.remote.gtask_sync_service` |
| 备注 | 终态通知（成功/失败/取消）在 `onPostExecute` 显示；通知 id 固定为 `5234235` 以覆盖而非堆叠 |

### 2.4 登录 Google Tasks（鉴权）

| 项 | 内容 |
|---|---|
| 功能描述 | 取 Google 账号 auth token，带 token 登录获取 Cookie，解析客户端版本号 |
| 涉及包/类 | `gtask/remote/GTaskClient` |
| 入口方法 | `GTaskManager.sync` → `client.login(Activity)` |
| 关键方法 | `login(Activity)`、`loginGoogleAccount(Activity,boolean)`、`tryToLoginGtask(Activity,String)`、`loginGtask(String)` |
| 触发与数据 | 每次同步开始（5 分钟内已登录则跳过）；账号来自 `AccountManager` 的 `com.google` 账号 |
| 备注 | 非 gmail 账号走自定义域 URL；token 失效时作废重取重试一次；Cookie 存于 `DefaultHttpClient` 的 CookieStore |

### 2.5 拉取云端任务列表/任务

| 项 | 内容 |
|---|---|
| 功能描述 | 从云端拉取账号下的任务列表及每个列表下的任务，构建内存模型 |
| 涉及包/类 | `GTaskClient`、`GTaskManager` |
| 入口方法 | `GTaskManager.initGTaskList()` → `client.getTaskLists()` / `client.getTaskList(gid)` |
| 关键方法 | `GTaskClient.getTaskLists()`、`getTaskList(String)`、`GTaskManager.initGTaskList()` |
| 触发与数据 | 同步开始时一次，`refreshLocalSyncId` 末尾再拉一次；返回 JSON 数组 |
| 备注 | `getTaskLists` 从 HTML `_setup(...)` 截取解析；`getTaskList` 用 GETALL 动作 POST |

### 2.6 同步流程编排

| 项 | 内容 |
|---|---|
| 功能描述 | 逐节点比较本地与云端，判定每个节点的同步动作并分派执行 |
| 涉及包/类 | `GTaskManager` |
| 入口方法 | `GTaskManager.sync(Context,GTaskASyncTask)` |
| 关键方法 | `sync(...)`、`initGTaskList()`、`syncContent()`、`syncFolder()`、`doContentSync(int,Node,Cursor)` |
| 触发与数据 | 由 `GTaskASyncTask.doInBackground` 调用；返回 STATE_* 结果码 |
| 备注 | `mGTaskHashMap` 作为工作集，匹配即移除，剩余即云端新增；异常在最外层映射为结果码，finally 清理缓存 |

### 2.7 节点新增（本地↔云端）

| 项 | 内容 |
|---|---|
| 功能描述 | 云端有本地无 → 新增到本地；本地有云端无 → 推送到云端 |
| 涉及包/类 | `GTaskManager`、`GTaskClient` |
| 入口方法 | `doContentSync` 分派 |
| 关键方法 | `GTaskManager.addLocalNode(Node)`、`addRemoteNode(Node,Cursor)`；`GTaskClient.createTask(Task)`、`createTaskList(TaskList)` |
| 触发与数据 | `syncContent` 判定 `SYNC_ACTION_ADD_LOCAL` / `SYNC_ACTION_ADD_REMOTE` |
| 备注 | `addLocalNode` 会处理 meta 中保存的本地 id 冲突（已占用则移除让数据库重分配）；`addRemoteNode` 文件夹先按名查云端避免重复创建 |

### 2.8 节点更新（本地↔云端）

| 项 | 内容 |
|---|---|
| 功能描述 | 按修改时间判定更新方向，用较新一方覆盖另一方 |
| 涉及包/类 | `GTaskManager`、`GTaskClient` |
| 入口方法 | `doContentSync` 分派 |
| 关键方法 | `GTaskManager.updateLocalNode(Node,Cursor)`、`updateRemoteNode(Node,Cursor)`；`GTaskClient.addUpdateNode(Node)`、`commitUpdate()` |
| 触发与数据 | `node.getSyncAction(c)` 返回 `SYNC_ACTION_UPDATE_LOCAL` / `UPDATE_REMOTE` / `UPDATE_CONFLICT` / `NONE` |
| 备注 | 更新走攒批 `mUpdateArray`（满 10 项或显式 commit 才提交）；冲突当前以本地覆盖远端 |

### 2.9 节点删除（本地↔云端）

| 项 | 内容 |
|---|---|
| 功能描述 | 同步已删除项：本地删→删云端；云端删→删本地；并清理关联 meta |
| 涉及包/类 | `GTaskManager`、`GTaskClient` |
| 入口方法 | `doContentSync` 的 `SYNC_ACTION_DEL_LOCAL` / `SYNC_ACTION_DEL_REMOTE` 分支；`syncContent` 阶段 1 处理回收站 |
| 关键方法 | `GTaskClient.deleteNode(Node)`；`GTaskManager` 内联逻辑；`DataUtils.batchDeleteNotes` |
| 触发与数据 | 本地便签在回收站 / 本地 gtask_id 非空但云端无对应节点 |
| 备注 | 删除会连带删除关联 meta；回收站项先记入 `mLocalDeleteIdMap`，最后批量物理删除 |

### 2.10 便签跨文件夹移动

| 项 | 内容 |
|---|---|
| 功能描述 | 便签更换父文件夹时，在云端执行 move 动作 |
| 涉及包/类 | `GTaskManager`、`GTaskClient` |
| 入口方法 | `GTaskManager.updateRemoteNode` 内检测父列表变化 |
| 关键方法 | `GTaskManager.updateRemoteNode(Node,Cursor)`；`GTaskClient.moveTask(Task,TaskList,TaskList)` |
| 触发与数据 | `updateRemoteNode` 时 `preParentList != curParentList` |
| 备注 | 先在内存模型移动父子关系，再调用云端 move；同列表内移动且非首项才带 `prior_sibling_id` |

### 2.11 meta 元数据维护

| 项 | 内容 |
|---|---|
| 功能描述 | 把本地便签的原始内容写入云端特殊 meta 列表，用于还原与冲突判定 |
| 涉及包/类 | `GTaskManager`、`GTaskClient` |
| 入口方法 | 每次新增/更新便签后调用 |
| 关键方法 | `GTaskManager.updateRemoteMeta(String,SqlNote)`；`GTaskManager.initGTaskList` 加载 meta；`GTaskClient.createTask`/`addUpdateNode` |
| 触发与数据 | meta 已存在则更新，不存在则在 `mMetaList` 下新建 |
| 备注 | meta 以“被关联任务的 gid”为键存于 `mMetaHashMap`；只对便签（非文件夹）生效 |

### 2.12 刷新本地 sync_id

| 项 | 内容 |
|---|---|
| 功能描述 | 同步末尾把云端最新 `lastModified` 写入本地 `SYNC_ID` 列，作为下次比较基准 |
| 涉及包/类 | `GTaskManager` |
| 入口方法 | `syncContent` 末尾 |
| 关键方法 | `GTaskManager.refreshLocalSyncId()` |
| 触发与数据 | 重新 `initGTaskList` 后遍历本地便签匹配云端节点 |
| 备注 | 若本地有 gid 但云端找不到对应节点，抛 `ActionFailureException` 视为同步不一致 |

### 2.13 同步异常分类与传递

| 项 | 内容 |
|---|---|
| 功能描述 | 区分网络传输失败与业务/逻辑失败，分别映射为不同同步结果码 |
| 涉及包/类 | `gtask/exception/ActionFailureException`、`gtask/exception/NetworkFailureException` |
| 入口方法 | 全链路抛出 |
| 关键方法/构造 | 两者均提供无参、`(String)`、`(String,Throwable)` 三个构造 |
| 触发与数据 | `GTaskClient` 抛两类异常；`GTaskManager.sync` 最外层 catch |
| 备注 | `NetworkFailureException` 受检→`STATE_NETWORK_ERROR`；`ActionFailureException` 非受检→`STATE_INTERNAL_ERROR` |
