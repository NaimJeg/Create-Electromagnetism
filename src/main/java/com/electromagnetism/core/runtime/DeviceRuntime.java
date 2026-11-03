package com.electromagnetism.core.runtime;

import com.electromagnetism.core.model.*;
import java.util.Objects;
import java.util.Optional;

/**
 * Single-device transaction, not a scheduler or solver. Construct and mutate on the solver thread.
 * Publish immutable snapshots to the world thread; only snapshot() is safe across threads.
 * P, S and T must be immutable. Discard does not roll back CEE's network or clock.
 */
public final class DeviceRuntime<P, S, T> implements AutoCloseable {
    public record Outcome(StepToken token, SolveReport report) {
        public Outcome { Objects.requireNonNull(token); Objects.requireNonNull(report); }
    }

    public record AcceptedStep(StepToken token, EnergySample electrical, SolveReport report) {
        public AcceptedStep {
            Objects.requireNonNull(token);
            Objects.requireNonNull(electrical);
            Objects.requireNonNull(report);
            if (!token.step().time().equals(electrical.interval())
                    || report.status() != SolveReport.Status.CONVERGED)
                throw new IllegalArgumentException("Accepted step has inconsistent interval or status");
        }
    }

    /** State/parameters/lastAccepted stay unchanged on failure; lastOutcome reports the new failure. */
    public record Snapshot<P, S>(P parameters, S state, Optional<AcceptedStep> lastAccepted,
                                  Optional<Outcome> lastOutcome, boolean stale) {
        public Snapshot {
            Objects.requireNonNull(parameters);
            Objects.requireNonNull(state);
            Objects.requireNonNull(lastAccepted);
            Objects.requireNonNull(lastOutcome);
        }
    }

    private final DeviceModel<P, S, T> model;
    private final Thread owner = Thread.currentThread();
    private DeviceBinding binding;
    private volatile Snapshot<P, S> published;
    private StepToken pending;
    private P pendingParameters;
    private T trialState;
    private long lastSequence = -1;
    private double lastPreparedEnd;
    private boolean closed;

    /** Loaded parameters/state enter here directly; storage belongs to the world-side device. */
    public DeviceRuntime(DeviceModel<P, S, T> model, DeviceBinding binding, P initialParameters, S initialState) {
        this.model = Objects.requireNonNull(model);
        this.binding = Objects.requireNonNull(binding);
        Objects.requireNonNull(model.definition());
        published = new Snapshot<>(initialParameters, initialState, Optional.empty(), Optional.empty(), true);
    }

    /** Freeze parameters/history for the entire microstep, including every nonlinear iteration. */
    public StepToken prepareStep(StepContext step, P parameters) {
        requireOpen();
        Objects.requireNonNull(parameters);
        if (pending != null) throw new IllegalStateException("Previous step still pending");
        StepToken token = new StepToken(binding, step);
        if (step.sequence() <= lastSequence || step.time().startSeconds() < lastPreparedEnd)
            throw new IllegalArgumentException("Stale or overlapping step; local retries are unsupported");

        // Even a failed model initialization consumes the attempt: no local replay or catch-up energy.
        lastSequence = step.sequence();
        lastPreparedEnd = step.time().endSeconds();
        pending = token;
        pendingParameters = parameters;
        try {
            trialState = Objects.requireNonNull(model.beginTrial(parameters, published.state(), step.time()));
        } catch (RuntimeException failure) {
            modelFailed(token, failure);
            throw failure;
        }
        return token;
    }

    public Linearization linearize(StepToken token, TerminalVoltages voltages) {
        requirePending(token);
        try {
            requireTerminals(voltages);
            TrialLinearization<T> next = Objects.requireNonNull(model.linearize(pendingParameters,
                    published.state(), token.step().time(), voltages, trialState));
            requireTerminals(next.equations().point());
            trialState = next.trialState();
            return next.equations();
        } catch (RuntimeException failure) {
            modelFailed(token, failure);
            throw failure;
        }
    }

