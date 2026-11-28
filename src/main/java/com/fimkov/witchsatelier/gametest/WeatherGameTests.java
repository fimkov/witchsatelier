package com.fimkov.witchsatelier.gametest;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.InkWear;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.storage.GlyphLevelData;
import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.spell.ActiveSpell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Glyphs out in the weather. Weather belongs to the whole level, so each of these tests keeps a batch of its own: it
 * snows or rains only while that test runs, and no other test sees it.
 */
@GameTestHolder(WitchsAtelier.MODID)
@PrefixGameTestTemplate(false)
public final class WeatherGameTests {
    private WeatherGameTests() {}

    private static final String SNOWFALL = "snowfall", RAINFALL = "rainfall";
    private static int accumulation = 1;
    private static double boost = 1;
    /** The ground a test gave another biome, and what it was, to be given back however the test ends. */
    private static BlockPos filledLo, filledHi;
    private static Holder<Biome> filledWas;

    private static void weatherBegins(ServerLevel level, boolean raining) {
        GameRules.IntegerValue rule = level.getGameRules().getRule(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT);
        accumulation = rule.get();
        // no snow piles up on its own while the test watches, or an open glyph could be buried halfway through
        rule.set(0, level.getServer());
        if (raining) startWeather(level);
        else clearWeather(level);
        boost = GlyphService.wearBoost;
        GlyphService.wearBoost = 3;
    }

    private static void startWeather(ServerLevel level) {
        level.setWeatherParameters(0, 1_000_000, true, false);
        level.setRainLevel(1);
    }

    private static void clearWeather(ServerLevel level) {
        level.setWeatherParameters(1_000_000, 0, false, false);
        level.setRainLevel(0);
    }

    private static void weatherEnds(ServerLevel level) {
        level.getGameRules().getRule(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT).set(accumulation, level.getServer());
        clearWeather(level);
        GlyphService.wearBoost = boost;
        if (filledWas != null) FillBiomeCommand.fill(level, filledLo, filledHi, filledWas);
        filledWas = null;
    }

    @BeforeBatch(batch = SNOWFALL)
    public static void snowBegins(ServerLevel level) {
        weatherBegins(level, true);
    }

    @AfterBatch(batch = SNOWFALL)
    public static void snowEnds(ServerLevel level) {
        weatherEnds(level);
    }

    @BeforeBatch(batch = RAINFALL)
    public static void rainBegins(ServerLevel level) {
        // the rain starts only once the glyphs are awake: rain washes ink that is not
        weatherBegins(level, false);
    }

    @AfterBatch(batch = RAINFALL)
    public static void rainEnds(ServerLevel level) {
        weatherEnds(level);
    }

