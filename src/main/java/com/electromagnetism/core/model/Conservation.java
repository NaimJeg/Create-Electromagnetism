package com.electromagnetism.core.model;

/** Structural roundoff check, not an electrical solver convergence criterion. */
final class Conservation {
    private Conservation() {}

    static void requireZeroSum(String name, double[] values) {
        double scale = 0;
        for (double value : values) scale = Math.max(scale, Math.abs(Numbers.finite(name, value)));
        if (scale == 0) return;
        double sum = 0;
        for (double value : values) sum += value / scale;
        if (Math.abs(sum) > 1e-12 * values.length)
            throw new IllegalArgumentException(name + " is not conserved");
    }
}
