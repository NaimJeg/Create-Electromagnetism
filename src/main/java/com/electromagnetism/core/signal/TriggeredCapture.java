package com.electromagnetism.core.signal;
import java.util.*;

/** Single-shot future-edge acquisition. Gaps disarm the partial capture, never bridge an edge. */
public final class TriggeredCapture {
    private final double threshold,armedAfter;
    private final int before,after;
    private final ArrayDeque<WaveformBuffer.Sample> history=new ArrayDeque<>();
    private final List<WaveformBuffer.Sample> captured=new ArrayList<>();
    private WaveformBuffer.Sample previous;
    private int remaining=-1;
    private double consumed=-1;
    public TriggeredCapture(double threshold,double armedAfter,int before,int after) {
        if(!Double.isFinite(threshold)||!Double.isFinite(armedAfter)||before<0||after<1||before+after>1024) throw new IllegalArgumentException("Trigger limits");
        this.threshold=threshold;this.armedAfter=armedAfter;this.before=before;this.after=after;
    }
    public void accept(List<WaveformBuffer.Sample> samples) {
        for(var sample:samples) {
            if(sample.timeSeconds()<=consumed) continue;consumed=sample.timeSeconds();
            if(remaining==0) return;
            if(sample.quality()!=WaveformBuffer.Quality.VALID) { history.clear();captured.clear();remaining=-1;previous=null;continue; }
            if(remaining<0 && sample.timeSeconds()>armedAfter && previous!=null
                && previous.value().getAsDouble()<threshold && sample.value().getAsDouble()>=threshold) {
                captured.addAll(history);remaining=after;
            }
            if(remaining>0) { captured.add(sample);remaining--; }
            history.addLast(sample);while(history.size()>before) history.removeFirst();previous=sample;
        }
    }
    public Optional<List<WaveformBuffer.Sample>> result() { return remaining==0?Optional.of(List.copyOf(captured)):Optional.empty(); }
}
