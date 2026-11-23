package com.fimkov.witchsatelier.gametest;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.advancement.Feats;
import com.fimkov.witchsatelier.advancement.MagicTrigger;
import com.fimkov.witchsatelier.block.sealed.BeastWardingTowerBlock;
import com.fimkov.witchsatelier.entity.Taboo;
import com.fimkov.witchsatelier.entity.Witnesses;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.ink.LoadedInk;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.item.InkwellItem;
import com.fimkov.witchsatelier.registry.ModBlocks;
import com.fimkov.witchsatelier.registry.ModItems;
import com.fimkov.witchsatelier.spell.ActiveSpell;
import com.fimkov.witchsatelier.spell.SpellProgram;
import com.fimkov.witchsatelier.world.PassageStars;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@GameTestHolder(WitchsAtelier.MODID)
@PrefixGameTestTemplate(false)
public final class WorldGameTests {
    private WorldGameTests() {}

    private static final List<String> ADVANCEMENTS = List.of("passage_stars", "behind_the_curtains", "many_small_seals", "three_rings_deep",
            "read_it_twice", "steam_and_silence", "river_to_sand", "the_bell_tolls", "forgiven_by_time", "ward_on_the_road");

    private static void floor(GameTestHelper h) {
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    @SuppressWarnings("removal")
    private static ServerPlayer survivor(GameTestHelper h, Vec3 rel) {
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
        Vec3 at = h.absoluteVec(rel);
        p.moveTo(at.x, at.y, at.z, 0, 0);
        return p;
    }

    private static boolean has(ServerPlayer p, String name) {
        var holder = p.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "witch/" + name));
        return holder != null && p.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static SurfacePlane plane(GameTestHelper h) {
        return SurfacePlane.of(h.absolutePos(new BlockPos(5, 0, 5)), Direction.UP);
    }

