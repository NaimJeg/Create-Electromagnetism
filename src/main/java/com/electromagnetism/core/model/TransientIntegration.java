package com.electromagnetism.core.model;
/** Norton companions use passive sign convention: i = conductance*v + source. */
public final class TransientIntegration {
    private TransientIntegration() {}
    public enum Method { BACKWARD_EULER, TRAPEZOIDAL }
    public record History(double voltage,double current,boolean initialized) {
        public History { Numbers.finite("voltage",voltage);Numbers.finite("current",current); }
    }
    public record Companion(double conductance,double source) {
        public Companion { Numbers.positive("conductance",conductance);Numbers.finite("source",source); }
        public double current(double voltage) { return Numbers.finite("current",conductance*voltage+source); }
    }
    public static Companion capacitor(double c,double dt,History old,Method method) {
        Numbers.positive("capacitance",c);Numbers.positive("dt",dt);
        boolean trap=method==Method.TRAPEZOIDAL && old.initialized();
        double g=(trap?2:1)*c/dt;
        return new Companion(g,-g*old.voltage()-(trap?old.current():0));
    }
    public static Companion inductor(double l,double dt,History old,Method method) {
        Numbers.positive("inductance",l);Numbers.positive("dt",dt);
        boolean trap=method==Method.TRAPEZOIDAL && old.initialized();
        double g=dt/((trap?2:1)*l);
        return new Companion(g,old.current()+(trap?g*old.voltage():0));
    }
}
