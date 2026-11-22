package com.fimkov.witchsatelier.client.dev;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.client.ClientGlyphs;
import com.fimkov.witchsatelier.client.fx.SpellFxClient;
import com.fimkov.witchsatelier.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SmokeClient {
    private static int t;
    private static boolean done;

    private SmokeClient() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (done) return;
        if (mc.screen instanceof DisconnectedScreen d) {
            WitchsAtelier.LOGGER.info("[smoke] client: DISCONNECTED: {}", d.getTitle().getString());
            done = true;
            mc.stop();
            return;
        }
        if (mc.player == null || mc.getConnection() == null || mc.hasSingleplayerServer()) return;
        t++;
        if (t == 100) mc.player.jumpFromGround();
        if (t == 104) {
            PacketDistributor.sendToServer(new Payloads.BootsJoin());
            WitchsAtelier.LOGGER.info("[smoke] client: heels clicked at y={}", String.format("%.2f", mc.player.getY()));
        }
        if (t % 100 == 0) WitchsAtelier.LOGGER.info("[smoke] client t={}: y={} soaring={} glyph strokes={} on ground={}", t,
                String.format("%.2f", mc.player.getY()), SpellFxClient.soaring(mc.player),
                ClientGlyphs.INSTANCE.all().size(), mc.player.onGround());
        if (t == 300) Screenshot.grab(mc.gameDirectory, "witchsatelier_smoke.png", mc.getMainRenderTarget(), c -> {});
        if (t == 700) {
            WitchsAtelier.LOGGER.info("[smoke] client: still connected after {} ticks, leaving", t);
            done = true;
            mc.stop();
        }
    }
}
