package com.fimkov.witchsatelier.item;

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
        else if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            // opened at the imagined ring (the thin notebook has nothing else): reading it teaches it
            ItemStack stack = player.getItemInHand(hand);
            var names = stack.get(com.fimkov.witchsatelier.registry.ModComponents.GRIMOIRE_ENTRIES.get());
            var gp = stack.get(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get());
            int at = gp == null ? 0 : gp.current();
            if (names != null && at >= 0 && at < names.size() && com.fimkov.witchsatelier.glyph.page.GlyphLibrary.IMAGINED_RING.equals(names.get(at)))
                com.fimkov.witchsatelier.glyph.Techniques.learnImaginedRing(sp);
        }
        player.playSound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 0.8f, 0.8f);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

        @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.witchsatelier.grimoire.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.add(Component.translatable("item.witchsatelier.grimoire.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
