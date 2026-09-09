package com.landray.rag.rpc.message;

/**
 * RPC 消息类型常量
 *
 * 【面试】协议设计：每种消息用 1 字节 type 区分，
 * 使同一条 TCP 连接能承载请求、响应、心跳三类流量（连接复用）。
 */
public final class MessageType {

    private MessageType() {}

    /** 请求 */
    public static final byte REQUEST = 1;

    /** 响应 */
    public static final byte RESPONSE = 2;

    /** 心跳 ping（客户端 → 服务端） */
    public static final byte PING = 3;

    /** 心跳 pong（服务端 → 客户端） */
    public static final byte PONG = 4;
}
