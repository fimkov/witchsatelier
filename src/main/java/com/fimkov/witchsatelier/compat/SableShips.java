package com.fimkov.witchsatelier.compat;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.function.UnaryOperator;

final class SableShips {
    private SableShips() {}

    private static Pose3dc pose(Level level, BlockPos pos, float partialTick) {
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, pos);
        if (sub == null) return null;

        return level.isClientSide && sub instanceof ClientSubLevelAccess c ? c.renderPose(partialTick) : sub.logicalPose();
    }

    static UnaryOperator<Vec3> toWorld(Level level, BlockPos pos, float partialTick) {
        Pose3dc p = pose(level, pos, partialTick);
        return p == null ? null : p::transformPosition;
    }

    static UnaryOperator<Vec3> toPlot(Level level, BlockPos pos, float partialTick, boolean direction) {
        Pose3dc p = pose(level, pos, partialTick);
        if (p == null) return null;
        if (!direction) return p::transformPositionInverse;
        return v -> {
            Vector3d d = p.transformNormalInverse(new Vector3d(v.x, v.y, v.z), new Vector3d());
            return new Vec3(d.x, d.y, d.z);
        };
    }
}
