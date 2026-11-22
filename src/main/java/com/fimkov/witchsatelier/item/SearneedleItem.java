package com.fimkov.witchsatelier.item;

import com.fimkov.witchsatelier.glyph.ink.InkType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

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
        lines.add(Component.translatable("item.witchsatelier.searneedle.desc").withStyle(ChatFormatting.GOLD));
    }
}
