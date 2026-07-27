package com.kma.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kma.knowledge.config.JsonbStringTypeHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeChatMessageMappingTest {

    @Test
    void citationsUsePostgresJsonbTypeHandler() throws Exception {
        TableName table = KnowledgeChatMessage.class.getAnnotation(TableName.class);
        Field citations = KnowledgeChatMessage.class.getDeclaredField("citations");
        TableField mapping = citations.getAnnotation(TableField.class);

        assertThat(table.autoResultMap()).isTrue();
        assertThat(mapping).isNotNull();
        assertThat(mapping.typeHandler()).isEqualTo(JsonbStringTypeHandler.class);
    }
}
