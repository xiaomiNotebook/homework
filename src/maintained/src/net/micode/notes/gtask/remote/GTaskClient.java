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

package net.micode.notes.gtask.remote;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.GTaskStringUtils;
import net.micode.notes.ui.NotesPreferenceActivity;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;


/**
 * Google Tasks 的 HTTP 客户端（单例）。
 *
 * <p>职责：封装与 Google Tasks 服务端的全部网络交互，包括登录鉴权、拉取任务列表、
 * 以及对任务/任务列表的增删改查。它是 {@code GTaskManager} 与服务端之间的唯一网络出口。</p>
 *
 * <p>重要背景：这里对接的<b>不是</b> Google Tasks 官方 REST API，而是早期 Gmail Tasks 的
 * 移动网页接口（{@code mail.google.com/tasks/ig}）。数据不是规整的 JSON REST 响应，
 * 而是嵌在 HTML 页面里的一段 {@code _setup(...)} JavaScript；本类靠字符串截取把它抠出来
 * 再解析为 JSON，因此对服务端页面结构非常敏感（页面改版即可能失效）。</p>
 *
 * <p>鉴权方式：</p>
 * <ol>
 *   <li>用 {@link AccountManager} 取 Google 账号的 {@code goanna_mobile} auth token；</li>
 *   <li>带 token 请求 {@code GET_URL?auth=token}，服务端种入含 {@code GTL} 的 Cookie；</li>
 *   <li>后续 POST 复用同一 {@code DefaultHttpClient}（带 CookieStore）完成鉴权，无需再带 token。</li>
 * </ol>
 *
 * <p>关键设计：</p>
 * <ul>
 *   <li>登录态 5 分钟后视为过期，需重新登录（Cookie 有效期约定）；</li>
 *   <li>非 gmail/googlemail 账号会改写 URL 到自定义域 {@code /a/<domain>/}；</li>
 *   <li>{@code mUpdateArray} 把多个更新动作攒批提交，超过 10 项自动先提交，避免单次请求过大；</li>
 *   <li>每个动作带递增的 {@code mActionId}，服务端按 id 关联请求与响应。</li>
 * </ul>
 *
 * <p>异常策略：网络/IO 失败抛受检 {@code NetworkFailureException}；JSON 解析等业务失败抛
 * 非受检 {@code ActionFailureException}。</p>
 *
 * <p>边界与已知风险（仅记录，不在标注阶段修复）：使用已废弃的 {@code org.apache.http} 与
 * {@code DefaultHttpClient}；{@code getResponseContent} 对 gzip/deflate 分支重复调用
 * {@code entity.getContent()}；{@code loginGoogleAccount} 在失效 token 递归重试时未使用返回值。</p>
 */
public class GTaskClient {
    /** 日志标签。 */
    private static final String TAG = GTaskClient.class.getSimpleName();

    /** Google Tasks 基础地址，自定义域账号会在此基础上拼接 {@code /a/<domain>/}。 */
    private static final String GTASK_URL = "https://mail.google.com/tasks/";

    /** 普通（gmail）账号的 GET 地址：用于登录与拉取任务列表。 */
    private static final String GTASK_GET_URL = "https://mail.google.com/tasks/ig";

    /** 普通（gmail）账号的 POST 地址：用于提交动作。 */
    private static final String GTASK_POST_URL = "https://mail.google.com/tasks/r/ig";

    /** 单例实例。 */
    private static GTaskClient mInstance = null;

    /** HTTP 客户端，持有 CookieStore 以维持登录态；每次重新登录时重建。 */
    private DefaultHttpClient mHttpClient;

    /** 实际使用的 GET 地址，登录时可能被改写为自定义域地址。 */
    private String mGetUrl;

    /** 实际使用的 POST 地址，登录时可能被改写为自定义域地址。 */
    private String mPostUrl;

    /** 服务端返回的客户端版本号，每次 POST 必须回传，用于并发控制。 */
    private long mClientVersion;

    /** 是否已登录（Cookie 有效）；5 分钟或账号切换后置 false。 */
    private boolean mLoggedin;

    /** 上次登录成功的时间戳，用于判断 5 分钟过期。 */
    private long mLastLoginTime;

    /** 动作 id 计数器，每次请求前自增，用于匹配请求与响应。 */
    private int mActionId;

