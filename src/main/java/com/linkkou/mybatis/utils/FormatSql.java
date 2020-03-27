package com.linkkou.mybatis.utils;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.util.List;

/**
 * @author lk
 * @version 1.0
 * @date 2020/3/26 16:18
 */
public class FormatSql {

    private MappedStatement mappedStatement;
    private BoundSql boundSql;
    private Object params;
    private List<ParameterMapping> parameterMappings;
    private String sql;
    private TypeHandlerRegistry typeHandlerRegistry;
    private Configuration configuration;

    public FormatSql(MappedStatement mappedStatement, BoundSql boundSql, Object params) {
        this.mappedStatement = mappedStatement;
        this.boundSql = boundSql;
        this.params = params;
        this.parameterMappings = boundSql.getParameterMappings();
        this.typeHandlerRegistry = mappedStatement.getConfiguration().getTypeHandlerRegistry();
        this.configuration = mappedStatement.getConfiguration();
        build();
    }

    private void build() {
        if (parameterMappings != null) {
            this.sql = boundSql.getSql();
            for (ParameterMapping parameterMapping : parameterMappings) {
                if (parameterMapping.getMode() != ParameterMode.OUT) {
                    String propertyName = parameterMapping.getProperty();
                    Object value;
                    if (boundSql.hasAdditionalParameter(propertyName)) {
                        value = boundSql.getAdditionalParameter(propertyName);
                    } else if (params == null) {
                        value = null;
                    } else if (typeHandlerRegistry.hasTypeHandler(params.getClass())) {
                        value = params;
                    } else {
                        MetaObject metaObject = configuration.newMetaObject(params);
                        value = metaObject.getValue(propertyName);
                    }
                    /*if (parameterMapping.getProperty().endsWith("offset")){
                        offset = (Integer) value;
                    }
                    if(parameterMapping.getProperty().endsWith("itemsPerPage")) {
                        itemsPerPage = (Integer) value;
                    }*/
                    JdbcType jdbcType = parameterMapping.getJdbcType();
                    if (value == null && jdbcType == null) {
                        jdbcType = configuration.getJdbcTypeForNull();
                    }
                    replaceParameter(this.sql, value, jdbcType, parameterMapping.getJavaType());
                }
            }
        }
    }

    /**
     * 根据类型替换参数
     * 仅作为数字和字符串两种类型进行处理，需要特殊处理的可以继续完善这里
     *
     * @param sql
     * @param value
     * @param jdbcType
     * @param javaType
     * @return
     */
    private void replaceParameter(String sql, Object value, JdbcType jdbcType, Class javaType) {
        String strValue = String.valueOf(value);
        if (jdbcType != null) {
            switch (jdbcType) {
                //数字
                case BIT:
                case TINYINT:
                case SMALLINT:
                case INTEGER:
                case BIGINT:
                case FLOAT:
                case REAL:
                case DOUBLE:
                case NUMERIC:
                case DECIMAL:
                    break;
                //日期
                case DATE:
                case TIME:
                case TIMESTAMP:
                    //其他，包含字符串和其他特殊类型
                default:
                    strValue = "'" + strValue + "'";
            }
        } else if (Number.class.isAssignableFrom(javaType) || Number.class.isAssignableFrom(value.getClass())) {
            //不加单引号
        } else {
            strValue = "'" + strValue + "'";
        }
        this.sql = sql.replaceFirst("\\?", strValue);
    }


    public String getSql() {
        return this.sql;
    }
}
