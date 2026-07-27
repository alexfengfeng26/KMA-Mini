package com.kma.knowledge.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kma.knowledge.dto.SpaceAclRequest;
import com.kma.knowledge.dto.SpaceCreateRequest;
import com.kma.knowledge.dto.SpaceQueryRequest;
import com.kma.knowledge.dto.SpaceUpdateRequest;
import com.kma.knowledge.dto.SpaceVO;
import com.kma.knowledge.entity.KnowledgeSpaceAcl;

import java.util.List;

/**
 * 知识空间服务接口
 *
 * @author party
 * @date 2026/06/30
 */
public interface KnowledgeSpaceService {

    /**
     * 创建空间
     */
    Long create(SpaceCreateRequest request);

    /**
     * 更新空间
     */
    void update(SpaceUpdateRequest request);

    /**
     * 删除空间（同时清理 ACL、文档、分块）
     */
    void delete(Long spaceId);

    /**
     * 分页查询
     */
    Page<SpaceVO> page(SpaceQueryRequest request);

    /**
     * 详情
     */
    SpaceVO getById(Long spaceId);

    /**
     * 根据编码查询
     */
    SpaceVO getBySpaceCode(String spaceCode);

    /**
     * 修改状态
     */
    void changeStatus(Long spaceId, String status);

    /**
     * 添加 ACL
     */
    Long addAcl(SpaceAclRequest request);

    /** 查询空间 ACL。 */
    List<KnowledgeSpaceAcl> listAcls(Long spaceId);

    /**
     * 删除 ACL
     */
    void removeAcl(Long aclId);
}



