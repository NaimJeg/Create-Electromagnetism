package com.electromagnetism.core.model;
import java.util.function.*;
/** Candidate states must be immutable. Probes never publish. */
public final class AdaptiveTransient<S> {
    public record Candidate<S>(S state,double[] voltages,double[] currents) {}
    @FunctionalInterface public interface Stepper<S> { Candidate<S> solve(S state,TimeStep interval); }
    public record Result<S>(S state,double time,int accepted,int attempts,String failure) {
        public boolean complete() { return failure.isEmpty(); }
    }
    public static final class Rejected extends RuntimeException {
        public Rejected(String message) { super(message); }
    }
    private final Stepper<S> stepper;
    private final BiConsumer<Candidate<S>,TimeStep> publish;
    private final boolean adaptive;
    private int attempts;
    public AdaptiveTransient(Stepper<S> stepper,BiConsumer<Candidate<S>,TimeStep> publish,boolean adaptive) {
        this.stepper=stepper;this.publish=publish;this.adaptive=adaptive;
    }
    private Candidate<S> probe(S state,TimeStep interval) {
        if(attempts>=4096) throw new Rejected("Candidate budget exhausted");
        attempts++;
        var candidate=stepper.solve(state,interval);
        if(candidate==null || candidate.state()==null || !finite(candidate.voltages()) || !finite(candidate.currents()))
            throw new Rejected("Non-finite or missing candidate");
        return candidate;
    }
    public Result<S> advance(S state,double start,double duration) {
        double end=start+duration,time=start,h=duration;int accepted=0;
        while(time<end) {
            h=Math.min(h,end-time);boolean done=false;String reason="";
            for(int shrink=0;shrink<=12;shrink++) {
                try {
                    Candidate<S> whole=probe(state,new TimeStep(time,h));
                    Candidate<S> chosen=whole;
                    if(adaptive) {
                        var first=probe(state,new TimeStep(time,h/2));
                        var second=probe(first.state(),new TimeStep(time+h/2,h/2));
                        if(!close(whole.voltages(),second.voltages(),1e-6) || !close(whole.currents(),second.currents(),1e-9))
                            throw new Rejected("Transient error exceeds tolerance");
                        chosen=second;
                    }
                    // All model evaluations and candidate preparation have finished.
                    publish.accept(chosen,new TimeStep(time,h));
                    state=chosen.state();time=(h==end-time)?end:time+h;accepted++;done=true;break;
                } catch(Rejected failure) { reason=failure.getMessage(); }
                if(!adaptive || attempts>=4096) break;
                h/=2;
            }
            if(!done) return new Result<>(state,time,accepted,attempts,reason);
            h=Math.min(h*2,end-time);
        }
        return new Result<>(state,time,accepted,attempts,"");
    }
    private static boolean finite(double[] values) {
        if(values==null) return false;
        for(double value:values) if(!Double.isFinite(value)) return false;
        return true;
    }
    private static boolean close(double[] a,double[] b,double absolute) {
        if(a.length!=b.length) return false;
        for(int i=0;i<a.length;i++)
            if(!Double.isFinite(a[i]) || !Double.isFinite(b[i]) || Math.abs(a[i]-b[i])>absolute+1e-4*Math.max(Math.abs(a[i]),Math.abs(b[i]))) return false;
        return true;
    }
}
