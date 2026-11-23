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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;


/**
 * 闹钟提醒的“重建”广播接收器：在系统广播（开机、应用升级等）后，
 * 把数据库中所有尚未到点的提醒重新注册到 AlarmManager。
 *
 * <p>设计原因：AlarmManager 的闹钟不持久化，关机或重启后会丢失，
 * 因此必须由 BOOT_COMPLETED（见 AndroidManifest）触发本接收器做一次全量重建。
 *
 * <p>边界条件：只查询 alert_date 大于当前时间的普通便签（TYPE_NOTE），
 * 已过期的提醒不会补发；本方法也不修改数据库，不做“已提醒”标记的清理。
 *
 * <p>职责与生命周期：本接收器只在系统广播到达时短暂存活，onReceive 返回即结束；
 * 因此它必须自己完成查询并立即注册闹钟，不能依赖任何成员状态。
 *
 * <p>异常路径：query 返回 null（Provider 不可用）时整体跳过，不注册任何提醒；
 * 未对 AlarmManager 做空判断，若系统服务异常会直接抛 NullPointerException；
 * PendingIntent 使用 flags=0，在新版本系统上可能因缺少可变性标记而告警。
 * 以上均为既有实现，只记录不修改。
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    /** 查询 note 表：id 用于拼 data Uri，alert_date 作为触发时间。 */
    private static final String [] PROJECTION = new String [] {
        NoteColumns.ID,
        NoteColumns.ALERTED_DATE
    };

    /** 与投影顺序一一对应的列下标。 */
    private static final int COLUMN_ID                = 0;
    private static final int COLUMN_ALERTED_DATE      = 1;

    /**
     * 重建全部未来提醒。onReceive 运行在主线程且有时间限制，
     * 这里直接做同步数据库查询，数据量大时会拉长启动耗时。
     *
     * <p>PendingIntent 的身份由 (requestCode, Intent 的 action/data/category...) 决定：
     * 每条便签的 data Uri 都不同，所以即使 requestCode 都是 0，也不会互相覆盖。
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        long currentDate = System.currentTimeMillis();
        Cursor c = context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.ALERTED_DATE + ">? AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE,
                new String[] { String.valueOf(currentDate) },
                null);

        if (c != null) {
            if (c.moveToFirst()) {
                do {
                    long alertDate = c.getLong(COLUMN_ALERTED_DATE);
                    // 把便签 id 编进 data，下游 AlarmReceiver/AlarmAlertActivity 依赖这个 Uri 取 id。
                    Intent sender = new Intent(context, AlarmReceiver.class);
                    sender.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, c.getLong(COLUMN_ID)));
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, sender, 0);
                    AlarmManager alermManager = (AlarmManager) context
                            .getSystemService(Context.ALARM_SERVICE);
                    // RTC_WAKEUP：按绝对时间触发并唤醒设备，保证息屏时也能准时提醒。
                    alermManager.set(AlarmManager.RTC_WAKEUP, alertDate, pendingIntent);
                } while (c.moveToNext());
            }
            c.close();
        }
    }
}
