package com.electromagnetism.core.model;
/**
 * Pure device equations. P, S and T must be immutable, validated value objects.
 * Implementations must not mutate parameters, accepted history, or the world.
 * Linearization can run any number of times without advancing physical time.
 */
public interface DeviceModel<P, S, T> {
    DeviceDefinition definition();
    T beginTrial(P parameters, S accepted, TimeStep step);

    TrialLinearization<T> linearize(P parameters, S accepted, TimeStep step,
                                    TerminalVoltages trial, T trialState);

    /** Physical evaluation at the supplied voltages; must not apply iteration limiters. */
    OperatingPoint evaluate(P parameters, S accepted, TimeStep step, TerminalVoltages voltages);

    /** Called once for an accepted electrical step, using its final voltages. */
    S advance(P parameters, S accepted, EnergySample acceptedElectrical);
}
