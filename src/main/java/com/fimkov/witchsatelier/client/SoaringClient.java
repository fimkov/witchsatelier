package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.client.fx.SpellFxClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Flying on sylph shoes, steered as creative flight is: forward, back and sideways along the ground whichever way
 * the flyer looks, jump to rise, sneak to sink; let go and they hang still. Two quick jumps click the heels and
 * end it; so does setting foot on the ground. Under water the shoes do nothing.
 */
public final class SoaringClient {
    private SoaringClient() {}

    private static int airborne;

    public static void onClientTick(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || p.isPassenger() || p.isInWater() || p.getAbilities().flying) { airborne = 0; return; }
        double speed = SpellFxClient.soarSpeed(p);
        if (speed <= 0) { airborne = 0; return; }
        float forward = p.input.forwardImpulse, left = p.input.leftImpulse;
        double vertical = (p.input.jumping ? 1 : 0) - (p.input.shiftKeyDown ? 1 : 0);
        // down on the ground again: the flight is over, as creative flight ends on landing
        if (p.onGround() && airborne > 10 && vertical <= 0) {
            airborne = 0;
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.fimkov.witchsatelier.network.Payloads.BootsJoin());
            return;
        }
        if (!p.onGround()) airborne++;
        double yaw = Math.toRadians(p.getYRot());
        Vec3 ahead = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), leftward = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 flat = ahead.scale(forward).add(leftward.scale(left));
        if (flat.lengthSqr() > 1) flat = flat.normalize();
        Vec3 want = new Vec3(flat.x * speed, vertical * speed * 0.75, flat.z * speed);
        Vec3 v = p.getDeltaMovement();
        // ease towards what the keys ask, and make up in advance for the air's drag and the pull of the ground
        Vec3 next = v.add(want.subtract(v).scale(0.3));
        p.setDeltaMovement(next.x / 0.91, next.y + 0.08, next.z / 0.91);
        p.resetFallDistance();
    }
}
