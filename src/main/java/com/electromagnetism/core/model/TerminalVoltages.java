package com.electromagnetism.core.model;

/** Immutable candidate voltages in volts, all relative to the same solver reference. */
public final class TerminalVoltages {
    private final double[] values;

    public TerminalVoltages(double... values) {
        if (values.length < 2) throw new IllegalArgumentException("At least two terminals required");
        this.values = values.clone();
        for (double value : this.values) Numbers.finite("terminal voltage", value);
    }

    public int size() { return values.length; }
    public double at(int terminal) { return values[terminal]; }
    public double between(int positive, int negative) {
        return Numbers.finite("voltage difference", at(positive) - at(negative));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TerminalVoltages that && java.util.Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() { return java.util.Arrays.hashCode(values); }
}
