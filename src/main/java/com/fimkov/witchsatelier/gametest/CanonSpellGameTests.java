package com.fimkov.witchsatelier.gametest;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.spell.ActiveSpell;
import com.fimkov.witchsatelier.spell.SpellProgram;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(WitchsAtelier.MODID)
@PrefixGameTestTemplate(false)
public final class CanonSpellGameTests {
    private CanonSpellGameTests() {}

    private static SurfacePlane floor(GameTestHelper h) {
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
        return SurfacePlane.of(h.absolutePos(new BlockPos(5, 0, 5)), Direction.UP);
    }

    private static Vec3 middle(GameTestHelper h) {
        return h.absoluteVec(new Vec3(5.5, 1, 5.5));
    }

    private static void draw(GameTestHelper h, SurfacePlane plane, String recipe, double radius) {
        Pt c = plane.toUV(middle(h));
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
        GlyphService.inscribe(h.getLevel(), plane, strokes, InkType.LASTING, null);
    }

    private static void lit(GameTestHelper h, SurfacePlane plane, String recipe) {
        if (spell(h, plane) != null) return;
        draw(h, plane, recipe, 1.4);
        h.fail("waiting for " + recipe + " to light without a misfire");
    }

    private static ActiveSpell spell(GameTestHelper h, SurfacePlane plane) {
        return GlyphService.activeSpells(h.getLevel()).stream()
                .filter(s -> plane.equals(s.plane) && s.program.origin().distanceTo(middle(h)) < 3).findFirst().orElse(null);
    }

