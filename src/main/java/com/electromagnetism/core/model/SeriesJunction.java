package com.electromagnetism.core.model;

/**
 * Exponential junction in series with resistance: V = Vt*ln(1+I/Is) + R*I.
 * The positive auxiliary root w + ln(w) = z avoids exponentiating large forward voltages.
 * This local scalar evaluation does not advance time or solve any network nodes.
 */
final class SeriesJunction {
    private SeriesJunction() {}

    record Value(double current, double derivative) {}

    static Value evaluate(double voltage, double saturationCurrent, double thermalVoltage, double resistance) {
        Numbers.finite("junction voltage", voltage);
        double a = saturationCurrent * resistance / thermalVoltage;
        if (voltage == 0)
            return new Value(0, saturationCurrent / (thermalVoltage + resistance * saturationCurrent));
        double z = Numbers.finite("junction argument", Math.log(a) + a + voltage / thermalVoltage);
        double w = omega(z);
        double current = Numbers.finite("junction current", thermalVoltage / resistance * w - saturationCurrent);
        double derivative = Numbers.nonNegative("junction derivative", (w / (1 + w)) / resistance);
        return new Value(current, derivative);
    }

    private static double omega(double z) {
        if (z <= 0) {
            double w = Math.exp(z);
            if (w == 0) return 0; // Representable reverse saturation, not a numerical g-min.
            for (int iteration = 0; iteration < 32; iteration++) {
                double exponential = Math.exp(z - w);
                double next = w - (w - exponential) / (1 + exponential);
                if (Math.abs(next - w) <= 8 * Math.ulp(w)) return next;
                w = next;
            }
        } else {
            double w = z >= 1 ? z : 1;
            for (int iteration = 0; iteration < 32; iteration++) {
                double next = w - (w + Math.log(w) - z) / (1 + 1 / w);
                if (Math.abs(next - w) <= 8 * Math.ulp(w)) return next;
                w = next;
            }
        }
        throw new ArithmeticException("Series junction evaluation did not converge");
    }
}
