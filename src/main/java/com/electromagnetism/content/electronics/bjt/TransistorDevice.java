package com.electromagnetism.content.electronics.bjt;

import com.electromagnetism.core.model.*;
import com.electromagnetism.integration.cee.electrical.ThreeTerminalDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Three-terminal pure model stamped into CEE's complete island Jacobian.
 * Parameters freeze per tick; solver iterations use physical multi-terminal residuals.
 */
public class TransistorDevice extends SimpleElectricalDevice {
    /** 电流放大倍数 β（玩家可调，1..1000）。 */
    public double beta = 100;
    /** 基射极导通电压 V_BE(on)（单位：伏）。 */
    public double vbeOn = 0.7;
    /** 最大集电极电流 I_C max（单位：安），用于限制输出电流源。 */
    public double icMax = 1;
    /** PNP 变体为 true；由器件类型工厂一次性设定。 */
    public boolean pnp;

    public TransistorDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        beta = ElectricalLimits.BETA.sanitize(beta);
        vbeOn = ElectricalLimits.VBE.sanitize(vbeOn);
        icMax = ElectricalLimits.COLLECTOR_CURRENT.sanitize(icMax);
        new ThreeTerminalDevice<>(new BjtModel(pnp),new BjtModel.Parameters(beta,vbeOn,icMax),pos).connect(bridges);
    }

    /** 从 NBT 恢复参数；缺失或非有限值使用默认值，越界值钳制到合法区间。 */
    @Override
    public void read(CompoundTag tag) {
        beta = tag.contains("Beta") ? ElectricalLimits.BETA.sanitize(tag.getDouble("Beta"))
                : ElectricalLimits.BETA.defaultValue();
        vbeOn = tag.contains("VbeOn") ? ElectricalLimits.VBE.sanitize(tag.getDouble("VbeOn"))
                : ElectricalLimits.VBE.defaultValue();
        icMax = tag.contains("IcMax") ? ElectricalLimits.COLLECTOR_CURRENT.sanitize(tag.getDouble("IcMax"))
                : ElectricalLimits.COLLECTOR_CURRENT.defaultValue();
    }

    /** 把三个参数写入 NBT 持久化。 */
    @Override
    public void write(CompoundTag tag) {
        tag.putDouble("Beta", beta);
        tag.putDouble("VbeOn", vbeOn);
        tag.putDouble("IcMax", icMax);
    }

    /**
     * 判断方块变化时是否需要重建器件：
     * 换成 NPN <-> PNP 时必须重建，让极性跟随方块本身。
     */
    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        // 在 NPN <-> PNP 之间切换时必须重建器件，让极性跟随方块。
        return !(newState.getBlock() instanceof TransistorBlock block) || block.isPnp() != pnp;
    }
}