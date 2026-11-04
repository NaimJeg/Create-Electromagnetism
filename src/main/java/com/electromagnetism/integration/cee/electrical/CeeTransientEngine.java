package com.electromagnetism.integration.cee.electrical;

import com.electromagnetism.core.model.*;
import com.electromagnetism.core.model.TransientIntegration.*;
import com.george_vi.electroenergetics.foundation.electrical_properties.*;
import com.george_vi.electroenergetics.foundation.nodes.DirectionalNodeConnection;
import com.george_vi.electroenergetics.simulation.*;
import com.george_vi.electroenergetics.simulation.electrical_properties.*;
import com.george_vi.electroenergetics.simulation.simulator.*;
import com.george_vi.electroenergetics.simulation.util.*;
import it.unimi.dsi.fastutil.objects.*;
import java.util.*;

/** One worker-owned clock and a shared acceptance barrier across all CEE islands. */
public final class CeeTransientEngine {
    public record Report(double start,double end,int attempts,String failure,List<TransientEnergy> energy) {
        public boolean complete() { return failure.isEmpty(); }
    }
    private record Branch(int a,int b,MicroTickingElectricalProperties property,double parameter) {}
    private record State(double[] values,Map<MicroTickingElectricalProperties,History> history,
                         Map<StatefulDevice<?,?,?>,Object> models,Map<Network,double[]> solutions,
                         Map<MicroTickingElectricalProperties,TransientEnergy> energy,
                         Map<MicroTickingElectricalProperties,double[]> coefficients) {}
    private final Map<MicroTickingElectricalProperties,History> histories=new WeakHashMap<>();
    private final Map<MicroTickingElectricalProperties,Double> parameters=new WeakHashMap<>();
    private double time;
    private int topology;
    private Report report=new Report(0,0,0,"",List.of());
    public CeeTransientEngine(double initialTime) { time=initialTime; }
    public Report report() { return report; }
    public SimulationResults run(CircuitBuilder builder,List<Network> networks,
            com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData sd,int count,boolean precise) {
        double start=time;int[] solves={0};
        double[] samples=new double[builder.allNodes().size()*count];
        var currents=new Object2DoubleOpenHashMap<DirectionalNodeConnection>();
        var branches=new ArrayList<Branch>();
        var modelStates=new IdentityHashMap<StatefulDevice<?,?,?>,Object>();
        var initial=new IdentityHashMap<MicroTickingElectricalProperties,History>();
        var coefficients=new IdentityHashMap<MicroTickingElectricalProperties,double[]>();
        var mna=new IdentityHashMap<Network,double[]>();
        String failure="";List<TransientEnergy> energy=List.of();
        int signature=builder.allNodes().stream().mapToInt(n->Objects.hash(n.node,n.groundConductance,
                n.adjacency.int2ObjectEntrySet().stream().mapToInt(e->Objects.hash(builder.getNode(e.getIntKey()).node,e.getValue().getClass())).sum())).sum();
        boolean reset=signature!=topology;topology=signature;
        for(var n:networks) mna.put(n,((CeeNetworkAccess)n).electromagnetism$last());
        try {
            for(var d:StatefulDevice.devices(builder)) modelStates.put(d,d.acceptedState());
            for(var entry:builder.microTickers.long2ObjectEntrySet()) {
                var p=entry.getValue();double parameter=0;History old;
                // Exact classes only: subclasses can introduce untracked state.
                if(p.getClass()==CapacitorProperties.class) {
                    var c=(CapacitorProperties)p;parameter=c.capacitance;old=new History(c.lastVoltage,0,false);
                } else if(p.getClass()==InductorProperties.class) {
                    var l=(InductorProperties)p;parameter=l.inductance;old=new History(0,l.lastCurrent,false);
                } else if(p.getClass()==DiodeProperties.class) {
                    parameter=DiodeCompanion.CAPACITANCE;old=new History(((DiodeProperties)p).lastVoltage,0,false);
                } else if(p instanceof FrozenMicroBranch) old=new History(0,0,false);
                else throw new AdaptiveTransient.Rejected("Unsupported transactional branch: "+p.getClass().getName());
                History saved=histories.get(p);
                if(!reset && Objects.equals(parameters.get(p),parameter) && saved!=null && (p instanceof InductorProperties?saved.current()==old.current():saved.voltage()==old.voltage())) old=saved;
                if(!(p instanceof FrozenMicroBranch) && (!(parameter>0)||!Double.isFinite(parameter)))
                    throw new AdaptiveTransient.Rejected("Invalid storage parameter: "+parameter);
                parameters.put(p,parameter);initial.put(p,old);coefficients.put(p,new double[]{p.resistance,p.currentSource,p.voltageSource});
                branches.add(new Branch(DataPacker.unpackFirstI(entry.getLongKey()),DataPacker.unpackSecondI(entry.getLongKey()),p,parameter));
            }
            State state=new State(new double[builder.allNodes().size()],initial,modelStates,Map.of(),Map.of(),coefficients);
            Method method=precise?Method.TRAPEZOIDAL:Method.BACKWARD_EULER;
            final State[] accepted={state};
            var scheduler=new AdaptiveTransient<State>((old,interval)-> {
                try {
                    return candidate(builder,networks,branches,old,interval,method,solves);
                } catch(AdaptiveTransient.Rejected rejected) { throw rejected; }
                  catch(RuntimeException invalid) { throw new AdaptiveTransient.Rejected("Candidate failed: "+invalid); }
            },(next,interval)-> {
                // Candidate generation completed before any persistent assignments.
                accepted[0]=next.state();publish(branches,next.state());
                coefficients.putAll(next.state().coefficients());
                for(var e:next.state().solutions().entrySet()) mna.put(e.getKey(),e.getValue());
                time=interval.endSeconds();
            },precise);
            for(int step=0;step<count;step++) {
                // Audited external sources are sampled once and held over this original microstep.
                for(var branch:branches) if(branch.property() instanceof FrozenMicroBranch)
                    branch.property().tick(samples,step,count,branch.a(),branch.b());
                var result=scheduler.advance(accepted[0],time,0.05/count);
                state=result.state();
                for(int node=0;node<state.values().length;node++) samples[node*count+step]=state.values()[node];
                if(!result.complete()) { failure=result.failure();break; }
                for(var branch:branches) if(branch.property() instanceof FrozenMicroBranch)
                    branch.property().afterTick(samples,branch.a(),branch.b(),step,count);
            }
            energy=List.copyOf(state.energy().values());
            if(failure.isEmpty()) {
                for(var entry:state.solutions().entrySet()) ((CeeNetworkAccess)entry.getKey()).electromagnetism$currents(entry.getValue(),currents);
            }
        } catch(AdaptiveTransient.Rejected rejected) { failure=rejected.getMessage(); }
          catch(RuntimeException exception) { failure="Transient batch failed: "+exception; }
        finally {
            for(var e:coefficients.entrySet()) { var p=e.getKey();var c=e.getValue();p.resistance=c[0];p.currentSource=c[1];p.voltageSource=c[2]; }
            for(var n:networks) ((CeeNetworkAccess)n).electromagnetism$last(mna.get(n));
            NonlinearNetworkSolver.take();
        }
        report=new Report(start,time,solves[0],failure,energy);
        if(!failure.isEmpty()) CeeStepEvidence.reject(failure);
        return new SimulationResults(samples,count,currents,builder,sd);
    }
    private AdaptiveTransient.Candidate<State> candidate(CircuitBuilder builder,List<Network> networks,List<Branch> branches,
            State old,TimeStep interval,Method method,int[] solves) {
        var companions=new IdentityHashMap<MicroTickingElectricalProperties,Companion>();
        var diodes=new IdentityHashMap<MicroTickingElectricalProperties,DiodeCompanion>();
        for(var b:branches) {
            var p=b.property();var h=old.history().get(p);Companion companion=null;
            if(p instanceof FrozenMicroBranch frozen) frozen.coefficientsAt(interval);
            if(p instanceof CapacitorProperties) companion=TransientIntegration.capacitor(b.parameter(),interval.durationSeconds(),h,method);
            if(p instanceof InductorProperties) companion=TransientIntegration.inductor(b.parameter(),interval.durationSeconds(),h,method);
            if(companion!=null) { companions.put(p,companion);p.resistance=1/companion.conductance();p.currentSource=companion.source(); }
            if(p instanceof DiodeProperties) {
                p.resistance=Double.POSITIVE_INFINITY;p.currentSource=0;
                diodes.put(p,new DiodeCompanion(h,interval.durationSeconds(),method));
            }
        }
        for(var d:old.models().keySet()) d.prepare(old.models().get(d),interval);
        double[] values=old.values().clone();
        var solutions=new IdentityHashMap<Network,double[]>();
        var allCurrents=new ArrayList<Double>();
        for(var n:networks) {
            if(solves[0]>=4096) throw new AdaptiveTransient.Rejected("Network solve budget exhausted");
            solves[0]++;
            n.formMatrix();NonlinearNetworkSolver.take();
            var entries=new ArrayList<>(((NonlinearNetworkAccess)n).electromagnetism$models());
            var access=(CeeNetworkAccess)n;
            for(var b:branches) if(diodes.containsKey(b.property())) {
                int a=access.electromagnetism$index(b.a()),bb=access.electromagnetism$index(b.b());
                if(a>=0 && bb>=0) entries.add(new NonlinearNetworkAccess.Entry(diodes.get(b.property()),new int[]{bb,a}));
            }
            var result=NonlinearNetworkSolver.solve(n.conductanceMatrix,n.rhsVector,entries);
            if(!result.accepted()) throw new AdaptiveTransient.Rejected("Network Newton/residual check failed");
            double[] previous=access.electromagnetism$last();
            try { n.getResults(result.voltages(),values,0,1); }
            finally { access.electromagnetism$last(previous); }
            solutions.put(n,result.voltages());
            // Include MNA branch currents in the error estimate.
            for(int i=access.electromagnetism$nodeCount();i<result.voltages().length;i++) allCurrents.add(result.voltages()[i]);
        }
        var energies=new IdentityHashMap<MicroTickingElectricalProperties,TransientEnergy>(old.energy());
        var nextHistory=new IdentityHashMap<MicroTickingElectricalProperties,History>();
        for(var b:branches) {
            var p=b.property();double v=values[b.a()]-values[b.b()],i=0;
            if(companions.containsKey(p)) i=companions.get(p).current(v);
            if(diodes.containsKey(p)) { v=-v;i=diodes.get(p).capacitiveCurrent(v); }
            nextHistory.put(p,p instanceof FrozenMicroBranch?old.history().get(p):new History(v,i,true));
            allCurrents.add(i);
            if(!(p instanceof FrozenMicroBranch)) {
                var h=old.history().get(p);double dt=interval.durationSeconds();
                boolean trap=method==Method.TRAPEZOIDAL && h.initialized();
                double stored=p instanceof InductorProperties
                        ?TransientEnergy.inductor(b.parameter(),i)-TransientEnergy.inductor(b.parameter(),h.current())
                        :TransientEnergy.capacitor(b.parameter(),v)-TransientEnergy.capacitor(b.parameter(),h.voltage());
                double heat=0,oldI=h.current(),newI=i;
                if(p instanceof DiodeProperties) {
                    double now=v*DiodeCompanion.staticCurrent(v),before=h.voltage()*DiodeCompanion.staticCurrent(h.voltage());
                    heat=dt*(trap?(now+before)/2:now);
                    oldI+=DiodeCompanion.staticCurrent(h.voltage());newI+=DiodeCompanion.staticCurrent(v);
                }
                double work=dt*(trap?(h.voltage()*oldI+v*newI)/2:v*newI);
                var previous=energies.getOrDefault(p,new TransientEnergy(0,0,0));
                energies.put(p,new TransientEnergy(previous.portJoules()+work,previous.storedChangeJoules()+stored,previous.dissipatedJoules()+Math.max(0,heat)));
            }
        }
        var nextModels=new IdentityHashMap<StatefulDevice<?,?,?>,Object>();
        for(var d:old.models().keySet()) {
            double[] local=new double[d.nodes.length];
            for(int i=0;i<local.length;i++) local[i]=values[builder.getNode(d.nodes[i]).ordinal];
            nextModels.put(d,d.nextState(new TerminalVoltages(local)));
            var op=d.evaluate(new TerminalVoltages(local));
            for(int i=0;i<local.length;i++) allCurrents.add(op.current(i));
        }
        var stamps=new IdentityHashMap<MicroTickingElectricalProperties,double[]>();
        for(var b:branches) { var p=b.property();stamps.put(p,new double[]{p.resistance,p.currentSource,p.voltageSource}); }
        var state=new State(values,nextHistory,nextModels,solutions,energies,stamps);
        return new AdaptiveTransient.Candidate<>(state,values,allCurrents.stream().mapToDouble(Double::doubleValue).toArray());
    }
    private void publish(List<Branch> branches,State state) {
        for(var b:branches) {
            var p=b.property();var h=state.history().get(p);
            if(p instanceof CapacitorProperties c) c.lastVoltage=h.voltage();
            if(p instanceof InductorProperties l) l.lastCurrent=h.current();
            if(p instanceof DiodeProperties d) d.lastVoltage=h.voltage();
            histories.put(p,h);
        }
        for(var entry:state.models().entrySet()) entry.getKey().publish(entry.getValue());
    }
}
