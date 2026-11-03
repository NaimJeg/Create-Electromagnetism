package com.electromagnetism.core.model;

/** Validation at model boundaries; invalid physics must not silently become zero. */
public final class Numbers {
    private Numbers() {}

    public static double finite(String name, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }

    public static double positive(String name, double value) {
        if (finite(name, value) <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    public static double nonNegative(String name, double value) {
        if (finite(name, value) < 0) throw new IllegalArgumentException(name + " must be nonnegative");
        return value;
    }

    public static double range(String name, double value, double minimum, double maximum) {
        if (finite(name, value) < minimum || value > maximum)
            throw new IllegalArgumentException(name + " must be in [" + minimum + ", " + maximum + "]");
        return value;
    }
}
