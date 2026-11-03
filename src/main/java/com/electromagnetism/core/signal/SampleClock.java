package com.electromagnetism.core.signal;

import com.electromagnetism.core.model.Numbers;

/** Explicit mapping of stream sample indices to simulation seconds, never wall-clock time. */
public record SampleClock(double originSeconds, int samplesPerSecond) {
    public SampleClock {
        Numbers.nonNegative("origin", originSeconds);
        if (samplesPerSecond < 1 || samplesPerSecond > 192000)
            throw new IllegalArgumentException("Unsupported sample rate");
    }

    public double timeAt(long index) {
        if (index < 0) throw new IllegalArgumentException("Negative sample index");
        return Numbers.finite("sample time", originSeconds + (double) index / samplesPerSecond);
    }
}
