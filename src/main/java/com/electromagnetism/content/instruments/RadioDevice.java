package com.electromagnetism.content.instruments;
import com.electromagnetism.platform.LoadedBlockAccess;
import com.electromagnetism.core.radio.*;
import com.electromagnetism.core.signal.WaveformBuffer;
import com.electromagnetism.integration.cee.electrical.CeeStepEvidence;
import com.george_vi.electroenergetics.devices.device.*;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.*;
import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** AM prototype. Terminals 0/1 supply; 2/3 finite receiver output (TX has only supply).
 * A deterministic 2Hz source replaces live microphone input in this milestone.
 */
public final class RadioDevice extends SimpleElectricalDevice {
    public boolean transmitter;
    public double carrierHz=1000;
    private double batchCarrierHz=1000;
    private final EnergyReservoir energy=new EnergyReservoir(0.1);
    private final Supply supply=new Supply();
    private final Output output=new Output();
    private long tick,consumed=-1;
    private net.minecraft.world.level.block.entity.BlockEntity batchEntity;
    private List<AmRadio.Path> paths=List.of();
    private double reservation,dc,trialDc;
    public record Ledger(double inputJoules,double transmittedJoules,double outputJoules,double dissipatedJoules,double storedJoules,boolean valid) {}
    private Ledger ledger=new Ledger(0,0,0,0,0,false);
    public Ledger ledger() { return ledger; }
    private double[] acceptedOutput=new double[0];
    public double[] outputSamples() { return acceptedOutput.clone(); }
    public RadioDevice(Level level,BlockPos pos,DevicesSavedData sd,SimulatedDeviceType<?> type) { super(level,pos,sd,type); }
    @Override public void preTick(BridgeCollector bridges) {
        batchEntity=level.isLoaded(pos)?LoadedBlockAccess.entity(level,pos):null;
        trialDc=dc;
        tick=level.getGameTime();supply.values=new double[0];supply.batch=null;output.values=new double[0];output.deliveredJoules=0;
        carrierHz=Double.isFinite(carrierHz)?Math.clamp(carrierHz,100,10000):1000;
        batchCarrierHz=carrierHz;
        bridges.bridge(new InWorldNode(0,pos),new InWorldNode(1,pos),supply);
        if(!transmitter) {
            paths=RadioMedium.paths(level,pos,tick);reservation=energy.reserve(0.05);
            bridges.bridge(new InWorldNode(2,pos),new InWorldNode(3,pos),output);
        }
    }
    @Override public void postTick(SimulationResults results) {
        if(tick<=consumed||!level.isLoaded(pos)||!(LoadedBlockAccess.state(level,pos).getBlock() instanceof RadioBlock block)||block.transmitter()!=transmitter
                ||LoadedBlockAccess.entity(level,pos)!=batchEntity) return;
        consumed=tick;
        boolean valid=CeeStepEvidence.verified(results,supply.batch)&&supply.values.length>0;
        double input=0;
        for(double voltage:supply.values) { valid &= Double.isFinite(voltage);input+=voltage*voltage/100*0.05/supply.values.length; }
        valid &= Double.isFinite(input);
        if(!transmitter) {
            valid &= output.values.length==supply.values.length;
            for(double voltage:output.values) valid &= Double.isFinite(voltage);
        }
        acceptedOutput=valid?output.values.clone():new double[0];
        double emitted=0,dissipated=0,delivered=valid?output.deliveredJoules:0;
        if(valid) {
            dc=trialDc;
            if(transmitter) {
                double[] message=new double[supply.values.length];
                for(int i=0;i<message.length;i++) message[i]=Math.sin(2*Math.PI*2*(tick+(i+1.0)/message.length)/20.0);
                var frame=AmRadio.transmit(batchCarrierHz,0,tick/20.0,message.length*20,message,input,0.5);
                emitted=frame.energy();dissipated=input-emitted;
                RadioMedium.transmit(level,pos,tick,frame);
            } else {
                double overflow=energy.deposit(input*0.5);
                dissipated=input*0.5+overflow+reservation-delivered;
            }
        } else {
            if(transmitter) RadioMedium.remove(level,pos);
            dissipated=reservation; // Unknown input is not credited; a failed reservation is lost.
        }
        ledger=new Ledger(valid?input:0,emitted,delivered,Math.max(0,dissipated),energy.stored(),valid);
        if(LoadedBlockAccess.entity(level,pos) instanceof InstrumentBlockEntity be) be.capture(tick,transmitter?supply.values:output.values,
            valid?WaveformBuffer.Quality.VALID:WaveformBuffer.Quality.UNCONVERGED);
    }
    private final class Supply extends MicroTickingElectricalProperties implements com.electromagnetism.integration.cee.electrical.FrozenMicroBranch {
        double[] values=new double[0];UUID batch;
        Supply() { resistance=100; }
        @Override public void tick(double[] v,int step,int count,int a,int b) {}
        @Override public void afterTick(double[] v,int a,int b,int step,int count) {
            if(values.length!=count) { values=new double[count];Arrays.fill(values,Double.NaN); }
            values[step]=v[b*count+step]-v[a*count+step];batch=CeeStepEvidence.currentBatch();
        }
    }
    private final class Output extends MicroTickingElectricalProperties implements com.electromagnetism.integration.cee.electrical.FrozenMicroBranch {
        double deliveredJoules;
        double nextDc;
        double[] values=new double[0];
        Output() { resistance=600; }
        @Override public void tick(double[] v,int step,int count,int a,int b) {
            // Sample the preceding transmitted frame. A one-tick explicit transport boundary.
            double time=(tick-1+(step+0.5)/count)/20.0;
            double envelope=time<0?0:AmRadio.receive(paths,batchCarrierHz,time).magnitude();
            nextDc=trialDc+ (envelope-trialDc)*(1-Math.exp(-0.05/count/0.5));
            double volts=AmRadio.outputVoltage(envelope-nextDc,10,resistance,reservation/0.05);
            currentSource=volts/resistance;
        }
        @Override public void afterTick(double[] v,int a,int b,int step,int count) {
            if(values.length!=count) { values=new double[count];Arrays.fill(values,Double.NaN); }
            values[step]=v[b*count+step]-v[a*count+step];
            trialDc=nextDc;
            deliveredJoules+=Math.max(0,AmRadio.deliveredWatts(currentSource*resistance,values[step],resistance))*0.05/count;
        }
    }
    @Override public void read(CompoundTag tag) {
        carrierHz=tag.contains("CarrierHz")?tag.getDouble("CarrierHz"):1000;
        double stored=tag.getDouble("RadioEnergy");energy.restore(Double.isFinite(stored)?Math.clamp(stored,0,0.1):0);
    }
    @Override public void write(CompoundTag tag) { tag.putDouble("CarrierHz",carrierHz);tag.putDouble("RadioEnergy",energy.stored()); }
    @Override public boolean shouldRemove(BlockState oldState,BlockState state) {
        boolean removed=!(state.getBlock() instanceof RadioBlock block)||block.transmitter()!=transmitter;
        if(removed) RadioMedium.remove(level,pos);return removed;
    }
}
