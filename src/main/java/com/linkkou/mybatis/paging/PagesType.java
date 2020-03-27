package com.linkkou.mybatis.paging;


import java.lang.annotation.*;

/**
 * 指定分页方式
 *
 * @author LK
 * @date 2018-05-05 16:08
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PagesType {

    /**
     * FOUND_ROWS == true
     * COUNT(*)  == false
     *
     * @return
     */
    boolean value() default true;
}
