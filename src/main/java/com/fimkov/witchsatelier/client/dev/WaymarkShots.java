package com.fimkov.witchsatelier.client.dev;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.gametest.GlyphSketch;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.model.Orientation;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.storage.GlyphLevelData;
import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import com.fimkov.witchsatelier.glyph.surface.StrokeState;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.spell.ActiveSpell;
import com.fimkov.witchsatelier.windowway.PortalFrame;
import com.fimkov.witchsatelier.windowway.WindowwayNetwork;
import com.fimkov.witchsatelier.windowway.WindowwayService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Shots of marked Windowways: the tinted rim beside a plain window, the fog of a far Sill, the iris of a window
 * held toward another mark, and the flicker of a window that turns its traveller away.
 */
final class WaymarkShots {
    private WaymarkShots() {}

    /** The witch who owns the blood Sill of the refusal film: nobody who plays. */
    private static final UUID STRANGER = UUID.fromString("5a1f0000-0000-4000-8000-0000000be11e");

    private static final SignType[] WAYMARK = {SignType.EYE, SignType.BIRD, SignType.BIRD};
    private static final SignType[] EYES = {SignType.EYE, SignType.EYE};
    private static final SignType[] COLUMNS = {SignType.COLUMN, SignType.COLUMN};
    private static final SignType[] RADIALS = {SignType.RADIAL, SignType.RADIAL};
    private static final SignType[] LIFTS = {SignType.LEVITATION, SignType.LEVITATION};
    private static final SignType[] MIXED = {SignType.COLUMN, SignType.RADIAL};
    private static final SignType[] DIAMONDS = {SignType.EYE, SignType.LEVITATION};

    private static final BlockPos BASE = VisualCheck.BASE;
    private static final BlockPos FAR72 = BASE.offset(72, 0, 0);
    private static final BlockPos FAR1200 = BASE.offset(1200, 0, 0);

    static boolean owns(VisualCheck.Shot s) {
        return s.name().startsWith("waymark_");
    }

    static List<VisualCheck.Shot> shots() {
        List<VisualCheck.Shot> out = new ArrayList<>();
        // a Sill and its Kin, with a plain pair beside them
        out.add(new VisualCheck.Shot("waymark_tint", 60, WaymarkShots::hall, new Vec3(1.5, 223.2, 0.5), 90, 3, false));
        out.add(new VisualCheck.Shot("waymark_tint_near", 30, l -> {}, new Vec3(-1.6, 223.0, -1.6), 106, 4, false));
        out.add(new VisualCheck.Shot("waymark_tint_far", 30, l -> {}, new Vec3(4.4, 225.6, 0.5), 90, 14, false));
        out.add(new VisualCheck.Shot("waymark_tint_kin", 30, l -> {}, new Vec3(-1.5, 223.2, 0.5), -90, 3, false));
        // a Kin whose Sill is 1200 blocks off, beside a plain window paired as far; then a Kin whose Sill is in the Nether
        out.add(new VisualCheck.Shot("waymark_fog", 60, WaymarkShots::farFog, new Vec3(1.5, 223.2, 0.5), 90, 3, false));
        out.add(new VisualCheck.Shot("waymark_fog_near", 30, l -> {}, new Vec3(-2.2, 222.9, -2.2), 106, 4, false));
        out.add(new VisualCheck.Shot("waymark_fog_nether", 60, WaymarkShots::netherFog, new Vec3(-1.0, 223.0, 0.5), 90, 3, false));
        // a Sill with a Kin, held toward another Sill by a page and let go again
        out.add(new VisualCheck.Shot("waymark_iris", 60, WaymarkShots::irisStage, new Vec3(-1.7, 223.2, 0.5), 90, 0, false, 140, WaymarkShots::irisDuring));
        // a witch walks into a stranger's blood Sill, and into a window whose far side is walled up
        out.add(new VisualCheck.Shot("waymark_refuse", 60, WaymarkShots::refuseStage, new Vec3(-0.5, 223.2, 0.5), 90, 5, false, 120, WaymarkShots::refuseDuring));
        out.add(new VisualCheck.Shot("waymark_refuse_live", 60, WaymarkShots::blockedStage, new Vec3(-0.5, 223.2, 0.5), 90, 5, false, 120, WaymarkShots::blockedDuring));
        return out;
    }

