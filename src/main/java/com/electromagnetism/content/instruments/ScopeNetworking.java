package com.electromagnetism.content.instruments;

import com.electromagnetism.ElectromagnetismMod;
import com.electromagnetism.core.signal.WaveformBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;

/** One instrument per observer, renewed every second; no world/chunk loading on requests. */
public final class ScopeNetworking {
    private ScopeNetworking() {}
    private record Subscription(BlockPos pos,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,long expires) {}
    private static final Map<UUID,Subscription> SUBSCRIPTIONS=new HashMap<>();
    public static boolean subscribed(ServerPlayer player,BlockPos pos,long tick) {
        var sub=SUBSCRIPTIONS.get(player.getUUID());
        if(sub!=null && sub.expires()<tick) { SUBSCRIPTIONS.remove(player.getUUID()); return false; }
        return sub!=null && sub.pos().equals(pos) && sub.dimension().equals(player.level().dimension()) && player.distanceToSqr(pos.getCenter())<=64;
    }
    public record Request(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ElectromagnetismMod.rl("scope_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->b.writeBlockPos(p.pos()), b->new Request(b.readBlockPos()));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record Frame(BlockPos pos,UUID epoch,List<WaveformBuffer.Sample> samples) implements CustomPacketPayload {
        public Frame { samples=List.copyOf(samples); if(samples.size()>256) throw new IllegalArgumentException("Scope packet budget"); }
        public static final Type<Frame> TYPE=new Type<>(ElectromagnetismMod.rl("scope_frame"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Frame> CODEC=StreamCodec.of((b,p)-> {
            b.writeBlockPos(p.pos());b.writeUUID(p.epoch());b.writeVarInt(p.samples().size());
            for(var s:p.samples()) { b.writeDouble(s.timeSeconds());b.writeByte(s.quality().ordinal());if(s.value().isPresent()) b.writeDouble(s.value().getAsDouble()); }
        },b-> {
            var pos=b.readBlockPos();var epoch=b.readUUID();int count=b.readVarInt();
            if(count<0||count>256) throw new IllegalArgumentException("Scope frame too large");
            var samples=new ArrayList<WaveformBuffer.Sample>(count);
            for(int i=0;i<count;i++) {
                double time=b.readDouble();int q=b.readUnsignedByte();
                if(q>=WaveformBuffer.Quality.values().length) throw new IllegalArgumentException("Bad scope quality");
                var quality=WaveformBuffer.Quality.values()[q];
                samples.add(new WaveformBuffer.Sample(time,quality==WaveformBuffer.Quality.VALID?OptionalDouble.of(b.readDouble()):OptionalDouble.empty(),quality));
            }
            return new Frame(pos,epoch,samples);
        });
        @Override public Type<Frame> type() { return TYPE; }
    }
    public record GeneratorControl(BlockPos pos, int action) implements CustomPacketPayload {
        public static final Type<GeneratorControl> TYPE = new Type<>(ElectromagnetismMod.rl("generator_control"));
        public static final StreamCodec<RegistryFriendlyByteBuf,GeneratorControl> CODEC = StreamCodec.of((b,p)-> { b.writeBlockPos(p.pos()); b.writeByte(p.action()); }, b->new GeneratorControl(b.readBlockPos(),b.readUnsignedByte()));
        @Override public Type<GeneratorControl> type() { return TYPE; }
    }
    public static void forget(UUID player) { SUBSCRIPTIONS.remove(player); }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("1");
        registrar.playToServer(GeneratorControl.TYPE,GeneratorControl.CODEC,(packet,context)->context.enqueueWork(()-> {
            var player=context.player(); var level=player.level();
            if(packet.action()<0 || packet.action()>7 || !level.isLoaded(packet.pos()) || player.distanceToSqr(packet.pos().getCenter())>64 || player.isSpectator()) return;
            if(level.getBlockEntity(packet.pos()) instanceof SignalGeneratorBlockEntity be) be.adjust(packet.action());
        }));
        registrar.playToServer(Request.TYPE,Request.CODEC,(request,context)->context.enqueueWork(()-> {
            if(!(context.player() instanceof ServerPlayer player)) return;
            var level=player.serverLevel();
            if(!level.isLoaded(request.pos())||player.distanceToSqr(request.pos().getCenter())>64
                    ||!(level.getBlockEntity(request.pos()) instanceof InstrumentBlockEntity)) return;
            SUBSCRIPTIONS.put(player.getUUID(),new Subscription(request.pos(),level.dimension(),level.getGameTime()+40));
        }));
        registrar.playToClient(Frame.TYPE,Frame.CODEC,(frame,context)->context.enqueueWork(()-> {
            var level=context.player().level();
            if(level.isLoaded(frame.pos()) && level.getBlockEntity(frame.pos()) instanceof InstrumentBlockEntity be) be.receive(frame.epoch(),frame.samples());
        }));
    }
    public static void request(BlockPos pos) { PacketDistributor.sendToServer(new Request(pos)); }
    public static void send(ServerPlayer player,Frame frame) { PacketDistributor.sendToPlayer(player,frame); }
}
