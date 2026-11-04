package com.electromagnetism.integration.cee;
import com.electromagnetism.content.instruments.ScopeNetworking;
import com.electromagnetism.core.signal.WaveformBuffer;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.*;
import java.util.*;
public final class ScopeCodecChecks {
    public static void main(String[] args) {
        var pos=new BlockPos(1,2,3);var epoch=UUID.randomUUID();
        var samples=List.of(WaveformBuffer.Sample.valid(1,0.5),new WaveformBuffer.Sample(2,OptionalDouble.empty(),WaveformBuffer.Quality.UNCONVERGED));
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            ScopeNetworking.Frame.CODEC.encode(buffer,new ScopeNetworking.Frame(pos,epoch,samples));
            var decoded=ScopeNetworking.Frame.CODEC.decode(buffer);
            if(!decoded.pos().equals(pos)||!decoded.epoch().equals(epoch)||!decoded.samples().equals(samples)) throw new AssertionError("Quality/value frame round trip");
            buffer.clear();buffer.writeBlockPos(pos);buffer.writeUUID(epoch);buffer.writeVarInt(257);
            try { ScopeNetworking.Frame.CODEC.decode(buffer);throw new AssertionError("Oversized frame accepted"); }
            catch(IllegalArgumentException expected) {}
        } finally { buffer.release(); }
        System.out.println("Scope codec checks passed: quality/epoch/time round trip and pre-allocation packet bound");
    }
}
