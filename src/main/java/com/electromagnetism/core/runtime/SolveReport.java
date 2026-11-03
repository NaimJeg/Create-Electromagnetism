package com.electromagnetism.core.runtime;

import com.electromagnetism.core.model.Numbers;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Missing diagnostics stay missing; a finite output alone is not convergence evidence. */
public record SolveReport(Status status, OptionalInt iterations, OptionalDouble residual,
                          String detail) {
    public enum Status {
        CONVERGED, ITERATION_LIMIT, SINGULAR, NON_FINITE, UNKNOWN,
        STALE_TOPOLOGY, CANCELLED, MODEL_FAILURE
    }

    public SolveReport {
        Objects.requireNonNull(status);
        Objects.requireNonNull(iterations);
        Objects.requireNonNull(residual);
        Objects.requireNonNull(detail);
        if (iterations.isPresent() && iterations.getAsInt() < 0)
            throw new IllegalArgumentException("Negative iteration count");
        if (residual.isPresent()) Numbers.nonNegative("residual", residual.getAsDouble());
    }

    public static SolveReport unknown(String detail) {
        return failure(Status.UNKNOWN, detail);
    }

    public static SolveReport failure(Status status, String detail) {
        if (status == Status.CONVERGED) throw new IllegalArgumentException("Expected failure status");
        return new SolveReport(status, OptionalInt.empty(), OptionalDouble.empty(), detail);
    }
}