    private static Pt centre(GameTestHelper h, SurfacePlane plane) {
        return plane.toUV(Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 0, 5))).add(0, 0.5, 0));
    }

    private static List<ActiveSpell> spellsOn(GameTestHelper h, SurfacePlane plane) {
        Vec3 mid = h.absoluteVec(new Vec3(5.5, 1, 5.5));
        return GlyphService.activeSpells(h.getLevel()).stream()
                .filter(s -> plane.equals(s.plane) && s.program.origin().distanceTo(mid) < 4).toList();
    }

    private static List<double[]> lantern(GameTestHelper h, SurfacePlane plane, double radius) {
        return new GlyphSketch(centre(h, plane), radius).ring(0, 362).sigil(Sigil.LIGHT, radius * 0.5).strokes;
    }

    private static SpellProgram program(Sigil element, double power, List<SpellProgram> layers) {
        Vec3 up = new Vec3(0, 1, 0);
        return new SpellProgram(element, null, SpellProgram.Form.BEAM, false, Vec3.ZERO, up, up, 1, power, 100,
                false, false, false, 0, 1, 0, false, 0, layers, false);
    }

    @GameTest(template = "empty")
    public static void passageStarsKeepTheirCalendar(GameTestHelper h) {
        int day = PassageStars.DAY;
        RandomSource rnd = RandomSource.create(7);
        var d = new PassageStars.Data();
        if (d.tryBegin(1000, 14000, true, rnd)) h.fail("a new world should not see the stars on its first night");
        long first = d.next();
        if (first < PassageStars.FIRST || first > PassageStars.FIRST + PassageStars.FIRST_SPREAD) h.fail("the first shower should be planned a week or two out, got day " + first);

        long now = 50_000, clock = first * day;
        if (d.tryBegin(now, clock + 6000, true, rnd)) h.fail("the stars do not fall at noon");
        if (d.tryBegin(now, clock + 14000, false, rnd)) h.fail("the stars do not fall in the rain");
        if (d.tryBegin(now, (first - 1) * day + 14000, true, rnd)) h.fail("the stars do not fall a night early");
        if (!d.tryBegin(now, clock + 14000, true, rnd)) h.fail("the stars should fall on their clear night");
        if (!d.falling(now) || !d.falling(now + PassageStars.SHOWER - 1) || d.falling(now + PassageStars.SHOWER)) h.fail("the shower should last its few minutes");
        if (d.harvest(now) || !d.harvest(now + PassageStars.SHOWER) || !d.harvest(now + PassageStars.SHOWER + PassageStars.HARVEST - 1)
                || d.harvest(now + PassageStars.SHOWER + PassageStars.HARVEST)) h.fail("the harvest should follow the stars for three days");
        long gap = d.next() - first;
        if (gap < PassageStars.INTERVAL - PassageStars.JITTER || gap > PassageStars.INTERVAL + PassageStars.JITTER) h.fail("the next shower should be about 24 days on, got " + gap);
        if (d.tryBegin(now + 20, clock + 14020, true, rnd)) h.fail("one shower at a time");

        CompoundTag tag = d.save(new CompoundTag(), h.getLevel().registryAccess());
        var back = PassageStars.Data.load(tag, h.getLevel().registryAccess());
        if (back.next() != d.next() || back.start() != d.start() || back.seed() != d.seed()) h.fail("the calendar should survive a restart");
        if (!back.harvest(now + PassageStars.SHOWER)) h.fail("a harvest in progress should survive a restart");

        var rainy = new PassageStars.Data();
        rainy.tryBegin(0, 0, true, rnd);
        long due = rainy.next();
        if (rainy.tryBegin(0, due * day + 14000, false, rnd)) h.fail("rain hides the stars");
        if (!rainy.tryBegin(day, (due + 1) * day + 14000, true, rnd)) h.fail("a shower lost to rain should come the next clear night");

        var turnedBack = new PassageStars.Data();
        turnedBack.tryBegin(0, 1000L * day, true, rnd);
        turnedBack.tryBegin(0, 10L * day, true, rnd);
        if (turnedBack.next() <= 10 || turnedBack.next() > 10 + PassageStars.INTERVAL) h.fail("turning the clock back should not push the stars away for ages: " + turnedBack.next());

        if (PassageStars.get(h.getLevel()) != PassageStars.get(h.getLevel())) h.fail("the calendar should be kept once per world");
        if (PassageStars.rule == null || !h.getLevel().getGameRules().getBoolean(PassageStars.rule)) h.fail("the Passage Stars game rule should exist and be on");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void theHarvestAfterTheStarsRipensCropsAndBerries(GameTestHelper h) {
        floor(h);
        BlockPos wheat = new BlockPos(3, 1, 5), bush = new BlockPos(7, 1, 5), vine = new BlockPos(5, 3, 5);
        h.setBlock(wheat.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(wheat, Blocks.WHEAT.defaultBlockState());
        h.setBlock(bush.below(), Blocks.GRASS_BLOCK);
        h.setBlock(bush, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 1));
        h.setBlock(vine.above(), Blocks.STONE);
        h.setBlock(vine, Blocks.CAVE_VINES.defaultBlockState().setValue(CaveVines.BERRIES, false));
        BlockPos centre = h.absolutePos(new BlockPos(5, 1, 5));
        RandomSource rnd = RandomSource.create(11);
        if (PassageStars.harvestAround(h.getLevel(), centre.above(40), rnd, 25, 2, 3) != 0) h.fail("there is nothing to ripen in empty air");
        h.startSequence()
                .thenWaitUntil(() -> {
                    PassageStars.harvestAround(h.getLevel(), centre, rnd, 25, 2, 3);
                    if (h.getBlockState(wheat).getValue(CropBlock.AGE) < 1) h.fail("the wheat should grow");
                    if (h.getBlockState(bush).getValue(SweetBerryBushBlock.AGE) < 2) h.fail("the sweet berries should ripen");
                    if (!h.getBlockState(vine).getValue(CaveVines.BERRIES)) h.fail("the cave vine should bear glow berries");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void theCanonAdvancementsAreLoadedAndNamed(GameTestHelper h) {
        var all = h.getLevel().getServer().getAdvancements();
        JsonObject lang = null;
        try (InputStream in = WorldGameTests.class.getResourceAsStream("/assets/" + WitchsAtelier.MODID + "/lang/en_us.json")) {
            if (in != null) lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            h.fail("the English names cannot be read: " + e);
        }
        if (lang == null) h.fail("the English names are missing");
        for (String name : ADVANCEMENTS) {
            var holder = all.get(ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "witch/" + name));
            if (holder == null) h.fail("advancement " + name + " did not load");
            var crit = holder.value().criteria().get(name);
            if (crit == null || crit.trigger() != MagicTrigger.MAGIC.get()) h.fail("advancement " + name + " should wait on the magic trigger");
            for (String part : new String[]{"title", "description"}) {
                if (!lang.has("advancements.witchsatelier." + name + "." + part)) h.fail("advancement " + name + " has no English " + part);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void thoseUnderTheOpenSkySeeThePassageStars(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, new Vec3(5.5, 1, 5.5));
        if (!h.getLevel().canSeeSky(BlockPos.containing(p.getEyePosition()))) h.fail("setup: the test floor should be under the open sky");
        PassageStars.witness(h.getLevel());
        if (!has(p, "passage_stars")) h.fail("a player under the open sky should have seen the Passage Stars");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void feltOnCastingDeeperMagic(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, new Vec3(5.5, 1, 5.5));
        SpellProgram fire = program(Sigil.FIRE, 20, List.of());
        Feats.cast(p, fire, 2, false, true);
        if (has(p, "three_rings_deep")) h.fail("two rings are not three");
        if (has(p, "river_to_sand")) h.fail("a weak glyph in blood is not yet the river turned to sand");
        if (has(p, "steam_and_silence")) h.fail("fire alone is not steam");
        Feats.cast(p, fire, 3, false, false);
        if (!has(p, "three_rings_deep")) h.fail("three nested rings should count");

        SpellProgram mighty = program(Sigil.EARTH, Feats.RIVER_POWER + 5, List.of());
        Feats.cast(p, mighty, 1, false, false);
        if (has(p, "river_to_sand")) h.fail("a mighty glyph in ordinary ink should not count");
        Feats.cast(p, mighty, 1, false, true);
        if (!has(p, "river_to_sand")) h.fail("a mighty glyph in blood ink should count");

        Feats.cast(p, program(Sigil.FIRE, 10, List.of(program(Sigil.WATER, 8, List.of()))), 1, false, false);
        if (!has(p, "steam_and_silence")) h.fail("fire and water in one glyph should count");

        for (int i = 1; i < Feats.TRACES; i++) Feats.cast(p, fire, 1, true, false);
        if (has(p, "read_it_twice")) h.fail("nine traces are not ten");
        Feats.cast(p, fire, 1, true, false);
        if (!has(p, "read_it_twice")) h.fail("the tenth faithful trace should count");

        Feats.chainWoke(p, Feats.CHAIN - 1);
        if (has(p, "many_small_seals")) h.fail("seven linked glyphs are not enough");
        Feats.chainWoke(p, Feats.CHAIN);
        if (!has(p, "many_small_seals")) h.fail("eight linked glyphs waking together should count");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void aGlyphDrawnUnseenBehindTheCurtains(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, new Vec3(5.5, 1, 2.5));
        h.spawn(EntityType.VILLAGER, new BlockPos(5, 1, 8));
        SurfacePlane plane = plane(h);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    GlyphService.inscribe(h.getLevel(), plane, lantern(h, plane, 1.2), p);
                    if (spellsOn(h, plane).isEmpty()) h.fail("the lantern glyph should wake");
                    if (!has(p, "behind_the_curtains")) h.fail("no villager saw it drawn, so it should count");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void aGlyphDrawnInFrontOfAVillagerIsNotBehindTheCurtains(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, new Vec3(5.5, 1, 2.5));
        var villager = h.spawn(EntityType.VILLAGER, new BlockPos(5, 1, 7));
        SurfacePlane plane = plane(h);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    villager.lookAt(EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
                    villager.setYBodyRot(villager.getYHeadRot());
                    if (Witnesses.saw(p) != 1) h.fail("setup: the villager should see the witch at work");
                    GlyphService.inscribe(h.getLevel(), plane, lantern(h, plane, 1.2), p);
                    if (spellsOn(h, plane).isEmpty()) h.fail("the lantern glyph should wake");
                    if (has(p, "behind_the_curtains")) h.fail("a villager saw it drawn");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void aMightyGlyphInBloodTurnsTheRiverToSand(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, new Vec3(5.5, 1, 0.5));
        SurfacePlane plane = plane(h);
        h.startSequence()
                .thenExecuteAfter(1, () -> {
                    GlyphService.inscribe(h.getLevel(), plane, lantern(h, plane, 2.6), InkType.BLOOD, p);
                    var woke = spellsOn(h, plane);
                    if (woke.isEmpty()) h.fail("the blood glyph should wake");
                    double power = woke.get(0).program.power();
                    if (power < Feats.RIVER_POWER) h.fail("setup: the glyph should be mighty, power " + power);
                    if (!has(p, "river_to_sand")) h.fail("a mighty glyph in blood should count, power " + power);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void theBellTollsAndTimeForgives(GameTestHelper h) {
        ServerPlayer p = survivor(h, new Vec3(5.5, 1, 5.5));
        Taboo.add(p, Taboo.HUNTED - 1);
        if (has(p, "the_bell_tolls")) h.fail("the bell tolls only once the debt is great enough");
        Taboo.add(p, 1);
        if (!has(p, "the_bell_tolls")) h.fail("the bell should toll when the Knights are sent for");
        p.getPersistentData().putInt(Taboo.KEY, 2);
        p.tickCount = 1200;
        Taboo.onPlayerTick(new PlayerTickEvent.Post(p));
        if (has(p, "forgiven_by_time")) h.fail("a debt not yet gone is not forgiven");
        p.tickCount = 2400;
        Taboo.onPlayerTick(new PlayerTickEvent.Post(p));
        if (Taboo.of(p) != 0) h.fail("the debt should fade to nothing, left " + Taboo.of(p));
        if (!has(p, "forgiven_by_time")) h.fail("outliving the debt should count");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void dyingForfeitsForgiveness(GameTestHelper h) {
        var before = h.makeMockPlayer(GameType.SURVIVAL);
        before.getPersistentData().putBoolean(Feats.WAS_HUNTED, true);
        before.getPersistentData().putInt(Feats.TRACED, 4);
        var dead = h.makeMockPlayer(GameType.SURVIVAL);
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(dead, before, true));
        if (dead.getPersistentData().getBoolean(Feats.WAS_HUNTED)) h.fail("dying should forfeit the forgiveness of time");
        if (dead.getPersistentData().getInt(Feats.TRACED) != 4) h.fail("the count of traces should survive death");
        var back = h.makeMockPlayer(GameType.SURVIVAL);
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(back, before, false));
        if (!back.getPersistentData().getBoolean(Feats.WAS_HUNTED)) h.fail("coming back from the End is not dying");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reInkingADarkWardingTower(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, new Vec3(5.5, 1, 3.5));
        var well = new ItemStack(ModItems.INKWELL.get());
        InkwellItem.setContents(well, new LoadedInk(InkType.CONJURING, 2048));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.INK_PEN.get()));
        p.setItemInHand(InteractionHand.OFF_HAND, well);
        BlockPos at = new BlockPos(5, 1, 6);
        h.setBlock(at, ModBlocks.WARDING_TOWER.get());
        var tower = (BeastWardingTowerBlock.Entity) h.getLevel().getBlockEntity(h.absolutePos(at));
        if (tower == null || tower.charge() != 0) h.fail("setup: the tower should start dark");
        h.useBlock(at, p);
        if (tower.charge() <= 0) h.fail("the ink should wake the tower");
        if (!has(p, "ward_on_the_road")) h.fail("re-inking a dark tower should count");
        h.succeed();
    }
}
