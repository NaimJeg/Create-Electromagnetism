package com.electromagnetism.core.model;

import java.util.List;
import java.util.Objects;

/** Fixed electrical resistance with thermal history; no artificial temperature ceiling. */
public final class ThermalResistorModel
        implements DeviceModel<ThermalResistorModel.Parameters, LumpedThermalModel.State, NoState> {
    public static final DeviceDefinition DEFINITION = new DeviceDefinition(
            "thermal_resistor", List.of("positive", "negative"), 1);

    public record Parameters(double resistanceOhms, LumpedThermalModel.Parameters thermal) {
        public Parameters {
            Numbers.positive("resistance (ohm)", resistanceOhms);
            Numbers.positive("conductance (S)", 1 / resistanceOhms);
            Objects.requireNonNull(thermal);
        }
    }

    @Override
    public DeviceDefinition definition() { return DEFINITION; }

    @Override
    public NoState beginTrial(Parameters p, LumpedThermalModel.State accepted, TimeStep step) {
        return NoState.INSTANCE;
    }

    @Override
    public TrialLinearization<NoState> linearize(Parameters p, LumpedThermalModel.State accepted,
                                                TimeStep step, TerminalVoltages trial, NoState ignored) {
        requireTwoTerminals(trial);
        double g = 1 / p.resistanceOhms();
        double i = trial.between(0, 1) * g;
        return new TrialLinearization<>(new Linearization(trial, new double[]{i, -i},
                new double[][]{{g, -g}, {-g, g}}), NoState.INSTANCE);
    }

    @Override
    public OperatingPoint evaluate(Parameters p, LumpedThermalModel.State accepted,
                                   TimeStep step, TerminalVoltages voltages) {
        requireTwoTerminals(voltages);
        double i = voltages.between(0, 1) / p.resistanceOhms();
        return new OperatingPoint(voltages, new double[]{i, -i}, 1);
    }

    @Override
    public LumpedThermalModel.State advance(Parameters p, LumpedThermalModel.State accepted,
                                            EnergySample electrical) {
        return LumpedThermalModel.advance(p.thermal(), accepted,
                electrical.point().absorbedWatts(), electrical.interval().durationSeconds());
    }

    private static void requireTwoTerminals(TerminalVoltages values) {
        if (values.size() != 2) throw new IllegalArgumentException("Resistor needs two terminals");
    }
}
