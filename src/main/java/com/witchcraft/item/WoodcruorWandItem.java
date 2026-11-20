package com.witchcraft.item;

import com.witchcraft.glyph.ink.InkType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A woodcruor wand: a rod of hardened woodcruor, drawn with like chalk on wood and stone. It needs no inkwell,
 * it wears down instead; its marks are weak and short-lived and the first rain takes them.
 */
public class WoodcruorWandItem extends InkPenItem {
    public WoodcruorWandItem(Properties props) {
        super(props);
    }

    @Override
    public InkType lineInk(InkType source) {
        return InkType.CHALK;
    }

    @Override
    public boolean selfInked() {
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.isDamaged();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f - stack.getDamageValue() * 13f / stack.getMaxDamage());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xB9A3C8;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.witchcraft.woodcruor_wand.desc").withStyle(ChatFormatting.GRAY));
    }
}
