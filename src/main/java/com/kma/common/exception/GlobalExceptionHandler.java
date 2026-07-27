package com.kma.common.exception;

import com.kma.common.result.ApiResult;
import com.kma.common.result.KmaResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将内部异常转换为稳定且不泄漏实现细节的 API 响应。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(KmaException.class)
    public ResponseEntity<ApiResult<Void>> handleKma(KmaException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getCode());
        if (status == null) status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(withTrace(ApiResult.fail(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(withTrace(ApiResult.fail(KmaResultCode.PERMISSION_DENIED.getCode(), "权限不足")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .findFirst().map(error -> error.getField() + ": " + error.getDefaultMessage())
            .orElse("请求参数错误");
        return ResponseEntity.badRequest().body(withTrace(ApiResult.fail(400, message)));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(withTrace(ApiResult.fail(405, "请求方法不支持")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("KMA 未处理异常: {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(withTrace(ApiResult.fail(500, "服务内部错误")));
    }

    private <T> ApiResult<T> withTrace(ApiResult<T> result) {
        result.setTraceId(MDC.get("traceId"));
        return result;
    }
}

