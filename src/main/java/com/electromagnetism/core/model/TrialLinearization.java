package com.electromagnetism.core.model;

import java.util.Objects;

/** Immutable numerical iteration memory; never persisted or advanced as physical history. */
public record TrialLinearization<T>(Linearization equations, T trialState) {
    public TrialLinearization {
        Objects.requireNonNull(equations);
        Objects.requireNonNull(trialState);
    }
}
