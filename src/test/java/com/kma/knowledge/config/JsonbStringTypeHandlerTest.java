package com.kma.knowledge.config;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonbStringTypeHandlerTest {

    private final JsonbStringTypeHandler handler = new JsonbStringTypeHandler();

    @Test
    void writesJsonAsPostgresJsonb() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);

        handler.setNonNullParameter(statement, 2, "{\"type\":\"recursive\"}", JdbcType.OTHER);

        var value = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(statement).setObject(org.mockito.ArgumentMatchers.eq(2), value.capture());
        assertThat(value.getValue()).isInstanceOfSatisfying(PGobject.class, jsonb -> {
            assertThat(jsonb.getType()).isEqualTo("jsonb");
            assertThat(jsonb.getValue()).isEqualTo("{\"type\":\"recursive\"}");
        });
    }

    @Test
    void readsJsonbAsString() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        jsonb.setValue("{\"enabled\":true}");
        when(resultSet.getObject("config")).thenReturn(jsonb);

        assertThat(handler.getNullableResult(resultSet, "config")).isEqualTo("{\"enabled\":true}");
    }
}
