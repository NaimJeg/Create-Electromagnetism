package com.electromagnetism.core.model;

/** Shared assembly for control/output/reference terminals. No solver or world dependencies. */
record ThreeTerminalEquations(double controlCurrent, double outputCurrent,
                               double controlConductance, double transconductance, double outputConductance) {
    ThreeTerminalEquations {
        Numbers.finite("control current", controlCurrent);
        Numbers.finite("output current", outputCurrent);
        Numbers.finite("control conductance", controlConductance);
        Numbers.finite("transconductance", transconductance);
        Numbers.finite("output conductance", outputConductance);
    }

    private double[] currents(double polarity) {
        double control = polarity * controlCurrent;
        double output = polarity * outputCurrent;
        return new double[]{control, output, -(control + output)};
    }

    Linearization linearize(TerminalVoltages point, double polarity) {
        double g = controlConductance;
        double gm = transconductance;
        double go = outputConductance;
        // Both voltage and current reverse for P devices, so the Jacobian signs cancel.
        return new Linearization(point, currents(polarity), new double[][]{
                {g, 0, -g}, {gm, go, -gm - go}, {-g - gm, -go, g + gm + go}
        });
    }

    OperatingPoint evaluate(TerminalVoltages voltages, double polarity) {
        return new OperatingPoint(voltages, currents(polarity), 2);
    }

    static void requireThree(TerminalVoltages voltages) {
        if (voltages.size() != 3) throw new IllegalArgumentException("Three terminals required");
    }
}
