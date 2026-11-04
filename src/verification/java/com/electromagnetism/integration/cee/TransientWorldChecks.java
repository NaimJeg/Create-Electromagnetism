package com.electromagnetism.integration.cee;
import com.electromagnetism.integration.cee.electrical.*;
import com.george_vi.electroenergetics.foundation.electrical_properties.*;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.*;
import com.george_vi.electroenergetics.simulation.electrical_properties.*;
import com.george_vi.electroenergetics.simulation.simulator.*;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import java.util.*;
/** Actual transformed CEE network, without placing or altering world blocks. */
public final class TransientWorldChecks {
    public static String run(MinecraftServer server) {
        var level=server.overworld();var sd=InfrastructureSavedData.load(level);
        var a=new InWorldNode(0,BlockPos.ZERO);var b=new InWorldNode(1,BlockPos.ZERO);var g=new InWorldNode(2,BlockPos.ZERO);
        var builder=new CircuitBuilder(new HashSet<>(List.of(a,b,g)));
        var c=new CapacitorProperties();c.capacitance=0.001;
        builder.connect(a,b,ElectricalProperties.resistor(100));builder.connect(b,g,c);
        builder.connect(a,g,new ElectricalProperties(1e-9,-1,0,true));builder.ground(g,1);
        var networks=networks(builder,sd);var engine=new CeeTransientEngine(0);
        for(int k=0;k<4;k++) {
            CeeStepEvidence.begin();var r=engine.run(builder,networks,sd,1,true);CeeStepEvidence.finish(r);
            if(!CeeStepEvidence.verified(r)) throw new AssertionError(engine.report().toString());
        }
        TransientChecks.near(c.lastVoltage,1-Math.exp(-2),0.002);
        double before=c.lastVoltage,time=engine.report().end();
        builder.microTickers.put(123,new MicroTickingElectricalProperties() {
            @Override public void tick(double[] v,int step,int count,int x,int y) { throw new AssertionError("Unknown callback invoked"); }
        });
        CeeStepEvidence.begin();var rejected=engine.run(builder,networks,sd,1,true);CeeStepEvidence.finish(rejected);
        if(CeeStepEvidence.verified(rejected)||c.lastVoltage!=before||engine.report().end()!=time) throw new AssertionError("Failed network changed history");
        // A native CEE diode participates in the same Newton loop, without its old one-shot stamp.
        var db=new CircuitBuilder(new HashSet<>(List.of(a,b,g)));
        var diode=new DiodeProperties();db.connect(g,b,diode);db.connect(a,b,ElectricalProperties.resistor(1000));
        db.connect(a,g,new ElectricalProperties(1e-9,-12,0,true));db.ground(g,1);
        var dn=networks(db,sd);var de=new CeeTransientEngine(0);
        CeeStepEvidence.begin();var dr=de.run(db,dn,sd,1,true);CeeStepEvidence.finish(dr);
        if(!CeeStepEvidence.verified(dr)||diode.lastVoltage<0.1||diode.lastVoltage>1) throw new AssertionError("Native diode: "+de.report());
        // Free LC through actual CEE matrices; only the return wire has negligible resistance.
        var lb=new CircuitBuilder(new HashSet<>(List.of(a,b,g)));
        var lc=new CapacitorProperties();lc.capacitance=0.001;lc.lastVoltage=1;
        var li=new InductorProperties();li.inductance=0.1;
        lb.connect(a,g,lc);lb.connect(a,b,li);lb.connect(b,g,ElectricalProperties.resistor(1e-9));lb.ground(g,1);
        var ln=networks(lb,sd);var le=new CeeTransientEngine(0);double peakDrift=0;
        for(int k=0;k<40;k++) {
            CeeStepEvidence.begin();var lr=le.run(lb,ln,sd,1,true);CeeStepEvidence.finish(lr);
            if(!CeeStepEvidence.verified(lr)) throw new AssertionError("LC: "+le.report());
            double energy=0.5*lc.capacitance*lc.lastVoltage*lc.lastVoltage+0.5*li.inductance*li.lastCurrent*li.lastCurrent;
            peakDrift=Math.max(peakDrift,Math.abs(energy/0.0005-1));
        }
        if(peakDrift>0.002) throw new AssertionError("Native LC energy drift "+peakDrift);
        // Exercise the transformed worker itself and a two-terminal stateful model.
        var model=new com.electromagnetism.core.model.ThermalResistorModel();
        var parameters=new com.electromagnetism.core.model.ThermalResistorModel.Parameters(10,
                new com.electromagnetism.core.model.LumpedThermalModel.Parameters(1,0,295.15));
        var device=new StatefulDevice<>(model,parameters,new com.electromagnetism.core.model.LumpedThermalModel.State(295.15),new BlockPos(1,1,1));
        var mb=new CircuitBuilder(new HashSet<>(List.of(device.nodes)));
        device.connect(new BridgeCollector(mb,sd,1));
        mb.connect(device.nodes[0],device.nodes[1],new ElectricalProperties(1e-9,-10,0,true));mb.ground(device.nodes[1],1);
        SimulationResults workerResult;
        try {
            var ticker=new SimulationTicker(level,sd);
            var field=SimulationTicker.class.getDeclaredField("circuitBuilder");field.setAccessible(true);field.set(ticker,mb);
            field=SimulationTicker.class.getDeclaredField("stats");field.setAccessible(true);field.set(ticker,new SimulationStats());
            var worker=SimulationTicker.class.getDeclaredMethod("lambda$tick$1",List.class,Collection.class,long.class);worker.setAccessible(true);
            workerResult=(SimulationResults)worker.invoke(ticker,List.of(),List.of(),System.nanoTime());
        } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
        if(!CeeStepEvidence.verified(workerResult)||CeeStepEvidence.report(workerResult).isEmpty()) throw new AssertionError("Worker transaction hook missing");
        TransientChecks.near(((com.electromagnetism.core.model.LumpedThermalModel.State)device.acceptedState()).temperatureKelvin(),295.65,1e-6);
        return "passed: worker/stateful thermal, diode="+diode.lastVoltage+", LC drift="+peakDrift+",  transformed CEE RC="+before+", unknown branch rejected without advancing state/time";
    }
    private static List<Network> networks(CircuitBuilder b,InfrastructureSavedData sd) {
        var result=new ArrayList<Network>();
        for(var island:b.dfsAndGround()) if(island.size()>1) {
            var n=new Network(island,b,sd);n.mapToSimNodes();result.add(n);
        }
        return result;
    }
}
