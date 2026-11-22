package com.fimkov.witchsatelier.item.artifact;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** A Sealchair to set down: it stands on its four hoofed legs until someone sits in it. */
public class SealchairItem extends BlockItem {
    public SealchairItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.witchsatelier.sealchair.desc").withStyle(ChatFormatting.GRAY));
    }
}
