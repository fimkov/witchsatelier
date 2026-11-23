package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.compat.Ships;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.InkWear;
import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import com.fimkov.witchsatelier.glyph.surface.StrokeState;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.UUID;
import java.util.function.UnaryOperator;

public final class GlyphRenderer {
    private static final double OFFSET = 0.0025;
    private static final double MAX_DIST_SQR = 96 * 96;
    private static final int SPENT = 0x8C5A5A60;

    private GlyphRenderer() {}

    static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        partial = pt;
        ClientDrawing.sample(pt);

        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        Matrix4f pose = ps.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());

        double time = (mc.level != null ? mc.level.getGameTime() : 0) + pt;
        float w = (float) GlyphService.INK_WIDTH;
        UUID me = mc.player != null ? mc.player.getUUID() : null;

        TraceGuide.render(vc, pose, cam, w);
        for (ClientGlyphs.Entry en : ClientGlyphs.INSTANCE.all()) {
            InkStroke s = en.stroke();

            if (s.ink().invisible && s.state() != StrokeState.ACTIVE && !s.author().equals(me)) continue;
            SurfacePlane plane = en.plane();
            double[] uv = en.abs();
            if (seen(plane, uv[0], uv[1], 0).distanceToSqr(cam) > MAX_DIST_SQR) continue;
            byte[] worn = ClientGlyphs.INSTANCE.wear(s.id());
            if (worn != null && worn.length != s.size()) worn = null;
            switch (s.state()) {
                case IDLE -> drawInk(vc, pose, cam, plane, uv, s.size(), w, s.ink().color, OFFSET, worn, s.id(), s.ink().color);
                case SPENT -> drawInk(vc, pose, cam, plane, uv, s.size(), w, SPENT, OFFSET, worn, s.id(), SPENT);
                case ACTIVE -> {
                    if ((s.color() >>> 24 & 0xFF) == 1) {
                        double breath = 0.5 + 0.5 * Math.sin(time * 0.07 + s.id());
                        drawStroke(vc, pose, cam, plane, uv, s.size(), w * 2.6f, withAlpha(s.color() & 0xFFFFFF, (int) (14 + 26 * breath)), OFFSET * 0.6);
                        drawStroke(vc, pose, cam, plane, uv, s.size(), w, withAlpha(mix(s.ink().color, s.color() & 0xFFFFFF, 0.15 + 0.15 * breath), 255), OFFSET * 1.4);
                        break;
                    }

                    double left = en.duration() > 0 ? Math.max(0, Math.min(1, (en.until() - time) / en.duration())) : 1;
                    double life = 0.25 + 0.75 * left;
                    if (left < 0.2) life *= 0.6 + 0.4 * Math.abs(Math.sin(time * 0.9));
                    double pulse = 0.75 + 0.25 * Math.sin(time * 0.25 + s.id());
                    int glow = withAlpha(s.color(), (int) (80 * pulse * life));
                    int core = withAlpha(mix(s.ink().color, brighten(s.color(), pulse), life), 255);

                    double since = time - (en.until() - en.duration());

                    double lit = en.duration() > 0 && since >= 0 ? Math.min(1, since / IGNITE) : 1;
                    if (lit >= 1) {
                        if (worn == null) drawStroke(vc, pose, cam, plane, uv, s.size(), w * 3.5f, glow, OFFSET * 0.6);
                        else drawFaded(vc, pose, cam, plane, uv, s.size(), w * 3.5f, glow, OFFSET * 0.6, worn);
                        drawInk(vc, pose, cam, plane, uv, s.size(), w, core, OFFSET * 1.4, worn, s.id(), withAlpha(s.ink().color, 255));
                    } else {
                        ignite(vc, pose, cam, plane, uv, s.size(), w, glow, core, withAlpha(s.ink().color, 255), lit);
                    }
                }
            }
        }

        BadAppleShow.render(vc, pose, cam, w);

        for (ClientDrawing.Sent sent : ClientDrawing.sent()) {
            if (sent.n() > 0) drawStroke(vc, pose, cam, sent.plane(), sent.uv(), sent.n(), w, sent.color(), OFFSET * 1.2);
        }

        SurfacePlane drawing = ClientDrawing.plane();
        int n = ClientDrawing.pointCount();
        if (drawing != null && n > 0) {
            double[] uv = new double[n * 2];
            for (int i = 0; i < n; i++) { uv[i * 2] = ClientDrawing.u(i); uv[i * 2 + 1] = ClientDrawing.v(i); }
            if (ClientDrawing.imagining()) {
                double lift = 0.14;
                var c = ClientDrawing.imaginedCircle();
                if (c != null) {
                    int seg = 96;
                    double[] ring = new double[(seg + 1) * 2];
                    for (int i = 0; i <= seg; i++) {
                        double a = Math.PI * 2 * i / seg;
                        ring[i * 2] = c.center().x() + Math.cos(a) * c.radius();
                        ring[i * 2 + 1] = c.center().y() + Math.sin(a) * c.radius();
                    }
                    drawStroke(vc, pose, cam, drawing, ring, seg + 1, w * 0.6f, 0x28B8A8FF, lift);
                }
                drawStroke(vc, pose, cam, drawing, uv, n, w * 3f, 0x309A84FF, lift);
                drawStroke(vc, pose, cam, drawing, uv, n, w * 0.8f, 0xB0D8CCFF, lift + 0.002);
            } else {
                drawStroke(vc, pose, cam, drawing, uv, n, w, ClientDrawing.currentInk().color, OFFSET * 1.2);
            }
        }
        var hover = ClientDrawing.hover();
        boolean aimingRag = false;
        if (drawing == null) {
            SurfacePlane[] pl = new SurfacePlane[1];
            hover = ClientDrawing.ragHover(pl);
            drawing = pl[0];
            aimingRag = hover != null;
        }
        if (drawing != null && hover != null) {
            if (ClientDrawing.erasingNow() || aimingRag) {
                double r = ClientDrawing.eraseRadius();
                double[] c = new double[34 * 2];
                for (int i = 0; i < 34; i++) {
                    double a = i * Math.PI * 2 / 33;
                    c[i * 2] = hover.x() + Math.cos(a) * r;
                    c[i * 2 + 1] = hover.y() + Math.sin(a) * r;
                }
                drawStroke(vc, pose, cam, drawing, c, 34, w * 0.5f, 0xC0FFFFFF, OFFSET * 2);
            } else {
                double h = 0.06;
                double[] box = {hover.x() - h, hover.y() - h, hover.x() + h, hover.y() - h, hover.x() + h, hover.y() + h,
                        hover.x() - h, hover.y() + h, hover.x() - h, hover.y() - h};
                drawStroke(vc, pose, cam, drawing, box, 5, w * 0.5f, 0xE0FFFFFF, OFFSET * 2);
            }
        }

        buffers.endBatch(RenderType.debugQuads());
        ps.popPose();
    }

    private static final double IGNITE = 16;

    private static void ignite(VertexConsumer vc, Matrix4f pose, Vec3 cam, SurfacePlane plane, double[] uv, int n, float w,
                               int glow, int core, int ink, double frac) {
        double total = 0;
        for (int i = 1; i < n; i++) total += Math.hypot(uv[i * 2] - uv[i * 2 - 2], uv[i * 2 + 1] - uv[i * 2 - 1]);
        double front = total * (frac * frac * (3 - 2 * frac));
        double acc = 0;
        int cut = n;
        double fu = uv[0], fv = uv[1];
        for (int i = 1; i < n; i++) {
            double seg = Math.hypot(uv[i * 2] - uv[i * 2 - 2], uv[i * 2 + 1] - uv[i * 2 - 1]);
            if (acc + seg >= front) {
                double t = seg > 0 ? (front - acc) / seg : 0;
                fu = uv[i * 2 - 2] + (uv[i * 2] - uv[i * 2 - 2]) * t;
                fv = uv[i * 2 - 1] + (uv[i * 2 + 1] - uv[i * 2 - 1]) * t;
                cut = i;
                break;
            }
            acc += seg;
        }

        double[] litPts = new double[(cut + 1) * 2];
        System.arraycopy(uv, 0, litPts, 0, cut * 2);
        litPts[cut * 2] = fu;
        litPts[cut * 2 + 1] = fv;
        drawStroke(vc, pose, cam, plane, litPts, cut + 1, w * 3.5f, glow, OFFSET * 0.6);
        drawStroke(vc, pose, cam, plane, litPts, cut + 1, w, core, OFFSET * 1.4);
        if (cut < n) {
            double[] rest = new double[(n - cut + 1) * 2];
            rest[0] = fu;
            rest[1] = fv;
            System.arraycopy(uv, cut * 2, rest, 2, (n - cut) * 2);
            drawStroke(vc, pose, cam, plane, rest, n - cut + 1, w, ink, OFFSET);
            double h = w * 2.2;
            double[] spark = {fu - h, fv, fu, fv - h, fu + h, fv, fu, fv + h, fu - h, fv};
            drawStroke(vc, pose, cam, plane, spark, 5, w * 1.6f, 0xF0FFFFFF, OFFSET * 2);
        }
    }

    private static float partial;
    private static UnaryOperator<Vec3> frame;

    static Vec3 seen(SurfacePlane plane, double u, double v, double offset) {
        Vec3 kept = plane.toWorld(u, v, offset);
        var level = Minecraft.getInstance().level;
        var f = level == null ? null : Ships.toWorld(level, BlockPos.containing(plane.toWorld(u, v, -0.01)), partial);
        return f == null ? kept : f.apply(kept);
    }

    static void drawStroke(VertexConsumer vc, Matrix4f pose, Vec3 cam, SurfacePlane plane, double[] uv, int n,
                                   float width, int argb, double offset) {
        var level = Minecraft.getInstance().level;
        frame = level == null || n == 0 ? null
                : Ships.toWorld(level, BlockPos.containing(plane.toWorld(uv[0], uv[1], -0.01)), partial);
        try {
            drawStrokeAt(vc, pose, cam, plane, uv, n, width, argb, offset);
        } finally {
            frame = null;
        }
    }

    private static void drawStrokeAt(VertexConsumer vc, Matrix4f pose, Vec3 cam, SurfacePlane plane, double[] uv, int n,
                                     float width, int argb, double offset) {
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        double hw = width / 2.0;
        if (n == 1) {
            quad(vc, pose, cam, plane, uv[0] - hw, uv[1] - hw, uv[0] + hw, uv[1] - hw, uv[0] + hw, uv[1] + hw, uv[0] - hw, uv[1] + hw, offset, r, g, b, a);
            return;
        }
        for (int i = 1; i < n; i++) {
            double u0 = uv[(i - 1) * 2], v0 = uv[(i - 1) * 2 + 1], u1 = uv[i * 2], v1 = uv[i * 2 + 1];
            double du = u1 - u0, dv = v1 - v0;
            double len = Math.sqrt(du * du + dv * dv);
            if (len < 1e-5) continue;
            du /= len;
            dv /= len;

            double su = u0 - du * hw, sv = v0 - dv * hw, eu = u1 + du * hw, ev = v1 + dv * hw;
            double nu = -dv * hw, nv = du * hw;
            quad(vc, pose, cam, plane, su + nu, sv + nv, eu + nu, ev + nv, eu - nu, ev - nv, su - nu, sv - nv, offset, r, g, b, a);
        }
    }

    private static void drawInk(VertexConsumer vc, Matrix4f pose, Vec3 cam, SurfacePlane plane, double[] uv, int n,
                                float width, int argb, double offset, byte[] worn, long seed, int fadeTo) {
        if (worn == null || n < 2) {
            drawStroke(vc, pose, cam, plane, uv, n, width, argb, offset);
            return;
        }
        withFrame(plane, uv, n, () -> drawWorn(vc, pose, cam, plane, uv, n, width, argb, offset, worn, seed, fadeTo));
    }

    private static void drawFaded(VertexConsumer vc, Matrix4f pose, Vec3 cam, SurfacePlane plane, double[] uv, int n,
                                  float width, int argb, double offset, byte[] worn) {
        withFrame(plane, uv, n, () -> {
            int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            for (int i = 1; i < n; i++) {
                double f = (InkWear.frac(worn[i - 1]) + InkWear.frac(worn[i])) / 2;
                segment(vc, pose, cam, plane, uv, i, width / 2.0, 0, offset, r, g, b, (int) (a * (1 - 0.95 * Math.min(1, f * 1.4))));
            }
        });
    }

    private static void withFrame(SurfacePlane plane, double[] uv, int n, Runnable draw) {
        var level = Minecraft.getInstance().level;
        frame = level == null || n == 0 ? null
                : Ships.toWorld(level, BlockPos.containing(plane.toWorld(uv[0], uv[1], -0.01)), partial);
        try {
            draw.run();
        } finally {
            frame = null;
        }
    }

    private static void drawWorn(VertexConsumer vc, Matrix4f pose, Vec3 cam, SurfacePlane plane, double[] uv, int n,
                                 float width, int base, double offset, byte[] worn, long seed, int fadeTo) {
        int a = base >>> 24;
        double hw = width / 2.0;

        Vec3 p0 = plane.toWorld(uv[0], uv[1], 0);
        double downU = -(plane.toWorld(uv[0] + 1, uv[1], 0).y - p0.y), downV = -(plane.toWorld(uv[0], uv[1] + 1, 0).y - p0.y);
        boolean wall = Math.hypot(downU, downV) > 0.5;
        for (int i = 1; i < n; i++) {
            byte wa = worn[i - 1], wb = worn[i];
            double f = (InkWear.frac(wa) + InkWear.frac(wb)) / 2;
            int col = f < 0.03 ? base : mix(base, fadeTo, Math.min(1, f * 1.5));
            int r = (col >> 16) & 0xFF, g = (col >> 8) & 0xFF, b = col & 0xFF;
            if (f < 0.03) {
                segment(vc, pose, cam, plane, uv, i, hw, 0, offset, r, g, b, a);
                continue;
            }
            double h = noise(seed, i);
            if (InkWear.washed(wa) || InkWear.washed(wb)) {
                segment(vc, pose, cam, plane, uv, i, hw * (2.5 + 4 * f), 0, offset * 0.8, r, g, b, (int) (a * 0.35 * f));
                segment(vc, pose, cam, plane, uv, i, hw * (1 + 0.6 * f), 0, offset, r, g, b, (int) (a * (1 - 0.75 * f)));
                if (wall && f > 0.15 && h < 0.3) {
                    double u = uv[i * 2], v = uv[i * 2 + 1], len = (0.08 + 0.4 * f) * (0.5 + noise(seed + 7, i));
                    double sw = hw * (0.5 + 0.4 * f), su = -downV * sw, sv = downU * sw;
                    double eu = u + downU * len, ev = v + downV * len;
                    fadeQuad(vc, pose, cam, plane, u + su, v + sv, u - su, v - sv, eu - su * 0.4, ev - sv * 0.4, eu + su * 0.4, ev + sv * 0.4,
                            offset * 0.9, r, g, b, (int) (a * 0.7 * Math.min(1, f * 1.5)), 0);
                }
            } else {
                double side = (h - 0.5) * 2;
                segment(vc, pose, cam, plane, uv, i, hw * (1.6 + 2.2 * f), side * hw * 3 * f, offset * 0.8, r, g, b, (int) (a * 0.32 * f));
                if (noise(seed + 3, i) < f) continue;
                segment(vc, pose, cam, plane, uv, i, hw * (1 - 0.35 * f), side * hw * 0.8 * f, offset, r, g, b, (int) (a * (1 - 0.6 * f)));
            }
        }
    }

    private static void segment(VertexConsumer vc, Matrix4f pose, Vec3 cam, SurfacePlane plane, double[] uv, int i, double hw,
                                double shift, double offset, int r, int g, int b, int a) {
        if (a <= 0) return;
        double u0 = uv[(i - 1) * 2], v0 = uv[(i - 1) * 2 + 1], u1 = uv[i * 2], v1 = uv[i * 2 + 1];
        double du = u1 - u0, dv = v1 - v0;
        double len = Math.sqrt(du * du + dv * dv);
        if (len < 1e-5) return;
        du /= len;
        dv /= len;
        double ou = -dv * shift, ov = du * shift;
        double su = u0 - du * hw + ou, sv = v0 - dv * hw + ov, eu = u1 + du * hw + ou, ev = v1 + dv * hw + ov;
        double nu = -dv * hw, nv = du * hw;
        quad(vc, pose, cam, plane, su + nu, sv + nv, eu + nu, ev + nv, eu - nu, ev - nv, su - nu, sv - nv, offset, r, g, b, Math.min(255, a));
    }

    private static void fadeQuad(VertexConsumer vc, Matrix4f pose, Vec3 cam, SurfacePlane plane,
                                 double u0, double v0, double u1, double v1, double u2, double v2, double u3, double v3,
                                 double offset, int r, int g, int b, int a0, int a1) {
        vertex(vc, pose, cam, plane.toWorld(u0, v0, offset), r, g, b, a0);
        vertex(vc, pose, cam, plane.toWorld(u1, v1, offset), r, g, b, a0);
        vertex(vc, pose, cam, plane.toWorld(u2, v2, offset), r, g, b, a1);
        vertex(vc, pose, cam, plane.toWorld(u3, v3, offset), r, g, b, a1);
    }

    private static double noise(long seed, int i) {
        long x = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
        x ^= x >>> 31;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 29;
        return (x >>> 11) / (double) (1L << 53);
    }

    static void quad(VertexConsumer vc, Matrix4f pose, Vec3 cam, SurfacePlane plane,
                             double u0, double v0, double u1, double v1, double u2, double v2, double u3, double v3,
                             double offset, int r, int g, int b, int a) {
        vertex(vc, pose, cam, plane.toWorld(u0, v0, offset), r, g, b, a);
        vertex(vc, pose, cam, plane.toWorld(u1, v1, offset), r, g, b, a);
        vertex(vc, pose, cam, plane.toWorld(u2, v2, offset), r, g, b, a);
        vertex(vc, pose, cam, plane.toWorld(u3, v3, offset), r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, Vec3 cam, Vec3 p, int r, int g, int b, int a) {
        if (frame != null) p = frame.apply(p);
        vc.addVertex(pose, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).setColor(r, g, b, a);
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
    }

    private static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int brighten(int argb, double f) {
        int r = (int) Math.min(255, ((argb >> 16) & 0xFF) * (0.7 + 0.5 * f));
        int g = (int) Math.min(255, ((argb >> 8) & 0xFF) * (0.7 + 0.5 * f));
        int b = (int) Math.min(255, (argb & 0xFF) * (0.7 + 0.5 * f));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
