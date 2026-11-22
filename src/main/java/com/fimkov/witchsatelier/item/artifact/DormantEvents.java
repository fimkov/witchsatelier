package com.fimkov.witchsatelier.item.artifact;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class DormantEvents {
    private DormantEvents() {}

    public static void onUseItem(PlayerInteractEvent.RightClickItem e) {
        if (!Awakening.dormant(e.getItemStack())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
        tell(e.getEntity());
    }

    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock e) {
        ItemStack stack = e.getItemStack();
        if (!Awakening.dormant(stack)) return;
        e.setUseItem(TriState.FALSE);
        tell(e.getEntity());
    }

    private static void tell(Player p) {
        if (!p.level().isClientSide) p.displayClientMessage(Component.translatable("message.witchsatelier.dormant"), true);
    }
}
