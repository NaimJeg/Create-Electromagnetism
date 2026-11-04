package com.electromagnetism.client;

import com.electromagnetism.content.instruments.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Locale;

public class SignalGeneratorScreen extends Screen {
    private final SignalGeneratorBlockEntity instrument;
    public SignalGeneratorScreen(SignalGeneratorBlockEntity instrument) {
        super(Component.translatable("block.electromagnetism.signal_generator")); this.instrument=instrument;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected void init() {
        String[] keys={"wave","frequency_up","frequency_down","amplitude_up","amplitude_down","offset_up","offset_down","enabled"};
        for(int i=0;i<keys.length;i++) {
            final int action=i;
            addRenderableWidget(Button.builder(Component.translatable("electromagnetism.generator."+keys[i]),b->
                PacketDistributor.sendToServer(new ScopeNetworking.GeneratorControl(instrument.getBlockPos(),action)))
                .bounds(width/2-150+(i%2)*152,height/2-30+(i/2)*24,148,20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("electromagnetism.generator.observe"),b->minecraft.setScreen(new OscilloscopeScreen(instrument)))
                .bounds(width/2-150,height/2+72,300,20).build());
    }
    @Override public void tick() {
        if(instrument.isRemoved() || minecraft.player==null || minecraft.player.distanceToSqr(instrument.getBlockPos().getCenter())>64) onClose();
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        super.render(g,mouseX,mouseY,partial);
        var s=instrument.settings();
        g.drawCenteredString(font,title,width/2,height/2-85,0xff80ded0);
        g.drawCenteredString(font,Component.translatable("electromagnetism.generator.status",Component.translatable("electromagnetism.generator."+s.wave().name().toLowerCase(Locale.ROOT)),String.format(Locale.ROOT,"%.3g",s.frequency()),String.format(Locale.ROOT,"%.2f",s.amplitude()),String.format(Locale.ROOT,"%.2f",s.offset()),Component.translatable("electromagnetism.generator."+(s.enabled()?"on":"off"))),width/2,height/2-65,0xffffffff);
        g.drawCenteredString(font,Component.translatable("electromagnetism.generator.limits"),width/2,height/2-48,0xffaaaaaa);
    }
}
