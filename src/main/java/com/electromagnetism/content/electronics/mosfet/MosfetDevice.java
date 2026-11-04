package com.electromagnetism.content.electronics.mosfet;

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
public class MosfetDevice extends SimpleElectricalDevice {
    /** 阈值电压 V_GS(th)（单位：伏）。 */
    public double vth = 2;
    /** 导通电阻 R_DS(on)（单位：欧姆）。 */
    public double rdsOn = 0.1;
    /** 最大漏极电流 I_D max（单位：安）。 */
    public double idMax = 10;
    /** P 沟道变体为 true；由器件类型工厂一次性设定。 */
    public boolean pChannel;

    public MosfetDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        vth = ElectricalLimits.GATE_THRESHOLD.sanitize(vth);
        rdsOn = ElectricalLimits.ON_RESISTANCE.sanitize(rdsOn);
        idMax = ElectricalLimits.DRAIN_CURRENT.sanitize(idMax);
        new ThreeTerminalDevice<>(new MosfetModel(pChannel),new MosfetModel.Parameters(vth,rdsOn,idMax),pos).connect(bridges);
    }

    /** 从 NBT 恢复参数；缺失或非有限值使用默认值，越界值钳制到合法区间。 */
    @Override
    public void read(CompoundTag tag) {
        vth = tag.contains("Vth") ? ElectricalLimits.GATE_THRESHOLD.sanitize(tag.getDouble("Vth"))
                : ElectricalLimits.GATE_THRESHOLD.defaultValue();
        rdsOn = tag.contains("RdsOn") ? ElectricalLimits.ON_RESISTANCE.sanitize(tag.getDouble("RdsOn"))
                : ElectricalLimits.ON_RESISTANCE.defaultValue();
        idMax = tag.contains("IdMax") ? ElectricalLimits.DRAIN_CURRENT.sanitize(tag.getDouble("IdMax"))
                : ElectricalLimits.DRAIN_CURRENT.defaultValue();
    }

    /** 把三个参数写入 NBT 持久化。 */
    @Override
    public void write(CompoundTag tag) {
        tag.putDouble("Vth", vth);
        tag.putDouble("RdsOn", rdsOn);
        tag.putDouble("IdMax", idMax);
    }

    /**
     * 判断方块变化时是否需要重建器件：
     * 换成 N 沟道 <-> P 沟道时必须重建，让极性跟随方块本身。
     */
    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof MosfetBlock block) || block.isPChannel() != pChannel;
    }
}
