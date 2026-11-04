package com.electromagnetism.content.instruments;

import com.electromagnetism.core.signal.SignalGenerator;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SignalGeneratorBlockEntity extends InstrumentBlockEntity {
    private SignalGenerator settings = new SignalGenerator(SignalGenerator.Wave.SINE, 2, 1, 0, false);
    public SignalGeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type,pos,state); }
    public SignalGenerator settings() { return settings; }
    public void adjust(int action) {
        var s = settings;
        settings = switch(action) {
            case 0 -> new SignalGenerator(SignalGenerator.Wave.values()[(s.wave().ordinal()+1)%4],s.frequency(),s.amplitude(),s.offset(),s.enabled());
            case 1,2 -> new SignalGenerator(s.wave(),s.frequency()*(action==1?2:0.5),s.amplitude(),s.offset(),s.enabled());
            case 3,4 -> new SignalGenerator(s.wave(),s.frequency(),s.amplitude()+(action==3?0.25:-0.25),s.offset(),s.enabled());
            case 5,6 -> new SignalGenerator(s.wave(),s.frequency(),s.amplitude(),s.offset()+(action==5?0.25:-0.25),s.enabled());
            case 7 -> new SignalGenerator(s.wave(),s.frequency(),s.amplitude(),s.offset(),!s.enabled());
            default -> s;
        };
        notifyUpdate();
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag,registries,clientPacket);
        tag.putInt("Wave",settings.wave().ordinal()); tag.putDouble("Frequency",settings.frequency());
        tag.putDouble("Amplitude",settings.amplitude()); tag.putDouble("Offset",settings.offset()); tag.putBoolean("Enabled",settings.enabled());
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag,registries,clientPacket);
        int wave=tag.getInt("Wave");
        settings=new SignalGenerator(SignalGenerator.Wave.values()[Math.clamp(wave,0,3)],
                tag.contains("Frequency")?tag.getDouble("Frequency"):2,
                tag.contains("Amplitude")?tag.getDouble("Amplitude"):1,tag.getDouble("Offset"),tag.getBoolean("Enabled"));
    }
}
