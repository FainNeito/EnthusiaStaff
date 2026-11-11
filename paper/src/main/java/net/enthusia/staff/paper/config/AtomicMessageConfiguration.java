package net.enthusia.staff.paper.config;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class AtomicMessageConfiguration {
    private final AtomicReference<MessageConfigurationSnapshot> active;

    public AtomicMessageConfiguration(MessageConfigurationSnapshot initial) {
        active = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
    }

    public MessageConfigurationSnapshot snapshot() {
        return active.get();
    }

    public boolean replace(
            MessageConfigurationSnapshot expected,
            MessageConfigurationSnapshot candidate
    ) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(candidate, "candidate");
        return active.compareAndSet(expected, candidate);
    }
}
