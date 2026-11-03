package com.electromagnetism.core.model;

import java.util.Objects;

/** One interval, using endpoint rectangular integration. Positive energy means absorption. */
public record EnergySample(TimeStep interval, OperatingPoint point) {
    public EnergySample {
        Objects.requireNonNull(interval);
        Objects.requireNonNull(point);
        Numbers.finite("absorbed energy", point.absorbedWatts() * interval.durationSeconds());
    }

    public double absorbedJoules() { return point.absorbedWatts() * interval.durationSeconds(); }
}
