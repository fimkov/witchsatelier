package com.fimkov.witchsatelier.glyph.page;

import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;

public final class PageDispensing extends DefaultDispenseItemBehavior {
    private static final PageDispensing INSTANCE = new PageDispensing();

    private PageDispensing() {}

    public static void register() {
        DispenserBlock.registerProjectileBehavior(ModItems.GLYPH_ARROW.get());
        DispenserBlock.registerBehavior(Items.PAPER, INSTANCE);
        DispenserBlock.registerBehavior(ModItems.TORN_PAGE.get(), INSTANCE);
    }

    @Override
    protected ItemStack execute(BlockSource src, ItemStack stack) {
        GlyphPages gp = stack.get(ModComponents.GLYPH_PAGES.get());
        if (gp == null || gp.page(0).isEmpty()) return super.execute(src, stack);
        Direction facing = src.state().getValue(DispenserBlock.FACING);
        BlockPos pos = src.pos();
        SurfacePlane plane = SurfacePlane.of(pos, facing);
        Vec3 at = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));
        net.minecraft.server.level.ServerPlayer near = null;
        double best = 8 * 8;
        for (var pl : src.level().players()) {
            double d = pl.distanceToSqr(at);
            if (d < best && !pl.isSpectator()) { best = d; near = pl; }
        }
        var spell = PageService.castOnSurface(src.level(), gp.page(0), plane, at, null);
        if (spell == null) return super.execute(src, stack);
        if (near != null && spell.program.form() == com.fimkov.witchsatelier.spell.SpellProgram.Form.PETRIFY) {
            com.fimkov.witchsatelier.entity.Taboo.add(near, 40);
            com.fimkov.witchsatelier.entity.Witnesses.saw(near);
        }
        stack.shrink(1);
        return stack;
    }
}
