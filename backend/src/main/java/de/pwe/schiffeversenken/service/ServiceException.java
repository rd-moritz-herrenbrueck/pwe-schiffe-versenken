package de.pwe.schiffeversenken.service;

public class ServiceException extends RuntimeException {

    private final ErrorCode code;

    public ServiceException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
