package com.fimkov.witchsatelier.item.artifact;

import com.fimkov.witchsatelier.item.attire.WitchAttire;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class BrimhatMaskItem extends ArmorItem {
    public BrimhatMaskItem(Properties props) {
        super(WitchAttire.BRIMHAT_PORCELAIN, Type.HELMET, props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.witchsatelier.brimhat_mask.desc").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
    }
}
