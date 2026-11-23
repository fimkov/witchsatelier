package com.fimkov.witchsatelier.gametest;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.block.DragonTeacupBlock;
import com.fimkov.witchsatelier.block.GlowstonePathBlock;
import com.fimkov.witchsatelier.block.PetrifiedBlock;
import com.fimkov.witchsatelier.block.PlantedRaincleaverBlock;
import com.fimkov.witchsatelier.block.StatueBlock;
import com.fimkov.witchsatelier.block.sealed.BeastWardingTowerBlock;
import com.fimkov.witchsatelier.block.sealed.WashingBarrelBlock;
import com.fimkov.witchsatelier.entity.BrimhatEntity;
import com.fimkov.witchsatelier.entity.GlyphArrow;
import com.fimkov.witchsatelier.entity.KnightMoralisEntity;
import com.fimkov.witchsatelier.entity.SealchairEntity;
import com.fimkov.witchsatelier.entity.Taboo;
import com.fimkov.witchsatelier.entity.Witnesses;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.InkWear;
import com.fimkov.witchsatelier.glyph.Tracing;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.ink.LoadedInk;
import com.fimkov.witchsatelier.glyph.model.Orientation;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import com.fimkov.witchsatelier.glyph.page.PageKind;
import com.fimkov.witchsatelier.glyph.page.PageService;
import com.fimkov.witchsatelier.glyph.recognize.GlyphAnalyzer;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.recognize.Shapes;
import com.fimkov.witchsatelier.glyph.recognize.Templates;
import com.fimkov.witchsatelier.glyph.storage.GlyphLevelData;
import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import com.fimkov.witchsatelier.glyph.surface.StrokeState;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.item.InkPenItem;
import com.fimkov.witchsatelier.item.InkwellItem;
import com.fimkov.witchsatelier.recipe.GlyphArrowRecipe;
import com.fimkov.witchsatelier.recipe.WitchGrimoireCloning;
import com.fimkov.witchsatelier.registry.ModBlocks;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.registry.ModEntities;
import com.fimkov.witchsatelier.registry.ModItems;
import com.fimkov.witchsatelier.spell.ActiveSpell;
import com.fimkov.witchsatelier.spell.GlyphFrame;
import com.fimkov.witchsatelier.spell.SpellCompiler;
import com.fimkov.witchsatelier.spell.SpellProgram;
import com.fimkov.witchsatelier.windowway.PortalFrame;
import com.fimkov.witchsatelier.windowway.WindowwayService;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@GameTestHolder(WitchsAtelier.MODID)
@PrefixGameTestTemplate(false)
public final class GlyphGameTests {
    private static void floor(GameTestHelper h) {
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    @SuppressWarnings("removal")
    private static ServerPlayer mockPlayer(GameTestHelper h) {
        return h.makeMockServerPlayerInLevel();
    }

    @SuppressWarnings("removal")
    private static ServerPlayer survivor(GameTestHelper h) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-survivor"), false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() { return false; }

            @Override
            public boolean isCreative() { return false; }
        };
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    private static List<ActiveSpell> spellsOn(GameTestHelper h, SurfacePlane plane) {
        Vec3 mid = h.absoluteVec(new Vec3(5.5, 1, 5.5));
        return GlyphService.activeSpells(h.getLevel()).stream()
                .filter(s -> plane.equals(s.plane) && s.program.origin().distanceTo(mid) < 4).toList();
    }

