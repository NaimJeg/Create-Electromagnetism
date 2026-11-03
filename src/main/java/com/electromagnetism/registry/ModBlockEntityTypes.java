package com.electromagnetism.registry;

import com.electromagnetism.ElectromagnetismMod;

import com.electromagnetism.content.electromechanics.excitation.ExcitationStatorBlockEntity;
import com.electromagnetism.content.lighting.factory.FactoryLightBlockEntity;
import com.electromagnetism.content.lighting.factory.FactoryLightLightBlockEntity;
import com.electromagnetism.content.lighting.factory.FactoryLightRenderer;
// import com.electromagnetism.content.electromechanics.fan.ElectricFanBlockEntity;
// import com.electromagnetism.content.electromechanics.fan.ElectricFanRenderer;
import com.electromagnetism.content.lighting.fixture.LightFixtureBlockEntity;
import com.electromagnetism.content.lighting.fixture.LightFixtureRenderer;
import com.electromagnetism.content.thermal.heater.HeatingCoilBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import static com.electromagnetism.ElectromagnetismMod.REGISTRATE;

/**
 * 本模组的方块实体类型注册类。
 * <p>
 * 使用 Registrate 声明各方块实体：绑定其有效的方块，
 * 需要特殊渲染的注册对应渲染器。
 */
public class ModBlockEntityTypes {
    /** 灯具固定座方块实体：处理灯具通电、灯泡安装与光照档位，带专用渲染器 */
    public static final BlockEntityEntry<LightFixtureBlockEntity> LIGHT_FIXTURE =
            REGISTRATE.blockEntity("light_fixture", LightFixtureBlockEntity::new)
                    .validBlock(ModBlocks.LIGHT_FIXTURE)
                    .renderer(() -> LightFixtureRenderer::new)
                    .register();

    /** 工厂灯方块实体：处理工厂灯通电与投射光块生成，带专用渲染器（光束特效） */
    public static final BlockEntityEntry<FactoryLightBlockEntity> FACTORY_LIGHT =
            REGISTRATE.blockEntity("factory_light", FactoryLightBlockEntity::new)
                    .validBlock(ModBlocks.FACTORY_LIGHT)
                    .renderer(() -> FactoryLightRenderer::new)
                    .register();

    /** 工厂灯投射光块方块实体：投射光源的附属方块实体，无需渲染器 */
    public static final BlockEntityEntry<FactoryLightLightBlockEntity> FACTORY_LIGHT_LIGHT =
            REGISTRATE.blockEntity("factory_light_light", FactoryLightLightBlockEntity::new)
                    .validBlock(ModBlocks.FACTORY_LIGHT_LIGHT)
                    .register();

    // Disabled: duplicates electroenergetics:electric_fan
    // 电动鼓风机方块实体已禁用：与电力学原版的电动风扇功能重复
    // public static final BlockEntityEntry<ElectricFanBlockEntity> ELECTRIC_FAN =
    //         REGISTRATE.blockEntity("electric_fan", ElectricFanBlockEntity::new)
    //                 .validBlock(ModBlocks.ELECTRIC_FAN)
    //                 .renderer(() -> ElectricFanRenderer::new)
    //                 .register();

    /** 加热线圈方块实体：处理线圈通电发热、温度模拟与物品加热逻辑 */
    public static final BlockEntityEntry<HeatingCoilBlockEntity> HEATING_COIL =
            REGISTRATE.blockEntity("heating_coil", HeatingCoilBlockEntity::new)
                    .validBlock(ModBlocks.HEATING_COIL)
                    .register();

    /** 励磁定子方块实体：处理定子绕组通电并为相邻转子提供励磁场 */
    public static final BlockEntityEntry<ExcitationStatorBlockEntity> EXCITATION_STATOR =
            REGISTRATE.blockEntity("excitation_stator", ExcitationStatorBlockEntity::new)
                    .validBlock(ModBlocks.EXCITATION_STATOR)
                    .register();

    /** 三极管方块实体：NPN 与 PNP 三极管共用同一方块实体类型，由方块实例区分极性 */
    public static final BlockEntityEntry<com.electromagnetism.content.electronics.bjt.TransistorBlockEntity> TRANSISTOR =
            REGISTRATE.blockEntity("transistor", com.electromagnetism.content.electronics.bjt.TransistorBlockEntity::new)
                    .validBlock(ModBlocks.NPN_TRANSISTOR)
                    .validBlock(ModBlocks.PNP_TRANSISTOR)
                    .register();

    /** MOS 管方块实体：N 沟道与 P 沟道 MOS 管共用同一方块实体类型，由方块实例区分极性 */
    public static final BlockEntityEntry<com.electromagnetism.content.electronics.mosfet.MosfetBlockEntity> MOSFET =
            REGISTRATE.blockEntity("mosfet", com.electromagnetism.content.electronics.mosfet.MosfetBlockEntity::new)
                    .validBlock(ModBlocks.N_MOSFET)
                    .validBlock(ModBlocks.P_MOSFET)
                    .register();

    /** 电铃方块实体：承载服务端解算同步来的音量/音调，客户端据此驱动铃声播放 */
    public static final BlockEntityEntry<com.electromagnetism.content.acoustics.bell.ElectricBellBlockEntity> ELECTRIC_BELL =
            REGISTRATE.blockEntity("electric_bell", com.electromagnetism.content.acoustics.bell.ElectricBellBlockEntity::new)
                    .validBlock(ModBlocks.ELECTRIC_BELL)
                    .validBlock(ModBlocks.ICE_BELL)
                    .validBlock(ModBlocks.NAILONG_BELL)
                    .register();

    /**
     * 注册入口（占位方法）。
     * Registrate 的方块实体注册在类加载静态字段初始化时已完成，
     * 主类调用本方法仅为确保本类被加载。
     */
    public static final BlockEntityEntry<com.electromagnetism.content.instruments.InstrumentBlockEntity> INSTRUMENT = REGISTRATE.blockEntity("instrument", com.electromagnetism.content.instruments.InstrumentBlockEntity::new).validBlock(ModBlocks.MICROPHONE).validBlock(ModBlocks.OSCILLOSCOPE).register();
    public static void register() {
    }
}
