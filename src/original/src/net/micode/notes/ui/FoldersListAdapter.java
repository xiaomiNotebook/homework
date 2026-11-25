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
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;


/**
 * “移动到文件夹”对话框的列表适配器。
 *
 * <p>职责：把文件夹 Cursor 的当前行映射为一行可点击视图，并提供选中项的显示名。
 * 设计意图：便签与文件夹共用 note 表，本对话框只需要 ID 与名称两列，故使用轻量投影。
 * 前置条件：Cursor 必须由 {@link #PROJECTION} 查询得到，且由调用方负责关闭。
 * 生命周期：随对话框创建与销毁；本类不缓存跨对话框状态，也不接管 Cursor 所有权。
 * 边界：根目录没有可读名称，统一替换为本地化文案，避免把内部 ID 暴露给用户。
 */
public class FoldersListAdapter extends CursorAdapter {
    /** 投影列顺序必须与 ID_COLUMN / NAME_COLUMN 的下标一致，改任一处都要同步另一处。 */
    public static final String [] PROJECTION = {
        NoteColumns.ID,
        NoteColumns.SNIPPET
    };

    /** 列下标：ID_COLUMN → NoteColumns.ID，NAME_COLUMN → NoteColumns.SNIPPET。 */
    public static final int ID_COLUMN   = 0;
    public static final int NAME_COLUMN = 1;

    /**
     * 仅把 Context 与 Cursor 透传给父类；原实现中的 TODO 空桩没有业务逻辑，保留以免改动行为。
     */
    public FoldersListAdapter(Context context, Cursor c) {
        super(context, c);
        // TODO Auto-generated constructor stub
    }

    @Override
    /**
     * 为每行新建一个 FolderListItem；行视图由 ListView 复用，数据绑定集中在 bindView。
     */
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new FolderListItem(context);
    }

    @Override
    /**
     * 把 Cursor 当前行的文件夹名写入行视图。
     * 边界：根目录用本地化文案替代数据库 SNIPPET；instanceof 守卫避免视图被误用时强制转换崩溃。
     */
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof FolderListItem) {
            String folderName = (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) ? context
                    .getString(R.string.menu_move_parent_folder) : cursor.getString(NAME_COLUMN);
            ((FolderListItem) view).bind(folderName);
        }
    }

    /**
     * 取指定位置的显示名，供“已移动 N 条到 X”提示语使用。
     * 前置条件：position 必须落在 Cursor 有效范围内；与 bindView 共用根目录文案规则，保证两处一致。
     */
    public String getFolderName(Context context, int position) {
        Cursor cursor = (Cursor) getItem(position);
        return (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) ? context
                .getString(R.string.menu_move_parent_folder) : cursor.getString(NAME_COLUMN);
    }

    /**
     * 单行视图封装：构造时 inflate 一次并缓存 TextView，避免每次绑定重复 findViewById。
     */
    private class FolderListItem extends LinearLayout {
        private TextView mName;

        public FolderListItem(Context context) {
            super(context);
            inflate(context, R.layout.folder_list_item, this);
            mName = (TextView) findViewById(R.id.tv_folder_name);
        }

        /** 只更新文本，不改变行的可见性或选中态；选中逻辑由对话框处理。 */
        public void bind(String name) {
            mName.setText(name);
        }
    }

}
