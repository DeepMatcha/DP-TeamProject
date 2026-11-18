package com.holub.life.api;
import java.util.Objects;
/** Unsolicited host failure, e.g. automatic tick; not a simulation state. */
public record HostError(String operation, String message, Throwable cause) {
    public HostError {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(message);
        Objects.requireNonNull(cause);
    }
}
