/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 abel533@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.linkkou.mybatis.mybatisplugins;


import com.linkkou.mybatis.paging.Pages;
import com.linkkou.mybatis.paging.PagesType;
import com.linkkou.mybatis.utils.FormatSql;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 目前只有支持MySQL分页支持
 * 需要开启allowMultiQueries
 *
 * @author lk
 * @version 1.0.0
 */
@Intercepts(
        {
                @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
        }
)
//@SuppressWarnings()
public class QueryPaginatorInterceptor implements Interceptor {

    private final static String QUERY = "query";
    /**
     * FOUND_ROWS == true
     * COUNT(*)  == false
     */
    private boolean PAGINATORTYPE = false;

    /**
     * 是否支持多条语句执行
     */
    protected boolean ALLOWMULTIQUERIES = true;

    public void setPaginatortype(boolean paginator) {
        this.PAGINATORTYPE = paginator;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        final Object[] queryArgs = invocation.getArgs();
        if (invocation.getTarget() instanceof Executor && ALLOWMULTIQUERIES) {
            final MappedStatement mappedStatement = (MappedStatement) queryArgs[0];
            if (SqlCommandType.SELECT == mappedStatement.getSqlCommandType()) {
                if (QUERY.equals(invocation.getMethod().getName()) && queryArgs.length == 4) {
                    if (getinterfaceclass(mappedStatement)) {
                        final Object parameter = queryArgs[1];
                        final BoundSql boundSql = mappedStatement.getBoundSql(parameter);
                        MappedStatement newMs = ProxyResultSetHandler(mappedStatement, new BoundSqlSqlSource(getPagingSql(mappedStatement, boundSql)));
                        queryArgs[0] = newMs;
                        return getPaginator(invocation.proceed(), mappedStatement, boundSql, parameter);
                    }
                }
            }
        }
        return invocation.proceed();
    }

    /**
     * 返回分页sql语句
     *
     * @param mappedStatement
     * @param boundSql
     * @return
     */
    private BoundSql getPagingSql(MappedStatement mappedStatement, BoundSql boundSql) {
        if (PAGINATORTYPE) {
            return getSqlFoundRows(mappedStatement, boundSql);
        } else {
            return getSqlCountTrue(mappedStatement, boundSql);
        }
    }

    /**
     * ALLOWMULTIQUERIES = true count(*) 模式
     * <p>
     * select rowid, fid
     * from t_sysconfig_carspec_config tscc
     * WHERE fcar_name = #{car}
     * LIMIT
     * #{offset},#{rows}
     * </p>
     *
     * @param mappedStatement
     * @param boundSql
     * @return
     */
    private BoundSql getSqlCountTrue(MappedStatement mappedStatement, BoundSql boundSql) {
        String sql = boundSql.getSql();
        try {
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            final PlainSelect selectBody = (PlainSelect) select.getSelectBody();
            TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
            final List<String> tableList = tablesNamesFinder.getTableList(select);
            if (tableList.size() == 1) {
                String newSql;
                if (selectBody.getWhere() != null) {
                    newSql = String.format("%s ; select count(*) from %s Where %s ", sql, tableList.get(0), selectBody.getWhere());
                } else {
                    newSql = String.format("%s ; select count(*) from %s", sql, tableList.get(0));
                }
                List<ParameterMapping> newparameterMappings = new ArrayList<>();
                List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
                //newparameterMappings.addAll(parameterMappings);
                //Limit 去两个参数，所有参数以Mapper顺序为准
                for (int i = 0; i < parameterMappings.size() - 2; i++) {
                    newparameterMappings.add(parameterMappings.get(i));
                }
                BoundSql countBoundSql = new BoundSql(mappedStatement.getConfiguration(), newSql, newparameterMappings, boundSql.getParameterObject());
                return countBoundSql;
            }
        } catch (JSQLParserException e) {
            e.printStackTrace();
        }
        return boundSql;
    }

