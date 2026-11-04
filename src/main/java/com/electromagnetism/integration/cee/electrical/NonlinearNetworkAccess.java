package com.electromagnetism.integration.cee.electrical;
import java.util.List;
public interface NonlinearNetworkAccess {
    record Entry(SolverDevice device,int[] indices) {}
    List<Entry> electromagnetism$models();
}
