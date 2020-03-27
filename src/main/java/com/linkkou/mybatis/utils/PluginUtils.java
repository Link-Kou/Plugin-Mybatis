package com.linkkou.mybatis.utils;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

/**
 * 获得真正的处理对象,可能多层代理.
 * copy mybatis-plus
 * {@see https://github.com/baomidou/mybatis-plus/blob/3.0/mybatis-plus-core/src/main/java/com/baomidou/mybatisplus/core/toolkit/PluginUtils.java}
 *
 * @author lk
 * @version 1.0
 * @date 2020/3/26 18:16
 */
public class PluginUtils {
    /**
     * 获得真正的处理对象,可能多层代理.
     * 必须使用{@link org.apache.ibatis.plugin}进行代理
     */
    @SuppressWarnings("unchecked")
    public static <T> T realTarget(Object target) {
        if (Proxy.isProxyClass(target.getClass())) {
            MetaObject metaObject = SystemMetaObject.forObject(target);
            return realTarget(metaObject.getValue("h.target"));
        }
        return (T) target;
    }

}
