package com.kma.knowledge.spi;

import java.util.List;

/**
 * 外部知识库桥接适配器 SPI
 * <p>
 * 用于把旧文件库、其他业务表的数据批量导入到独立知识库。
 *
 * @author party
 * @date 2026/06/30
 */
public interface IngestionAdapter {

    /**
     * 来源类型
     */
    String sourceType();

    /**
     * 批量拉取待摄入的数据
     */
    List<IngestionSource> fetchBatch(String spaceCode, int offset, int limit);
}



