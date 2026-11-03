package com.electromagnetism.core.runtime;

import java.util.Objects;
import java.util.UUID;

/** Live world/device identity. Replacements must get a new instance; never persist this identity. */
public record DeviceBinding(UUID session, UUID deviceInstance, UUID network, long topologyRevision) {
    public DeviceBinding {
        Objects.requireNonNull(session);
        Objects.requireNonNull(deviceInstance);
        Objects.requireNonNull(network);
        if (topologyRevision < 0) throw new IllegalArgumentException("Negative topology revision");
    }

    public boolean matches(StepContext step) {
        return session.equals(step.session()) && network.equals(step.network())
                && topologyRevision == step.topologyRevision();
    }
}
