package com.landray.rag.rpc.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 远端异常描述
 *
 * 【面试】为什么不直接序列化 Throwable？
 * 1. JDK 异常类大多有定制的 writeObject/writeReplace，Kryo 直接序列化容易出错或丢信息；
 * 2. 消费端 classpath 未必有异常类，反序列化会 ClassNotFoundException；
 * 3. 把异常"拍平"成 (className, message, stackTrace) 三元组是各主流 RPC（Dubbo 例外）的通用做法。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RpcExceptionInfo {

    /** 异常类全限定名 */
    private String className;

    /** 异常消息 */
    private String message;

    /** 堆栈字符串（排查用） */
    private String stackTrace;
}
