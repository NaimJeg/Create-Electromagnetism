package com.electromagnetism.integration.cee.electrical;
import com.electromagnetism.core.model.*;
import com.electromagnetism.core.model.TransientIntegration.*;
/** CEE orientation is forward from the SECOND bridge terminal to the first. */
public final class DiodeCompanion implements SolverDevice {
    public static final double SATURATION=1e-9,THERMAL=0.05,CAPACITANCE=1e-11;
    private final Companion capacitor;
    private double candidate,limited;
    public DiodeCompanion(History history,double dt,Method method) { capacitor=TransientIntegration.capacitor(CAPACITANCE,dt,history,method); }
    public double capacitiveCurrent(double v) { return capacitor.current(v); }
    public static double staticCurrent(double v) { return SATURATION*Math.expm1(v/THERMAL); }
    @Override public void beginMicrostep() { candidate=limited=0; }
    @Override public void candidate(TerminalVoltages v) { candidate=v.between(0,1); }
    @Override public Linearization linearize() {
        limited+=Math.clamp(candidate-limited,-0.2,0.2);
        double g=SATURATION/THERMAL*Math.exp(limited/THERMAL)+capacitor.conductance();
        double i=staticCurrent(limited)+capacitor.current(limited);
        return new Linearization(new TerminalVoltages(limited,0),new double[]{i,-i},new double[][]{{g,-g},{-g,g}});
    }
    @Override public OperatingPoint evaluate(TerminalVoltages v) {
        double i=staticCurrent(v.between(0,1))+capacitor.current(v.between(0,1));
        return new OperatingPoint(v,new double[]{i,-i},1);
    }
}
