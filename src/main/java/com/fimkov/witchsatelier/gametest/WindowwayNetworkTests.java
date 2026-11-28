package com.fimkov.witchsatelier.gametest;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.model.Orientation;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import com.fimkov.witchsatelier.glyph.recognize.GlyphReading;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.recognize.RingFinder;
import com.fimkov.witchsatelier.glyph.storage.GlyphLevelData;
import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import com.fimkov.witchsatelier.glyph.surface.StrokeState;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.registry.ModBlocks;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.spell.ActiveSpell;
import com.fimkov.witchsatelier.windowway.PortalFrame;
import com.fimkov.witchsatelier.windowway.WindowMark;
import com.fimkov.witchsatelier.windowway.WindowwayNetwork;
import com.fimkov.witchsatelier.windowway.WindowwayNetwork.Role;
import com.fimkov.witchsatelier.windowway.WindowwayNetwork.Station;
import com.fimkov.witchsatelier.windowway.WindowwayService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Marked Windowways and their network. Each test keeps a batch of its own: marks are shared by the whole server, so two
 * tests drawing at once would find each other's Sills, and plain windows of one witch pair across tests too.
 */
@GameTestHolder(WitchsAtelier.MODID)
@PrefixGameTestTemplate(false)
public final class WindowwayNetworkTests {

    /** A wall to draw a window on: the block, the face drawn on, where a crosser stands and faces, and a spot to hold a page up. */
    private record Spot(BlockPos block, Direction face, Vec3 toFace, Vec3 stand, float yaw, Vec3 front) {
        Vec3 center() {
            return Vec3.atCenterOf(block).add(toFace);
        }
    }

    private static final Spot WEST = new Spot(new BlockPos(1, 2, 5), Direction.EAST, new Vec3(0.5, 0, 0), new Vec3(1.9, 1, 5.5), 90, new Vec3(3.5, 1, 5.5));
    private static final Spot EAST = new Spot(new BlockPos(9, 2, 5), Direction.WEST, new Vec3(-0.5, 0, 0), new Vec3(9.1, 1, 5.5), -90, new Vec3(7.5, 1, 5.5));
    private static final Spot NORTH = new Spot(new BlockPos(5, 2, 1), Direction.SOUTH, new Vec3(0, 0, 0.5), new Vec3(5.5, 1, 1.9), 180, new Vec3(5.5, 1, 3.5));
    private static final Spot SOUTH = new Spot(new BlockPos(5, 2, 9), Direction.NORTH, new Vec3(0, 0, -0.5), new Vec3(5.5, 1, 9.1), 0, new Vec3(5.5, 1, 7.5));

    private static void room(GameTestHelper h) {
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
        for (int y = 1; y <= 4; y++) {
            for (int k = 2; k <= 8; k++) {
                h.setBlock(1, y, k, Blocks.STONE);
                h.setBlock(9, y, k, Blocks.STONE);
                h.setBlock(k, y, 1, Blocks.STONE);
                h.setBlock(k, y, 9, Blocks.STONE);
            }
        }
    }

    /**
     * Clears the marks a test draws with: a test that failed half way leaves its windows lit, and they would hold the
     * marks against every later test.
     */
    private static void fresh(GameTestHelper h, String... marks) {
        var server = h.getLevel().getServer();
        Set<String> these = Set.of(marks);
        for (ServerLevel l : server.getAllLevels()) {
            for (ActiveSpell s : GlyphService.activeSpells(l)) if (these.contains(s.windowMark)) GlyphService.stop(l, s);
        }
        var net = WindowwayNetwork.get(server);
        for (String m : marks) for (Station st : net.withMark(m)) net.forget(st.wid());
    }

    @SuppressWarnings("removal")
    private static ServerPlayer witch(GameTestHelper h) {
        return h.makeMockServerPlayerInLevel();
    }

    private static void leave(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
    }

    private static GlyphSketch sketch(Pt c, double radius, double window, double sign, SignType... signs) {
        GlyphSketch g = new GlyphSketch(c, radius).ring(0, 362).centerSign(SignType.WINDOW, window);
        for (int k = 0; k < signs.length; k++) g.signs(signs[k], 1, Orientation.OUTWARD, sign, 90 + k * 360.0 / signs.length);
        return g;
    }

    private static SurfacePlane plane(GameTestHelper h, Spot s) {
        return SurfacePlane.of(h.absolutePos(s.block()), s.face());
    }

    private static Pt uv(GameTestHelper h, Spot s) {
        return plane(h, s).toUV(h.absoluteVec(s.center()));
    }

    private static List<InkStroke> window(GameTestHelper h, Spot s, ServerPlayer who, InkType ink, SignType... signs) {
        return GlyphService.inscribe(h.getLevel(), plane(h, s), sketch(uv(h, s), 2.0, 1.1, 0.4, signs).strokes, ink, who);
    }

    private static List<InkStroke> window(GameTestHelper h, Spot s, ServerPlayer who, SignType... signs) {
        return window(h, s, who, InkType.CONJURING, signs);
    }

    /** Ink laid down and lit the way a world finds it on waking, read quietly, so even risky ink never misfires. */
    private static List<InkStroke> litQuietly(GameTestHelper h, Spot s, UUID author, InkType ink, SignType... signs) {
        ServerLevel level = h.getLevel();
        List<InkStroke> ink2 = GlyphService.inkWithoutReading(level, plane(h, s), sketch(uv(h, s), 2.0, 1.1, 0.4, signs).strokes, ink, author);
        var data = GlyphLevelData.get(level);
        for (InkStroke st : ink2) data.setState(st.id(), StrokeState.ACTIVE, 0xFFFFFF);
        GlyphService.rescanLitGlyphs(level);
        return ink2;
    }

    private static ActiveSpell spell(GameTestHelper h, Spot s) {
        SurfacePlane p = plane(h, s);
        Vec3 c = h.absoluteVec(s.center());
        return GlyphService.activeSpells(h.getLevel()).stream()
                .filter(x -> p.equals(x.plane) && x.program.origin().distanceTo(c) < 1.5).findFirst().orElse(null);
    }

    private static PortalFrame frame(GameTestHelper h, Spot s) {
        ActiveSpell sp = spell(h, s);
        PortalFrame f = sp == null ? null : WindowwayService.frameOf(sp);
        if (f == null) h.fail("no open window on the " + s.face().getOpposite() + " wall" + (sp == null ? " (no spell)" : " (spell " + sp.windowMark + ")"));
        return f;
    }

    private static Station station(GameTestHelper h, Spot s) {
        ActiveSpell sp = spell(h, s);
        Station st = sp == null ? null : WindowwayService.stationOf(h.getLevel(), sp);
        if (st == null) h.fail("no station on the " + s.face().getOpposite() + " wall" + (sp == null ? " (no spell)" : " (mark '" + sp.windowMark + "')"));
        return st;
    }

    /** Lets a witch find the window on a wall, as if she had stood by it a while. */
    private static void meet(GameTestHelper h, ServerPlayer who, Spot s) {
        WindowwayService.discover(h.getLevel().getServer(), who.getUUID(), station(h, s).wid());
    }

    private static void wipe(GameTestHelper h, List<?>... inks) {
        List<Long> ids = new ArrayList<>();
        for (List<?> l : inks) for (Object o : l) ids.add(((InkStroke) o).id());
        GlyphService.eraseStrokes(h.getLevel(), ids);
    }

    private static void standAt(GameTestHelper h, ServerPlayer p, Vec3 rel, float yaw) {
        Vec3 a = h.absoluteVec(rel);
        p.teleportTo(h.getLevel(), a.x, a.y, a.z, yaw, 0);
    }

    /** Steps into a window from its front, as the client tells it, with the link it saw. */
    private static void stepInto(GameTestHelper h, ServerPlayer p, Spot s, long expected) {
        standAt(h, p, s.stand(), s.yaw());
        WindowwayService.handleCross(p, frame(h, s).id(), expected);
    }

    private static void stepInto(GameTestHelper h, ServerPlayer p, Spot s) {
        PortalFrame f = frame(h, s);
        stepInto(h, p, s, f.link());
    }

