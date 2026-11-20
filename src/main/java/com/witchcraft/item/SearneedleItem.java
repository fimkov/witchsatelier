package com.witchcraft.item;

import com.witchcraft.glyph.ink.InkType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The searneedle: a pen with a fire spell heating its ink, burning the line into whatever it touches. Seared
 * lines laugh at rain, rivers, fire and lava. It dips like any pen, and the heat wears it slowly.
 */
public class SearneedleItem extends InkPenItem {
    public SearneedleItem(Properties props) {
        super(props);
    }

    @Override
    public InkType lineInk(InkType source) {
        return source == InkType.INVISIBLE || source == InkType.BLOOD ? source : InkType.SEARED;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, lines, flag);
        lines.add(Component.translatable("item.witchcraft.searneedle.desc").withStyle(ChatFormatting.GOLD));
    }
}
