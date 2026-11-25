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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import net.micode.notes.data.Notes;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;


/**
 * 便签列表适配器：负责创建/绑定行视图，并维护“位置 → 是否选中”的多选状态。
 *
 * <p>职责边界：选择状态只在内存保存，退出选择模式即清空；不持久化、不写数据库。
 * 设计意图：用 CursorAdapter 直接消费 Provider 查询结果；Cursor 所有权归 Activity，适配器不关闭它。
 * 生命周期：changeCursor/onContentChanged 时重算便签数量，保证“全选”判断基于最新数据。
 * 边界：只有 TYPE_NOTE 行可被选中；无效 Cursor 记日志并跳过刷新，不抛异常打断 UI。
 */
public class NotesListAdapter extends CursorAdapter {
    private static final String TAG = "NotesListAdapter";
    private Context mContext;
    /** key 是 ListView 的 adapter 位置，value 为是否选中；未记录的位置一律视为未选中。 */
    private HashMap<Integer, Boolean> mSelectedIndex;
    /** 当前 Cursor 中 TYPE_NOTE 行的数量，用于判断“是否已全选”，由 calcNotesCount 维护。 */
    private int mNotesCount;
    private boolean mChoiceMode;

    /** 选中便签所对应的桌面小部件属性，批量删除后据此刷新对应小部件。 */
    public static class AppWidgetAttribute {
        public int widgetId;
        public int widgetType;
    };

    /** 构造时不传 Cursor：由 Activity 在查询完成后通过 changeCursor 注入。 */
    public NotesListAdapter(Context context) {
        super(context, null);
        mSelectedIndex = new HashMap<Integer, Boolean>();
        mContext = context;
        mNotesCount = 0;
    }

    @Override
    /** 新建可复用的行视图；具体渲染交给 NotesListItem.bind。 */
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new NotesListItem(context);
    }

    @Override
    /**
     * 把当前行包装成 NoteItemData 快照后绑定，同时传入该位置的选择状态。
     * 前置条件：cursor 当前位置有效；视图类型不符时跳过绑定而不是崩溃。
     */
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof NotesListItem) {
            NoteItemData itemData = new NoteItemData(context, cursor);
            ((NotesListItem) view).bind(context, itemData, mChoiceMode,
                    isSelectedItem(cursor.getPosition()));
        }
    }

    /**
     * 写入选择状态并立即刷新列表，使 CheckBox 与菜单计数同步。
     * 前置条件：position 是 adapter 位置（调用方需扣掉 header 数量）。
     */
    public void setCheckedItem(final int position, final boolean checked) {
        mSelectedIndex.put(position, checked);
        notifyDataSetChanged();
    }

    public boolean isInChoiceMode() {
        return mChoiceMode;
    }

    /** 进入/退出选择模式都清空历史选择，避免上一次的选中项残留到下一次批量操作。 */
    public void setChoiceMode(boolean mode) {
        mSelectedIndex.clear();
        mChoiceMode = mode;
    }

    /**
     * 全选/取消全选：遍历 Cursor，只勾选便签行，文件夹不参与批量操作。
     * 性能边界：每个位置都会触发一次 notifyDataSetChanged；这是原实现的行为，标注阶段不改动。
     */
    public void selectAll(boolean checked) {
        Cursor cursor = getCursor();
        for (int i = 0; i < getCount(); i++) {
            if (cursor.moveToPosition(i)) {
                if (NoteItemData.getNoteType(cursor) == Notes.TYPE_NOTE) {
                    setCheckedItem(i, checked);
                }
            }
        }
    }

    /**
     * 收集选中便签的 ID 供批量删除/移动使用。
     * 防御：根文件夹 ID 被视为非法值跳过并记录日志；返回 HashSet，顺序不确定。
     */
    public HashSet<Long> getSelectedItemIds() {
        HashSet<Long> itemSet = new HashSet<Long>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position) == true) {
                Long id = getItemId(position);
                if (id == Notes.ID_ROOT_FOLDER) {
                    Log.d(TAG, "Wrong item id, should not happen");
                } else {
                    itemSet.add(id);
                }
            }
        }

        return itemSet;
    }

    /**
     * 收集选中便签关联的小部件；Cursor 异常时返回 null，调用方据此跳过小部件刷新。
     * 前置条件：Cursor 由本适配器持有；此处只能读取游标，不能关闭它。
     */
    public HashSet<AppWidgetAttribute> getSelectedWidget() {
        HashSet<AppWidgetAttribute> itemSet = new HashSet<AppWidgetAttribute>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position) == true) {
                Cursor c = (Cursor) getItem(position);
                if (c != null) {
                    AppWidgetAttribute widget = new AppWidgetAttribute();
                    NoteItemData item = new NoteItemData(mContext, c);
                    widget.widgetId = item.getWidgetId();
                    widget.widgetType = item.getWidgetType();
                    itemSet.add(widget);
                    /**
                     * Don't close cursor here, only the adapter could close it
                     */
                } else {
                    Log.e(TAG, "Invalid cursor");
                    return null;
                }
            }
        }
        return itemSet;
    }

    /** 遍历选择表计数；null 值集合防御性返回 0。 */
    public int getSelectedCount() {
        Collection<Boolean> values = mSelectedIndex.values();
        if (null == values) {
            return 0;
        }
        Iterator<Boolean> iter = values.iterator();
        int count = 0;
        while (iter.hasNext()) {
            if (true == iter.next()) {
                count++;
            }
        }
        return count;
    }

    /** 空列表不算全选：必须选中数大于 0 且等于当前便签总数。 */
    public boolean isAllSelected() {
        int checkedCount = getSelectedCount();
        return (checkedCount != 0 && checkedCount == mNotesCount);
    }

    public boolean isSelectedItem(final int position) {
        if (null == mSelectedIndex.get(position)) {
            return false;
        }
        return mSelectedIndex.get(position);
    }

    @Override
    /** Cursor 内容变化时重算便签总数，保证全选菜单文案正确。 */
    protected void onContentChanged() {
        super.onContentChanged();
        calcNotesCount();
    }

    @Override
    /** 替换 Cursor 后同步重算便签总数；新 Cursor 可以是 null（查询未完成时）。 */
    public void changeCursor(Cursor cursor) {
        super.changeCursor(cursor);
        calcNotesCount();
    }

    /**
     * 遍历当前 Cursor 统计便签行数。
     * 异常路径：中途遇到无效行则记日志并提前返回，mNotesCount 已在开头清零，不会留下旧计数。
     */
    private void calcNotesCount() {
        mNotesCount = 0;
        for (int i = 0; i < getCount(); i++) {
            Cursor c = (Cursor) getItem(i);
            if (c != null) {
                if (NoteItemData.getNoteType(c) == Notes.TYPE_NOTE) {
                    mNotesCount++;
                }
            } else {
                Log.e(TAG, "Invalid cursor");
                return;
            }
        }
    }
}
