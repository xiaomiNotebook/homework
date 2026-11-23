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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import net.micode.notes.R;

/**
 * 下拉菜单的轻量封装：把一个 Button 和 PopupMenu 绑成“点按钮弹菜单”的组件。
 *
 * <p>职责边界：本类不处理菜单项语义，只负责展示、标题显示和点击回调转发；
 * 具体行为由调用方通过 {@link #setOnDropdownMenuItemClickListener} 注入。
 *
 * <p>生命周期：构造时就完成了菜单填充与事件绑定，之后只在 setTitle/findItem 时被访问；
 * 本类没有 Activity 引用以外的资源需要释放，跟随宿主界面的生命周期即可。
 */
public class DropdownMenu {
    private Button mButton;
    private PopupMenu mPopupMenu;
    private Menu mMenu;

    /**
     * 构造即完成绑定：设置下拉图标、以按钮为锚点创建 PopupMenu、按资源 id 填充菜单，
     * 并让按钮点击时弹出菜单。
     *
     * @param context 宿主上下文，PopupMenu 以它为主题
     * @param button  触发菜单显示的按钮，同时作为菜单锚点
     * @param menuId  菜单资源 id
     */
    public DropdownMenu(Context context, Button button, int menuId) {
        mButton = button;
        mButton.setBackgroundResource(R.drawable.dropdown_icon);
        mPopupMenu = new PopupMenu(context, mButton);
        mMenu = mPopupMenu.getMenu();
        mPopupMenu.getMenuInflater().inflate(menuId, mMenu);
        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mPopupMenu.show();
            }
        });
    }

    /**
     * 注入菜单项点击回调。允许传 null（PopupMenu 自身接受 null），
     * 用一个空判断避免构造失败时抛异常。
     */
    public void setOnDropdownMenuItemClickListener(OnMenuItemClickListener listener) {
        if (mPopupMenu != null) {
            mPopupMenu.setOnMenuItemClickListener(listener);
        }
    }

    /**
     * 按 id 查找菜单项，供调用方动态改标题或启用状态；找不到返回 null。
     */
    public MenuItem findItem(int id) {
        return mMenu.findItem(id);
    }

    /**
     * 设置按钮上显示的文字（即当前选项名）。
     */
    public void setTitle(CharSequence title) {
        mButton.setText(title);
    }
}
