package com.linkkou.mybatis.paging;


import com.linkkou.configproperty.Config;
import com.linkkou.configproperty.ConfigValue;
import org.springframework.beans.factory.annotation.Value;

import javax.validation.Valid;

/**
 * 统一分页工具
 * {
 * page:
 * itemsPerPage:
 * data:
 * ......:
 * }
 */
public class Pages<T> {

    //默认分页-每页数量
    @ConfigValue(value = @Value("${Globalparam.Paging.DEFAULT_ITEMS_PER_PAGE}"), defaultValue = "10")
    private transient Config<Integer> DEFAULT_ITEMS_PER_PAGE;

    //默认分页-当前页
    @ConfigValue(value = @Value("${Globalparam.Paging.DEFAULT_PAGE}"), defaultValue = "1")
    private transient Config<Integer> DEFAULT_PAGE;

    //传递的页号是否减掉
    @ConfigValue(value = @Value("${Globalparam.Paging.START_NUMBER}"), defaultValue = "1")
    private transient Config<Integer> START_NUMBER;

    /**
     * 当前页
     */
    private Integer page;

    /**
     * 每页页数
     */
    private Integer itemsPerPage;

    @Valid
    private T data;

    /**
     * 总页数
     */
    private int total;


    public Pages() { }

    public Pages(Pages<?> pages) {
        this.page = pages.getPage();
        this.itemsPerPage = pages.getItemsPerPage();
        this.total = pages.getTotal();
    }

    /**
     * 数据
     *
     * @return
     */
    public T getData() {
        return data;
    }

    /**
     * 数据
     *
     * @param data
     */
    public Pages<T> setData(T data) {
        this.data = data;
        return this;
    }

    /**
     * 偏移，根据传递的当前页转换
     *
     * @return
     */
    public int getOffset() {
        final Integer integer = START_NUMBER.get();
        return getPage() > 0 ? getItemsPerPage() * (getPage() - integer) : 0;
    }

    /**
     * 当前页
     *
     * @return
     */
    public int getPage() {
        return page == null ? DEFAULT_PAGE.get() : page;
    }

    /**
     * 当前页
     *
     * @return
     */
    public Pages<T> setPage(int val) {
        this.page = val;
        return this;
    }

    /**
     * 每页数量
     *
     * @return
     */
    public Pages<T> setItemsPerPage(int val) {
        this.itemsPerPage = val;
        return this;
    }

    /**
     * 每页数量
     *
     * @return
     */
    public int getItemsPerPage() {
        return itemsPerPage == null ? DEFAULT_ITEMS_PER_PAGE.get() : itemsPerPage;
    }

    /**
     * 获取总页数
     * 用于解决dao输出总页数的时候处理使用
     *
     * @return
     */
    public int getTotal() {
        return this.total;
    }

    /**
     * 设置总页数
     * 用于解决dao输出总页数的时候处理使用
     *
     * @return
     */
    public Pages<T> setTotal(int total) {
        this.total = total;
        return this;
    }


}
