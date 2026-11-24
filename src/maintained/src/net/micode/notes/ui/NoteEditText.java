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

import android.content.Context;
import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.widget.EditText;

import net.micode.notes.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 编辑页使用的自定义输入框，主要服务于清单模式：清单里的每一个条目就是一个 NoteEditText。
 * 在原生 EditText 的基础上做了三件事：
 * 1) 触摸时把光标精确定位到手指落点；
 * 2) 拦截回车和退格，把“回车 → 新增条目”“行首退格 → 合并或删除条目”交给 NoteEditActivity
 *    处理；
 * 3) 在长按菜单中，为文本里的链接（电话、网址、邮箱）增加快捷入口。
 * 如果没有注册监听器，回车、退格等按键保持系统默认行为。
 */
public class NoteEditText extends EditText {
    private static final String TAG = "NoteEditText";
    // 该输入框在清单中的位置（从 0 开始），回调时用它告诉 Activity 是哪一行。
    private int mIndex;
    // 记录按下退格键之前的光标位置。到 onKeyUp 时文本已经被删掉了，必须靠它判断“是不是在行
    // 首按的退格”。
    private int mSelectionStartBeforeDelete;

    // 链接类型的前缀，用来判断一个 URLSpan 是电话、网页还是邮箱。
    private static final String SCHEME_TEL = "tel:" ;
    private static final String SCHEME_HTTP = "http:" ;
    private static final String SCHEME_EMAIL = "mailto:" ;

    // 链接类型前缀 → 长按菜单文案的映射表。
    private static final Map<String, Integer> sSchemaActionResMap = new HashMap<String, Integer>();
    static {
        sSchemaActionResMap.put(SCHEME_TEL, R.string.note_link_tel);
        sSchemaActionResMap.put(SCHEME_HTTP, R.string.note_link_web);
        sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email);
    }

    /**
     * Call by the {@link NoteEditActivity} to delete or add edit text
     */
    public interface OnTextViewChangeListener {
        /**
         * Delete current edit text when {@link KeyEvent#KEYCODE_DEL} happens
         * and the text is null
         */
        void onEditTextDelete(int index, String text);

        /**
         * Add edit text after current edit text when {@link KeyEvent#KEYCODE_ENTER}
         * happen
         */
        void onEditTextEnter(int index, String text);

        /**
         * Hide or show item option when text change
         */
        void onTextChange(int index, boolean hasText);
    }

    // 清单模式下由 Activity 注入；为 null 说明处于普通模式或还没有设置。
    private OnTextViewChangeListener mOnTextViewChangeListener;

    // 在代码中直接创建控件时使用的构造方法。
    public NoteEditText(Context context) {
        super(context, null);
        mIndex = 0;
    }

    // 清单条目增删之后，Activity 会重新给受影响的条目编号。
    public void setIndex(int index) {
        mIndex = index;
    }

    public void setOnTextViewChangeListener(OnTextViewChangeListener listener) {
        mOnTextViewChangeListener = listener;
    }

    // 从 XML 布局填充（inflate）时系统调用的构造方法，使用系统默认的 EditText 样式。
    public NoteEditText(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.editTextStyle);
    }

    public NoteEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        // TODO Auto-generated constructor stub
    }

    /**
     * 手指按下时，手动把触点坐标换算成文本偏移量并设置光标：先扣掉内边距、加上滚动偏移，再
     * 通过 Layout 找到对应的行和字符位置。
     * 这样点在输入框空白处时，光标也会落在最近的字符上。最后仍交给父类处理，保留长按选择等
     * 系统行为。
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:

                int x = (int) event.getX();
                int y = (int) event.getY();
                x -= getTotalPaddingLeft();
                y -= getTotalPaddingTop();
                x += getScrollX();
                y += getScrollY();

                Layout layout = getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);
                Selection.setSelection(getText(), off);
                break;
        }

        return super.onTouchEvent(event);
    }

    /**
     * 回车：已设置监听器时直接返回 false 且不调用父类，系统就不会往文本里插入换行符，真正的
     * 拆分逻辑放在 onKeyUp 中。
     * 退格：先记下按键之前的光标位置，供 onKeyUp 判断使用。
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                if (mOnTextViewChangeListener != null) {
                    return false;
                }
                break;
            case KeyEvent.KEYCODE_DEL:
                mSelectionStartBeforeDelete = getSelectionStart();
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 退格：光标原本在行首（位置 0）且不是第一个条目，说明用户想删除这一行。此时通知
     * Activity 把当前条目的文字并到上一条并移除本条，返回 true 表示已处理。
     * 回车：以光标为界把文本切成两段，光标前的留在当前条目，光标后的内容作为新条目，通过
     * onEditTextEnter 交给 Activity 插入到下一行。
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_DEL:
                if (mOnTextViewChangeListener != null) {
                    if (0 == mSelectionStartBeforeDelete && mIndex != 0) {
                        mOnTextViewChangeListener.onEditTextDelete(mIndex, getText().toString());
                        return true;
                    }
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            case KeyEvent.KEYCODE_ENTER:
                if (mOnTextViewChangeListener != null) {
                    int selectionStart = getSelectionStart();
                    String text = getText().subSequence(selectionStart, length()).toString();
                    setText(getText().subSequence(0, selectionStart));
                    mOnTextViewChangeListener.onEditTextEnter(mIndex + 1, text);
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * 焦点变化时通知 Activity 是否显示这一行前面的复选框：失去焦点且内容为空时隐藏，其余情
     * 况显示。
     * 这样空白的占位行不会出现孤零零的复选框。
     */
    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (mOnTextViewChangeListener != null) {
            if (!focused && TextUtils.isEmpty(getText())) {
                mOnTextViewChangeListener.onTextChange(mIndex, false);
            } else {
                mOnTextViewChangeListener.onTextChange(mIndex, true);
            }
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    /**
     * 长按菜单：如果当前选区内恰好只有一个链接（URLSpan），就在菜单中加一项“打开电话 / 网页
     * / 邮箱”之类的入口，文案根据链接前缀选择，都不匹配时使用通用文案。
     * 选区里有多个链接时不添加，避免产生歧义。最后调用父类补全系统默认菜单项。
     */
    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        if (getText() instanceof Spanned) {
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();

            int min = Math.min(selStart, selEnd);
            int max = Math.max(selStart, selEnd);

            final URLSpan[] urls = ((Spanned) getText()).getSpans(min, max, URLSpan.class);
            if (urls.length == 1) {
                int defaultResId = 0;
                for(String schema: sSchemaActionResMap.keySet()) {
                    if(urls[0].getURL().indexOf(schema) >= 0) {
                        defaultResId = sSchemaActionResMap.get(schema);
                        break;
                    }
                }

                if (defaultResId == 0) {
                    defaultResId = R.string.note_link_other;
                }

                menu.add(0, 0, 0, defaultResId).setOnMenuItemClickListener(
                        new OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                // goto a new intent
                                urls[0].onClick(NoteEditText.this);
                                return true;
                            }
                        });
            }
        }
        super.onCreateContextMenu(menu);
    }
}