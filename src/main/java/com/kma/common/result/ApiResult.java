package com.kma.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.slf4j.MDC;

import java.io.Serializable;

/** KMA 统一 API 响应。 */
@Data
@Schema(description = "统一响应结果")
public class ApiResult<T> implements Serializable {
    private Integer code;
    private String message;
    private T data;
    private Long timestamp = System.currentTimeMillis();
    private String traceId;

    public ApiResult() {}

    public ApiResult(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = MDC.get("traceId");
    }

    public static <T> ApiResult<T> success() {
        return new ApiResult<>(KmaResultCode.SUCCESS.getCode(), KmaResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(KmaResultCode.SUCCESS.getCode(), KmaResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> ApiResult<T> success(String message) {
        return new ApiResult<>(KmaResultCode.SUCCESS.getCode(), message, null);
    }

    public static <T> ApiResult<T> success(String message, T data) {
        return new ApiResult<>(KmaResultCode.SUCCESS.getCode(), message, data);
    }

    public static <T> ApiResult<T> fail(String message) {
        return new ApiResult<>(KmaResultCode.FAIL.getCode(), message, null);
    }

    public static <T> ApiResult<T> fail(Integer code, String message) {
        return new ApiResult<>(code, message, null);
    }
}