    // ---- scenes -------------------------------------------------------------------------------------------------

    private static void hall(ServerLevel l) {
        MinecraftServer server = l.getServer();
        ServerPlayer me = VisualCheck.player(server);
        VisualCheck.clear(l, BASE, 18);
        fresh(server, "BIRD*2+EYE");
        VisualCheck.platform(l, BASE, 16);
        walls(l, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS);
        props(l);
        window(l, BASE.offset(-6, 2, -3), Direction.EAST, me, InkType.CONJURING, WAYMARK);
        plain(l, BASE.offset(-6, 2, 3), Direction.EAST);
        later(10, () -> {
            window(l, BASE.offset(6, 2, -3), Direction.WEST, me, InkType.CONJURING, WAYMARK);
            plain(l, BASE.offset(6, 2, 3), Direction.WEST);
        });
        later(30, () -> report(l, 0));
    }

    private static void farFog(ServerLevel l) {
        MinecraftServer server = l.getServer();
        ServerPlayer me = VisualCheck.player(server);
        VisualCheck.clear(l, BASE, 18);
        VisualCheck.clear(l, FAR1200, 8);
        fresh(server, "EYE*2");
        VisualCheck.platform(l, BASE, 16);
        VisualCheck.platform(l, FAR1200, 8);
        walls(l, Blocks.STONE_BRICKS, null);
        props(l);
        for (int y = 1; y <= 5; y++) for (int z = -6; z <= 6; z++) l.setBlock(FAR1200.offset(-6, y, z), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 3);
        // the Sill first, far away, so the window at home is its Kin; its chunk is held loaded so that it lights at
        // all, and is still too far off for the client to be sent
        force(l, FAR1200, true);
        window(l, FAR1200.offset(-6, 2, -3), Direction.EAST, me, InkType.CONJURING, EYES);
        plain(l, FAR1200.offset(-6, 2, 3), Direction.EAST);
        later(10, () -> {
            window(l, BASE.offset(-6, 2, -3), Direction.EAST, me, InkType.CONJURING, EYES);
            plain(l, BASE.offset(-6, 2, 3), Direction.EAST);
        });
        later(30, () -> report(l, 0));
    }

    private static void netherFog(ServerLevel l) {
        MinecraftServer server = l.getServer();
        ServerPlayer me = VisualCheck.player(server);
        ServerLevel nether = server.getLevel(Level.NETHER);
        VisualCheck.clear(l, BASE, 18);
        fresh(server, "COLUMN*2");
        VisualCheck.platform(l, BASE, 16);
        walls(l, Blocks.STONE_BRICKS, null);
        props(l);
        if (nether == null) {
            WitchsAtelier.LOGGER.warn("[visualcheck] no Nether to draw a Sill in");
            return;
        }
        VisualCheck.clear(nether, BASE, 6);
        VisualCheck.platform(nether, BASE, 5);
        for (int y = 1; y <= 5; y++) for (int z = -4; z <= 4; z++) nether.setBlock(BASE.offset(-4, y, z), Blocks.NETHER_BRICKS.defaultBlockState(), 3);
        force(l, FAR1200, false);
        force(nether, BASE, true);
        window(nether, BASE.offset(-4, 2, 0), Direction.EAST, me, InkType.CONJURING, COLUMNS);
        later(10, () -> window(l, BASE.offset(-6, 2, 0), Direction.EAST, me, InkType.CONJURING, COLUMNS));
        later(30, () -> report(l, 0));
    }

