package com.electromagnetism.integration.cee.mixin;

import com.electromagnetism.integration.cee.electrical.*;
import com.electromagnetism.config.ModConfigs;
import com.george_vi.electroenergetics.simulation.*;
import com.george_vi.electroenergetics.simulation.simulator.*;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.foundation.nodes.DirectionalNodeConnection;
import it.unimi.dsi.fastutil.objects.ObjectDoublePair;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

/** Pinned worker replacement: CEE topology and matrix construction, transactional time integration. */
@Mixin(value=SimulationTicker.class,remap=false)
public abstract class SimulationEvidenceMixin {
    @Shadow private CircuitBuilder circuitBuilder;
    @Shadow private SimulationStats stats;
    @Shadow public int microTicks;
    @Shadow @Final public InfrastructureSavedData sd;
    @Shadow @Final public ServerLevel level;
    @Unique private CeeTransientEngine electromagnetism$engine;
    @Unique private String electromagnetism$lastFailure="";
    @Inject(method="lambda$tick$1",at=@At("HEAD"),cancellable=true)
    private void electromagnetism$run(List<ObjectDoublePair<DirectionalNodeConnection>> connections,Collection<?> devices,long started,
            CallbackInfoReturnable<SimulationResults> cir) {
        CeeStepEvidence.begin();
        try {
            circuitBuilder.connectAll(connections);
            var separated=circuitBuilder.dfsAndGround();
            stats.totalNodes=circuitBuilder.allNodes().size();stats.totalDevices=devices.size();
            stats.totalSeparatedNodes=new int[separated.size()];stats.totalOptimizedNodes=new int[separated.size()];
            stats.totalMicroTickers=0;
            var networks=new ArrayList<Network>();int index=0;
            for(var island:separated) {
                stats.totalSeparatedNodes[index]=island.size();stats.totalOptimizedNodes[index++]=island.size();
                if(island.size()<2) continue;
                boolean active=false;
                for(var node:island) for(var properties:node.adjacency.values()) active|=!properties.isSimpleResistor();
                if(!active) continue;
                var network=new Network(island,circuitBuilder,sd);
                // Preserve dynamic terminals and avoid reconstruction side effects during probes.
                network.mapToSimNodes();networks.add(network);
            }
            stats.totalMicroTickers=circuitBuilder.microTickers.size();
            if(electromagnetism$engine==null) electromagnetism$engine=new CeeTransientEngine(level.getGameTime()/20.0);
            boolean precise=ModConfigs.server().preciseTransient.get();
            var results=electromagnetism$engine.run(circuitBuilder,networks,sd,microTicks,precise);
            var failure=electromagnetism$engine.report().failure();
            if(!failure.equals(electromagnetism$lastFailure)) {
                if(!failure.isEmpty()) org.slf4j.LoggerFactory.getLogger("Electromagnetism/Transient").warn("Electrical simulation paused: {}",failure);
                electromagnetism$lastFailure=failure;
            }
            CeeStepEvidence.report(results,electromagnetism$engine.report());
            CeeStepEvidence.finish(results);cir.setReturnValue(results);
        } finally { CeeStepEvidence.clear();NonlinearNetworkSolver.take(); }
    }
}
