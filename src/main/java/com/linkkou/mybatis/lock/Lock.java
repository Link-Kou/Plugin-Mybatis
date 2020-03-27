package com.linkkou.mybatis.lock;

import java.lang.annotation.*;

/**
 * 乐观锁
 * @author lk
 * @version 1.0
 * @date 2020/3/26 13:19
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Lock {

}
