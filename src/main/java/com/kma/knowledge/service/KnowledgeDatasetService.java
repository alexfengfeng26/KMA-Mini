package com.kma.knowledge.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kma.knowledge.dto.DatasetCreateRequest;
import com.kma.knowledge.dto.DatasetQueryRequest;
import com.kma.knowledge.dto.DatasetUpdateRequest;
import com.kma.knowledge.dto.DatasetVO;

import java.util.List;

/**
 * 数据集服务接口
 *
 * @author party
 * @date 2026/06/30
 */
public interface KnowledgeDatasetService {

    /**
     * 创建数据集
     */
    Long create(DatasetCreateRequest request);

    /**
     * 更新数据集
     */
    void update(DatasetUpdateRequest request);

    /**
     * 删除数据集（仅未绑定空间时）
     */
    void delete(Long datasetId);

    /**
     * 分页查询
     */
    Page<DatasetVO> page(DatasetQueryRequest request);

    /**
     * 详情
     */
    DatasetVO getById(Long datasetId);

    /**
     * 修改状态
     */
    void changeStatus(Long datasetId, String status);

    /**
     * 下拉列表
     */
    List<DatasetVO> listActive();
}



