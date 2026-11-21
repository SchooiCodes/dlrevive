package dev.dlrevive.integrations;

/** Thrown when a video ID cannot be resolved to a downloadable audio stream. */
public final class ResolutionException extends Exception {

    public ResolutionException(String message) {
        super(message);
    }

    public ResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
