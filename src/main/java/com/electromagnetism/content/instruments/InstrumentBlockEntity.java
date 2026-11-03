package com.electromagnetism.content.instruments;
import com.electromagnetism.core.signal.*;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
/** Transient bounded microstep observations; synchronized only after simulation returns. */
public class InstrumentBlockEntity extends SmartBlockEntity {
    private final WaveformBuffer waveform=new WaveformBuffer(1024);
    private volatile List<WaveformBuffer.Sample> displayed=List.of();
    private long lastTick=Long.MIN_VALUE;
    public InstrumentBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state) { super(type,pos,state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
    public void capture(long tick,double[] values) {
        if(tick<=lastTick || values.length==0) return;
        if(lastTick!=Long.MIN_VALUE && tick>lastTick+1)
            waveform.append(new WaveformBuffer.Sample((lastTick+1.5)/20.0,OptionalDouble.empty(),WaveformBuffer.Quality.MISSING));
        lastTick=tick;
        for(int i=0;i<values.length;i++) {
            double t=(tick+(i+1.0)/values.length)/20.0;
            waveform.append(Double.isFinite(values[i])?WaveformBuffer.Sample.valid(t,values[i]):new WaveformBuffer.Sample(t,OptionalDouble.empty(),WaveformBuffer.Quality.MISSING));
        }
        if(tick%2==0) sendData();
    }
    public List<WaveformBuffer.Sample> samples() { return displayed; }
    @Override public void write(CompoundTag tag,HolderLookup.Provider registries,boolean clientPacket) {
        super.write(tag,registries,clientPacket);
        if(!clientPacket) return;
        ListTag list=new ListTag();
        for(var sample:waveform.snapshot()) {
            CompoundTag entry=new CompoundTag();entry.putDouble("T",sample.timeSeconds());
            if(sample.value().isPresent()) entry.putDouble("V",sample.value().getAsDouble());
            list.add(entry);
        }
        tag.put("Waveform",list);
    }
    @Override public void read(CompoundTag tag,HolderLookup.Provider registries,boolean clientPacket) {
        super.read(tag,registries,clientPacket);
        if(!clientPacket) return;
        var list=tag.getList("Waveform",Tag.TAG_COMPOUND);
        var samples=new ArrayList<WaveformBuffer.Sample>();
        for(int i=0;i<Math.min(1024,list.size());i++) {
            var e=list.getCompound(i);
            samples.add(e.contains("V")?WaveformBuffer.Sample.valid(e.getDouble("T"),e.getDouble("V")):new WaveformBuffer.Sample(e.getDouble("T"),OptionalDouble.empty(),WaveformBuffer.Quality.MISSING));
        }
        displayed=List.copyOf(samples);
    }
}
