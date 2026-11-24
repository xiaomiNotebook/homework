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

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * 联系人查询工具：根据电话号码在系统通讯录中反查联系人显示名。
 *
 * <p>职责：被通话记录便签(CallNote)等场景调用，把裸号码翻译成"姓名"用于展示。</p>
 *
 * <p>设计意图：查询系统通讯录开销较大，且同一号码常被反复查询，
 * 因此用一个静态 Map 做号码->姓名的进程内缓存，命中即返回。</p>
 *
 * <p>生命周期/线程：{@link #sContactCache} 为静态(进程级)且惰性初始化，
 * 全进程共享、永不清理；本类所有方法为静态方法，未做任何同步。</p>
 *
 * <p>缺陷提示：
 * 1) 缓存无上限、无淘汰、无失效策略，长期运行会持续占用内存，
 *    且联系人改名后缓存不会更新；
 * 2) 非线程安全，多线程并发读写 HashMap 可能读到不一致状态；
 * 3) 号码查不到联系人的"未命中"结果不会被缓存，导致相同号码反复查询通讯录。</p>
 */
public class Contact {
    // 进程级号码->姓名缓存；为 null 表示尚未初始化
    private static HashMap<String, String> sContactCache;
    private static final String TAG = "Contact";

    /**
     * 查询用的 selection 模板。其中最后一个查询参数(?)是号码本身；
     * 子查询 phone_lookup.min_match = '+' 中的 '+' 是一个占位符，
     * 调用时会用 {@code PhoneNumberUtils.toCallerIDMinMatch(号码)} 的结果替换，
     * 从而实现"按号码匹配度"的模糊查找(而非精确相等)。
     * 之所以用替换而非直接参数化，是因为 min_match 是计算列，无法用占位符绑定。
     */
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
    + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
    + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 根据号码查询联系人显示名。
     *
     * <p>前置条件：{@code context}、{@code phoneNumber} 均不应为 null(本方法未做空值校验)。</p>
     *
     * <p>返回值：查到时返回联系人姓名(可能为 null)；查不到或无匹配时返回 null。
     * 调用方需自行判空，不要假定一定返回非 null。</p>
     *
     * <p>算法：先查内存缓存；未命中再查系统通讯录，命中后回填缓存。</p>
     */
    public static String getContact(Context context, String phoneNumber) {
        // 惰性初始化缓存
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 缓存命中直接返回
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // 用号码的 min_match 替换 selection 中的 '+' 占位符，得到可执行的条件
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },
                selection,
                new String[] { phoneNumber },
                null);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                String name = cursor.getString(0);
                sContactCache.put(phoneNumber, name);
                return name;
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                cursor.close();
            }
        } else {
            // 异常/边界提示：当 cursor 非空但结果为空(moveToFirst() 为 false)时，
            // 会走到此分支且【不会关闭 cursor】，造成游标泄漏。
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}
