package com.fimkov.witchsatelier.item;

import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import com.fimkov.witchsatelier.glyph.page.PageKind;
import com.fimkov.witchsatelier.glyph.page.PageService;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class PalmQuireItem extends Item {
    public static Consumer<InteractionHand> clientOpener = h -> {};

    public PalmQuireItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
        if (action != ClickAction.PRIMARY) return false;
        boolean paper = other.is(Items.PAPER) && !other.has(ModComponents.GLYPH_PAGES.get());
        boolean torn = other.is(ModItems.TORN_PAGE.get());
        if (!paper && !torn) return false;
        GlyphPages gp = PageService.pages(self, PageKind.PALM_QUIRE);
        if (gp.pages().size() >= PageKind.PALM_QUIRE.maxPages) return false;
        List<GlyphPages.Page> pages = new ArrayList<>(gp.pages());
        GlyphPages.Page page = torn ? PageService.pages(other, PageKind.TORN_PAGE).page(0) : GlyphPages.Page.EMPTY;
        int at = pages.isEmpty() ? 0 : gp.current() + 1;
        pages.add(at, page);
        self.set(ModComponents.GLYPH_PAGES.get(), new GlyphPages(List.copyOf(pages), at));
        other.shrink(1);
        player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8f, 1.2f);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        GlyphPages gp = PageService.pages(stack, PageKind.PALM_QUIRE);
        lines.add(Component.translatable("item.witchsatelier.palm_quire.pages", gp.pages().size()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.witchsatelier.palm_quire.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) clientOpener.accept(hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
