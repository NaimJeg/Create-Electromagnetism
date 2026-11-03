package com.electromagnetism.core.signal;

import com.electromagnetism.core.model.Numbers;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/** Measurements of supplied samples, not assertions about the unsampled analog bandwidth. */
public final class WaveformMeasurements {
    private WaveformMeasurements() {}

    public record Statistics(double startSeconds, double endSeconds, double mean, double rms,
                             double minimum, double maximum) {}

    /** Time-weighted trapezoidal integration of value and squared value; invalid samples break the window. */
    public static Optional<Statistics> statistics(List<WaveformBuffer.Sample> input) {
        List<WaveformBuffer.Sample> samples = List.copyOf(input);
        validateOrder(samples);
        if (samples.size() < 2 || samples.stream().anyMatch(s -> s.quality() != WaveformBuffer.Quality.VALID))
            return Optional.empty();
        double start = samples.getFirst().timeSeconds();
        double end = samples.getLast().timeSeconds();
        double scale = 0, minimum = Double.POSITIVE_INFINITY, maximum = Double.NEGATIVE_INFINITY;
        for (var sample : samples) {
            double v = sample.value().orElseThrow();
            scale = Math.max(scale, Math.abs(v));
            minimum = Math.min(minimum, v);
            maximum = Math.max(maximum, v);
        }
        double mean = 0, square = 0;
        if (scale > 0) for (int i = 1; i < samples.size(); i++) {
            var a = samples.get(i - 1);
            var b = samples.get(i);
            double weight = (b.timeSeconds() - a.timeSeconds()) / (end - start);
            double av = a.value().orElseThrow() / scale, bv = b.value().orElseThrow() / scale;
            mean += weight * (av / 2 + bv / 2);
            square += weight * (av * av / 2 + bv * bv / 2);
        }
        return Optional.of(new Statistics(start, end, Math.max(-1, Math.min(1, mean)) * scale,
                Math.sqrt(Math.min(1, square)) * scale, minimum, maximum));
    }

    /** First strict-to-inclusive threshold crossing, linearly interpolated; never bridges invalid data. */
    public static OptionalDouble edge(List<WaveformBuffer.Sample> input, double threshold, boolean rising) {
        Numbers.finite("threshold", threshold);
        List<WaveformBuffer.Sample> samples = List.copyOf(input);
        validateOrder(samples);
        for (int i = 1; i < samples.size(); i++) {
            var a = samples.get(i - 1);
            var b = samples.get(i);
            if (a.value().isEmpty() || b.value().isEmpty()) continue;
            double av = a.value().getAsDouble(), bv = b.value().getAsDouble();
            if (rising ? av < threshold && bv >= threshold : av > threshold && bv <= threshold) {
                double scale = Math.max(Math.abs(av), Math.max(Math.abs(bv), Math.abs(threshold)));
                double fraction = (threshold / scale - av / scale) / (bv / scale - av / scale);
                return OptionalDouble.of(a.timeSeconds() + fraction * (b.timeSeconds() - a.timeSeconds()));
            }
        }
        return OptionalDouble.empty();
    }

    private static void validateOrder(List<WaveformBuffer.Sample> samples) {
        for (int i = 1; i < samples.size(); i++)
            if (samples.get(i).timeSeconds() <= samples.get(i - 1).timeSeconds())
                throw new IllegalArgumentException("Samples must advance in time");
    }
}
