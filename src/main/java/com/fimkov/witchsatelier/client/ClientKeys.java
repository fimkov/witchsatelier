package com.fimkov.witchsatelier.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.fimkov.witchsatelier.network.Payloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClientKeys {
    /** Close the ring on the current page in hand: the prepared spell fires. */
    public static final KeyMapping CLOSE_RING = new KeyMapping("key.witchsatelier.close_ring",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.witchsatelier");

    private ClientKeys() {}

    static void register(RegisterKeyMappingsEvent e) {
        e.register(CLOSE_RING);
    }

    private static boolean jumpWasDown;
    private static int sinceJump = 100;

    static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        while (CLOSE_RING.consumeClick()) {
            if (mc.player != null) PacketDistributor.sendToServer(new Payloads.QuickClose());
        }
        // two quick jumps: the heels click together and the glyph on the boots fires (or, flying on them, lets go:
        // as creative flight is switched on and off)
        boolean down = mc.screen == null && mc.options.keyJump.isDown();
        sinceJump++;
        if (down && !jumpWasDown) {
            if (sinceJump <= 7 && mc.player != null && hasBootsGlyph(mc.player)) {
                PacketDistributor.sendToServer(new Payloads.BootsJoin());
                sinceJump = 100;
            } else {
                sinceJump = 0;
            }
        }
        jumpWasDown = down;
    }

    private static boolean hasBootsGlyph(net.minecraft.client.player.LocalPlayer p) {
        var boots = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
        return com.fimkov.witchsatelier.glyph.page.PageKind.of(boots) == com.fimkov.witchsatelier.glyph.page.PageKind.BOOTS
                && boots.has(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get());
    }
}
