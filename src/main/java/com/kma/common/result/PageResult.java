package com.kma.common.result;

import java.util.List;

/**
 * Stable paged API contract shared by the administration and portal clients.
 */
public record PageResult<T>(List<T> list, long total, int pageNum, int pageSize) {
    public PageResult {
        list = list == null ? List.of() : List.copyOf(list);
        pageNum = Math.max(1, pageNum);
        pageSize = Math.max(1, pageSize);
        total = Math.max(0, total);
    }
}
