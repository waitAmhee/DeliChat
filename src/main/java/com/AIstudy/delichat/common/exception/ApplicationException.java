package com.AIstudy.delichat.common.exception;

import org.springframework.http.HttpStatus;

public class ApplicationException extends RuntimeException {

    private final HttpStatus status;

    public ApplicationException(String message, Throwable cause) {
        this(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }

    public ApplicationException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
