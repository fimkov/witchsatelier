package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.glyph.page.PageKind;
import com.fimkov.witchsatelier.network.Payloads;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClientKeys {
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

    private static boolean hasBootsGlyph(LocalPlayer p) {
        var boots = p.getItemBySlot(EquipmentSlot.FEET);
        return PageKind.of(boots) == PageKind.BOOTS
                && boots.has(ModComponents.GLYPH_PAGES.get());
    }
}
