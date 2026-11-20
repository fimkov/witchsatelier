package com.witchcraft.worldgen;

import com.witchcraft.glyph.GlyphService;
import com.witchcraft.glyph.page.GlyphLibrary;
import com.witchcraft.glyph.page.GlyphPages;
import com.witchcraft.glyph.recognize.Pt;
import com.witchcraft.glyph.surface.SurfacePlane;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Glyphs that world generation draws onto walls and floors (in ateliers and ruins). World generation runs off
 * the main thread, so the ink is queued here and laid down on the next tick of the level.
 */
public final class WorldGlyphs {
    private record Job(ResourceKey<Level> dim, SurfacePlane plane, List<double[]> strokes) {}

    private static final ConcurrentLinkedQueue<Job> QUEUE = new ConcurrentLinkedQueue<>();

    private WorldGlyphs() {}

    /**
     * Queues a library glyph on the {@code face} of the block at {@code pos}, {@code radius} blocks across,
     * with its ring left open (a prepared glyph) when {@code open}.
     */
    public static void draw(ResourceKey<Level> dim, BlockPos pos, Direction face, String recipe, double radius, boolean open, RandomSource rnd) {
        GlyphLibrary.Recipe r = GlyphLibrary.byName(recipe);
        if (r == null) return;
        GlyphPages.Page page = GlyphLibrary.draw(r, rnd, open, com.witchcraft.glyph.ink.InkType.LASTING);
        SurfacePlane plane = SurfacePlane.of(pos, face);
        Pt c = plane.toUV(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)));
        double k = radius / 0.85;
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
        QUEUE.add(new Job(dim, plane, strokes));
    }

    public static void tick(ServerLevel level) {
        if (QUEUE.isEmpty()) return;
        for (var it = QUEUE.iterator(); it.hasNext(); ) {
            Job j = it.next();
            if (!j.dim().equals(level.dimension())) continue;
            it.remove();
            GlyphService.inscribe(level, j.plane(), j.strokes(), com.witchcraft.glyph.ink.InkType.LASTING, null);
        }
    }
}
