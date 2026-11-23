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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 单个便签提醒到点时的广播接收器，只做一件事：把提醒转成界面。
 *
 * <p>为什么需要这一层：AlarmManager 到点后只能发广播，不能直接启动界面；
 * 而 BroadcastReceiver 里启动 Activity 必须加 FLAG_ACTIVITY_NEW_TASK，
 * 因为此时没有宿主任务栈。
 *
 * <p>数据依赖：Intent 的 data 仍是 AlarmInitReceiver 注册时写入的
 * content://.../note/&lt;id&gt;，改类但不改 data，AlarmAlertActivity 才能解析出便签 id。
 *
 * <p>职责边界：本类不校验便签是否存在、不判断是否需要响铃，也不修改任何数据；
 * 只做“广播 → 界面”的组件转发。屏幕是否点亮、是否锁屏等前置条件的处理全部在
 * AlarmAlertActivity 中完成。
 *
 * <p>生命周期与异常路径：onReceive 返回后接收器即结束，不能在这里做耗时操作；
 * 若系统禁止后台启动界面（或目标 Activity 被禁用），startActivity 会抛异常，
 * 本类不做捕获，由系统记录崩溃——这是既有实现，标注阶段不修改。
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // 复用同一个 Intent，仅替换目标组件：data（便签 Uri）和 flags 会被保留。
        intent.setClass(context, AlarmAlertActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
