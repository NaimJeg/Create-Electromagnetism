package com.electromagnetism.integration.cee.electrical;

import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;

/**
 * Build-baseline adapter for CEE 1.21.1-1.1.4's microtick API.
 * Uses the previous microtick voltage, not a Newton iteration or an accepted core runtime step.
 * Branch orientation and coupled-device behavior still require circuit-level validation.
 */
public abstract class CeeNonlinearBranch extends MicroTickingElectricalProperties {
    protected double conductance;
    private double lastVoltage;

    protected abstract double gMin();

    protected abstract void tick(double positiveVoltage, double negativeVoltage, boolean first);

    @Override
    public final void tick(double[] voltages, int firstNode, int secondNode, int microtick, int microticks) {
        tick(lastVoltage, 0, false);
        conductance = Math.max(gMin(), conductance);
        if (!Double.isFinite(conductance) || conductance <= 0 || !Double.isFinite(currentSource))
            throw new IllegalStateException("Invalid CEE branch coefficients");
        resistance = 1 / conductance;
    }

    @Override
    public final void afterTick(double[] voltages, int firstNode, int secondNode, int microtick, int microticks) {
        // Same matrix layout and polarity as the pinned CEE DiodeProperties implementation.
        lastVoltage = voltages[secondNode * microticks + microtick]
                - voltages[firstNode * microticks + microtick];
    }

    @Override
    public final double conductance() {
        return conductance > 0 ? conductance : gMin();
    }

    @Override
    public final double resistance() {
        return 1 / conductance();
    }
}
