package com.electromagnetism.core.model;

/** Shared parameter bounds for persisted settings and electrical models. */
public final class ElectricalLimits {
    public static final Range BETA = new Range(1, 1000, 100);
    public static final Range VBE = new Range(0.05, 3, 0.7);
    public static final Range COLLECTOR_CURRENT = new Range(0.001, 1000, 1);
    public static final Range GATE_THRESHOLD = new Range(0.1, 100, 2);
    public static final Range ON_RESISTANCE = new Range(0.001, 100, 0.1);
    public static final Range DRAIN_CURRENT = new Range(0.001, 1000, 10);

    private ElectricalLimits() {}

    public record Range(double minimum, double maximum, double defaultValue) {
        public double sanitize(double value) {
            return Double.isFinite(value) ? Math.clamp(value, minimum, maximum) : defaultValue;
        }
    }
}
