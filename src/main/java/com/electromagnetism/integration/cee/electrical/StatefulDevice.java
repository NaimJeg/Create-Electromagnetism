package com.electromagnetism.integration.cee.electrical;

import com.electromagnetism.core.model.*;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.*;
import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;
import net.minecraft.core.BlockPos;
import java.util.*;

/** One complete multi-terminal model. Zero-conductance graph edges only preserve connectivity;
 * the full Jacobian and physical residual are stamped by the pinned CEE network extension.
 */
public class StatefulDevice<P,S,T> implements SolverDevice {
    private static final Map<CircuitBuilder,List<StatefulDevice<?,?,?>>> REGISTRY = Collections.synchronizedMap(new WeakHashMap<>());
    public final DeviceModel<P,S,T> model;
    public final P parameters;
    public final InWorldNode[] nodes;
    private S accepted;
    private S history;
    private T trial;
    private TerminalVoltages candidate;
    private TimeStep step = new TimeStep(0,0.05);
    public StatefulDevice(DeviceModel<P,S,T> model,P parameters,S initial,BlockPos pos) {
        this.model=model;this.parameters=parameters;accepted=Objects.requireNonNull(initial);history=accepted;
        nodes=new InWorldNode[model.definition().terminals().size()];
        for(int i=0;i<nodes.length;i++) nodes[i]=new InWorldNode(i,pos);
    }
    public void connect(BridgeCollector bridges) {
        synchronized(REGISTRY) { REGISTRY.computeIfAbsent(bridges.getCircuitBuilder(), k->new ArrayList<>()).add(this); }
        for(int i=1;i<nodes.length;i++) bridges.bridge(nodes[0],nodes[i],new GraphEdge());
    }
    public static List<StatefulDevice<?,?,?>> devices(CircuitBuilder builder) {
        synchronized(REGISTRY) { return List.copyOf(REGISTRY.getOrDefault(builder,List.of())); }
    }
    public Object acceptedState() { return accepted; }
    @SuppressWarnings("unchecked")
    public void prepare(Object state,TimeStep time) { history=(S)state;step=time; }
    public Object nextState(TerminalVoltages v) {
        return Objects.requireNonNull(model.advance(parameters,history,new EnergySample(step,evaluate(v))));
    }
    @SuppressWarnings("unchecked")
    public void publish(Object state) { accepted=(S)state; }
    public void beginMicrostep() {
        // Numeric guesses/history never survive a completed or rejected physical microstep.
        candidate=new TerminalVoltages(new double[nodes.length]);
        trial=model.beginTrial(parameters,history,step);
    }
    public Linearization linearize() {
        var next=model.linearize(parameters,history,step,candidate,trial);trial=next.trialState();return next.equations();
    }
    public void candidate(TerminalVoltages voltages) { candidate=voltages; }
    public OperatingPoint evaluate(TerminalVoltages voltages) { return model.evaluate(parameters,history,step,voltages); }
    public static final class GraphEdge extends MicroTickingElectricalProperties implements FrozenMicroBranch {
        GraphEdge() { resistance=Double.POSITIVE_INFINITY; }
        @Override public double conductance() { return 0; }
        @Override public void tick(double[] values,int step,int count,int a,int b) {}
    }
}
