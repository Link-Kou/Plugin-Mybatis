package com.linkkou.mybatis.mybatisplugins.TypeHandlers;

import com.linkkou.gson.typefactory.GsonEnum;
import org.apache.ibatis.type.*;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 对象解析
 *
 * @author lk
 * @date 2018/9/25 17:02
 */
@MappedJdbcTypes({JdbcType.TINYINT})
@MappedTypes({GsonEnum.class})
public class AutoGsonEnumTypeHandler<E extends GsonEnum> extends BaseTypeHandler<E> {

    private Class<E> enumType;

    private E[] enums;

    public AutoGsonEnumTypeHandler() {

    }

    /**
     * 项目运行后自动注入相关对象类
     *
     * @param type
     */
    public AutoGsonEnumTypeHandler(Class<E> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type argument cannot be null");
        }
        this.enumType = type;
        this.enums = type.getEnumConstants();
        if (this.enums == null) {
            throw new IllegalArgumentException(type.getName() + " does not represent an enum type.");
        }
    }

    private E getEnums(Integer integer) {
        for (E e : enums) {
            return (E) e.convert(integer);
        }
        return null;
    }

    @Override
    public void setNonNullParameter(PreparedStatement preparedStatement, int i, GsonEnum gsonEnum, JdbcType jdbcType) throws SQLException {
        preparedStatement.setInt(i, gsonEnum.reverse());
    }

    @Override
    public E getNullableResult(ResultSet resultSet, String s) throws SQLException {
        final int anInt = resultSet.getInt(s);
        return getEnums(anInt);
    }

    @Override
    public E getNullableResult(ResultSet resultSet, int i) throws SQLException {
        final int anInt = resultSet.getInt(i);
        return getEnums(anInt);
    }

    @Override
    public E getNullableResult(CallableStatement callableStatement, int i) throws SQLException {
        final int anInt = callableStatement.getInt(i);
        return getEnums(anInt);
    }

    @Override
    public E getResult(ResultSet rs, String columnName) throws SQLException {
        return this.getNullableResult(rs, columnName);
    }

}
