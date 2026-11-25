package com.fimkov.witchsatelier.gametest;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.entity.StrangerEntity;
import com.fimkov.witchsatelier.entity.Taboo;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.model.Orientation;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import com.fimkov.witchsatelier.glyph.page.PageService;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.storage.GlyphLevelData;
import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import com.fimkov.witchsatelier.glyph.surface.StrokeState;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.spell.ActiveSpell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@GameTestHolder(WitchsAtelier.MODID)
@PrefixGameTestTemplate(false)
public final class RegressionGameTests {
    private RegressionGameTests() {}

    private static void floor(GameTestHelper h) {
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static SurfacePlane plane(GameTestHelper h) {
        return SurfacePlane.of(h.absolutePos(new BlockPos(5, 0, 5)), Direction.UP);
    }

    private static Pt centre(GameTestHelper h, SurfacePlane plane) {
        BlockPos abs = h.absolutePos(new BlockPos(5, 0, 5));
        return plane.toUV(Vec3.atCenterOf(abs).add(0, 0.5, 0));
    }

    private static List<ActiveSpell> spellsOn(GameTestHelper h, SurfacePlane plane) {
        Vec3 mid = h.absoluteVec(new Vec3(5.5, 1, 5.5));
        return GlyphService.activeSpells(h.getLevel()).stream()
                .filter(s -> plane.equals(s.plane) && s.program.origin().distanceTo(mid) < 4).toList();
    }

    private static List<double[]> risingEarth(Pt c) {
        return new GlyphSketch(c, 1.6).ring(0, 362).sigil(Sigil.EARTH, 0.9)
                .signs(SignType.LEVITATION, 4, Orientation.OUTWARD, 0.45, 45).strokes;
    }

    private static int risen(GameTestHelper h, net.minecraft.world.level.block.Block block) {
        int n = 0;
        for (int x = 2; x <= 8; x++) for (int y = 1; y <= 12; y++) for (int z = 2; z <= 8; z++) {
            var st = h.getBlockState(new BlockPos(x, y, z));
            if (st.is(block)) n++;
        }
        return n;
    }

    /** Bows and crossbows only take ammunition tagged as arrows; glyph arrows used to work from dispensers only. */
    @GameTest(template = "empty")
    public static void bowsAndCrossbowsTakeGlyphArrows(GameTestHelper h) {
        var arrows = new net.minecraft.world.item.ItemStack(com.fimkov.witchsatelier.registry.ModItems.GLYPH_ARROW.get(), 8);
        h.assertTrue(arrows.is(net.minecraft.tags.ItemTags.ARROWS), "glyph arrows are not tagged as arrows");
        for (var weapon : List.of(net.minecraft.world.item.Items.BOW, net.minecraft.world.item.Items.CROSSBOW)) {
            var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.getInventory().add(arrows.copy());
            var ammo = player.getProjectile(new net.minecraft.world.item.ItemStack(weapon));
            h.assertTrue(ammo.is(com.fimkov.witchsatelier.registry.ModItems.GLYPH_ARROW.get()),
                    "a " + weapon + " does not take glyph arrows, it took " + ammo);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void risingEarthDoesNotCopyPreciousBlocks(GameTestHelper h) {
        floor(h);
        h.setBlock(5, 0, 5, Blocks.DIAMOND_BLOCK);
        SurfacePlane plane = plane(h);
        GlyphService.inscribe(h.getLevel(), plane, risingEarth(centre(h, plane)), null);
        h.startSequence()
                .thenWaitUntil(() -> { if (risen(h, Blocks.PACKED_MUD) < 3) h.fail("the earth should rise as plain earth"); })
                .thenExecute(() -> {
                    if (risen(h, Blocks.DIAMOND_BLOCK) > 0) h.fail("rising earth must not copy a diamond block");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void anExplosionThatBreaksNothingLeavesTheInk(GameTestHelper h) {
        floor(h);
        SurfacePlane plane = plane(h);
        GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(centre(h, plane), 1.6).ring(0, 362).sigil(Sigil.FIRE, 1.0)
                .signs(SignType.RADIAL, 4, Orientation.OUTWARD, 0.5, 45).strokes, null);
        h.startSequence()
                .thenExecute(() -> {
                    if (spellsOn(h, plane).size() != 1) h.fail("the glyph should wake");
                    Vec3 at = h.absoluteVec(new Vec3(5.5, 1.2, 5.5));
                    h.getLevel().explode(null, at.x, at.y, at.z, 2.5f, Level.ExplosionInteraction.NONE);
                })
                .thenExecuteAfter(2, () -> {
                    if (spellsOn(h, plane).size() != 1) h.fail("an explosion that breaks no blocks must not cut the ring");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void litInkIsSavedLit(GameTestHelper h) {
        InkStroke s = InkStroke.of(7, new double[]{0, 0, 1, 1}, InkType.CONJURING, null, StrokeState.ACTIVE, 0xFF8800);
        InkStroke back = InkStroke.load(s.save());
        if (back.state() != StrokeState.ACTIVE) h.fail("lit ink should load lit, so its glyph can wake again: " + back.state());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void thePactFollowsAPlayerPastDeath(GameTestHelper h) {
        var before = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var after = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        Taboo.add(before, 50);
        before.getPersistentData().putBoolean(StrangerEntity.GIFT_TAG, true);
        Taboo.onClone(new PlayerEvent.Clone(after, before, true));
        if (Taboo.of(after) != 50) h.fail("taboo should survive respawning, got " + Taboo.of(after));
        if (!after.getPersistentData().getBoolean(StrangerEntity.GIFT_TAG)) h.fail("the Stranger's gift should be remembered");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aFullPageTakesNoMoreInk(GameTestHelper h) {
        List<GlyphPages.PageStroke> dots = new ArrayList<>();
        for (int i = 0; i < PageService.MAX_PAGE_STROKES; i++) dots.add(new GlyphPages.PageStroke(new float[]{0, 0}, InkType.CONJURING));
        if (!PageService.pageFull(new GlyphPages.Page(List.copyOf(dots)), 1)) h.fail("a page at the stroke limit should count as full");
        if (PageService.pageFull(GlyphPages.Page.EMPTY, 1)) h.fail("an empty page is not full");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "restart")
    public static void stoppingTheServerTakesTemporaryBlocksBackAndRelightsAfter(GameTestHelper h) {
        floor(h);
        SurfacePlane plane = plane(h);
        GlyphService.inscribe(h.getLevel(), plane, risingEarth(centre(h, plane)), null);
        h.startSequence()
                .thenWaitUntil(() -> { if (risen(h, Blocks.STONE) < 3) h.fail("the earth should rise"); })
                .thenExecute(() -> {
                    int before = spellsOn(h, plane).size(), all = GlyphService.activeSpells(h.getLevel()).size();
                    GlyphService.shutdown(h.getLevel());
                    if (risen(h, Blocks.STONE) > 0) h.fail("stopping must take the risen earth back before the world is saved, left " + risen(h, Blocks.STONE)
                            + " (spells here " + before + ", in level " + all + ", after " + GlyphService.activeSpells(h.getLevel()).size() + ")");
                    var data = GlyphLevelData.get(h.getLevel());
                    boolean lit = data.near(plane, centre(h, plane).x(), centre(h, plane).y(), 1).stream().anyMatch(s -> s.state() == StrokeState.ACTIVE);
                    if (!lit) h.fail("the ink should stay lit so the glyph wakes after a restart");
                    GlyphService.rescanLitGlyphs(h.getLevel());
                })
                .thenWaitUntil(() -> {
                    var s = spellsOn(h, plane);
                    if (s.size() != 1) h.fail("the lit glyph should wake again, got " + s.size());
                    if (!s.get(0).quiet) h.fail("a glyph woken after a restart should not misfire again");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 20000, batch = "async_reading")
    public static void glyphsAreReadOffTheServerThread(GameTestHelper h) {
        floor(h);
        SurfacePlane plane = plane(h);
        GlyphService.readOffThread = true;
        List<InkStroke> ink = GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(centre(h, plane), 1.6).ring(0, 362)
                .sigil(Sigil.FIRE, 1.0).signs(SignType.RADIAL, 4, Orientation.OUTWARD, 0.5, 45).strokes, null);
        long[] id = {-1}, probe = {-1};
        h.startSequence()
                .thenWaitUntil(() -> {
                    var s = spellsOn(h, plane);
                    if (s.size() != 1 || GlyphService.readsPending()) h.fail("the glyph should wake once it is read, spells: " + s.size());
                    id[0] = s.get(0).id;
                    var sign = ink.get(ink.size() - 1);
                    boolean[] cut = new boolean[sign.size()];
                    cut[sign.size() / 2] = true;
                    GlyphService.applyCuts(h.getLevel(), Map.of(sign.id(), cut));
                })
                .thenExecuteAfter(2, () -> {})
                .thenWaitUntil(() -> { if (GlyphService.readsPending()) h.fail("still reading"); })
                .thenExecute(() -> {
                    var s = spellsOn(h, plane);
                    if (s.size() != 1 || s.get(0).id != id[0]) h.fail("a nick in a sign should not stop the spell");
                    var ring = GlyphLevelData.get(h.getLevel()).get(ink.get(0).id());
                    if (ring == null) h.fail("the ring is gone");
                    boolean[] gap = new boolean[ring.size()];
                    for (int i = ring.size() / 3; i < ring.size() / 2; i++) gap[i] = true;
                    GlyphService.applyCuts(h.getLevel(), Map.of(ring.id(), gap));
                    probe[0] = spellsOn(h, plane).isEmpty() ? -1 : spellsOn(h, plane).get(0).strokes.stream().mapToLong(Long::longValue).max().orElse(-1);
                })
                .thenWaitUntil(() -> {
                    var left = spellsOn(h, plane);
                    if (!left.isEmpty()) h.fail("with its ring broken open the spell should stop: " + left.stream()
                            .map(x -> x.id + (x.id == id[0] ? "(same)" : "(new)") + " r=" + String.format("%.2f", x.ringRadius) + " " + x.program.summary()
                                    + " strokes=" + x.strokes + " pending=" + GlyphService.readsPending()
                                    + " reads as: " + GlyphService.describeAround(h.getLevel(), plane, probe[0])).toList());
                })
                .thenExecute(() -> GlyphService.readOffThread = false)
                .thenSucceed();
    }
}