    /** Requires explicit authority acceptance. Unknown/failed outcomes follow the discard path. */
    public void commitStep(DeviceSolution solution) {
        Objects.requireNonNull(solution);
        StepToken token = solution.token();
        requirePending(token);
        if (solution.report().status() != SolveReport.Status.CONVERGED) {
            discardStep(token, solution.report());
            return;
        }
        try {
            TerminalVoltages voltages = solution.voltages().orElseThrow();
            requireTerminals(voltages);
            OperatingPoint point = Objects.requireNonNull(model.evaluate(pendingParameters,
                    published.state(), token.step().time(), voltages));
            if (!point.voltages().equals(voltages)
                    || point.referenceTerminal() != model.definition().referenceTerminal())
                throw new IllegalArgumentException("Physical evaluation changed solution or reference terminal");
            EnergySample electrical = new EnergySample(token.step().time(), point);
            S next = Objects.requireNonNull(model.advance(pendingParameters, published.state(), electrical));
            Snapshot<P, S> nextSnapshot = new Snapshot<>(pendingParameters, next,
                    Optional.of(new AcceptedStep(token, electrical, solution.report())),
                    Optional.of(new Outcome(token, solution.report())), false);
            clearPending();
            published = nextSnapshot;
        } catch (RuntimeException failure) {
            modelFailed(token, failure);
            throw failure;
        }
    }

    public void discardStep(StepToken token, SolveReport reason) {
        requirePending(token);
        Objects.requireNonNull(reason);
        if (reason.status() == SolveReport.Status.CONVERGED)
            throw new IllegalArgumentException("Discard requires a non-accepted outcome");
        publishFailure(token, reason);
        clearPending();
    }

    /**
     * Invalidate old results when live wiring changes. A different network UUID must be fresh,
     * never recycled. Its sequence can restart; the session's physical time cannot rewind.
     * Device replacement/new world session requires a new runtime, not rebind.
     */
    public void rebind(DeviceBinding next) {
        requireOpen();
        Objects.requireNonNull(next);
        if (!binding.session().equals(next.session()) || !binding.deviceInstance().equals(next.deviceInstance()))
            throw new IllegalArgumentException("Replacement requires a new runtime");
        boolean sameNetwork = binding.network().equals(next.network());
        if (sameNetwork && next.topologyRevision() < binding.topologyRevision())
            throw new IllegalArgumentException("Topology cannot rewind within a network instance");
        if (binding.equals(next)) return;
        if (pending != null) discardStep(pending,
                SolveReport.failure(SolveReport.Status.STALE_TOPOLOGY, "Device binding changed"));
        Snapshot<P, S> current = published;
        published = new Snapshot<>(current.parameters(), current.state(),
                current.lastAccepted(), current.lastOutcome(), true);
        binding = next;
        if (!sameNetwork) lastSequence = -1;
    }

    public Snapshot<P, S> snapshot() { return published; }

    /** Solver-thread removal/unload boundary. No pending or future result may advance this device. */
    @Override
    public void close() {
        requireOwner();
        if (closed) return;
        if (pending != null)
            discardStep(pending, SolveReport.failure(SolveReport.Status.CANCELLED, "Device runtime closed"));
        Snapshot<P, S> current = published;
        published = new Snapshot<>(current.parameters(), current.state(),
                current.lastAccepted(), current.lastOutcome(), true);
        closed = true;
    }

    private void modelFailed(StepToken token, RuntimeException failure) {
        publishFailure(token, SolveReport.failure(SolveReport.Status.MODEL_FAILURE,
                failure.getClass().getSimpleName() + ": " + Objects.toString(failure.getMessage(), "")));
        clearPending();
    }

    private void publishFailure(StepToken token, SolveReport report) {
        Snapshot<P, S> current = published;
        published = new Snapshot<>(current.parameters(), current.state(), current.lastAccepted(),
                Optional.of(new Outcome(token, report)), true);
    }

    private void requirePending(StepToken token) {
        requireOpen();
        if (pending == null || !pending.equals(token))
            throw new IllegalStateException("No matching pending device step");
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Wrong device owner thread");
    }

    private void requireOpen() {
        requireOwner();
        if (closed) throw new IllegalStateException("Device runtime is closed");
    }

    private void requireTerminals(TerminalVoltages voltages) {
        if (voltages.size() != model.definition().terminals().size())
            throw new IllegalArgumentException("Terminal count mismatch");
    }

    private void clearPending() {
        pending = null;
        pendingParameters = null;
        trialState = null;
    }
}
