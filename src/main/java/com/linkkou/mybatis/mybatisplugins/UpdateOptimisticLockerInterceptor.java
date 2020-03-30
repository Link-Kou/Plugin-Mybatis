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


import com.linkkou.mybatis.lock.Lock;
import com.linkkou.mybatis.lock.UnLock;
import com.linkkou.mybatis.utils.PluginUtils;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;

import java.lang.reflect.Method;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于特点字段的乐观锁
 * 只支持时间方式
 *
 * @author lk
 * @version 1.0.0
 */
@Intercepts(
        {
                @Signature(type = StatementHandler.class, method = "update", args = {Statement.class}),
                @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
        }
)
public class UpdateOptimisticLockerInterceptor implements Interceptor {

    private static ConcurrentHashMap<String, DateTimeFormatter> dateTimeFormatterHashMap = new ConcurrentHashMap<String, DateTimeFormatter>();

    /**
     * 版本字段
     */
    private String VERSIONFIELD;

    private final static String PREPARE = "prepare";

    private final static String UPDATE = "update";

    private Object VALUE;

    /**
     * 默认全局开启关闭采用
     * {@link Lock}进行独立加锁
     */
    private Boolean GLOBALOPENING = false;

    static {
        dateTimeFormatterHashMap.put("Timestamp", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        dateTimeFormatterHashMap.put("Time", DateTimeFormatter.ofPattern("HH:mm:ss"));
        dateTimeFormatterHashMap.put("Date", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    public void setGlobalopening(Boolean globalopening) {
        this.GLOBALOPENING = globalopening;
    }

    public void setVersionFieldType(String versionfield) {
        this.VERSIONFIELD = versionfield;
    }

    public void setTimestampFormatter(String formatter) {
        dateTimeFormatterHashMap.put("Timestamp", DateTimeFormatter.ofPattern(formatter));
    }

    public void setTimeFormatter(String formatter) {
        dateTimeFormatterHashMap.put("Time", DateTimeFormatter.ofPattern(formatter));
    }

    public void setDateFormatter(String formatter) {
        dateTimeFormatterHashMap.put("Date", DateTimeFormatter.ofPattern(formatter));
    }


    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        try {
            if (invocation.getTarget() instanceof StatementHandler) {
                final StatementHandler target = (StatementHandler) invocation.getTarget();
                StatementHandler delegate = PluginUtils.realTarget(target);
                MetaObject metaObject = SystemMetaObject.forObject(delegate);
                MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
                if (SqlCommandType.UPDATE == mappedStatement.getSqlCommandType()) {
                    BoundSql boundSql = (BoundSql) metaObject.getValue("delegate.boundSql");
                    if (getinterfaceclass(mappedStatement)) {
                        if (PREPARE.equals(invocation.getMethod().getName())) {
                            VALUE = executeSelectSql(invocation, mappedStatement, boundSql);
                            modifyUpdateSql(metaObject, mappedStatement, boundSql, this.VALUE);
                        }
                        return invocation.proceed();
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return invocation.proceed();
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
            Update update = (Update) CCJSqlParserUtil.parse(sql);
            String newsql;
            //TODO 无论条件如何，返回就一条
            if (update.getWhere() != null) {
                newsql = String.format("Select %s from %s where %s limit 0,1", this.VERSIONFIELD, update.getTable(), update.getWhere());
            } else {
                newsql = String.format("Select %s from %s limit 0,1", this.VERSIONFIELD, update.getTable());
            }
            List<ParameterMapping> newparameterMappings = getWhereParameter(update.getWhere().toString(), boundSql.getParameterMappings());
            BoundSql newBoundSql = new BoundSql(mappedStatement.getConfiguration(), newsql, newparameterMappings, boundSql.getParameterObject());
            final MappedStatement newMappedStatement = ProxyResultSetSelectHandler(mappedStatement, new BoundSqlSqlSource(newBoundSql), SqlCommandType.SELECT);
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
        } catch (JSQLParserException e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 重新构建更新语句
     */
    private void modifyUpdateSql(MetaObject metaObject, MappedStatement mappedStatement, BoundSql boundSql, Object value) {
        String sql = boundSql.getSql();
        TemporalAccessor temporalAccessor = null;
        DateTimeFormatter dateTimeFormatter = null;
        if (value instanceof Timestamp) {
            temporalAccessor = ((Timestamp) value).toLocalDateTime();
            dateTimeFormatter = dateTimeFormatterHashMap.get("Timestamp");
        }
        if (value instanceof Time) {
            temporalAccessor = ((Time) value).toLocalTime();
            dateTimeFormatter = dateTimeFormatterHashMap.get("Time");
        }
        if (value instanceof Date) {
            temporalAccessor = ((Date) value).toLocalDate();
            dateTimeFormatter = dateTimeFormatterHashMap.get("Date");
        }
        if (temporalAccessor != null && dateTimeFormatter != null) {
            final String Dateformat = dateTimeFormatter.format(temporalAccessor);
            final String newsql = String.format("%s and %s = '%s' ", sql, this.VERSIONFIELD, Dateformat);
            BoundSql newBoundSql = new BoundSql(mappedStatement.getConfiguration(), newsql, boundSql.getParameterMappings(), boundSql.getParameterObject());
            final MappedStatement newMappedStatement = ProxyResultSetSelectHandler(mappedStatement, new BoundSqlSqlSource(newBoundSql), mappedStatement.getSqlCommandType());
            metaObject.setValue("delegate.mappedStatement", newMappedStatement);
            metaObject.setValue("delegate.boundSql.parameterMappings", boundSql.getParameterMappings());
            metaObject.setValue("delegate.boundSql", newBoundSql);
        }
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
     * 判断方法上面是否有@Lock注解
     *
     * @param mappedStatement
     * @return
     */
    private boolean getinterfaceclass(MappedStatement mappedStatement) {
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
            //TODO 有方法重载问题
            Method[] methods = classs.getMethods();
            for (Method method : methods) {
                if (method.getName().equals(methodsname)) {
                    if (this.GLOBALOPENING) {
                        UnLock lock = method.getAnnotation(UnLock.class);
                        return lock == null;
                    } else {
                        Lock lock = method.getAnnotation(Lock.class);
                        return lock != null;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 新加Mapper结构
     *
     * @param ms
     * @param newSqlSource
     * @return
     */
    private MappedStatement ProxyResultSetSelectHandler(MappedStatement ms, SqlSource newSqlSource, SqlCommandType sqlCommandType) {
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
        builder.resultMaps(newResultMap(ms.getResultMaps()));
        builder.resultSetType(ms.getResultSetType());
        builder.cache(ms.getCache());
        builder.flushCacheRequired(ms.isFlushCacheRequired());
        builder.useCache(ms.isUseCache());
        return builder.build();
    }

    /**
     * 构建返回值
     */
    private List<ResultMap> newResultMap(List<ResultMap> lrm) {
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
        /*if (target instanceof StatementHandler) {
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
