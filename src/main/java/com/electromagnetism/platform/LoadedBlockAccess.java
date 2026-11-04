package com.electromagnetism.platform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** World-thread lookups that neither load a chunk nor refresh its UNKNOWN ticket. */
public final class LoadedBlockAccess {
    private LoadedBlockAccess() {}
    public static BlockEntity entity(Level level,BlockPos pos) {
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        return chunk==null?null:chunk.getBlockEntity(pos);
    }
    public static BlockState state(Level level,BlockPos pos) {
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        return chunk==null?Blocks.AIR.defaultBlockState():chunk.getBlockState(pos);
    }
}
