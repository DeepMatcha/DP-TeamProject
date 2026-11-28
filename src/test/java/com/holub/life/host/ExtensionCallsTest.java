package com.holub.life.host;

import java.util.Optional;
import java.util.ServiceConfigurationError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtensionCallsTest {
    @Test
    void aCompletedCallReportsNoFailure() {
        assertEquals(Optional.empty(), ExtensionCalls.failureOf(() -> {}));
    }

    @Test
    void failuresOfABrokenExtensionAreReturnedInsteadOfThrown() {
        for (Throwable failure : new Throwable[] {new IllegalStateException("bug"), new AssertionError("assert"),
                new NoClassDefFoundError("missing"), new ServiceConfigurationError("bad provider")}) {
            assertSame(failure, ExtensionCalls.failureOf(() -> sneakyThrow(failure)).orElseThrow());
        }
    }

    @Test
    void virtualMachineErrorsStillPropagate() {
        var fatal = new StackOverflowError();

        assertSame(fatal, assertThrows(StackOverflowError.class,
                () -> ExtensionCalls.failureOf(() -> sneakyThrow(fatal))));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable failure) throws T {
        throw (T) failure;
    }
}
