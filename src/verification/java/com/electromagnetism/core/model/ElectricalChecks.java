package com.electromagnetism.core.model;

/** Regression checks for externally supplied device parameters. */
public final class ElectricalChecks {
    public static void main(String[] args) {
        var ranges = new ElectricalLimits.Range[] {
                ElectricalLimits.BETA, ElectricalLimits.VBE, ElectricalLimits.COLLECTOR_CURRENT,
                ElectricalLimits.GATE_THRESHOLD, ElectricalLimits.ON_RESISTANCE, ElectricalLimits.DRAIN_CURRENT
        };
        for (var range : ranges) {
            for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
                require(range.sanitize(invalid) == range.defaultValue(), "Non-finite parameter must restore default");
            require(range.sanitize(-Double.MAX_VALUE) == range.minimum(), "Lower bound");
            require(range.sanitize(Double.MAX_VALUE) == range.maximum(), "Upper bound");
            require(range.sanitize(range.defaultValue()) == range.defaultValue(), "Default must survive round trip");
            double middle = (range.minimum() + range.maximum()) / 2;
            require(range.sanitize(middle) == middle, "Valid user setting must survive round trip");
        }
        System.out.println("Electrical parameter checks passed");
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
