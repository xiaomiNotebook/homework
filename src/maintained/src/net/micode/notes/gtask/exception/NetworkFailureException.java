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

package net.micode.notes.gtask.exception;

/**
 * GTask 同步过程中“网络传输失败”的受检异常。
 *
 * <p>表示 HTTP 请求/响应层面的错误，与 {@link ActionFailureException}（非受检、业务逻辑失败）
 * 相互区分。典型触发场景：</p>
 * <ul>
 *   <li>{@code ClientProtocolException}：HTTP 协议错误；</li>
 *   <li>{@code IOException}：连接超时、读取超时、网络中断；</li>
 *   <li>登录 Google Tasks 失败（账号无 token 或登录请求失败）。</li>
 * </ul>
 *
 * <p>设计为受检异常（继承 {@link Exception}）的原因：网络失败是外部环境导致的、
 * 调用方应当感知并处理的可预期失败。{@code GTaskClient} 的远程方法签名显式声明
 * {@code throws NetworkFailureException}，{@code GTaskManager.sync} 在最外层捕获并
 * 映射为 {@code STATE_NETWORK_ERROR}，由界面提示用户检查网络。</p>
 *
 * <p>边界说明：本异常只表达“传输失败”，不表达业务失败；JSON 解析等业务错误由
 * {@link ActionFailureException} 承担。抛出后当前次同步终止，{@code finally} 清理状态。</p>
 */
public class NetworkFailureException extends Exception {
    /** 序列化版本号，保持稳定以免反序列化兼容问题。 */
    private static final long serialVersionUID = 2107610287180234136L;

    /** 无参构造，不附带消息与原因。 */
    public NetworkFailureException() {
        super();
    }

    /**
     * 仅携带错误消息的构造。
     *
     * @param paramString 失败原因的可读描述，调用方据此打日志
     */
    public NetworkFailureException(String paramString) {
        super(paramString);
    }

    /**
     * 同时携带消息与原始异常的构造，便于在最外层日志中看到根因堆栈。
     *
     * @param paramString    失败原因描述
     * @param paramThrowable 原始异常（如 IOException、ClientProtocolException）
     */
    public NetworkFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}
