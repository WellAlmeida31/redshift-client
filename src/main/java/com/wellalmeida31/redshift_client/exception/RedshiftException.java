package com.wellalmeida31.redshift_client.exception;

public class RedshiftException extends RuntimeException {
    public RedshiftException(String message) {
        super(message);
    }
    public RedshiftException(String message, Throwable throwable) {
        super(message, throwable);
    }
    public RedshiftException(Throwable throwable) {
        super(throwable);
    }
}
