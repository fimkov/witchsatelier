package com.fimkov.witchsatelier.compat;

import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.spell.SpellProgram;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class Ships {
    private Ships() {}

    private static final boolean SABLE = ModList.get().isLoaded("sable");

    public static final Predicate<SpellProgram> FLIGHT = p ->
            (p.form() == SpellProgram.Form.LIFT
                    && (p.element() == Sigil.WIND || p.element() == Sigil.WIND_UNDERFOOT)
                    || p.element() == Sigil.WIND_UNDERFOOT)
                    && p.normal().y < -0.5;

    public static UnaryOperator<Vec3> toWorld(Level level, BlockPos pos, float partialTick) {
        return SABLE ? SableShips.toWorld(level, pos, partialTick) : null;
    }

    public static UnaryOperator<Vec3> toPlot(Level level, BlockPos pos, float partialTick, boolean direction) {
        return SABLE ? SableShips.toPlot(level, pos, partialTick, direction) : null;
    }

    public static boolean onShip(Level level, BlockPos pos) {
        return SABLE && SableShips.toWorld(level, pos, 1f) != null;
    }

    public static Vec3 seen(Level level, Vec3 kept) {
        UnaryOperator<Vec3> f = toWorld(level, BlockPos.containing(kept), 1f);
        return f == null ? kept : f.apply(kept);
    }
}
