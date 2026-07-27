package com.kma.knowledge.dto;

import java.time.LocalDateTime;

public record SpaceAclView(Long aclId, Long spaceId, String principalType, String principalValue,
                           String principalDisplayName, String permission, LocalDateTime createTime,
                           String principalStatus, Boolean effective, String ineffectiveReason) {}
