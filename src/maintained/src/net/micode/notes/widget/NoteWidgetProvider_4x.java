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

package net.micode.notes.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.ResourceParser;


/**
 * 4x 尺寸的桌面小部件。与 2x 版本结构相同，只是布局、背景图和类型常量不同，其余逻辑复用父类
 * NoteWidgetProvider。
 */
public class NoteWidgetProvider_4x extends NoteWidgetProvider {
    // 系统要求刷新小部件时调用（刚添加到桌面、定时刷新等），转交给父类统一处理。
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.update(context, appWidgetManager, appWidgetIds);
    }

    protected int getLayoutId() {
        return R.layout.widget_4x;
    }

    @Override
    protected int getBgResourceId(int bgId) {
        return ResourceParser.WidgetBgResources.getWidget4xBgResource(bgId);
    }

    @Override
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_4X;
    }
}