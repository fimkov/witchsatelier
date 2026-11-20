package com.witchcraft.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A page torn out of a palm quire, crumpled. Use it to smooth it out and look at it (and close its ring to cast),
 * or click it onto a palm quire to put it back. Left lying on the ground it is gone in five minutes.
 */
public class TornPageItem extends Item {
    public TornPageItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) PalmQuireItem.clientOpener.accept(hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    /** A page from a ruin still says, in faded ink, what glyph it holds. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, java.util.List<net.minecraft.network.chat.Component> lines,
                                net.minecraft.world.item.TooltipFlag flag) {
        java.util.List<String> names = stack.get(com.witchcraft.registry.ModComponents.GRIMOIRE_ENTRIES.get());
        if (names != null && !names.isEmpty() && !names.get(0).isEmpty()) {
            lines.add(net.minecraft.network.chat.Component.translatable("glyph.witchcraft." + names.get(0))
                    .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
        }
    }
}
