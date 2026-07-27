package com.kma.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kma_model_profile")
public class ModelProfile {
    @TableId(type = IdType.AUTO)
    private Long profileId;
    private String profileCode;
    private String name;
    private String capability;
    private String provider;
    private String modelName;
    private String baseUrl;
    private Integer dimension;
    private Integer timeoutSeconds;
    private String secretAlias;
    private String fallbackProfileCodes;
    private Boolean enabled;
    private Boolean defaultProfile;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
