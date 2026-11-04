package com.electromagnetism.integration.cee;
import com.electromagnetism.core.model.*;
import com.electromagnetism.core.model.TransientIntegration.*;
import com.electromagnetism.integration.cee.electrical.*;
import com.george_vi.electroenergetics.simulation.util.SparseMatrix;
import java.util.*;

public final class TransientChecks {
    static void near(double a,double b,double tolerance) {
        if(!Double.isFinite(a)||Math.abs(a-b)>tolerance) throw new AssertionError(a+" != "+b+" tolerance "+tolerance);
    }
    public static void main(String[] args) {
        for(var method:Method.values()) {
            var c=new History(0,0,false);var l=new History(0,0,false);double dt=0.001;
            for(int k=0;k<1000;k++) {
                var cc=TransientIntegration.capacitor(1,dt,c,method);
                double v=(1-cc.source())/(1+cc.conductance());c=new History(v,cc.current(v),true);
                var ll=TransientIntegration.inductor(1,dt,l,method);
                v=(1-ll.source())/(1+ll.conductance());l=new History(v,ll.current(v),true);
            }
            near(c.voltage(),1-Math.exp(-1),method==Method.TRAPEZOIDAL?1e-6:0.0002);
            near(l.current(),1-Math.exp(-1),method==Method.TRAPEZOIDAL?1e-6:0.0002);
        }
        double omega=2*Math.PI,dt=1.0/200;var c=new History(1,0,true);var l=new History(1,0,true);
        double inductance=1/(omega*omega),e0=0.5,maxDrift=0;
        for(int k=0;k<20000;k++) {
            var cc=TransientIntegration.capacitor(1,dt,c,Method.TRAPEZOIDAL);
            var ll=TransientIntegration.inductor(inductance,dt,l,Method.TRAPEZOIDAL);
            double v=-(cc.source()+ll.source())/(cc.conductance()+ll.conductance());
            c=new History(v,cc.current(v),true);l=new History(v,ll.current(v),true);
            maxDrift=Math.max(maxDrift,Math.abs(TransientEnergy.capacitor(1,v)+TransientEnergy.inductor(inductance,l.current())-e0)/e0);
        }
        near(maxDrift,0,1e-5);
        double phase=Math.atan2(l.current()/omega,c.voltage());near(phase,0,0.1);
        // Parallel C with a series R-L branch: known underdamped analytic response.
        var rc=new History(1,0,true);var rl=new History(1,0,true);
        double resistance=0.2,stepRlc=0.001;
        for(int k=0;k<10000;k++) {
            var cc=TransientIntegration.capacitor(1,stepRlc,rc,Method.TRAPEZOIDAL);
            var ll=TransientIntegration.inductor(1,stepRlc,rl,Method.TRAPEZOIDAL);
            double g=ll.conductance()/(1+resistance*ll.conductance());
            double source=ll.source()/(1+resistance*ll.conductance());
            double v=-(cc.source()+source)/(cc.conductance()+g),i=g*v+source;
            rc=new History(v,cc.current(v),true);rl=new History(v-resistance*i,i,true);
            double t=(k+1)*stepRlc,wd=Math.sqrt(0.99);
            double expected=Math.exp(-0.1*t)*(Math.cos(wd*t)+0.1/wd*Math.sin(wd*t));
            near(v,expected,1e-6);
        }
        for(double supply:new double[]{-12,0.05,0.5,1,12,100}) {
            var diode=new DiodeCompanion(new History(0,0,false),0.001,Method.TRAPEZOIDAL);
            var a=new SparseMatrix(3);a.set(0,0,0.001);a.set(1,2,1);a.set(2,1,1);
            var result=NonlinearNetworkSolver.solve(a,new double[]{supply/1000,0,0},List.of(new NonlinearNetworkAccess.Entry(diode,new int[]{0,1})));
            if(!result.accepted()) throw new AssertionError("Diode failed at "+supply);
            double v=result.voltages()[0];near((supply-v)/1000,DiodeCompanion.staticCurrent(v)+diode.capacitiveCurrent(v),1e-8);
        }
        // A small-signal diode and hold capacitor share the same matrix and load.
        double detected=0;
        for(int k=0;k<4000;k++) {
            double source=0.3*Math.sin(2*Math.PI*k/40.0),step=0.0001;
            var diode=new DiodeCompanion(new History(0,0,false),step,Method.BACKWARD_EULER);
            var a=new SparseMatrix(4);double conductance=1e-6/step;
            a.set(0,0,0.001);a.set(1,1,conductance+1e-5);a.set(2,3,1);a.set(3,2,1);
            var result=NonlinearNetworkSolver.solve(a,new double[]{source/1000,conductance*detected,0,0},List.of(new NonlinearNetworkAccess.Entry(diode,new int[]{0,1})));
            if(!result.accepted()) throw new AssertionError("Detector failed");
            detected=result.voltages()[1];
        }
        if(!(detected>0.001 && detected<0.3)) throw new AssertionError("Detector output "+detected);
        int[] publishes={0};
        var scheduler=new AdaptiveTransient<Integer>((state,t)->{
            if(t.durationSeconds()>0.01) throw new AdaptiveTransient.Rejected("injected");
            return new AdaptiveTransient.Candidate<>(state+1,new double[]{1},new double[]{0});
        },(next,t)->publishes[0]++,true);
        var accepted=scheduler.advance(0,0,0.05);
        if(!accepted.complete()||publishes[0]!=accepted.accepted()) throw new AssertionError("Probe was published");
        var failed=new AdaptiveTransient<Integer>((state,t)->{throw new AdaptiveTransient.Rejected("singular");},(next,t)->{throw new AssertionError("Published rejection");},true).advance(7,0,0.05);
        if(failed.complete()||failed.state()!=7||failed.time()!=0) throw new AssertionError("Rejected step changed history");
        var invalid=new AdaptiveTransient<Integer>((state,t)->new AdaptiveTransient.Candidate<>(99,new double[]{Double.NaN},new double[]{0}),
                (next,t)->{throw new AssertionError("Published NaN");},true).advance(7,0,0.05);
        if(invalid.complete()||invalid.state()!=7||invalid.time()!=0) throw new AssertionError("NaN changed accepted state");
        System.out.println("Transient checks passed: RC/RL analytic, LC 100 cycles drift="+maxDrift+", phase="+phase+", diode sweep/detector, retry isolation");
    }
}
