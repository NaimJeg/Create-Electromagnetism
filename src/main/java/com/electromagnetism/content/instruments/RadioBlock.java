package com.electromagnetism.content.instruments;

import com.electromagnetism.registry.ModBlockEntityTypes;
import com.electromagnetism.registry.ModDeviceTypes;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

public class RadioBlock extends SimpleElectricalDeviceBlock<RadioDevice> implements IBE<InstrumentBlockEntity> {
    public static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShaper SHAPE = VoxelShaper.forHorizontal(
            Shapes.box(4 / 16d, 4 / 16d, 0 / 16d, 12 / 16d, 12 / 16d, 6 / 16d), Direction.SOUTH);

    private static final NodeConfigurator NODES = new NodeConfigurator.Builder()
            .add(6.5f, 12f, 0.5f)
            .add(9.5f, 12f, 0.5f)
            .add(6.5f, 8f, 0.5f)
            .add(9.5f, 8f, 0.5f)
            .simple(Direction.SOUTH);

    private final boolean transmitter;
    public boolean transmitter() { return transmitter; }
    public RadioBlock(Properties properties, boolean transmitter) {
        super(properties);
        this.transmitter=transmitter;
        registerDefaultState(defaultBlockState().setValue(HORIZONTAL_FACING, Direction.NORTH));
    }



    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state.getValue(HORIZONTAL_FACING));
    }

    @Override
    public SimulatedDeviceType<RadioDevice> getDevice() {
        return transmitter ? ModDeviceTypes.RADIO_TRANSMITTER.get() : ModDeviceTypes.RADIO_RECEIVER.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        var nodes=NODES.getNodes(state.getValue(HORIZONTAL_FACING));
        return transmitter ? Map.of(0,nodes.get(0),1,nodes.get(1)) : nodes;
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return NODES.getNodePos(state.getValue(HORIZONTAL_FACING), id);
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return Component.literal(id < 2 ? "Supply " + id : "Output " + (id-2));
    }

    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int id) {
        return 2 / 16f;
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        // Default block interaction runs before Item.useOn, including offhand wiring tools.
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())
            return net.minecraft.world.InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && level instanceof net.minecraft.server.level.ServerLevel server) {
                var device=com.george_vi.electroenergetics.devices.device.DevicesSavedData.load(server).getDevice(pos);
                if(device instanceof RadioDevice radio) {
                    radio.carrierHz=radio.carrierHz==1000?1001:radio.carrierHz==1001?1100:1000;
                    player.displayClientMessage(Component.literal("AM " + radio.carrierHz + " Hz | supply 0/1; output 2/3"),true);
                    if(level.getBlockEntity(pos) instanceof InstrumentBlockEntity instrument) instrument.resetObservations();
                }
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide && level.getBlockEntity(pos) instanceof InstrumentBlockEntity instrument)
            com.electromagnetism.platform.ClientHooks.openOscilloscope.accept(instrument);
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Class<InstrumentBlockEntity> getBlockEntityClass() {
        return InstrumentBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends InstrumentBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.INSTRUMENT.get();
    }
}
