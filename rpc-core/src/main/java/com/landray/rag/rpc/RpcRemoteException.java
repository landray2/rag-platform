package com.landray.rag.rpc;

import com.landray.rag.rpc.message.RpcExceptionInfo;

/**
 * RPC 远端调用异常
 *
 * 【面试】消费端如何感知服务端抛出的业务异常？
 * 服务端将异常"拍平"为 RpcExceptionInfo（类名/消息/堆栈），随响应传回；
 * 消费端无法直接恢复原始异常类型（classpath 里可能没有那个类），
 * 因此统一包装成 RpcRemoteException 抛出，同时保留原始类名和堆栈供排查。
 */
public class RpcRemoteException extends RuntimeException {

    /** 原始异常类全限定名 */
    private final String remoteClassName;

    /** 原始异常堆栈（服务端打印的） */
    private final String remoteStackTrace;

    public RpcRemoteException(String message) {
        super(message);
        this.remoteClassName = null;
        this.remoteStackTrace = null;
    }

    public RpcRemoteException(RpcExceptionInfo info) {
        super(info.getClassName() + ": " + info.getMessage());
        this.remoteClassName = info.getClassName();
        this.remoteStackTrace = info.getStackTrace();
    }

    public String getRemoteClassName() {
        return remoteClassName;
    }

    public String getRemoteStackTrace() {
        return remoteStackTrace;
    }
}
