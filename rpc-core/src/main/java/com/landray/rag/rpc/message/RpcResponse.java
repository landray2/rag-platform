package com.landray.rag.rpc.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RPC 响应对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RpcResponse {

    /** 成功 */
    public static final int CODE_SUCCESS = 200;

    /** 服务端处理失败（业务异常） */
    public static final int CODE_SERVER_ERROR = 500;

    /** 服务不存在（接口未注册/版本不匹配） */
    public static final int CODE_SERVICE_NOT_FOUND = 404;

    /** 请求 ID（与请求一一对应） */
    private long requestId;

    /** 状态码 */
    private int code;

    /** 提示消息 */
    private String message;

    /** 返回数据 */
    private Object data;

    /** 远端异常信息（code != 200 时） */
    private RpcExceptionInfo exception;

    public static RpcResponse success(long requestId, Object data) {
        return RpcResponse.builder()
                .requestId(requestId)
                .code(CODE_SUCCESS)
                .data(data)
                .build();
    }

    public static RpcResponse fail(long requestId, int code, String message) {
        return RpcResponse.builder()
                .requestId(requestId)
                .code(code)
                .message(message)
                .build();
    }

    public static RpcResponse fail(long requestId, RpcExceptionInfo exception) {
        return RpcResponse.builder()
                .requestId(requestId)
                .code(CODE_SERVER_ERROR)
                .message(exception.getMessage())
                .exception(exception)
                .build();
    }
}
