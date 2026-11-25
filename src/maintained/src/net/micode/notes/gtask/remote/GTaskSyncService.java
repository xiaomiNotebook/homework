/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

/**
 * GTask 云端同步的调度入口 Service。
 *
 * <p>职责边界：本类只负责“启动/取消一次同步”和“把同步进度广播给界面”，
 * 不直接执行同步逻辑——真正的网络与数据操作在 {@link GTaskASyncTask}（后台线程）
 * 和 {@link GTaskManager}（同步编排）中完成。</p>
 *
 * <p>调用关系：</p>
 * <ul>
 *   <li>外部（设置页 {@code NotesPreferenceActivity}）通过静态方法
 *       {@link #startSync(Activity)} / {@link #cancelSync(Context)} 启动本 Service；</li>
 *   <li>本 Service 在 {@link #onStartCommand} 中按 Intent extra 的动作类型分派；</li>
 *   <li>同步过程中通过 {@link #sendBroadcast(String)} 发送自定义广播，
 *       界面据此刷新“同步中/进度文案”状态；</li>
 *   <li>同步结束时 {@code onComplete} 回调清理任务引用并 {@code stopSelf()}。</li>
 * </ul>
 *
 * <p>关键设计：</p>
 * <ul>
 *   <li>用静态字段 {@code mSyncTask} 保证全局同一时刻只有一个同步在执行（互斥）；</li>
 *   <li>返回 {@code START_STICKY}：进程被系统杀死后，Service 会被重建，但重建不会自动
 *       恢复未完成的同步（需用户重新触发）；</li>
 *   <li>{@link #onLowMemory} 主动取消同步，避免在内存压力下继续占用网络与数据库。</li>
 * </ul>
 *
 * <p>生命周期与异常路径：</p>
 * <ul>
 *   <li>{@link #onCreate} 只把 {@code mSyncTask} 置空，确保冷启动后不残留旧引用；</li>
 *   <li>{@link #onBind} 返回 null：本 Service 是“启动式”而非“绑定式”，外部不通过
 *       IBinder 直接调用；</li>
 *   <li>若同步中 Service 被销毁，{@code GTaskASyncTask} 仍可能持有 Context 引用，
 *       存在回调访问已销毁 Service 的风险（维护阶段需关注）。</li>
 * </ul>
 */
public class GTaskSyncService extends Service {
    /** Intent extra 的键：携带本次启动要执行的动作类型（取值见 ACTION_* 常量）。 */
    public final static String ACTION_STRING_NAME = "sync_action_type";

    /** 动作类型：开始一次同步。 */
    public final static int ACTION_START_SYNC = 0;

    /** 动作类型：取消正在进行的同步。 */
    public final static int ACTION_CANCEL_SYNC = 1;

    /** 动作类型：非法/缺省值，用于 switch 的兜底分支。 */
    public final static int ACTION_INVALID = 2;

    /** 同步进度广播的 Action 名；设置页通过它注册 BroadcastReceiver 接收状态。 */
    public final static String GTASK_SERVICE_BROADCAST_NAME = "net.micode.notes.gtask.remote.gtask_sync_service";

    /** 广播 extra 键：当前是否正在同步（boolean）。 */
    public final static String GTASK_SERVICE_BROADCAST_IS_SYNCING = "isSyncing";

    /** 广播 extra 键：当前同步进度的可读文案（String）。 */
    public final static String GTASK_SERVICE_BROADCAST_PROGRESS_MSG = "progressMsg";

    /**
     * 当前正在执行的同步任务；静态字段实现全局互斥（同一进程只允许一个同步）。
     * 同步完成或取消后置回 null。
     */
    private static GTaskASyncTask mSyncTask = null;

    /** 最近一次同步进度文案，供 {@link #getProgressString()} 给界面查询。 */
    private static String mSyncProgress = "";

    /**
     * 启动一次同步：仅当当前没有同步任务时才创建并执行 {@link GTaskASyncTask}。
     * 完成回调里把任务引用置空、发一次空进度广播并停掉 Service。
     */
    private void startSync() {
        if (mSyncTask == null) {
            mSyncTask = new GTaskASyncTask(this, new GTaskASyncTask.OnCompleteListener() {
                public void onComplete() {
                    // 注意：此回调在 GTaskASyncTask 另起的子线程中执行，并非主线程
                    mSyncTask = null;
                    sendBroadcast("");
                    stopSelf();
                }
            });
            // 先广播一次“同步中”，让界面立即进入同步态，再异步执行任务
            sendBroadcast("");
            mSyncTask.execute();
        }
    }

    /**
     * 取消当前同步：委托给任务的 {@code cancelSync}，后者会置位
     * {@code GTaskManager.mCancelled}，同步循环在各检查点退出。
     */
    private void cancelSync() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    @Override
    public void onCreate() {
        // 冷启动时清理可能残留的静态任务引用，避免误判“正在同步”
        mSyncTask = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 从 Intent extra 读取动作类型并分派；取不到 extra 时走系统默认处理
        Bundle bundle = intent.getExtras();
        if (bundle != null && bundle.containsKey(ACTION_STRING_NAME)) {
            switch (bundle.getInt(ACTION_STRING_NAME, ACTION_INVALID)) {
                case ACTION_START_SYNC:
                    startSync();
                    break;
                case ACTION_CANCEL_SYNC:
                    cancelSync();
                    break;
                default:
                    break;
            }
            // START_STICKY：被杀后重建，但不会自动续跑未完成同步
            return START_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onLowMemory() {
        // 系统内存紧张时主动取消同步，释放网络与数据库占用
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    public IBinder onBind(Intent intent) {
        // 启动式 Service，不提供绑定（原方法未加 @Override，标注阶段保持原样不改代码）
        return null;
    }

    /**
     * 发送同步进度广播：更新缓存的进度文案，并把“是否同步中”与文案一起发给界面。
     *
     * @param msg 进度文案；同步结束时传空串
     */
    public void sendBroadcast(String msg) {
        mSyncProgress = msg;
        Intent intent = new Intent(GTASK_SERVICE_BROADCAST_NAME);
        intent.putExtra(GTASK_SERVICE_BROADCAST_IS_SYNCING, mSyncTask != null);
        intent.putExtra(GTASK_SERVICE_BROADCAST_PROGRESS_MSG, msg);
        sendBroadcast(intent);
    }

    /**
     * 对外静态入口：设置页点击“立即同步”时调用。
     * 先把 Activity 交给 {@code GTaskManager}（用于取 auth token），再启动 Service。
     *
     * @param activity 触发同步的界面，用于获取账号与 token
     */
    public static void startSync(Activity activity) {
        GTaskManager.getInstance().setActivityContext(activity);
        Intent intent = new Intent(activity, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_START_SYNC);
        activity.startService(intent);
    }

    /**
     * 对外静态入口：取消正在进行的同步。
     *
     * @param context 任意可用上下文（不一定是 Activity）
     */
    public static void cancelSync(Context context) {
        Intent intent = new Intent(context, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_CANCEL_SYNC);
        context.startService(intent);
    }

    /** 查询当前是否有同步任务在执行（界面据此决定按钮文案）。 */
    public static boolean isSyncing() {
        return mSyncTask != null;
    }

    /** 返回最近一次缓存的同步进度文案。 */
    public static String getProgressString() {
        return mSyncProgress;
    }
}
