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

package net.micode.notes.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;

import java.io.IOException;


/**
 * 便签闹钟到点后的提醒界面：锁屏/亮屏弹出对话框并循环播放闹钟铃声。
 *
 * <p>职责边界：本类只负责“展示提醒 + 播放铃声 + 关闭提醒”，
 * 不负责修改便签的提醒时间，也不负责取消 AlarmManager 中的闹钟。
 *
 * <p>在流程中的位置：{@link AlarmInitReceiver} 开机或应用启动时把未来的提醒重新注册到
 * AlarmManager；到点后系统广播给 {@link AlarmReceiver}；AlarmReceiver 再启动本 Activity。
 * 因此本类只在“提醒已到点且便签仍有效”时才会被展示。
 *
 * <p>生命周期与前置条件：
 * <ul>
 *   <li>必须携带 data 为 content://.../note/&lt;id&gt; 的 Intent，否则解析 id 失败直接返回；</li>
 *   <li>便签必须仍存在于 note 表且不在回收站，否则立即 finish（防止提醒已被删除的便签）；</li>
 *   <li>对话框关闭时必须停止铃声并结束界面，避免后台持续响铃。</li>
 * </ul>
 *
 * <p>边界条件：摘要为空时不显示正文；超过 {@link #SNIPPET_PREW_MAX_LEN} 的摘要会截断；
 * 屏幕未点亮时不提供“进入便签”按钮，避免从锁屏直接跳到编辑界面。
 */
public class AlarmAlertActivity extends Activity implements OnClickListener, OnDismissListener {
    /** 当前提醒对应的便签 id，来自 Intent 的 data 路径段。 */
    private long mNoteId;
    /** 对话框正文使用的便签摘要。 */
    private String mSnippet;
    /** 摘要最大展示长度，超出部分截断并追加省略提示，避免弹窗内容过长。 */
    private static final int SNIPPET_PREW_MAX_LEN = 60;
    MediaPlayer mPlayer;

    /**
     * 初始化窗口、解析便签、展示对话框并播放铃声。
     *
     * <p>窗口标志的意图：SHOW_WHEN_LOCKED 让提醒在锁屏上直接可见；
     * 若创建时屏幕是黑的，再追加点亮屏幕、保持常亮等标志，保证用户能看到提醒。
     *
     * <p>异常路径：Intent 数据缺失或路径格式不对时，getPathSegments().get(1) 会抛
     * IllegalArgumentException，这里只记堆栈并 return，界面停留在空白页由系统回收。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        final Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        if (!isScreenOn()) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
        }

        Intent intent = getIntent();

        try {
            mNoteId = Long.valueOf(intent.getData().getPathSegments().get(1));
            mSnippet = DataUtils.getSnippetById(this.getContentResolver(), mNoteId);
            mSnippet = mSnippet.length() > SNIPPET_PREW_MAX_LEN ? mSnippet.substring(0,
                    SNIPPET_PREW_MAX_LEN) + getResources().getString(R.string.notelist_string_info)
                    : mSnippet;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return;
        }

        mPlayer = new MediaPlayer();
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, Notes.TYPE_NOTE)) {
            showActionDialog();
            playAlarmSound();
        } else {
            finish();
        }
    }

    /**
     * 当前屏幕是否处于点亮状态，用于决定是否需要主动点亮屏幕、以及是否提供“进入便签”按钮。
     */
    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isScreenOn();
    }

    /**
     * 循环播放系统默认闹钟铃声。
     *
     * <p>流类型选择：先读取“哪些音频流受静音模式影响”的系统设置位图，
     * 若其中包含闹钟流，就把整个位图当作流类型传入；否则回退到 STREAM_ALARM。
     * 这里的取值方式与 AudioManager 的流类型语义并不一致，属于既有实现，
     * 标注阶段按原样保留，问题记录在 Issue 中，不在此修改。
     *
     * <p>异常路径：铃声 Uri 为空、无权限或播放器状态不合法时，各自捕获并打印堆栈，
     * 不向上抛出，保证对话框仍能显示（只是没有声音）。
     */
    private void playAlarmSound() {
        Uri url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);

        int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

        if ((silentModeStreams & (1 << AudioManager.STREAM_ALARM)) != 0) {
            mPlayer.setAudioStreamType(silentModeStreams);
        } else {
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }
        try {
            mPlayer.setDataSource(this, url);
            mPlayer.prepare();
            mPlayer.setLooping(true);
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * 展示提醒对话框。
     *
     * <p>交互设计：确定按钮只关闭提醒；“进入便签”按钮仅在屏幕已点亮时出现，
     * 避免用户在锁屏状态下被直接跳转到编辑界面。
     */
    private void showActionDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.app_name);
        dialog.setMessage(mSnippet);
        dialog.setPositiveButton(R.string.notealert_ok, this);
        if (isScreenOn()) {
            dialog.setNegativeButton(R.string.notealert_enter, this);
        }
        dialog.show().setOnDismissListener(this);
    }

    /**
     * 对话框按钮回调：只有“进入便签”会跳转，确定按钮不做任何事，
     * 后续统一由 onDismiss 停止铃声并结束界面。
     */
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE:
                Intent intent = new Intent(this, NoteEditActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(Intent.EXTRA_UID, mNoteId);
                startActivity(intent);
                break;
            default:
                break;
        }
    }

    /**
     * 对话框消失回调（无论点确定、点进入还是返回键）：停止铃声并关闭界面。
     * 这是保证铃声不会泄漏的唯一收口点。
     */
    public void onDismiss(DialogInterface dialog) {
        stopAlarmSound();
        finish();
    }

    /**
     * 停止并释放播放器。置空引用是为了让重复调用成为幂等操作。
     */
    private void stopAlarmSound() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }
}