    private static void irisStage(ServerLevel l) {
        MinecraftServer server = l.getServer();
        ServerPlayer me = VisualCheck.player(server);
        VisualCheck.clear(l, BASE, 18);
        VisualCheck.wipeFar(l, FAR72);
        VisualCheck.clear(l, FAR72, 8);
        fresh(server, "RADIAL*2", "LEVITATION*2");
        force(l, FAR1200, false);
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether != null) force(nether, BASE, false);
        VisualCheck.platform(l, BASE, 16);
        VisualCheck.platform(l, FAR72, 6);
        walls(l, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS);
        props(l);
        farGarden(l);
        window(l, BASE.offset(-6, 2, 0), Direction.EAST, me, InkType.CONJURING, RADIALS);
        window(l, BASE.offset(6, 2, 0), Direction.WEST, me, InkType.CONJURING, RADIALS);
        window(l, FAR72.offset(0, 2, 0), Direction.EAST, me, InkType.CONJURING, LIFTS);
        if (me != null) me.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        report(l, 0);
    }

    private static void irisDuring(ServerLevel l, int t) {
        ServerPlayer me = VisualCheck.player(l.getServer());
        if (me == null) return;
        if (t == 25) me.setItemInHand(InteractionHand.MAIN_HAND, page(LIFTS));
        if (t == 80) me.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        report(l, t);
    }

    private static void refuseStage(ServerLevel l) {
        MinecraftServer server = l.getServer();
        VisualCheck.clear(l, BASE, 18);
        fresh(server, "COLUMN+RADIAL");
        VisualCheck.platform(l, BASE, 16);
        walls(l, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS);
        props(l);
        ServerPlayer me = VisualCheck.player(server);
        if (me != null) me.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // a stranger's Sill in blood, lit the way a world finds it on waking
        quietWindow(l, BASE.offset(6, 2, 0), Direction.WEST, STRANGER, InkType.BLOOD, MIXED);
    }

    private static void refuseDuring(ServerLevel l, int t) {
        MinecraftServer server = l.getServer();
        ServerPlayer me = VisualCheck.player(server);
        if (me == null) return;
        if (t == 1) {
            WindowwayNetwork.Station sill = WindowwayNetwork.get(server).sill("COLUMN+RADIAL");
            if (sill == null) WitchsAtelier.LOGGER.warn("[visualcheck] the blood Sill did not light");
            else WindowwayService.discover(server, me.getUUID(), sill.wid());
            window(l, BASE.offset(-6, 2, 0), Direction.EAST, me, InkType.CONJURING, MIXED);
        }
        report(l, t);
    }

    private static void blockedStage(ServerLevel l) {
        MinecraftServer server = l.getServer();
        ServerPlayer me = VisualCheck.player(server);
        VisualCheck.clear(l, BASE, 18);
        VisualCheck.wipeFar(l, FAR72);
        VisualCheck.clear(l, FAR72, 8);
        fresh(server, "EYE+LEVITATION");
        VisualCheck.platform(l, BASE, 16);
        VisualCheck.platform(l, FAR72, 6);
        walls(l, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS);
        props(l);
        farGarden(l);
        if (me != null) me.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        window(l, FAR72.offset(0, 2, 0), Direction.EAST, me, InkType.CONJURING, DIAMONDS);
        window(l, BASE.offset(-6, 2, 0), Direction.EAST, me, InkType.CONJURING, DIAMONDS);
        report(l, 0);
    }

    /** Once the windows are open, the far side is walled up with glass: nowhere to step out. */
    private static void blockedDuring(ServerLevel l, int t) {
        if (t == 2) {
            for (int z = -1; z <= 1; z++) for (int x = 1; x <= 3; x++) for (int y = 1; y <= 4; y++)
                l.setBlock(FAR72.offset(x, y, z), Blocks.GLASS.defaultBlockState(), 3);
        }
        report(l, t);
    }

    // ---- client side ------------------------------------------------------------------------------------------

    /**
     * Each client tick of a waymark shot. The refusal films watch from behind the witch and walk her into the window:
     * the crossing must be seen by her own client, the way a player makes it.
     */
    static void clientTick(Minecraft mc, VisualCheck.Shot now, int filmTick) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) runDue(server);
        if (!now.name().startsWith("waymark_refuse") || mc.player == null) return;
        if (filmTick < 0) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            return;
        }
        // a steady walk in from 4.5 blocks out, just through the plane of the window; once she is set back out she
        // stands still, as someone would who has been turned away
        if (filmTick == 20 || filmTick == 70) walkingIn = true;
        double x = mc.player.getX();
        if (x > lastX + 0.2) walkingIn = false;
        lastX = x;
        if (walkingIn && filmTick < 110 && x > -5.4) mc.player.setDeltaMovement(-0.28, 0, 0);
        if ((filmTick >= 30 && filmTick <= 60) || (filmTick >= 78 && filmTick <= 100))
            WitchsAtelier.LOGGER.info("[visualcheck] client t={} at {} yaw {} walking {}", filmTick,
                    String.format("%.2f %.2f %.2f", x, mc.player.getY(), mc.player.getZ()), String.format("%.1f", mc.player.getYRot()), walkingIn);
    }

    /** Server work a scene leaves for later, with the server tick it is due on. */
    private static final List<Object[]> LATER = new ArrayList<>();

    private static void later(int ticks, Runnable r) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;
        synchronized (LATER) {
            LATER.add(new Object[]{server.getTickCount() + ticks, r});
        }
    }

    /** Runs, on the server, the work that has come due; called each client tick of a waymark shot. */
    private static void runDue(MinecraftServer server) {
        server.execute(() -> {
            List<Runnable> due = new ArrayList<>();
            synchronized (LATER) {
                LATER.removeIf(e -> {
                    if ((int) e[0] > server.getTickCount()) return false;
                    due.add((Runnable) e[1]);
                    return true;
                });
            }
            for (Runnable r : due) {
                try {
                    r.run();
                } catch (Throwable t) {
                    WitchsAtelier.LOGGER.warn("[visualcheck] waymark step failed", t);
                }
            }
        });
    }

    private static void force(ServerLevel l, BlockPos at, boolean on) {
        l.setChunkForced(at.getX() >> 4, at.getZ() >> 4, on);
    }

    private static boolean walkingIn;
    private static double lastX;

    static void finish(Minecraft mc) {
        walkingIn = false;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    /** Forgets the stations a scene draws with, so marks left by an earlier run do not claim them first. */
    private static void fresh(MinecraftServer server, String... marks) {
        Set<String> these = Set.of(marks);
        for (ServerLevel lv : server.getAllLevels()) {
            for (ActiveSpell s : GlyphService.activeSpells(lv)) if (these.contains(s.windowMark)) GlyphService.stop(lv, s);
        }
        WindowwayNetwork net = WindowwayNetwork.get(server);
        for (String m : marks) for (WindowwayNetwork.Station st : List.copyOf(net.withMark(m))) net.forget(st.wid());
    }

    private static void walls(ServerLevel l, Block west, Block east) {
        for (int y = 1; y <= 5; y++) for (int z = -8; z <= 8; z++) {
            if (west != null) l.setBlock(BASE.offset(-6, y, z), west.defaultBlockState(), 3);
            if (east != null) l.setBlock(BASE.offset(6, y, z), east.defaultBlockState(), 3);
        }
    }

    /** A few things in the hall, out of the way, so each picture shows where it looks. */
    private static void props(ServerLevel l) {
        l.setBlock(BASE.offset(-3, 1, -7), Blocks.RED_WOOL.defaultBlockState(), 3);
        l.setBlock(BASE.offset(-3, 1, 7), Blocks.BLUE_WOOL.defaultBlockState(), 3);
        l.setBlock(BASE.offset(3, 1, -7), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        l.setBlock(BASE.offset(3, 2, -7), Blocks.LANTERN.defaultBlockState(), 3);
        l.setBlock(BASE.offset(3, 1, 7), Blocks.OAK_LOG.defaultBlockState(), 3);
        l.setBlock(BASE.offset(3, 2, 7), Blocks.OAK_LEAVES.defaultBlockState(), 3);
    }

    /** The far place of the iris and walled-up films: gold, a chest, a little tree and a pig. */
    private static void farGarden(ServerLevel l) {
        for (int y = 1; y <= 5; y++) for (int z = -3; z <= 3; z++) l.setBlock(FAR72.offset(0, y, z), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 3);
        for (int k = -2; k <= 2; k++) l.setBlock(FAR72.offset(5, 1, k), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        l.setBlock(FAR72.offset(4, 1, 2), Blocks.CHEST.defaultBlockState(), 3);
        l.setBlock(FAR72.offset(4, 2, -1), Blocks.TORCH.defaultBlockState(), 3);
        l.setBlock(FAR72.offset(6, 1, 0), Blocks.OAK_LOG.defaultBlockState(), 3);
        l.setBlock(FAR72.offset(6, 2, 0), Blocks.OAK_LOG.defaultBlockState(), 3);
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) l.setBlock(FAR72.offset(6 + dx, 3, dz), Blocks.OAK_LEAVES.defaultBlockState(), 3);
        var pig = net.minecraft.world.entity.EntityType.PIG.create(l);
        if (pig != null) {
            pig.moveTo(FAR72.getX() + 3.5, FAR72.getY() + 1, FAR72.getZ() - 1.5, 120, 0);
            pig.setNoAi(true);
            l.addFreshEntity(pig);
        }
    }

    private static GlyphSketch sketch(Pt c, double radius, double window, double sign, SignType... signs) {
        GlyphSketch g = new GlyphSketch(c, radius).ring(0, 362).centerSign(SignType.WINDOW, window);
        for (int k = 0; k < signs.length; k++) g.signs(signs[k], 1, Orientation.OUTWARD, sign, 90 + k * 360.0 / signs.length);
        return g;
    }

    private static SurfacePlane plane(BlockPos block, Direction face) {
        return SurfacePlane.of(block, face);
    }

    private static Pt uv(BlockPos block, Direction face) {
        Vec3 c = Vec3.atCenterOf(block).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5));
        return plane(block, face).toUV(c);
    }

    private static List<InkStroke> window(ServerLevel l, BlockPos block, Direction face, ServerPlayer who, InkType ink, SignType... signs) {
        return GlyphService.inscribe(l, plane(block, face), sketch(uv(block, face), 2.0, 1.1, 0.4, signs).strokes, ink, who);
    }

    private static void plain(ServerLevel l, BlockPos block, Direction face) {
        GlyphService.inscribe(l, plane(block, face), new GlyphSketch(uv(block, face), 2.0).ring(0, 362).centerSign(SignType.WINDOW, 1.1).strokes, null);
    }

    /** Ink laid down and lit the way a world finds it on waking, read quietly, so blood ink never misfires. */
    private static void quietWindow(ServerLevel l, BlockPos block, Direction face, UUID author, InkType ink, SignType... signs) {
        List<InkStroke> ink2 = GlyphService.inkWithoutReading(l, plane(block, face), sketch(uv(block, face), 2.0, 1.1, 0.4, signs).strokes, ink, author);
        var data = GlyphLevelData.get(l);
        for (InkStroke st : ink2) data.setState(st.id(), StrokeState.ACTIVE, 0xFFFFFF);
        GlyphService.rescanLitGlyphs(l);
    }

    private static ItemStack page(SignType... signs) {
        GlyphSketch g = sketch(new Pt(0, 0), 0.85, 0.5, 0.28, signs);
        var strokes = new ArrayList<GlyphPages.PageStroke>();
        for (double[] d : g.strokes) {
            float[] f = new float[d.length];
            for (int i = 0; i < d.length; i++) f[i] = (float) d[i];
            strokes.add(new GlyphPages.PageStroke(f, InkType.CONJURING));
        }
        ItemStack paper = new ItemStack(Items.PAPER);
        paper.set(ModComponents.GLYPH_PAGES.get(), new GlyphPages(List.of(new GlyphPages.Page(List.copyOf(strokes))), 0));
        return paper;
    }

    /** Logs every window that is open and where it looks, now and then through a film. */
    private static void report(ServerLevel l, int t) {
        if (t != 0 && t != 1 && t % 10 != 0) return;
        for (ServerLevel lv : l.getServer().getAllLevels()) {
            for (ActiveSpell s : GlyphService.activeSpells(lv)) {
                PortalFrame f = WindowwayService.frameOf(s);
                if (f == null) continue;
                var st = WindowwayService.stationOf(lv, s);
                WitchsAtelier.LOGGER.info("[visualcheck] t={} {} window mark='{}' role={} id={} link={} at {}", t, lv.dimension().location(),
                        s.windowMark, st == null ? "-" : st.role(), f.id(), f.link(), BlockPos.containing(f.center()));
            }
        }
    }
}
