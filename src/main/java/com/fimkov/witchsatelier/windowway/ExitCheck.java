package com.fimkov.witchsatelier.windowway;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where someone coming out of a far window can stand. A window never sets a traveller down inside stone, in water or
 * in fire: it tries the spot the crossing leads to, then a little and a block or two further out in front of the
 * window, then a block higher, and if none of them is free it does not open at all.
 */
public final class ExitCheck {
    private ExitCheck() {}

    /** The feet position to set the traveller down at, or null when there is nowhere free. */
    public static Vec3 find(ServerLevel level, Entity who, Vec3 feet, Vec3 n) {
        Vec3 out = n.normalize();
        for (Vec3 at : new Vec3[]{feet, feet.add(out.scale(0.4)), feet.add(out), feet.add(out.scale(2)), feet.add(0, 1, 0)}) {
            if (free(level, who, at)) return at;
            // a crossing carried far from the world's middle can land a hair inside the floor it should stand on
            Vec3 up = at.add(0, SETTLE, 0);
            if (free(level, who, up)) return up;
        }
        return null;
    }

    /** How far a traveller may be lifted onto the floor under her: more than rounding, less than anyone would see. */
    private static final double SETTLE = 0.01;

    public static boolean free(ServerLevel level, Entity who, Vec3 feet) {
        AABB box = who.getDimensions(who.getPose()).makeBoundingBox(feet);
        if (!level.noCollision(who, box) || level.containsAnyLiquid(box)) return false;
        return level.getBlockStates(box.deflate(1e-3)).noneMatch(ExitCheck::burns);
    }

    private static boolean burns(BlockState s) {
        return s.is(BlockTags.FIRE) || (s.is(BlockTags.CAMPFIRES) && s.hasProperty(CampfireBlock.LIT) && s.getValue(CampfireBlock.LIT));
    }
}
