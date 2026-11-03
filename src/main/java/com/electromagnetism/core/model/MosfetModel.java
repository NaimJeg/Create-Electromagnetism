package com.electromagnetism.core.model;

import java.util.List;

/**
 * Simplified N/P MOS, terminals G/D/S. Keeps the existing gate leakage, square-law regions,
 * reverse channel extension and piecewise body diode. Full gm coupling replaces branch-order
 * dependence. Saturation-only limiting intentionally retains the old discontinuity at the
 * ohmic/saturation boundary when the clamp is active. No charge storage or RF response;
 * numerical regression and suitability for a particular CEE step size remain unverified.
 */
public final class MosfetModel implements DeviceModel<MosfetModel.Parameters, NoState, NoState> {
    private static final double GATE_CONDUCTANCE = 1e-9;
    private static final double OFF_CONDUCTANCE = 1e-6;
    private static final double BODY_DIODE_VOLTAGE = 0.7;
    private static final double BODY_DIODE_CONDUCTANCE = 20;
    private static final double REFERENCE_OVERDRIVE = 10;
    private final double polarity;
    private final DeviceDefinition definition;

    public MosfetModel(boolean pChannel) {
        polarity = pChannel ? -1 : 1;
        definition = new DeviceDefinition(pChannel ? "p_mosfet" : "n_mosfet", List.of("G", "D", "S"), 2);
    }

    /** Threshold in V, on-resistance in ohm at 10 V overdrive; limit is active protection in A. */
    public record Parameters(double thresholdVolts, double onResistanceOhms, double activeCurrentLimitAmps) {
        public Parameters {
            Numbers.range("threshold (V)", thresholdVolts, 0.1, 100);
            Numbers.range("on resistance (ohm)", onResistanceOhms, 0.001, 100);
            Numbers.range("active current limit (A)", activeCurrentLimitAmps, 0.001, 1000);
        }
    }

    @Override
    public DeviceDefinition definition() { return definition; }

    @Override
    public NoState beginTrial(Parameters p, NoState state, TimeStep step) { return NoState.INSTANCE; }

    @Override
    public TrialLinearization<NoState> linearize(Parameters p, NoState state, TimeStep step,
                                                TerminalVoltages trial, NoState ignored) {
        ThreeTerminalEquations.requireThree(trial);
        return new TrialLinearization<>(equations(p, polarity * trial.between(0, 2), polarity * trial.between(1, 2))
                .linearize(trial, polarity), NoState.INSTANCE);
    }

    @Override
    public OperatingPoint evaluate(Parameters p, NoState state, TimeStep step, TerminalVoltages voltages) {
        ThreeTerminalEquations.requireThree(voltages);
        return equations(p, polarity * voltages.between(0, 2), polarity * voltages.between(1, 2))
                .evaluate(voltages, polarity);
    }

    @Override
    public NoState advance(Parameters p, NoState state, EnergySample electrical) { return NoState.INSTANCE; }

    private static ThreeTerminalEquations equations(Parameters p, double gate, double drain) {
        double overdrive = Numbers.finite("gate overdrive", gate - p.thresholdVolts());
        double k = 1 / (p.onResistanceOhms() * REFERENCE_OVERDRIVE);
        double current;
        double gm;
        double go;
        if (overdrive <= 0) {
            current = drain * OFF_CONDUCTANCE;
            gm = 0;
            go = OFF_CONDUCTANCE;
        } else if (drain < overdrive) {
            current = k * drain * (overdrive - drain / 2);
            gm = k * drain;
            go = k * (overdrive - drain);
        } else {
            double unlimited = Numbers.finite("saturation current", 0.5 * k * overdrive * overdrive);
            current = Math.min(unlimited, p.activeCurrentLimitAmps());
            gm = unlimited >= p.activeCurrentLimitAmps() ? 0 : k * overdrive;
            go = 0; // No fictitious physical leakage to hide a singular network.
        }
        // At the diode knee choose the nonconducting-side derivative.
        if (drain < -BODY_DIODE_VOLTAGE) {
            current += (drain + BODY_DIODE_VOLTAGE) * BODY_DIODE_CONDUCTANCE;
            go += BODY_DIODE_CONDUCTANCE;
        }
        return new ThreeTerminalEquations(gate * GATE_CONDUCTANCE, current, GATE_CONDUCTANCE, gm, go);
    }
}
