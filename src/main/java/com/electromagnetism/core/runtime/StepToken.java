package com.electromagnetism.core.runtime;

import java.util.Objects;

/** Identifies one device's pending participation in a CEE microstep. */
public record StepToken(DeviceBinding binding, StepContext step) {
    public StepToken {
        Objects.requireNonNull(binding);
        Objects.requireNonNull(step);
        if (!binding.matches(step)) throw new IllegalArgumentException("Step does not match live binding");
    }
}
