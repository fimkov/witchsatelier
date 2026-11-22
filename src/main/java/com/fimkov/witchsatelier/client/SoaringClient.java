package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.client.fx.SpellFxClient;
import com.fimkov.witchsatelier.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SoaringClient {
    private SoaringClient() {}

    private static int airborne;

    private static double rise;

    public static void onClientTick(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || p.isPassenger() || p.isInWater() || p.getAbilities().flying) { airborne = 0; rise = 0; return; }
        double speed = SpellFxClient.soarSpeed(p);
        if (speed <= 0) { airborne = 0; rise = 0; return; }
        float forward = p.input.forwardImpulse, left = p.input.leftImpulse;
        double vertical = (p.input.jumping ? 1 : 0) - (p.input.shiftKeyDown ? 1 : 0);

        if (p.onGround() && airborne > 10 && vertical <= 0) {
            airborne = 0;
            PacketDistributor.sendToServer(new Payloads.BootsJoin());
            return;
        }
        if (!p.onGround()) airborne++;
        double yaw = Math.toRadians(p.getYRot());
        Vec3 ahead = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), leftward = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 flat = ahead.scale(forward).add(leftward.scale(left));
        if (flat.lengthSqr() > 1) flat = flat.normalize();
        Vec3 want = new Vec3(flat.x * speed, vertical * speed * 0.75, flat.z * speed);
        Vec3 v = p.getDeltaMovement();

        if (p.verticalCollision) rise = 0;

        double nx = v.x + (want.x - v.x) * 0.3, nz = v.z + (want.z - v.z) * 0.3;
        rise += (want.y - rise) * 0.3;
        p.setDeltaMovement(nx / 0.91, rise, nz / 0.91);
        p.resetFallDistance();
    }
}
