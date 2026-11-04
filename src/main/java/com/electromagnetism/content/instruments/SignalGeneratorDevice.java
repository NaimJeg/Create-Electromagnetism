package com.electromagnetism.content.instruments;
import com.electromagnetism.platform.LoadedBlockAccess;
import com.electromagnetism.core.signal.MicrophoneTransducer;
import com.electromagnetism.core.signal.WaveformBuffer;
import com.electromagnetism.integration.cee.electrical.CeeStepEvidence;
import com.george_vi.electroenergetics.devices.device.*;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.*;
import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
public class SignalGeneratorDevice extends SimpleElectricalDevice {
    private com.electromagnetism.core.signal.SignalGenerator settings = new com.electromagnetism.core.signal.SignalGenerator(null,2,1,0,false);
    private final Branch branch = new Branch();
    private long captureTick;
    private Integer topology;
    private net.minecraft.world.level.block.entity.BlockEntity batchEntity;
    public SignalGeneratorDevice(Level level, BlockPos pos, DevicesSavedData sd, SimulatedDeviceType<?> type) { super(level,pos,sd,type); }
    @Override public void preTick(BridgeCollector bridges) {
        int fingerprint=31*bridges.getSD().getConnections(new InWorldNode(0,pos)).hashCode()
                +bridges.getSD().getConnections(new InWorldNode(1,pos)).hashCode();
        if(topology!=null && topology!=fingerprint && LoadedBlockAccess.entity(level,pos) instanceof InstrumentBlockEntity be) be.resetObservations();
        topology=fingerprint;
        batchEntity=LoadedBlockAccess.entity(level,pos);
        captureTick = level.getGameTime();
        if (batchEntity instanceof SignalGeneratorBlockEntity generator) settings = generator.settings();
        branch.resistance = settings.enabled() ? 50 : 1e12;
        branch.currentSource = 0;
        branch.values = new double[0];
        branch.batch=null;
        bridges.bridge(new InWorldNode(0,pos),new InWorldNode(1,pos),branch);
    }
    @Override public void postTick(SimulationResults results) {
        if (level.isLoaded(pos) && LoadedBlockAccess.entity(level,pos)==batchEntity && batchEntity instanceof InstrumentBlockEntity be) be.capture(captureTick,branch.values,CeeStepEvidence.verified(results,branch.batch)?WaveformBuffer.Quality.VALID:WaveformBuffer.Quality.UNCONVERGED);
    }
    @Override public boolean shouldRemove(BlockState oldState, BlockState newState) { return !(newState.getBlock() instanceof SignalGeneratorBlock); }
    private class Branch extends MicroTickingElectricalProperties implements com.electromagnetism.integration.cee.electrical.FrozenMicroBranch {
        double[] values = new double[0];
        java.util.UUID batch;
        Branch() { resistance = 50; }
        @Override public void tick(double[] v,int step,int count,int a,int b) { resistance = settings.enabled() ? 50 : 1e12; currentSource = settings.voltage((captureTick+(step+1.0)/count)/20.0)/resistance; }
        @Override public void coefficientsAt(com.electromagnetism.core.model.TimeStep interval) { resistance=settings.enabled()?50:1e12;currentSource=settings.voltage(interval.endSeconds())/resistance; }
        @Override public void afterTick(double[] v,int a,int b,int step,int count) {
            if(values.length!=count) { values=new double[count]; java.util.Arrays.fill(values,Double.NaN); }
            batch=CeeStepEvidence.currentBatch();
            values[step]=v[b*count+step]-v[a*count+step];
        }
    }
}