    /**
     * ALLOWMULTIQUERIES = true SQL_CALC_FOUND_ROWS 模式
     * <p>
     * select SQL_CALC_FOUND_ROWS rowid, fid
     * from t_sysconfig_carspec_config tscc
     * WHERE fcar_name = #{car}
     * LIMIT
     * #{offset},#{rows}
     * </p>
     *
     * @param mappedStatement
     * @param boundSql
     * @return
     */
    private BoundSql getSqlFoundRows(MappedStatement mappedStatement, BoundSql boundSql) {
        String sql = boundSql.getSql();
        sql = sql.replaceFirst("SQL_CALC_FOUND_ROWS", "").replaceFirst("(?i)select", "select SQL_CALC_FOUND_ROWS");
        String countSql = "; SELECT FOUND_ROWS() AS COUNT ; ";
        BoundSql countBoundSql = new BoundSql(mappedStatement.getConfiguration(), sql + countSql, boundSql.getParameterMappings(), boundSql.getParameterObject());
        //构建对Foreach的支持
        try {
            Field[] boundSqlfield = boundSql.getClass().getDeclaredFields();
            Field[] countBoundSqlfields = countBoundSql.getClass().getDeclaredFields();
            Field.setAccessible(countBoundSqlfields, true);
            Field.setAccessible(boundSqlfield, true);
            for (Field f1 : boundSqlfield) {
                for (Field f2 : countBoundSqlfields) {
                    if ("additionalParameters".equals(f1.getName()) && "additionalParameters".equals(f2.getName())) {
                        f2.set(countBoundSql, f1.get(boundSql));
                    }
                    if ("metaParameters".equals(f1.getName()) && "metaParameters".equals(f2.getName())) {
                        f2.set(countBoundSql, f1.get(boundSql));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return countBoundSql;
    }


    /**
     * 判断返回参数是否为指定的分页对象
     *
     * @param mappedStatement
     * @return
     */
    protected boolean getinterfaceclass(MappedStatement mappedStatement) {
        //class.forName("com.plugin.dao.SysconfigCarspecConfigMapper").getMethods()[0].getGenericReturnType
        StringBuilder stringBuilder = new StringBuilder();
        String[] oldpath = mappedStatement.getId().split("\\.");
        int oldpathlength = oldpath.length;
        String methodsname = "", classname = "";
        for (int i = 0; i < oldpathlength; i++) {
            if (i == oldpathlength - 1) {
                methodsname = oldpath[i];
            } else if (i < oldpathlength - 2) {
                stringBuilder.append(oldpath[i]).append(".");
            } else {
                stringBuilder.append(oldpath[i]);
            }
        }
        classname = stringBuilder.toString();
        try {
            Class classs = Class.forName(classname);
            Method[] methods = classs.getMethods();
            for (Method method : methods) {
                if (method.getName().equals(methodsname)) {
                    PagesType jsonResultValue = method.getAnnotation(PagesType.class);
                    if (jsonResultValue != null) {
                        PAGINATORTYPE = jsonResultValue.value();
                    }
                    if (method.getGenericReturnType() instanceof ParameterizedTypeImpl) {
                        Class typeImpl = ((ParameterizedTypeImpl) method.getGenericReturnType()).getRawType();
                        return typeImpl.equals(Pages.class);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 构建返回参数
     * {@link org.apache.ibatis.session.defaults} selectOne 方法实现
     *
     * @param invocation
     * @return Select 默认支持 T
     */
    private List<?> getPaginator(Object invocation, MappedStatement mappedStatement, BoundSql boundSql, Object parameter) {
        final Pages pages = getSqlLimitToPages(mappedStatement, boundSql, parameter);
        if (invocation instanceof ArrayList) {
            ArrayList resultList = (ArrayList) invocation;
            pages.setData(resultList.get(0));
            pages.setTotal((int) ((ArrayList) resultList.get(1)).get(0));
        }
        List<Pages> list = new ArrayList<>();
        list.add(pages);
        return list;
    }

    /**
     * 构建完整的SQL语句，获取到输入的Limt的值
     * 设置到Pages对象中
     *
     * @param mappedStatement
     * @param boundSql
     * @param params
     * @return
     */
    public Pages getSqlLimitToPages(MappedStatement mappedStatement, BoundSql boundSql, Object params) {
        Pages<Object> pages = new Pages();
        final FormatSql formatSql = new FormatSql(mappedStatement, boundSql, params);
        try {
            Select select = (Select) CCJSqlParserUtil.parse(formatSql.getSql());
            final PlainSelect selectBody = (PlainSelect) select.getSelectBody();
            final Limit limit = selectBody.getLimit();
            Integer offset = Integer.parseInt(limit.getOffset().toString());
            Integer itemsPerPage = Integer.parseInt(limit.getRowCount().toString());
            pages.setItemsPerPage(itemsPerPage);
            pages.setPage(offset >= 0 && itemsPerPage > 0 ? offset / itemsPerPage + 1 : 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pages;
    }


    /**
     * 新加Mapper结构
     *
     * @param ms
     * @param newSqlSource
     * @return
     */
    private MappedStatement ProxyResultSetHandler(MappedStatement ms, SqlSource newSqlSource) {
        MappedStatement.Builder builder = new MappedStatement.Builder(ms.getConfiguration(), ms.getId(), newSqlSource, ms.getSqlCommandType());
        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.statementType(ms.getStatementType());
        builder.keyGenerator(ms.getKeyGenerator());
        if (ms.getKeyProperties() != null && ms.getKeyProperties().length > 0) {
            builder.keyProperty(ms.getKeyProperties()[0]);
        }
        builder.timeout(ms.getTimeout());
        builder.parameterMap(ms.getParameterMap());
        builder.resultMaps(newResultMap(ms.getResultMaps()));
        builder.resultSetType(ms.getResultSetType());
        builder.cache(ms.getCache());
        builder.flushCacheRequired(ms.isFlushCacheRequired());
        builder.useCache(ms.isUseCache());
        return builder.build();
    }

    /**
     * 构建统一的返回值
     * 支持 allowMultiQueries=true的时候ResultMap会返回多值
     */
    protected List<ResultMap> newResultMap(List<ResultMap> lrm) {
        ResultMap resultMap = new ResultMap.Builder(null, lrm.size() > 0 ? lrm.get(0).getId() : "", Object.class, new ArrayList<ResultMapping>()).build();
        List<ResultMap> list = new ArrayList<>();
        if (lrm.size() > 0) {
            list.add(lrm.get(0));
        }
        list.add(resultMap);
        return list;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }

    public static class BoundSqlSqlSource implements SqlSource {
        private BoundSql boundSql;

        public BoundSqlSqlSource(BoundSql boundSql) {
            this.boundSql = boundSql;
        }

        @Override
        public BoundSql getBoundSql(Object parameterObject) {
            return boundSql;
        }
    }

}
