package com.kma.knowledge.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * String ↔ tsvector 类型转换器
 * <p>
 * tsvector 由数据库自动生成，Java 侧仅做可读展示，不主动写入。
 *
 * @author party
 * @date 2026/06/30
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class TsvectorTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType("tsvector");
        pgObject.setValue(parameter);
        ps.setObject(i, pgObject);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toString(rs.getObject(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toString(rs.getObject(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toString(cs.getObject(columnIndex));
    }

    private String toString(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof PGobject) {
            return ((PGobject) obj).getValue();
        }
        return obj.toString();
    }
}



