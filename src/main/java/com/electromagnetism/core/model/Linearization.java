package com.electromagnetism.core.model;

/**
 * Local multi-terminal Newton contribution: I(V) ~= I(V0) + J * (V - V0).
 * Currents (A) are positive INTO the device; J[row][column] is dIrow/dVcolumn (S).
 * Models must conserve current and be invariant to a common voltage offset.
 * Full coupling is retained; an adapter must reject unsupported off-diagonal terms,
 * never silently replace them with independent two-terminal branches. If point differs from
 * the solver's candidate (numerical limiting), the adapter must require another iteration,
 * not attest convergence on this limited point.
 */
public final class Linearization {
    private final TerminalVoltages point;
    private final double[] currents;
    private final double[][] jacobian;

    public Linearization(TerminalVoltages point, double[] currents, double[][] jacobian) {
        this.point = java.util.Objects.requireNonNull(point);
        int size = point.size();
        if (currents.length != size || jacobian.length != size)
            throw new IllegalArgumentException("Terminal count mismatch");
        this.currents = currents.clone();
        this.jacobian = new double[size][];
        for (int row = 0; row < size; row++) {
            Numbers.finite("current", this.currents[row]);
            if (jacobian[row].length != size) throw new IllegalArgumentException("Jacobian must be square");
            this.jacobian[row] = jacobian[row].clone();
            for (double value : this.jacobian[row]) Numbers.finite("Jacobian", value);
            Conservation.requireZeroSum("Jacobian row (common voltage invariance)", this.jacobian[row]);
        }
        Conservation.requireZeroSum("terminal currents (KCL)", this.currents);
        for (int column = 0; column < size; column++) {
            double[] derivatives = new double[size];
            for (int row = 0; row < size; row++) derivatives[row] = this.jacobian[row][column];
            Conservation.requireZeroSum("Jacobian column (KCL)", derivatives);
        }
    }

    public TerminalVoltages point() { return point; }
    public double current(int terminal) { return currents[terminal]; }
    public double derivative(int currentTerminal, int voltageTerminal) {
        return jacobian[currentTerminal][voltageTerminal];
    }

    /**
     * I = J V + b. This is b, NOT the opposite-sign Norton source convention.
     * Use terminal zero as a local reference to avoid multiplying large common-mode offsets.
     */
    public double affineCurrent(int terminal) {
        double value = currents[terminal];
        for (int column = 0; column < point.size(); column++)
            value = Numbers.finite("affine current", value - jacobian[terminal][column] * point.between(column, 0));
        return value;
    }
}
