package com.electromagnetism.integration.cee;
import com.electromagnetism.integration.cee.electrical.CeeStepEvidence;
import com.george_vi.electroenergetics.simulation.util.*;
import org.objectweb.asm.*;
import java.util.*;

/** Executable checks of the exact worker hook and mutable solver contract; no world boot. */
public final class CeeContractChecks {
    public static void main(String[] args) throws Exception {
        var calls = new ArrayList<String>();
        try(var stream=CeeContractChecks.class.getResourceAsStream("/com/george_vi/electroenergetics/simulation/simulator/SimulationTicker.class")) {
            if(stream==null) throw new AssertionError("CEE class absent");
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                    if(!name.equals("lambda$tick$1")) return null;
                    if(!descriptor.equals("(Ljava/util/List;Ljava/util/Collection;J)Lcom/george_vi/electroenergetics/simulation/SimulationResults;")) throw new AssertionError("Worker signature changed");
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean iface) {
                            if(owner.contains("MicroTickingElectricalProperties") || owner.endsWith("LUSolver") || owner.endsWith("CholeskySolver")) calls.add(name);
                        }
                    };
                }
            },0);
        }
        if(!calls.equals(List.of("tick","solve","solve","afterTick"))) throw new AssertionError("CEE microstep contract changed: "+calls);
        for(boolean lu:new boolean[]{false,true}) {
            CeeStepEvidence.begin();
            var a=new SparseMatrix(2); a.set(0,0,2);a.set(0,1,-1);a.set(1,0,-1);a.set(1,1,2);
            double[] x=CeeStepEvidence.solve(a,new double[]{1,0},lu?LUSolver::solve:CholeskySolver::solve);
            near(x[0],2.0/3);near(x[1],1.0/3);
            if(!CeeStepEvidence.currentLinearSystemsValid()) throw new AssertionError("Correct solve rejected");
        }
        for(double wrong:new double[]{0,Double.NaN,Double.POSITIVE_INFINITY}) {
            CeeStepEvidence.begin();
            var a=new SparseMatrix(1);a.set(0,0,2);
            CeeStepEvidence.solve(a,new double[]{1},(matrix,rhs)->new double[]{wrong});
            if(CeeStepEvidence.currentLinearSystemsValid()) throw new AssertionError("Invalid result accepted");
            var b=new SparseMatrix(1);b.set(0,0,1);
            CeeStepEvidence.solve(b,new double[]{1},(matrix,rhs)->new double[]{1});
            if(CeeStepEvidence.currentLinearSystemsValid()) throw new AssertionError("Later solve erased batch failure");
        }
        System.out.println("CEE contract checks passed: worker descriptor, callback/solver order, LU/Cholesky analytic circuit");
    }
    static void near(double a,double b) { if(!Double.isFinite(a)||Math.abs(a-b)>1e-9) throw new AssertionError(a+" != "+b); }
}
