package com.electromagnetism.core.signal;

import com.electromagnetism.core.model.Numbers;
import java.util.Objects;

/** Immutable mono normalized PCM. Stream identity/authorization belongs to the transport adapter. */
public final class AudioFrame {
    public static final int MAX_SAMPLES = 4096;
    private final SampleClock clock;
    private final long firstSample;
    private final double[] samples;

    public AudioFrame(SampleClock clock, long firstSample, double[] samples) {
        this.clock = Objects.requireNonNull(clock);
        Objects.requireNonNull(samples);
        if (firstSample < 0 || samples.length < 1 || samples.length > MAX_SAMPLES)
            throw new IllegalArgumentException("Invalid audio frame bounds");
        Math.addExact(firstSample, samples.length);
        this.firstSample = firstSample;
        this.samples = samples.clone();
        for (double sample : this.samples) Numbers.range("PCM", sample, -1, 1);
    }

    public SampleClock clock() { return clock; }
    public long firstSample() { return firstSample; }
    public long endSample() { return firstSample + samples.length; }
    public int size() { return samples.length; }
    public double at(int index) { return samples[index]; }

    /** Deterministic input for calibration; frequency must be strictly below Nyquist. */
    public static AudioFrame sine(SampleClock clock, long firstSample, int size,
                                  double frequencyHz, double amplitude, double phaseRadians) {
        Objects.requireNonNull(clock);
        Numbers.nonNegative("frequency", frequencyHz);
        Numbers.range("amplitude", amplitude, 0, 1);
        Numbers.finite("phase", phaseRadians);
        if (frequencyHz >= clock.samplesPerSecond() / 2.0 || size < 1 || size > MAX_SAMPLES
                || firstSample < 0) throw new IllegalArgumentException("Invalid test signal");
        Math.addExact(firstSample, size);
        double[] samples = new double[size];
        for (int i = 0; i < size; i++)
            samples[i] = amplitude * Math.sin(2 * Math.PI
                    * ((firstSample + i) * (frequencyHz / clock.samplesPerSecond()) % 1) + phaseRadians);
        return new AudioFrame(clock, firstSample, samples);
    }
}
