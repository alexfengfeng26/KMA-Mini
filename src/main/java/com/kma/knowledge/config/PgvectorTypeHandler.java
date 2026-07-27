package com.kma.knowledge.config;

import com.pgvector.PGvector;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * float[] ↔ pgvector VECTOR 类型转换器
 *
 * @author party
 * @date 2026/06/30
 */
@MappedTypes(float[].class)
@MappedJdbcTypes(JdbcType.OTHER)
public class PgvectorTypeHandler extends BaseTypeHandler<float[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, float[] parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, new PGvector(parameter));
    }

    @Override
    public float[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toFloatArray(rs.getObject(columnName));
    }

    @Override
    public float[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toFloatArray(rs.getObject(columnIndex));
    }

    @Override
    public float[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toFloatArray(cs.getObject(columnIndex));
    }

    private float[] toFloatArray(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof PGvector) {
            return ((PGvector) obj).toArray();
        }
        if (obj instanceof float[]) {
            return (float[]) obj;
        }
        if (obj instanceof String) {
            return parseVectorString((String) obj);
        }
        throw new IllegalArgumentException("Unsupported pgvector type: " + obj.getClass());
    }

    private float[] parseVectorString(String str) {
        String trimmed = str.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return new float[0];
        }
        String[] parts = trimmed.split(",");
        float[] ApiResult = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            ApiResult[i] = Float.parseFloat(parts[i].trim());
        }
        return ApiResult;
    }
}



