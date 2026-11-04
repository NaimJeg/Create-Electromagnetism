package com.electromagnetism.integration.cee;

import com.electromagnetism.content.thermal.heater.*;
import com.electromagnetism.content.instruments.*;
import com.electromagnetism.content.electronics.mosfet.MosfetDevice;
import com.electromagnetism.integration.cee.electrical.CeeStepEvidence;
import com.electromagnetism.registry.*;
import com.george_vi.electroenergetics.devices.device.*;
import com.george_vi.electroenergetics.foundation.device.TickingElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.*;
import com.george_vi.electroenergetics.simulation.*;
import com.george_vi.electroenergetics.simulation.simulator.*;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.util.*;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;
import java.util.function.Consumer;

/** Dev-only real CEE island/adapter fixtures. Runs on the server thread, never packaged in release. */
public final class WorldChecks {
    private static String status="idle";
    private static boolean registered;
    private static MinecraftServer server;
    private static int stage;
    private static boolean placed,remotePlaced;
    private static HeatingCoilDevice remote;
    private static int unloadedAt=-1;
    private static double unloadedTemperature;
    private static final BlockPos FAR=new BlockPos(16008,150,16008);
    private static double radioPeak,radioInput,radioOutput,radioDissipation;
    private static HeatingCoilDevice heater;
    private static RadioDevice tx,rx;
    private static final BlockPos HEAT=new BlockPos(8,150,8), TX=new BlockPos(10,150,8), RX=new BlockPos(12,150,8), MOS=new BlockPos(14,150,8);
    public static String status() { return status; }
    public static String start(MinecraftServer instance) {
        if(status.equals("running")) throw new IllegalStateException("Already running");
        server=instance;stage=0;radioPeak=0;radioInput=radioOutput=radioDissipation=0;placed=false;remotePlaced=false;unloadedAt=-1;status="running";
        if(!registered) { NeoForge.EVENT_BUS.addListener(WorldChecks::tick);registered=true; }
        return status;
    }
    private static void tick(ServerTickEvent.Post event) {
        if(!status.equals("running")||event.getServer()!=server) return;
        var level=server.overworld();var sd=DevicesSavedData.load(level);
        try {
            if(stage==0) {
                for(var pos:List.of(HEAT,TX,RX,MOS)) {
                    if(!level.getBlockState(pos).isAir()) throw new AssertionError("Test area occupied: "+pos);
                }
                placed=true;
                level.setBlockAndUpdate(HEAT,ModBlocks.HEATING_COIL.getDefaultState());
                level.setBlockAndUpdate(TX,ModBlocks.RADIO_TRANSMITTER.getDefaultState());
                level.setBlockAndUpdate(RX,ModBlocks.RADIO_RECEIVER.getDefaultState());
                level.setBlockAndUpdate(MOS,ModBlocks.N_MOSFET.getDefaultState());
                level.getChunk(FAR);
                if(!level.getBlockState(FAR).isAir()) throw new AssertionError("Remote test area occupied");
                level.setBlockAndUpdate(FAR,ModBlocks.HEATING_COIL.getDefaultState());remotePlaced=true;
                heater=new HeatingCoilDevice(level,HEAT,sd,ModDeviceTypes.HEATING_COIL.get());
                tx=new RadioDevice(level,TX,sd,ModDeviceTypes.RADIO_TRANSMITTER.get());tx.transmitter=true;
                rx=new RadioDevice(level,RX,sd,ModDeviceTypes.RADIO_RECEIVER.get());
            }
            if(stage==1) {
                remote=sd.getDevice(FAR,HeatingCoilDevice.class);
                if(remote==null) throw new AssertionError("Remote CEE device missing");
                var state=new CompoundTag();state.putFloat("Temperature",100);remote.read(state);
            }
            if(stage>1 && unloadedAt<0 && !level.isLoaded(FAR)) {
                unloadedAt=stage;unloadedTemperature=temperature(remote);
            }
            if(unloadedAt>=0 && stage<unloadedAt+20) near(temperature(remote),unloadedTemperature,0);
            if(unloadedAt>=0 && stage==unloadedAt+20) {
                level.getChunk(FAR);near(temperature(remote),unloadedTemperature,0);
            }
            double before=temperature(heater);
            var result=simulate(level,List.of(heater),b->source(b,HEAT,0,1,12,0.001),stage!=3);
            heater.postTick(result);double after=temperature(heater);
            if(stage==3) near(after,before,0);
            else {
                var config=com.electromagnetism.config.ModConfigs.server();
                double resistance=config.heatingCoilResistance.getF();
                double voltage=12*resistance/(resistance+0.001);
                var parameters=new com.electromagnetism.core.model.LumpedThermalModel.Parameters(config.heatingCoilMass.getF(),
                    config.heatingCoilMaxPower.getF()/(600-22),22+273.15);
                double expected=com.electromagnetism.core.model.LumpedThermalModel.advance(parameters,
                    new com.electromagnetism.core.model.LumpedThermalModel.State(before+273.15),voltage*voltage/resistance,0.05).temperatureKelvin()-273.15;
                near(after,expected,1e-4);
            }
            heater.postTick(result);near(temperature(heater),after,0);
            if(stage==4) {
                var saved=new CompoundTag();heater.write(saved);
                var restored=new HeatingCoilDevice(level,HEAT,sd,ModDeviceTypes.HEATING_COIL.get());restored.read(saved);
                near(temperature(restored),after,0);heater=restored;
            }
            if(stage==5) {
                var rotated=ModBlocks.HEATING_COIL.getDefaultState().setValue(HeatingCoilBlock.FACING,Direction.EAST);
                if(heater.shouldRemove(level.getBlockState(HEAT),rotated)) throw new AssertionError("Rotation replaced heater");
                level.setBlockAndUpdate(HEAT,rotated);
            }
            var mos=new MosfetDevice(level,MOS,sd,ModDeviceTypes.N_MOSFET.get());
            var mr=simulate(level,List.of(mos),b->{source(b,MOS,0,2,4,0.001);source(b,MOS,1,2,12,10);});
            if(!CeeStepEvidence.verified(mr)) throw new AssertionError("Real CEE MOS island failed");
            var tr=simulate(level,List.of(tx),b->source(b,TX,0,1,stage<30?12:0,0.001));tx.postTick(tr);
            var rr=simulate(level,List.of(rx),b->{source(b,RX,0,1,stage<40?12:0,0.001);b.addInternalNode(20,RX);b.bridge(new InWorldNode(2,RX),new InWorldNode(20,RX),ElectricalProperties.resistor(0.001));b.bridge(new InWorldNode(20,RX),new InWorldNode(3,RX),ElectricalProperties.resistor(600));});rx.postTick(rr);
            var ledger=rx.ledger();
            if(!ledger.valid()) throw new AssertionError("Receiver batch unverified");
            radioInput+=ledger.inputJoules();radioOutput+=ledger.outputJoules();radioDissipation+=ledger.dissipatedJoules();
            near(radioInput,radioOutput+radioDissipation+ledger.storedJoules(),1e-7);
            if(tx.ledger().transmittedJoules()>tx.ledger().inputJoules()+1e-8) throw new AssertionError("TX created RF energy");
            for(double sample:rx.outputSamples()) {
                radioPeak=Math.max(radioPeak,Math.abs(sample));
                if(stage==50) near(sample,0,1e-6);
            }
            if(stage>800 && unloadedAt<0) throw new AssertionError("Remote chunk never unloaded");
            if(stage++>=50 && unloadedAt>=0 && stage>unloadedAt+21) {
                if(radioPeak<1e-5) throw new AssertionError("AM link never produced a received waveform");
                level.setBlockAndUpdate(HEAT,Blocks.AIR.defaultBlockState());heater.postTick(result);near(temperature(heater),after,0);
                cleanup(level);status="passed: heater energy/duplicate/unknown/NBT/rotation/removal/unload/reload; CEE MOS full island; powered AM TX/channel/RX/load/outage/energy ledger; peak="+radioPeak;
            }
        } catch(Throwable failure) { cleanup(level);status="FAILED stage="+stage+": "+failure;failure.printStackTrace(); }
    }
    private static double temperature(HeatingCoilDevice device) { var tag=new CompoundTag();device.write(tag);return tag.getFloat("Temperature"); }
    private static void near(double actual,double expected,double tolerance) { if(!Double.isFinite(actual)||Math.abs(actual-expected)>tolerance) throw new AssertionError(actual+" != "+expected); }
    private static void cleanup(ServerLevel level) {
        if(remotePlaced) { level.getChunk(FAR);level.setBlockAndUpdate(FAR,Blocks.AIR.defaultBlockState());remotePlaced=false; }
        if(!placed) return; for(var pos:List.of(HEAT,TX,RX,MOS)) level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState()); }
    private static void source(BridgeCollector b,BlockPos pos,int positive,int negative,double volts,double resistance) {
        int internal=10+positive;b.addInternalNode(internal,pos);
        b.bridge(new InWorldNode(internal,pos),new InWorldNode(negative,pos),new ElectricalProperties(1e-9,volts,0,true));
        b.bridge(new InWorldNode(internal,pos),new InWorldNode(positive,pos),ElectricalProperties.resistor(resistance));
    }
    private static SimulationResults simulate(ServerLevel level,List<? extends TickingElectricalDevice> devices,Consumer<BridgeCollector> extra) {
        return simulate(level,devices,extra,true);
    }
    private static SimulationResults simulate(ServerLevel level,List<? extends TickingElectricalDevice> devices,Consumer<BridgeCollector> extra,boolean attest) {
        int count=16;var sd=InfrastructureSavedData.load(level);var builder=new CircuitBuilder(Set.of());
        var bridges=new BridgeCollector(builder,sd,count);
        for(var device:devices) {
            var pos=((com.george_vi.electroenergetics.devices.device.SimulatedDevice)device).pos;
            for(int i=0;i<4;i++) bridges.addInternalNode(i,pos);
            device.preTick(bridges);
        }
        extra.accept(bridges);var islands=builder.dfsAndGround();var networks=new ArrayList<Network>();
        for(var island:islands) if(island.size()>1) { var network=new Network(island,builder,sd);network.optimize();network.mapToSimNodes();networks.add(network); }
        double[] voltages=new double[builder.allNodes().size()*count];
        CeeStepEvidence.begin();
        for(int step=0;step<count;step++) {
            for(var entry:builder.microTickers.long2ObjectEntrySet()) entry.getValue().tick(voltages,step,count,DataPacker.unpackFirstI(entry.getLongKey()),DataPacker.unpackSecondI(entry.getLongKey()));
            for(var network:networks) {
                network.formMatrix();var x=CeeStepEvidence.solve(network.conductanceMatrix,network.rhsVector,network.voltageSourcesInMatrix?LUSolver::solve:CholeskySolver::solve);
                network.getResults(x,voltages,step,count);
            }
            for(var entry:builder.microTickers.long2ObjectEntrySet()) entry.getValue().afterTick(voltages,DataPacker.unpackFirstI(entry.getLongKey()),DataPacker.unpackSecondI(entry.getLongKey()),step,count);
        }
        var result=new SimulationResults(voltages,count,new Object2DoubleOpenHashMap<>(),builder,sd);if(attest) CeeStepEvidence.finish(result);return result;
    }
}
