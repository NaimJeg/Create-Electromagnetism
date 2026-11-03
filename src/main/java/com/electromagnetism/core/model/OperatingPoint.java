package com.electromagnetism.core.model;

import java.util.Objects;

/** Physical currents at the unmodified final voltages; no numerical limiting or g-min stamps. */
public final class OperatingPoint {
    private final TerminalVoltages voltages;
    private final double[] currents;
    private final int referenceTerminal;
    private final double absorbedWatts;

    public OperatingPoint(TerminalVoltages voltages, double[] currents, int referenceTerminal) {
        this.voltages = Objects.requireNonNull(voltages);
        if (currents.length != voltages.size() || referenceTerminal < 0 || referenceTerminal >= currents.length)
            throw new IllegalArgumentException("Invalid terminal mapping");
        this.currents = currents.clone();
        this.referenceTerminal = referenceTerminal;
        Conservation.requireZeroSum("terminal currents (KCL)", this.currents);
        double power = 0;
        for (int terminal = 0; terminal < currents.length; terminal++)
            power = Numbers.finite("absorbed power", power
                    + voltages.between(terminal, referenceTerminal) * this.currents[terminal]);
        absorbedWatts = power;
    }

    public TerminalVoltages voltages() { return voltages; }
    public int referenceTerminal() { return referenceTerminal; }
    public double current(int terminal) { return currents[terminal]; }
    public double absorbedWatts() { return absorbedWatts; }
}
