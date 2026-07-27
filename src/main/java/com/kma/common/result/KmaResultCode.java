package com.kma.common.result;

import lombok.Getter;

/** KMA 稳定业务错误码。 */
@Getter
public enum KmaResultCode {
    SUCCESS(200, "操作成功"),
    FAIL(400, "操作失败"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未认证"),
    PERMISSION_DENIED(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务内部错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用");

    private final Integer code;
    private final String message;

    KmaResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}

