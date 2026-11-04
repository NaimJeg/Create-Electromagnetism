package com.electromagnetism.integration.cee.electrical;
import com.electromagnetism.core.model.*;
import net.minecraft.core.BlockPos;
/** Compatibility entry point for memoryless three-terminal devices. */
public final class ThreeTerminalDevice<P,T> extends StatefulDevice<P,NoState,T> {
    public ThreeTerminalDevice(DeviceModel<P,NoState,T> model,P parameters,BlockPos pos) {
        super(model,parameters,NoState.INSTANCE,pos);
        if(nodes.length!=3) throw new IllegalArgumentException("Expected three terminals");
    }
}
