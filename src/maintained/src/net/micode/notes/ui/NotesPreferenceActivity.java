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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;


/**
 * 设置界面：管理 Google 账号与便签同步（GTask）。
 *
 * <p>职责边界：本类只负责“选择/更换/移除同步账号”和“手动触发或取消同步”的界面与偏好持久化，
 * 真正的同步流程在 {@link GTaskSyncService} 中；本类通过广播接收同步进度。
 *
 * <p>界面结构：在 preferences.xml 的基础上追加两块动态内容——
 * 账号 Preference（动态创建，因为文案依赖当前账号）和一个自定义 header（含同步按钮与状态文本）。
 *
 * <p>生命周期要点：注册的广播接收器必须在 onDestroy 反注册；账号列表在打开对话框时快照到
 * mOriAccounts，用于 onResume 判断“用户是否刚从系统设置里新增了账号”。
 *
 * <p>前置条件与异常路径：本界面假定运行环境已配置 Google 账号体系，
 * 若无任何账号，只展示“添加账号”入口；跳转系统添加账号页失败时仅打印日志、不做提示。
 * 更换账号后的云端字段清理在后台线程执行，与界面刷新不同步，存在短暂的状态不一致窗口。
 */
public class NotesPreferenceActivity extends PreferenceActivity {
    /** 本界面所有偏好共用的文件名。 */
    public static final String PREFERENCE_NAME = "notes_preferences";

    /** 当前同步账号名的偏好键；静态读取方法也依赖它，因此必须保持稳定。 */
    public static final String PREFERENCE_SYNC_ACCOUNT_NAME = "pref_key_account_name";

    /** 上次同步完成时间的偏好键（毫秒，0 表示从未同步）。 */
    public static final String PREFERENCE_LAST_SYNC_TIME = "pref_last_sync_time";

    /** 随机背景色开关的偏好键，本界面不直接读写，仅对其它模块公开。 */
    public static final String PREFERENCE_SET_BG_COLOR_KEY = "pref_key_bg_random_appear";

    /** preferences.xml 中账号分类的键，动态账号 Preference 会加到该分类下。 */
    private static final String PREFERENCE_SYNC_ACCOUNT_KEY = "pref_sync_account_key";

    /** 跳转系统“添加账号”页时，用这个 extra 限定只显示支持 Gmail 同步的账号类型。 */
    private static final String AUTHORITIES_FILTER_KEY = "authorities";

    private PreferenceCategory mAccountCategory;

    private GTaskReceiver mReceiver;

    /** 打开账号对话框时的账号快照，用于 onResume 对比新增账号。 */
    private Account[] mOriAccounts;

    /** 是否刚从本界面跳去系统“添加账号”页，只有为 true 才需要在 onResume 里自动选中新账号。 */
    private boolean mHasAddedAccount;

    /**
     * 初始化界面：启用返回箭头、加载偏好、注册同步进度广播、插入自定义 header。
     *
     * <p>注意注册了接收器却没有在 onPause 反注册，只在 onDestroy 反注册，
     * 因此界面不可见期间仍会收到广播并刷新 UI（这是既有实现）。
     */
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        /* using the app icon for navigation */
        getActionBar().setDisplayHomeAsUpEnabled(true);

        addPreferencesFromResource(R.xml.preferences);
        mAccountCategory = (PreferenceCategory) findPreference(PREFERENCE_SYNC_ACCOUNT_KEY);
        mReceiver = new GTaskReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(GTaskSyncService.GTASK_SERVICE_BROADCAST_NAME);
        registerReceiver(mReceiver, filter);

