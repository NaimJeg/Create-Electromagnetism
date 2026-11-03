package com.electromagnetism.core.runtime;

import com.electromagnetism.core.model.TimeStep;
import java.util.Objects;
import java.util.UUID;

/** Identity/time supplied by CEE. A topology revision identifies live wiring, not a mod version. */
public record StepContext(UUID session, UUID network, long topologyRevision, long sequence, TimeStep time) {
    public StepContext {
        Objects.requireNonNull(session);
        Objects.requireNonNull(network);
        Objects.requireNonNull(time);
        if (topologyRevision < 0 || sequence < 0) throw new IllegalArgumentException("Negative revision");
    }
}