    /** 当前同步使用的 Google 账号。 */
    private Account mAccount;

    /** 攒批的更新动作数组；满 10 项或显式 commit 时提交。 */
    private JSONArray mUpdateArray;

    /** 私有构造：单例。初始化各字段默认值，URL 先用普通账号地址。 */
    private GTaskClient() {
        mHttpClient = null;
        mGetUrl = GTASK_GET_URL;
        mPostUrl = GTASK_POST_URL;
        mClientVersion = -1;
        mLoggedin = false;
        mLastLoginTime = 0;
        mActionId = 1;
        mAccount = null;
        mUpdateArray = null;
    }

    /** 获取单例，同步方法保证多线程下只创建一次。 */
    public static synchronized GTaskClient getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskClient();
        }
        return mInstance;
    }

    /**
     * 登录 Google Tasks：判断是否需要重新登录，取 auth token，再按账号类型选择
     * 自定义域或官方 URL 完成登录。
     *
     * <p>重新登录的触发条件：</p>
     * <ul>
     *   <li>距上次登录超过 5 分钟（约定的 Cookie 有效期）；</li>
     *   <li>设置页记录的同步账号与当前登录账号不一致（用户切换了账号）。</li>
     * </ul>
     *
     * @param activity 用于取账号与 token 的界面上下文
     * @return 登录成功返回 true；取 token 失败或登录失败返回 false
     */
    public boolean login(Activity activity) {
        // we suppose that the cookie would expire after 5 minutes
        // then we need to re-login
        // 约定 Cookie 5 分钟过期，超时后强制重新登录
        final long interval = 1000 * 60 * 5;
        if (mLastLoginTime + interval < System.currentTimeMillis()) {
            mLoggedin = false;
        }

        // need to re-login after account switch
        // 账号切换（设置页账号名与当前登录账号不一致）也要重新登录
        if (mLoggedin
                && !TextUtils.equals(getSyncAccount().name, NotesPreferenceActivity
                        .getSyncAccountName(activity))) {
            mLoggedin = false;
        }

        if (mLoggedin) {
            Log.d(TAG, "already logged in");
            return true;
        }

        mLastLoginTime = System.currentTimeMillis();
        // 第一步：取 Google 账号 auth token
        String authToken = loginGoogleAccount(activity, false);
        if (authToken == null) {
            Log.e(TAG, "login google account failed");
            return false;
        }

        // login with custom domain if necessary
        // 非 gmail/googlemail 账号走自定义域 URL（企业邮箱等），先尝试自定义域登录
        if (!(mAccount.name.toLowerCase().endsWith("gmail.com") || mAccount.name.toLowerCase()
                .endsWith("googlemail.com"))) {
            StringBuilder url = new StringBuilder(GTASK_URL).append("a/");
            int index = mAccount.name.indexOf('@') + 1;
            String suffix = mAccount.name.substring(index);
            url.append(suffix + "/");
            mGetUrl = url.toString() + "ig";
            mPostUrl = url.toString() + "r/ig";

            if (tryToLoginGtask(activity, authToken)) {
                mLoggedin = true;
            }
        }

        // try to login with google official url
        // 自定义域未登录成功（或本就是 gmail 账号）则回退到官方 URL
        if (!mLoggedin) {
            mGetUrl = GTASK_GET_URL;
            mPostUrl = GTASK_POST_URL;
            if (!tryToLoginGtask(activity, authToken)) {
                return false;
            }
        }

        mLoggedin = true;
        return true;
    }

    /**
     * 从 AccountManager 取当前同步账号的 auth token。
     *
     * <p>流程：枚举本机 Google 账号 → 匹配设置页选定的账号 → 申请 {@code goanna_mobile}
     * token → 阻塞等待结果。</p>
     *
     * <p>注意：{@code invalidateToken=true} 时会先作废旧 token 再递归调用自身取新 token，
     * 但递归返回值<b>未被使用</b>，本层仍返回作废前的旧 token——这是一个疑似缺陷，
     * 失效重试场景下可能仍拿旧 token 去登录（仅记录，不在标注阶段修复）。</p>
     *
     * @param activity       界面上下文
     * @param invalidateToken 是否先作废旧 token 再取新 token
     * @return auth token；无账号或取 token 失败返回 null
     */
    private String loginGoogleAccount(Activity activity, boolean invalidateToken) {
        String authToken;
        AccountManager accountManager = AccountManager.get(activity);
        // 只取类型为 com.google 的账号
        Account[] accounts = accountManager.getAccountsByType("com.google");

        if (accounts.length == 0) {
            Log.e(TAG, "there is no available google account");
            return null;
        }

        // 在账号列表里匹配设置页记录的同步账号名
        String accountName = NotesPreferenceActivity.getSyncAccountName(activity);
        Account account = null;
        for (Account a : accounts) {
            if (a.name.equals(accountName)) {
                account = a;
                break;
            }
        }
        if (account != null) {
            mAccount = account;
        } else {
            Log.e(TAG, "unable to get an account with the same name in the settings");
            return null;
        }

        // get the token now
        // 申请 goanna_mobile 类型的 auth token；getResult() 会阻塞直到系统返回
        AccountManagerFuture<Bundle> accountManagerFuture = accountManager.getAuthToken(account,
                "goanna_mobile", null, activity, null, null);
        try {
            Bundle authTokenBundle = accountManagerFuture.getResult();
            authToken = authTokenBundle.getString(AccountManager.KEY_AUTHTOKEN);
            if (invalidateToken) {
                // 作废旧 token 后递归取新 token（注意：递归返回值未赋回 authToken）
                accountManager.invalidateAuthToken("com.google", authToken);
                loginGoogleAccount(activity, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "get auth token failed");
            authToken = null;
        }

        return authToken;
    }

    /**
     * 用 token 登录 GTask；若首次失败，认为 token 过期，作废后取新 token 重试一次。
     *
     * @param activity  界面上下文
     * @param authToken 首次使用的 auth token
     * @return 两次尝试内登录成功返回 true，否则 false
     */
    private boolean tryToLoginGtask(Activity activity, String authToken) {
        if (!loginGtask(authToken)) {
            // maybe the auth token is out of date, now let's invalidate the
            // token and try again
            // 首次失败可能是 token 过期，作废后重取再试一次
            authToken = loginGoogleAccount(activity, true);
            if (authToken == null) {
                Log.e(TAG, "login google account failed");
                return false;
            }

            if (!loginGtask(authToken)) {
                Log.e(TAG, "login gtask failed");
                return false;
            }
        }
        return true;
    }

    /**
     * 真正执行一次登录请求：带 token GET 登录页，从响应 Cookie 确认鉴权，
     * 并从 HTML 中解析出客户端版本号 {@code mClientVersion}。
     *
     * <p>每次调用都会重建 {@code mHttpClient} 并新建 CookieStore，即重新登录会丢弃旧会话。</p>
     *
     * @param authToken Google 账号 auth token
     * @return 登录成功（拿到客户端版本号）返回 true；JSON 解析或网络异常返回 false
     */
    private boolean loginGtask(String authToken) {
        // 连接超时 10s、读超时 15s
        int timeoutConnection = 10000;
        int timeoutSocket = 15000;
        HttpParams httpParameters = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
        // 重建 HttpClient 与 CookieStore：每次登录都是一个全新会话
        mHttpClient = new DefaultHttpClient(httpParameters);
        BasicCookieStore localBasicCookieStore = new BasicCookieStore();
        mHttpClient.setCookieStore(localBasicCookieStore);
        HttpProtocolParams.setUseExpectContinue(mHttpClient.getParams(), false);

        // login gtask
        try {
            // 把 auth token 拼到 URL 上完成一次性鉴权，服务端随后种 Cookie
            String loginUrl = mGetUrl + "?auth=" + authToken;
            HttpGet httpGet = new HttpGet(loginUrl);
            HttpResponse response = null;
            response = mHttpClient.execute(httpGet);

            // get the cookie now
            // 检查是否拿到含 GTL 的鉴权 Cookie；没有仅打 warning，不直接失败
            List<Cookie> cookies = mHttpClient.getCookieStore().getCookies();
            boolean hasAuthCookie = false;
            for (Cookie cookie : cookies) {
                if (cookie.getName().contains("GTL")) {
                    hasAuthCookie = true;
                }
            }
            if (!hasAuthCookie) {
                Log.w(TAG, "it seems that there is no auth cookie");
            }

            // get the client version
            // 从返回的 HTML 中截取 _setup(...) 这段 JS，解析出客户端版本号 v
            String resString = getResponseContent(response.getEntity());
            String jsBegin = "_setup(";
            String jsEnd = ")}</script>";
            int begin = resString.indexOf(jsBegin);
            int end = resString.lastIndexOf(jsEnd);
            String jsString = null;
            if (begin != -1 && end != -1 && begin < end) {
                jsString = resString.substring(begin + jsBegin.length(), end);
            }
            // 若截取失败 jsString 为 null，new JSONObject(null) 会抛异常，被下面 catch
            JSONObject js = new JSONObject(jsString);
            mClientVersion = js.getLong("v");
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            // simply catch all exceptions
            // 兜底捕获所有异常（网络、解析等），统一视为登录失败
            Log.e(TAG, "httpget gtask_url failed");
            return false;
        }

        return true;
    }

    /** 取下一个动作 id 并自增，用于匹配请求与响应。 */
    private int getActionId() {
        return mActionId++;
    }

    /**
     * 构造一个带固定请求头的 POST 请求：表单编码 + AT=1（GTask 接口约定的标志头）。
     */
    private HttpPost createHttpPost() {
        HttpPost httpPost = new HttpPost(mPostUrl);
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
        httpPost.setHeader("AT", "1");
        return httpPost;
    }

    /**
     * 读取 HTTP 响应体为字符串，支持 gzip / deflate 解压。
     *
     * <p>不显然之处：先 {@code entity.getContent()} 取原始流赋给 {@code input}，
     * 但在 gzip/deflate 分支又<b>再次</b>调用 {@code entity.getContent()} 来构造解压流。
     * 多数实现在第二次取流时已读到 EOF，会导致解压流读到空内容——这是疑似缺陷，
     * 仅记录不修复。finally 中关闭的是最终包装后的 {@code input}。</p>
     *
     * @param entity HTTP 响应实体
     * @return 解压后的响应文本
     * @throws IOException 读取失败
     */
    private String getResponseContent(HttpEntity entity) throws IOException {
        String contentEncoding = null;
        if (entity.getContentEncoding() != null) {
            contentEncoding = entity.getContentEncoding().getValue();
            Log.d(TAG, "encoding: " + contentEncoding);
        }

        InputStream input = entity.getContent();
        // 根据 Content-Encoding 选择解压流（注意这里又取了一次 entity.getContent()）
        if (contentEncoding != null && contentEncoding.equalsIgnoreCase("gzip")) {
            input = new GZIPInputStream(entity.getContent());
        } else if (contentEncoding != null && contentEncoding.equalsIgnoreCase("deflate")) {
            // true 表示使用 raw deflate（无 zlib 头），适配 GTask 服务端的返回方式
            Inflater inflater = new Inflater(true);
            input = new InflaterInputStream(entity.getContent(), inflater);
        }

        try {
            InputStreamReader isr = new InputStreamReader(input);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();

            // 逐行读取并拼接，直到流结束（readLine 返回 null）
            while (true) {
                String buff = br.readLine();
                if (buff == null) {
                    return sb.toString();
                }
                sb = sb.append(buff);
            }
        } finally {
            // 无论是否异常都关闭底层流，避免连接泄漏
            input.close();
        }
    }

    /**
     * 向 GTask 提交一个动作请求（已登录前置），把 JSON 序列化为表单字段 {@code r} 后 POST。
     *
     * <p>异常映射：</p>
     * <ul>
     *   <li>网络/IO 异常 → {@code NetworkFailureException}（受检，需调用方处理）；</li>
     *   <li>JSON 解析或其它异常 → {@code ActionFailureException}（非受检，内部错误）。</li>
     * </ul>
     *
     * @param js 要提交的 JSON 请求体
     * @return 服务端返回的 JSON
     * @throws NetworkFailureException 网络传输失败
     */
    private JSONObject postRequest(JSONObject js) throws NetworkFailureException {
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }

        HttpPost httpPost = createHttpPost();
        try {
            // 请求体只有一个表单字段 r，值为整个 JSON 的字符串
            LinkedList<BasicNameValuePair> list = new LinkedList<BasicNameValuePair>();
            list.add(new BasicNameValuePair("r", js.toString()));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(list, "UTF-8");
            httpPost.setEntity(entity);

            // execute the post
            HttpResponse response = mHttpClient.execute(httpPost);
            String jsString = getResponseContent(response.getEntity());
            return new JSONObject(jsString);

        } catch (ClientProtocolException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("postRequest failed");
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("postRequest failed");
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("unable to convert response content to jsonobject");
        } catch (Exception e) {
            // 兜底：其余未预期异常按内部错误处理
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("error occurs when posting request");
        }
    }

    /**
     * 在云端新建一条任务（Task），并把服务端返回的新 gid 写回 {@code task}。
     *
     * <p>提交前先 {@link #commitUpdate()} 把之前攒批的更新刷出去，保证新建动作不与
     * 未提交的更新混在同一个请求里（新建需要拿返回的 new_id，不能和普通更新批量）。</p>
     *
     * @param task 待创建的任务对象；成功后其 gid 被赋值
     * @throws NetworkFailureException 网络失败；JSON 解析失败抛 ActionFailureException
     */
    public void createTask(Task task) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // action_list
            // 由 task 生成 create 动作 JSON，带本次 action_id
            actionList.put(task.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client_version
            // 每次请求都回传当前客户端版本号，服务端据此做并发冲突检测
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // post
            JSONObject jsResponse = postRequest(jsPost);
            // 取 results 数组第一个元素中的 new_id，作为云端分配的 gid
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
            task.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("create task: handing jsonobject failed");
        }
    }

    /**
     * 在云端新建一个任务列表（TaskList），并把服务端返回的新 gid 写回 {@code tasklist}。
     * 结构与 {@link #createTask} 对称，区别在于动作由 tasklist 生成。
     *
     * @param tasklist 待创建的任务列表；成功后其 gid 被赋值
     * @throws NetworkFailureException 网络失败
     */
    public void createTaskList(TaskList tasklist) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // action_list
            actionList.put(tasklist.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // post
            JSONObject jsResponse = postRequest(jsPost);
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
            tasklist.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("create tasklist: handing jsonobject failed");
        }
    }

    /**
     * 把 {@code mUpdateArray} 中攒批的更新动作一次性提交到服务端，提交后清空数组。
     * 没有待提交更新时直接返回。各业务方法在做“需要立即拿到结果”的操作前会先调用本方法。
     *
     * @throws NetworkFailureException 网络失败
     */
    public void commitUpdate() throws NetworkFailureException {
        if (mUpdateArray != null) {
            try {
                JSONObject jsPost = new JSONObject();

                // action_list
                jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, mUpdateArray);

                // client_version
                jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

                postRequest(jsPost);
                // 提交成功后清空批处理数组
                mUpdateArray = null;
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("commit update: handing jsonobject failed");
            }
        }
    }

    /**
     * 把一个节点的更新动作加入攒批数组 {@code mUpdateArray}；当批内超过 10 项时先自动提交，
     * 避免单次请求体过大被服务端拒绝。
     *
     * @param node 待更新的节点（Task / TaskList / MetaData），为 null 时忽略
     * @throws NetworkFailureException 自动提交时网络失败
     */
    public void addUpdateNode(Node node) throws NetworkFailureException {
        if (node != null) {
            // too many update items may result in an error
            // set max to 10 items
            // 批内超过 10 项先提交，控制单次请求大小
            if (mUpdateArray != null && mUpdateArray.length() > 10) {
                commitUpdate();
            }

            if (mUpdateArray == null)
                mUpdateArray = new JSONArray();
            mUpdateArray.put(node.getUpdateAction(getActionId()));
        }
    }

    /**
     * 把一条任务在任务列表之间或同一列表内移动。
     *
     * <p>字段拼装规则（不显然）：</p>
     * <ul>
     *   <li>仅当在<b>同一列表内</b>移动且任务不是第一个时，才带 {@code prior_sibling_id}
     *       （表示移动后的前一个兄弟）；</li>
     *   <li>仅当在<b>不同列表</b>之间移动时，才带 {@code dest_list}；</li>
     *   <li>{@code source_list} 与 {@code dest_parent} 始终带上。</li>
     * </ul>
     *
     * @param task      要移动的任务
     * @param preParent 移动前所在列表
     * @param curParent 移动后目标列表
     * @throws NetworkFailureException 网络失败
     */
    public void moveTask(Task task, TaskList preParent, TaskList curParent)
            throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // action_list
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_MOVE);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_ID, task.getGid());
            if (preParent == curParent && task.getPriorSibling() != null) {
                // put prioring_sibing_id only if moving within the tasklist and
                // it is not the first one
                // 同列表内移动且非首项时，才需要指定前一个兄弟来定位
                action.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, task.getPriorSibling());
            }
            action.put(GTaskStringUtils.GTASK_JSON_SOURCE_LIST, preParent.getGid());
            action.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT, curParent.getGid());
            if (preParent != curParent) {
                // put the dest_list only if moving between tasklists
                // 跨列表移动时才需要指定目标列表
                action.put(GTaskStringUtils.GTASK_JSON_DEST_LIST, curParent.getGid());
            }
            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("move task: handing jsonobject failed");
        }
    }

    /**
     * 删除一个节点：先标记 {@code node.setDeleted(true)} 再以 update 动作提交，
     * 提交后清空攒批数组（删除是立即生效动作，不与后续更新批量）。
     *
     * @param node 要删除的节点
     * @throws NetworkFailureException 网络失败
     */
    public void deleteNode(Node node) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // action_list
            // 服务端通过 deleted=true 的 update 动作实现删除，而非独立 delete 动作
            node.setDeleted(true);
            actionList.put(node.getUpdateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);
            mUpdateArray = null;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("delete node: handing jsonobject failed");
        }
    }

    /**
     * 拉取当前账号下的全部任务列表（含 meta 列表）。
     *
     * <p>通过 GET 登录页返回的 HTML，截取 {@code _setup(...)} 中的 {@code t.lists} 数组。
     * 与登录时共用同一段页面解析逻辑（结构脆弱，依赖服务端页面格式）。</p>
     *
     * @return 任务列表 JSON 数组
     * @throws NetworkFailureException 未登录或网络失败
     */
    public JSONArray getTaskLists() throws NetworkFailureException {
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }

        try {
            HttpGet httpGet = new HttpGet(mGetUrl);
            HttpResponse response = null;
            response = mHttpClient.execute(httpGet);

            // get the task list
            // 同样从 HTML 的 _setup(...) 段中解析
            String resString = getResponseContent(response.getEntity());
            String jsBegin = "_setup(";
            String jsEnd = ")}</script>";
            int begin = resString.indexOf(jsBegin);
            int end = resString.lastIndexOf(jsEnd);
            String jsString = null;
            if (begin != -1 && end != -1 && begin < end) {
                jsString = resString.substring(begin + jsBegin.length(), end);
            }
            JSONObject js = new JSONObject(jsString);
            // t.lists 即所有任务列表（含系统 meta 列表）
            return js.getJSONObject("t").getJSONArray(GTaskStringUtils.GTASK_JSON_LISTS);
        } catch (ClientProtocolException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("gettasklists: httpget failed");
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("gettasklists: httpget failed");
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("get task lists: handing jasonobject failed");
        }
    }

    /**
     * 拉取指定任务列表下的全部任务（不含已删除）。
     *
     * @param listGid 目标列表的云端 id
     * @return 任务 JSON 数组
     * @throws NetworkFailureException 网络失败
     */
    public JSONArray getTaskList(String listGid) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // action_list
            // GETALL 动作拉取指定列表的任务，get_deleted=false 表示不取已删除项
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_GETALL);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_LIST_ID, listGid);
            action.put(GTaskStringUtils.GTASK_JSON_GET_DELETED, false);
            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            JSONObject jsResponse = postRequest(jsPost);
            return jsResponse.getJSONArray(GTaskStringUtils.GTASK_JSON_TASKS);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("get task list: handing jsonobject failed");
        }
    }

    /** 返回当前登录的 Google 账号（由 GTaskManager 在成功通知等处使用）。 */
    public Account getSyncAccount() {
        return mAccount;
    }

    /** 清空攒批数组；同步开始时由 GTaskManager 调用，避免上一次残留的更新被带入本次。 */
    public void resetUpdateArray() {
        mUpdateArray = null;
    }
}
