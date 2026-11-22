package com.fimkov.witchsatelier.item;

import com.fimkov.witchsatelier.glyph.Techniques;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import com.fimkov.witchsatelier.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class GrimoireItem extends Item {
    public GrimoireItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) PalmQuireItem.clientOpener.accept(hand);
        else if (player instanceof ServerPlayer sp) {
            ItemStack stack = player.getItemInHand(hand);
            var names = stack.get(ModComponents.GRIMOIRE_ENTRIES.get());
            var gp = stack.get(ModComponents.GLYPH_PAGES.get());
            int at = gp == null ? 0 : gp.current();
            if (names != null && at >= 0 && at < names.size() && GlyphLibrary.IMAGINED_RING.equals(names.get(at)))
                Techniques.learnImaginedRing(sp);
        }
        player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8f, 0.8f);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

        @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.witchsatelier.grimoire.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.add(Component.translatable("item.witchsatelier.grimoire.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
