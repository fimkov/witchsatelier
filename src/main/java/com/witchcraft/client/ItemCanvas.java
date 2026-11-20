package com.witchcraft.client;

import com.witchcraft.glyph.page.PageKind;
import com.witchcraft.item.InkPenItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/** Opening the page canvas for paper, a book or a palm quire held opposite the pen. */
public final class ItemCanvas {
    private ItemCanvas() {}

    /** The hand holding something to draw on (the hand without the pen), or null. */
    public static InteractionHand findPage(LocalPlayer p) {
        if (p == null) return null;
        if (p.getMainHandItem().getItem() instanceof InkPenItem && PageKind.of(p.getOffhandItem()) != null) return InteractionHand.OFF_HAND;
        if (p.getOffhandItem().getItem() instanceof InkPenItem && PageKind.of(p.getMainHandItem()) != null) return InteractionHand.MAIN_HAND;
        return null;
    }

    public static void open(LocalPlayer p) {
        InteractionHand hand = findPage(p);
        if (hand != null) Minecraft.getInstance().setScreen(new PageScreen(hand));
    }
}
