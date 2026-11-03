package com.electromagnetism.core.signal;

import java.util.List;
import java.util.OptionalDouble;

/** Dependency-free regression entry point; run with scripts/test-signal.ps1. */
public final class SignalChecks {
    public static void main(String[] args) {
        var viewport = new ScopeViewport(0.02, 0.1, 0.04, 0);
        near(viewport.relativeTime(0), -1);
        near(viewport.relativeTime(1), 0);
        near(viewport.horizontalFraction(1000, 1000), 1);
        near(viewport.horizontalFraction(999.5, 1000), 0.5);
        near(viewport.horizontalFraction(9.5, 10), 0.5);
        near(viewport.verticalFraction(0.04), 0.5);
        near(viewport.verticalFraction(0.12), 0);
        var shifted = new ScopeViewport(0.02, 0.1, 0, -0.5);
        near(shifted.relativeTime(1), -0.5);
        near(shifted.horizontalFraction(999.5, 1000), 1);
        rejects(() -> new ScopeViewport(0, 1, 0, 0));
        var fitted = new ScopeViewport(1, 60, 0, -100).fitHistory(1.6, 0.003125);
        near(fitted.relativeTime(0), -1.6);
        near(fitted.relativeTime(1), 0);
        var panned = new ScopeViewport(1, 0.05, 0, -100).fitHistory(1.6, 0.003125);
        near(panned.relativeTime(0), -1.6);
        near(panned.relativeTime(1), -1.1);
        near(new ScopeViewport(1, 1e-6, 0, 0).fitHistory(1.6, 0.003125).secondsPerDivision(), 0.0003125);
        var microphone = new MicrophoneTransducer(1, 0.05, 600, 0.04);
        near(microphone.openCircuitVoltage(1), 0.04);
        near(microphone.openCircuitVoltage(-1), -0.04);
        near(microphone.outputCurrent(0.4, 0.01), 0.01 / 600);
        rejects(() -> microphone.openCircuitVoltage(1.1));
        SampleClock clock = new SampleClock(3, 8000);
        near(clock.timeAt(8000), 4);
        AudioFrame first = AudioFrame.sine(clock, 0, 1000, 100, 0.5, 0);
        AudioFrame second = AudioFrame.sine(clock, 1000, 1000, 100, 0.5, 0);
        AudioFrame whole = AudioFrame.sine(clock, 0, 2000, 100, 0.5, 0);
        for (int i = 0; i < 1000; i++) near(second.at(i), whole.at(i + 1000));
        double[] mutable = {0.2};
        AudioFrame immutable = new AudioFrame(clock, 0, mutable);
        mutable[0] = 0.9;
        near(immutable.at(0), 0.2);
        rejects(() -> new AudioFrame(clock, 0, new double[]{Double.NaN}));
        rejects(() -> AudioFrame.sine(clock, 0, 10, 4000, 1, 0));
        AudioInputBuffer input = new AudioInputBuffer(clock, 1);
        input.offer(first);
        input.offer(second);
        check(input.droppedSamples() == 1000 && input.size() == 1);
        rejects(() -> input.offer(second));
        check(input.poll().orElseThrow() == second && input.poll().isEmpty());
        input.offer(new AudioFrame(clock, 2010, new double[]{0}));
        check(input.missingSamples() == 10);

        WaveformBuffer waveform = new WaveformBuffer(801);
        AudioFrame sine = AudioFrame.sine(clock, 0, 801, 100, 0.5, 0);
        for (int i = 0; i < sine.size(); i++)
            waveform.append(WaveformBuffer.Sample.valid(clock.timeAt(i), sine.at(i)));
        near(WaveformMeasurements.frequency(waveform.snapshot(), 0, true).orElseThrow(), 100);
        var stats = WaveformMeasurements.statistics(waveform.snapshot()).orElseThrow();
        near(stats.mean(), 0);
        near(stats.rms(), 0.5 / Math.sqrt(2));
        near(stats.maximum(), 0.5);
        near(stats.minimum(), -0.5);

        var uneven = List.of(WaveformBuffer.Sample.valid(0, 0),
                WaveformBuffer.Sample.valid(1, 2), WaveformBuffer.Sample.valid(4, 2));
        var weighted = WaveformMeasurements.statistics(uneven).orElseThrow();
        near(weighted.mean(), 1.75);
        near(weighted.rms(), Math.sqrt(3.5));
        near(WaveformMeasurements.edge(uneven, 1, true).orElseThrow(), 0.5);
        var gap = new WaveformBuffer.Sample(0.5, OptionalDouble.empty(), WaveformBuffer.Quality.UNCONVERGED);
        var broken = List.of(uneven.get(0), gap, uneven.get(1));
        check(WaveformMeasurements.statistics(broken).isEmpty());
        check(WaveformMeasurements.edge(broken, 1, true).isEmpty());
        check(WaveformMeasurements.frequency(broken, 1, true).isEmpty());
        WaveformBuffer bounded = new WaveformBuffer(2);
        bounded.append(uneven.get(0));
        bounded.append(gap);
        var snapshot = bounded.snapshot();
        bounded.append(uneven.get(1));
        check(bounded.overwrittenSamples() == 1 && snapshot.getFirst().timeSeconds() == 0);
        rejects(() -> bounded.append(uneven.get(1)));
        System.out.println("Signal checks passed: PCM, clock, overflow, gaps, RMS, trigger, snapshots");
    }

    private static void near(double actual, double expected) {
        if (Math.abs(actual - expected) > 1e-10) throw new AssertionError(actual + " != " + expected);
    }
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection");
    }
}
