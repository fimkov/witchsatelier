package com.witchcraft.item.artifact;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Snugstone: a slice of stone with a fire glyph whose columns face inward and a Radial sign to soften it, so it
 * gives off a gentle warmth and never a flame. Carried, it keeps the cold away; held, it thaws snow and ice
 * around you; in a furnace it burns for a long time.
 */
public class SnugstoneItem extends Item {
    public SnugstoneItem(Properties props) {
        super(props);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof LivingEntity le) || level.isClientSide) return;
        if (le.getTicksFrozen() > 0) le.setTicksFrozen(Math.max(0, le.getTicksFrozen() - 4));
        boolean held = le.getMainHandItem() == stack || le.getOffhandItem() == stack;
        if (!held || level.getGameTime() % 20 != 0) return;
        BlockPos c = le.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 1, 2))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.SNOW) || s.is(Blocks.POWDER_SNOW)) {
                level.removeBlock(p, false);
            } else if (s.is(Blocks.ICE) || s.is(Blocks.FROSTED_ICE)) {
                level.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
            } else continue;
            if (level instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.01);
        }
    }

    @Override
    public int getBurnTime(ItemStack stack, RecipeType<?> recipeType) {
        return 4800;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.witchcraft.snugstone.desc").withStyle(ChatFormatting.GRAY));
    }
}
