package com.electromagnetism.integration.cee.mixin;
import com.electromagnetism.integration.cee.electrical.*;
import com.george_vi.electroenergetics.simulation.*;
import com.george_vi.electroenergetics.simulation.simulator.Network;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

@Mixin(value=Network.class,remap=false)
public abstract class NonlinearNetworkMixin implements NonlinearNetworkAccess, CeeNetworkAccess {
    @Shadow @Final CircuitBuilder builder;
    @Shadow @Final Set<WrappedIndexedNode> allNodes;
    @Shadow Object2IntOpenHashMap<WrappedIndexedNode> nodeIDs;
    @Shadow com.george_vi.electroenergetics.simulation.simulator.SimulationNode[] simulationNodes;
    @Override public int electromagnetism$nodeCount() { return simulationNodes.length; }
    @Shadow double[] lastMNAResult;
    @Shadow @Final it.unimi.dsi.fastutil.longs.Long2DoubleMap voltageSources;
    @Override public int electromagnetism$index(int global) {
        var node=builder.getNode(global);
        return nodeIDs.containsKey(node)?nodeIDs.getInt(node):-1;
    }
    @Override public double[] electromagnetism$last() { return lastMNAResult; }
    @Override public void electromagnetism$last(double[] values) { lastMNAResult=values; }
    @Override public void electromagnetism$currents(double[] values,
            it.unimi.dsi.fastutil.objects.Object2DoubleMap<com.george_vi.electroenergetics.foundation.nodes.DirectionalNodeConnection> output) {
        int offset=simulationNodes.length,i=0;
        for(long key:voltageSources.keySet()) {
            int a=com.george_vi.electroenergetics.simulation.util.DataPacker.unpackFirstI(key);
            int b=com.george_vi.electroenergetics.simulation.util.DataPacker.unpackSecondI(key);
            WrappedIndexedNode na=null,nb=null;
            for(var node:allNodes) { int id=nodeIDs.getInt(node);if(id==a) na=node;if(id==b) nb=node; }
            if(na!=null && nb!=null && na.node instanceof com.george_vi.electroenergetics.foundation.nodes.InWorldNode
                    && nb.node instanceof com.george_vi.electroenergetics.foundation.nodes.InWorldNode)
                output.put(new com.george_vi.electroenergetics.foundation.nodes.DirectionalNodeConnection(na.node,nb.node),values[offset+i]);
            i++;
        }
    }
    @Inject(method="optimize",at=@At("HEAD"),cancellable=true)
    private void electromagnetism$preserveTerminals(CallbackInfo ci) {
        for(var d:StatefulDevice.devices(builder))
            if(allNodes.contains(builder.getNode(d.nodes[0]))) { ci.cancel();return; }
    }
    @Override public List<Entry> electromagnetism$models() {
        List<Entry> entries=new ArrayList<>();
        for(var d:StatefulDevice.devices(builder)) {
            if(!allNodes.contains(builder.getNode(d.nodes[0]))) continue;
            int[] indices=new int[d.nodes.length];
            for(int i=0;i<indices.length;i++) {
                var node=builder.getNode(d.nodes[i]);
                if(!nodeIDs.containsKey(node)) throw new IllegalStateException("Nonlinear terminal was eliminated or split");
                indices[i]=nodeIDs.getInt(node);
            }
            entries.add(new Entry(d,indices));
        }
        return entries;
    }
    @Inject(method="formMatrix",at=@At("RETURN"))
    private void electromagnetism$select(CallbackInfo ci) { NonlinearNetworkSolver.select(this); }
}
