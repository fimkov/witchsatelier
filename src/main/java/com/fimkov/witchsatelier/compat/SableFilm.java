package com.fimkov.witchsatelier.compat;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public final class SableFilm {
    private SableFilm() {}

    public static void clearShips(ServerLevel level) {
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) return;
        for (var sub : new ArrayList<>(container.getAllSubLevels()))
            container.removeSubLevel(sub, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
    }

    public static UnaryOperator<Vec3> assemble(ServerLevel level, BlockPos min, BlockPos max) {
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(min, max)) if (!level.getBlockState(p).isAir()) blocks.add(p.immutable());
        if (blocks.isEmpty()) return null;
        var ship = SubLevelAssemblyHelper.assembleBlocks(level, min, blocks, new BoundingBox3i(min, max));
        if (ship == null) return null;
        var pose = new dev.ryanhcode.sable.companion.math.Pose3d(ship.logicalPose());
        return pose::transformPositionInverse;
    }
}