    private static Object[] top(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        SurfacePlane plane = SurfacePlane.of(abs, Direction.UP);
        Pt c = plane.toUV(Vec3.atCenterOf(abs).add(0, 0.5, 0));
        return new Object[]{plane, c};
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void plantedRaincleaverKeepsTheWaterBack(GameTestHelper h) {
        floor(h);
        for (int x = 1; x <= 9; x++) for (int z = 1; z <= 9; z++) for (int y = 1; y <= 3; y++) h.setBlock(x, y, z, Blocks.WATER);
        BlockPos at = new BlockPos(5, 1, 5);
        ItemStack sword = new ItemStack(ModItems.RAINCLEAVER.get());
        if (!PlantedRaincleaverBlock.plant(h.getLevel(), h.absolutePos(at), sword)) h.fail("it should plant in water");
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

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void footstepsWearAGlyphAwayLittleByLittle(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        List<InkStroke> ink = GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(c, 1.6).ring(0, 362)
                .sigil(Sigil.FIRE, 1.0).signs(SignType.RADIAL, 4, Orientation.OUTWARD, 0.5, 45).strokes, null);
        var data = GlyphLevelData.get(h.getLevel());
        long[] spell = {-1};
        ServerPlayer walker = mockPlayer(h);
        Vec3 onRing = plane.toWorld(c.x() + 1.6, c.y(), 0);
        IntSupplier mostWorn = () -> {
            int most = 0;
            for (var st : ink) {
                byte[] w = data.wear(st.id());
                if (w != null) for (byte b : w) most = Math.max(most, InkWear.amount(b));
            }
            return most;
        };
        Runnable walk = () -> {
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
                        h.fail("not worn enough yet: " + mostWorn.getAsInt() + " at tick " + h.getLevel().getGameTime());
                    }
                })
                .thenExecute(() -> {
                    List<ActiveSpell> s = spellsOn(h, plane);
                    if (s.size() != 1 || s.get(0).id != spell[0]) h.fail("the spell should keep going while the ink only thins");
                })
                .thenWaitUntil(() -> {
                    walk.run();
                    if (!spellsOn(h, plane).isEmpty()) h.fail("the ring should wear through and the spell stop");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aNickInASignKeepsTheSpellGoing(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        List<InkStroke> ink = GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(c, 1.6).ring(0, 362)
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
                    GlyphService.applyCuts(h.getLevel(), Map.of(sign.id(), cut));
                })
                .thenExecuteAfter(2, () -> {
                    List<ActiveSpell> s = spellsOn(h, plane);
                    if (s.size() != 1 || s.get(0).id != spell[0]) h.fail("the spell should go on, got " + s.stream().map(x -> x.id).toList() + " (was " + spell[0] + ")");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void anAppleInARingIsGoneWithoutASpell(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        GlyphSketch g = new GlyphSketch(c, 1.6).ring(0, 362).sigil(Sigil.APPLE, 1.0);
        GlyphService.inscribe(h.getLevel(), plane, g.strokes, null);
        var data = GlyphLevelData.get(h.getLevel());
        h.startSequence()
                .thenExecute(() -> {
                    if (!spellsOn(h, plane).isEmpty()) h.fail("an apple wakes no spell");

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
                    ServerPlayer player = mockPlayer(h);
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

        GlyphService.inscribe(h.getLevel(), plane, List.of(new double[]{c.x() - 2.4, c.y(), c.x() - 1.2, c.y(), c.x(), c.y(), c.x() + 1.2, c.y(), c.x() + 2.4, c.y()}), null);
        var data = GlyphLevelData.get(h.getLevel());
        h.startSequence()
                .thenExecute(() -> {
                    ServerPlayer player = mockPlayer(h);
                    player.gameMode.destroyBlock(h.absolutePos(new BlockPos(5, 0, 5)));
                })
                .thenExecuteAfter(1, () -> {
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
        ServerPlayer player = mockPlayer(h);
        var quire = new ItemStack(ModItems.PALM_QUIRE.get());
        GlyphSketch g = new GlyphSketch(new Pt(0, 0), 0.85).ring(25, 335).sigil(Sigil.FIRE, 0.55)
                .signs(SignType.COLUMN, 4, Orientation.OUTWARD, 0.28, 45);
        var strokes = new ArrayList<GlyphPages.PageStroke>();
        for (double[] d : g.strokes) {
            float[] f = new float[d.length];
            for (int i = 0; i < d.length; i++) f[i] = (float) d[i];
            strokes.add(new GlyphPages.PageStroke(f, InkType.CONJURING));
        }
        var pages = GlyphPages.blank(16).withPage(0, new GlyphPages.Page(List.copyOf(strokes)));
        quire.set(ModComponents.GLYPH_PAGES.get(), pages);
        player.setItemInHand(InteractionHand.OFF_HAND, quire);

        h.startSequence()
                .thenExecute(() -> {
                    if (GlyphService.activeSpells(h.getLevel()).stream().anyMatch(s -> player.getUUID().equals(s.caster)))
                        h.fail("an open ring must not cast");
                    PageService.handleQuickClose(player);
                })
                .thenExecuteAfter(1, () -> {
                    var mine = GlyphService.activeSpells(h.getLevel()).stream().filter(s -> player.getUUID().equals(s.caster)).toList();
                    if (mine.size() != 1) h.fail("quick close should cast one spell, got " + mine.size());
                    if (!mine.get(0).handheld || mine.get(0).program.element() != Sigil.FIRE) h.fail("cast " + mine.get(0).program.summary());
                    var after = player.getOffhandItem().get(ModComponents.GLYPH_PAGES.get());
                    if (after == null || !after.page(0).isEmpty() || after.current() != 1) h.fail("the used page should be burnt and turned");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void inkIsBrewedFromSilverwood(GameTestHelper h) {
        var brewing = h.getLevel().potionBrewing();
        var water = PotionContents.createItemStack(Items.POTION,
                Potions.WATER);
        var twig = new ItemStack(ModItems.BLACKENED_TWIG.get());
        var woodcruor = brewing.mix(twig, water);
        if (!woodcruor.is(ModItems.WOODCRUOR.get())) h.fail("water + twig gave " + woodcruor);
        var ink = brewing.mix(new ItemStack(Items.GLOWSTONE_DUST), woodcruor);
        if (!ink.is(ModItems.CONJURING_INK.get())) h.fail("woodcruor + glowstone gave " + ink);
        var proof = brewing.mix(new ItemStack(Items.PRISMARINE_SHARD), ink);
        if (!proof.is(ModItems.WATERPROOF_INK.get())) h.fail("conjuring + prismarine gave " + proof);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void drawingDipsIntoTheInkwellInTheOtherHand(GameTestHelper h) {
        floor(h);
        ServerPlayer player = survivor(h);
        player.teleportTo(h.absoluteVec(new Vec3(5.5, 1, 3.5)).x, h.absoluteVec(new Vec3(5.5, 1, 3.5)).y, h.absoluteVec(new Vec3(5.5, 1, 3.5)).z);
        var pen = new ItemStack(ModItems.INK_PEN.get());
        var well = new ItemStack(ModItems.INKWELL.get());
        InkwellItem.setContents(well, new LoadedInk(InkType.WATERPROOF, 2048));
        player.setItemInHand(InteractionHand.MAIN_HAND, pen);
        player.setItemInHand(InteractionHand.OFF_HAND, well);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        double[] line = new double[42];
        for (int i = 0; i < 21; i++) { line[i * 2] = c.x() - 1 + i * 0.1; line[i * 2 + 1] = c.y(); }
        GlyphService.handleDraw(player, plane, line);
        var data = GlyphLevelData.get(h.getLevel());
        var drawn = data.near(plane, c.x(), c.y(), 1).stream().filter(s -> Math.abs(s.v(0) - c.y()) < 0.01 && s.ink() == InkType.WATERPROOF).toList();
        if (drawn.size() != 1) h.fail("expected one waterproof line, got " + drawn.size());
        var left = InkwellItem.contents(player.getOffhandItem());
        var nib = InkPenItem.loaded(player.getMainHandItem());

        int full = InkPenItem.NIB_CAPACITY;
        if (left == null || left.amount() != 2048 - 8 - full) h.fail("inkwell should hold " + (2048 - 8 - full) + ", holds " + (left == null ? 0 : left.amount()));
        if (nib == null || nib.amount() != full || nib.type() != InkType.WATERPROOF) h.fail("nib " + nib);
        h.succeed();
    }

    private static GlyphPages.Page page(GlyphSketch g) {
        var strokes = new ArrayList<GlyphPages.PageStroke>();
        for (double[] d : g.strokes) {
            float[] f = new float[d.length];
            for (int i = 0; i < d.length; i++) f[i] = (float) d[i];
            strokes.add(new GlyphPages.PageStroke(f, InkType.CONJURING));
        }
        return new GlyphPages.Page(List.copyOf(strokes));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bootsGlyphFliesOnHeelClick(GameTestHelper h) {
        floor(h);
        ServerPlayer player = mockPlayer(h);
        var boots = new ItemStack(Items.LEATHER_BOOTS);
        GlyphSketch g = new GlyphSketch(new Pt(0, 0), 0.85).ring(0, 360).sigil(Sigil.WIND, 0.55)
                .signs(SignType.LEVITATION, 4, Orientation.OUTWARD, 0.28, 45);

        var halves = new ArrayList<GlyphPages.PageStroke>();
        for (double[] d : g.strokes) {
            float[] f = new float[d.length];
            for (int i = 0; i < d.length; i++) f[i] = (float) d[i];
            for (float[] part : PageKind.splitForSoles(f, 0.82f))
                halves.add(new GlyphPages.PageStroke(part, InkType.CONJURING));
        }
        boots.set(ModComponents.GLYPH_PAGES.get(), GlyphPages.blank(1)
                .withPage(0, new GlyphPages.Page(List.copyOf(halves))));
        player.setItemSlot(EquipmentSlot.FEET, boots);
        if (PageKind.of(boots) != PageKind.BOOTS) h.fail("boots are not a canvas");

        h.startSequence()
                .thenExecute(() -> PageService.handleBootsJoin(player))
                .thenExecuteAfter(3, () -> {
                    var worn = GlyphService.wornSpell(h.getLevel(), player.getUUID());
                    if (worn == null) h.fail("heel click should start the boots spell");
                    if (worn.program.form() != SpellProgram.Form.LIFT) h.fail("cast " + worn.program.summary());
                    if (worn.ticksLeft < 20 * 60 * 60) h.fail("shoe flight should last until the heels click again, not " + worn.ticksLeft + " ticks");
                    if (player.fallDistance > 0) h.fail("flying, the wearer must not count as falling");
                    var still = player.getItemBySlot(EquipmentSlot.FEET).get(ModComponents.GLYPH_PAGES.get());
                    if (still == null || still.page(0).isEmpty()) h.fail("the glyph must stay on the boots");
                    PageService.handleBootsJoin(player);
                })
                .thenExecuteAfter(1, () -> {
                    if (GlyphService.wornSpell(h.getLevel(), player.getUUID()) != null) h.fail("a second heel click should stop it");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void waterWashesTheGlyphOffShoes(GameTestHelper h) {
        floor(h);
        for (int x = 3; x <= 7; x++) for (int z = 3; z <= 7; z++) h.setBlock(x, 1, z, Blocks.WATER);
        ServerPlayer player = mockPlayer(h);
        var boots = new ItemStack(Items.LEATHER_BOOTS);
        GlyphSketch g = new GlyphSketch(new Pt(0, 0), 0.85).ring(0, 360).sigil(Sigil.WIND, 0.55)
                .signs(SignType.LEVITATION, 4, Orientation.OUTWARD, 0.28, 45);
        boots.set(ModComponents.GLYPH_PAGES.get(), GlyphPages.blank(1).withPage(0, page(g)));
        player.setItemSlot(EquipmentSlot.FEET, boots);
        h.startSequence()
                .thenExecute(() -> PageService.handleBootsJoin(player))
                .thenExecute(() -> {
                    BlockPos w = h.absolutePos(new BlockPos(5, 1, 5));
                    player.teleportTo(w.getX() + 0.5, w.getY(), w.getZ() + 0.5);
                })

                .thenExecuteFor(12, player::doTick)
                .thenWaitUntil(() -> {
                    var still = player.getItemBySlot(EquipmentSlot.FEET).get(ModComponents.GLYPH_PAGES.get());
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
        ServerPlayer player = mockPlayer(h);
        var quire = new ItemStack(ModItems.PALM_QUIRE.get());
        GlyphSketch g = new GlyphSketch(new Pt(0, 0), 0.85).ring(25, 335).sigil(Sigil.LIGHT, 0.55)
                .signs(SignType.BIRD, 2, Orientation.OUTWARD, 0.3, 90);
        quire.set(ModComponents.GLYPH_PAGES.get(), GlyphPages.blank(16).withPage(0, page(g)));
        player.setItemInHand(InteractionHand.OFF_HAND, quire);
        Vec3[] start = new Vec3[1];

        h.startSequence()
                .thenExecute(() -> PageService.handleQuickClose(player))
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

    @GameTest(template = "empty", timeoutTicks = 60, batch = "windowway_small")
    public static void smallWindowwaysStillOpenAWayThrough(GameTestHelper h) {
        floor(h);
        for (int y = 1; y <= 4; y++) for (int z = 2; z <= 8; z++) { h.setBlock(1, y, z, Blocks.STONE); h.setBlock(9, y, z, Blocks.STONE); }
        BlockPos a = h.absolutePos(new BlockPos(1, 2, 5)), b = h.absolutePos(new BlockPos(9, 2, 5));
        SurfacePlane pa = SurfacePlane.of(a, Direction.EAST), pb = SurfacePlane.of(b, Direction.WEST);

        Pt ca = pa.toUV(Vec3.atCenterOf(a).add(0.5, 0, 0)), cb = pb.toUV(Vec3.atCenterOf(b).add(-0.5, 0.5, 0.5));
        GlyphService.inscribe(h.getLevel(), pa, new GlyphSketch(ca, 1.3).ring(0, 362).centerSign(SignType.WINDOW, 0.75).strokes, null);
        GlyphService.inscribe(h.getLevel(), pb, new GlyphSketch(cb, 1.3).ring(0, 362).centerSign(SignType.WINDOW, 0.75).strokes, null);
        h.startSequence()
                .thenExecuteAfter(3, () -> {
                    var gap = ModBlocks.WINDOWWAY_GAP.get();
                    for (int[] q : new int[][]{{2, 5}, {1, 5}, {3, 5}, {2, 4}, {2, 6}}) h.assertBlockPresent(gap, new BlockPos(1, q[0], q[1]));
                    h.assertBlockPresent(Blocks.STONE, new BlockPos(1, 1, 4));
                    for (int[] q : new int[][]{{2, 5}, {3, 5}, {2, 6}, {3, 6}}) h.assertBlockPresent(gap, new BlockPos(9, q[0], q[1]));
                    var west = WindowwayService.frames(h.getLevel()).stream()
                            .filter(f -> f.center().distanceTo(Vec3.atCenterOf(a)) < 3).findFirst().orElseThrow();
                    if (west.link() < 0) h.fail("the two small windows should join");

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

        BlockPos a = h.absolutePos(new BlockPos(1, 2, 5)), b = h.absolutePos(new BlockPos(9, 2, 5));
        SurfacePlane pa = SurfacePlane.of(a, Direction.EAST), pb = SurfacePlane.of(b, Direction.WEST);
        Pt ca = pa.toUV(Vec3.atCenterOf(a).add(0.5, 0, 0)), cb = pb.toUV(Vec3.atCenterOf(b).add(-0.5, 0, 0));
        GlyphService.inscribe(h.getLevel(), pa, new GlyphSketch(ca, 2.0).ring(0, 362).centerSign(SignType.WINDOW, 1.1).strokes, null);
        GlyphService.inscribe(h.getLevel(), pb, new GlyphSketch(cb, 2.0).ring(0, 362).centerSign(SignType.WINDOW, 1.1).strokes, null);
        var item = new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(Items.FEATHER));
        ServerPlayer player = mockPlayer(h);

        h.startSequence()
                .thenExecuteAfter(3, () -> {
                    var mine = WindowwayService.frames(h.getLevel()).stream()
                            .filter(f -> f.center().distanceTo(Vec3.atCenterOf(a)) < 3 || f.center().distanceTo(Vec3.atCenterOf(b)) < 3).toList();
                    if (mine.size() != 2) h.fail("expected two windows, got " + mine.size() + " (" + spellsOn(h, pa).size() + " spells on A)");
                    if (mine.stream().anyMatch(f -> f.link() < 0)) h.fail("the two windows should join");
                    h.assertBlockPresent(ModBlocks.WINDOWWAY_GAP.get(), new BlockPos(1, 2, 5));
                    h.assertBlockPresent(ModBlocks.WINDOWWAY_GAP.get(), new BlockPos(1, 1, 5));

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

                    Vec3 p = h.absoluteVec(new Vec3(1.9, 1, 5.5));
                    player.moveTo(p.x, p.y, p.z, 90, 0);
                    var west = WindowwayService.frames(h.getLevel()).stream()
                            .filter(f -> f.center().distanceTo(Vec3.atCenterOf(a)) < 3).findFirst().orElseThrow();
                    WindowwayService.handleCross(player, west.id());
                    Vec3 r = player.position().subtract(h.absoluteVec(Vec3.ZERO));
                    if (r.x < 8.5 || r.x > 9.0 || Math.abs(r.z - 5.5) > 0.3 || Math.abs(r.y - 1) > 0.05) h.fail("player came out at " + r);
                    if (player.getLookAngle().x > -0.9) h.fail("player should face west, out of the window: " + player.getLookAngle());
                    for (var s : spellsOn(h, pa)) GlyphService.stop(h.getLevel(), s);
                    for (var s : spellsOn(h, pb)) GlyphService.stop(h.getLevel(), s);
                })
                .thenExecuteAfter(2, () -> {
                    h.assertBlockPresent(Blocks.STONE, new BlockPos(1, 2, 5));
                    h.assertBlockPresent(Blocks.STONE, new BlockPos(9, 2, 5));
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void windowwayMathIsAHalfTurn(GameTestHelper h) {
        var A = PortalFrame.of(1, new Vec3(3, 2.5, 5.5), new Vec3(0, 1, 0), new Vec3(1, 0, 0), 1.5, 2);
        var B = PortalFrame.of(2, new Vec3(40, 70.5, -12.5), new Vec3(0, 1, 0), new Vec3(0, 0, 1), 1.5, 1);
        if (A.u().cross(A.v()).distanceTo(A.n()) > 1e-9) h.fail("basis not right-handed");
        Vec3 through = A.center().add(-0.1, -1, 0.3);
        if (B.side(PortalFrame.mapPoint(A, B, through)) <= 0) h.fail("should come out of B's front");
        if (PortalFrame.mapDir(A, B, new Vec3(-1, 0, 0)).distanceTo(B.n()) > 1e-9) h.fail("into A = out of B");
        Vec3 p = new Vec3(4.1, 3.3, 6.0);
        if (PortalFrame.mapPoint(B, A, PortalFrame.mapPoint(A, B, p)).distanceTo(p) > 1e-9) h.fail("not invertible");
        Vec3 d = new Vec3(0.3, -0.5, 0.8).normalize();
        var r = new Vector3f((float) d.x, (float) d.y, (float) d.z).rotate(PortalFrame.rotation(A, B));
        if (PortalFrame.mapDir(A, B, d).distanceTo(new Vec3(r.x, r.y, r.z)) > 1e-4) h.fail("rotation disagrees with mapping");
        if (!WindowwayService.crossed(A, A.center().add(0.2, 0, 0), A.center().add(-0.2, 0, 0))) h.fail("crossing missed");
        if (WindowwayService.crossed(A, A.center().add(0.2, 0, 2), A.center().add(-0.2, 0, 2))) h.fail("beside the disc");
        if (WindowwayService.crossed(A, A.center().add(-0.2, 0, 0), A.center().add(0.2, 0, 0))) h.fail("out the back");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void dungeonChestsHoldGrimoiresAndPreparedPages(GameTestHelper h) {
        var level = h.getLevel();
        var key = ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.withDefaultNamespace("chests/stronghold_library"));
        var table = level.getServer().reloadableRegistries().getLootTable(key);
        int books = 0, pages = 0;
        for (int i = 0; i < 100; i++) {
            var params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, h.absoluteVec(Vec3.ZERO))
                    .create(LootContextParamSets.CHEST);
            for (var st : table.getRandomItems(params)) {
                if (st.is(ModItems.GRIMOIRE.get())) {
                    books++;
                    var gp = st.get(ModComponents.GLYPH_PAGES.get());
                    var names = st.get(ModComponents.GRIMOIRE_ENTRIES.get());
                    if (gp == null || names == null || names.size() != gp.pages().size()) h.fail("every page of a book should say what it shows: " + names);

                    for (int p = 0; p < names.size(); p++) {
                        if (names.get(p).isEmpty()) h.fail("blank page " + p + " in " + names);
                        if (GlyphLibrary.byName(names.get(p)) != null && gp.page(p).isEmpty()) h.fail("glyph " + names.get(p) + " not drawn");
                    }
                    if (GlyphLibrary.VOLUMES.stream().noneMatch(v -> v.entries().equals(names))
                            && !GlyphLibrary.RING_NOTES.entries().equals(names)
                            && !GlyphLibrary.FORBIDDEN_BOOK.entries().equals(names)) h.fail("not one of the volumes: " + names);
                    if (st.get(DataComponents.ITEM_NAME) == null) h.fail("a volume should have its own title");
                    for (int p = 0; p < names.size(); p++) {
                        if (GlyphLibrary.byName(names.get(p)) == null) continue;
                        var r = GlyphAnalyzer.analyze(PageService.pointsOf(gp.page(p)), PageService.PAGE_INK);
                        if (r.ring() == null || !r.closed()) h.fail("a grimoire's glyph should be complete: " + names.get(p));
                    }
                }
                if (st.is(ModItems.TORN_PAGE.get())) {
                    pages++;
                    var gp = st.get(ModComponents.GLYPH_PAGES.get());
                    var r = GlyphAnalyzer.analyze(PageService.pointsOf(gp.page(0)), PageService.PAGE_INK);
                    if (r.ring() == null || r.closed()) h.fail("a prepared page should have an open ring");
                }
            }
        }
        if (books < 10 || pages < 15) h.fail("too few spell finds in 100 library chests: " + books + " grimoires, " + pages + " pages");

        for (var recipe : allRecipes()) {
            var page = GlyphLibrary.draw(recipe, RandomSource.create(1), false, InkType.CONJURING);
            var r = GlyphAnalyzer.analyze(PageService.pointsOf(page), PageService.PAGE_INK);
            if (!r.closed()) h.fail(recipe.name() + " should be closed: " + r.describe());
            var inner = r;
            for (var band : recipe.bands()) {
                if (inner.core() == null) h.fail(recipe.name() + " should read as nested: " + r.describe());
                if (inner.sigil() != band.sigil()) h.fail(recipe.name() + " band sigil read as " + inner.sigil() + ": " + r.describe());
                for (var sg : band.signs()) if (inner.count(sg.type()) < sg.count()) h.fail(recipe.name() + " band: " + sg.type() + " read " + inner.count(sg.type()) + "x: " + r.describe());
                inner = inner.core();
            }
            if (!GlyphLibrary.readsAs(recipe, r)) h.fail(recipe.name() + " does not read as its page: " + r.describe());
            if (inner.sigil() != recipe.sigil()) h.fail(recipe.name() + " read as " + inner.sigil() + ": " + r.describe());
            if (inner.centerSign() != recipe.centerSign() && recipe.centerSign() != null) h.fail(recipe.name() + " centre read as " + inner.centerSign());
            for (var sg : recipe.signs()) if (inner.count(sg.type()) < sg.count()) h.fail(recipe.name() + ": " + sg.type() + " read " + inner.count(sg.type()) + "x: " + r.describe());
            var program = SpellCompiler.compile(r, GlyphFrame.facing(new Vec3(0, 1, 0)), Vec3.ZERO, 1, 1, 1, 0);
            if (program.form() == SpellProgram.Form.FIZZLE) h.fail(recipe.name() + " fizzles: " + r.describe());
            if (!recipe.bands().isEmpty() && program.layers().isEmpty() && program.duration() <= 0) h.fail(recipe.name() + " band does nothing");
        }
        h.succeed();
    }

    private static List<GlyphLibrary.Recipe> allRecipes() {
        List<GlyphLibrary.Recipe> all = new ArrayList<>(GlyphLibrary.ALL);
        all.addAll(GlyphLibrary.FORBIDDEN);
        return all;
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void strangerGivesAGrimoireOnceThenTrades(GameTestHelper h) {
        floor(h);
        ServerPlayer player = mockPlayer(h);
        var stranger = h.spawn(ModEntities.STRANGER.get(), new BlockPos(5, 1, 5));
        stranger.mobInteract(player, InteractionHand.MAIN_HAND);
        var inv = player.getInventory();
        if (inv.countItem(ModItems.GRIMOIRE.get()) != 1) h.fail("no grimoire given");
        if (!inv.contains(new ItemStack(ModItems.INK_PEN.get()))) h.fail("no pen given");
        int books = inv.countItem(ModItems.GRIMOIRE.get());
        stranger.mobInteract(player, InteractionHand.MAIN_HAND);
        if (inv.countItem(ModItems.GRIMOIRE.get()) != books) h.fail("the gift comes only once");
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

        h.setBlock(2, 1, 2, ModBlocks.WASHING_BARREL.get());
        var barrel = (WashingBarrelBlock.Entity) level.getBlockEntity(h.absolutePos(new BlockPos(2, 1, 2)));
        barrel.addCharge(24000, 96000);
        ServerPlayer player = mockPlayer(h);
        var pick = new ItemStack(Items.IRON_PICKAXE);
        pick.setDamageValue(200);
        player.setItemInHand(InteractionHand.MAIN_HAND, pick);
        h.useBlock(new BlockPos(2, 1, 2), player);

        h.setBlock(6, 1, 2, ModBlocks.VOID_BASIN.get());
        var junk = h.spawnItem(Items.DIRT, new Vec3(6.5, 1.6, 2.5));

        h.setBlock(5, 1, 7, ModBlocks.WARDING_TOWER.get());
        var tower = (BeastWardingTowerBlock.Entity) level.getBlockEntity(h.absolutePos(new BlockPos(5, 1, 7)));
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
        h.setBlock(2, 1, 5, ModBlocks.ARCANE_LENS.get());
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
        var wr = GlyphLibrary.byName("crystal_ward");
        GlyphService.inscribe(h.getLevel(), (SurfacePlane) ward[0], new GlyphSketch((Pt) ward[1], 1.2).ring(0, 362).centerSign(SignType.DIAMOND, 0.7).strokes, null);
        GlyphService.inscribe(h.getLevel(), (SurfacePlane) dance[0], new GlyphSketch((Pt) dance[1], 1.2).ring(0, 362).centerSign(SignType.DANCING_PUPPET, 0.7).strokes, null);
        var arrow = new Arrow(EntityType.ARROW, h.getLevel());
        Vec3 c = h.absoluteVec(new Vec3(3.5, 1, 5.5));
        var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(8, 1, 6));
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    arrow.setPos(c.x + 2, c.y + 1, c.z);
                    arrow.setDeltaMovement(-0.6, 0, 0);
                    arrow.setNoGravity(true);
                    h.getLevel().addFreshEntity(arrow);
                    zombie.setTarget(mockPlayer(h));
                })
                .thenExecuteAfter(4, () -> {
                    if (arrow.getDeltaMovement().x <= 0) h.fail("the ward should turn the arrow back, v=" + arrow.getDeltaMovement());
                    if (zombie.getTarget() != null) h.fail("a dancing puppet forgets its target");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "petrification")
    public static void petrificationMakesStatuesAndIntegrationRevives(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        BlockPos wall = new BlockPos(5, 1, 8), plank = new BlockPos(2, 1, 5);
        h.setBlock(wall, Blocks.COBBLESTONE_WALL);
        h.setBlock(plank, Blocks.OAK_PLANKS);
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(7, 1, 5));
        ServerPlayer player = mockPlayer(h);
        GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(c, 1.4).ring(0, 362).sigil(Sigil.EARTH, 0.8)
                .signs(SignType.EYE, 4, Orientation.OUTWARD, 0.4, 45).strokes, null);
        var stone = ModBlocks.PETRIFIED.get();
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    var s = spellsOn(h, plane);
                    if (s.isEmpty() || s.get(0).program.form() != SpellProgram.Form.PETRIFY) h.fail("expected petrification, got " + (s.isEmpty() ? "nothing" : s.get(0).program.summary()));
                })
                .thenWaitUntil(() -> {
                    if (pig.isAlive()) h.fail("the pig should turn to stone");
                    if (!h.getBlockState(wall).is(stone) || !h.getBlockState(plank).is(stone)) h.fail("the stone should take the wall and the plank");
                })
                .thenExecute(() -> {
                    h.assertBlockPresent(ModBlocks.STATUE.get(), new BlockPos(7, 1, 5));
                    if (h.getBlockState(new BlockPos(5, 0, 5)).is(stone)) h.fail("the glyph's own ground should be spared");
                    if (!h.getBlockState(new BlockPos(5, 0, 1)).is(stone)) h.fail("the floor further out should be taken");
                    for (var s : spellsOn(h, plane)) GlyphService.stop(h.getLevel(), s);

                    Vec3 mid = h.absoluteVec(new Vec3(5.5, 1, 5.5));
                    int blocks = PetrifiedBlock.restore(h.getLevel(), mid, 14);
                    int back = StatueBlock.revive(h.getLevel(), mid, 14);
                    if (back != 1) h.fail("integration should bring the pig back, revived " + back);
                    if (blocks < 20) h.fail("integration should give the blocks back, restored " + blocks);
                    h.assertBlockPresent(Blocks.COBBLESTONE_WALL, wall);
                    h.assertBlockPresent(Blocks.OAK_PLANKS, plank);
                    h.assertBlockPresent(Blocks.STONE, new BlockPos(5, 0, 1));
                    Taboo.add(player, 40);
                    if (Taboo.of(player) < Taboo.HUNTED) h.fail("forbidden magic should be counted");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "petrification_big")
    public static void theBiggestPetrificationKeepsTheServerResponsive(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];

        for (int x = 1; x <= 9; x++) for (int y = 1; y <= 4; y++) for (int z : new int[]{1, 9}) h.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS);
        GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(c, 3.2).ring(0, 362).sigil(Sigil.EARTH, 1.6)
                .signs(SignType.EYE, 4, Orientation.OUTWARD, 0.8, 45).strokes, null);
        var server = h.getLevel().getServer();
        long[] worst = {0};
        int[] watched = {0};
        h.startSequence()
                .thenExecuteFor(90, () -> {
                    long[] times = server.getTickTimesNanos();
                    long last = times[(server.getTickCount() + times.length - 1) % times.length];

                    if (watched[0]++ >= 8) worst[0] = Math.max(worst[0], last);
                })
                .thenExecute(() -> {
                    int crystal = 0;
                    BlockPos o = h.absolutePos(new BlockPos(5, 1, 5));
                    for (BlockPos p : BlockPos.betweenClosed(o.offset(-11, -11, -11), o.offset(11, 11, 11)))
                        if (h.getLevel().getBlockState(p).is(ModBlocks.PETRIFIED.get())) crystal++;
                    WitchsAtelier.LOGGER.info("[perf] biggest petrification: {} crystal blocks, worst tick {} ms over {} ticks",
                            crystal, worst[0] / 1_000_000.0, watched[0]);
                    if (crystal < 2000) h.fail("expected a ball of ten blocks round, got " + crystal + " crystal blocks");

                    if (worst[0] > 50_000_000L) h.fail("a tick took " + worst[0] / 1_000_000 + " ms");
                    for (var sp : spellsOn(h, plane)) GlyphService.stop(h.getLevel(), sp);
                    PetrifiedBlock.restore(h.getLevel(), Vec3.atCenterOf(o), 14);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void aWitchsGrimoireKeepsCopiesNamesAndNotes(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(c, 1.4).ring(0, 362).sigil(Sigil.FIRE, 0.8)
                .signs(SignType.COLUMN, 4, Orientation.OUTWARD, 0.4, 45).strokes, null);
        ServerPlayer player = mockPlayer(h);
        var book = new ItemStack(ModItems.WITCH_GRIMOIRE.get());
        player.setItemSlot(EquipmentSlot.MAINHAND, book);
        var pages = ModComponents.GLYPH_PAGES.get();
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    Vec3 up = plane.planeVector(0, 1);
                    player.setYRot((float) Math.toDegrees(Math.atan2(-up.x, up.z)));
                    player.setXRot(60);
                    Vec3 at = h.absoluteVec(new Vec3(5.5, 1.0, 5.5));
                    PageService.handleCopy(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(at, Direction.UP, h.absolutePos(new BlockPos(5, 0, 5)), false));
                    var b = player.getMainHandItem();
                    var gp = b.get(pages);
                    if (gp == null || gp.page(0).isEmpty()) h.fail("the glyph should be copied onto the first page");
                    var r = GlyphAnalyzer.analyze(PageService.pointsOf(gp.page(0)),
                            PageService.PAGE_INK);
                    if (!r.closed() || r.sigil() != Sigil.FIRE || r.count(SignType.COLUMN) != 4) h.fail("the copy should read as the glyph: " + r.describe());
                    double rad = r.ring().radius();
                    if (rad < 0.7 || rad > 1.0) h.fail("the copy should fill the page, ring radius " + rad);
                    if (!player.getGameProfile().getName().equals(b.get(ModComponents.KEEPER.get()))) h.fail("the book should be the copier's");

                    var volume = GlyphLibrary.volumeBook(GlyphLibrary.PRIMER, RandomSource.create(2));
                    volume.set(pages, volume.get(pages).withCurrent(1));
                    player.setItemSlot(EquipmentSlot.OFFHAND, volume);
                    PageService.handleCopy(player, InteractionHand.MAIN_HAND, null);
                    b = player.getMainHandItem();
                    if (b.get(pages).page(1).isEmpty()) h.fail("the old book's page should go onto page 2");
                    var titles = b.get(ModComponents.PAGE_TITLES.get());
                    if (titles == null || !"#glyph.witchsatelier.lantern".equals(titles.get(1))) h.fail("the copied page should keep its name: " + titles);

                    PageService.handleNote(player, InteractionHand.MAIN_HAND, 0, "My flame", "Four columns, drawn wide.");
                    b = player.getMainHandItem();
                    if (!"My flame".equals(b.get(ModComponents.PAGE_TITLES.get()).get(0))
                            || !"Four columns, drawn wide.".equals(b.get(ModComponents.PAGE_NOTES.get()).get(0))) h.fail("the page's name and notes should be kept");

                    int before = GlyphService.activeSpells(h.getLevel()).size();
                    player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.INK_PEN.get()));
                    InkPenItem.setLoaded(player.getOffhandItem(), InkType.CONJURING, InkPenItem.NIB_CAPACITY);
                    var ring = new GlyphSketch(new Pt(0, 0), 0.85).ring(0, 362).sigil(Sigil.LIGHT, 0.5).strokes;
                    for (double[] st : ring) {
                        float[] f = new float[st.length];
                        for (int i = 0; i < st.length; i++) f[i] = (float) st[i];
                        PageService.handleStroke(player, InteractionHand.MAIN_HAND, 2, f);
                    }
                    if (player.getMainHandItem().get(pages).page(2).isEmpty()) h.fail("drawing in the book should stay on its page");
                    if (GlyphService.activeSpells(h.getLevel()).size() > before) h.fail("a glyph in a witch's grimoire must not cast");

                    var written = player.getMainHandItem().copy();
                    var blank = new ItemStack(ModItems.WITCH_GRIMOIRE.get());
                    var input = CraftingInput.of(2, 1, List.of(written, blank));
                    var recipe = new WitchGrimoireCloning(CraftingBookCategory.MISC);
                    if (!recipe.matches(input, h.getLevel())) h.fail("a written grimoire and a blank one should make a copy");
                    var copy = recipe.assemble(input, h.getLevel().registryAccess());
                    if (copy.getCount() != 1 || !copy.get(pages).equals(written.get(pages))) h.fail("the copy should be the same book");
                    if (recipe.getRemainingItems(input).get(0).isEmpty()) h.fail("the original should stay");
                    if (recipe.matches(CraftingInput.of(2, 1, List.of(blank, blank.copy())), h.getLevel())) h.fail("two blank books copy nothing");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void aChainWaitsForItsTriggerAndLetsGo(GameTestHelper h) {
        floor(h);
        Object[] ta = top(h, new BlockPos(3, 0, 5)), tb = top(h, new BlockPos(7, 0, 5));
        SurfacePlane plane = (SurfacePlane) ta[0];
        Pt ca = (Pt) ta[1], cb = (Pt) tb[1];
        double r = 1.2, dx = cb.x() - ca.x(), dy = cb.y() - ca.y(), len = Math.hypot(dx, dy);
        double ux = dx / len, uy = dy / len;

        GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(cb, r).ring(20, 340).sigil(Sigil.LIGHT, 0.6).strokes, null);
        List<double[]> link = new ArrayList<>();
        double[] line = new double[2 * 21];
        for (int i = 0; i <= 20; i++) {
            double t = i / 20.0;
            line[i * 2] = ca.x() + ux * r + (dx - 2 * ux * r) * t;
            line[i * 2 + 1] = ca.y() + uy * r + (dy - 2 * uy * r) * t;
        }
        link.add(line);
        GlyphService.inscribe(h.getLevel(), plane, link, null);
        GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(ca, r).ring(90, 452).sigil(Sigil.LIGHT, 0.6)
                .signs(SignType.TREAD, 1, Orientation.OUTWARD, 0.35, 270).strokes, null);
        Supplier<ActiveSpell> trigger = () -> spellsOn(h, plane).stream().filter(x -> x.program.has(SpellProgram.TREAD)).findFirst().orElse(null);
        Supplier<ActiveSpell> lamp = () -> spellsOn(h, plane).stream().filter(x -> !x.program.has(SpellProgram.TREAD)).findFirst().orElse(null);
        BlockPos under = new BlockPos(3, -1, 5);
        h.startSequence()
                .thenExecuteAfter(3, () -> {
                    if (trigger.get() == null || lamp.get() == null) h.fail("both glyphs of the chain should be awake: " + spellsOn(h, plane).size());
                    if (!trigger.get().waiting()) h.fail("the Tread glyph should wait to be pressed");
                    if (!lamp.get().held) h.fail("the lantern should wait for its trigger");
                    if (lamp.get().chain == 0 || lamp.get().chain != trigger.get().chain) h.fail("they should be one chain");
                    h.setBlock(under, Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> { if (trigger.get().waiting()) h.fail("power should press the Tread glyph"); })
                .thenExecute(() -> { if (!lamp.get().held) h.fail("the light should take a moment to run along the line"); })
                .thenWaitUntil(() -> { if (lamp.get().held) h.fail("the lantern should light after its trigger"); })
                .thenExecute(() -> h.setBlock(under, Blocks.STONE))
                .thenWaitUntil(() -> {
                    if (!trigger.get().waiting()) h.fail("let go, the Tread glyph should wait again");
                    if (!lamp.get().held) h.fail("and the lantern with it");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void glyphsNestThreeDeep(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        List<double[]> ink = new ArrayList<>();

        ink.addAll(new GlyphSketch(c, 0.9).ring(0, 362).sigil(Sigil.FIRE, 0.5).signs(SignType.COLUMN, 4, Orientation.OUTWARD, 0.22, 45).strokes);

        ink.addAll(new GlyphSketch(c, 1.6).ring(0, 362).strokes);
        ink.addAll(new GlyphSketch(new Pt(c.x(), c.y() + 1.25), 0.3).sigil(Sigil.WATER, 0.42).strokes);

        ink.addAll(new GlyphSketch(c, 2.0 / 0.62).signs(SignType.LEVITATION, 4, Orientation.OUTWARD, 0.4, 45).strokes);
        ink.addAll(new GlyphSketch(new Pt(c.x(), c.y() + 2.0), 0.3).sigil(Sigil.WIND, 0.45).strokes);
        ink.addAll(new GlyphSketch(c, 2.45).ring(0, 362).strokes);
        GlyphService.inscribe(h.getLevel(), plane, ink, null);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    List<ActiveSpell> s = spellsOn(h, plane);
                    if (s.isEmpty()) h.fail("the glyph should wake");
                    var p = s.get(0).program;
                    WitchsAtelier.LOGGER.info("[nest] {} layers: {}", p.layers().size(), p.summary());
                    if (p.element() != Sigil.FIRE || p.form() != SpellProgram.Form.BEAM) h.fail("the heart should be a pillar of flame: " + p.summary());
                    if (p.layers().size() < 2) h.fail("each band should add a layer, got " + p.layers().size() + ": " + p.summary());
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void bigSignsMakeAStrongerSpell(GameTestHelper h) {
        floor(h);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        DoubleFunction<Double> powerWith = size -> {
            var g = new GlyphSketch(c, 1.4).ring(0, 362).sigil(Sigil.FIRE, 0.8).signs(SignType.COLUMN, 4, Orientation.OUTWARD, size, 45);
            List<List<Pt>> pts = new ArrayList<>();
            for (double[] st : g.strokes) {
                List<Pt> q = new ArrayList<>();
                for (int i = 0; i + 1 < st.length; i += 2) q.add(new Pt(st[i], st[i + 1]));
                pts.add(q);
            }
            var r = GlyphAnalyzer.analyze(pts, GlyphService.INK_WIDTH);
            if (r.count(SignType.COLUMN) != 4) h.fail("signs of size " + size + " should still read: " + r.describe());
            return SpellCompiler.compile(r, plane, 1.0).power();
        };
        double small = powerWith.apply(0.3), big = powerWith.apply(0.55);
        WitchsAtelier.LOGGER.info("[signs] power with small signs {} and big ones {}", small, big);
        if (big <= small * 1.1) h.fail("big signs should make the spell stronger: " + small + " vs " + big);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void glyphArrowsAndDispensersCastPages(GameTestHelper h) {
        floor(h);
        var level = h.getLevel();
        var pages = ModComponents.GLYPH_PAGES.get();
        var rnd = RandomSource.create(9);
        var flame = GlyphLibrary.draw(GlyphLibrary.byName("pillar_of_flame"), rnd, true, InkType.CONJURING);
        var lantern = GlyphLibrary.draw(GlyphLibrary.byName("lantern"), rnd, false, InkType.CONJURING);

        var sheet = new ItemStack(Items.PAPER);
        sheet.set(pages, new GlyphPages(List.of(flame), 0));
        var recipe = new GlyphArrowRecipe(CraftingBookCategory.EQUIPMENT);
        var grid = CraftingInput.of(2, 1, List.of(new ItemStack(Items.ARROW), sheet));
        if (!recipe.matches(grid, level)) h.fail("an arrow and an inked sheet should make a glyph arrow");
        var ammo = recipe.assemble(grid, level.registryAccess());
        if (!ammo.is(ModItems.GLYPH_ARROW.get()) || ammo.get(pages) == null) h.fail("the glyph arrow should carry the page");

        Vec3 from = h.absoluteVec(new Vec3(3.5, 4, 5.5));
        var arrow = new GlyphArrow(level, from.x, from.y, from.z, ammo);
        arrow.setDeltaMovement(0, -1.5, 0);
        level.addFreshEntity(arrow);

        BlockPos disp = new BlockPos(7, 1, 5);
        h.setBlock(disp, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.UP));
        var page = new ItemStack(Items.PAPER);
        page.set(pages, new GlyphPages(List.of(lantern), 0));
        if (h.getBlockEntity(disp) instanceof DispenserBlockEntity be) be.setItem(0, page);
        h.startSequence()
                .thenExecuteAfter(2, () -> h.setBlock(new BlockPos(8, 1, 5), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> {
                    var all = GlyphService.activeSpells(level);
                    Vec3 hitAt = h.absoluteVec(new Vec3(3.5, 1, 5.5)), dispAt = h.absoluteVec(new Vec3(7.5, 2, 5.5));
                    boolean fire = all.stream().anyMatch(x -> x.program.element() == Sigil.FIRE && x.program.origin().distanceTo(hitAt) < 1.5);
                    boolean light = all.stream().anyMatch(x -> x.program.element() == Sigil.LIGHT && x.program.origin().distanceTo(dispAt) < 1.5);
                    if (!fire) h.fail("the arrow should cast its flame where it struck");
                    if (!light) h.fail("the dispenser should cast the page's lantern from its front");
                })
                .thenExecute(() -> {
                    if (h.getBlockEntity(disp) instanceof DispenserBlockEntity be && !be.getItem(0).isEmpty()) h.fail("the page should burn away");
                    for (var sp : GlyphService.activeSpells(level)) if (sp.ticksLeft > PageService.CARRIED_TICKS
                            && sp.program.origin().distanceTo(h.absoluteVec(new Vec3(5.5, 1, 5.5))) < 4) h.fail("a carried glyph should not last for ever");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "mixed_sigils")
    public static void sideSigilsMixTheirElementIn(GameTestHelper h) {
        floor(h);
        var level = h.getLevel();
        BiFunction<int[], Object[], SurfacePlane> glyph = (at, sig) -> {
            Object[] t = top(h, new BlockPos(at[0], 0, at[1]));
            SurfacePlane plane = (SurfacePlane) t[0];
            Pt c = (Pt) t[1];
            double R = 1.4, off = R * 0.6;
            List<double[]> ink = new ArrayList<>(new GlyphSketch(c, R).ring(0, 362).sigil((Sigil) sig[0], 0.7).strokes);
            Sigil side = (Sigil) sig[1];
            for (double[] o : new double[][]{{off, 0}, {-off, 0}})
                ink.addAll(new GlyphSketch(new Pt(c.x() + o[0], c.y() + o[1]), 0.3).sigil(side, 0.5).strokes);
            GlyphService.inscribe(level, plane, ink, null);
            return plane;
        };
        SurfacePlane mist = glyph.apply(new int[]{2, 2}, new Object[]{Sigil.WIND, Sigil.WATER});
        SurfacePlane dry = glyph.apply(new int[]{8, 2}, new Object[]{Sigil.WATER, Sigil.FIRE});
        SurfacePlane repel = glyph.apply(new int[]{5, 8}, new Object[]{Sigil.EARTH, Sigil.LIGHT});
        Pig burning = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 1, 3));
        burning.igniteForSeconds(30);
        Pig wanderer = h.spawn(EntityType.PIG, new BlockPos(6, 1, 8));
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    var all = GlyphService.activeSpells(level);
                    Vec3 m = h.absoluteVec(new Vec3(2.5, 1, 2.5)), d = h.absoluteVec(new Vec3(8.5, 1, 2.5)), rp = h.absoluteVec(new Vec3(5.5, 1, 8.5));
                    var sm = all.stream().filter(x -> x.program.origin().distanceTo(m) < 1).findFirst().orElse(null);
                    var sd = all.stream().filter(x -> x.program.origin().distanceTo(d) < 1).findFirst().orElse(null);
                    var sr = all.stream().filter(x -> x.program.origin().distanceTo(rp) < 1).findFirst().orElse(null);
                    if (sm == null || !sm.program.has(SpellProgram.MIST)) h.fail("Water beside Wind should hang drops in the air: " + (sm == null ? "no spell" : sm.program.summary()));
                    if (sd == null || !sd.program.has(SpellProgram.DRY)) h.fail("Fire beside Water should keep water out: " + (sd == null ? "no spell" : sd.program.summary()));
                    if (sr == null || !sr.program.has(SpellProgram.REPEL)) h.fail("Light beside the middle should keep beasts off: " + (sr == null ? "no spell" : sr.program.summary()));
                })
                .thenExecute(() -> h.setBlock(9, 1, 2, Blocks.WATER))
                .thenWaitUntil(() -> { if (burning.isOnFire()) h.fail("the drops should put the pig out"); })
                .thenWaitUntil(() -> {
                    if (h.getLevel().getFluidState(h.absolutePos(new BlockPos(9, 1, 2))).is(FluidTags.WATER)) h.fail("the fire beside the water should keep water out");
                })
                .thenWaitUntil(() -> {
                    if (wanderer.position().distanceTo(h.absoluteVec(new Vec3(5.5, 1, 8.5))) < 2.5) h.fail("the pig should be driven out of the light");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void villagersWhoSeeMagicMadeTakeFright(GameTestHelper h) {
        floor(h);
        ServerPlayer player = survivor(h);
        Vec3 at = h.absoluteVec(new Vec3(5.5, 1, 2.5));
        player.moveTo(at.x, at.y, at.z, 0, 0);
        var villager = h.spawn(EntityType.VILLAGER, new BlockPos(5, 1, 7));
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    villager.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
                    villager.setYBodyRot(villager.getYHeadRot());
                    int before = Taboo.of(player);
                    int saw = Witnesses.saw(player);
                    if (saw != 1) h.fail("the villager should have seen it, saw=" + saw);
                    if (!villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY)) h.fail("the villager should run from the witch");
                    if (villager.getGossips().getReputation(player.getUUID(), t -> true) >= 0) h.fail("the villager should think worse of the witch");
                    if (Taboo.of(player) != before + Witnesses.TABOO) h.fail("the Pact should count it");

                    Witnesses.saw(player);
                    if (Taboo.of(player) != before + Witnesses.TABOO) h.fail("one glyph counts once");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void knightsLeaveOnceTheyHaveKilled(GameTestHelper h) {
        floor(h);
        ServerPlayer player = survivor(h);
        Vec3 at = h.absoluteVec(new Vec3(5.5, 1, 5.5));
        player.moveTo(at.x, at.y, at.z, 0, 0);
        var knight = h.spawn(ModEntities.KNIGHT_MORALIS.get(), new BlockPos(2, 1, 5));
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    Taboo.add(player, 60);
                    player.getPersistentData().putLong(Taboo.HUNT, 7);
                    knight.hunt(player, 7);

                    player.hurt(new DamageSource(h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(DamageTypes.GENERIC_KILL), knight), 1000);
                })
                .thenExecuteAfter(2, () -> {
                    if (player.isAlive()) h.fail("the player should be dead");
                    if (Taboo.huntOf(player) != 0 || Taboo.of(player) != 0) h.fail("the hunt should be over");
                    if (knight.getTarget() != null) h.fail("the knight should stop fighting");
                })
                .thenWaitUntil(() -> { if (knight.isAlive()) h.fail("the knight should leave"); })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void knightsLeftBehindAreReplaced(GameTestHelper h) {
        floor(h);
        ServerPlayer player = survivor(h);
        Vec3 at = h.absoluteVec(new Vec3(5.5, 1, 5.5));
        player.moveTo(at.x, at.y, at.z, 0, 0);
        Supplier<List<KnightMoralisEntity>> near = () -> h.getLevel().getEntitiesOfClass(
                KnightMoralisEntity.class, player.getBoundingBox().inflate(48), k -> k.huntId() == 9);
        h.startSequence()
                .thenExecute(() -> {
                    Taboo.add(player, 40);
                    player.getPersistentData().putLong(Taboo.HUNT, 9);
                    if (!near.get().isEmpty()) h.fail("no knights yet");
                })

                .thenWaitUntil(() -> {
                    Taboo.onPlayerTick(new PlayerTickEvent.Post(player));
                    if (near.get().isEmpty()) h.fail("knights should have been sent");
                })
                .thenExecute(() -> near.get().forEach(x -> x.discard()))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void brimhatsCastRealGlyphs(GameTestHelper h) {
        floor(h);
        var brimhat = h.spawn(ModEntities.BRIMHAT.get(), new BlockPos(2, 1, 5));
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(8, 1, 5));
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    brimhat.setTarget(pig);
                    brimhat.cast(BrimhatEntity.Spell.BIND, pig);
                    brimhat.cast(BrimhatEntity.Spell.BOLTS, pig);
                })
                .thenExecuteAfter(25, () -> {
                    if (!pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) h.fail("the binding threads should hold the pig");
                    var mask = new ItemStack(ModItems.BRIMHAT_MASK.get());
                    ServerPlayer p = mockPlayer(h);
                    p.setItemSlot(EquipmentSlot.HEAD, mask);
                    if (!BrimhatEntity.wearsMask(p)) h.fail("the mask should pass as a Brimhat");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void smallContraptionsWork(GameTestHelper h) {
        floor(h);
        h.setBlock(3, 1, 3, ModBlocks.GLOWSTONE_PATH.get());
        var pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 2, 3));
        h.setBlock(6, 1, 6, ModBlocks.DRAGON_TEACUP.get());
        ServerPlayer player = mockPlayer(h);
        h.useBlock(new BlockPos(6, 1, 6), player);
        h.setBlock(8, 1, 3, ModBlocks.SEALCHAIR.get().defaultBlockState());
        h.startSequence()
                .thenExecuteAfter(10, () -> {
                    if (!h.getBlockState(new BlockPos(3, 1, 3)).getValue(GlowstonePathBlock.LIT)) h.fail("the path should glow under a step");
                    if (!h.getBlockState(new BlockPos(6, 1, 6)).getValue(DragonTeacupBlock.DANCING)) h.fail("the tea dragon should rise");

                    h.useBlock(new BlockPos(8, 1, 3), player);
                    if (!(player.getVehicle() instanceof SealchairEntity chair) || chair.getControllingPassenger() != player)
                        h.fail("the sealchair should get up and carry its rider");
                    else if (!h.getBlockState(new BlockPos(8, 1, 3)).isAir()) h.fail("the chair should leave its spot when it gets up");
                    pig.discard();
                })
                .thenExecuteAfter(60, () -> {
                    if (h.getBlockState(new BlockPos(3, 1, 3)).getValue(GlowstonePathBlock.LIT)) h.fail("the path should go dark again");
                    player.stopRiding();
                })
                .thenExecuteAfter(3, () -> {
                    if (!h.getLevel().getEntitiesOfClass(SealchairEntity.class, new AABB(h.absolutePos(new BlockPos(8, 1, 3))).inflate(4)).isEmpty())
                        h.fail("an empty sealchair should not stay up and about");
                    boolean block = false;
                    for (BlockPos p : BlockPos.betweenClosed(new BlockPos(5, 0, 0), new BlockPos(10, 3, 6)))
                        block |= h.getBlockState(p).is(ModBlocks.SEALCHAIR.get());
                    if (!block) h.fail("the sealchair should settle back into a block");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void witchPlacesArePlaceableStructures(GameTestHelper h) throws CommandSyntaxException {
        var level = h.getLevel();
        var server = level.getServer();
        var src = server.createCommandSourceStack().withLevel(level).withSuppressedOutput().withPermission(4);
        String[] kinds = {"library_ruin"};
        BlockPos[] at = new BlockPos[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            at[i] = h.absolutePos(new BlockPos(5, 1, 5)).offset(200 + i * 64, 0, 0);
            BlockPos a = at[i];

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

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void worldgenFeatureOrderIsSound(GameTestHelper h) {
        var biomes = h.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        List<Holder<Biome>> all = new ArrayList<>(biomes.holders().toList());
        try {
            FeatureSorter.buildFeaturesPerStep(all, b -> b.value().getGenerationSettings().features(), true);
        } catch (IllegalStateException ex) {
            h.fail("feature order cycle: " + ex.getMessage());
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void tentStaffRaisesATent(GameTestHelper h) {
        floor(h);
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) h.setBlock(x, 0, z, Blocks.DIRT);
        ServerPlayer player = mockPlayer(h);
        var staff = new ItemStack(ModItems.TENT_STAFF.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, staff);
        BlockPos g = h.absolutePos(new BlockPos(5, 0, 5));
        var ctx = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(g).add(0, 0.5, 0), Direction.UP, g, false));
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
                    var forms = new HashSet<SpellProgram.Form>();
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
                .thenWaitUntil(() -> {
                    if (!pig.isOnFire()) h.fail("stepping on the snare should spring its pillar");
                    if (spellsOn(h, plane).get(0).waiting()) h.fail("the snare should be pressed while the pig stands on it");
                })
                .thenExecute(() -> pig.teleportTo(h.absoluteVec(new Vec3(1.5, 1, 1.5)).x, h.absoluteVec(new Vec3(1.5, 1, 1.5)).y, h.absoluteVec(new Vec3(1.5, 1, 1.5)).z))
                .thenWaitUntil(() -> { if (!spellsOn(h, plane).get(0).waiting()) h.fail("left alone, the snare should set itself again"); })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void glyphsJoinedByInkWakeTogether(GameTestHelper h) {
        floor(h);
        Object[] ta = top(h, new BlockPos(2, 0, 5)), tb = top(h, new BlockPos(8, 0, 5));
        SurfacePlane plane = (SurfacePlane) ta[0];
        Pt a = (Pt) ta[1], b = (Pt) tb[1];
        var level = h.getLevel();

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
                .thenExecuteAfter(1, () -> {
                    Vec3 mid = h.absoluteVec(new Vec3(5.5, 1, 5.5));
                    var s = GlyphService.activeSpells(level).stream().filter(sp -> plane.equals(sp.plane) && sp.program.origin().distanceTo(mid) < 6).toList();
                    if (s.size() != 2) h.fail("closing one glyph should wake the one joined to it: " + s.size() + " spells");
                    var closed = s.stream().filter(sp -> sp.hop == 0).findFirst().orElse(null);
                    var joined = s.stream().filter(sp -> sp.hop == 1).findFirst().orElse(null);
                    if (closed == null || joined == null || closed.chain != joined.chain) h.fail("the two glyphs should be one chain");
                    if (closed.held) h.fail("the glyph just closed should light at once");
                    if (!joined.held) h.fail("the light should take a moment to run along the line");
                })
                .thenWaitUntil(() -> {
                    Vec3 mid = h.absoluteVec(new Vec3(5.5, 1, 5.5));
                    if (GlyphService.activeSpells(level).stream().anyMatch(sp -> plane.equals(sp.plane) && sp.program.origin().distanceTo(mid) < 6 && sp.held))
                        h.fail("the joined glyph should light when the wave reaches it");
                })
                .thenSucceed();
    }

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

        List<double[]> halves = new ArrayList<>();
        for (double[] st : new GlyphSketch(c, 0.95).ring(0, 362).sigil(Sigil.LIGHT, 0.45).strokes) {
            List<Double> cur = new ArrayList<>();
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
        var data = GlyphLevelData.get(level);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    if (GlyphService.activeSpells(level).stream().anyMatch(s -> plane.equals(s.plane) && s.program.origin().distanceTo(mid) < 2))
                        h.fail("the halves are a block apart: nothing should wake yet");
                    h.setBlock(8, 1, 4, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.WEST));
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

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aCopyTracedOverAPinnedPageCastsAsThePage(GameTestHelper h) {
        floor(h);
        var level = h.getLevel();
        ServerPlayer player = mockPlayer(h);
        ItemStack book = GlyphLibrary.grimoireBook(RandomSource.create(5), 4, "pillar_of_flame");
        var gp = book.get(ModComponents.GLYPH_PAGES.get());
        book.set(ModComponents.GLYPH_PAGES.get(), gp.withCurrent(2));
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.INK_PEN.get()));
        player.setItemSlot(EquipmentSlot.OFFHAND, book);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        double turn = 1.1, scale = 2.0, rot = turn - Math.PI / 2, cos = Math.cos(rot), sin = Math.sin(rot);
        Tracing.pin(player, new Tracing.Pin(plane, c.x(), c.y(), turn, scale));

        List<double[]> copy = new ArrayList<>();
        int k = 0;
        for (var st : book.get(ModComponents.GLYPH_PAGES.get()).page(2).strokes()) {
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
        GlyphService.inscribe(level, plane, List.of(copy.get(0)), InkType.CONJURING, player);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    var data = GlyphLevelData.get(level);
                    for (var st : data.near(plane, c.x(), c.y(), 1)) {
                        if (st.centroid().dist(c) < 0.5 && st.state() == StrokeState.SPENT) h.fail("the traced ring went off before the copy was done");
                    }
                    if (!spellsOn(h, plane).isEmpty()) h.fail("nothing should wake while the copy is unfinished");
                    GlyphService.inscribe(level, plane, copy.subList(1, copy.size()), InkType.CONJURING, player);
                })
                .thenExecuteAfter(3, () -> {
                    var s = spellsOn(h, plane);
                    if (s.isEmpty()) h.fail("the finished copy should wake");
                    else if (s.get(0).program.element() != Sigil.FIRE || s.get(0).program.form() != SpellProgram.Form.BEAM)
                        h.fail("a copy of Pillar of Flame should be a pillar of flame, got " + s.get(0).program.summary());
                })
                .thenSucceed();
    }

    private static GlyphPages.Page oldBirdsPage() {
        List<GlyphPages.PageStroke> out = new ArrayList<>();
        var ink = InkType.LASTING;
        Consumer<List<Pt>> add = pts -> {
            float[] f = new float[pts.size() * 2];
            for (int i = 0; i < pts.size(); i++) { f[i * 2] = (float) pts.get(i).x(); f[i * 2 + 1] = (float) pts.get(i).y(); }
            out.add(new GlyphPages.PageStroke(f, ink));
        };
        List<Pt> ring = new ArrayList<>();
        for (int i = 0; i <= 180; i++) { double a = Math.toRadians(i * 2.0); ring.add(new Pt(Math.cos(a) * 0.85, Math.sin(a) * 0.85)); }
        add.accept(ring);
        var light = Templates.SIGILS.stream().filter(t -> t.sigil() == Sigil.LIGHT).findFirst().orElseThrow();
        for (var st : light.strokes()) add.accept(st.stream().map(q -> q.scale(0.55 / 2)).toList());
        var old = Shapes.of().arc(0.3, 0, 0.75, 130, 230).arc(-0.3, 0, 0.75, -50, 50).poly(-0.25, 0.7, 0, 0.95, 0.25, 0.7);
        for (double pos : new double[]{Math.PI / 2, -Math.PI / 2}) {
            Pt at = new Pt(Math.cos(pos) * 0.85 * 0.62, Math.sin(pos) * 0.85 * 0.62);
            for (var st : old.build()) add.accept(st.stream().map(q -> q.rotate(pos - Math.PI / 2).scale(0.3 / 2).plus(at)).toList());
        }
        return new GlyphPages.Page(List.copyOf(out));
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void anOldPageTracedCastsAsItsRecipeAndIsRedrawn(GameTestHelper h) {
        floor(h);
        var level = h.getLevel();
        ServerPlayer player = mockPlayer(h);
        ItemStack book = GlyphLibrary.grimoireBook(RandomSource.create(5), 4, "birds_of_light");
        var comp = ModComponents.GLYPH_PAGES.get();
        book.set(comp, book.get(comp).withPage(2, oldBirdsPage()).withCurrent(2));
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.INK_PEN.get()));
        player.setItemSlot(EquipmentSlot.OFFHAND, book);
        Object[] t = top(h, new BlockPos(5, 0, 5));
        SurfacePlane plane = (SurfacePlane) t[0];
        Pt c = (Pt) t[1];
        double turn = Math.PI / 2, scale = 2.2;
        Tracing.pin(player, new Tracing.Pin(plane, c.x(), c.y(), turn, scale));

        List<double[]> copy = new ArrayList<>();
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
        GlyphService.inscribe(level, plane, copy.subList(1, copy.size()), InkType.CONJURING, player);
        GlyphService.inscribe(level, plane, List.of(copy.get(0)), InkType.CONJURING, player);
        h.startSequence()
                .thenExecuteAfter(3, () -> {
                    var s = spellsOn(h, plane);
                    if (s.isEmpty()) h.fail("a traced copy of the old page should wake");
                    else if (s.get(0).program.form() != SpellProgram.Form.BIRD) h.fail("it should be Birds of Light, got " + s.get(0).program.summary());

                    PageService.handleTurn(player, InteractionHand.OFF_HAND, 2);
                    var now = player.getOffhandItem().get(comp).page(2);
                    var r = GlyphAnalyzer.analyze(GlyphLibrary.strokesOf(now), PageService.PAGE_INK);
                    if (!GlyphLibrary.readsAs(GlyphLibrary.byName("birds_of_light"), r))
                        h.fail("the old page should be drawn again so that it reads as Birds of Light: " + r.describe());
                })
                .thenSucceed();
    }

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
