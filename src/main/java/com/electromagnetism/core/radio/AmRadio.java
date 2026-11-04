package com.electromagnetism.core.radio;
import com.electromagnetism.core.model.Numbers;
import java.util.*;

/** Complex-envelope AM behavior model. It does not solve RF carrier transients. */
public final class AmRadio {
    private AmRadio() {}
    public record Frame(double carrierHz,double phase,double start,double sampleRate,double carrierAmplitude,double[] envelope) {
        public Frame {
            Numbers.positive("carrier",carrierHz);Numbers.finite("phase",phase);Numbers.nonNegative("start",start);
            Numbers.positive("sample rate",sampleRate);Numbers.nonNegative("carrier amplitude",carrierAmplitude);
            if(envelope.length==0||envelope.length>4096) throw new IllegalArgumentException("Frame size");
            envelope=envelope.clone();for(double v:envelope) Numbers.nonNegative("envelope",v);
        }
        @Override public double[] envelope() { return envelope.clone(); }
        public int size() { return envelope.length; }
        public double at(int index) { return envelope[index]; }
        public double energy() { double sum=0;for(double v:envelope) sum+=v*v/sampleRate;return sum; }
    }
    /** Unit impedance analytic envelope: envelope squared is watts; efficiency includes all RF losses. */
    public static Frame transmit(double carrier,double phase,double start,double sampleRate,double[] message,double inputJoules,double efficiency) {
        Numbers.nonNegative("input energy",inputJoules);Numbers.range("efficiency",efficiency,0,1);
        double sum=0;for(double m:message) { Numbers.range("message",m,-1,1);sum+=Math.pow(1+0.5*m,2); }
        if(message.length==0) throw new IllegalArgumentException("Empty message");
        double amplitude=Math.sqrt(inputJoules*efficiency*sampleRate/sum);
        double[] envelope=new double[message.length];for(int i=0;i<envelope.length;i++) envelope[i]=amplitude*(1+0.5*message[i]);
        return new Frame(carrier,phase,start,sampleRate,amplitude,envelope);
    }
    public record Path(Frame frame,double amplitudeGain,double phaseShift) {
        public Path { Objects.requireNonNull(frame);Numbers.range("path gain",amplitudeGain,0,1);Numbers.finite("phase shift",phaseShift); }
    }
    public record IQ(double i,double q) { public double magnitude() { return Math.hypot(i,q); } }
    public static IQ receive(List<Path> paths,double receiverHz,double time) {
        Numbers.positive("receiver carrier",receiverHz);Numbers.nonNegative("receive time",time);
        double i=0,q=0;
        for(var path:paths) {
            var f=path.frame();
            if(Math.abs(f.carrierHz()-receiverHz)>8) continue; // Explicit 16Hz toy receiver passband.
            int index=(int)Math.floor((time-f.start())*f.sampleRate()+1e-9);
            if(index<0||index>=f.size()) continue;
            double phase=f.phase()+path.phaseShift()+2*Math.PI*(f.carrierHz()-receiverHz)*time;
            double amplitude=f.at(index)*path.amplitudeGain();
            i+=amplitude*Math.cos(phase);q+=amplitude*Math.sin(phase);
        }
        return new IQ(i,q);
    }
    /** Finite Thevenin output: maximum delivered load power is Voc²/(4R). */
    public static double outputVoltage(double signal,double gain,double resistance,double availableWatts) {
        Numbers.finite("signal",signal);Numbers.nonNegative("gain",gain);Numbers.positive("output resistance",resistance);Numbers.nonNegative("available watts",availableWatts);
        double maximum=Math.sqrt(4*resistance*availableWatts);
        return Math.clamp(signal*gain,-maximum,maximum);
    }
    public static double deliveredWatts(double openCircuitVoltage,double loadVoltage,double resistance) {
        return loadVoltage*(openCircuitVoltage-loadVoltage)/resistance;
    }
}
