package com.fimkov.witchsatelier.spell;

import com.fimkov.witchsatelier.mixin.ServerGamePacketListenerAccess;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Airborne {
    private static final Map<UUID, Long> HELD_UNTIL = new HashMap<>();

    private Airborne() {}

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
        ((ServerGamePacketListenerAccess) p.connection).witchsatelier$setAboveGroundTickCount(0);
    }
}
