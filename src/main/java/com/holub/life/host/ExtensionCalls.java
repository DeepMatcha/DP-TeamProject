package com.holub.life.host;

import java.util.Optional;
import java.util.ServiceConfigurationError;

/**
 * The host's one rule for calling code it does not own (plugins, views, listeners):
 * a failure there is reported, never allowed to stop the host. Exceptions and the errors a
 * broken extension typically raises (a failed assertion, a missing or incompatible class,
 * a bad service declaration) are caught; errors of the virtual machine itself still propagate.
 */
final class ExtensionCalls {
    private ExtensionCalls() {}

    /** Runs the call and returns what it threw, or empty when it completed. */
    static Optional<Throwable> failureOf(Runnable call) {
        try {
            call.run();
            return Optional.empty();
        } catch (RuntimeException | AssertionError | LinkageError | ServiceConfigurationError failure) {
            return Optional.of(failure);
        }
    }
}
