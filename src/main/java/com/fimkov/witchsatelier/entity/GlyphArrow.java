package com.fimkov.witchsatelier.entity;

import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import com.fimkov.witchsatelier.glyph.page.PageService;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.registry.ModEntities;
import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class GlyphArrow extends AbstractArrow {
    public GlyphArrow(EntityType<? extends GlyphArrow> type, Level level) {
        super(type, level);
    }

    public GlyphArrow(Level level, LivingEntity shooter, ItemStack ammo, ItemStack weapon) {
        super(ModEntities.GLYPH_ARROW.get(), shooter, level, ammo, weapon);
        pickup = Pickup.DISALLOWED;
    }

    public GlyphArrow(Level level, double x, double y, double z, ItemStack ammo) {
        super(ModEntities.GLYPH_ARROW.get(), x, y, z, level, ammo, null);
        pickup = Pickup.DISALLOWED;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.GLYPH_ARROW.get());
    }

    private GlyphPages.Page page() {
        GlyphPages gp = getPickupItemStackOrigin().get(ModComponents.GLYPH_PAGES.get());
        return gp == null ? null : gp.page(0);
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (level() instanceof ServerLevel sl && isAlive()) {
            cast(sl, SurfacePlane.of(hit.getBlockPos(), hit.getDirection()), hit.getLocation());
            discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level() instanceof ServerLevel sl && !isRemoved()) {
            var e = hit.getEntity();
            BlockPos under = BlockPos.containing(e.getX(), e.getY() - 0.2, e.getZ());
            cast(sl, SurfacePlane.of(under, Direction.UP), new Vec3(e.getX(), under.getY() + 1, e.getZ()));
            discard();
        }
    }

    private void cast(ServerLevel level, SurfacePlane plane, Vec3 at) {
        PageService.castOnSurface(level, page(), plane, at, getOwner() == null ? null : getOwner().getUUID());
    }
}
