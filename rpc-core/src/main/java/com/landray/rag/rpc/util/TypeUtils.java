package com.landray.rag.rpc.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 类型解析工具：参数类型字符串 → Class 对象
 *
 * 【面试】为什么不能直接 Class.forName？
 * 因为基本类型 int.class 的 getName() 是 "int" 而不是 "java.lang.Integer"，
 * 反射 getMethod(methodName, int.class) 时必须传原始类型，否则 NoSuchMethodException。
 */
public final class TypeUtils {

    private TypeUtils() {}

    private static final Map<String, Class<?>> PRIMITIVE_TYPES = new HashMap<>(16);

    static {
        PRIMITIVE_TYPES.put("void", void.class);
        PRIMITIVE_TYPES.put("boolean", boolean.class);
        PRIMITIVE_TYPES.put("byte", byte.class);
        PRIMITIVE_TYPES.put("char", char.class);
        PRIMITIVE_TYPES.put("short", short.class);
        PRIMITIVE_TYPES.put("int", int.class);
        PRIMITIVE_TYPES.put("long", long.class);
        PRIMITIVE_TYPES.put("float", float.class);
        PRIMITIVE_TYPES.put("double", double.class);
    }

    public static Class<?> resolve(String className) {
        try {
            Class<?> primitive = PRIMITIVE_TYPES.get(className);
            if (primitive != null) {
                return primitive;
            }
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("无法解析参数类型: " + className, e);
        }
    }
}
