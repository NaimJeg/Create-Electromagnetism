package com.electromagnetism.core.signal;

import com.electromagnetism.core.model.Numbers;
import com.electromagnetism.core.runtime.DeviceRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/** Bounded timestamped observations. One channel/session per instance; snapshots are immutable. */
public final class WaveformBuffer {
    public enum Quality { VALID, STALE, UNCONVERGED, MISSING }

    public record Sample(double timeSeconds, OptionalDouble value, Quality quality) {
        public Sample {
            Numbers.nonNegative("sample time", timeSeconds);
            Objects.requireNonNull(value);
            Objects.requireNonNull(quality);
            if (value.isPresent()) Numbers.finite("sample", value.getAsDouble());
            if ((quality == Quality.VALID) != value.isPresent())
                throw new IllegalArgumentException("Only valid observations carry a value");
        }
        public static Sample valid(double time, double value) {
            return new Sample(time, OptionalDouble.of(value), Quality.VALID);
        }
    }

    private final Sample[] ring;
    private int start;
    private int size;
    private long overwritten;

    public WaveformBuffer(int capacity) {
        if (capacity < 2 || capacity > 1048576) throw new IllegalArgumentException("Invalid capacity");
        ring = new Sample[capacity];
    }

    public synchronized void append(Sample sample) {
        Objects.requireNonNull(sample);
        if (size > 0 && sample.timeSeconds() <= ring[(start + size - 1) % ring.length].timeSeconds())
            throw new IllegalArgumentException("Samples must advance in time");
        if (size == ring.length) {
            start = (start + 1) % ring.length;
            size--;
            overwritten++;
        }
        ring[(start + size++) % ring.length] = sample;
    }

    /** Call at each accepted microstep, not once per tick. This is an ideal debug probe (no loading). */
    public void appendDifferential(DeviceRuntime.AcceptedStep step, int positive, int negative) {
        Objects.requireNonNull(step);
        append(Sample.valid(step.electrical().interval().endSeconds(),
                step.electrical().point().voltages().between(positive, negative)));
    }

    public synchronized List<Sample> snapshot() {
        List<Sample> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(ring[(start + i) % ring.length]);
        return List.copyOf(result);
    }

    public synchronized long overwrittenSamples() { return overwritten; }
}
