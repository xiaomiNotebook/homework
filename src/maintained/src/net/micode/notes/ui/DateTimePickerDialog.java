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

import java.util.Calendar;

import net.micode.notes.R;
import net.micode.notes.ui.DateTimePicker;
import net.micode.notes.ui.DateTimePicker.OnDateTimeChangedListener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

/**
 * 日期时间选择对话框：内部承载 {@link DateTimePicker}，把用户选中的时间通过回调抛给宿主。
 *
 * <p>职责边界：只负责“选时间”和标题实时刷新，不负责把时间写进数据库；
 * 保存动作由调用方在 {@link OnDateTimeSetListener} 中完成。
 *
 * <p>数据流：DateTimePicker 每次滚动都会回调 listener，这里把年/月/日/时/分写进
 * 内部的 Calendar 并刷新标题；用户点确定时，把 Calendar 的毫秒值回调出去。
 * 因此“标题显示的时间”和“最终返回的时间”始终一致。
 */
public class DateTimePickerDialog extends AlertDialog implements OnClickListener {

    /** 当前选中时间，作为唯一数据源；秒字段在构造时被清零。 */
    private Calendar mDate = Calendar.getInstance();
    /** 当前是否 24 小时制，只影响标题格式。 */
    private boolean mIs24HourView;
    private OnDateTimeSetListener mOnDateTimeSetListener;
    private DateTimePicker mDateTimePicker;

    /**
     * 用户按“确定”后的回调，宿主在此保存提醒时间。
     */
    public interface OnDateTimeSetListener {
        void OnDateTimeSet(AlertDialog dialog, long date);
    }

    /**
     * 构造对话框并完成初始同步。
     *
     * <p>非显然步骤：
     * <ol>
     *   <li>先注册变更监听，再 setCurrentDate，使控件初始值也能回写到 mDate；</li>
     *   <li>秒字段被强制置 0，保证提醒时间精确到分钟，避免“同一分钟设置出不同时间”；</li>
     *   <li>取消按钮传 null 监听，表示只关闭对话框、不回调，因此取消不会误保存。</li>
     * </ol>
     *
     * @param context 宿主上下文
     * @param date    初始时间（毫秒）
     */
    public DateTimePickerDialog(Context context, long date) {
        super(context);
        mDateTimePicker = new DateTimePicker(context);
        setView(mDateTimePicker);
        mDateTimePicker.setOnDateTimeChangedListener(new OnDateTimeChangedListener() {
            public void onDateTimeChanged(DateTimePicker view, int year, int month,
                    int dayOfMonth, int hourOfDay, int minute) {
                mDate.set(Calendar.YEAR, year);
                mDate.set(Calendar.MONTH, month);
                mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                mDate.set(Calendar.MINUTE, minute);
                updateTitle(mDate.getTimeInMillis());
            }
        });
        mDate.setTimeInMillis(date);
        mDate.set(Calendar.SECOND, 0);
        mDateTimePicker.setCurrentDate(mDate.getTimeInMillis());
        setButton(context.getString(R.string.datetime_dialog_ok), this);
        setButton2(context.getString(R.string.datetime_dialog_cancel), (OnClickListener)null);
        set24HourView(DateFormat.is24HourFormat(this.getContext()));
        updateTitle(mDate.getTimeInMillis());
    }

    /**
     * 只记录标志位，不主动刷新标题；真正的标题刷新发生在构造末尾和每次滚动回调里。
     */
    public void set24HourView(boolean is24HourView) {
        mIs24HourView = is24HourView;
    }

    /**
     * 注入“确定”回调；不注入时点确定只会关闭对话框。
     */
    public void setOnDateTimeSetListener(OnDateTimeSetListener callBack) {
        mOnDateTimeSetListener = callBack;
    }

    /**
     * 按“年-月-日 时:分”刷新标题。
     *
     * <p>边界说明：三元表达式两个分支都取 FORMAT_24HOUR，实际等价于始终使用 24 小时格式，
     * 12/24 小时切换在标题上不生效；这是既有实现，标注阶段不修改，已记录为缺陷。
     */
    private void updateTitle(long date) {
        int flag =
            DateUtils.FORMAT_SHOW_YEAR |
            DateUtils.FORMAT_SHOW_DATE |
            DateUtils.FORMAT_SHOW_TIME;
        flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_24HOUR;
        setTitle(DateUtils.formatDateTime(this.getContext(), date, flag));
    }

    /**
     * 确定按钮回调：把内部 Calendar 的毫秒值交给宿主。
     * 回调不存在时静默关闭，保证对话框不会因为未设置监听而异常。
     */
    public void onClick(DialogInterface arg0, int arg1) {
        if (mOnDateTimeSetListener != null) {
            mOnDateTimeSetListener.OnDateTimeSet(this, mDate.getTimeInMillis());
        }
    }

}