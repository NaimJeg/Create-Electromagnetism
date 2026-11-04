package com.electromagnetism.content.instruments;
import com.electromagnetism.core.radio.AmRadio;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import java.util.*;

/** World-thread, dimension-local, bounded far-field toy channel; one tick frame transport latency. */
public final class RadioMedium {
    private RadioMedium() {}
    private record Transmission(long tick,AmRadio.Frame frame,Transmission previous) {}
    private static final Map<Level,Map<BlockPos,Transmission>> WORLDS=new WeakHashMap<>();
    public static void transmit(Level level,BlockPos pos,long tick,AmRadio.Frame frame) {
        var map=WORLDS.computeIfAbsent(level,k->new HashMap<>());
        map.entrySet().removeIf(e->e.getValue().tick()<tick-2);
        if(map.size()<64||map.containsKey(pos)) {
            var old=map.get(pos);
            var previous=old==null?null:old.tick()==tick?old.previous():new Transmission(old.tick(),old.frame(),null);
            map.put(pos.immutable(),new Transmission(tick,frame,previous));
        }
    }
    public static List<AmRadio.Path> paths(Level level,BlockPos receiver,long tick) {
        var map=WORLDS.getOrDefault(level,Map.of());var result=new ArrayList<AmRadio.Path>();
        for(var e:map.entrySet()) {
            var packet=e.getValue().tick()==tick-1?e.getValue():e.getValue().previous();
            if(packet==null||packet.tick()!=tick-1||!level.isLoaded(e.getKey())) continue;
            double distance=Math.sqrt(e.getKey().distSqr(receiver));
            if(distance>128) continue;
            double gain=1/Math.sqrt(1+distance*distance);
            result.add(new AmRadio.Path(packet.frame(),gain,-2*Math.PI*packet.frame().carrierHz()*distance/299792458.0));
        }
        return List.copyOf(result);
    }
    public static void remove(Level level,BlockPos pos) { var map=WORLDS.get(level);if(map!=null) map.remove(pos); }
}
