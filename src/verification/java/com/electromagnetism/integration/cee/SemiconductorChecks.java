package com.electromagnetism.integration.cee;
import com.electromagnetism.core.model.*;
import com.electromagnetism.integration.cee.electrical.*;
import com.george_vi.electroenergetics.simulation.util.SparseMatrix;
import net.minecraft.core.BlockPos;
import java.util.*;
public final class SemiconductorChecks {
    static void near(double a,double b) { if(!Double.isFinite(a)||Math.abs(a-b)>1e-5) throw new AssertionError(a+" != "+b); }
    public static void main(String[] args) {
        for(boolean p:new boolean[]{false,true}) for(double gate:new double[]{0,4,8}) for(double load:new double[]{1,10,100}) {
            double sign=p?-1:1;
            var d=new ThreeTerminalDevice<>(new MosfetModel(p),new MosfetModel.Parameters(2,0.1,10),BlockPos.ZERO);
            var result=solve(d,gate*sign,12*sign,load);
            if(!result.accepted()) throw new AssertionError("MOS did not converge");
            double drain=result.voltages()[1]*sign;
            if(drain<0||drain>12.000001) throw new AssertionError("Passive load excursion");
            if(gate==0) near(drain,12/(1+load*1e-6));
            if(gate==4&&load==10) near(drain,2.1-Math.sqrt(2.1*2.1-2.4));
        }
        for(boolean p:new boolean[]{false,true}) for(double base:new double[]{0,0.6,0.7,0.9,-0.2}) {
            double sign=p?-1:1;
            var d=new ThreeTerminalDevice<>(new BjtModel(p),new BjtModel.Parameters(100,0.7,1),BlockPos.ZERO);
            var result=solve(d,base*sign,12*sign,10);
            if(!result.accepted()) throw new AssertionError("BJT did not converge: "+base);
            var op=d.evaluate(new TerminalVoltages(result.voltages()[0],result.voltages()[1],result.voltages()[2]));
            near(op.current(0)+op.current(1)+op.current(2),0);
        }
        System.out.println("Semiconductor loaded circuits passed: gate/bias, N/P mirror, load sweep, off state, full KCL residual");
    }
    static NonlinearNetworkSolver.Result solve(ThreeTerminalDevice<?,?> device,double control,double supply,double load) {
        var a=new SparseMatrix(5);double[] b=new double[5];
        a.set(0,3,1);a.set(3,0,1);b[3]=control;
        a.set(2,4,1);a.set(4,2,1);
        a.set(1,1,1/load);b[1]=supply/load;
        return NonlinearNetworkSolver.solve(a,b,List.of(new NonlinearNetworkAccess.Entry(device,new int[]{0,1,2})));
    }
}
