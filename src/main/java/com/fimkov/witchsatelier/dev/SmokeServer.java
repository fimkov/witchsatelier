package com.fimkov.witchsatelier.dev;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.gametest.GlyphSketch;
import com.fimkov.witchsatelier.glyph.model.Orientation;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import com.fimkov.witchsatelier.glyph.page.PageKind;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Development only (left out of the release jar): with -Dwitchsatelier.smoke, a dedicated server that sets a scene
 * for the client that joins it (see client/dev/SmokeClient) and reports what happened, then stops.
 */
public final class SmokeServer {
    private static ServerPlayer guest;
    private static int t;

    private SmokeServer() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                guest = p;
                t = 0;
                WitchsAtelier.LOGGER.info("[smoke] server: {} joined", p.getGameProfile().getName());
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) ->
                WitchsAtelier.LOGGER.info("[smoke] server: {} left after {} ticks", e.getEntity().getGameProfile().getName(), t));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick());
    }

    private static void tick() {
        if (guest == null) return;
        t++;
        ServerLevel level = guest.serverLevel();
        if (t == 20) {
            guest.setGameMode(GameType.SURVIVAL);
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.ZERO);
            guest.teleportTo(0.5, ground.getY(), 0.5);
            guest.setItemSlot(EquipmentSlot.FEET, flightBoots());
            guest.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(com.fimkov.witchsatelier.registry.ModItems.INK_PEN.get()));
            // glyphs for the client to receive: a lantern beside the guest, a small petrification further off
            inscribe(level, ground.offset(3, -1, 0), new GlyphSketch(new Pt(0, 0), 1.0).ring(0, 362).sigil(Sigil.LIGHT, 0.6));
            inscribe(level, ground.offset(14, -1, 0), new GlyphSketch(new Pt(0, 0), 0.8).ring(0, 362).sigil(Sigil.EARTH, 0.5)
                    .signs(SignType.EYE, 4, Orientation.OUTWARD, 0.25, 45));
            WitchsAtelier.LOGGER.info("[smoke] server: scene set, {} spells running", GlyphService.activeSpells(level).size());
        }
        if (t % 100 == 0) {
            var worn = GlyphService.wornSpell(level, guest.getUUID());
            WitchsAtelier.LOGGER.info("[smoke] server t={}: guest at y={} connected={} boots flight={}", t,
                    String.format("%.2f", guest.getY()), guest.connection != null && guest.connection.isAcceptingMessages(), worn != null);
        }
        if (t == 900) {
            WitchsAtelier.LOGGER.info("[smoke] server: done");
            level.getServer().halt(false);
        }
    }

    /** A glyph laid on the top face of {@code under}, centred on it. */
    private static void inscribe(ServerLevel level, BlockPos under, GlyphSketch g) {
        SurfacePlane plane = SurfacePlane.of(under, Direction.UP);
        Pt c = plane.toUV(Vec3.atCenterOf(under).add(0, 0.5, 0));
        List<double[]> placed = new ArrayList<>();
        for (double[] s : g.strokes) {
            double[] q = new double[s.length];
            for (int i = 0; i + 1 < s.length; i += 2) {
                q[i] = s[i] + c.x();
                q[i + 1] = s[i + 1] + c.y();
            }
            placed.add(q);
        }
        GlyphService.inscribe(level, plane, placed, null);
    }

    /** Leather boots with the flight glyph drawn across both soles. */
    private static ItemStack flightBoots() {
        var boots = new ItemStack(Items.LEATHER_BOOTS);
        GlyphSketch g = new GlyphSketch(new Pt(0, 0), 0.85).ring(0, 360).sigil(Sigil.WIND, 0.55)
                .signs(SignType.LEVITATION, 4, Orientation.OUTWARD, 0.28, 45);
        var halves = new ArrayList<GlyphPages.PageStroke>();
        for (double[] d : g.strokes) {
            float[] f = new float[d.length];
            for (int i = 0; i < d.length; i++) f[i] = (float) d[i];
            for (float[] part : PageKind.splitForSoles(f, 0.82f))
                halves.add(new GlyphPages.PageStroke(part, com.fimkov.witchsatelier.glyph.ink.InkType.CONJURING));
        }
        boots.set(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get(), GlyphPages.blank(1).withPage(0, new GlyphPages.Page(List.copyOf(halves))));
        return boots;
    }
}