        mOriAccounts = null;
        View header = LayoutInflater.from(this).inflate(R.layout.settings_header, null);
        getListView().addHeaderView(header, null, true);
    }

    /**
     * 每次回到前台时刷新界面，并处理“用户刚新增了账号”的自动选中逻辑。
     *
     * <p>非显然算法：只有 mHasAddedAccount 为 true（确实是从本界面跳去加账号）才处理；
     * 用 mOriAccounts 与当前账号列表做差集，找到第一个新增账号后立即把它设为同步账号并跳出，
     * 因为产品上只允许一个同步账号。
     *
     * <p>边界：mOriAccounts 为 null（未打开过账号对话框）时直接跳过对比，不会误选。
     */
    @Override
    protected void onResume() {
        super.onResume();

        // need to set sync account automatically if user has added a new
        // account
        if (mHasAddedAccount) {
            Account[] accounts = getGoogleAccounts();
            if (mOriAccounts != null && accounts.length > mOriAccounts.length) {
                for (Account accountNew : accounts) {
                    boolean found = false;
                    for (Account accountOld : mOriAccounts) {
                        if (TextUtils.equals(accountOld.name, accountNew.name)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        setSyncAccount(accountNew.name);
                        break;
                    }
                }
            }
        }

        refreshUI();
    }

    /**
     * 反注册广播接收器，防止 Activity 销毁后仍被回调导致泄漏。
     */
    @Override
    protected void onDestroy() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        super.onDestroy();
    }

    /**
     * 重建“同步账号”这一条 Preference。
     *
     * <p>点击行为分三种情况，优先级不可调换：
     * 正在同步 → 只提示不能改；尚未设置账号 → 直接弹选择框；已设置账号 → 先弹风险确认框。
     * 先判断同步状态，是为了避免同步过程中改账号导致本地与云端数据对不上。
     */
    private void loadAccountPreference() {
        mAccountCategory.removeAll();

        Preference accountPref = new Preference(this);
        final String defaultAccount = getSyncAccountName(this);
        accountPref.setTitle(getString(R.string.preferences_account_title));
        accountPref.setSummary(getString(R.string.preferences_account_summary));
        accountPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                if (!GTaskSyncService.isSyncing()) {
                    if (TextUtils.isEmpty(defaultAccount)) {
                        // the first time to set account
                        showSelectAccountAlertDialog();
                    } else {
                        // if the account has already been set, we need to promp
                        // user about the risk
                        showChangeAccountConfirmAlertDialog();
                    }
                } else {
                    Toast.makeText(NotesPreferenceActivity.this,
                            R.string.preferences_toast_cannot_change_account, Toast.LENGTH_SHORT)
                            .show();
                }
                return true;
            }
        });

        mAccountCategory.addPreference(accountPref);
    }

    /**
     * 根据同步状态刷新按钮文案/行为与“上次同步时间”。
     *
     * <p>状态机：同步中 → 按钮为“取消”，状态文本显示实时进度；
     * 非同步 → 按钮为“立即同步”，状态文本显示上次同步时间（从未同步则隐藏）。
     * 另外账号为空时按钮会被禁用，避免无账号可同步却可点击。
     */
    private void loadSyncButton() {
        Button syncButton = (Button) findViewById(R.id.preference_sync_button);
        TextView lastSyncTimeView = (TextView) findViewById(R.id.prefenerece_sync_status_textview);

        // set button state
        if (GTaskSyncService.isSyncing()) {
            syncButton.setText(getString(R.string.preferences_button_sync_cancel));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.cancelSync(NotesPreferenceActivity.this);
                }
            });
        } else {
            syncButton.setText(getString(R.string.preferences_button_sync_immediately));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.startSync(NotesPreferenceActivity.this);
                }
            });
        }
        syncButton.setEnabled(!TextUtils.isEmpty(getSyncAccountName(this)));

        // set last sync time
        if (GTaskSyncService.isSyncing()) {
            lastSyncTimeView.setText(GTaskSyncService.getProgressString());
            lastSyncTimeView.setVisibility(View.VISIBLE);
        } else {
            long lastSyncTime = getLastSyncTime(this);
            if (lastSyncTime != 0) {
                lastSyncTimeView.setText(getString(R.string.preferences_last_sync_time,
                        DateFormat.format(getString(R.string.preferences_last_sync_time_format),
                                lastSyncTime)));
                lastSyncTimeView.setVisibility(View.VISIBLE);
            } else {
                lastSyncTimeView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * 界面刷新入口：账号条目与同步按钮都依赖同步状态和偏好，所以总是一起重建。
     */
    private void refreshUI() {
        loadAccountPreference();
        loadSyncButton();
    }

    /**
     * 弹出账号选择框：单选列表 + 底部“添加账号”入口。
     *
     * <p>关键副作用：打开时快照当前账号列表到 mOriAccounts，并清零 mHasAddedAccount，
     * 供 onResume 判断用户是否新增了账号（见 onResume 的差集逻辑）。
     *
     * <p>边界：没有任何 Google 账号时列表为空，只显示“添加账号”入口；
     * checkedItem 为 -1 表示当前没有选中项。
     */
    private void showSelectAccountAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_select_account_title));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_select_account_tips));

        dialogBuilder.setCustomTitle(titleView);
        dialogBuilder.setPositiveButton(null, null);

        Account[] accounts = getGoogleAccounts();
        String defAccount = getSyncAccountName(this);

        mOriAccounts = accounts;
        mHasAddedAccount = false;

        if (accounts.length > 0) {
            CharSequence[] items = new CharSequence[accounts.length];
            final CharSequence[] itemMapping = items;
            int checkedItem = -1;
            int index = 0;
            for (Account account : accounts) {
                if (TextUtils.equals(account.name, defAccount)) {
                    checkedItem = index;
                }
                items[index++] = account.name;
            }
            dialogBuilder.setSingleChoiceItems(items, checkedItem,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            setSyncAccount(itemMapping[which].toString());
                            dialog.dismiss();
                            refreshUI();
                        }
                    });
        }

        View addAccountView = LayoutInflater.from(this).inflate(R.layout.add_account_text, null);
        dialogBuilder.setView(addAccountView);

        final AlertDialog dialog = dialogBuilder.show();
        addAccountView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mHasAddedAccount = true;
                Intent intent = new Intent("android.settings.ADD_ACCOUNT_SETTINGS");
                intent.putExtra(AUTHORITIES_FILTER_KEY, new String[] {
                    "gmail-ls"
                });
                startActivityForResult(intent, -1);
                dialog.dismiss();
            }
        });
    }

    /**
     * 已设置账号时点击账号条目弹出的二次确认框：更换、移除、取消。
     *
     * <p>设计原因：更换账号会清空本地 GTask 关联字段，属于破坏性操作，必须先告知风险再执行。
     * 取消（index=2）不执行任何动作，只关闭弹窗。
     */
    private void showChangeAccountConfirmAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_change_account_title,
                getSyncAccountName(this)));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_change_account_warn_msg));
        dialogBuilder.setCustomTitle(titleView);

        CharSequence[] menuItemArray = new CharSequence[] {
                getString(R.string.preferences_menu_change_account),
                getString(R.string.preferences_menu_remove_account),
                getString(R.string.preferences_menu_cancel)
        };
        dialogBuilder.setItems(menuItemArray, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showSelectAccountAlertDialog();
                } else if (which == 1) {
                    removeSyncAccount();
                    refreshUI();
                }
            }
        });
        dialogBuilder.show();
    }

    /**
     * 只取类型为 com.google 的账号；不传任何筛选条件，因此包括未开启同步的账号。
     */
    private Account[] getGoogleAccounts() {
        AccountManager accountManager = AccountManager.get(this);
        return accountManager.getAccountsByType("com.google");
    }

    /**
     * 切换同步账号，是“换号”动作的收口点，必须一次性完成三件事：
     * <ol>
     *   <li>把新账号写入偏好（null 存空串，避免类型不一致）；</li>
     *   <li>清零“上次同步时间”，因为旧时间对新账号没有意义；</li>
     *   <li>在后台线程清空所有便签的 gtask_id / sync_id，断开与旧账号云端数据的绑定。</li>
     * </ol>
     *
     * <p>为什么开线程：全表 update 可能较慢，不能阻塞主线程；
     * 这也意味着清理与界面刷新是异步的，紧接着立刻同步时可能出现短暂的旧 id 残留。
     * 账号未变化时整个方法无操作，保证幂等。
     */
    private void setSyncAccount(String account) {
        if (!getSyncAccountName(this).equals(account)) {
            SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            if (account != null) {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, account);
            } else {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
            }
            editor.commit();

            // clean up last sync time
            setLastSyncTime(this, 0);

            // clean up local gtask related info
            new Thread(new Runnable() {
                public void run() {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.GTASK_ID, "");
                    values.put(NoteColumns.SYNC_ID, 0);
                    getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
                }
            }).start();

            Toast.makeText(NotesPreferenceActivity.this,
                    getString(R.string.preferences_toast_success_set_accout, account),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 移除同步账号：删除账号与上次同步时间两个键，并同样在后台清空 gtask 关联字段。
     * 与 setSyncAccount 的区别是不写入新账号，仅回到“未绑定”状态。
     */
    private void removeSyncAccount() {
        SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        if (settings.contains(PREFERENCE_SYNC_ACCOUNT_NAME)) {
            editor.remove(PREFERENCE_SYNC_ACCOUNT_NAME);
        }
        if (settings.contains(PREFERENCE_LAST_SYNC_TIME)) {
            editor.remove(PREFERENCE_LAST_SYNC_TIME);
        }
        editor.commit();

        // clean up local gtask related info
        new Thread(new Runnable() {
            public void run() {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.GTASK_ID, "");
                values.put(NoteColumns.SYNC_ID, 0);
                getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
            }
        }).start();
    }

    /**
     * 供全应用读取当前同步账号；未设置时返回空串而不是 null，
     * 调用方因此可以直接用 TextUtils.isEmpty 判断，不必做空值保护。
     */
    public static String getSyncAccountName(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
    }

    /**
     * 写入上次同步时间；同步服务在结束后调用，传 0 表示清除记录。
     */
    public static void setLastSyncTime(Context context, long time) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong(PREFERENCE_LAST_SYNC_TIME, time);
        editor.commit();
    }

    /**
     * 读取上次同步时间，默认 0；界面据此决定是否显示“上次同步于…”。
     */
    public static long getLastSyncTime(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getLong(PREFERENCE_LAST_SYNC_TIME, 0);
    }

    /**
     * 同步服务广播的接收器：每次收到广播都先整体刷新界面，
     * 若广播携带“正在同步”标记，再用广播里的进度文本覆盖状态栏文字。
     *
     * <p>边界：同步结束的广播不带该标记，此时只刷新按钮状态，进度文本由 loadSyncButton 决定。
     */
    private class GTaskReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUI();
            if (intent.getBooleanExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_IS_SYNCING, false)) {
                TextView syncStatus = (TextView) findViewById(R.id.prefenerece_sync_status_textview);
                syncStatus.setText(intent
                        .getStringExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_PROGRESS_MSG));
            }

        }
    }

    /**
     * 处理标题栏返回箭头：回到便签列表而不是简单 finish，
     * 并加 CLEAR_TOP 清掉中间栈，避免从设置反复进入时任务栈不断堆叠。
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this, NotesListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            default:
                return false;
        }
    }
}
