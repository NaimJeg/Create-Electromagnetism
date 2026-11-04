package com.electromagnetism.core.model;
import com.electromagnetism.core.runtime.*;
import java.util.*;

public final class ModelRuntimeChecks {
    static final TimeStep STEP = new TimeStep(0, 0.05);
    static void near(double a, double b) { if (!Double.isFinite(a) || !Double.isFinite(b) || Math.abs(a-b)>1e-6*Math.max(1,Math.abs(b))) throw new AssertionError(a+" != "+b); }
    static void rejects(Runnable r) { try { r.run(); } catch (IllegalArgumentException | IllegalStateException e) { return; } throw new AssertionError("Expected rejection"); }
    public static void main(String[] args) {
        var thermal = new LumpedThermalModel.Parameters(10, 2, 295.15);
        var initial = new LumpedThermalModel.State(295.15);
        var whole = LumpedThermalModel.advance(thermal, initial, 20, 1);
        var split = initial;
        for (int i=0;i<100;i++) split=LumpedThermalModel.advance(thermal,split,20,0.01);
        near(whole.temperatureKelvin(),split.temperatureKelvin());
        near(whole.temperatureKelvin(),295.15+10*(1-Math.exp(-0.2)));
        var p = new ThermalResistorModel.Parameters(10,thermal);
        var model = new ThermalResistorModel();
        var binding = new DeviceBinding(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),0);
        var runtime = new DeviceRuntime<>(model,binding,p,initial);
        var token = runtime.prepareStep(context(binding,0,STEP),p);
        for(int i=0;i<20;i++) runtime.linearize(token,new TerminalVoltages(10,0));
        near(runtime.snapshot().state().temperatureKelvin(),initial.temperatureKelvin());
        var accepted = new SolveReport(SolveReport.Status.CONVERGED,OptionalInt.empty(),OptionalDouble.empty(),"test authority");
        var solution = new DeviceSolution(token,accepted,Optional.of(new TerminalVoltages(10,0)));
        runtime.commitStep(solution);
        near(runtime.snapshot().lastAccepted().orElseThrow().electrical().absorbedJoules(),0.5);
        near(runtime.snapshot().state().temperatureKelvin(),LumpedThermalModel.advance(thermal,initial,10,0.05).temperatureKelvin());
        rejects(()->runtime.commitStep(solution));
        var before=runtime.snapshot().state();
        var failed=runtime.prepareStep(context(binding,1,new TimeStep(0.05,0.05)),p);
        runtime.discardStep(failed,SolveReport.unknown("injected failure"));
        near(runtime.snapshot().state().temperatureKelvin(),before.temperatureKelvin());
        if(!runtime.snapshot().stale()) throw new AssertionError("Failure must be stale");
        var retry=runtime.prepareStep(new StepContext(binding.session(),binding.network(),0,1,1,new TimeStep(0.05,0.025)),p);
        runtime.commitStep(new DeviceSolution(retry,accepted,Optional.of(new TerminalVoltages(10,0))));
        rejects(()->runtime.commitStep(new DeviceSolution(failed,accepted,Optional.of(new TerminalVoltages(10,0)))));
        rejects(()->runtime.prepareStep(context(binding,3,new TimeStep(0.05,0.025)),p));
        var pending=runtime.prepareStep(context(binding,4,new TimeStep(0.1,0.05)),p);
        runtime.rebind(new DeviceBinding(binding.session(),binding.deviceInstance(),binding.network(),1));
        rejects(()->runtime.commitStep(new DeviceSolution(pending,accepted,Optional.of(new TerminalVoltages(10,0)))));
        runtime.close();
        rejects(()->runtime.prepareStep(context(binding,3,new TimeStep(0.15,0.05)),p));
        for(boolean mirror:new boolean[]{false,true}) {
            double sign=mirror?-1:1;
            checkModel(new MosfetModel(mirror),new MosfetModel.Parameters(2,0.1,100),NoState.INSTANCE,new TerminalVoltages(sign*4,sign*1,0));
            checkModel(new MosfetModel(mirror),new MosfetModel.Parameters(2,0.1,100),NoState.INSTANCE,new TerminalVoltages(sign*4,sign*5,0));
            var bjt=new BjtModel(mirror);
            checkModel(bjt,new BjtModel.Parameters(100,0.7,10),new BjtModel.TrialState(0.7),new TerminalVoltages(sign*0.7,sign*5,0));
        }
        System.out.println("Model/runtime checks passed: thermal analytic/split, energy, iteration isolation, discard, topology, close, Jacobians");
    }
    static StepContext context(DeviceBinding b,long seq,TimeStep t) { return new StepContext(b.session(),b.network(),b.topologyRevision(),seq,t); }
    static <P,T> void checkModel(DeviceModel<P,NoState,T> m,P p,T trial,TerminalVoltages v) {
        var linear=m.linearize(p,NoState.INSTANCE,STEP,v,trial).equations();
        var point=linear.point();
        var op=m.evaluate(p,NoState.INSTANCE,STEP,point);
        double sum=0; for(int i=0;i<point.size();i++) { sum+=op.current(i); near(linear.current(i),op.current(i)); }
        near(sum,0);
        for(int c=0;c<point.size();c++) {
            double[] plus=new double[point.size()],minus=new double[point.size()];
            for(int i=0;i<point.size();i++) plus[i]=minus[i]=point.at(i);
            plus[c]+=1e-6; minus[c]-=1e-6;
            var a=m.evaluate(p,NoState.INSTANCE,STEP,new TerminalVoltages(plus));
            var b=m.evaluate(p,NoState.INSTANCE,STEP,new TerminalVoltages(minus));
            for(int r=0;r<point.size();r++) near((a.current(r)-b.current(r))/2e-6,linear.derivative(r,c));
        }
    }
}
