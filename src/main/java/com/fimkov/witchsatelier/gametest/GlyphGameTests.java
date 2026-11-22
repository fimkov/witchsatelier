package com.fimkov.witchsatelier.gametest;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.model.Orientation;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.spell.ActiveSpell;
import com.fimkov.witchsatelier.spell.SpellProgram;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** In-world checks of the whole chain: ink → reading → spell → effect → ring broken. */
@GameTestHolder(WitchsAtelier.MODID)
@PrefixGameTestTemplate(false)
public final class GlyphGameTests {

    private static void floor(GameTestHelper h) {
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    /** Like the helper's mock player, but one who is not in creative (the Pact leaves creative players alone). */
    @SuppressWarnings("removal")
    private static ServerPlayer survivor(GameTestHelper h) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-survivor"), false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() { return false; }

            @Override
            public boolean isCreative() { return false; }
        };
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return p;
    }

    /** Spells of this test only: tests run side by side and may share a plane. */
    private static List<ActiveSpell> spellsOn(GameTestHelper h, SurfacePlane plane) {
        Vec3 mid = h.absoluteVec(new Vec3(5.5, 1, 5.5));
        return GlyphService.activeSpells(h.getLevel()).stream()
                .filter(s -> plane.equals(s.plane) && s.program.origin().distanceTo(mid) < 4).toList();
    }

    /** Glyph on the top face of the block at {@code rel}: returns the plane and the face centre in plane coordinates. */
    private static Object[] top(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        SurfacePlane plane = SurfacePlane.of(abs, Direction.UP);
        Pt c = plane.toUV(Vec3.atCenterOf(abs).add(0, 0.5, 0));
        return new Object[]{plane, c};
    }

    /** A Raincleaver planted in a pool keeps a dry bubble round it; pulled out, the water closes in again. */
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void plantedRaincleaverKeepsTheWaterBack(GameTestHelper h) {
        floor(h);
        for (int x = 1; x <= 9; x++) for (int z = 1; z <= 9; z++) for (int y = 1; y <= 3; y++) h.setBlock(x, y, z, Blocks.WATER);
        BlockPos at = new BlockPos(5, 1, 5);
        ItemStack sword = new ItemStack(com.fimkov.witchsatelier.registry.ModItems.RAINCLEAVER.get());
        if (!com.fimkov.witchsatelier.block.PlantedRaincleaverBlock.plant(h.getLevel(), h.absolutePos(at), sword)) h.fail("it should plant in water");
        h.startSequence()
                .thenExecuteAfter(10, () -> {
                    if (!h.getBlockState(new BlockPos(7, 1, 5)).isAir()) h.fail("two blocks off it should be dry");
                    if (!h.getBlockState(new BlockPos(5, 2, 5)).isAir()) h.fail("above it should be dry");
                })
                .thenExecute(() -> h.getLevel().removeBlock(h.absolutePos(at), false))
                .thenExecuteAfter(40, () -> {
                    if (h.getBlockState(new BlockPos(7, 1, 5)).getFluidState().isEmpty()) h.fail("the water should close in again");
                })
                .thenSucceed();
    }

    /** Warming Hearth: a fire tamed by Radial signs thaws the snow and ice around it and sets nothing alight. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void warmingHearthThawsSnowAndIce(GameTestHelper h) {
        floor(h);
        BlockPos[] snow = {new BlockPos(8, 1, 5), new BlockPos(2, 1, 5), new BlockPos(5, 1, 8), new BlockPos(5, 1, 2)};
        for (BlockPos b : snow) h.setBlock(b, Blocks.SNOW_BLOCK);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(c, 1.6).ring(0, 362).sigil(Sigil.FIRE, 1.0)
                .signs(SignType.RADIAL, 4, Orientation.OUTWARD, 0.5, 45).strokes, null);
        h.startSequence()
                .thenExecute(() -> {
                    List<ActiveSpell> s = spellsOn(h, plane);
                    if (s.size() != 1 || !s.get(0).program.gentle()) h.fail("expected a gentle fire, got " + s.stream().map(x -> x.program.summary()).toList());
                })
                .thenWaitUntil(() -> {
                    for (BlockPos b : snow) if (!h.getBlockState(b).isAir()) h.fail("snow at " + b + " not thawed yet");
                })
                .thenExecute(() -> {
                    for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++)
                        if (h.getBlockState(new BlockPos(x, 1, z)).is(Blocks.FIRE)) h.fail("a tamed fire set something alight");
                })
                .thenSucceed();
    }

    /** A hearth glyph walked over: the ink under the feet thins a step at a time while the spell goes on, then the ring parts. */
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void footstepsWearAGlyphAwayLittleByLittle(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        List<com.fimkov.witchsatelier.glyph.surface.InkStroke> ink = GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(c, 1.6).ring(0, 362)
                .sigil(Sigil.FIRE, 1.0).signs(SignType.RADIAL, 4, Orientation.OUTWARD, 0.5, 45).strokes, null);
        var data = com.fimkov.witchsatelier.glyph.storage.GlyphLevelData.get(h.getLevel());
        long[] spell = {-1};
        ServerPlayer walker = h.makeMockServerPlayerInLevel();
        Vec3 onRing = plane.toWorld(c.x() + 1.6, c.y(), 0);
        java.util.function.IntSupplier mostWorn = () -> {
            int most = 0;
            for (var st : ink) {
                byte[] w = data.wear(st.id());
                if (w != null) for (byte b : w) most = Math.max(most, com.fimkov.witchsatelier.glyph.InkWear.amount(b));
            }
            return most;
        };
        Runnable walk = () -> {
            // pacing on the spot over the ring
            walker.setPos(onRing.x, onRing.y, onRing.z);
            walker.walkDist += 0.1f;
            walker.setOnGround(true);
        };
        h.startSequence()
                .thenExecute(() -> {
                    List<ActiveSpell> s = spellsOn(h, plane);
                    if (s.size() != 1) h.fail("expected one running spell, got " + s.size());
                    spell[0] = s.get(0).id;
                })
                .thenWaitUntil(() -> {
                    walk.run();
                    if (mostWorn.getAsInt() < 50) {
                        var sp = spellsOn(h, plane);
                        h.fail("not worn enough yet: " + mostWorn.getAsInt() + " origin " + (sp.isEmpty() ? "none" : sp.get(0).program.origin() + " r " + sp.get(0).program.radius() + " tough " + sp.get(0).toughness + " mend " + sp.get(0).mending + " hand " + sp.get(0).handheld)
                                + " time " + h.getLevel().getGameTime() + " walker " + walker.position() + " ground " + walker.onGround() + " inLevel " + h.getLevel().getEntitiesOfClass(ServerPlayer.class, walker.getBoundingBox().inflate(1)).size());
                    }
                })
                .thenExecute(() -> {
                    // well worn, but not through: the same spell is still running, never restarted
                    List<ActiveSpell> s = spellsOn(h, plane);
                    if (s.size() != 1 || s.get(0).id != spell[0]) h.fail("the spell should keep going while the ink only thins");
                })
                .thenWaitUntil(() -> {
                    walk.run();
                    if (!spellsOn(h, plane).isEmpty()) h.fail("the ring should wear through and the spell stop");
                })
                .thenSucceed();
    }

    /** A nick in one of the signs leaves the ring whole: the spell carries on as it was, not lit afresh. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aNickInASignKeepsTheSpellGoing(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        List<com.fimkov.witchsatelier.glyph.surface.InkStroke> ink = GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(c, 1.6).ring(0, 362)
                .sigil(Sigil.FIRE, 1.0).signs(SignType.RADIAL, 4, Orientation.OUTWARD, 0.5, 45).strokes, null);
        long[] spell = {-1};
        h.startSequence()
                .thenExecute(() -> {
                    List<ActiveSpell> s = spellsOn(h, plane);
                    if (s.size() != 1) h.fail("expected one running spell, got " + s.size());
                    spell[0] = s.get(0).id;
                    var sign = ink.get(ink.size() - 1);
                    boolean[] cut = new boolean[sign.size()];
                    cut[sign.size() / 2] = true;
                    GlyphService.applyCuts(h.getLevel(), java.util.Map.of(sign.id(), cut));
                })
                .thenExecuteAfter(2, () -> {
                    List<ActiveSpell> s = spellsOn(h, plane);
                    if (s.size() != 1 || s.get(0).id != spell[0]) h.fail("the spell should go on, got " + s.stream().map(x -> x.id).toList() + " (was " + spell[0] + ")");
                })
                .thenSucceed();
    }

    /** The apple no book shows: closing its ring wakes no spell, and the glyph is gone (it plays out on clients). */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void anAppleInARingIsGoneWithoutASpell(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        GlyphSketch g = new GlyphSketch(c, 1.6).ring(0, 362).sigil(Sigil.APPLE, 1.0);
        GlyphService.inscribe(h.getLevel(), plane, g.strokes, null);
        var data = com.fimkov.witchsatelier.glyph.storage.GlyphLevelData.get(h.getLevel());
        h.startSequence()
                .thenExecute(() -> {
                    if (!spellsOn(h, plane).isEmpty()) h.fail("an apple wakes no spell");
                    // (tests side by side share the floor's plane: only ink of this one counts)
                    var left = data.near(plane, c.x(), c.y(), 1).stream().filter(st -> st.centroid().dist(c) < 2.5).toList();
                    if (!left.isEmpty()) h.fail("the apple's glyph should be gone, " + left.size() + " lines left");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fireColumnGlyphBurnsAndStopsWhenRingBreaks(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(5, 1, 5));

        GlyphSketch g = new GlyphSketch(c, 1.6).ring(0, 362).sigil(Sigil.FIRE, 1.0)
                .signs(SignType.COLUMN, 4, Orientation.OUTWARD, 0.5, 45);
        GlyphService.inscribe(h.getLevel(), plane, g.strokes, null);

        h.startSequence()
                .thenExecute(() -> {
                    List<ActiveSpell> s = spellsOn(h, plane);
                    if (s.size() != 1) h.fail("expected one running spell, got " + s.size());
                    SpellProgram p = s.get(0).program;
                    if (p.element() != Sigil.FIRE || p.form() != SpellProgram.Form.BEAM) h.fail("read as " + p.summary());
                })
                .thenWaitUntil(() -> { if (!pig.isOnFire()) h.fail("pig not burning yet"); })
                .thenExecute(() -> {
                    // break a block under the ring the way a player would
                    ServerPlayer player = h.makeMockServerPlayerInLevel();
                    player.gameMode.destroyBlock(h.absolutePos(new BlockPos(7, 0, 5)));
                })
                .thenExecuteAfter(2, () -> {
                    if (!spellsOn(h, plane).isEmpty()) h.fail("spell should stop when the ring is broken");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ringWithGapWaitsUntilClosed(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        GlyphSketch g = new GlyphSketch(c, 1.6).ring(20, 340).sigil(Sigil.WIND, 1.0)
                .signs(SignType.COLUMN, 4, Orientation.OUTWARD, 0.5, 45);
        GlyphService.inscribe(h.getLevel(), plane, g.strokes, null);

        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    if (!spellsOn(h, plane).isEmpty()) h.fail("an open ring must not cast");
                    GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(c, 1.6).ring(330, 390).strokes, null);
                })
                .thenExecuteAfter(2, () -> {
                    List<ActiveSpell> s = spellsOn(h, plane);
                    if (s.size() != 1) h.fail("closing the gap should cast, spells: " + s.size());
                    if (s.get(0).program.element() != Sigil.WIND) h.fail("read as " + s.get(0).program.summary());
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void wallBreakerAndIntegration(GameTestHelper h) {
        floor(h);
        for (int x = 2; x <= 8; x++) for (int y = 1; y <= 5; y++) h.setBlock(x, y, 7, Blocks.STONE);
        BlockPos wallRel = new BlockPos(5, 3, 7);
        BlockPos abs = h.absolutePos(wallRel);
        SurfacePlane wall = SurfacePlane.of(abs, Direction.NORTH);
        Pt c = wall.toUV(Vec3.atCenterOf(abs).add(0, 0, -0.5));

        GlyphSketch breaker = new GlyphSketch(c, 1.2).ring(0, 362).sigil(Sigil.EARTH, 0.8)
                .signs(SignType.CRUSH, 4, Orientation.OUTWARD, 0.45, 45);
        GlyphService.inscribe(h.getLevel(), wall, breaker.strokes, null);

        Object[] t = top(h, new BlockPos(5, 0, 4));
        SurfacePlane floorPlane = (SurfacePlane) t[0];
        Pt fc = (Pt) t[1];

        h.startSequence()
                .thenExecuteAfter(3, () -> h.assertBlockPresent(Blocks.AIR, wallRel))
                .thenExecute(() -> {
                    GlyphSketch integration = new GlyphSketch(fc, 1.2).ring(0, 362).sigil(Sigil.EARTH, 0.8)
                            .signs(SignType.CRUSH, 4, Orientation.INWARD, 0.45, 45);
                    GlyphService.inscribe(h.getLevel(), floorPlane, integration.strokes, null);
                })
                .thenExecuteAfter(3, () -> h.assertBlockPresent(Blocks.STONE, wallRel))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void breakingABlockCutsOnlyItsInk(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        // one long straight line across five blocks
        GlyphService.inscribe(h.getLevel(), plane, List.of(new double[]{c.x() - 2.4, c.y(), c.x() - 1.2, c.y(), c.x(), c.y(), c.x() + 1.2, c.y(), c.x() + 2.4, c.y()}), null);
        var data = com.fimkov.witchsatelier.glyph.storage.GlyphLevelData.get(h.getLevel());
        h.startSequence()
                .thenExecute(() -> {
                    ServerPlayer player = h.makeMockServerPlayerInLevel();
                    player.gameMode.destroyBlock(h.absolutePos(new BlockPos(5, 0, 5)));
                })
                .thenExecuteAfter(1, () -> {
                    // only this test's line (other tests draw on the same floor plane)
                    var strokes = data.near(plane, c.x(), c.y(), 1).stream()
                            .filter(st -> Math.abs(st.v(0) - c.y()) < 0.05 && Math.abs(st.u(0) - c.x()) < 3).toList();
                    if (strokes.size() != 2) h.fail("the line should be cut in two, got " + strokes.size() + " pieces");
                    for (var s : strokes) {
                        if (s.touchesSquare(c.x() - 0.45, c.y() - 0.45, c.x() + 0.45, c.y() + 0.45)) h.fail("ink left on the broken block");
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void palmQuireQuickCloseCastsFromTheHand(GameTestHelper h) {
        floor(h);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        var quire = new net.minecraft.world.item.ItemStack(com.fimkov.witchsatelier.registry.ModItems.PALM_QUIRE.get());
        GlyphSketch g = new GlyphSketch(new Pt(0, 0), 0.85).ring(25, 335).sigil(Sigil.FIRE, 0.55)
                .signs(SignType.COLUMN, 4, Orientation.OUTWARD, 0.28, 45);
        var strokes = new java.util.ArrayList<com.fimkov.witchsatelier.glyph.page.GlyphPages.PageStroke>();
        for (double[] d : g.strokes) {
            float[] f = new float[d.length];
            for (int i = 0; i < d.length; i++) f[i] = (float) d[i];
            strokes.add(new com.fimkov.witchsatelier.glyph.page.GlyphPages.PageStroke(f, com.fimkov.witchsatelier.glyph.ink.InkType.CONJURING));
        }
        var pages = com.fimkov.witchsatelier.glyph.page.GlyphPages.blank(16).withPage(0, new com.fimkov.witchsatelier.glyph.page.GlyphPages.Page(List.copyOf(strokes)));
        quire.set(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get(), pages);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, quire);

        h.startSequence()
                .thenExecute(() -> {
                    if (GlyphService.activeSpells(h.getLevel()).stream().anyMatch(s -> player.getUUID().equals(s.caster)))
                        h.fail("an open ring must not cast");
                    com.fimkov.witchsatelier.glyph.page.PageService.handleQuickClose(player);
                })
                .thenExecuteAfter(1, () -> {
                    var mine = GlyphService.activeSpells(h.getLevel()).stream().filter(s -> player.getUUID().equals(s.caster)).toList();
                    if (mine.size() != 1) h.fail("quick close should cast one spell, got " + mine.size());
                    if (!mine.get(0).handheld || mine.get(0).program.element() != Sigil.FIRE) h.fail("cast " + mine.get(0).program.summary());
                    var after = player.getOffhandItem().get(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get());
                    if (after == null || !after.page(0).isEmpty() || after.current() != 1) h.fail("the used page should be burnt and turned");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void inkIsBrewedFromSilverwood(GameTestHelper h) {
        var brewing = h.getLevel().potionBrewing();
        var water = net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION,
                net.minecraft.world.item.alchemy.Potions.WATER);
        var twig = new net.minecraft.world.item.ItemStack(com.fimkov.witchsatelier.registry.ModItems.BLACKENED_TWIG.get());
        var woodcruor = brewing.mix(twig, water);
        if (!woodcruor.is(com.fimkov.witchsatelier.registry.ModItems.WOODCRUOR.get())) h.fail("water + twig gave " + woodcruor);
        var ink = brewing.mix(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLOWSTONE_DUST), woodcruor);
        if (!ink.is(com.fimkov.witchsatelier.registry.ModItems.CONJURING_INK.get())) h.fail("woodcruor + glowstone gave " + ink);
        var proof = brewing.mix(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PRISMARINE_SHARD), ink);
        if (!proof.is(com.fimkov.witchsatelier.registry.ModItems.WATERPROOF_INK.get())) h.fail("conjuring + prismarine gave " + proof);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void drawingDipsIntoTheInkwellInTheOtherHand(GameTestHelper h) {
        floor(h);
        ServerPlayer player = survivor(h);
        player.teleportTo(h.absoluteVec(new Vec3(5.5, 1, 3.5)).x, h.absoluteVec(new Vec3(5.5, 1, 3.5)).y, h.absoluteVec(new Vec3(5.5, 1, 3.5)).z);
        var pen = new net.minecraft.world.item.ItemStack(com.fimkov.witchsatelier.registry.ModItems.INK_PEN.get());
        var well = new net.minecraft.world.item.ItemStack(com.fimkov.witchsatelier.registry.ModItems.INKWELL.get());
        com.fimkov.witchsatelier.item.InkwellItem.setContents(well, new com.fimkov.witchsatelier.glyph.ink.LoadedInk(com.fimkov.witchsatelier.glyph.ink.InkType.WATERPROOF, 2048));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, pen);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, well);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        double[] line = new double[42];
        for (int i = 0; i < 21; i++) { line[i * 2] = c.x() - 1 + i * 0.1; line[i * 2 + 1] = c.y(); }
        GlyphService.handleDraw(player, plane, line);
        var data = com.fimkov.witchsatelier.glyph.storage.GlyphLevelData.get(h.getLevel());
        var drawn = data.near(plane, c.x(), c.y(), 1).stream().filter(s -> Math.abs(s.v(0) - c.y()) < 0.01 && s.ink() == com.fimkov.witchsatelier.glyph.ink.InkType.WATERPROOF).toList();
        if (drawn.size() != 1) h.fail("expected one waterproof line, got " + drawn.size());
        var left = com.fimkov.witchsatelier.item.InkwellItem.contents(player.getOffhandItem());
        var nib = com.fimkov.witchsatelier.item.InkPenItem.loaded(player.getMainHandItem());
        // 2 blocks of line = 8 units; the pen dipped once: took 8 + a full nib from the well
        int full = com.fimkov.witchsatelier.item.InkPenItem.NIB_CAPACITY;
        if (left == null || left.amount() != 2048 - 8 - full) h.fail("inkwell should hold " + (2048 - 8 - full) + ", holds " + (left == null ? 0 : left.amount()));
        if (nib == null || nib.amount() != full || nib.type() != com.fimkov.witchsatelier.glyph.ink.InkType.WATERPROOF) h.fail("nib " + nib);
        h.succeed();
    }

    private static com.fimkov.witchsatelier.glyph.page.GlyphPages.Page page(GlyphSketch g) {
        var strokes = new java.util.ArrayList<com.fimkov.witchsatelier.glyph.page.GlyphPages.PageStroke>();
        for (double[] d : g.strokes) {
            float[] f = new float[d.length];
            for (int i = 0; i < d.length; i++) f[i] = (float) d[i];
            strokes.add(new com.fimkov.witchsatelier.glyph.page.GlyphPages.PageStroke(f, com.fimkov.witchsatelier.glyph.ink.InkType.CONJURING));
        }
        return new com.fimkov.witchsatelier.glyph.page.GlyphPages.Page(List.copyOf(strokes));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bootsGlyphFliesOnHeelClick(GameTestHelper h) {
        floor(h);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        var boots = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_BOOTS);
        GlyphSketch g = new GlyphSketch(new Pt(0, 0), 0.85).ring(0, 360).sigil(Sigil.WIND, 0.55)
                .signs(SignType.LEVITATION, 4, Orientation.OUTWARD, 0.28, 45);
        // drawn as on a pair of shoes: half the glyph on each sole, the two apart
        var halves = new java.util.ArrayList<com.fimkov.witchsatelier.glyph.page.GlyphPages.PageStroke>();
        for (double[] d : g.strokes) {
            float[] f = new float[d.length];
            for (int i = 0; i < d.length; i++) f[i] = (float) d[i];
            for (float[] part : com.fimkov.witchsatelier.glyph.page.PageKind.splitForSoles(f, 0.82f))
                halves.add(new com.fimkov.witchsatelier.glyph.page.GlyphPages.PageStroke(part, com.fimkov.witchsatelier.glyph.ink.InkType.CONJURING));
        }
        boots.set(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get(), com.fimkov.witchsatelier.glyph.page.GlyphPages.blank(1)
                .withPage(0, new com.fimkov.witchsatelier.glyph.page.GlyphPages.Page(List.copyOf(halves))));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, boots);
        if (com.fimkov.witchsatelier.glyph.page.PageKind.of(boots) != com.fimkov.witchsatelier.glyph.page.PageKind.BOOTS) h.fail("boots are not a canvas");

        h.startSequence()
                .thenExecute(() -> com.fimkov.witchsatelier.glyph.page.PageService.handleBootsJoin(player))
                .thenExecuteAfter(3, () -> {
                    var worn = GlyphService.wornSpell(h.getLevel(), player.getUUID());
                    if (worn == null) h.fail("heel click should start the boots spell");
                    if (worn.program.form() != com.fimkov.witchsatelier.spell.SpellProgram.Form.LIFT) h.fail("cast " + worn.program.summary());
                    if (worn.ticksLeft < 20 * 60 * 60) h.fail("shoe flight should last until the heels click again, not " + worn.ticksLeft + " ticks");
                    if (player.fallDistance > 0) h.fail("flying, the wearer must not count as falling");
                    var still = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).get(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get());
                    if (still == null || still.page(0).isEmpty()) h.fail("the glyph must stay on the boots");
                    com.fimkov.witchsatelier.glyph.page.PageService.handleBootsJoin(player);
                })
                .thenExecuteAfter(1, () -> {
                    if (GlyphService.wornSpell(h.getLevel(), player.getUUID()) != null) h.fail("a second heel click should stop it");
                })
                .thenSucceed();
    }

    /** Wading into water washes the glyph off shoe soles (unless waterproof), and ends the flight it was giving. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void waterWashesTheGlyphOffShoes(GameTestHelper h) {
        floor(h);
        for (int x = 3; x <= 7; x++) for (int z = 3; z <= 7; z++) h.setBlock(x, 1, z, Blocks.WATER);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        var boots = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_BOOTS);
        GlyphSketch g = new GlyphSketch(new Pt(0, 0), 0.85).ring(0, 360).sigil(Sigil.WIND, 0.55)
                .signs(SignType.LEVITATION, 4, Orientation.OUTWARD, 0.28, 45);
        boots.set(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get(), com.fimkov.witchsatelier.glyph.page.GlyphPages.blank(1).withPage(0, page(g)));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, boots);
        h.startSequence()
                .thenExecute(() -> com.fimkov.witchsatelier.glyph.page.PageService.handleBootsJoin(player))
                .thenExecute(() -> {
                    BlockPos w = h.absolutePos(new BlockPos(5, 1, 5));
                    player.teleportTo(w.getX() + 0.5, w.getY(), w.getZ() + 0.5);
                })
                // a test player is not ticked by a connection: tick it as one would be
                .thenExecuteFor(12, player::doTick)
                .thenWaitUntil(() -> {
                    var still = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).get(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get());
                    if (still != null && !still.page(0).isEmpty()) h.fail("the water should wash the glyph off");
                })
                .thenExecute(() -> {
                    if (GlyphService.wornSpell(h.getLevel(), player.getUUID()) != null) h.fail("with its glyph gone the flight should end");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void birdSignSendsOutBirdsOfLight(GameTestHelper h) {
        floor(h);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        var quire = new net.minecraft.world.item.ItemStack(com.fimkov.witchsatelier.registry.ModItems.PALM_QUIRE.get());
        GlyphSketch g = new GlyphSketch(new Pt(0, 0), 0.85).ring(25, 335).sigil(Sigil.LIGHT, 0.55)
                .signs(SignType.BIRD, 2, Orientation.OUTWARD, 0.3, 90);
        quire.set(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get(), com.fimkov.witchsatelier.glyph.page.GlyphPages.blank(16).withPage(0, page(g)));
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, quire);
        Vec3[] start = new Vec3[1];

        h.startSequence()
                .thenExecute(() -> com.fimkov.witchsatelier.glyph.page.PageService.handleQuickClose(player))
                .thenExecuteAfter(2, () -> {
                    var mine = GlyphService.activeSpells(h.getLevel()).stream().filter(s -> player.getUUID().equals(s.caster)).toList();
                    if (mine.size() != 1) h.fail("expected one spell, got " + mine.size());
                    var s = mine.get(0);
                    if (s.program.birds() < 1 || s.birds().isEmpty()) h.fail("no birds: " + s.program.summary());
                    start[0] = s.birds().get(0).pos;
                })
                .thenExecuteAfter(20, () -> {
                    var s = GlyphService.activeSpells(h.getLevel()).stream().filter(x -> player.getUUID().equals(x.caster)).findFirst().orElse(null);
                    if (s == null || s.birds().isEmpty()) h.fail("the birds vanished too soon");
                    if (s.birds().get(0).pos.distanceTo(start[0]) < 3) h.fail("the bird should fly off");
                })
                .thenSucceed();
    }

    /**
     * Rings the size people draw them (under three blocks across) once opened a single block of wall, or none: no
     * way through. Every block whose face's middle lies in the disc steps aside now.
     */
    @GameTest(template = "empty", timeoutTicks = 60, batch = "windowway_small") // its own batch: windows pair across tests
    public static void smallWindowwaysStillOpenAWayThrough(GameTestHelper h) {
        floor(h);
        for (int y = 1; y <= 4; y++) for (int z = 2; z <= 8; z++) { h.setBlock(1, y, z, Blocks.STONE); h.setBlock(9, y, z, Blocks.STONE); }
        BlockPos a = h.absolutePos(new BlockPos(1, 2, 5)), b = h.absolutePos(new BlockPos(9, 2, 5));
        SurfacePlane pa = SurfacePlane.of(a, Direction.EAST), pb = SurfacePlane.of(b, Direction.WEST);
        // one centred on a block, one on a block corner
        Pt ca = pa.toUV(Vec3.atCenterOf(a).add(0.5, 0, 0)), cb = pb.toUV(Vec3.atCenterOf(b).add(-0.5, 0.5, 0.5));
        GlyphService.inscribe(h.getLevel(), pa, new GlyphSketch(ca, 1.3).ring(0, 362).centerSign(SignType.WINDOW, 0.75).strokes, null);
        GlyphService.inscribe(h.getLevel(), pb, new GlyphSketch(cb, 1.3).ring(0, 362).centerSign(SignType.WINDOW, 0.75).strokes, null);
        h.startSequence()
                .thenExecuteAfter(3, () -> {
                    var gap = com.fimkov.witchsatelier.registry.ModBlocks.WINDOWWAY_GAP.get();
                    for (int[] q : new int[][]{{2, 5}, {1, 5}, {3, 5}, {2, 4}, {2, 6}}) h.assertBlockPresent(gap, new BlockPos(1, q[0], q[1]));
                    h.assertBlockPresent(Blocks.STONE, new BlockPos(1, 1, 4));
                    for (int[] q : new int[][]{{2, 5}, {3, 5}, {2, 6}, {3, 6}}) h.assertBlockPresent(gap, new BlockPos(9, q[0], q[1]));
                    var west = com.fimkov.witchsatelier.windowway.WindowwayService.frames(h.getLevel()).stream()
                            .filter(f -> f.center().distanceTo(Vec3.atCenterOf(a)) < 3).findFirst().orElseThrow();
                    if (west.link() < 0) h.fail("the two small windows should join");
                    // stepping through the lower block of the opening, outside the round disc, still counts
                    Vec3 low = h.absoluteVec(new Vec3(2.0, 1.2, 5.5));
                    if (!west.inOpening(low)) h.fail("the opening's lower block should be a way through");
                    for (var sp : spellsOn(h, pa)) GlyphService.stop(h.getLevel(), sp);
                    for (var sp : spellsOn(h, pb)) GlyphService.stop(h.getLevel(), sp);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void windowwaysJoinOpenTheWallAndCarryThingsAcross(GameTestHelper h) {
        floor(h);
        for (int y = 1; y <= 4; y++) for (int z = 2; z <= 8; z++) { h.setBlock(1, y, z, Blocks.STONE); h.setBlock(9, y, z, Blocks.STONE); }
        // one window on the east face of the west wall, one on the west face of the east wall
        BlockPos a = h.absolutePos(new BlockPos(1, 2, 5)), b = h.absolutePos(new BlockPos(9, 2, 5));
        SurfacePlane pa = SurfacePlane.of(a, Direction.EAST), pb = SurfacePlane.of(b, Direction.WEST);
        Pt ca = pa.toUV(Vec3.atCenterOf(a).add(0.5, 0, 0)), cb = pb.toUV(Vec3.atCenterOf(b).add(-0.5, 0, 0));
        GlyphService.inscribe(h.getLevel(), pa, new GlyphSketch(ca, 2.0).ring(0, 362).centerSign(SignType.WINDOW, 1.1).strokes, null);
        GlyphService.inscribe(h.getLevel(), pb, new GlyphSketch(cb, 2.0).ring(0, 362).centerSign(SignType.WINDOW, 1.1).strokes, null);
        var item = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), 0, 0, 0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FEATHER));
        ServerPlayer player = h.makeMockServerPlayerInLevel();

        h.startSequence()
                .thenExecuteAfter(3, () -> {
                    var mine = com.fimkov.witchsatelier.windowway.WindowwayService.frames(h.getLevel()).stream()
                            .filter(f -> f.center().distanceTo(Vec3.atCenterOf(a)) < 3 || f.center().distanceTo(Vec3.atCenterOf(b)) < 3).toList();
                    if (mine.size() != 2) h.fail("expected two windows, got " + mine.size() + " (" + spellsOn(h, pa).size() + " spells on A)");
                    if (mine.stream().anyMatch(f -> f.link() < 0)) h.fail("the two windows should join");
                    h.assertBlockPresent(com.fimkov.witchsatelier.registry.ModBlocks.WINDOWWAY_GAP.get(), new BlockPos(1, 2, 5));
                    h.assertBlockPresent(com.fimkov.witchsatelier.registry.ModBlocks.WINDOWWAY_GAP.get(), new BlockPos(1, 1, 5));
                    // a feather drifting into the west window
                    Vec3 start = h.absoluteVec(new Vec3(2.4, 2.5, 5.5));
                    item.setPos(start.x, start.y, start.z);
                    item.setNoGravity(true);
                    item.setDeltaMovement(-0.25, 0, 0);
                    item.setPickUpDelay(1000);
                    h.getLevel().addFreshEntity(item);
                })
                .thenExecuteAfter(8, () -> {
                    Vec3 rel = item.position().subtract(h.absoluteVec(Vec3.ZERO));
                    if (rel.x < 5 || rel.x > 9.2 || Math.abs(rel.z - 5.5) > 0.6) h.fail("the feather should come out of the east window, it is at " + rel);
                    if (item.getDeltaMovement().x >= 0) h.fail("it should keep flying away from the window, v=" + item.getDeltaMovement());
                    // a player stepping into the west window comes out of the east one, facing away from it
                    Vec3 p = h.absoluteVec(new Vec3(1.9, 1, 5.5)); // just through the west window's surface
                    player.moveTo(p.x, p.y, p.z, 90, 0);
                    var west = com.fimkov.witchsatelier.windowway.WindowwayService.frames(h.getLevel()).stream()
                            .filter(f -> f.center().distanceTo(Vec3.atCenterOf(a)) < 3).findFirst().orElseThrow();
                    com.fimkov.witchsatelier.windowway.WindowwayService.handleCross(player, west.id());
                    Vec3 r = player.position().subtract(h.absoluteVec(Vec3.ZERO));
                    if (r.x < 8.5 || r.x > 9.0 || Math.abs(r.z - 5.5) > 0.3 || Math.abs(r.y - 1) > 0.05) h.fail("player came out at " + r);
                    if (player.getLookAngle().x > -0.9) h.fail("player should face west, out of the window: " + player.getLookAngle());
                    for (var s : spellsOn(h, pa)) GlyphService.stop(h.getLevel(), s);
                    for (var s : spellsOn(h, pb)) GlyphService.stop(h.getLevel(), s); // a window left waiting would join the next test's
                })
                .thenExecuteAfter(2, () -> {
                    h.assertBlockPresent(Blocks.STONE, new BlockPos(1, 2, 5));
                    h.assertBlockPresent(Blocks.STONE, new BlockPos(9, 2, 5));
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void windowwayMathIsAHalfTurn(GameTestHelper h) {
        var A = com.fimkov.witchsatelier.windowway.PortalFrame.of(1, new Vec3(3, 2.5, 5.5), new Vec3(0, 1, 0), new Vec3(1, 0, 0), 1.5, 2);
        var B = com.fimkov.witchsatelier.windowway.PortalFrame.of(2, new Vec3(40, 70.5, -12.5), new Vec3(0, 1, 0), new Vec3(0, 0, 1), 1.5, 1);
        if (A.u().cross(A.v()).distanceTo(A.n()) > 1e-9) h.fail("basis not right-handed");
        Vec3 through = A.center().add(-0.1, -1, 0.3);
        if (B.side(com.fimkov.witchsatelier.windowway.PortalFrame.mapPoint(A, B, through)) <= 0) h.fail("should come out of B's front");
        if (com.fimkov.witchsatelier.windowway.PortalFrame.mapDir(A, B, new Vec3(-1, 0, 0)).distanceTo(B.n()) > 1e-9) h.fail("into A = out of B");
        Vec3 p = new Vec3(4.1, 3.3, 6.0);
        if (com.fimkov.witchsatelier.windowway.PortalFrame.mapPoint(B, A, com.fimkov.witchsatelier.windowway.PortalFrame.mapPoint(A, B, p)).distanceTo(p) > 1e-9) h.fail("not invertible");
        Vec3 d = new Vec3(0.3, -0.5, 0.8).normalize();
        var r = new org.joml.Vector3f((float) d.x, (float) d.y, (float) d.z).rotate(com.fimkov.witchsatelier.windowway.PortalFrame.rotation(A, B));
        if (com.fimkov.witchsatelier.windowway.PortalFrame.mapDir(A, B, d).distanceTo(new Vec3(r.x, r.y, r.z)) > 1e-4) h.fail("rotation disagrees with mapping");
        if (!com.fimkov.witchsatelier.windowway.WindowwayService.crossed(A, A.center().add(0.2, 0, 0), A.center().add(-0.2, 0, 0))) h.fail("crossing missed");
        if (com.fimkov.witchsatelier.windowway.WindowwayService.crossed(A, A.center().add(0.2, 0, 2), A.center().add(-0.2, 0, 2))) h.fail("beside the disc");
        if (com.fimkov.witchsatelier.windowway.WindowwayService.crossed(A, A.center().add(-0.2, 0, 0), A.center().add(0.2, 0, 0))) h.fail("out the back");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void dungeonChestsHoldGrimoiresAndPreparedPages(GameTestHelper h) {
        var level = h.getLevel();
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("chests/stronghold_library"));
        var table = level.getServer().reloadableRegistries().getLootTable(key);
        int books = 0, pages = 0;
        for (int i = 0; i < 100; i++) {
            var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, h.absoluteVec(Vec3.ZERO))
                    .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
            for (var st : table.getRandomItems(params)) {
                if (st.is(com.fimkov.witchsatelier.registry.ModItems.GRIMOIRE.get())) {
                    books++;
                    var gp = st.get(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get());
                    if (gp == null || gp.page(2).isEmpty()) h.fail("grimoire without glyphs");
                    var names = st.get(com.fimkov.witchsatelier.registry.ModComponents.GRIMOIRE_ENTRIES.get());
                    if (names == null || !"basics".equals(names.get(0)) || !"imagined_ring".equals(names.get(1)) || names.get(2).isEmpty()) h.fail("grimoire pages should say what they show: " + names);
                    var r = com.fimkov.witchsatelier.glyph.recognize.GlyphAnalyzer.analyze(com.fimkov.witchsatelier.glyph.page.PageService.pointsOf(gp.page(2)), com.fimkov.witchsatelier.glyph.page.PageService.PAGE_INK);
                    if (r.ring() == null || !r.closed()) h.fail("a grimoire's glyph should be complete");
                }
                if (st.is(com.fimkov.witchsatelier.registry.ModItems.TORN_PAGE.get())) {
                    pages++;
                    var gp = st.get(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get());
                    var r = com.fimkov.witchsatelier.glyph.recognize.GlyphAnalyzer.analyze(com.fimkov.witchsatelier.glyph.page.PageService.pointsOf(gp.page(0)), com.fimkov.witchsatelier.glyph.page.PageService.PAGE_INK);
                    if (r.ring() == null || r.closed()) h.fail("a prepared page should have an open ring");
                }
            }
        }
        if (books < 10 || pages < 15) h.fail("too few spell finds in 100 library chests: " + books + " grimoires, " + pages + " pages");
        // every glyph of the library reads as what it is meant to be
        for (var recipe : com.fimkov.witchsatelier.glyph.page.GlyphLibrary.ALL) {
            var page = com.fimkov.witchsatelier.glyph.page.GlyphLibrary.draw(recipe, net.minecraft.util.RandomSource.create(1), false, com.fimkov.witchsatelier.glyph.ink.InkType.CONJURING);
            var r = com.fimkov.witchsatelier.glyph.recognize.GlyphAnalyzer.analyze(com.fimkov.witchsatelier.glyph.page.PageService.pointsOf(page), com.fimkov.witchsatelier.glyph.page.PageService.PAGE_INK);
            if (!r.closed()) h.fail(recipe.name() + " should be closed: " + r.describe());
            var inner = r;
            if (recipe.band() != null) {
                if (r.core() == null) h.fail(recipe.name() + " should read as nested: " + r.describe());
                inner = r.core();
                if (r.sigil() != recipe.band().sigil()) h.fail(recipe.name() + " band sigil read as " + r.sigil() + ": " + r.describe());
                for (var sg : recipe.band().signs()) if (r.count(sg.type()) < sg.count()) h.fail(recipe.name() + " band: " + sg.type() + " read " + r.count(sg.type()) + "x: " + r.describe());
            }
            if (inner.sigil() != recipe.sigil()) h.fail(recipe.name() + " read as " + inner.sigil() + ": " + r.describe());
            if (inner.centerSign() != recipe.centerSign() && recipe.centerSign() != null) h.fail(recipe.name() + " centre read as " + inner.centerSign());
            for (var sg : recipe.signs()) if (inner.count(sg.type()) < sg.count()) h.fail(recipe.name() + ": " + sg.type() + " read " + inner.count(sg.type()) + "x: " + r.describe());
            var program = com.fimkov.witchsatelier.spell.SpellCompiler.compile(r, com.fimkov.witchsatelier.spell.GlyphFrame.facing(new Vec3(0, 1, 0)), Vec3.ZERO, 1, 1, 1, 0);
            if (program.form() == com.fimkov.witchsatelier.spell.SpellProgram.Form.FIZZLE) h.fail(recipe.name() + " fizzles: " + r.describe());
            if (recipe.band() != null && program.layers().isEmpty() && program.duration() <= 0) h.fail(recipe.name() + " band does nothing");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void strangerGivesAGrimoireOnceThenTrades(GameTestHelper h) {
        floor(h);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        var stranger = h.spawn(com.fimkov.witchsatelier.registry.ModEntities.STRANGER.get(), new BlockPos(5, 1, 5));
        stranger.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        var inv = player.getInventory();
        if (inv.countItem(com.fimkov.witchsatelier.registry.ModItems.GRIMOIRE.get()) != 1) h.fail("no grimoire given");
        if (!inv.contains(new net.minecraft.world.item.ItemStack(com.fimkov.witchsatelier.registry.ModItems.INK_PEN.get()))) h.fail("no pen given");
        int books = inv.countItem(com.fimkov.witchsatelier.registry.ModItems.GRIMOIRE.get());
        stranger.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (inv.countItem(com.fimkov.witchsatelier.registry.ModItems.GRIMOIRE.get()) != books) h.fail("the gift comes only once");
        if (stranger.getOffers().isEmpty()) h.fail("the stranger should have things to trade");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void waterJetSoaksAndWaveRollsOut(GameTestHelper h) {
        floor(h);
        for (int y = 1; y <= 4; y++) for (int z = 3; z <= 7; z++) h.setBlock(1, y, z, Blocks.STONE);
        h.setBlock(5, 1, 5, Blocks.CAULDRON);
        BlockPos wall = h.absolutePos(new BlockPos(1, 2, 5));
        SurfacePlane pw = SurfacePlane.of(wall, Direction.EAST);
        Pt cw = pw.toUV(Vec3.atCenterOf(wall).add(0.5, -0.3, 0));
        // a water jet out of the wall, tilted down onto the cauldron by a Direction sign below the sigil
        GlyphService.inscribe(h.getLevel(), pw, new GlyphSketch(cw, 1.0).ring(0, 362).sigil(Sigil.WATER, 0.6)
                .signs(SignType.COLUMN, 4, Orientation.OUTWARD, 0.3, 45).strokes, null);
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 1, 5));
        pig.igniteForSeconds(20);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    var s = spellsOn(h, pw);
                    if (s.isEmpty() || s.get(0).program.element() != Sigil.WATER || s.get(0).program.form() != SpellProgram.Form.BEAM)
                        h.fail("expected a water jet, got " + (s.isEmpty() ? "nothing" : s.get(0).program.summary()));
                })
                .thenWaitUntil(() -> { if (pig.isOnFire()) h.fail("the jet should put the pig out"); })
                .thenExecute(() -> { if (pig.getX() - h.absoluteVec(Vec3.ZERO).x < 3.3) h.fail("the jet should push the pig away"); })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void contraptionsDoTheirWork(GameTestHelper h) {
        floor(h);
        var level = h.getLevel();
        // washing barrel mends what lies in it while its seal holds
        h.setBlock(2, 1, 2, com.fimkov.witchsatelier.registry.ModBlocks.WASHING_BARREL.get());
        var barrel = (com.fimkov.witchsatelier.block.sealed.WashingBarrelBlock.Entity) level.getBlockEntity(h.absolutePos(new BlockPos(2, 1, 2)));
        barrel.addCharge(24000, 96000);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        var pick = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
        pick.setDamageValue(200);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, pick);
        h.useBlock(new BlockPos(2, 1, 2), player);
        // a void basin swallows what falls in
        h.setBlock(6, 1, 2, com.fimkov.witchsatelier.registry.ModBlocks.VOID_BASIN.get());
        var junk = h.spawnItem(net.minecraft.world.item.Items.DIRT, new Vec3(6.5, 1.6, 2.5));
        // a warded tower drives a zombie off
        h.setBlock(5, 1, 7, com.fimkov.witchsatelier.registry.ModBlocks.WARDING_TOWER.get());
        var tower = (com.fimkov.witchsatelier.block.sealed.BeastWardingTowerBlock.Entity) level.getBlockEntity(h.absolutePos(new BlockPos(5, 1, 7)));
        tower.addCharge(48000, 192000);
        var zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(7, 1, 7));
        double startDist = zombie.position().distanceTo(Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 1, 7))));
        h.startSequence()
                .thenExecuteAfter(1, () -> {
                    if (barrel.held().isEmpty()) h.fail("the barrel should take the pickaxe");
                })
                .thenExecuteAfter(60, () -> {
                    if (barrel.held().getDamageValue() >= 200) h.fail("the barrel should mend it, damage " + barrel.held().getDamageValue());
                    if (junk.isAlive()) h.fail("the void basin should swallow the dirt");
                    double d = zombie.position().distanceTo(Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 1, 7))));
                    if (d < startDist + 1) h.fail("the tower should drive the zombie off: " + startDist + " -> " + d);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void arcaneLensAmplifiesAGlyph(GameTestHelper h) {
        floor(h);
        h.setBlock(2, 1, 5, com.fimkov.witchsatelier.registry.ModBlocks.ARCANE_LENS.get());
        Object[] near = top(h, new BlockPos(2, 0, 5)), far = top(h, new BlockPos(8, 0, 5));
        for (Object[] t : List.of(near, far)) {
            GlyphService.inscribe(h.getLevel(), (SurfacePlane) t[0], new GlyphSketch((Pt) t[1], 1.0).ring(0, 362).sigil(Sigil.LIGHT, 0.6).strokes, null);
        }
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    Vec3 a = h.absoluteVec(new Vec3(2.5, 1, 5.5)), b = h.absoluteVec(new Vec3(8.5, 1, 5.5));
                    ActiveSpell sa = null, sb = null;
                    for (ActiveSpell s : GlyphService.activeSpells(h.getLevel())) {
                        if (s.program.origin().distanceTo(a) < 1) sa = s;
                        if (s.program.origin().distanceTo(b) < 1) sb = s;
                    }
                    if (sa == null || sb == null) h.fail("both glyphs should cast");
                    if (sa.program.power() < sb.program.power() * 1.5) h.fail("the lens should amplify: " + sa.program.power() + " vs " + sb.program.power());
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void wardTurnsArrowsAndPuppetsDance(GameTestHelper h) {
        floor(h);
        Object[] ward = top(h, new BlockPos(3, 0, 5)), dance = top(h, new BlockPos(8, 0, 5));
        var wr = com.fimkov.witchsatelier.glyph.page.GlyphLibrary.byName("crystal_ward");
        GlyphService.inscribe(h.getLevel(), (SurfacePlane) ward[0], new GlyphSketch((Pt) ward[1], 1.2).ring(0, 362).centerSign(SignType.DIAMOND, 0.7).strokes, null);
        GlyphService.inscribe(h.getLevel(), (SurfacePlane) dance[0], new GlyphSketch((Pt) dance[1], 1.2).ring(0, 362).centerSign(SignType.DANCING_PUPPET, 0.7).strokes, null);
        var arrow = new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, h.getLevel());
        Vec3 c = h.absoluteVec(new Vec3(3.5, 1, 5.5));
        var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(8, 1, 6));
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    arrow.setPos(c.x + 2, c.y + 1, c.z);
                    arrow.setDeltaMovement(-0.6, 0, 0);
                    arrow.setNoGravity(true);
                    h.getLevel().addFreshEntity(arrow);
                    zombie.setTarget(h.makeMockServerPlayerInLevel());
                })
                .thenExecuteAfter(4, () -> {
                    if (arrow.getDeltaMovement().x <= 0) h.fail("the ward should turn the arrow back, v=" + arrow.getDeltaMovement());
                    if (zombie.getTarget() != null) h.fail("a dancing puppet forgets its target");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void petrificationMakesStatuesAndIntegrationRevives(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(5, 1, 5));
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(c, 1.4).ring(0, 362).sigil(Sigil.EARTH, 0.8)
                .signs(SignType.EYE, 4, Orientation.OUTWARD, 0.4, 45).strokes, null);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    var s = spellsOn(h, plane);
                    if (s.isEmpty() || s.get(0).program.form() != SpellProgram.Form.PETRIFY) h.fail("expected petrification, got " + (s.isEmpty() ? "nothing" : s.get(0).program.summary()));
                })
                .thenWaitUntil(() -> { if (pig.isAlive()) h.fail("the pig should turn to stone"); })
                .thenExecute(() -> {
                    h.assertBlockPresent(com.fimkov.witchsatelier.registry.ModBlocks.STATUE.get(), new BlockPos(5, 1, 5));
                    for (var s : spellsOn(h, plane)) GlyphService.stop(h.getLevel(), s);
                    int back = com.fimkov.witchsatelier.block.StatueBlock.revive(h.getLevel(), h.absoluteVec(new Vec3(5.5, 1, 5.5)), 3);
                    if (back != 1) h.fail("integration should bring the pig back, revived " + back);
                    com.fimkov.witchsatelier.entity.Taboo.add(player, 40);
                    if (com.fimkov.witchsatelier.entity.Taboo.of(player) < com.fimkov.witchsatelier.entity.Taboo.HUNTED) h.fail("forbidden magic should be counted");
                })
                .thenSucceed();
    }

    /** A villager watching ink being drawn takes fright, thinks worse of the witch, and the Pact counts it (once a while). */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void villagersWhoSeeMagicMadeTakeFright(GameTestHelper h) {
        floor(h);
        ServerPlayer player = survivor(h);
        Vec3 at = h.absoluteVec(new Vec3(5.5, 1, 2.5));
        player.moveTo(at.x, at.y, at.z, 0, 0);
        var villager = h.spawn(EntityType.VILLAGER, new BlockPos(5, 1, 7));
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    villager.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
                    villager.setYBodyRot(villager.getYHeadRot());
                    int before = com.fimkov.witchsatelier.entity.Taboo.of(player);
                    int saw = com.fimkov.witchsatelier.entity.Witnesses.saw(player);
                    if (saw != 1) h.fail("the villager should have seen it, saw=" + saw);
                    if (!villager.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY_ENTITY)) h.fail("the villager should run from the witch");
                    if (villager.getGossips().getReputation(player.getUUID(), t -> true) >= 0) h.fail("the villager should think worse of the witch");
                    if (com.fimkov.witchsatelier.entity.Taboo.of(player) != before + com.fimkov.witchsatelier.entity.Witnesses.TABOO) h.fail("the Pact should count it");
                    // the next stroke of the same glyph is not counted again
                    com.fimkov.witchsatelier.entity.Witnesses.saw(player);
                    if (com.fimkov.witchsatelier.entity.Taboo.of(player) != before + com.fimkov.witchsatelier.entity.Witnesses.TABOO) h.fail("one glyph counts once");
                })
                .thenSucceed();
    }

    /** The knights sent after a player go away once the player is dead, and the hunt is over. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void knightsLeaveOnceTheyHaveKilled(GameTestHelper h) {
        floor(h);
        ServerPlayer player = survivor(h);
        Vec3 at = h.absoluteVec(new Vec3(5.5, 1, 5.5));
        player.moveTo(at.x, at.y, at.z, 0, 0);
        var knight = h.spawn(com.fimkov.witchsatelier.registry.ModEntities.KNIGHT_MORALIS.get(), new BlockPos(2, 1, 5));
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    com.fimkov.witchsatelier.entity.Taboo.add(player, 60);
                    player.getPersistentData().putLong(com.fimkov.witchsatelier.entity.Taboo.HUNT, 7);
                    knight.hunt(player, 7);
                    // (a fresh player is shielded for a while after joining: this blow goes through)
                    player.hurt(new net.minecraft.world.damagesource.DamageSource(h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL), knight), 1000);
                })
                .thenExecuteAfter(2, () -> {
                    if (player.isAlive()) h.fail("the player should be dead");
                    if (com.fimkov.witchsatelier.entity.Taboo.huntOf(player) != 0 || com.fimkov.witchsatelier.entity.Taboo.of(player) != 0) h.fail("the hunt should be over");
                    if (knight.getTarget() != null) h.fail("the knight should stop fighting");
                })
                .thenWaitUntil(() -> { if (knight.isAlive()) h.fail("the knight should leave"); })
                .thenSucceed();
    }

    /** Knights left behind (gone far off, or stuck in country nobody is near) are replaced by others near the hunted player. */
    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void knightsLeftBehindAreReplaced(GameTestHelper h) {
        floor(h);
        ServerPlayer player = survivor(h);
        Vec3 at = h.absoluteVec(new Vec3(5.5, 1, 5.5));
        player.moveTo(at.x, at.y, at.z, 0, 0);
        java.util.function.Supplier<List<com.fimkov.witchsatelier.entity.KnightMoralisEntity>> near = () -> h.getLevel().getEntitiesOfClass(
                com.fimkov.witchsatelier.entity.KnightMoralisEntity.class, player.getBoundingBox().inflate(48), k -> k.huntId() == 9);
        h.startSequence()
                .thenExecute(() -> {
                    com.fimkov.witchsatelier.entity.Taboo.add(player, 40);
                    player.getPersistentData().putLong(com.fimkov.witchsatelier.entity.Taboo.HUNT, 9);
                    if (!near.get().isEmpty()) h.fail("no knights yet");
                })
                // (only the Pact's part of the player's tick: a mock player left to tick on its own dies of it)
                .thenWaitUntil(() -> {
                    com.fimkov.witchsatelier.entity.Taboo.onPlayerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
                    if (near.get().isEmpty()) h.fail("knights should have been sent");
                })
                .thenExecute(() -> near.get().forEach(x -> x.discard()))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void brimhatsCastRealGlyphs(GameTestHelper h) {
        floor(h);
        var brimhat = h.spawn(com.fimkov.witchsatelier.registry.ModEntities.BRIMHAT.get(), new BlockPos(2, 1, 5));
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(8, 1, 5));
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    brimhat.setTarget(pig);
                    brimhat.cast(com.fimkov.witchsatelier.entity.BrimhatEntity.Spell.BIND, pig);
                    brimhat.cast(com.fimkov.witchsatelier.entity.BrimhatEntity.Spell.BOLTS, pig);
                })
                .thenExecuteAfter(25, () -> {
                    if (!pig.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)) h.fail("the binding threads should hold the pig");
                    var mask = new net.minecraft.world.item.ItemStack(com.fimkov.witchsatelier.registry.ModItems.BRIMHAT_MASK.get());
                    ServerPlayer p = h.makeMockServerPlayerInLevel();
                    p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, mask);
                    if (!com.fimkov.witchsatelier.entity.BrimhatEntity.wearsMask(p)) h.fail("the mask should pass as a Brimhat");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void smallContraptionsWork(GameTestHelper h) {
        floor(h);
        h.setBlock(3, 1, 3, com.fimkov.witchsatelier.registry.ModBlocks.GLOWSTONE_PATH.get());
        var pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 2, 3));
        h.setBlock(6, 1, 6, com.fimkov.witchsatelier.registry.ModBlocks.DRAGON_TEACUP.get());
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        h.useBlock(new BlockPos(6, 1, 6), player);
        h.setBlock(8, 1, 3, com.fimkov.witchsatelier.registry.ModBlocks.SEALCHAIR.get().defaultBlockState());
        h.startSequence()
                .thenExecuteAfter(10, () -> {
                    if (!h.getBlockState(new BlockPos(3, 1, 3)).getValue(com.fimkov.witchsatelier.block.GlowstonePathBlock.LIT)) h.fail("the path should glow under a step");
                    if (!h.getBlockState(new BlockPos(6, 1, 6)).getValue(com.fimkov.witchsatelier.block.DragonTeacupBlock.DANCING)) h.fail("the tea dragon should rise");
                    // sitting in the chair block wakes it: the block gets up and carries its rider
                    h.useBlock(new BlockPos(8, 1, 3), player);
                    if (!(player.getVehicle() instanceof com.fimkov.witchsatelier.entity.SealchairEntity chair) || chair.getControllingPassenger() != player)
                        h.fail("the sealchair should get up and carry its rider");
                    else if (!h.getBlockState(new BlockPos(8, 1, 3)).isAir()) h.fail("the chair should leave its spot when it gets up");
                    pig.discard();
                })
                .thenExecuteAfter(60, () -> {
                    if (h.getBlockState(new BlockPos(3, 1, 3)).getValue(com.fimkov.witchsatelier.block.GlowstonePathBlock.LIT)) h.fail("the path should go dark again");
                    player.stopRiding();
                })
                .thenExecuteAfter(3, () -> {
                    // let go of, it settles back into a block where it stopped
                    if (!h.getLevel().getEntitiesOfClass(com.fimkov.witchsatelier.entity.SealchairEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(8, 1, 3))).inflate(4)).isEmpty())
                        h.fail("an empty sealchair should not stay up and about");
                    boolean block = false;
                    for (BlockPos p : BlockPos.betweenClosed(new BlockPos(5, 0, 0), new BlockPos(10, 3, 6)))
                        block |= h.getBlockState(p).is(com.fimkov.witchsatelier.registry.ModBlocks.SEALCHAIR.get());
                    if (!block) h.fail("the sealchair should settle back into a block");
                })
                .thenSucceed();
    }

    /** The witches' places are real structures now: /place structure raises each, chest and all. */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void witchPlacesArePlaceableStructures(GameTestHelper h) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var level = h.getLevel();
        var server = level.getServer();
        var src = server.createCommandSourceStack().withLevel(level).withSuppressedOutput().withPermission(4);
        String[] kinds = {"library_ruin"};
        BlockPos[] at = new BlockPos[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            at[i] = h.absolutePos(new BlockPos(5, 1, 5)).offset(200 + i * 64, 0, 0);
            BlockPos a = at[i];
            // the command only builds where the world is loaded
            for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) level.getChunk((a.getX() >> 4) + cx, (a.getZ() >> 4) + cz);
            int ok = server.getCommands().getDispatcher().execute("place structure witchsatelier:" + kinds[i] + " " + a.getX() + " " + a.getY() + " " + a.getZ(), src);
            if (ok <= 0) h.fail("/place structure witchsatelier:" + kinds[i] + " failed");
        }
        h.startSequence()
                .thenExecuteAfter(5, () -> {
                    for (int i = 0; i < kinds.length; i++) {
                        boolean chest = false;
                        for (BlockPos p : BlockPos.betweenClosed(at[i].offset(-14, -10, -14), at[i].offset(14, 30, 14)))
                            if (level.getBlockState(p).is(Blocks.CHEST)) { chest = true; break; }
                        if (!chest) h.fail(kinds[i] + " should stand there with its chest after /place structure");
                    }
                })
                .thenSucceed();
    }

    /** Our features added to biomes must not make the feature order cyclic (that crashes chunk generation). */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void worldgenFeatureOrderIsSound(GameTestHelper h) {
        var biomes = h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        java.util.List<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> all = new java.util.ArrayList<>(biomes.holders().toList());
        try {
            net.minecraft.world.level.biome.FeatureSorter.buildFeaturesPerStep(all, b -> b.value().getGenerationSettings().features(), true);
        } catch (IllegalStateException ex) {
            h.fail("feature order cycle: " + ex.getMessage());
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void tentStaffRaisesATent(GameTestHelper h) {
        floor(h);
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) h.setBlock(x, 0, z, Blocks.DIRT);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        var staff = new net.minecraft.world.item.ItemStack(com.fimkov.witchsatelier.registry.ModItems.TENT_STAFF.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, staff);
        BlockPos g = h.absolutePos(new BlockPos(5, 0, 5));
        var ctx = new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(g).add(0, 0.5, 0), Direction.UP, g, false));
        staff.useOn(ctx);
        int earth = 0;
        for (BlockPos p : BlockPos.betweenClosed(g.offset(-4, 1, -4), g.offset(4, 4, 4))) if (h.getLevel().getBlockState(p).is(Blocks.DIRT)) earth++;
        if (earth < 30) h.fail("the tent should rise around the staff, got " + earth + " blocks");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void twoKindsOfShapingSignsWorkTogether(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(c, 1.6).ring(0, 362).sigil(Sigil.FIRE, 0.9)
                .signs(SignType.COLUMN, 2, Orientation.OUTWARD, 0.45, 45).signs(SignType.BOLT, 2, Orientation.OUTWARD, 0.45, 135).strokes, null);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    var s = spellsOn(h, plane);
                    if (s.size() != 1) h.fail("one glyph, one spell: " + s.size());
                    var p = s.get(0).program;
                    var forms = new java.util.HashSet<SpellProgram.Form>();
                    forms.add(p.form());
                    for (var l : p.layers()) forms.add(l.form());
                    if (!forms.contains(SpellProgram.Form.BEAM) || !forms.contains(SpellProgram.Form.BOLTS))
                        h.fail("Columns and Bolts in one ring should give a pillar and a volley: " + p.summary() + " layers " + p.layers().size());
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void treadHoldsTheSpellUntilSomethingStepsOnIt(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(c, 1.4).ring(0, 362).sigil(Sigil.FIRE, 0.8)
                .signs(SignType.COLUMN, 3, Orientation.OUTWARD, 0.4, 90).signs(SignType.TREAD, 1, Orientation.OUTWARD, 0.4, 270).strokes, null);
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 1));
        h.startSequence()
                .thenExecuteAfter(20, () -> {
                    var s = spellsOn(h, plane);
                    if (s.size() != 1 || !s.get(0).program.has(SpellProgram.TREAD)) h.fail("the snare should be set: " + s.size());
                    if (pig.isOnFire()) h.fail("nothing has stepped on it yet");
                    pig.teleportTo(h.absoluteVec(new Vec3(5.5, 1, 5.5)).x, h.absoluteVec(new Vec3(5.5, 1, 5.5)).y, h.absoluteVec(new Vec3(5.5, 1, 5.5)).z);
                })
                .thenWaitUntil(() -> { if (!pig.isOnFire()) h.fail("stepping on the snare should spring its pillar"); })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void glyphsJoinedByInkWakeTogether(GameTestHelper h) {
        floor(h);
        Object[] ta = top(h, new BlockPos(2, 0, 5)), tb = top(h, new BlockPos(8, 0, 5));
        SurfacePlane plane = (SurfacePlane) ta[0];
        Pt a = (Pt) ta[1], b = (Pt) tb[1];
        var level = h.getLevel();
        // the second glyph is left open; a line of ink runs from its rim to the first one's
        GlyphService.inscribe(level, plane, new GlyphSketch(b, 1.0).ring(40, 320).sigil(Sigil.LIGHT, 0.6).strokes, null);
        Vec3 wa = Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 0, 5))).add(1.0, 0.5, 0), wb = Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 0, 5))).add(-1.0, 0.5, 0);
        Pt la = plane.toUV(wa), lb = plane.toUV(wb);
        double[] link = new double[42];
        for (int i = 0; i <= 20; i++) { link[i * 2] = lb.x() + (la.x() - lb.x()) * i / 20; link[i * 2 + 1] = lb.y() + (la.y() - lb.y()) * i / 20; }
        GlyphService.inscribe(level, plane, List.of(link), null);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    Vec3 mid0 = h.absoluteVec(new Vec3(5.5, 1, 5.5));
                    if (!GlyphService.activeSpells(level).stream().filter(s -> plane.equals(s.plane) && s.program.origin().distanceTo(mid0) < 6).toList().isEmpty())
                        h.fail("nothing is closed yet");
                    GlyphService.inscribe(level, plane, new GlyphSketch(a, 1.0).ring(0, 362).sigil(Sigil.LIGHT, 0.6).strokes, null);
                })
                .thenExecuteAfter(2, () -> {
                    Vec3 mid = h.absoluteVec(new Vec3(5.5, 1, 5.5));
                    var s = GlyphService.activeSpells(level).stream().filter(sp -> plane.equals(sp.plane) && sp.program.origin().distanceTo(mid) < 6).toList();
                    if (s.size() != 2) h.fail("closing one glyph should wake the one joined to it: " + s.size() + " spells");
                })
                .thenSucceed();
    }

    /**
     * A glyph drawn in two halves a block apart: the right half is on slime blocks, which a piston pushes over to the
     * left half. The ink rides along, the ring closes, the glyph wakes; nothing is left where the slime was.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pistonsCarryInkAndJoinAGlyph(GameTestHelper h) {
        for (int z = 4; z <= 5; z++) {
            h.setBlock(4, 1, z, Blocks.STONE);
            h.setBlock(5, 1, z, Blocks.STONE);
            h.setBlock(7, 1, z, Blocks.SLIME_BLOCK);
        }
        var level = h.getLevel();
        SurfacePlane plane = SurfacePlane.of(h.absolutePos(new BlockPos(4, 1, 4)), Direction.UP);
        Pt c = plane.toUV(h.absoluteVec(new Vec3(5.5, 2, 5.0)));
        double cut = plane.toUV(h.absoluteVec(new Vec3(6.0, 2, 5.0))).x();
        // what lies past x = 6 is drawn one block further out, on the slime
        List<double[]> halves = new java.util.ArrayList<>();
        for (double[] st : new GlyphSketch(c, 0.95).ring(0, 362).sigil(Sigil.LIGHT, 0.45).strokes) {
            List<Double> cur = new java.util.ArrayList<>();
            for (int i = 0; i <= st.length / 2; i++) {
                boolean far = i < st.length / 2 && st[i * 2] > cut;
                if (i == st.length / 2 || (i > 0 && far != (st[i * 2 - 2] > cut))) {
                    if (cur.size() >= 4) halves.add(cur.stream().mapToDouble(Double::doubleValue).toArray());
                    cur.clear();
                }
                if (i == st.length / 2) break;
                cur.add(st[i * 2] + (far ? 1 : 0));
                cur.add(st[i * 2 + 1]);
            }
        }
        GlyphService.inscribe(level, plane, halves, null);
        Vec3 mid = h.absoluteVec(new Vec3(5.5, 2, 5.0));
        var data = com.fimkov.witchsatelier.glyph.storage.GlyphLevelData.get(level);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    if (GlyphService.activeSpells(level).stream().anyMatch(s -> plane.equals(s.plane) && s.program.origin().distanceTo(mid) < 2))
                        h.fail("the halves are a block apart: nothing should wake yet");
                    h.setBlock(8, 1, 4, Blocks.PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, Direction.WEST));
                    h.setBlock(9, 1, 4, Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(8, () -> {
                    if (!h.getBlockState(new BlockPos(6, 1, 4)).is(Blocks.SLIME_BLOCK)) h.fail("the piston should have pushed the slime");
                    double slimeU = plane.toUV(h.absoluteVec(new Vec3(7.0, 2, 5.0))).x();
                    for (var s : data.near(plane, c.x(), c.y(), 1)) {
                        if (Math.abs(s.v(0) - c.y()) > 1.2) continue;
                        for (int i = 0; i < s.size(); i++) if (s.u(i) > slimeU + 0.02 && s.u(i) < slimeU + 1) h.fail("ink was left behind where the slime was");
                    }
                    if (GlyphService.activeSpells(level).stream().noneMatch(s -> plane.equals(s.plane) && s.program.origin().distanceTo(mid) < 2))
                        h.fail("the halves pushed together should close the ring and wake the glyph");
                })
                .thenSucceed();
    }

    /**
     * Tracing a pinned grimoire page: the ring, traced first, closes but waits for the rest; the copy, made with a
     * shaking hand, then casts as the page's glyph.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aCopyTracedOverAPinnedPageCastsAsThePage(GameTestHelper h) {
        floor(h);
        var level = h.getLevel();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        net.minecraft.world.item.ItemStack book = com.fimkov.witchsatelier.glyph.page.GlyphLibrary.grimoireBook(net.minecraft.util.RandomSource.create(5), 4, "pillar_of_flame");
        var gp = book.get(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get());
        book.set(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get(), gp.withCurrent(2));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(com.fimkov.witchsatelier.registry.ModItems.INK_PEN.get()));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, book);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        double turn = 1.1, scale = 2.0, rot = turn - Math.PI / 2, cos = Math.cos(rot), sin = Math.sin(rot);
        com.fimkov.witchsatelier.glyph.Tracing.pin(player, new com.fimkov.witchsatelier.glyph.Tracing.Pin(plane, c.x(), c.y(), turn, scale));
        // the page laid out as the guide is, each line followed with a wobble of up to 0.07 blocks
        List<double[]> copy = new java.util.ArrayList<>();
        int k = 0;
        for (var st : book.get(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get()).page(2).strokes()) {
            float[] pts = st.pts();
            double[] uv = new double[pts.length];
            for (int i = 0; i + 1 < pts.length; i += 2, k++) {
                double x = pts[i] * scale, y = pts[i + 1] * scale;
                uv[i] = c.x() + x * cos - y * sin + 0.07 * Math.sin(k * 0.21);
                uv[i + 1] = c.y() + x * sin + y * cos + 0.07 * Math.cos(k * 0.17);
            }
            copy.add(uv);
        }
        Vec3 mid = Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 1, 5)));
        GlyphService.inscribe(level, plane, List.of(copy.get(0)), com.fimkov.witchsatelier.glyph.ink.InkType.CONJURING, player);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    var data = com.fimkov.witchsatelier.glyph.storage.GlyphLevelData.get(level);
                    for (var st : data.near(plane, c.x(), c.y(), 1)) {
                        if (st.centroid().dist(c) < 0.5 && st.state() == com.fimkov.witchsatelier.glyph.surface.StrokeState.SPENT) h.fail("the traced ring went off before the copy was done");
                    }
                    if (!spellsOn(h, plane).isEmpty()) h.fail("nothing should wake while the copy is unfinished");
                    GlyphService.inscribe(level, plane, copy.subList(1, copy.size()), com.fimkov.witchsatelier.glyph.ink.InkType.CONJURING, player);
                })
                .thenExecuteAfter(3, () -> {
                    var s = spellsOn(h, plane);
                    if (s.isEmpty()) h.fail("the finished copy should wake");
                    else if (s.get(0).program.element() != Sigil.FIRE || s.get(0).program.form() != SpellProgram.Form.BEAM)
                        h.fail("a copy of Pillar of Flame should be a pillar of flame, got " + s.get(0).program.summary());
                })
                .thenSucceed();
    }

    /** A grimoire page drawn with the Bird sign as it once was (a seed with a beak), the way old books still hold it. */
    private static com.fimkov.witchsatelier.glyph.page.GlyphPages.Page oldBirdsPage() {
        List<com.fimkov.witchsatelier.glyph.page.GlyphPages.PageStroke> out = new java.util.ArrayList<>();
        var ink = com.fimkov.witchsatelier.glyph.ink.InkType.LASTING;
        java.util.function.Consumer<List<Pt>> add = pts -> {
            float[] f = new float[pts.size() * 2];
            for (int i = 0; i < pts.size(); i++) { f[i * 2] = (float) pts.get(i).x(); f[i * 2 + 1] = (float) pts.get(i).y(); }
            out.add(new com.fimkov.witchsatelier.glyph.page.GlyphPages.PageStroke(f, ink));
        };
        List<Pt> ring = new java.util.ArrayList<>();
        for (int i = 0; i <= 180; i++) { double a = Math.toRadians(i * 2.0); ring.add(new Pt(Math.cos(a) * 0.85, Math.sin(a) * 0.85)); }
        add.accept(ring);
        var light = com.fimkov.witchsatelier.glyph.recognize.Templates.SIGILS.stream().filter(t -> t.sigil() == Sigil.LIGHT).findFirst().orElseThrow();
        for (var st : light.strokes()) add.accept(st.stream().map(q -> q.scale(0.55 / 2)).toList());
        var old = com.fimkov.witchsatelier.glyph.recognize.Shapes.of().arc(0.3, 0, 0.75, 130, 230).arc(-0.3, 0, 0.75, -50, 50).poly(-0.25, 0.7, 0, 0.95, 0.25, 0.7);
        for (double pos : new double[]{Math.PI / 2, -Math.PI / 2}) {
            Pt at = new Pt(Math.cos(pos) * 0.85 * 0.62, Math.sin(pos) * 0.85 * 0.62);
            for (var st : old.build()) add.accept(st.stream().map(q -> q.rotate(pos - Math.PI / 2).scale(0.3 / 2).plus(at)).toList());
        }
        return new com.fimkov.witchsatelier.glyph.page.GlyphPages.Page(List.copyOf(out));
    }

    /**
     * An old grimoire whose Birds of Light page still shows the Bird sign as it was: traced over, the copy casts as
     * the page's glyph all the same; turned to, the page is drawn again as the sign is now.
     */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void anOldPageTracedCastsAsItsRecipeAndIsRedrawn(GameTestHelper h) {
        floor(h);
        var level = h.getLevel();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        net.minecraft.world.item.ItemStack book = com.fimkov.witchsatelier.glyph.page.GlyphLibrary.grimoireBook(net.minecraft.util.RandomSource.create(5), 4, "birds_of_light");
        var comp = com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get();
        book.set(comp, book.get(comp).withPage(2, oldBirdsPage()).withCurrent(2));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(com.fimkov.witchsatelier.registry.ModItems.INK_PEN.get()));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, book);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        double turn = Math.PI / 2, scale = 2.2;
        com.fimkov.witchsatelier.glyph.Tracing.pin(player, new com.fimkov.witchsatelier.glyph.Tracing.Pin(plane, c.x(), c.y(), turn, scale));
        // the witch traces what the old page shows, with a little wobble
        List<double[]> copy = new java.util.ArrayList<>();
        int k = 0;
        for (var st : oldBirdsPage().strokes()) {
            float[] pts = st.pts();
            double[] uv = new double[pts.length];
            for (int i = 0; i + 1 < pts.length; i += 2, k++) {
                uv[i] = c.x() + pts[i] * scale + 0.04 * Math.sin(k * 0.3);
                uv[i + 1] = c.y() + pts[i + 1] * scale + 0.04 * Math.cos(k * 0.2);
            }
            copy.add(uv);
        }
        GlyphService.inscribe(level, plane, copy.subList(1, copy.size()), com.fimkov.witchsatelier.glyph.ink.InkType.CONJURING, player);
        GlyphService.inscribe(level, plane, List.of(copy.get(0)), com.fimkov.witchsatelier.glyph.ink.InkType.CONJURING, player);
        h.startSequence()
                .thenExecuteAfter(3, () -> {
                    var s = spellsOn(h, plane);
                    if (s.isEmpty()) h.fail("a traced copy of the old page should wake");
                    else if (s.get(0).program.form() != SpellProgram.Form.BIRD) h.fail("it should be Birds of Light, got " + s.get(0).program.summary());
                    // turning to the page draws it again as the sign is now
                    com.fimkov.witchsatelier.glyph.page.PageService.handleTurn(player, net.minecraft.world.InteractionHand.OFF_HAND, 2);
                    var now = player.getOffhandItem().get(comp).page(2);
                    var r = com.fimkov.witchsatelier.glyph.recognize.GlyphAnalyzer.analyze(com.fimkov.witchsatelier.glyph.page.GlyphLibrary.strokesOf(now), com.fimkov.witchsatelier.glyph.page.PageService.PAGE_INK);
                    if (!com.fimkov.witchsatelier.glyph.page.GlyphLibrary.readsAs(com.fimkov.witchsatelier.glyph.page.GlyphLibrary.byName("birds_of_light"), r))
                        h.fail("the old page should be drawn again so that it reads as Birds of Light: " + r.describe());
                })
                .thenSucceed();
    }

    /** A fire glyph sets a flame over itself: the flame must not burn the very ink that keeps it lit. */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void aFireGlyphDoesNotBurnItsOwnInk(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(c, 1.2).ring(0, 362).sigil(Sigil.FIRE, 0.7).strokes, null);
        h.startSequence()
                .thenExecuteAfter(5, () -> {
                    if (spellsOn(h, plane).isEmpty()) h.fail("a fire glyph should wake");
                })
                .thenExecuteAfter(50, () -> {
                    // two sweeps of the ink have passed with the flame standing on the glyph
                    if (spellsOn(h, plane).isEmpty()) h.fail("the fire glyph went out: its own flame burnt its ink");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void earthRaisesWallsAndPlatforms(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(c, 1.4).ring(0, 362).sigil(Sigil.EARTH, 0.8)
                .signs(SignType.DISPERSION, 4, Orientation.OUTWARD, 0.4, 45).strokes, null);
        h.startSequence()
                .thenExecuteAfter(30, () -> {
                    int wall = 0;
                    for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) {
                        double d = Math.hypot(x - 5, z - 5);
                        if (d >= 1.5 && !h.getBlockState(new BlockPos(x, 1, z)).isAir()) wall++;
                    }
                    if (wall < 8) h.fail("earth with Dispersion should heave up a wall round the glyph, found " + wall + " blocks");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 260)
    public static void earthLevitationLiftsAPlatform(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(5, 1, 5));
        double y0 = pig.getY();
        GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(c, 1.4).ring(0, 362).sigil(Sigil.EARTH, 0.8)
                .signs(SignType.LEVITATION, 4, Orientation.OUTWARD, 0.4, 45).strokes, null);
        h.startSequence()
                .thenExecuteAfter(30, () -> {
                    if (pig.getY() < y0 + 2) h.fail("the rising earth should carry the pig up, it is at +" + (pig.getY() - y0));
                    if (h.getBlockState(new BlockPos(5, 1, 5)).isAir()) h.fail("there should be earth under it");
                })
                .thenSucceed();
    }
}
