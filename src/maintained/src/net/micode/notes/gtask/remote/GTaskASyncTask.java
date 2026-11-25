
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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import net.micode.notes.R;
import net.micode.notes.ui.NotesListActivity;
import net.micode.notes.ui.NotesPreferenceActivity;


/**
 * 把 {@link GTaskManager#sync} 包装到 {@link AsyncTask} 中执行的桥接类。
 *
 * <p>职责边界：本类只负责三件事——</p>
 * <ol>
 *   <li>在后台线程（{@code doInBackground}）调用同步编排；</li>
 *   <li>把同步进度以通知栏 Notification + Service 广播的形式转发给界面（{@code onProgressUpdate}，主线程）；</li>
 *   <li>同步结束后按结果码显示终态通知，并回调 {@link OnCompleteListener}（{@code onPostExecute}，主线程）。</li>
 * </ol>
 *
 * <p>它不做任何数据决策：同步动作、冲突处理、数据库读写都在 {@code GTaskManager} 内。</p>
 *
 * <p>关键设计与不显然之处：</p>
 * <ul>
 *   <li>进度发布走 {@link #publishProgess(String)} → {@code publishProgress} → {@code onProgressUpdate}，
 *       因此进度回调一定在主线程，可以安全更新通知与广播；</li>
 *   <li>{@link #cancelSync()} 并不调用 {@code AsyncTask.cancel(true)}，而是置位
 *       {@code GTaskManager.mCancelled}，让同步循环在各检查点自行退出——
 *       这样能保证数据库与云端的状态一致性，避免强中断留下半同步数据；</li>
 *   <li>{@code onPostExecute} 中的完成回调被放到<b>新建子线程</b>执行（而非主线程），
 *       回调内会调用 {@code stopSelf()} 与 {@code sendBroadcast}，需注意线程安全。</li>
 * </ul>
 *
 * <p>异常路径：若 {@code sync} 抛出未捕获异常，{@code onPostExecute} 不会被回调，
 *     Service 不会自行停止——依赖 {@code GTaskManager.sync} 内部已捕获所有异常并返回结果码。</p>
 */
public class GTaskASyncTask extends AsyncTask<Void, String, Integer> {

    /** 同步通知的固定 ID，多次 notify 会覆盖同一条通知而不是堆叠。 */
    private static int GTASK_SYNC_NOTIFICATION_ID = 5234235;

    /**
     * 同步完成回调接口。由 {@link GTaskSyncService} 传入，用于在结束时清理任务引用、
     * 发送结束广播并停止 Service。
     */
    public interface OnCompleteListener {
        void onComplete();
    }

    /** 传入的 Context；实际运行时是 {@link GTaskSyncService} 实例，用于发广播与通知。 */
    private Context mContext;

    /** 通知管理器，用于在状态栏显示同步进度与结果。 */
    private NotificationManager mNotifiManager;

    /** 同步编排单例，真正的同步逻辑在此执行。 */
    private GTaskManager mTaskManager;

    /** 完成回调，由构造时注入，结束时在子线程触发。 */
    private OnCompleteListener mOnCompleteListener;

    /**
     * 构造时注入 Context 与完成回调，并拿到通知管理器与同步管理器单例。
     *
     * @param context  运行上下文（GTaskSyncService）
     * @param listener 同步完成回调，可为 null
     */
    public GTaskASyncTask(Context context, OnCompleteListener listener) {
        mContext = context;
        mOnCompleteListener = listener;
        mNotifiManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        mTaskManager = GTaskManager.getInstance();
    }

    /**
     * 请求取消同步：只置位取消标志，不强制中断线程，保证同步状态一致性。
     */
    public void cancelSync() {
        mTaskManager.cancelSync();
    }

    /**
     * 发布一条进度消息；AsyncTask 会把它投递到主线程的 {@code onProgressUpdate}。
     *
     * @param message 进度文案
     */
    public void publishProgess(String message) {
        publishProgress(new String[] {
            message
        });
    }

    /**
     * 显示一条同步通知。非成功通知点击后跳转到设置页（可取消/重试），
     * 成功通知点击后跳转到便签列表。
     *
     * @param tickerId 状态栏 ticker 文案的字符串资源 id
     * @param content  通知正文内容
     */
    private void showNotification(int tickerId, String content) {
        PendingIntent pendingIntent;
        // 成功 → 列表页；其余（同步中/失败/取消）→ 设置页
        if (tickerId != R.string.ticker_success) {
            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
                    NotesPreferenceActivity.class), 0);

        } else {
            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
                    NotesListActivity.class), 0);
        }
        // 使用 Notification.Builder 构建（已替换原项目中被新 SDK 移除的 setLatestEventInfo 写法）
        Notification notification = new Notification.Builder(mContext)
                .setSmallIcon(R.drawable.notification)
                .setTicker(mContext.getString(tickerId))
                .setWhen(System.currentTimeMillis())
                .setContentTitle(mContext.getString(R.string.app_name))
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .setAutoCancel(true)
                .build();
        mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, notification);
    }

    @Override
    protected Integer doInBackground(Void... unused) {
        // 先发布“正在登录账号”的初始进度，再调用同步主流程；返回值即结果码
        publishProgess(mContext.getString(R.string.sync_progress_login, NotesPreferenceActivity
                .getSyncAccountName(mContext)));
        return mTaskManager.sync(mContext, this);
    }

    @Override
    protected void onProgressUpdate(String... progress) {
        // 主线程：更新通知栏进度，并在 Context 是 Service 时转发广播给设置页
        showNotification(R.string.ticker_syncing, progress[0]);
        if (mContext instanceof GTaskSyncService) {
            ((GTaskSyncService) mContext).sendBroadcast(progress[0]);
        }
    }

    @Override
    protected void onPostExecute(Integer result) {
        // 主线程：按结果码显示终态通知；成功时顺便记录上次同步时间
        if (result == GTaskManager.STATE_SUCCESS) {
            showNotification(R.string.ticker_success, mContext.getString(
                    R.string.success_sync_account, mTaskManager.getSyncAccount()));
            NotesPreferenceActivity.setLastSyncTime(mContext, System.currentTimeMillis());
        } else if (result == GTaskManager.STATE_NETWORK_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_network));
        } else if (result == GTaskManager.STATE_INTERNAL_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_internal));
        } else if (result == GTaskManager.STATE_SYNC_CANCELLED) {
            showNotification(R.string.ticker_cancel, mContext
                    .getString(R.string.error_sync_cancelled));
        }
        // 完成回调放到新建子线程执行（不在主线程），避免阻塞 UI；回调内会 stopSelf
        if (mOnCompleteListener != null) {
            new Thread(new Runnable() {

                public void run() {
                    mOnCompleteListener.onComplete();
                }
            }).start();
        }
    }
}
