package com.electromagnetism.core.model;
/** Signed port work is distinct from stored energy and physical heat. */
public record TransientEnergy(double portJoules,double storedChangeJoules,double dissipatedJoules) {
    public TransientEnergy {
        Numbers.finite("port work",portJoules);Numbers.finite("storage change",storedChangeJoules);
        Numbers.nonNegative("physical dissipation",dissipatedJoules);
    }
    public double residualJoules() { return portJoules-storedChangeJoules-dissipatedJoules; }
    public static double capacitor(double c,double v) { return Numbers.finite("capacitor energy",0.5*c*v*v); }
    public static double inductor(double l,double i) { return Numbers.finite("inductor energy",0.5*l*i*i); }
}
