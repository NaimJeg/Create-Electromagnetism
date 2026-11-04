package com.electromagnetism.integration.cee.electrical;
/** Audited branch: tick only sets coefficients; afterTick only records output samples.
 * Sources are held constant over a CEE microstep. Unknown implementations are rejected. */
public interface FrozenMicroBranch {
    /** Pure source evaluation at electrical candidate time. Default is a held external sample. */
    default void coefficientsAt(com.electromagnetism.core.model.TimeStep interval) {}
}
