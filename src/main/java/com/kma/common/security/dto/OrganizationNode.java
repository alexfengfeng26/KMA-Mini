package com.kma.common.security.dto;

import java.util.ArrayList;
import java.util.List;

public record OrganizationNode(Long orgId, String orgCode, String name, Long parentId, String status,
                               boolean builtIn, int sortOrder, long memberCount,
                               List<OrganizationNode> children) {
    public OrganizationNode(Long orgId, String orgCode, String name, Long parentId, String status,
                            boolean builtIn, int sortOrder, long memberCount) {
        this(orgId, orgCode, name, parentId, status, builtIn, sortOrder, memberCount, new ArrayList<>());
    }
}
