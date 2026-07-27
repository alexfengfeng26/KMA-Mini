package com.kma.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识空间访问控制
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@TableName("knowledge_space_acl")
public class KnowledgeSpaceAcl implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long aclId;

    private Long spaceId;

    /**
     * 主体类型：role / user / org
     */
    private String principalType;

    /**
     * 主体标识：角色编码 / 用户ID / 组织编码
     */
    private String principalValue;

    /**
     * 权限：read / ingest / admin
     */
    private String permission;

    private LocalDateTime createTime;
}



