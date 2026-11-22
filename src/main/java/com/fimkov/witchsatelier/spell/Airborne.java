package com.fimkov.witchsatelier.spell;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Players our magic holds in the air: flying on sylph shoes, lifted or floating over a glyph. A server that does not
 * allow flight would kick them for hanging in the air too long; while magic holds them, it is told they are not.
 */
public final class Airborne {
    private static final Map<UUID, Long> HELD_UNTIL = new java.util.HashMap<>();

    private Airborne() {}

    /** {@code p} is held up by a spell this tick (and for a moment after, while they come down). */
    public static void hold(ServerPlayer p) {
        HELD_UNTIL.put(p.getUUID(), p.serverLevel().getGameTime() + 40);
    }

    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.connection == null) return;
        Long until = HELD_UNTIL.get(p.getUUID());
        if (until == null) return;
        if (p.serverLevel().getGameTime() > until) {
            HELD_UNTIL.remove(p.getUUID());
            return;
        }
        ((com.fimkov.witchsatelier.mixin.ServerGamePacketListenerAccess) p.connection).witchsatelier$setAboveGroundTickCount(0);
    }
}
