package com.electromagnetism.integration.cee.electrical;
import com.electromagnetism.core.model.*;
/** Iteration-local equations; no accepted history may change during these calls. */
public interface SolverDevice {
    void beginMicrostep();
    Linearization linearize();
    void candidate(TerminalVoltages voltages);
    OperatingPoint evaluate(TerminalVoltages voltages);
}
