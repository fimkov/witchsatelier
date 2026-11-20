package com.witchcraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * An old grimoire. Its pages show glyphs drawn long ago in lasting ink: they cannot be cast from the book,
 * but anyone who copies them faithfully (on a wall, on paper, in a palm quire) gets the same spell.
 */
public class GrimoireItem extends Item {
    public GrimoireItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) PalmQuireItem.clientOpener.accept(hand);
        player.playSound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 0.8f, 0.8f);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

        @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.witchcraft.grimoire.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.add(Component.translatable("item.witchcraft.grimoire.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
