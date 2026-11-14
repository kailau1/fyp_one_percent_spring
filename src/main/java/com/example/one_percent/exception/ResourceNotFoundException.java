package com.example.one_percent.exception;

/**
 * Thrown when a requested resource doesn't exist <em>or</em> doesn't belong to the
 * authenticated user. Both cases deliberately map to 404 so the API never reveals
 * whether another user's resource exists.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
