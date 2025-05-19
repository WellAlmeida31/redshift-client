package com.wellalmeida31.redshift_client.exception;

public class NoStackTraceThrowable extends Throwable {
    public NoStackTraceThrowable(String message) {
        super(message, (Throwable) null, false, false);
    }
}
