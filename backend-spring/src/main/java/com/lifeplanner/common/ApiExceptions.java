package com.lifeplanner.common;

/** Application exceptions that map to clean HTTP responses. */
public final class ApiExceptions {

    private ApiExceptions() {}

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }

    /** Raised when the FastAPI planner cannot be reached. We surface this
     *  honestly rather than pretending a plan was produced. */
    public static class PlannerUnavailableException extends RuntimeException {
        public PlannerUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
