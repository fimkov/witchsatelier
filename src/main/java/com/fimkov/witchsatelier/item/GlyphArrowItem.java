package com.fimkov.witchsatelier.item;

import com.fimkov.witchsatelier.entity.GlyphArrow;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class GlyphArrowItem extends ArrowItem {
    public GlyphArrowItem(Properties props) {
        super(props);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, ItemStack weapon) {
        return new GlyphArrow(level, shooter, ammo.copyWithCount(1), weapon);
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction dir) {
        return new GlyphArrow(level, pos.x(), pos.y(), pos.z(), stack.copyWithCount(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.witchsatelier.glyph_arrow.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
