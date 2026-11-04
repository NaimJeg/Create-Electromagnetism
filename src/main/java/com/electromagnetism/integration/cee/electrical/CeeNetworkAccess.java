package com.electromagnetism.integration.cee.electrical;
import com.george_vi.electroenergetics.simulation.simulator.Network;
import com.george_vi.electroenergetics.foundation.nodes.DirectionalNodeConnection;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
public interface CeeNetworkAccess {
    int electromagnetism$nodeCount();
    int electromagnetism$index(int global);
    double[] electromagnetism$last();
    void electromagnetism$last(double[] values);
    void electromagnetism$currents(double[] values,Object2DoubleMap<DirectionalNodeConnection> output);
}
