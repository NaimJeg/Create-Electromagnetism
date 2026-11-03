package com.electromagnetism.core.model;

import java.util.List;

/**
 * Simplified BJT, terminals B/C/E. NPN and PNP share polarity-normalized equations.
 * Retains the existing series base junction, beta gain, active current clamp and 2-ohm
 * saturation/reverse branch. It has no junction capacitance, charge storage or RF response.
 * Unlike the old independent branch approximation, gm is part of the full Jacobian and
 * numerical leakage stamps do not count as physical current. Numerical regression is pending.
 */
public final class BjtModel implements DeviceModel<BjtModel.Parameters, NoState, BjtModel.TrialState> {
    private static final double THERMAL_VOLTAGE = 0.025;
    private static final double BASE_RESISTANCE = 10;
    private static final double REFERENCE_CURRENT = 0.0025;
    private static final double SATURATION_RESISTANCE = 2;
    private static final double OFF_CONDUCTANCE = 1e-6;
    private final double polarity;
    private final DeviceDefinition definition;

    public BjtModel(boolean pnp) {
        polarity = pnp ? -1 : 1;
        definition = new DeviceDefinition(pnp ? "pnp_transistor" : "npn_transistor", List.of("B", "C", "E"), 2);
    }

    /** beta is dimensionless, vbeOn in V; activeCurrentLimitAmps is protection, not a rating. */
    public record Parameters(double beta, double vbeOn, double activeCurrentLimitAmps) {
        public Parameters {
            Numbers.range("beta", beta, 1, 1000);
            Numbers.range("base junction knee (V)", vbeOn, 0.05, 3);
            Numbers.range("active current limit (A)", activeCurrentLimitAmps, 0.001, 1000);
        }

        double saturationCurrent() { return REFERENCE_CURRENT * Math.exp(-vbeOn / THERMAL_VOLTAGE); }
    }

    /** Numerical iteration memory only: discarded at every completed or rejected step. */
    public record TrialState(double previousBaseVoltage) {
        public TrialState { Numbers.finite("previous base voltage", previousBaseVoltage); }
    }

    @Override
    public DeviceDefinition definition() { return definition; }

    @Override
    public TrialState beginTrial(Parameters p, NoState state, TimeStep step) { return new TrialState(0); }

    @Override
    public TrialLinearization<TrialState> linearize(Parameters p, NoState state, TimeStep step,
                                                   TerminalVoltages trial, TrialState previous) {
        ThreeTerminalEquations.requireThree(trial);
        double base = polarity * trial.between(0, 2);
        double limited = limitBaseVoltage(base, previous.previousBaseVoltage(), p.saturationCurrent());
        TerminalVoltages point = limited == base ? trial : new TerminalVoltages(
                trial.at(2) + polarity * limited, trial.at(1), trial.at(2));
        // Evaluate at the actual representable limited point, not a disconnected cached branch value.
        double actualBase = polarity * point.between(0, 2);
        ThreeTerminalEquations equations = equations(p, actualBase, polarity * point.between(1, 2));
        return new TrialLinearization<>(equations.linearize(point, polarity), new TrialState(actualBase));
    }

    @Override
    public OperatingPoint evaluate(Parameters p, NoState state, TimeStep step, TerminalVoltages voltages) {
        ThreeTerminalEquations.requireThree(voltages);
        return equations(p, polarity * voltages.between(0, 2), polarity * voltages.between(1, 2))
                .evaluate(voltages, polarity);
    }

    @Override
    public NoState advance(Parameters p, NoState state, EnergySample electrical) { return NoState.INSTANCE; }

    private static ThreeTerminalEquations equations(Parameters p, double base, double collector) {
        SeriesJunction.Value junction = SeriesJunction.evaluate(base, p.saturationCurrent(),
                THERMAL_VOLTAGE, BASE_RESISTANCE);
        double drive = Numbers.finite("collector drive", p.beta() * Math.max(0, junction.current()));
        double limit = Math.min(drive, p.activeCurrentLimitAmps());
        double current;
        double gm;
        double go;
        if (limit < 1e-9) {
            current = collector * OFF_CONDUCTANCE;
            gm = 0;
            go = OFF_CONDUCTANCE;
        } else if (collector >= limit * SATURATION_RESISTANCE) {
            current = limit;
            // At the clamp boundary choose the clamped-side derivative.
            gm = drive >= p.activeCurrentLimitAmps() ? 0 : p.beta() * junction.derivative();
            go = 0;
        } else {
            current = collector / SATURATION_RESISTANCE;
            gm = 0;
            go = 1 / SATURATION_RESISTANCE;
        }
        return new ThreeTerminalEquations(junction.current(), current, junction.derivative(), gm, go);
    }

    private static double limitBaseVoltage(double voltage, double previous, double saturationCurrent) {
        double critical = THERMAL_VOLTAGE * Math.log(THERMAL_VOLTAGE / (saturationCurrent * Math.sqrt(2)));
        if (previous < critical && voltage > critical) return critical;
        double delta = Numbers.finite("base iteration delta", voltage - previous);
        if (voltage > critical && Math.abs(delta) > 2 * THERMAL_VOLTAGE) {
            double ratio = Numbers.finite("base iteration ratio", delta / THERMAL_VOLTAGE);
            if (ratio <= -1) return critical;
            return Numbers.finite("limited base voltage", previous + THERMAL_VOLTAGE * Math.log1p(ratio));
        }
        return voltage;
    }
}