    private static int count(GameTestHelper h, Block block) {
        int n = 0;
        for (int x = 0; x <= 10; x++) for (int y = 1; y <= 14; y++) for (int z = 0; z <= 10; z++) if (h.getBlockState(new BlockPos(x, y, z)).is(block)) n++;
        return n;
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aPyreballCooksWhatIsDroppedIntoIt(GameTestHelper h) {
        SurfacePlane plane = floor(h);
        draw(h, plane, "pyreball", 1.4);
        Vec3 m = middle(h);
        ItemEntity beef = new ItemEntity(h.getLevel(), m.x, m.y + 1.8, m.z, new ItemStack(Items.BEEF, 3));
        beef.setPickUpDelay(32767);
        h.startSequence()
                .thenWaitUntil(() -> lit(h, plane, "pyreball"))
                .thenExecute(() -> {
                    var s = spell(h, plane);
                    if (s == null || s.program.form() != SpellProgram.Form.LIFT || s.program.element() != com.fimkov.witchsatelier.glyph.model.Sigil.FIRE)
                        h.fail("the seal should wake as lifted fire: " + (s == null ? "nothing" : s.program.summary()));
                    h.getLevel().addFreshEntity(beef);
                })
                .thenWaitUntil(() -> {
                    if (count(h, Blocks.LIGHT) == 0) h.fail("the fireball should light the room");
                    if (!beef.getItem().is(Items.COOKED_BEEF)) h.fail("the beef should come out cooked, it is " + beef.getItem());
                })
                .thenExecute(() -> { if (beef.getItem().getCount() != 3) h.fail("all of the stack should cook"); })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aFloatglowLampHangsAboveItsRing(GameTestHelper h) {
        SurfacePlane plane = floor(h);
        draw(h, plane, "floatglow_lamp", 1.4);
        h.startSequence()
                .thenWaitUntil(() -> lit(h, plane, "floatglow_lamp"))
                .thenWaitUntil(() -> {
                    boolean high = false;
                    for (int y = 2; y <= 14; y++) for (int x = 2; x <= 8; x++) for (int z = 2; z <= 8; z++)
                        if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.LIGHT)) high = true;
                    if (!high) h.fail("the lamp should hang well above the floor");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void risingWaterCarriesAPigUp(GameTestHelper h) {
        SurfacePlane plane = floor(h);
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(5, 1, 5));
        double start = pig.getY();
        draw(h, plane, "rising_water", 1.4);
        h.startSequence()
                .thenWaitUntil(() -> lit(h, plane, "rising_water"))
                .thenWaitUntil(() -> { if (pig.getY() < start + 2) h.fail("the water should carry the pig up, it is at " + (pig.getY() - start)); })
                .thenExecute(() -> { if (count(h, Blocks.WATER) > 0) h.fail("the column must not leave water blocks behind"); })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void aWaterCageHoldsTheNearestCreature(GameTestHelper h) {
        SurfacePlane plane = floor(h);
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(6, 1, 5));
        draw(h, plane, "water_cage", 1.4);
        Vec3 cage = middle(h).add(0, 1.4, 0);
        h.startSequence()
                .thenWaitUntil(() -> lit(h, plane, "water_cage"))
                .thenWaitUntil(() -> { if (pig.getY() < middle(h).y + 0.5) h.fail("the cage should lift the pig off the floor"); })
                .thenExecuteFor(40, () -> pig.setDeltaMovement(0.4, 0, 0))
                .thenExecute(() -> {
                    if (pig.position().distanceTo(cage) > 2.5) h.fail("the pig should still be held, it is " + pig.position().distanceTo(cage) + " away");
                    if (pig.getAirSupply() >= pig.getMaxAirSupply()) h.fail("a pig in the water ball should run short of air");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void aWallOfWindKeepsACreatureIn(GameTestHelper h) {
        SurfacePlane plane = floor(h);
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(5, 1, 5));
        draw(h, plane, "wall_of_wind", 1.4);
        h.startSequence()
                .thenWaitUntil(() -> lit(h, plane, "wall_of_wind"))
                .thenExecute(() -> {
                    var s = spell(h, plane);
                    if (s == null || !s.program.has(SpellProgram.WALL)) h.fail("the glyph should raise a wall of wind: " + (s == null ? "nothing" : s.program.summary()));
                })
                .thenExecuteFor(80, () -> { pig.setDeltaMovement(0.35, pig.getDeltaMovement().y, 0); pig.hurtMarked = true; })
                .thenExecute(() -> {
                    double ring = 1.5 + spell(h, plane).program.radius() * 1.2;
                    if (flat(pig.position(), middle(h)) > ring + 0.6) h.fail("the pig got through the wind, " + flat(pig.position(), middle(h)) + " out of " + ring);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void aWaterHorseLastsWhileItsGlyphIsWhole(GameTestHelper h) {
        SurfacePlane plane = floor(h);
        draw(h, plane, "water_horse", 1.4);
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).expandTowards(10, 6, 10);
        h.startSequence()
                .thenWaitUntil(() -> lit(h, plane, "water_horse"))
                .thenWaitUntil(() -> {
                    var horses = h.getLevel().getEntitiesOfClass(com.fimkov.witchsatelier.entity.WaterHorseEntity.class, area);
                    var lit = spell(h, plane);
                    if (horses.size() != 1) h.fail("one water horse should step out, got " + horses.size() + ", spell: " + (lit == null ? "none" : lit.program.summary() + " mods " + lit.program.mods() + " age " + lit.age()));
                    if (!horses.get(0).isSaddled()) h.fail("the water horse should come saddled");
                })
                .thenExecute(() -> {
                    var s = spell(h, plane);
                    if (s == null) h.fail("the glyph should be lit");
                    com.fimkov.witchsatelier.glyph.GlyphService.eraseStrokes(h.getLevel(), List.copyOf(s.strokes));
                })
                .thenWaitUntil(() -> {
                    if (!h.getLevel().getEntitiesOfClass(com.fimkov.witchsatelier.entity.WaterHorseEntity.class, area).isEmpty()) h.fail("with its glyph gone the horse should fall apart");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void aRingOfFireSparesItsMiddle(GameTestHelper h) {
        SurfacePlane plane = floor(h);
        Pig inside = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(5, 1, 5));
        Pig outside = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(8, 1, 5));
        draw(h, plane, "ring_of_fire", 1.4);
        h.startSequence()
                .thenWaitUntil(() -> lit(h, plane, "ring_of_fire"))
                .thenExecute(() -> {
                    int m = spell(h, plane).program.mods();
                    if ((m & SpellProgram.REGION_OUT) == 0 || (m & SpellProgram.REGION_IN) != 0) h.fail("regions opening outward should keep the middle: mods " + m);
                })
                .thenWaitUntil(() -> { if (!outside.isOnFire()) h.fail("the pig outside the ring should burn"); })
                .thenExecute(() -> { if (inside.isOnFire()) h.fail("the pig in the middle should be spared"); })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aTreadClosedOverSomeoneKnowsOnlyThem(GameTestHelper h) {
        SurfacePlane plane = floor(h);
        Pig keyed = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(5, 1, 5));
        Pig stranger = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 1));
        Vec3 m = middle(h), away = h.absoluteVec(new Vec3(9.5, 1, 9.5));
        draw(h, plane, "snare_of_flame", 1.4);
        h.startSequence()
                .thenWaitUntil(() -> lit(h, plane, "snare_of_flame"))
                .thenExecute(() -> {
                    var s = spell(h, plane);
                    if (!keyed.getUUID().equals(s.key)) h.fail("the snare should remember the pig standing in it, key " + s.key);
                    keyed.teleportTo(away.x, away.y, away.z);
                    keyed.clearFire();
                })
                .thenWaitUntil(() -> { if (!spell(h, plane).waiting()) h.fail("left alone, the snare should set itself again"); })
                .thenExecute(() -> {
                    keyed.clearFire();
                    stranger.teleportTo(m.x, m.y, m.z);
                })
                .thenExecuteAfter(30, () -> {
                    if (!spell(h, plane).waiting()) h.fail("a creature it does not know should not spring it");
                    stranger.teleportTo(away.x, away.y, away.z - 3);
                    keyed.teleportTo(m.x, m.y, m.z);
                })
                .thenWaitUntil(() -> { if (spell(h, plane).waiting()) h.fail("the one it knows should spring it"); })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void wallBendPullsAWallOutOfTheFloor(GameTestHelper h) {
        SurfacePlane plane = floor(h);
        draw(h, plane, "wall_bend", 1.4);
        h.startSequence()
                .thenWaitUntil(() -> lit(h, plane, "wall_bend"))
                .thenExecute(() -> {
                    var s = spell(h, plane);
                    if (s == null || !s.program.has(SpellProgram.BEND)) h.fail("the glyph should bend a wall: " + (s == null ? "nothing" : s.program.summary()));
                })
                .thenWaitUntil(() -> { if (count(h, Blocks.STONE) < 6) h.fail("a stone wall should grow out of the stone floor, " + count(h, Blocks.STONE) + " so far"); })
                .thenSucceed();
    }
}
