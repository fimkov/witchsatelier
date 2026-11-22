package com.fimkov.witchsatelier.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class Net {
    private Net() {}

    public static void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload)) player.connection.send(payload);
    }

    public static void toNear(ServerLevel level, Vec3 at, double range, CustomPacketPayload payload) {
        for (ServerPlayer p : level.players()) if (p.position().distanceToSqr(at) <= range * range) toPlayer(p, payload);
    }

    public static void toDimension(ServerLevel level, CustomPacketPayload payload) {
        for (ServerPlayer p : level.players()) toPlayer(p, payload);
    }
}
