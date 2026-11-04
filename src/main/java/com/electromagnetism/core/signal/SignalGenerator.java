package com.electromagnetism.core.signal;

/** Finite, bounded laboratory source settings. Amplitude is peak volts. */
public record SignalGenerator(Wave wave, double frequency, double amplitude, double offset, boolean enabled) {
    public enum Wave { SINE, SQUARE, TRIANGLE, DC }
    public SignalGenerator {
        if (wave == null) wave = Wave.SINE;
        frequency = bound(frequency, 0.1, 10, 2);
        amplitude = bound(amplitude, 0, 10, 1);
        offset = bound(offset, -10, 10, 0);
    }
    private static double bound(double v, double min, double max, double fallback) {
        return Double.isFinite(v) ? Math.clamp(v, min, max) : fallback;
    }
    public double voltage(double seconds) {
        if (!enabled || !Double.isFinite(seconds)) return 0;
        double phase = seconds * frequency;
        phase -= Math.floor(phase);
        return offset + amplitude * switch (wave) {
            case SINE -> Math.sin(2 * Math.PI * phase);
            case SQUARE -> phase < 0.5 ? 1 : -1;
            case TRIANGLE -> 1 - 4 * Math.abs(phase - 0.5);
            case DC -> 0;
        };
    }
}
