package com.fimkov.witchsatelier.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.UnaryOperator;

/**
 * Ink on ships (Create Aeronautics, through Sable). A ship's blocks are kept in a plot of their own, far from where
 * the ship is seen; ink drawn on them is kept there too, in the plot's coordinates. These carry points between the
 * two: where the ink is seen and drawn on, and where it is kept. Without Sable every block is simply where it is.
 */
public final class Ships {
    private Ships() {}

    private static final boolean SABLE = net.neoforged.fml.ModList.get().isLoaded("sable");

    /**
     * A glyph that flies a ship: Wind with Levitation or Float (a lift), or the sigil of the wind underfoot, drawn on
     * the ship's underside (facing down, in the ship's own frame), where it bears the ship up as wind bears a bird.
     */
    public static final java.util.function.Predicate<com.fimkov.witchsatelier.spell.SpellProgram> FLIGHT = p ->
            (p.form() == com.fimkov.witchsatelier.spell.SpellProgram.Form.LIFT
                    && (p.element() == com.fimkov.witchsatelier.glyph.model.Sigil.WIND || p.element() == com.fimkov.witchsatelier.glyph.model.Sigil.WIND_UNDERFOOT)
                    || p.element() == com.fimkov.witchsatelier.glyph.model.Sigil.WIND_UNDERFOOT)
                    && p.normal().y < -0.5;

    /** From a ship's plot to where it is seen now ({@code partialTick} for drawing), or null if {@code pos} is no ship's. */
    public static UnaryOperator<Vec3> toWorld(Level level, BlockPos pos, float partialTick) {
        return SABLE ? SableShips.toWorld(level, pos, partialTick) : null;
    }

    /** From the world into the plot of the ship {@code pos} belongs to (points, or directions), or null if none. */
    public static UnaryOperator<Vec3> toPlot(Level level, BlockPos pos, float partialTick, boolean direction) {
        return SABLE ? SableShips.toPlot(level, pos, partialTick, direction) : null;
    }

    public static boolean onShip(Level level, BlockPos pos) {
        return SABLE && SableShips.toWorld(level, pos, 1f) != null;
    }

    /** Where a point kept in a plot is seen in the world (itself, if it is not on a ship). */
    public static Vec3 seen(Level level, Vec3 kept) {
        UnaryOperator<Vec3> f = toWorld(level, BlockPos.containing(kept), 1f);
        return f == null ? kept : f.apply(kept);
    }
}
