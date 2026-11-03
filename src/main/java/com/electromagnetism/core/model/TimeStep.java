package com.electromagnetism.core.model;

/** A physical interval supplied by the electrical authority, in seconds. */
public record TimeStep(double startSeconds, double durationSeconds) {
    public TimeStep {
        Numbers.nonNegative("start time", startSeconds);
        Numbers.positive("duration", durationSeconds);
        Numbers.finite("end time", startSeconds + durationSeconds);
        if (startSeconds + durationSeconds <= startSeconds)
            throw new IllegalArgumentException("Duration below clock precision");
    }

    public double endSeconds() { return startSeconds + durationSeconds; }
}
