package com.electromagnetism.client;

import com.electromagnetism.content.instruments.InstrumentBlockEntity;
import com.electromagnetism.core.signal.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Client display controls never modify the measured circuit or its simulation clock. */
public final class OscilloscopeScreen extends Screen {
    private final InstrumentBlockEntity instrument;
    private double volts = 0.02, seconds = 0.1, center, timeOffset;
    private final List<Button> controls = new ArrayList<>();
    private List<WaveformBuffer.Sample> frozen;
    private int subscriptionTicks;
    private boolean triggered;
    private List<WaveformBuffer.Sample> triggeredCapture;
    private TriggeredCapture trigger;
    private int left, top, panelWidth, panelHeight, plotX, plotY, plotWidth, plotHeight;

    public OscilloscopeScreen(InstrumentBlockEntity instrument) {
        super(Component.translatable("electromagnetism.scope.title"));
        this.instrument = instrument;
    }
    @Override public boolean isPauseScreen() { return false; }
    private Component text(String key) { return Component.translatable("electromagnetism.scope." + key); }
    @Override protected void init() {
        controls.clear();
        panelWidth = Math.min(480, width - 20);
        panelHeight = Math.min(310, height - 12);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        plotX = left + 42; plotY = top + 32;
        plotWidth = panelWidth - 58; plotHeight = Math.max(40, panelHeight - 152);
        int row = plotY + plotHeight + 48;
        control(0, row, "gain_up", () -> volts = Math.max(1e-6, volts / 2));
        control(1, row, "gain_down", () -> volts = Math.min(1e6, volts * 2));
        control(2, row, "time_in", () -> seconds = Math.max(1e-6, viewport().secondsPerDivision() / 2));
        control(3, row, "time_out", () -> seconds = Math.min(60, viewport().secondsPerDivision() * 2));
        control(4, row, "freeze", () -> { frozen = frozen == null ? List.copyOf(instrument.samples()) : null; });
        row += 23;
        control(0, row, "up", () -> center += volts);
        control(1, row, "down", () -> center -= volts);
        control(2, row, "older", () -> timeOffset = viewport().timeOffsetSeconds() - viewport().secondsPerDivision());
        control(3, row, "newer", () -> timeOffset = Math.min(0, viewport().timeOffsetSeconds() + viewport().secondsPerDivision()));
        control(4, row, "reset", () -> { volts = 0.02; seconds = 0.1; center = timeOffset = 0; frozen = null; triggered=false;triggeredCapture=null;trigger=null; });
        controls.add(Button.builder(text("trigger"), b -> { triggered=!triggered;triggeredCapture=null;
            var samples=instrument.samples();
            trigger=new TriggeredCapture(0,samples.isEmpty()?0:samples.getLast().timeSeconds(),128,256); })
                .bounds(left+8,row+23,140,20).build());
        com.electromagnetism.content.instruments.ScopeNetworking.request(instrument.getBlockPos());
        addRenderableWidget(new ScopePanel(left, top, panelWidth, panelHeight));
    }
    private void control(int column, int y, String key, Runnable action) {
        int slot = (panelWidth - 16) / 5;
        controls.add(Button.builder(text(key), b -> action.run()).bounds(left + 8 + column * slot, y, slot - 3, 20).build());
    }
    private String number(double value) { return String.format(Locale.ROOT, "%.3g", value == 0 ? 0 : value); }
    @Override public void tick() {
        if (++subscriptionTicks%20==0) com.electromagnetism.content.instruments.ScopeNetworking.request(instrument.getBlockPos());
        if(triggered && triggeredCapture==null && trigger!=null) {
            trigger.accept(instrument.samples());triggeredCapture=trigger.result().orElse(null);
        }
        if (instrument.isRemoved() || minecraft.level == null || minecraft.player == null
                || minecraft.player.distanceToSqr(instrument.getBlockPos().getCenter()) > 64) onClose();
    }
    private ScopeViewport viewport() {
        var samples = frozen != null ? frozen : triggeredCapture != null ? triggeredCapture : instrument.samples();
        var view = new ScopeViewport(volts, seconds, center, timeOffset);
        if (samples.size() < 2) return view;
        double history = samples.getLast().timeSeconds() - samples.getFirst().timeSeconds();
        double minimum = Math.min(history, 2 * (samples.getLast().timeSeconds() - samples.get(samples.size() - 2).timeSeconds()));
        return view.fitHistory(history, minimum);
    }
    private void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xf0142028);
        g.drawString(font, title, left + 10, top + 9, 0xffffff);
        g.drawString(font, text(frozen != null || triggeredCapture != null ? "held" : triggered ? "armed" : "live"), left + panelWidth - 80, top + 9, 0x66ffaa);
        var samples = frozen != null ? frozen : triggeredCapture != null ? triggeredCapture : instrument.samples();
        var view = viewport();
        g.fill(plotX, plotY, plotX + plotWidth, plotY + plotHeight, 0xff081014);
        for (int i = 0; i <= 10; i++) g.fill(plotX + i * plotWidth / 10, plotY, plotX + i * plotWidth / 10 + 1, plotY + plotHeight, 0xff263b43);
        for (int i = 0; i <= 8; i++) g.fill(plotX, plotY + i * plotHeight / 8, plotX + plotWidth, plotY + i * plotHeight / 8 + 1, 0xff263b43);
        g.drawString(font, number(center + volts * 4) + " V", left + 3, plotY - 10, 0xaaaaaa);
        g.drawString(font, number(center) + " V", left + 3, plotY + plotHeight / 2, 0xaaaaaa);
        g.drawString(font, number(center - volts * 4) + " V", left + 3, plotY + plotHeight - 8, 0xaaaaaa);
        // The latest captured sample is always t=0. Only the fixed span and user offset affect axis labels.
        for (int i = 0; i <= 2; i++) {
            String label = number(view.relativeTime(i / 2.0)) + " s";
            int x = plotX + i * plotWidth / 2;
            g.drawString(font, label, Math.min(x, plotX + plotWidth - font.width(label)), plotY + plotHeight + 4, 0xaaaaaa);
        }
        var visible = new ArrayList<WaveformBuffer.Sample>();
        boolean stale = samples.isEmpty() || (frozen == null && triggeredCapture == null && minecraft.level != null
                && minecraft.level.getGameTime() / 20.0 - samples.getLast().timeSeconds() > 0.25);
        if (!samples.isEmpty()) {
            double reference = samples.getLast().timeSeconds();
            double[] low = new double[plotWidth], high = new double[plotWidth];
            boolean[] invalid = new boolean[plotWidth];
            Arrays.fill(low, Double.POSITIVE_INFINITY); Arrays.fill(high, Double.NEGATIVE_INFINITY);
            for (var s : samples) {
                double fraction = view.horizontalFraction(s.timeSeconds(), reference);
                if (fraction < 0 || fraction > 1) continue;
                visible.add(s);
                int col = Math.min(plotWidth - 1, (int)(fraction * (plotWidth - 1)));
                if (s.value().isEmpty()) { invalid[col] = true; continue; }
                double v = s.value().getAsDouble(); low[col] = Math.min(low[col], v); high[col] = Math.max(high[col], v);
            }
            g.enableScissor(plotX, plotY, plotX + plotWidth, plotY + plotHeight);
            int previous = -1, previousY = 0;
            for (int col = 0; col < plotWidth; col++) {
                if (invalid[col]) { g.fill(plotX + col, plotY, plotX + col + 1, plotY + plotHeight, 0x99ff5533); previous = -1; continue; }
                if (low[col] > high[col]) continue;
                int y1 = plotY + (int)(Math.clamp(view.verticalFraction(high[col]), -1, 2) * plotHeight);
                int y2 = plotY + (int)(Math.clamp(view.verticalFraction(low[col]), -1, 2) * plotHeight);
                g.fill(plotX + col, y1, plotX + col + 1, y2 + 1, stale ? 0xff667777 : 0xff55ff99);
                int middle = (y1 + y2) / 2;
                if (previous >= 0) {
                    for (int x = previous + 1; x <= col; x++) {
                        int y = previousY + (middle - previousY) * (x - previous) / (col - previous);
                        g.fill(plotX + x, y, plotX + x + 1, y + 1, stale ? 0xff667777 : 0xff55ff99);
                    }
                }
                previous = col; previousY = middle;
            }
            g.disableScissor();
        }
        String settings = number(volts) + " V/div | " + number(view.secondsPerDivision()) + " s/div | Y " + number(center) + " V | X " + number(view.timeOffsetSeconds()) + " s";
        g.drawString(font, settings, left + 9, plotY + plotHeight + 18, 0xeeeeee);
        var stats = WaveformMeasurements.statistics(visible);
        var hz = stats.isPresent() ? WaveformMeasurements.frequency(visible, stats.get().mean(), true) : OptionalDouble.empty();
        boolean undersampled=hz.isPresent() && visible.size()>1 && hz.getAsDouble()*(visible.getLast().timeSeconds()-visible.getFirst().timeSeconds())*8>visible.size();
        String status = undersampled ? text("undersampled").getString() : stale ? text("stale").getString() : stats.isEmpty() ? text("incomplete").getString()
                : "RMS " + number(stats.get().rms()) + " V | f " + (hz.isPresent() ? number(hz.getAsDouble()) : "?") + " Hz | " + visible.size() + " samples";
        g.drawString(font, status, left + 9, plotY + plotHeight + 31, stale ? 0xffaa44 : 0xaaaaaa);
        for (var control : controls) control.render(g, mouseX, mouseY, partialTick);
    }
    /** One screen widget owns graph, axes and controls. Screen draws its blur before this widget. */
    private final class ScopePanel extends AbstractWidget {
        private int selected = -1;
        ScopePanel(int x, int y, int width, int height) { super(x, y, width, height, title); }
        @Override protected void renderWidget(GuiGraphics g, int x, int y, float partialTick) {
            renderPanel(g, x, y, partialTick);
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            for (int i = 0; i < controls.size(); i++) {
                if (controls.get(i).mouseClicked(x, y, button)) {
                    select(i);
                    return true;
                }
            }
            return super.mouseClicked(x, y, button);
        }
        private void select(int index) {
            selected = index;
            for (int i = 0; i < controls.size(); i++) controls.get(i).setFocused(i == index);
        }
        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key == 258) {
                select(Math.floorMod(selected + (hasShiftDown() ? -1 : 1), controls.size()));
                return true;
            }
            return selected >= 0 && controls.get(selected).keyPressed(key, scanCode, modifiers);
        }
        @Override public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (!focused) { for (var button : controls) button.setFocused(false); }
            else if (selected >= 0) controls.get(selected).setFocused(true);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

}
