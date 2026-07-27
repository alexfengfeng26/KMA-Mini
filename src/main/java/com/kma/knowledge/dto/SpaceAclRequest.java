package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 知识空间 ACL 授权请求
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@Schema(name = "SpaceAclRequest", description = "SpaceAclRequest 数据模型")
public class SpaceAclRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "空间 ID 不能为空")
    private Long spaceId;

    @NotBlank(message = "主体类型不能为空")
    @Pattern(regexp = "user|role|org", message = "主体类型必须是 user、role 或 org")
    @Size(max = 16, message = "主体类型长度不能超过 16")
    private String principalType;

    @NotBlank(message = "主体标识不能为空")
    @Size(max = 64, message = "主体标识长度不能超过 64")
    private String principalValue;

    @NotBlank(message = "权限不能为空")
    @Pattern(regexp = "read|ingest|admin", message = "权限必须是 read、ingest 或 admin")
    @Size(max = 16, message = "权限长度不能超过 16")
    private String permission;
}



