package com.electromagnetism.integration.cee.electrical;
import com.electromagnetism.core.model.*;
import com.george_vi.electroenergetics.simulation.util.*;
import java.util.*;

/** Newton extension of the SAME CEE island matrix, using CEE LU. No extra clock or node overlay. */
public final class NonlinearNetworkSolver {
    private NonlinearNetworkSolver() {}
    private static final ThreadLocal<NonlinearNetworkAccess> CURRENT=new ThreadLocal<>();
    public static void select(NonlinearNetworkAccess network) { CURRENT.set(network); }
    public static List<NonlinearNetworkAccess.Entry> take() {
        var network=CURRENT.get();CURRENT.remove();return network==null?List.of():network.electromagnetism$models();
    }
    public record Result(double[] voltages,boolean accepted,int iterations) {}
    public static Result solve(SparseMatrix base,double[] rhs,List<NonlinearNetworkAccess.Entry> entries) {
        for(var entry:entries) entry.device().beginMicrostep();
        double[] x=new double[base.size];
        for(int iteration=1;iteration<=64;iteration++) {
            SparseMatrix a=copy(base);double[] b=rhs.clone();
            for(var entry:entries) {
                var linear=entry.device().linearize();int[] id=entry.indices();
                for(int r=0;r<id.length;r++) {
                    b[id[r]]-=linear.affineCurrent(r);
                    for(int c=0;c<id.length;c++) a.set(id[r],id[c],a.getValue(id[r],id[c])+linear.derivative(r,c));
                }
            }
            x=LUSolver.solve(a,b);
            if(x==null) return new Result(new double[base.size],false,iteration);
            boolean finite=x.length==base.size;
            for(double v:x) finite &= Double.isFinite(v);
            if(!finite) return new Result(x,false,iteration);
            double[] residual=new double[base.size],scale=new double[base.size];
            base.multiplyAndFillInto(x,residual);
            for(int i=0;i<base.size;i++) {
                scale[i]=Math.abs(rhs[i]);residual[i]-=rhs[i];
                var row=base.data[i];int[] nz=row.getFlatNz();
                for(int j=0;j<row.getFlatNzSize();j++) scale[i]+=Math.abs(row.get(nz[j])*x[nz[j]]);
            }
            for(var entry:entries) {
                int[] id=entry.indices();double[] local=new double[id.length];
                for(int i=0;i<id.length;i++) local[i]=x[id[i]];
                var v=new TerminalVoltages(local);
                entry.device().candidate(v);
                OperatingPoint op;
                try { op=entry.device().evaluate(v); }
                catch(IllegalArgumentException invalidDomain) {
                    for(int r:id) residual[r]=Double.NaN;
                    continue;
                }
                for(int r=0;r<id.length;r++) { residual[id[r]]+=op.current(r);scale[id[r]]+=Math.abs(op.current(r)); }
            }
            boolean accepted=true;
            for(int i=0;i<base.size;i++) accepted &= Double.isFinite(residual[i]) && Double.isFinite(scale[i])
                    && Math.abs(residual[i])<=1e-9+1e-7*scale[i];
            if(accepted) return new Result(x,true,iteration);
        }
        return new Result(x,false,64);
    }
    static SparseMatrix copy(SparseMatrix matrix) {
        var copy=new SparseMatrix(matrix.size);
        for(int i=0;i<matrix.size;i++) { var row=matrix.data[i];int[] nz=row.getFlatNz();
            for(int j=0;j<row.getFlatNzSize();j++) copy.set(i,nz[j],row.get(nz[j])); }
        return copy;
    }
}
