package com.electromagnetism.core.signal;
import com.electromagnetism.core.radio.*;
import java.util.*;
public final class RadioChecks {
    static void near(double a,double b) { if(!Double.isFinite(a)||Math.abs(a-b)>1e-8) throw new AssertionError(a+" != "+b); }
    public static void main(String[] args) {
        double[] message=new double[320];for(int i=0;i<message.length;i++) message[i]=Math.sin(2*Math.PI*2*i/320);
        var frame=AmRadio.transmit(1000,0,0,320,message,2,0.5);near(frame.energy(),1);
        var path=new AmRadio.Path(frame,1,0);
        for(int i=0;i<320;i++) near((AmRadio.receive(List.of(path),1000,i/320.0).magnitude()/frame.carrierAmplitude()-1)*2,message[i]);
        var inverted=new AmRadio.Path(frame,1,Math.PI);
        near(AmRadio.receive(List.of(path,inverted),1000,0.1).magnitude(),0);
        near(AmRadio.receive(List.of(path,path),1000,0.1).magnitude(),2*AmRadio.receive(List.of(path),1000,0.1).magnitude());
        var quiet=AmRadio.transmit(1000,0,0,320,message,0,0.5);near(quiet.energy(),0);
        var weak=AmRadio.transmit(1000,0,0,320,message,0.5,0.5);near(weak.energy(),0.25);
        near(AmRadio.receive(List.of(new AmRadio.Path(frame,0.5,0)),1000,0).magnitude(),frame.at(0)*0.5);
        var detuned=AmRadio.transmit(1001,0,0,320,message,2,0.5);
        near(AmRadio.receive(List.of(new AmRadio.Path(detuned,1,0)),1000,0.25).i(),0);
        for(double power:new double[]{0,0.001,1}) for(double signal:new double[]{-100,0,100}) {
            double v=AmRadio.outputVoltage(signal,10,600,power);
            for(double load:new double[]{-100,-1,0,1,10,100})
                if(AmRadio.deliveredWatts(v,load,600)>power+1e-9) throw new AssertionError("Created output energy");
        }
        var store=new EnergyReservoir(1);near(store.deposit(2),1);near(store.reserve(0.75),0.75);near(store.reserve(1),0.25);near(store.reserve(1),0);
        var trigger=new TriggeredCapture(0,1,1,2);
        trigger.accept(List.of(WaveformBuffer.Sample.valid(1,-1),WaveformBuffer.Sample.valid(2,1),WaveformBuffer.Sample.valid(3,2)));
        if(trigger.result().orElseThrow().size()!=3) throw new AssertionError("Trigger acquisition");
        System.out.println("Radio/trigger checks passed: AM recovery, energy, undervoltage/off, interference, phase/frequency offset, loading, bounded storage, future edge");
    }
}
