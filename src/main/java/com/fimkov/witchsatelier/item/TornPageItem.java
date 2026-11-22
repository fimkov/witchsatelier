package com.fimkov.witchsatelier.item;

import com.fimkov.witchsatelier.registry.ModComponents;
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

public class TornPageItem extends Item {
    public TornPageItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) PalmQuireItem.clientOpener.accept(hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines,
                                TooltipFlag flag) {
        List<String> names = stack.get(ModComponents.GRIMOIRE_ENTRIES.get());
        if (names != null && !names.isEmpty() && !names.get(0).isEmpty()) {
            lines.add(Component.translatable("glyph.witchsatelier." + names.get(0))
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
