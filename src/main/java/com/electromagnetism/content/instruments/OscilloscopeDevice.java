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
public class OscilloscopeDevice extends SimpleElectricalDevice {
    private final Branch branch = new Branch();
    private long captureTick;
    private Integer topology;
    private net.minecraft.world.level.block.entity.BlockEntity batchEntity;
    public OscilloscopeDevice(Level level, BlockPos pos, DevicesSavedData sd, SimulatedDeviceType<?> type) { super(level,pos,sd,type); }
    @Override public void preTick(BridgeCollector bridges) {
        int fingerprint=31*bridges.getSD().getConnections(new InWorldNode(0,pos)).hashCode()
                +bridges.getSD().getConnections(new InWorldNode(1,pos)).hashCode();
        if(topology!=null && topology!=fingerprint && LoadedBlockAccess.entity(level,pos) instanceof InstrumentBlockEntity be) be.resetObservations();
        topology=fingerprint;
        batchEntity=LoadedBlockAccess.entity(level,pos);
        captureTick = level.getGameTime();
        branch.values = new double[0];
        branch.batch=null;
        bridges.bridge(new InWorldNode(0,pos),new InWorldNode(1,pos),branch);
    }
    @Override public void postTick(SimulationResults results) {
        if (level.isLoaded(pos) && LoadedBlockAccess.entity(level,pos)==batchEntity && batchEntity instanceof InstrumentBlockEntity be) be.capture(captureTick,branch.values,CeeStepEvidence.verified(results,branch.batch)?WaveformBuffer.Quality.VALID:WaveformBuffer.Quality.UNCONVERGED);
    }
    @Override public boolean shouldRemove(BlockState oldState, BlockState newState) { return !(newState.getBlock() instanceof OscilloscopeBlock); }
    private class Branch extends MicroTickingElectricalProperties implements com.electromagnetism.integration.cee.electrical.FrozenMicroBranch {
        double[] values = new double[0];
        java.util.UUID batch;
        Branch() { resistance = 1e6; }
        @Override public void tick(double[] v,int step,int count,int a,int b) {  }
        @Override public void afterTick(double[] v,int a,int b,int step,int count) {
            if(values.length!=count) { values=new double[count]; java.util.Arrays.fill(values,Double.NaN); }
            batch=CeeStepEvidence.currentBatch();
            values[step]=v[b*count+step]-v[a*count+step];
        }
    }
}
