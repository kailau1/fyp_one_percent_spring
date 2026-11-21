package com.example.one_percent.exception;

/** The AI couldn't do something just now (quota, outage, or an unusable reply): a 503 with this message. */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
