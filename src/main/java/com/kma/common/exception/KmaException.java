package com.kma.common.exception;

import com.kma.common.result.KmaResultCode;
import lombok.Getter;

/** KMA 业务异常。 */
@Getter
public class KmaException extends RuntimeException {
    private final Integer code;

    public KmaException(String message) {
        this(KmaResultCode.INTERNAL_ERROR.getCode(), message);
    }

    public KmaException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public KmaException(KmaResultCode resultCode) {
        this(resultCode.getCode(), resultCode.getMessage());
    }

    public KmaException(KmaResultCode resultCode, String message) {
        this(resultCode.getCode(), message);
    }

    public KmaException(String message, Throwable cause) {
        super(message, cause);
        this.code = KmaResultCode.INTERNAL_ERROR.getCode();
    }

    public KmaException(KmaResultCode resultCode, Throwable cause) {
        super(resultCode.getMessage(), cause);
        this.code = resultCode.getCode();
    }
}

