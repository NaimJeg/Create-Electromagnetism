package com.electromagnetism.content.instruments;
import com.electromagnetism.core.signal.*;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Ephemeral instrument history. Only nearby subscribed players receive bounded deltas. */
public class InstrumentBlockEntity extends SmartBlockEntity {
    private WaveformBuffer waveform=new WaveformBuffer(1024);
    private UUID epoch=UUID.randomUUID();
    private final Map<UUID,Double> sentThrough=new HashMap<>();
    private long lastTick=Long.MIN_VALUE;
    public InstrumentBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state) { super(type,pos,state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
    public void resetObservations() { waveform=new WaveformBuffer(1024);epoch=UUID.randomUUID();lastTick=Long.MIN_VALUE;sentThrough.clear(); }
    public void capture(long tick,double[] values,WaveformBuffer.Quality quality) {
        if(tick<=lastTick) return;
        if(lastTick!=Long.MIN_VALUE && tick>lastTick+1)
            waveform.append(new WaveformBuffer.Sample((lastTick+1.5)/20.0,OptionalDouble.empty(),WaveformBuffer.Quality.MISSING));
        lastTick=tick;
        int count=Math.max(1,values.length);
        for(int i=0;i<count;i++) {
            double t=(tick+(i+1.0)/count)/20.0;
            var q=quality;
            if(values.length==0||!Double.isFinite(values[i])) q=WaveformBuffer.Quality.MISSING;
            waveform.append(q==WaveformBuffer.Quality.VALID?WaveformBuffer.Sample.valid(t,values[i]):new WaveformBuffer.Sample(t,OptionalDouble.empty(),q));
        }
        if(tick%2==0 && level instanceof ServerLevel server) {
            var snapshot=waveform.snapshot();
            var active=new HashSet<UUID>();int observers=0;
            for(var player:server.players()) {
                if(!ScopeNetworking.subscribed(player,worldPosition,tick)||observers++>=16) continue;
                active.add(player.getUUID());double after=sentThrough.getOrDefault(player.getUUID(),-1.0);
                var pending=new ArrayList<>(snapshot.stream().filter(s->s.timeSeconds()>after).toList());
                if(pending.size()>256) {
                    pending=new ArrayList<>(pending.subList(pending.size()-256,pending.size()));
                    var first=pending.getFirst();pending.set(0,new WaveformBuffer.Sample(first.timeSeconds(),OptionalDouble.empty(),WaveformBuffer.Quality.MISSING));
                }
                if(!pending.isEmpty()) {
                    ScopeNetworking.send(player,new ScopeNetworking.Frame(worldPosition,epoch,pending));
                    sentThrough.put(player.getUUID(),pending.getLast().timeSeconds());
                }
            }
            sentThrough.keySet().retainAll(active);
        }
    }
    public List<WaveformBuffer.Sample> samples() { return waveform.snapshot(); }
    public void receive(UUID nextEpoch,List<WaveformBuffer.Sample> samples) {
        if(!epoch.equals(nextEpoch)) { waveform=new WaveformBuffer(1024);epoch=nextEpoch; }
        var existing=waveform.snapshot();double last=existing.isEmpty()?-1:existing.getLast().timeSeconds();
        for(var s:samples) if(s.timeSeconds()>last) { waveform.append(s);last=s.timeSeconds(); }
    }
}
