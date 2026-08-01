package com.kma.common.security.dto;

import java.util.ArrayList;
import java.util.List;

public record PermissionNode(String permissionCode, String name, String type, String scope,
                             String module, String description, int sortOrder, List<PermissionNode> children) {
    public PermissionNode(String permissionCode, String name, String type, String scope,
                          String module, String description, int sortOrder) {
        this(permissionCode, name, type, scope, module, description, sortOrder, new ArrayList<>());
    }
}
