package com.electromagnetism.core.radio;
import com.electromagnetism.core.model.Numbers;
/** Explicit one-frame energy storage. Unused reservations dissipate; no implicit catch-up. */
public final class EnergyReservoir {
    private final double capacity;
    private double stored;
    public EnergyReservoir(double capacity) { Numbers.positive("capacity",capacity);this.capacity=capacity; }
    public double deposit(double joules) { Numbers.nonNegative("energy",joules);double accepted=Math.min(joules,capacity-stored);stored+=accepted;return joules-accepted; }
    public double reserve(double maximum) { Numbers.nonNegative("reservation",maximum);double result=Math.min(maximum,stored);stored-=result;return result; }
    public double stored() { return stored; }
    public void restore(double joules) { Numbers.range("stored energy",joules,0,capacity);stored=joules; }
}
