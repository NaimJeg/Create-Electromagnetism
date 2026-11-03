package com.electromagnetism.core.runtime;

import com.electromagnetism.core.model.TerminalVoltages;

import java.util.Objects;
import java.util.Optional;

/** Adapter attestation: convergence must come from the authority, never from finite voltages. */
public record DeviceSolution(StepToken token, SolveReport report, Optional<TerminalVoltages> voltages) {
    public DeviceSolution {
        Objects.requireNonNull(token);
        Objects.requireNonNull(report);
        Objects.requireNonNull(voltages);
        if (report.status() == SolveReport.Status.CONVERGED && voltages.isEmpty())
            throw new IllegalArgumentException("Accepted solve requires a final solution");
    }
}
