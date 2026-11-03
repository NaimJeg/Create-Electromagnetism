package com.electromagnetism.content.instruments;
import com.electromagnetism.core.signal.MicrophoneTransducer;
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
    public OscilloscopeDevice(Level level, BlockPos pos, DevicesSavedData sd, SimulatedDeviceType<?> type) { super(level,pos,sd,type); }
    @Override public void preTick(BridgeCollector bridges) {
        captureTick = level.getGameTime();
        branch.values = new double[0];
        bridges.bridge(new InWorldNode(0,pos),new InWorldNode(1,pos),branch);
    }
    @Override public void postTick(SimulationResults results) {
        if (level.getBlockEntity(pos) instanceof InstrumentBlockEntity be) be.capture(captureTick,branch.values);
    }
    @Override public boolean shouldRemove(BlockState oldState, BlockState newState) { return !(newState.getBlock() instanceof OscilloscopeBlock); }
    private class Branch extends MicroTickingElectricalProperties {
        double[] values = new double[0];
        Branch() { resistance = 1e6; }
        @Override public void tick(double[] v,int step,int count,int a,int b) {  }
        @Override public void afterTick(double[] v,int a,int b,int step,int count) {
            if(values.length!=count) { values=new double[count]; java.util.Arrays.fill(values,Double.NaN); }
            values[step]=v[b*count+step]-v[a*count+step];
        }
    }
}