    private static boolean before(GameTestHelper h, ServerPlayer p, Spot s) {
        Vec3 c = h.absoluteVec(s.center());
        return p.level() == h.getLevel() && p.position().add(0, 0.9, 0).distanceTo(c) < 2.6
                && PortalFrame.of(0, c, new Vec3(0, 1, 0), Vec3.atLowerCornerOf(s.face().getNormal()), 2, -1).side(p.position().add(0, 0.9, 0)) > 0;
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

    private static GlyphReading reading(SignType center, Sigil sigil, int unknown, boolean closed, SignType... signs) {
        var ring = new RingFinder.Ring(new Pt(0, 0), 1, 1, 1, 0, closed, List.of());
        List<GlyphReading.ReadSign> read = new ArrayList<>();
        for (int i = 0; i < signs.length; i++) {
            read.add(new GlyphReading.ReadSign(signs[i], i % 2 == 1, Orientation.values()[i % Orientation.values().length], i * 0.7,
                    0.2 + 0.05 * i, 0.9, new Pt(Math.cos(i), Math.sin(i)), List.of()));
        }
        return new GlyphReading(ring, List.of(), sigil, center, 0.9, read, unknown, new Pt(0, 0), 0.9, List.of());
    }

    private static final SignType[] COLUMNS = {SignType.COLUMN, SignType.COLUMN};
    private static final SignType[] RADIALS = {SignType.RADIAL, SignType.RADIAL};
    private static final SignType[] LIFTS = {SignType.LEVITATION, SignType.LEVITATION};
    private static final SignType[] EYES = {SignType.EYE, SignType.EYE};
    private static final SignType[] WAYMARK = {SignType.BIRD, SignType.BIRD, SignType.EYE};

    @GameTest(template = "empty", timeoutTicks = 20, batch = "ww_marks")
    public static void markIgnoresOrderAndModifiers(GameTestHelper h) {
        String a = WindowMark.of(reading(SignType.WINDOW, null, 0, true, SignType.BIRD, SignType.EYE, SignType.BIRD));
        String b = WindowMark.of(reading(SignType.WINDOW, null, 0, true, SignType.EYE, SignType.BIRD, SignType.BIRD));
        if (!a.equals("BIRD*2+EYE")) h.fail("two Birds and an Eye should read BIRD*2+EYE, got '" + a + "'");
        if (!a.equals(b)) h.fail("order changed the mark: " + a + " / " + b);
        String guarded = WindowMark.of(reading(SignType.WINDOW, null, 0, true, SignType.TREAD, SignType.BIRD, SignType.DIAMOND, SignType.EYE, SignType.BIRD));
        if (!guarded.equals(a)) h.fail("Tread and Diamond are not part of a mark, got '" + guarded + "'");
        if (!WindowMark.of(reading(SignType.WINDOW, null, 0, true, SignType.BIRD)).isEmpty()) h.fail("one sign is not a mark");
        if (!WindowMark.of(reading(SignType.WINDOW, null, 0, true, SignType.BIRD, SignType.TREAD, SignType.COLLECTION)).isEmpty())
            h.fail("one naming sign and two modifiers is not a mark");
        if (!WindowMark.of(reading(SignType.WINDOW, null, 1, true, SignType.BIRD, SignType.EYE)).isEmpty()) h.fail("an unread sign spoils the mark");
        if (!WindowMark.of(reading(SignType.WINDOW, Sigil.FIRE, 0, true, SignType.BIRD, SignType.EYE)).isEmpty()) h.fail("a sigil makes it no Window");
        if (!WindowMark.of(reading(SignType.VISION, null, 0, true, SignType.BIRD, SignType.EYE)).isEmpty()) h.fail("only a Window has a mark");
        if (!WindowMark.of(reading(SignType.WINDOW, null, 0, false, SignType.BIRD, SignType.EYE)).isEmpty()) h.fail("an open ring has no mark");

        String held = WindowMark.ofStack(page(WAYMARK));
        if (!held.equals("BIRD*2+EYE")) h.fail("a page with the waymark should read BIRD*2+EYE, got '" + held + "'");
        String cols = WindowMark.ofStack(page(COLUMNS));
        if (!cols.equals("COLUMN*2")) h.fail("a page with two Columns should read COLUMN*2, got '" + cols + "'");
        if (!WindowMark.ofStack(new ItemStack(Items.LEATHER_BOOTS)).isEmpty()) h.fail("boots are no address");
        if (WindowMark.tint("COLUMN*2") != WindowMark.tint("COLUMN*2") || WindowMark.tint("COLUMN*2") == WindowMark.tint("RADIAL*2"))
            h.fail("tints should be steady per mark and differ between marks");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "ww_kin")
    public static void sillThenKinLinkBothWays(GameTestHelper h) {
        room(h);
        fresh(h, "COLUMN*2");
        ServerPlayer witch = witch(h);
        var a = window(h, WEST, witch, COLUMNS);
        List<InkStroke> b = new ArrayList<>();
        h.startSequence()
                .thenExecuteAfter(2, () -> b.addAll(window(h, EAST, witch, COLUMNS)))
                .thenExecuteAfter(3, () -> {
                    Station sa = station(h, WEST), sb = station(h, EAST);
                    if (!sa.mark().equals("COLUMN*2")) h.fail("the mark should read COLUMN*2, got " + sa.mark());
                    if (sa.role() != Role.SILL || sb.role() != Role.KIN) h.fail("roles " + sa.role() + "/" + sb.role());
                    if (frame(h, WEST).link() != frame(h, EAST).id() || frame(h, EAST).link() != frame(h, WEST).id()) h.fail("Sill and Kin should answer each other");
                    if ((frame(h, WEST).id() & WindowwayService.TRANSIENT) != 0) h.fail("a lasting window should carry its lasting id");
                    h.assertBlockPresent(ModBlocks.WINDOWWAY_GAP.get(), new BlockPos(1, 2, 5));
                    h.assertBlockPresent(ModBlocks.WINDOWWAY_GAP.get(), new BlockPos(9, 2, 5));
                })
                .thenExecuteAfter(10, () -> {
                    stepInto(h, witch, WEST);
                    if (!before(h, witch, EAST)) h.fail("the witch should come out of her Kin, she is at " + witch.position().subtract(h.absoluteVec(Vec3.ZERO)));
                    wipe(h, a, b);
                    leave(h, witch);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "ww_visitor")
    public static void strangersCopyIsVisitor(GameTestHelper h) {
        room(h);
        fresh(h, "RADIAL*2");
        ServerPlayer owner = witch(h), stranger = witch(h);
        var a = window(h, WEST, owner, RADIALS);
        List<InkStroke> b = new ArrayList<>();
        h.startSequence()
                .thenExecuteAfter(2, () -> b.addAll(window(h, EAST, stranger, RADIALS)))
                .thenExecuteAfter(3, () -> {
                    if (station(h, EAST).role() != Role.VISITOR) h.fail("a stranger's window with the mark only visits, got " + station(h, EAST).role());
                    if (frame(h, EAST).link() >= 0) h.fail("a stranger's copy must not open onto a Sill she has never found");
                    meet(h, stranger, WEST);
                    if (frame(h, EAST).link() != frame(h, WEST).id()) h.fail("once she has found it, the visitor should look at the Sill");
                    if (frame(h, WEST).link() >= 0) h.fail("the Sill should not look back at a visitor");
                    h.assertBlockPresent(Blocks.STONE, new BlockPos(1, 2, 5));
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 2, () -> {
                    stepInto(h, stranger, EAST);
                    if (!before(h, stranger, WEST)) h.fail("the stranger should come out of the Sill");
                    wipe(h, a, b);
                    leave(h, owner, stranger);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "ww_third")
    public static void thirdOwnerWindowIsVisitor(GameTestHelper h) {
        room(h);
        fresh(h, "LEVITATION*2");
        ServerPlayer witch = witch(h);
        var a = window(h, WEST, witch, LIFTS);
        List<InkStroke> b = new ArrayList<>(), c = new ArrayList<>();
        h.startSequence()
                .thenExecuteAfter(2, () -> b.addAll(window(h, EAST, witch, LIFTS)))
                .thenExecuteAfter(2, () -> c.addAll(window(h, NORTH, witch, LIFTS)))
                .thenExecuteAfter(3, () -> {
                    if (station(h, NORTH).role() != Role.VISITOR) h.fail("a third window should visit, got " + station(h, NORTH).role());
                    if (frame(h, NORTH).link() != frame(h, WEST).id()) h.fail("the third window should look at the Sill");
                    if (frame(h, WEST).link() != frame(h, EAST).id()) h.fail("the Sill should still answer its Kin");
                    wipe(h, a, b, c);
                    leave(h, witch);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "ww_retune")
    public static void heldPageRetunesThenSettles(GameTestHelper h) {
        room(h);
        fresh(h, "COLUMN*2", "RADIAL*2");
        ServerPlayer witch = witch(h);
        var a = window(h, WEST, witch, COLUMNS);
        var b = window(h, EAST, witch, RADIALS);
        long[] at = new long[1];
        h.startSequence()
                .thenExecuteAfter(3, () -> {
                    if (frame(h, WEST).link() >= 0 || frame(h, EAST).link() >= 0) h.fail("two lone Sills look nowhere");
                    witch.setItemInHand(InteractionHand.MAIN_HAND, page(RADIALS));
                    standAt(h, witch, WEST.front(), WEST.yaw());
                })
                .thenWaitUntil(() -> { if (frame(h, WEST).link() != frame(h, EAST).id()) h.fail("the page should turn the window"); })
                .thenExecute(() -> {
                    at[0] = h.getTick();
                    if (!WindowwayService.tuned(spell(h, WEST))) h.fail("the window should know it is held");
                    stepInto(h, witch, WEST);
                    if (before(h, witch, EAST)) h.fail("a window that has only just turned must not take anyone yet");
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 1, () -> {
                    stepInto(h, witch, WEST);
                    if (!before(h, witch, EAST)) h.fail("once settled the window should lead to the page's Sill");
                    if (frame(h, EAST).link() != frame(h, WEST).id()) h.fail("the lone Sill should open back toward where she came from");
                    standAt(h, witch, new Vec3(8.5, 1, 0.5), 0);
                })
                .thenWaitUntil(() -> { if (frame(h, WEST).link() >= 0) h.fail("with the page gone and no one following, the window turns back"); })
                .thenExecute(() -> {
                    if (h.getTick() - at[0] < WindowwayService.HOLD_AFTER_CROSS) h.fail("it turned back too soon, after " + (h.getTick() - at[0]));
                    wipe(h, a, b);
                    leave(h, witch);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "ww_first_tuner")
    public static void firstTunerHolds(GameTestHelper h) {
        room(h);
        fresh(h, "COLUMN*2", "RADIAL*2", "EYE*2");
        ServerPlayer first = witch(h), second = witch(h);
        var a = window(h, WEST, first, COLUMNS);
        var b = window(h, EAST, first, RADIALS);
        var c = window(h, NORTH, second, EYES);
        h.startSequence()
                .thenExecuteAfter(3, () -> {
                    first.setItemInHand(InteractionHand.MAIN_HAND, page(RADIALS));
                    second.setItemInHand(InteractionHand.OFF_HAND, page(EYES));
                    standAt(h, first, new Vec3(4.5, 1, 5.5), 90);
                })
                .thenWaitUntil(() -> { if (frame(h, WEST).link() != frame(h, EAST).id()) h.fail("the first page should turn the window"); })
                .thenExecute(() -> standAt(h, second, new Vec3(2.6, 1, 5.5), 90))
                .thenExecuteAfter(12, () -> {
                    if (frame(h, WEST).link() != frame(h, EAST).id()) h.fail("a second page must not take the window from the first");
                    standAt(h, first, new Vec3(8.5, 1, 0.5), 0);
                })
                .thenWaitUntil(() -> { if (frame(h, WEST).link() != frame(h, NORTH).id()) h.fail("once the first has gone, the second may turn it"); })
                .thenExecute(() -> {
                    wipe(h, a, b, c);
                    leave(h, first, second);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "ww_stale")
    public static void staleOrSettlingCrossRejected(GameTestHelper h) {
        room(h);
        fresh(h, "EYE*2");
        ServerPlayer owner = witch(h), visitor = witch(h);
        var a = window(h, WEST, owner, EYES);
        List<InkStroke> b = new ArrayList<>();
        h.startSequence()
                .thenExecuteAfter(2, () -> b.addAll(window(h, EAST, visitor, EYES)))
                .thenExecuteAfter(3, () -> {
                    // found only now, the visitor turns toward the Sill: a turn, which must settle
                    meet(h, visitor, WEST);
                    stepInto(h, visitor, EAST);
                    if (before(h, visitor, WEST)) h.fail("a window that has just turned is still settling");
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 2, () -> {
                    stepInto(h, visitor, EAST, 12345);
                    if (before(h, visitor, WEST)) h.fail("a crossing toward a link the window no longer has must be refused");
                })
                .thenExecuteAfter(8, () -> {
                    stepInto(h, visitor, EAST);
                    if (!before(h, visitor, WEST)) h.fail("with the right link and the window settled she should come through");
                    wipe(h, a, b);
                    leave(h, owner, visitor);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 320, batch = "ww_back")
    public static void backLinkReturnsTraveller(GameTestHelper h) {
        room(h);
        fresh(h, "LEVITATION*2");
        ServerPlayer owner = witch(h), traveller = witch(h);
        var a = window(h, WEST, owner, LIFTS);
        List<InkStroke> b = new ArrayList<>();
        long[] arrived = new long[1];
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    meet(h, traveller, WEST);
                    b.addAll(window(h, EAST, traveller, LIFTS));
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 2, () -> {
                    stepInto(h, traveller, EAST);
                    if (!before(h, traveller, WEST)) h.fail("the visitor should lead to the Sill");
                    if (frame(h, WEST).link() != frame(h, EAST).id()) h.fail("the Sill should look back toward the visitor for a while");
                    arrived[0] = h.getTick();
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 2, () -> {
                    stepInto(h, traveller, WEST);
                    if (!before(h, traveller, EAST)) h.fail("stepping back into the Sill should return her, she is at "
                            + traveller.position().subtract(h.absoluteVec(Vec3.ZERO)));
                })
                .thenWaitUntil(() -> { if (frame(h, WEST).link() >= 0) h.fail("the way back should close again"); })
                .thenExecute(() -> {
                    if (h.getTick() - arrived[0] < WindowwayService.BACK_LINK_TICKS - 5) h.fail("the way back closed early");
                    wipe(h, a, b);
                    leave(h, owner, traveller);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "ww_discovery")
    public static void discoveryRequiredForStrangersSill(GameTestHelper h) {
        room(h);
        fresh(h, "COLUMN*2", "RADIAL*2");
        ServerPlayer owner = witch(h), stranger = witch(h);
        var a = window(h, NORTH, owner, COLUMNS);
        var b = window(h, SOUTH, stranger, RADIALS);
        Vec3 tune = new Vec3(5.5, 1, 8.3);
        h.startSequence()
                .thenExecuteAfter(3, () -> {
                    stranger.setItemInHand(InteractionHand.MAIN_HAND, page(COLUMNS));
                    standAt(h, stranger, tune, 0);
                })
                .thenExecuteAfter(15, () -> {
                    if (frame(h, SOUTH).link() >= 0) h.fail("someone else's Sill must not answer a page she has never stood by");
                    if (WindowwayNetwork.get(h.getLevel().getServer()).knows(stranger.getUUID(), station(h, NORTH).wid()))
                        h.fail("she has not been near it yet");
                    standAt(h, stranger, new Vec3(5.5, 1, 4.5), 180);
                })
                .thenWaitUntil(() -> {
                    if (!WindowwayNetwork.get(h.getLevel().getServer()).knows(stranger.getUUID(), station(h, NORTH).wid())) h.fail("not seen yet");
                })
                .thenExecute(() -> standAt(h, stranger, tune, 0))
                .thenWaitUntil(() -> { if (frame(h, SOUTH).link() != frame(h, NORTH).id()) h.fail("a Sill she has stood by answers her page"); })
                .thenExecute(() -> {
                    wipe(h, a, b);
                    leave(h, owner, stranger);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "ww_blood")
    public static void bloodSillTurnsAway(GameTestHelper h) {
        room(h);
        fresh(h, "EYE*2");
        ServerPlayer owner = witch(h), stranger = witch(h);
        var a = litQuietly(h, WEST, owner.getUUID(), InkType.BLOOD, EYES);
        List<InkStroke> b = new ArrayList<>();
        h.startSequence()
                .thenWaitUntil(() -> station(h, WEST))
                .thenExecute(() -> {
                    meet(h, stranger, WEST);
                    b.addAll(window(h, EAST, stranger, EYES));
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 3, () -> {
                    if (!station(h, WEST).blood()) h.fail("the Sill is drawn in blood");
                    if (station(h, EAST).role() != Role.VISITOR) h.fail("the stranger's window should visit");
                    stepInto(h, stranger, EAST);
                    if (before(h, stranger, WEST)) h.fail("a blood Sill takes in no one but its witch");
                    if (!before(h, stranger, EAST)) h.fail("she should be set back out where she tried to go in");
                })
                .thenExecuteAfter(8, () -> {
                    stepInto(h, owner, EAST);
                    if (!before(h, owner, WEST)) h.fail("its own witch may come home through anyone's window");
                    wipe(h, a, b);
                    leave(h, owner, stranger);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "ww_tread")
    public static void treadGatedSillRefusesWhileShut(GameTestHelper h) {
        room(h);
        fresh(h, "RADIAL*2");
        ServerPlayer owner = witch(h), visitor = witch(h);
        h.setBlock(0, 2, 5, Blocks.REDSTONE_BLOCK);
        var a = window(h, WEST, owner, SignType.RADIAL, SignType.RADIAL, SignType.TREAD);
        List<InkStroke> b = new ArrayList<>();
        h.startSequence()
                .thenWaitUntil(() -> {
                    Station s = station(h, WEST);
                    if (!s.gated() || !s.mark().equals("RADIAL*2")) h.fail("a Tread is no part of the mark but gates it: " + s.mark() + " gated=" + s.gated());
                })
                .thenExecute(() -> {
                    meet(h, visitor, WEST);
                    b.addAll(window(h, EAST, visitor, RADIALS));
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 3, () -> {
                    if (frame(h, EAST).link() != frame(h, WEST).id()) h.fail("the visitor should look at the gated Sill");
                    stepInto(h, visitor, EAST);
                    if (!before(h, visitor, WEST)) h.fail("while the Sill is held open she should come through");
                    standAt(h, visitor, new Vec3(8.0, 1, 8.0), 0);
                    h.setBlock(0, 2, 5, Blocks.AIR);
                })
                .thenWaitUntil(() -> {
                    ActiveSpell s = spell(h, WEST);
                    if (s == null || WindowwayService.frameOf(s) != null) h.fail("with the power gone the Sill should shut");
                })
                .thenExecute(() -> {
                    var net = WindowwayNetwork.get(h.getLevel().getServer());
                    Station st = net.sill("RADIAL*2");
                    if (st == null) h.fail("a shut Sill keeps its place");
                    stepInto(h, visitor, EAST);
                    if (before(h, visitor, WEST)) h.fail("a shut Sill accepts no arrivals");
                    standAt(h, visitor, new Vec3(8.0, 1, 8.0), 0);
                    h.setBlock(0, 2, 5, Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> { if (WindowwayService.frameOf(spell(h, WEST)) == null) h.fail("powered again, it opens"); })
                .thenExecuteAfter(8, () -> {
                    if (station(h, WEST).role() != Role.SILL) h.fail("reopened, it is still the Sill");
                    stepInto(h, visitor, EAST);
                    if (!before(h, visitor, WEST)) h.fail("open again, it takes her in");
                    wipe(h, a, b);
                    leave(h, owner, visitor);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "ww_tread_added")
    public static void addingTreadKeepsStation(GameTestHelper h) {
        room(h);
        fresh(h, "COLUMN*2");
        ServerPlayer witch = witch(h);
        h.setBlock(0, 2, 5, Blocks.REDSTONE_BLOCK);
        var a = new ArrayList<>(window(h, WEST, witch, COLUMNS));
        List<InkStroke> b = new ArrayList<>();
        UUID[] first = new UUID[1];
        h.startSequence()
                .thenExecuteAfter(3, () -> {
                    first[0] = station(h, WEST).wid();
                    b.addAll(window(h, EAST, witch, COLUMNS));
                })
                .thenExecuteAfter(3, () -> {
                    if (station(h, EAST).role() != Role.KIN) h.fail("the second window should be the Kin");
                    a.addAll(GlyphService.inscribe(h.getLevel(), plane(h, WEST),
                            new GlyphSketch(uv(h, WEST), 2.0).signs(SignType.TREAD, 1, Orientation.OUTWARD, 0.4, 0).strokes, InkType.CONJURING, witch));
                })
                .thenWaitUntil(() -> {
                    ActiveSpell s = spell(h, WEST);
                    if (s == null || WindowwayService.frameOf(s) == null || !station(h, WEST).gated()) h.fail("the Tread should gate the window and, powered, it opens");
                })
                .thenExecute(() -> {
                    Station s = station(h, WEST);
                    if (!s.wid().equals(first[0]) || s.role() != Role.SILL) h.fail("a Tread added to a Sill must not make it someone else: " + s.role());
                    if (station(h, EAST).role() != Role.KIN) h.fail("the Kin should stay the Kin");
                    if (frame(h, WEST).link() != frame(h, EAST).id()) h.fail("Sill and Kin should still answer each other");
                    wipe(h, a, b);
                    leave(h, witch);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "ww_exit")
    public static void blockedExitRefused(GameTestHelper h) {
        room(h);
        fresh(h, "LEVITATION*2");
        ServerPlayer owner = witch(h), visitor = witch(h);
        var a = window(h, EAST, owner, LIFTS);
        List<InkStroke> b = new ArrayList<>();
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    meet(h, visitor, EAST);
                    b.addAll(window(h, WEST, visitor, LIFTS));
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 3, () -> {
                    h.setBlock(new BlockPos(8, 1, 5), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                    for (int y = 1; y <= 3; y++) {
                        h.setBlock(7, y, 5, Blocks.STONE);
                        h.setBlock(6, y, 5, Blocks.STONE);
                    }
                    stepInto(h, visitor, WEST);
                    if (before(h, visitor, EAST)) h.fail("with no room in front of the Sill no one may be set down there");
                    if (!before(h, visitor, WEST)) h.fail("refused, she should stay where she was");
                })
                .thenExecuteAfter(8, () -> {
                    h.setBlock(8, 1, 5, Blocks.AIR);
                    for (int y = 1; y <= 3; y++) {
                        h.setBlock(7, y, 5, Blocks.AIR);
                        h.setBlock(6, y, 5, Blocks.AIR);
                    }
                    stepInto(h, visitor, WEST);
                    if (!before(h, visitor, EAST)) h.fail("cleared, the way is open");
                    wipe(h, a, b);
                    leave(h, owner, visitor);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "ww_far")
    public static void unloadedSillStillReached(GameTestHelper h) {
        room(h);
        fresh(h, "EYE*2");
        ServerLevel level = h.getLevel();
        ServerPlayer owner = witch(h), traveller = witch(h);
        BlockPos o = h.absolutePos(BlockPos.ZERO).offset(1200, 0, 0);
        int cx = (o.getX() + 9) >> 4, cz = (o.getZ() + 5) >> 4;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.setChunkForced(cx + dx, cz + dz, true);
        for (int x = 3; x <= 10; x++) for (int y = 0; y <= 5; y++) for (int z = 1; z <= 9; z++) {
            boolean wall = x == 9 && y >= 1 && y <= 4 && z >= 2 && z <= 8;
            level.setBlockAndUpdate(o.offset(x, y, z), y == 0 || wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        BlockPos farBlock = o.offset(9, 2, 5);
        SurfacePlane farPlane = SurfacePlane.of(farBlock, Direction.WEST);
        Vec3 farCenter = Vec3.atCenterOf(farBlock).add(-0.5, 0, 0);
        var a = GlyphService.inscribe(level, farPlane, sketch(farPlane.toUV(farCenter), 2.0, 1.1, 0.4, EYES).strokes, InkType.CONJURING, owner);
        List<InkStroke> b = new ArrayList<>();
        ActiveSpell[] far = new ActiveSpell[1];
        h.startSequence()
                .thenWaitUntil(() -> {
                    far[0] = GlyphService.activeSpells(level).stream().filter(s -> farPlane.equals(s.plane)).findFirst().orElse(null);
                    if (far[0] == null || WindowwayService.stationOf(level, far[0]) == null) h.fail("the far Sill should open");
                })
                .thenExecute(() -> {
                    WindowwayService.discover(level.getServer(), traveller.getUUID(), WindowwayService.stationOf(level, far[0]).wid());
                    b.addAll(window(h, WEST, traveller, EYES));
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.setChunkForced(cx + dx, cz + dz, false);
                })
                .thenWaitUntil(() -> { if (level.shouldTickBlocksAt(BlockPos.containing(farCenter))) h.fail("the far Sill should fall out of reach"); })
                .thenExecute(() -> WindowwayService.close(level, far[0].id, WindowwayService.Reason.SHUTDOWN))
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 2, () -> {
                    Station sill = WindowwayNetwork.get(level.getServer()).sill("EYE*2");
                    if (sill == null || frame(h, WEST).link() != sill.frameId()) h.fail("the visitor should look at the far Sill");
                    stepInto(h, traveller, WEST);
                    Vec3 p = traveller.position().add(0, 0.9, 0);
                    if (p.distanceTo(farCenter) > 2.6 || p.x > farCenter.x) h.fail("she should come out of the far Sill, 1200 blocks off; she is "
                            + p.distanceTo(farCenter) + " from it");
                })
                .thenExecute(() -> {
                    standAt(h, traveller, new Vec3(5.5, 1, 5.5), 0);
                    GlyphService.stop(level, far[0]);
                    wipe(h, a, b);
                    leave(h, owner, traveller);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "ww_restart")
    public static void shutdownKeepsNetwork(GameTestHelper h) {
        room(h);
        fresh(h, "BIRD*2+EYE", "RADIAL*2");
        ServerLevel level = h.getLevel();
        ServerPlayer owner = witch(h), stranger = witch(h);
        owner.setYRot(180);
        owner.setYHeadRot(180);
        var a = window(h, WEST, owner, WAYMARK);
        List<InkStroke> b = new ArrayList<>(), c = new ArrayList<>();
        BlockPos floor = h.absolutePos(new BlockPos(5, 0, 5));
        SurfacePlane fp = SurfacePlane.of(floor, Direction.UP);
        Vec3 fc = Vec3.atCenterOf(floor).add(0, 0.5, 0);
        var d = GlyphService.inscribe(level, fp, sketch(fp.toUV(fc), 2.0, 1.1, 0.4, RADIALS).strokes, InkType.CONJURING, owner);
        Spot[] walls = {WEST, EAST, NORTH};
        Station[] before = new Station[4];
        PortalFrame[] frames = new PortalFrame[4];
        // every test's floor lies in the same plane, so the floor window is told from the others by where it is
        java.util.function.Supplier<ActiveSpell> floorSpell = () -> GlyphService.activeSpells(level).stream()
                .filter(s -> fp.equals(s.plane) && s.program.origin().distanceTo(fc) < 1.5).findFirst().orElse(null);
        h.startSequence()
                .thenExecuteAfter(2, () -> b.addAll(window(h, EAST, owner, WAYMARK)))
                .thenExecuteAfter(2, () -> {
                    meet(h, stranger, WEST);
                    c.addAll(window(h, NORTH, stranger, WAYMARK));
                })
                .thenExecuteAfter(3, () -> {
                    for (int i = 0; i < 3; i++) { before[i] = station(h, walls[i]); frames[i] = frame(h, walls[i]); }
                    ActiveSpell fs = floorSpell.get();
                    before[3] = fs == null ? null : WindowwayService.stationOf(level, fs);
                    frames[3] = fs == null ? null : WindowwayService.frameOf(fs);
                    if (before[3] == null) h.fail("the floor window should open: " + (fs == null ? "no spell" : "mark '" + fs.windowMark + "'")
                            + "; reads as " + GlyphService.describeAround(level, fp, d.get(0).id()));
                    if (!before[0].mark().equals("BIRD*2+EYE")) h.fail("the waymark should read BIRD*2+EYE, got " + before[0].mark());
                    if (before[0].role() != Role.SILL || before[1].role() != Role.KIN || before[2].role() != Role.VISITOR) h.fail("roles before");
                    GlyphService.shutdown(level);
                    WindowwayService.closeAll(level);
                    var net = WindowwayNetwork.get(level.getServer());
                    for (Station s : before) if (!s.equals(net.station(s.wid()))) h.fail("stopping the world must not change the network");
                    owner.setYRot(-90);
                    owner.setYHeadRot(-90);
                    GlyphService.rescanLitGlyphs(level);
                })
                .thenWaitUntil(() -> {
                    for (Spot s : walls) frame(h, s);
                    if (floorSpell.get() == null || WindowwayService.frameOf(floorSpell.get()) == null) h.fail("the floor window should wake");
                })
                .thenExecute(() -> {
                    for (int i = 0; i < 3; i++) {
                        Station now = station(h, walls[i]);
                        if (!now.wid().equals(before[i].wid()) || now.role() != before[i].role()) h.fail("window " + i + " woke as someone else");
                        if (frame(h, walls[i]).link() != frames[i].link()) h.fail("window " + i + " woke looking elsewhere");
                        if (WindowwayService.tint(frames[i].id()) != WindowMark.tint(before[i].mark())) h.fail("window " + i + " woke in another colour");
                    }
                    PortalFrame f = WindowwayService.frameOf(floorSpell.get());
                    if (f.id() != frames[3].id() || f.v().distanceTo(frames[3].v()) > 1e-6) h.fail("the floor window should keep the way it faced, " + frames[3].v() + " now " + f.v());
                    wipe(h, a, b, c, d);
                    leave(h, owner, stranger);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "ww_promote")
    public static void erasedSillPromotesOwnersNext(GameTestHelper h) {
        room(h);
        fresh(h, "COLUMN*2");
        ServerPlayer owner = witch(h), stranger = witch(h);
        var a = window(h, WEST, owner, COLUMNS);
        List<InkStroke> b = new ArrayList<>(), c = new ArrayList<>();
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    meet(h, stranger, WEST);
                    b.addAll(window(h, NORTH, stranger, COLUMNS));
                })
                .thenExecuteAfter(2, () -> c.addAll(window(h, EAST, owner, COLUMNS)))
                .thenExecuteAfter(3, () -> {
                    if (station(h, WEST).role() != Role.SILL || station(h, NORTH).role() != Role.VISITOR || station(h, EAST).role() != Role.KIN)
                        h.fail("roles before: " + station(h, WEST).role() + " " + station(h, NORTH).role() + " " + station(h, EAST).role());
                    meet(h, stranger, EAST);
                    wipe(h, a);
                })
                .thenExecuteAfter(2, () -> {
                    if (station(h, EAST).role() != Role.SILL) h.fail("the mark should pass to its witch's next window before a stranger's older one");
                    if (station(h, NORTH).role() != Role.VISITOR || frame(h, NORTH).link() != frame(h, EAST).id()) h.fail("the visitor should now look at the new Sill");
                    if (frame(h, EAST).link() >= 0) h.fail("the new Sill has no Kin");
                    wipe(h, c);
                })
                .thenExecuteAfter(2, () -> {
                    if (station(h, NORTH).role() != Role.SILL) h.fail("with its witch's windows gone, the mark passes to the stranger");
                    if (frame(h, NORTH).link() >= 0) h.fail("a lone Sill looks nowhere");
                    wipe(h, b);
                    leave(h, owner, stranger);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "ww_remark")
    public static void remarkingLiveWindowReRegisters(GameTestHelper h) {
        room(h);
        fresh(h, "COLUMN*2", "COLUMN*3");
        ServerPlayer witch = witch(h);
        var a = new ArrayList<>(window(h, WEST, witch, COLUMNS));
        UUID[] first = new UUID[1];
        h.startSequence()
                .thenExecuteAfter(3, () -> {
                    Station s = station(h, WEST);
                    if (!s.mark().equals("COLUMN*2")) h.fail("first mark " + s.mark());
                    first[0] = s.wid();
                    a.addAll(GlyphService.inscribe(h.getLevel(), plane(h, WEST),
                            new GlyphSketch(uv(h, WEST), 2.0).signs(SignType.COLUMN, 1, Orientation.OUTWARD, 0.4, 0).strokes, InkType.CONJURING, witch));
                })
                .thenExecuteAfter(3, () -> {
                    Station s = station(h, WEST);
                    if (!s.mark().equals("COLUMN*3")) h.fail("a third Column renames the window, got " + s.mark());
                    var net = WindowwayNetwork.get(h.getLevel().getServer());
                    if (net.station(first[0]) != null) h.fail("the old name should be forgotten");
                    if (net.sill("COLUMN*2") != null) h.fail("nothing bears the old mark any more");
                    wipe(h, a);
                    leave(h, witch);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "ww_nether", required = false)
    public static void acrossDimensions(GameTestHelper h) {
        room(h);
        fresh(h, "LEVITATION*2");
        ServerLevel level = h.getLevel();
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        if (nether == null) { h.succeed(); return; }
        ServerPlayer owner = witch(h), traveller = witch(h), late = witch(h);
        BlockPos o = new BlockPos(h.absolutePos(BlockPos.ZERO).getX() / 8, 64, h.absolutePos(BlockPos.ZERO).getZ() / 8);
        int cx = (o.getX() + 9) >> 4, cz = (o.getZ() + 5) >> 4;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) nether.setChunkForced(cx + dx, cz + dz, true);
        for (int x = 2; x <= 11; x++) for (int y = -1; y <= 6; y++) for (int z = 0; z <= 10; z++) nether.setBlockAndUpdate(o.offset(x, y, z), Blocks.STONE.defaultBlockState());
        for (int x = 3; x <= 10; x++) for (int y = 0; y <= 5; y++) for (int z = 1; z <= 9; z++) {
            boolean wall = x == 9 && y >= 1 && y <= 4 && z >= 2 && z <= 8;
            nether.setBlockAndUpdate(o.offset(x, y, z), y == 0 || wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        BlockPos farBlock = o.offset(9, 2, 5);
        SurfacePlane farPlane = SurfacePlane.of(farBlock, Direction.WEST);
        Vec3 farCenter = Vec3.atCenterOf(farBlock).add(-0.5, 0, 0);
        var a = GlyphService.inscribe(nether, farPlane, sketch(farPlane.toUV(farCenter), 2.0, 1.1, 0.4, LIFTS).strokes, InkType.CONJURING, owner);
        List<InkStroke> b = new ArrayList<>();
        var rules = level.getServer().getGameRules();
        h.startSequence()
                .thenWaitUntil(() -> {
                    var s = GlyphService.activeSpells(nether).stream().filter(x -> farPlane.equals(x.plane)).findFirst().orElse(null);
                    if (s == null || WindowwayService.stationOf(nether, s) == null) h.fail("the Sill in the Nether should open");
                })
                .thenExecute(() -> {
                    var s = GlyphService.activeSpells(nether).stream().filter(x -> farPlane.equals(x.plane)).findFirst().orElseThrow();
                    WindowwayService.discover(level.getServer(), traveller.getUUID(), WindowwayService.stationOf(nether, s).wid());
                    b.addAll(window(h, WEST, traveller, LIFTS));
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 2, () -> {
                    stepInto(h, traveller, WEST);
                    if (traveller.level() != nether || traveller.position().add(0, 0.9, 0).distanceTo(farCenter) > 2.6)
                        h.fail("she should come out in the Nether, she is in " + traveller.level().dimension().location() + "; here " + station(h, WEST)
                                + " link " + frame(h, WEST).link() + "; there " + GlyphService.activeSpells(nether).stream().filter(x -> farPlane.equals(x.plane))
                                .map(x -> String.valueOf(WindowwayService.stationOf(nether, x))).toList());
                    if (WindowwayService.acrossDimensions != null) rules.getRule(WindowwayService.acrossDimensions).set(false, level.getServer());
                    stepInto(h, late, WEST);
                    if (late.level() != level) h.fail("with windows kept to their own world, no one crosses to another");
                })
                .thenExecute(() -> {
                    if (WindowwayService.acrossDimensions != null) rules.getRule(WindowwayService.acrossDimensions).set(true, level.getServer());
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) nether.setChunkForced(cx + dx, cz + dz, false);
                    GlyphService.eraseStrokes(nether, a.stream().map(InkStroke::id).toList());
                    wipe(h, b);
                    leave(h, owner, traveller, late);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "ww_cap")
    public static void capEnforced(GameTestHelper h) {
        room(h);
        fresh(h, "EYE*2");
        ServerLevel level = h.getLevel();
        ServerPlayer witch = witch(h);
        var net = WindowwayNetwork.get(level.getServer());
        List<UUID> made = new ArrayList<>();
        List<InkStroke> a = new ArrayList<>();
        h.startSequence()
                .thenWaitUntil(() -> { long t = level.getGameTime() % 100; if (t < 45 || t > 80) h.fail("waiting for a quiet moment between sweeps"); })
                .thenExecute(() -> {
                    while (net.ownedBy(witch.getUUID()) < WindowwayNetwork.PER_OWNER) {
                        UUID w = UUID.randomUUID();
                        if (net.seal(w, Level.OVERWORLD, Set.of(), Vec3.ZERO, new Vec3(0, 1, 0), new Vec3(1, 0, 0), 1, witch.getUUID(),
                                "CAPTEST" + made.size(), false, false) == null) h.fail("the cap came early, at " + made.size());
                        made.add(w);
                    }
                    a.addAll(window(h, WEST, witch, EYES));
                })
                .thenExecuteAfter(3, () -> {
                    ActiveSpell s = spell(h, WEST);
                    PortalFrame f = s == null ? null : WindowwayService.frameOf(s);
                    if (f == null) h.fail("a window over the cap still shows its rim");
                    if (WindowwayService.stationOf(level, s) != null) h.fail("a window over the cap must not join the network");
                    if (f.link() >= 0 || (f.id() & WindowwayService.TRANSIENT) == 0) h.fail("a window over the cap stays quiet");
                    for (UUID w : made) net.forget(w);
                    wipe(h, a);
                    leave(h, witch);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "ww_bare")
    public static void bareWindowsStillPair(GameTestHelper h) {
        room(h);
        fresh(h, "RADIAL*2");
        ServerPlayer witch = witch(h);
        var a = GlyphService.inscribe(h.getLevel(), plane(h, WEST), new GlyphSketch(uv(h, WEST), 2.0).ring(0, 362).centerSign(SignType.WINDOW, 1.1).strokes, witch);
        var c = window(h, NORTH, witch, RADIALS);
        var b = GlyphService.inscribe(h.getLevel(), plane(h, EAST), new GlyphSketch(uv(h, EAST), 2.0).ring(0, 362).centerSign(SignType.WINDOW, 1.1).strokes, witch);
        h.startSequence()
                .thenExecuteAfter(3, () -> {
                    if (frame(h, WEST).link() != frame(h, EAST).id() || frame(h, EAST).link() != frame(h, WEST).id()) h.fail("two plain windows should still pair");
                    if (WindowwayService.stationOf(h.getLevel(), spell(h, WEST)) != null) h.fail("a plain window is no station");
                    if (frame(h, NORTH).link() >= 0) h.fail("a marked window must not pair with plain ones");
                    stepInto(h, witch, WEST, WindowwayService.ANY_LINK);
                    if (!before(h, witch, EAST)) h.fail("a plain pair carries her across at once, as always");
                    wipe(h, a, b, c);
                    leave(h, witch);
                })
                .thenSucceed();
    }

    /** How far a player's middle is in front of a wall's window, negative behind it. */
    private static double sideOf(GameTestHelper h, ServerPlayer p, Spot s) {
        Vec3 c = h.absoluteVec(s.center());
        return PortalFrame.of(0, c, new Vec3(0, 1, 0), Vec3.atLowerCornerOf(s.face().getNormal()), 2, -1).side(p.position().add(0, 0.9, 0));
    }

    /** A window that turns her away is a wall from the front: walking into it for two seconds never gets her behind it. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "ww_wall")
    public static void refusingWindowIsAWallFromTheFront(GameTestHelper h) {
        room(h);
        fresh(h, "EYE*2");
        ServerPlayer owner = witch(h), stranger = witch(h);
        var a = litQuietly(h, WEST, owner.getUUID(), InkType.BLOOD, EYES);
        List<InkStroke> b = new ArrayList<>();
        Vec3 push = Vec3.atLowerCornerOf(EAST.face().getOpposite().getNormal()).scale(0.3);
        h.startSequence()
                .thenWaitUntil(() -> station(h, WEST))
                .thenExecute(() -> {
                    meet(h, stranger, WEST);
                    b.addAll(window(h, EAST, stranger, EYES));
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 3, () -> {
                    if (frame(h, EAST).link() != frame(h, WEST).id()) h.fail("the stranger's window should look at the blood Sill");
                    standAt(h, stranger, EAST.front(), EAST.yaw());
                })
                .thenExecuteFor(40, () -> {
                    if (sideOf(h, stranger, EAST) < 0) h.fail("turned away, she got behind the window: "
                            + stranger.position().subtract(h.absoluteVec(Vec3.ZERO)));
                    if (!h.getLevel().noCollision(stranger, stranger.getBoundingBox())) h.fail("she was set down inside the wall");
                    if (before(h, stranger, WEST)) h.fail("a blood Sill takes in no one but its witch");
                    stranger.move(MoverType.SELF, push);
                })
                .thenExecute(() -> {
                    wipe(h, a, b);
                    leave(h, owner, stranger);
                })
                .thenSucceed();
    }

    /** A crossing said to start behind the wall is no crossing: not through a window that would take her, not out of one that would not. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "ww_forged")
    public static void forgedCrossFromBehindIgnored(GameTestHelper h) {
        room(h);
        fresh(h, "EYE*2");
        ServerPlayer owner = witch(h), stranger = witch(h);
        var a = litQuietly(h, WEST, owner.getUUID(), InkType.BLOOD, EYES);
        List<InkStroke> b = new ArrayList<>();
        Vec3 behind = new Vec3(10.5, 1, 5.5), inTheHole = new Vec3(9.9, 1, 5.5);
        h.startSequence()
                .thenWaitUntil(() -> station(h, WEST))
                .thenExecute(() -> {
                    meet(h, stranger, WEST);
                    b.addAll(window(h, EAST, stranger, EYES));
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 3, () -> {
                    standAt(h, owner, behind, EAST.yaw());
                    standAt(h, stranger, behind, EAST.yaw());
                })
                .thenExecuteAfter(2, () -> {
                    long link = frame(h, EAST).link(), id = frame(h, EAST).id();
                    for (ServerPlayer p : List.of(owner, stranger)) {
                        WindowwayService.handleCross(p, id, link);
                        if (sideOf(h, p, EAST) > -1.4) h.fail("from behind the wall no one is taken or set out in front: "
                                + p.position().subtract(h.absoluteVec(Vec3.ZERO)));
                        standAt(h, p, inTheHole, EAST.yaw());
                        WindowwayService.handleCross(p, id, link);
                        if (sideOf(h, p, EAST) > -0.8) h.fail("from the back of the hole no one is taken or set out in front: "
                                + p.position().subtract(h.absoluteVec(Vec3.ZERO)));
                        standAt(h, p, behind, EAST.yaw());
                    }
                })
                .thenExecuteAfter(2, () -> {
                    // and the window still works from the front, for the witch whose Sill it is
                    stepInto(h, owner, EAST);
                    if (!before(h, owner, WEST)) h.fail("from the front its witch goes home as before");
                    wipe(h, a, b);
                    leave(h, owner, stranger);
                })
                .thenSucceed();
    }

    /** Walking into a window that takes her, as the server sees it with no word from her client, still carries her across. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "ww_walk")
    public static void walkingIntoAnOpenWindowStillCrosses(GameTestHelper h) {
        room(h);
        fresh(h, "LEVITATION*2");
        ServerPlayer owner = witch(h), visitor = witch(h);
        var a = window(h, WEST, owner, LIFTS);
        List<InkStroke> b = new ArrayList<>();
        Vec3 push = Vec3.atLowerCornerOf(EAST.face().getOpposite().getNormal()).scale(0.3);
        boolean[] over = {false};
        String[] wrong = {null};
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    meet(h, visitor, WEST);
                    b.addAll(window(h, EAST, visitor, LIFTS));
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 3, () -> standAt(h, visitor, EAST.front(), EAST.yaw()))
                .thenWaitUntil(() -> {
                    if (!over[0] && before(h, visitor, WEST)) over[0] = true;
                    if (over[0] || wrong[0] != null) return;
                    if (sideOf(h, visitor, EAST) < -0.6) wrong[0] = "she walked into the wall instead of through the window: "
                            + visitor.position().subtract(h.absoluteVec(Vec3.ZERO));
                    visitor.move(MoverType.SELF, push);
                    h.fail("not across yet");
                })
                .thenExecute(() -> {
                    if (wrong[0] != null) h.fail(wrong[0]);
                    if (frame(h, WEST).link() != frame(h, EAST).id()) h.fail("the Sill should look back toward the visitor for a while");
                    wipe(h, a, b);
                    leave(h, owner, visitor);
                })
                .thenSucceed();
    }

    /** The floor of the room, as a spot to draw a window on: whoever comes to it comes down from above. */
    private static final Spot FLOOR = new Spot(new BlockPos(5, 0, 5), Direction.UP, new Vec3(0, 0.5, 0), new Vec3(5.5, 1, 5.5), 0, new Vec3(5.5, 1, 5.5));

    /** A window on the floor that turns her away holds her up, all of her: her feet are never left down in the hole. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "ww_floor_wall")
    public static void refusingFloorWindowHoldsAllOfHer(GameTestHelper h) {
        room(h);
        fresh(h, "EYE*2");
        ServerPlayer owner = witch(h), stranger = witch(h);
        var a = litQuietly(h, WEST, owner.getUUID(), InkType.BLOOD, EYES);
        List<InkStroke> b = new ArrayList<>();
        double plane = h.absoluteVec(FLOOR.center()).y - 0.05;
        boolean[] turned = {false};
        h.startSequence()
                .thenWaitUntil(() -> station(h, WEST))
                .thenExecute(() -> {
                    meet(h, stranger, WEST);
                    b.addAll(window(h, FLOOR, stranger, EYES));
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 3, () -> {
                    if (frame(h, FLOOR).link() != frame(h, WEST).id()) h.fail("the stranger's floor window should look at the blood Sill");
                    standAt(h, stranger, FLOOR.stand().add(0, 0.5, 0), 0);
                })
                .thenExecuteFor(30, () -> {
                    double feet = stranger.getY() - plane;
                    if (feet < -0.01) h.fail("turned away, her feet were left down in the floor window: " + feet);
                    if (before(h, stranger, WEST)) h.fail("a blood Sill takes in no one but its witch");
                    if (feet < 0.5) turned[0] = true;
                    // sinking into it a third of a block a tick, as she would fall
                    stranger.setPos(stranger.getX(), stranger.getY() - 0.3, stranger.getZ());
                })
                .thenExecute(() -> {
                    if (!turned[0]) h.fail("she should have come down onto the floor window");
                    wipe(h, a, b);
                    leave(h, owner, stranger);
                })
                .thenSucceed();
    }

    /** Coming at a window that turns her away faster than a block a tick, flying or falling, still finds it a wall. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "ww_fast_wall")
    public static void refusingWindowStopsTheFast(GameTestHelper h) {
        room(h);
        fresh(h, "EYE*2");
        ServerPlayer owner = witch(h), stranger = witch(h);
        var a = litQuietly(h, WEST, owner.getUUID(), InkType.BLOOD, EYES);
        List<InkStroke> b = new ArrayList<>();
        int[] step = {0};
        h.startSequence()
                .thenWaitUntil(() -> station(h, WEST))
                .thenExecute(() -> {
                    meet(h, stranger, WEST);
                    b.addAll(window(h, EAST, stranger, EYES));
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 3, () -> standAt(h, stranger, new Vec3(6.5, 1, 5.5), EAST.yaw()))
                .thenExecuteFor(12, () -> {
                    if (sideOf(h, stranger, EAST) < 0) h.fail("coming in fast, she got behind the window: "
                            + stranger.position().subtract(h.absoluteVec(Vec3.ZERO)));
                    if (!h.getLevel().noCollision(stranger, stranger.getBoundingBox())) h.fail("she was set down inside the wall");
                    if (before(h, stranger, WEST)) h.fail("a blood Sill takes in no one but its witch");
                    // every other tick back to her start, then one leap of 2.6 or 4.4 blocks through the hole
                    int k = step[0]++;
                    if (k % 2 == 0) standAt(h, stranger, new Vec3(k % 4 == 0 ? 6.5 : 5.6, 1, 5.5), EAST.yaw());
                    else stranger.setPos(stranger.getX() + (k % 4 == 1 ? 2.6 : 4.4), stranger.getY(), stranger.getZ());
                })
                .thenExecute(() -> {
                    wipe(h, a, b);
                    leave(h, owner, stranger);
                })
                .thenSucceed();
    }

    /** Coming at a window that takes her faster than it can see her in the opening, she is still carried across. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "ww_fast_walk")
    public static void fastIntoAnOpenWindowStillCrosses(GameTestHelper h) {
        room(h);
        fresh(h, "LEVITATION*2");
        ServerPlayer owner = witch(h), visitor = witch(h);
        var a = window(h, WEST, owner, LIFTS);
        List<InkStroke> b = new ArrayList<>();
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    meet(h, visitor, WEST);
                    b.addAll(window(h, EAST, visitor, LIFTS));
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 3, () -> standAt(h, visitor, new Vec3(5.6, 1, 5.5), EAST.yaw()))
                .thenExecuteAfter(1, () -> visitor.setPos(visitor.getX() + 4.4, visitor.getY(), visitor.getZ()))
                .thenExecuteAfter(1, () -> {
                    if (!before(h, visitor, WEST)) h.fail("leaping 4.4 blocks through the window she should come out at the Sill: "
                            + visitor.position().subtract(h.absoluteVec(Vec3.ZERO)));
                    wipe(h, a, b);
                    leave(h, owner, visitor);
                })
                .thenSucceed();
    }

    /** Riding into a window that turns her away, her mount is stopped in front of it: a saddle is no way past it. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "ww_ride_wall")
    public static void refusingWindowStopsHerMount(GameTestHelper h) {
        room(h);
        fresh(h, "EYE*2");
        ServerPlayer owner = witch(h), stranger = witch(h);
        var a = litQuietly(h, WEST, owner.getUUID(), InkType.BLOOD, EYES);
        List<InkStroke> b = new ArrayList<>();
        net.minecraft.world.entity.Entity[] mount = {null};
        h.startSequence()
                .thenWaitUntil(() -> station(h, WEST))
                .thenExecute(() -> {
                    meet(h, stranger, WEST);
                    b.addAll(window(h, EAST, stranger, EYES));
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 3, () -> {
                    var pig = net.minecraft.world.entity.EntityType.PIG.create(h.getLevel());
                    Vec3 at = h.absoluteVec(new Vec3(7.5, 1, 5.5));
                    pig.moveTo(at.x, at.y, at.z, EAST.yaw(), 0);
                    pig.setNoAi(true);
                    h.getLevel().addFreshEntity(pig);
                    stranger.startRiding(pig, true);
                    mount[0] = pig;
                })
                .thenExecuteFor(30, () -> {
                    var pig = mount[0];
                    Vec3 rel = pig.position().subtract(h.absoluteVec(Vec3.ZERO));
                    // the pig's middle may not pass the plane at x 8.95
                    if (rel.x > 8.95) h.fail("ridden, she got behind the window: " + rel);
                    if (before(h, stranger, WEST)) h.fail("a blood Sill takes in no one but its witch");
                    pig.setPos(pig.getX() + 0.3, pig.getY(), pig.getZ());
                })
                .thenExecute(() -> {
                    stranger.stopRiding();
                    mount[0].discard();
                    wipe(h, a, b);
                    leave(h, owner, stranger);
                })
                .thenSucceed();
    }

    /** Behind the east wall, in the one block of room before the barrier at x 11; the back of the hole is at x 10. */
    private static final Vec3 BEHIND_EAST = new Vec3(10.6, 1, 5.5);

    /**
     * A window that turns her away is a wall from behind as well: walking into the back of the hole for two seconds
     * never gets her into it, let alone out in front, and it still takes its own witch from the front.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "ww_back_wall")
    public static void refusingWindowIsAWallFromBehind(GameTestHelper h) {
        room(h);
        fresh(h, "EYE*2");
        ServerPlayer owner = witch(h), stranger = witch(h);
        var a = litQuietly(h, WEST, owner.getUUID(), InkType.BLOOD, EYES);
        List<InkStroke> b = new ArrayList<>();
        Vec3 push = Vec3.atLowerCornerOf(EAST.face().getNormal()).scale(0.3);
        double hole = h.absoluteVec(new Vec3(10, 0, 0)).x;
        boolean[] met = {false};
        h.startSequence()
                .thenWaitUntil(() -> station(h, WEST))
                .thenExecute(() -> {
                    meet(h, stranger, WEST);
                    b.addAll(window(h, EAST, stranger, EYES));
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 3, () -> {
                    if (frame(h, EAST).link() != frame(h, WEST).id()) h.fail("the stranger's window should look at the blood Sill");
                    if (!h.getLevel().getBlockState(h.absolutePos(new BlockPos(9, 1, 5))).is(ModBlocks.WINDOWWAY_GAP.get()))
                        h.fail("the window should have opened a hole through the wall");
                    standAt(h, stranger, BEHIND_EAST, -EAST.yaw());
                })
                .thenExecuteFor(40, () -> {
                    double into = hole - stranger.getBoundingBox().minX;
                    if (into > 1e-3) h.fail("turned away, she got into the hole from behind, " + into + " deep: "
                            + stranger.position().subtract(h.absoluteVec(Vec3.ZERO)));
                    if (sideOf(h, stranger, EAST) > -1) h.fail("turned away, she came through from behind: "
                            + stranger.position().subtract(h.absoluteVec(Vec3.ZERO)));
                    if (!h.getLevel().noCollision(stranger, stranger.getBoundingBox())) h.fail("she was set down inside a block");
                    if (before(h, stranger, WEST)) h.fail("a blood Sill takes in no one but its witch");
                    if (into > -0.2) met[0] = true;
                    stranger.move(MoverType.SELF, push);
                })
                .thenExecute(() -> {
                    if (!met[0]) h.fail("she should have come right up to the back of the hole");
                    // and from the front the window still takes the witch whose Sill it looks at
                    stepInto(h, owner, EAST);
                    if (!before(h, owner, WEST)) h.fail("from the front its witch goes home as before");
                    wipe(h, a, b);
                    leave(h, owner, stranger);
                })
                .thenSucceed();
    }

    /** One the window would take is not walled off from behind: she walks through the hole as she always could. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "ww_back_open")
    public static void allowedWalkerPassesTheHoleFromBehind(GameTestHelper h) {
        room(h);
        fresh(h, "EYE*2");
        ServerPlayer owner = witch(h), stranger = witch(h);
        var a = litQuietly(h, WEST, owner.getUUID(), InkType.BLOOD, EYES);
        List<InkStroke> b = new ArrayList<>();
        Vec3 push = Vec3.atLowerCornerOf(EAST.face().getNormal()).scale(0.3);
        boolean[] out = {false};
        h.startSequence()
                .thenWaitUntil(() -> station(h, WEST))
                .thenExecute(() -> {
                    meet(h, stranger, WEST);
                    b.addAll(window(h, EAST, stranger, EYES));
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 3, () -> standAt(h, owner, BEHIND_EAST, -EAST.yaw()))
                .thenWaitUntil(() -> {
                    if (!out[0] && sideOf(h, owner, EAST) > 0.4) out[0] = true;
                    if (out[0]) return;
                    owner.move(MoverType.SELF, push);
                    h.fail("not through the hole yet");
                })
                .thenExecute(() -> {
                    if (before(h, owner, WEST)) h.fail("walking out of the hole from behind is no crossing");
                    wipe(h, a, b);
                    leave(h, owner, stranger);
                })
                .thenSucceed();
    }

    /** Something thrown at the back of the hole of a window that will not carry it glances off there, never comes out in front. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "ww_back_throw")
    public static void thrownAtTheBackGlancesOff(GameTestHelper h) {
        room(h);
        fresh(h, "EYE*2");
        ServerPlayer owner = witch(h), stranger = witch(h);
        var a = litQuietly(h, WEST, owner.getUUID(), InkType.BLOOD, EYES);
        List<InkStroke> b = new ArrayList<>();
        net.minecraft.world.entity.Entity[] ball = {null};
        boolean[] glanced = {false};
        double front = h.absoluteVec(new Vec3(9.5, 0, 0)).x;
        h.startSequence()
                .thenWaitUntil(() -> station(h, WEST))
                .thenExecute(() -> {
                    meet(h, stranger, WEST);
                    b.addAll(window(h, EAST, stranger, EYES));
                })
                .thenExecuteAfter(WindowwayService.SETTLE_TICKS + 3, () -> {
                    var snow = new net.minecraft.world.entity.projectile.Snowball(net.minecraft.world.entity.EntityType.SNOWBALL, h.getLevel());
                    Vec3 at = h.absoluteVec(new Vec3(10.8, 2.4, 5.5));
                    snow.setPos(at.x, at.y, at.z);
                    snow.setDeltaMovement(-1, 0, 0);
                    h.getLevel().addFreshEntity(snow);
                    ball[0] = snow;
                })
                .thenExecuteFor(12, () -> {
                    var snow = ball[0];
                    if (snow.isAlive() && snow.getX() < front) h.fail("thrown from behind, it came through the hole: "
                            + snow.position().subtract(h.absoluteVec(Vec3.ZERO)));
                    if (snow.isAlive() && snow.getDeltaMovement().x > 0) glanced[0] = true;
                })
                .thenExecute(() -> {
                    if (!glanced[0]) h.fail("thrown at the back of the hole, it should have glanced off");
                    if (ball[0].isAlive()) ball[0].discard();
                    wipe(h, a, b);
                    leave(h, owner, stranger);
                })
                .thenSucceed();
    }
}
