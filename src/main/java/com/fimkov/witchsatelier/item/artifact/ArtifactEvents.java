package com.fimkov.witchsatelier.item.artifact;

import com.fimkov.witchsatelier.WitchsAtelier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-side upkeep for the doorknobs, the guidance orb and the fluttergrams. */
@EventBusSubscriber(modid = WitchsAtelier.MODID)
public final class ArtifactEvents {
    private ArtifactEvents() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post e) {
        if (e.getLevel() instanceof ServerLevel level) Doorknobs.tick(level);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        Mailroom.tick(e.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Mailroom.remember(p);
    }

    /** Sneak and use a door with an empty hand to take its knob off; the door itself still opens or shuts as usual. */
    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock e) {
        Player p = e.getEntity();
        if (e.getHand() != InteractionHand.MAIN_HAND || !p.isSecondaryUseActive() || !p.getMainHandItem().isEmpty()) return;
        if (e.getLevel() instanceof ServerLevel level) Doorknobs.takeOff(level, p, e.getPos());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        Doorknobs.forget();
        GuidanceOrbItem.forget();
    }
}