    /**
     * Turns the test's ground into {@code biome}. A biome is kept in cells of four blocks, and a block reads one of the
     * cells around its own at random (the zoom noise), so the new ground reaches a cell and more past every glyph,
     * wherever the test is set down.
     */
    private static void fillBiome(GameTestHelper h, ResourceKey<Biome> biome) {
        ServerLevel level = h.getLevel();
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
        BlockPos lo = h.absolutePos(new BlockPos(-8, -4, -8)), hi = h.absolutePos(new BlockPos(18, 12, 18));
        filledLo = lo;
        filledHi = hi;
        filledWas = level.getBiome(h.absolutePos(new BlockPos(5, 1, 5)));
        FillBiomeCommand.fill(level, lo, hi, level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome));
    }

    private static void giveBackBiome(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (ActiveSpell s : GlyphService.activeSpells(level)) {
            if (s.program.origin().distanceTo(h.absoluteVec(new Vec3(5.5, 1, 5.5))) < 8) GlyphService.stop(level, s);
        }
        if (filledWas != null) FillBiomeCommand.fill(level, filledLo, filledHi, filledWas);
        filledWas = null;
    }

    private static int mostWorn(GlyphLevelData data, List<InkStroke> ink) {
        int most = 0;
        for (InkStroke st : ink) {
            byte[] w = data.wear(st.id());
            if (w != null) for (byte b : w) most = Math.max(most, InkWear.amount(b));
        }
        return most;
    }

    private static List<InkStroke> glyph(GameTestHelper h, BlockPos at, InkType ink) {
        BlockPos abs = h.absolutePos(at);
        SurfacePlane plane = SurfacePlane.of(abs, Direction.UP);
        Pt c = plane.toUV(Vec3.atCenterOf(abs).add(0, 0.5, 0));
        return GlyphService.inscribe(h.getLevel(), plane, new GlyphSketch(c, 1.0).ring(0, 362).sigil(Sigil.LIGHT, 0.6).strokes, ink, null);
    }

    private static boolean running(GameTestHelper h, BlockPos at) {
        Vec3 o = h.absoluteVec(Vec3.atCenterOf(at).add(0, 0.5, 0));
        for (ActiveSpell s : GlyphService.activeSpells(h.getLevel())) if (s.program.origin().distanceTo(o) < 0.8) return true;
        return false;
    }

    /** Snow does not wear ink: a glyph out under falling snow keeps every line and keeps working. */
    @GameTest(template = "empty", timeoutTicks = 1200, batch = SNOWFALL, skyAccess = true)
    public static void snowfallLeavesOpenGlyphsUnworn(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        fillBiome(h, Biomes.SNOWY_PLAINS);
        BlockPos open = new BlockPos(2, 0, 2), other = new BlockPos(8, 0, 8);
        var data = GlyphLevelData.get(level);
        List<InkStroke> openInk = glyph(h, open, InkType.CONJURING);
        List<InkStroke> otherInk = glyph(h, other, InkType.CONJURING);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    for (BlockPos at : List.of(open, other)) if (!running(h, at)) h.fail("every glyph should wake, not " + at);
                    BlockPos front = h.absolutePos(open.above());
                    if (!level.isRaining() || !level.canSeeSky(front)
                            || level.getBiome(front).value().getPrecipitationAt(front) != Biome.Precipitation.SNOW)
                        h.fail("snow should be falling on the open glyph, raining=" + level.isRaining()
                                + " sky=" + level.canSeeSky(front) + " biome=" + level.getBiome(front).getRegisteredName());
                })
                // fifteen weather turns at three times the usual wear: rain would have worn these glyphs many times over
                .thenIdle(600)
                .thenExecute(() -> {
                    if (mostWorn(data, openInk) > 0) h.fail("snow should not wear ink: " + mostWorn(data, openInk));
                    if (mostWorn(data, otherInk) > 0) h.fail("snow should not wear ink: " + mostWorn(data, otherInk));
                    for (BlockPos at : List.of(open, other)) if (!running(h, at)) h.fail("the glyphs in the snow should still hold, not " + at);
                    giveBackBiome(h);
                })
                .thenSucceed();
    }

    /**
     * Rain wears an open glyph, but not one under waterproof ink, a snow layer lying on the ink, or a roof.
     */
    @GameTest(template = "empty", timeoutTicks = 2400, batch = RAINFALL, skyAccess = true)
    public static void rainWearsOpenGlyphsOnly(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        fillBiome(h, Biomes.PLAINS);
        BlockPos open = new BlockPos(2, 0, 2), proof = new BlockPos(8, 0, 2), buried = new BlockPos(2, 0, 8), roofed = new BlockPos(8, 0, 8);
        var data = GlyphLevelData.get(level);
        List<InkStroke> openInk = glyph(h, open, InkType.CONJURING);
        List<InkStroke> proofInk = glyph(h, proof, InkType.WATERPROOF);
        List<InkStroke> buriedInk = glyph(h, buried, InkType.CONJURING);
        List<InkStroke> roofedInk = glyph(h, roofed, InkType.CONJURING);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    for (BlockPos at : List.of(open, proof, buried, roofed)) if (!running(h, at)) h.fail("every glyph should wake, not " + at);
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                        h.setBlock(buried.offset(dx, 1, dz), Blocks.SNOW);
                        h.setBlock(roofed.offset(dx, 3, dz), Blocks.STONE);
                    }
                    startWeather(level);
                })
                .thenExecuteAfter(1, () -> {
                    if (!level.isRainingAt(h.absolutePos(open.above()))) h.fail("rain should be falling on the open glyph");
                    if (level.isRainingAt(h.absolutePos(roofed.above()))) h.fail("no rain falls under a roof");
                    if (!GlyphService.buried(level, SurfacePlane.of(h.absolutePos(buried), Direction.UP), h.absolutePos(buried.above())))
                        h.fail("a snow layer lying on a glyph covers it");
                })
                .thenWaitUntil(() -> {
                    if (mostWorn(data, openInk) == 0) h.fail("the open glyph should wear in the rain");
                })
                // ten more weather turns, so ink the rain could reach would have had every chance to wear
                .thenIdle(400)
                .thenExecute(() -> {
                    if (mostWorn(data, proofInk) > 0) h.fail("waterproof ink keeps rain off: " + mostWorn(data, proofInk));
                    if (mostWorn(data, buriedInk) > 0) h.fail("ink under a snow layer is out of the rain: " + mostWorn(data, buriedInk));
                    if (mostWorn(data, roofedInk) > 0) h.fail("ink under a roof is out of the rain: " + mostWorn(data, roofedInk));
                    for (BlockPos at : List.of(proof, buried, roofed)) if (!running(h, at)) h.fail("the sheltered glyphs should still hold, not " + at);
                    clearWeather(level);
                    giveBackBiome(h);
                })
                .thenSucceed();
    }
}
