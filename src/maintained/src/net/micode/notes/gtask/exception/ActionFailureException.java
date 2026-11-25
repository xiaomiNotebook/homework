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
 * GTask 同步过程中“动作执行失败”的非受检异常。
 *
 * <p>与 {@link NetworkFailureException}（受检、表示网络传输层失败）不同，本异常继承
 * {@link RuntimeException}，表示的是业务/逻辑层面的失败，例如：</p>
 * <ul>
 *   <li>未登录就调用远程动作（{@code postRequest} / {@code getTaskLists}）；</li>
 *   <li>服务端返回内容无法解析为 JSON（{@code JSONException}）；</li>
 *   <li>同步过程中出现未知的同步动作类型；</li>
 *   <li>本地与云端映射缺失导致无法继续同步。</li>
 * </ul>
 *
 * <p>设计为非受检异常的原因：这些错误大多是编程约定被破坏或远端契约变化，
 * 调用方（{@code GTaskManager.sync}）在最外层统一捕获并映射为
 * {@code STATE_INTERNAL_ERROR}，不需要每个中间方法都声明或捕获。</p>
 *
 * <p>边界说明：本异常不携带可恢复的状态，抛出后当前次同步即视为内部错误终止；
 * 由 {@code finally} 块负责清理同步缓存与 {@code mSyncing} 标志。</p>
 */
public class ActionFailureException extends RuntimeException {
    /** 序列化版本号，由 IDE/工具生成，保持稳定以免反序列化兼容问题。 */
    private static final long serialVersionUID = 4425249765923293627L;

    /** 无参构造，不附带消息与原因。 */
    public ActionFailureException() {
        super();
    }

    /**
     * 仅携带错误消息的构造。
     *
     * @param paramString 失败原因的可读描述，调用方据此打日志
     */
    public ActionFailureException(String paramString) {
        super(paramString);
    }

    /**
     * 同时携带消息与原始异常的构造，便于在最外层日志中看到根因堆栈。
     *
     * @param paramString    失败原因描述
     * @param paramThrowable 原始异常（如 JSONException、未预期 Exception）
     */
    public ActionFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}
