package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A glyph drawn small in a screen (recipe viewers): the grimoire's own drawing of it, in dark ink. */
public final class GlyphIcon {
    private static final Map<String, List<float[]>> CACHE = new HashMap<>();
    /** How far the ring of a grimoire drawing reaches from its centre, in page units. */
    private static final float RING = 0.88f;

    private GlyphIcon() {}

    /** Draws {@code r} filling a {@code size}-pixel square whose top-left corner is at (x, y). */
    public static void draw(GuiGraphics g, GlyphLibrary.Recipe r, int x, int y, int size, int argb) {
        List<float[]> strokes = CACHE.computeIfAbsent(r.name(), k -> {
            var page = GlyphLibrary.draw(r, RandomSource.create(k.hashCode()), false, InkType.CONJURING);
            return GlyphLibrary.strokesOf(page).stream().map(s -> {
                float[] f = new float[s.size() * 2];
                for (int i = 0; i < s.size(); i++) {
                    f[i * 2] = (float) s.get(i).x();
                    f[i * 2 + 1] = (float) s.get(i).y();
                }
                return f;
            }).toList();
        });
        float cx = x + size / 2f, cy = y + size / 2f, scale = size / 2f / RING * 0.94f;
        float w = Math.max(1.1f, size / 60f);
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        for (float[] s : strokes) line(vc, m, s, cx, cy, scale, w, argb);
        g.flush();
    }

    private static void line(VertexConsumer vc, Matrix4f m, float[] pts, float cx, float cy, float r, float w, int argb) {
        int n = pts.length / 2;
        float hw = w / 2;
        for (int i = 1; i < n; i++) {
            float x0 = cx + pts[(i - 1) * 2] * r, y0 = cy - pts[(i - 1) * 2 + 1] * r;
            float x1 = cx + pts[i * 2] * r, y1 = cy - pts[i * 2 + 1] * r;
            float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-3f) continue;
            dx /= len;
            dy /= len;
            x0 -= dx * hw * 0.6f;
            y0 -= dy * hw * 0.6f;
            x1 += dx * hw * 0.6f;
            y1 += dy * hw * 0.6f;
            float nx = -dy * hw, ny = dx * hw;
            // counter-clockwise on screen, or the GUI's back-face culling drops the quad
            vc.addVertex(m, x0 - nx, y0 - ny, 0).setColor(argb);
            vc.addVertex(m, x0 + nx, y0 + ny, 0).setColor(argb);
            vc.addVertex(m, x1 + nx, y1 + ny, 0).setColor(argb);
            vc.addVertex(m, x1 - nx, y1 - ny, 0).setColor(argb);
        }
    }
}
