package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.glyph.page.PageKind;
import com.fimkov.witchsatelier.item.InkPenItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

public final class ItemCanvas {
    private ItemCanvas() {}

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
