package com.fimkov.witchsatelier.gametest;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.block.PetrifiedBlock;
import com.fimkov.witchsatelier.block.StatueBlock;
import com.fimkov.witchsatelier.entity.KnightMoralisEntity;
import com.fimkov.witchsatelier.entity.Taboo;
import com.fimkov.witchsatelier.entity.Witnesses;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.model.Orientation;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import com.fimkov.witchsatelier.glyph.page.PageKind;
import com.fimkov.witchsatelier.glyph.page.PageService;
import com.fimkov.witchsatelier.glyph.recognize.GlyphAnalyzer;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.registry.ModBlocks;
import com.fimkov.witchsatelier.registry.ModEntities;
import com.fimkov.witchsatelier.registry.ModItems;
import com.fimkov.witchsatelier.spell.ActiveSpell;
import com.fimkov.witchsatelier.spell.GlyphFrame;
import com.fimkov.witchsatelier.spell.SpellCompiler;
import com.fimkov.witchsatelier.spell.SpellProgram;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The Pact: talebearers and the Seal of Obliviation, Sinsinger's Talons, and statues that only blood ink can wake. */
@GameTestHolder(WitchsAtelier.MODID)
@PrefixGameTestTemplate(false)
public final class PactGameTests {
    private PactGameTests() {}

    private static SurfacePlane floor(GameTestHelper h) {
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
        return SurfacePlane.of(h.absolutePos(new BlockPos(5, 0, 5)), Direction.UP);
    }

    private static Pt centre(GameTestHelper h, SurfacePlane plane) {
        return plane.toUV(h.absoluteVec(new Vec3(5.5, 1, 5.5)));
    }

    @SuppressWarnings("removal")
    private static ServerPlayer survivor(GameTestHelper h) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-witch"), false);
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

    private static ServerPlayer witchAt(GameTestHelper h, double x, double z) {
        ServerPlayer p = survivor(h);
        Vec3 at = h.absoluteVec(new Vec3(x, 1, z));
        p.moveTo(at.x, at.y, at.z, 0, 0);
        return p;
    }

