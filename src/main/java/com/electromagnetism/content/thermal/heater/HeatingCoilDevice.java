package com.electromagnetism.content.thermal.heater;

import com.electromagnetism.platform.LoadedBlockAccess;
import com.electromagnetism.config.ModConfigs;
import com.electromagnetism.core.model.*;
import com.electromagnetism.core.runtime.*;
import com.electromagnetism.integration.cee.electrical.CeeStepEvidence;
import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Fixed resistor with microstep energy and exact thermal integration.
 * Failed or missing linear evidence freezes heat/damage; failed intervals are never replayed.
 * Runtime is owned by the world thread and consumes immutable completed worker observations.
 */
public class HeatingCoilDevice extends SimpleElectricalDevice {
    /** 环境温度基准（°C）。 */
    public static final float AMBIENT_TEMPERATURE = 22f;
    /** 过热温度上限（°C）：散热系数按此值与最大功率标定，额定功率时的平衡温度，超载允许超过它。 */
    public static final float OVERHEAT_TEMPERATURE = 600f;

    /** 缓存的方块实体引用，用于推送温度。 */
    public HeatingCoilBlockEntity be;
    /** 线圈温度（°C）。 */
    private float temperature = AMBIENT_TEMPERATURE;
    private boolean burned;
    private int overloadTicks;

    public HeatingCoilDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    private final java.util.UUID session = java.util.UUID.randomUUID(), instance = java.util.UUID.randomUUID();
    private DeviceRuntime<ThermalResistorModel.Parameters, LumpedThermalModel.State, NoState> runtime;
    private ThermalResistorModel.Parameters parameters;
    private final SamplingResistor branch = new SamplingResistor();
    private long batchTick = -1, consumedTick = -1, sequence;
    private java.util.UUID batch;
    private boolean damageEnabled;
    private double maximumPower;
    private net.minecraft.world.level.block.entity.BlockEntity batchEntity;

    @Override public void preTick(BridgeCollector bridges) {
        batchEntity=level.isLoaded(pos)?LoadedBlockAccess.entity(level,pos):null;
        batchTick = level.getGameTime();
        batch = java.util.UUID.randomUUID(); // Local observation batch, not a CEE network ID.
        branch.values = new double[0];
        branch.evidenceBatch = null;
        maximumPower = ModConfigs.server().heatingCoilMaxPower.getF();
        damageEnabled = ModConfigs.server().componentDamage.get();
        parameters = new ThermalResistorModel.Parameters(ModConfigs.server().heatingCoilResistance.getF(),
            new LumpedThermalModel.Parameters(Math.max(0.01, ModConfigs.server().heatingCoilMass.getF()),
                maximumPower / (OVERHEAT_TEMPERATURE - AMBIENT_TEMPERATURE), AMBIENT_TEMPERATURE + 273.15));
        var binding = new DeviceBinding(session, instance, batch, 0);
        if (runtime == null) runtime = new DeviceRuntime<>(new ThermalResistorModel(), binding, parameters,
            new LumpedThermalModel.State(temperature + 273.15));
        else runtime.rebind(binding);
        branch.resistance = parameters.resistanceOhms();
        if (!burned) bridges.bridge(new InWorldNode(0, pos), new InWorldNode(1, pos), branch);
    }
    private static final class SamplingResistor extends MicroTickingElectricalProperties implements com.electromagnetism.integration.cee.electrical.FrozenMicroBranch {
        double[] values = new double[0];
        java.util.UUID evidenceBatch;
        @Override public void tick(double[] v, int step, int count, int a, int b) {}
        @Override public void afterTick(double[] v, int a, int b, int step, int count) {
            if (values.length != count) { values = new double[count]; java.util.Arrays.fill(values, Double.NaN); }
            evidenceBatch = CeeStepEvidence.currentBatch();
            values[step] = v[b * count + step] - v[a * count + step];
        }
    }
    @Override public void postTick(SimulationResults results) {
        if (!level.isLoaded(pos) || !(LoadedBlockAccess.state(level,pos).getBlock() instanceof HeatingCoilBlock)
                || batchEntity!=LoadedBlockAccess.entity(level,pos)) {
            if (runtime != null) runtime.close();
            runtime = null;
            return;
        }
        if (runtime == null || batchTick <= consumedTick) return;
        consumedTick = batchTick;
        double[] samples = burned ? new double[]{0} : branch.values;
        boolean valid = (burned ? CeeStepEvidence.verified(results) : CeeStepEvidence.verified(results, branch.evidenceBatch)) && samples.length > 0;
        for (double v : samples) valid &= Double.isFinite(v) && Double.isFinite(v * v / parameters.resistanceOhms());
        if (!valid) {
            var token = runtime.prepareStep(new StepContext(session, batch, 0, sequence++, new TimeStep(batchTick / 20.0, (batchTick + 1) / 20.0 - batchTick / 20.0)), parameters);
            runtime.discardStep(token, SolveReport.unknown("Missing samples or unverified CEE linear batch"));
            return;
        }
        double energy = 0;
        double start = batchTick / 20.0, end = (batchTick + 1) / 20.0;
        for (int i = 0; i < samples.length; i++) {
            double next = i == samples.length - 1 ? end : (batchTick + (i + 1.0) / samples.length) / 20.0;
            var token = runtime.prepareStep(new StepContext(session, batch, 0, sequence++, new TimeStep(start, next - start)), parameters);
            var report = new SolveReport(SolveReport.Status.CONVERGED, java.util.OptionalInt.empty(), java.util.OptionalDouble.empty(),
                "Frozen linear-system residual verified; no nonlinear or uniqueness claim");
            runtime.commitStep(new DeviceSolution(token, report, java.util.Optional.of(new TerminalVoltages(samples[i], 0))));
            energy += runtime.snapshot().lastAccepted().orElseThrow().electrical().absorbedJoules();
            start = next;
        }
        temperature = (float)(runtime.snapshot().state().temperatureKelvin() - 273.15);
        if (!burned && damageEnabled && energy / 0.05 > maximumPower * 1.25) {
            if (++overloadTicks >= 4) burned = true;
        } else overloadTicks = 0;

        // 惰性查找并校验 BE 引用，然后推送温度
        if (be == null && level.isLoaded(pos) && LoadedBlockAccess.entity(level,pos) instanceof HeatingCoilBlockEntity found)
            be = found;
        if (be != null) {
            if (be.isRemoved())
                be = null;
            else {
                be.setBurned(burned);
                be.setTemperature(temperature);
            }
        }
    }

    /** 从存档 NBT 读取温度。 */
    @Override
    public void read(CompoundTag tag) {
        float saved = tag.getFloat("Temperature");
        temperature = Float.isFinite(saved) ? Math.max(saved, AMBIENT_TEMPERATURE) : AMBIENT_TEMPERATURE;
        burned = tag.getBoolean("Burned");
        overloadTicks = 0;
        if (runtime != null) runtime.close();
        runtime = null;
    }

    /** 写入存档 NBT。 */
    @Override
    public void write(CompoundTag tag) {
        tag.putFloat("Temperature", temperature);
        tag.putBoolean("Burned", burned);
    }

    /** 方块不再是加热线圈时移除设备。 */
    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof HeatingCoilBlock);
    }
}
