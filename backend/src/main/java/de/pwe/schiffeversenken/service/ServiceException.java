package de.pwe.schiffeversenken.service;

public class ServiceException extends RuntimeException {

    private final ErrorCode code;
    private final Long retryAfterSeconds;

    public ServiceException(ErrorCode code, String message) {
        this(code, message, null);
    }

    /** {@code retryAfterSeconds}: Wartezeit bei {@link ErrorCode#RATE_LIMITED}, sonst {@code null}. */
    public ServiceException(ErrorCode code, String message, Long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode code() {
        return code;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
