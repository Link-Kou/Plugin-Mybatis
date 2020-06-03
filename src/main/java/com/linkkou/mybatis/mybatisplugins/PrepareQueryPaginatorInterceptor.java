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
import com.linkkou.mybatis.utils.FormatSql;
import com.linkkou.mybatis.utils.PluginUtils;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.ResultHandler;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * 基于特点字段的乐观锁
 * 只支持时间方式
 *
 * @author lk
 * @version 1.0.0
 */
@Intercepts(
        {
                @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}),
                @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class})
        }
)
public class PrepareQueryPaginatorInterceptor implements Interceptor {

    /**
     * 是否支持多条语句执行
     */
    protected boolean ALLOWMULTIQUERIES = false;

    private final static String PREPARE = "prepare";

    private final static String QUERY = "query";

    private Object VALUE;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        try {
            if (invocation.getTarget() instanceof StatementHandler && !ALLOWMULTIQUERIES) {
                final StatementHandler target = (StatementHandler) invocation.getTarget();
                StatementHandler delegate = PluginUtils.realTarget(target);
                MetaObject metaObject = SystemMetaObject.forObject(delegate);
                MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
                BoundSql boundSql = (BoundSql) metaObject.getValue("delegate.boundSql");
                if (SqlCommandType.SELECT == mappedStatement.getSqlCommandType()) {
                    if (getinterfaceclass(mappedStatement)) {
                        if (PREPARE.equals(invocation.getMethod().getName())) {
                            VALUE = executeSelectSql(invocation, mappedStatement, boundSql);
                        }
                        return this.getPaginator(metaObject, VALUE, invocation, mappedStatement, boundSql, boundSql.getParameterObject());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return invocation.proceed();
    }

    /**
     * 重新构建更新语句
     */
    private void ResetSelectSql(MetaObject metaObject, MappedStatement mappedStatement, BoundSql boundSql) {
        String sql = boundSql.getSql();
        BoundSql newBoundSql = new BoundSql(mappedStatement.getConfiguration(), sql, boundSql.getParameterMappings(), boundSql.getParameterObject());
        ResultMap resultMap = new ResultMap.Builder(null, mappedStatement.getResultMaps().get(0).getId(), Object.class, new ArrayList<ResultMapping>()).build();
        final MappedStatement newMappedStatement = ProxyResultSetSelectHandler(mappedStatement, new BoundSqlSqlSource(newBoundSql), mappedStatement.getSqlCommandType(), Arrays.asList(resultMap));
        metaObject.setValue("delegate.mappedStatement", newMappedStatement);
        metaObject.setValue("delegate.boundSql.parameterMappings", boundSql.getParameterMappings());
        metaObject.setValue("delegate.boundSql", newBoundSql);
    }

    /**
     * 构建返回参数
     * {@link org.apache.ibatis.session.defaults} selectOne 方法实现
     *
     * @param invocation
     * @return Select 默认支持 T
     */
    private Object getPaginator(MetaObject metaObject, Object count, Invocation invocation, MappedStatement mappedStatement, BoundSql boundSql, Object parameter) throws InvocationTargetException, IllegalAccessException {
        List<Pages> list = new ArrayList<>();
        final Object proceed = invocation.proceed();
        if (proceed instanceof ArrayList) {
            final Pages pages = getSqlLimitToPages(mappedStatement, boundSql, parameter);
            //ResetSelectSql(metaObject, mappedStatement, boundSql);
            try {
                pages.setData(proceed);
                if (Number.class.isAssignableFrom(count.getClass())) {
                    pages.setTotal((Integer) count);
                } else {
                    pages.setTotal(Integer.parseInt(count.toString()));
                }
                return list;
            } catch (Exception e) {

            }
            list.add(pages);
            return list;
        } else {
            return proceed;
        }
    }

    /**
     * 构建查询语句
     *
     * @param mappedStatement
     * @param boundSql
     * @return
     */
    private Object executeSelectSql(Invocation invocation, MappedStatement mappedStatement, BoundSql boundSql) {
        String sql = boundSql.getSql();
        try {
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            final PlainSelect selectBody = (PlainSelect) select.getSelectBody();
            TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
            final List<String> tableList = tablesNamesFinder.getTableList(select);
            if (tableList.size() == 1) {
                String newsql;
                if (selectBody.getWhere() != null) {
                    newsql = String.format("select count(*) from %s where %s", tableList.get(0), selectBody.getWhere());
                } else {
                    newsql = String.format("select count(*) from %s", tableList.get(0));
                }
                List<ParameterMapping> newparameterMappings = getWhereParameter(selectBody.getWhere().toString(), boundSql.getParameterMappings());
                BoundSql newBoundSql = new BoundSql(mappedStatement.getConfiguration(), newsql, newparameterMappings, boundSql.getParameterObject());
                ResultMap resultMap = new ResultMap.Builder(null, "", Integer.class, new ArrayList<ResultMapping>()).build();
                final MappedStatement newMappedStatement = ProxyResultSetSelectHandler(mappedStatement, new BoundSqlSqlSource(newBoundSql), SqlCommandType.SELECT, Arrays.asList(resultMap));
                if (invocation.getArgs()[0] instanceof Connection) {
                    Connection connection = (Connection) invocation.getArgs()[0];
                    try (PreparedStatement statement = connection.prepareStatement(newsql)) {
                        //构建ParameterHandler，用于设置统计sql的参数
                        ParameterHandler parameterHandler = new DefaultParameterHandler(newMappedStatement, newBoundSql.getParameterObject(), newBoundSql);
                        //设置总数sql的参数
                        parameterHandler.setParameters(statement);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                return resultSet.getObject(1);
                            }
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (JSQLParserException e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 获取到 Where 的参数
     *
     * @param where
     * @param parameterMappings
     */
    private List<ParameterMapping> getWhereParameter(String where, List<ParameterMapping> parameterMappings) {
        List<ParameterMapping> newparameterMappings = new ArrayList<>();
        final String[] split = where.split("");
        List<String> count = new ArrayList<String>();
        for (String s : split) {
            if ("?".equals(s.trim())) {
                count.add("?");
            }
        }
        final int i1 = parameterMappings.size() - count.size();
        for (int i = parameterMappings.size(); i > i1; i--) {
            newparameterMappings.add(0, parameterMappings.get(i - 1));
        }
        return newparameterMappings;
    }

    /**
     * 新加Mapper结构
     *
     * @param ms
     * @param newSqlSource
     * @return
     */
    private MappedStatement ProxyResultSetSelectHandler(MappedStatement ms, SqlSource newSqlSource, SqlCommandType sqlCommandType, List<ResultMap> newResultMap) {
        MappedStatement.Builder builder = new MappedStatement.Builder(ms.getConfiguration(), ms.getId(), newSqlSource, sqlCommandType);
        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.statementType(ms.getStatementType());
        builder.keyGenerator(ms.getKeyGenerator());
        if (ms.getKeyProperties() != null && ms.getKeyProperties().length > 0) {
            builder.keyProperty(ms.getKeyProperties()[0]);
        }
        builder.timeout(ms.getTimeout());
        builder.parameterMap(ms.getParameterMap());
        //查询返回的结果集合
        builder.resultMaps(newResultMap);
        builder.resultSetType(ms.getResultSetType());
        builder.cache(ms.getCache());
        builder.flushCacheRequired(ms.isFlushCacheRequired());
        builder.useCache(ms.isUseCache());
        return builder.build();
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
            try {
                int offset = Integer.parseInt(limit.getOffset().toString());
                int itemsPerPage = Integer.parseInt(limit.getRowCount().toString());
                pages.setItemsPerPage(itemsPerPage);
                pages.setPage(offset >= 0 && itemsPerPage > 0 ? offset / itemsPerPage + 1 : 0);
            } catch (Exception e) {
                pages.setItemsPerPage(0);
                pages.setPage(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pages;
    }

    /**
     * 判断返回参数是否为指定的分页对象
     *
     * @param mappedStatement
     * @return
     */
    protected boolean getinterfaceclass(MappedStatement mappedStatement) {
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

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
       /* if (target instanceof StatementHandler) {
            return Proxy.newProxyInstance(this.getClass().getClassLoader(), target.getClass().getInterfaces(), new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    return intercept(new Invocation(target, method, args));
                }
            });
        }
        return target;*/
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