    private static void facing(Villager v, ServerPlayer p) {
        v.lookAt(EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
        v.setYBodyRot(v.getYHeadRot());
    }

    private static List<InkStroke> draw(GameTestHelper h, SurfacePlane plane, String recipe, double radius, InkType ink) {
        Pt c = centre(h, plane);
        var page = GlyphLibrary.draw(GlyphLibrary.byName(recipe), RandomSource.create(11), false, InkType.CONJURING);
        double k = radius / GlyphLibrary.OUTER_RADIUS;
        List<double[]> strokes = new ArrayList<>();
        for (var s : page.strokes()) {
            float[] f = s.pts();
            double[] d = new double[f.length];
            for (int i = 0; i + 1 < f.length; i += 2) {
                d[i] = c.x() + f[i] * k;
                d[i + 1] = c.y() + f[i + 1] * k;
            }
            strokes.add(d);
        }
        return GlyphService.inscribe(h.getLevel(), plane, strokes, ink, null);
    }

    private static ActiveSpell spellOn(GameTestHelper h, SurfacePlane plane) {
        Vec3 mid = h.absoluteVec(new Vec3(5.5, 1, 5.5));
        return GlyphService.activeSpells(h.getLevel()).stream()
                .filter(s -> plane.equals(s.plane) && s.program.origin().distanceTo(mid) < 3).findFirst().orElse(null);
    }

    @GameTest(template = "empty")
    public static void theSealOfObliviationHasAGlyphOfItsOwn(GameTestHelper h) {
        var seal = GlyphLibrary.byName("seal_of_obliviation");
        if (seal == null) h.fail("the seal should be in the library");
        if (!GlyphLibrary.FORBIDDEN_BOOK.entries().contains("seal_of_obliviation")) h.fail("the black-bound book should show the seal");
        List<GlyphLibrary.Recipe> all = new ArrayList<>(GlyphLibrary.ALL);
        all.addAll(GlyphLibrary.FORBIDDEN);
        for (int seed = 1; seed <= 6; seed++) {
            var page = GlyphLibrary.draw(seal, RandomSource.create(seed), false, InkType.CONJURING);
            var r = GlyphAnalyzer.analyze(PageService.pointsOf(page), PageService.PAGE_INK);
            if (!GlyphLibrary.readsAs(seal, r)) h.fail("the seal should read as itself (seed " + seed + "): " + r.describe());
            for (var other : all) {
                if (other != seal && GlyphLibrary.readsAs(other, r)) h.fail("the seal also reads as " + other.name());
            }
            var program = SpellCompiler.compile(r, GlyphFrame.facing(new Vec3(0, 1, 0)), Vec3.ZERO, 1, 1, 1, 0);
            if (program.form() != SpellProgram.Form.OBLIVION) h.fail("the seal should work as obliviation: " + program.summary());
        }
        for (String name : List.of("night_eye", "veil_of_unseeing")) {
            var page = GlyphLibrary.draw(GlyphLibrary.byName(name), RandomSource.create(5), false, InkType.CONJURING);
            var r = GlyphAnalyzer.analyze(PageService.pointsOf(page), PageService.PAGE_INK);
            var program = SpellCompiler.compile(r, GlyphFrame.facing(new Vec3(0, 1, 0)), Vec3.ZERO, 1, 1, 1, 0);
            if (program.form() == SpellProgram.Form.OBLIVION) h.fail(name + " must not erase minds");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "pact_bell")
    public static void aWitnessRunsForTheBellAndTheVillageHears(GameTestHelper h) {
        floor(h);
        BlockPos bell = new BlockPos(5, 1, 9);
        h.setBlock(bell, Blocks.BELL);
        ServerPlayer witch = witchAt(h, 5.5, 0.5);
        Villager witness = h.spawn(EntityType.VILLAGER, new BlockPos(5, 1, 3));
        Villager neighbour = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 1, 9));
        int[] before = new int[1];
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    facing(witness, witch);
                    neighbour.setYRot(0);
                    neighbour.setYHeadRot(0);
                    neighbour.setYBodyRot(0);
                    before[0] = Taboo.of(witch);
                    if (Witnesses.saw(witch) != 1) h.fail("only the witness should see the drawing");
                    if (!witch.getUUID().equals(Witnesses.talebearerAbout(witness))) h.fail("the witness should carry the tale");
                    if (Witnesses.talebearerAbout(neighbour) != null) h.fail("the neighbour saw nothing");
                    if (Taboo.of(witch) != before[0] + Witnesses.TABOO) h.fail("the sighting itself should count");
                })
                .thenWaitUntil(() -> {
                    if (Taboo.of(witch) != before[0] + Witnesses.TABOO + Witnesses.BELL)
                        h.fail("the witness should ring the bell, debt " + Taboo.of(witch) + " vs " + before[0]);
                })
                .thenExecute(() -> {
                    if (!(h.getLevel().getBlockEntity(h.absolutePos(bell)) instanceof BellBlockEntity b) || !b.shaking) h.fail("the bell should ring");
                    if (Witnesses.talebearerAbout(witness) != null) h.fail("the tale is told");
                    if (neighbour.getGossips().getReputation(witch.getUUID(), t -> true) >= 0) h.fail("the whole village should hear");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "pact_seal")
    public static void aSealOnTheGroundErasesTheWitness(GameTestHelper h) {
        SurfacePlane plane = floor(h);
        ServerPlayer witch = witchAt(h, 5.5, 0.5);
        Villager witness = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(5, 1, 5));
        int[] before = new int[1];
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    facing(witness, witch);
                    before[0] = Taboo.of(witch);
                    if (Witnesses.saw(witch) != 1) h.fail("the villager should see it");
                    if (Witnesses.talebearerAbout(witness) == null) h.fail("the witness should carry the tale");
                    if (witness.getGossips().getReputation(witch.getUUID(), t -> true) >= 0) h.fail("the witness should think worse of the witch");
                    draw(h, plane, "seal_of_obliviation", 1.4, InkType.LASTING);
                })
                .thenWaitUntil(() -> { if (Witnesses.talebearerAbout(witness) != null) h.fail("the seal should take the tale"); })
                .thenExecute(() -> {
                    ActiveSpell s = spellOn(h, plane);
                    if (s == null || s.program.form() != SpellProgram.Form.OBLIVION) h.fail("the seal should be working");
                    if (Taboo.of(witch) != before[0]) h.fail("a sighting no one remembers is not counted, debt " + Taboo.of(witch));
                    if (witness.getGossips().getReputation(witch.getUUID(), t -> true) < 0) h.fail("the bad word should be taken back");
                    if (witness.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY)) h.fail("the fright should be gone");
                    if (!witness.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) h.fail("the witness should doze off");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "pact_palm")
    public static void aSealInThePalmErasesTheCreatureInSight(GameTestHelper h) {
        floor(h);
        ServerPlayer witch = witchAt(h, 5.5, 1.5);
        Villager witness = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(5, 1, 5));
        Villager bystander = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(9, 1, 5));
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    facing(witness, witch);
                    facing(bystander, witch);
                    if (Witnesses.saw(witch) != 2) h.fail("both villagers should see it");
                    var page = GlyphLibrary.draw(GlyphLibrary.byName("seal_of_obliviation"), RandomSource.create(3), false, InkType.CONJURING);
                    var r = GlyphAnalyzer.analyze(PageService.pointsOf(page), PageService.PAGE_INK);
                    Vec3 look = witch.getLookAngle();
                    var program = SpellCompiler.compile(r, GlyphFrame.facing(look), witch.getEyePosition().add(look.scale(0.9)),
                            PageKind.PAPER.scale, 1, 1, 0);
                    GlyphService.startSpell(h.getLevel(), null, program, witch, true);
                })
                .thenWaitUntil(() -> { if (Witnesses.talebearerAbout(witness) != null) h.fail("the witness in sight should forget"); })
                .thenExecute(() -> {
                    if (Witnesses.talebearerAbout(bystander) == null) h.fail("the villager off to the side should still remember");
                    if (Taboo.of(witch) != Witnesses.TABOO) h.fail("one witness still remembers, so the sighting still counts");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "pact_silenced")
    public static void killingAWitnessIsCountedHeavily(GameTestHelper h) {
        floor(h);
        ServerPlayer witch = witchAt(h, 5.5, 0.5);
        Villager witness = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(5, 1, 5));
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    facing(witness, witch);
                    Witnesses.saw(witch);
                    int before = Taboo.of(witch);
                    witness.hurt(h.getLevel().damageSources().playerAttack(witch), 1000);
                    if (witness.isAlive()) h.fail("the witness should be dead");
                    if (Taboo.of(witch) != before + Witnesses.SILENCED) h.fail("the order always learns of a silenced witness");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "pact_talons")
    public static void sinsingersTalonsTakeTheWitchAlive(GameTestHelper h) {
        floor(h);
        ServerPlayer witch = witchAt(h, 5.5, 5.5);
        witch.getInventory().add(new ItemStack(Items.DIAMOND, 3));
        KnightMoralisEntity knight = h.spawn(ModEntities.KNIGHT_MORALIS.get(), new BlockPos(5, 1, 3));
        knight.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.CAPTURE_PENNANT.get()));
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    Taboo.add(witch, 60);
                    witch.getPersistentData().putLong(Taboo.HUNT, 7);
                    knight.hunt(witch, 7);
                    witch.setHealth(9);
                    witch.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 4));
                })
                .thenWaitUntil(() -> {
                    if (!knight.getOffhandItem().has(DataComponents.DYED_COLOR)) h.fail("the pennant should turn black before the lunge");
                })
                .thenWaitUntil(() -> { if (Taboo.huntOf(witch) != 0) h.fail("the talons should end the hunt"); })
                .thenExecute(() -> {
                    if (!witch.isAlive()) h.fail("the witch should be taken alive");
                    if (Taboo.of(witch) != Taboo.HUNTED / 2) h.fail("only part of the debt should remain, got " + Taboo.of(witch));
                    if (!witch.hasEffect(MobEffects.DARKNESS) || !witch.hasEffect(MobEffects.CONFUSION)) h.fail("the erasure should leave the witch dazed");
                    if (witch.getInventory().countItem(Items.DIAMOND) != 3) h.fail("nothing carried should be lost");
                    if (knight.getTarget() != null) h.fail("the knight should be done");
                })
                .thenWaitUntil(() -> {
                    if (knight.isAlive() && knight.getOffhandItem().has(DataComponents.DYED_COLOR)) h.fail("the pennant should go back to silver");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "pact_talons_rule")
    public static void theTalonsWaitForABoundAndWornDownWitch(GameTestHelper h) {
        floor(h);
        ServerPlayer witch = witchAt(h, 5.5, 5.5);
        KnightMoralisEntity knight = h.spawnWithNoFreeWill(ModEntities.KNIGHT_MORALIS.get(), new BlockPos(5, 1, 3));
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    Taboo.add(witch, 60);
                    witch.getPersistentData().putLong(Taboo.HUNT, 7);
                    knight.hunt(witch, 7);
                    witch.setHealth(9);
                    if (knight.mayTakeAlive(witch)) h.fail("a witch who is not bound should not be taken");
                    witch.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 4));
                    if (!knight.mayTakeAlive(witch)) h.fail("a bound, worn-down witch should be taken alive");
                    witch.setHealth(witch.getMaxHealth());
                    if (knight.mayTakeAlive(witch)) h.fail("a witch still on her feet should not be taken");
                    witch.setHealth(9);
                    witch.getPersistentData().putLong(Taboo.HUNT, 8);
                    if (knight.mayTakeAlive(witch)) h.fail("only the knights of this hunt take her");
                })
                .thenSucceed();
    }

    private static List<double[]> integration(Pt c) {
        return new GlyphSketch(c, 1.2).ring(0, 362).sigil(Sigil.EARTH, 0.8)
                .signs(SignType.CRUSH, 4, Orientation.INWARD, 0.45, 45).strokes;
    }

    private static final BlockPos STATUE = new BlockPos(7, 1, 5), PLANK = new BlockPos(2, 1, 5);

    private static void statueAndStone(GameTestHelper h) {
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, STATUE);
        if (!StatueBlock.petrify(h.getLevel(), pig)) h.fail("the pig should turn to stone");
        h.setBlock(PLANK, Blocks.OAK_PLANKS);
        if (!PetrifiedBlock.petrify(h.getLevel(), h.absolutePos(PLANK))) h.fail("the plank should turn to stone");
    }

    /** Draws Integration once, and again if the last one came to nothing (blood ink misfires). */
    private static void integrate(GameTestHelper h, SurfacePlane plane, InkType ink, List<InkStroke> drawn, long[] at) {
        if (!drawn.isEmpty() && h.getTick() - at[0] < 10) return;
        if (!drawn.isEmpty()) GlyphService.eraseStrokes(h.getLevel(), drawn.stream().map(InkStroke::id).toList());
        drawn.clear();
        drawn.addAll(GlyphService.inscribe(h.getLevel(), plane, integration(centre(h, plane)), ink, null));
        at[0] = h.getTick();
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "pact_stir")
    public static void integrationInOrdinaryInkOnlyHalfWakesAStatue(GameTestHelper h) {
        SurfacePlane plane = floor(h);
        statueAndStone(h);
        List<InkStroke> drawn = new ArrayList<>();
        long[] at = new long[1];
        h.startSequence()
                .thenWaitUntil(() -> {
                    if (h.getBlockState(PLANK).is(Blocks.OAK_PLANKS)) return;
                    integrate(h, plane, InkType.CONJURING, drawn, at);
                    h.fail("waiting for Integration to give the stone back");
                })
                .thenExecute(() -> {
                    h.assertBlockPresent(ModBlocks.STATUE.get(), STATUE);
                    if (!(h.getLevel().getBlockEntity(h.absolutePos(STATUE)) instanceof StatueBlock.Entity be) || !be.stirred()) h.fail("the statue should half wake");
                    if (!h.getLevel().getEntitiesOfClass(Pig.class, new AABB(h.absolutePos(STATUE)).inflate(4)).isEmpty())
                        h.fail("ordinary ink must not give the body back");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "pact_blood")
    public static void integrationInBloodInkGivesTheBodyBack(GameTestHelper h) {
        SurfacePlane plane = floor(h);
        statueAndStone(h);
        List<InkStroke> drawn = new ArrayList<>();
        long[] at = new long[1];
        h.startSequence()
                .thenWaitUntil(() -> {
                    if (!h.getBlockState(STATUE).is(ModBlocks.STATUE.get())) return;
                    integrate(h, plane, InkType.BLOOD, drawn, at);
                    h.fail("waiting for Integration in blood ink");
                })
                .thenExecute(() -> {
                    if (h.getLevel().getEntitiesOfClass(Pig.class, new AABB(h.absolutePos(STATUE)).inflate(4)).isEmpty())
                        h.fail("blood ink should give the body back");
                    h.assertBlockPresent(Blocks.OAK_PLANKS, PLANK);
                })
                .thenSucceed();
    }
}
