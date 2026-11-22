package com.fimkov.witchsatelier.client.fx;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.compat.Ships;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.page.PageKind;
import com.fimkov.witchsatelier.network.Payloads;
import com.fimkov.witchsatelier.spell.SpellProgram.Form;
import com.fimkov.witchsatelier.spell.SpellProgram;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class SpellFxClient {
    private static final class Fx {
        Payloads.SpellFx p;
        double born, seen, ended = -1;
    }

    private static final Map<Long, Fx> FX = new HashMap<>();

    public static double soarSpeed(Entity e) {
        for (Fx fx : FX.values()) {
            Payloads.SpellFx p = fx.p;
            if (fx.ended >= 0 || !e.getUUID().equals(p.caster()) || (p.mods() & GlyphService.WORN_FX) == 0) continue;
            if (FORMS[Math.floorMod(p.form() & 15, FORMS.length)] == Form.LIFT && (p.mods() & GlyphService.WORN_SINKING) == 0)
                return Math.max(0.2, p.length());
        }
        return 0;
    }

    public static boolean soaring(Entity e) {
        for (Fx fx : FX.values()) {
            Payloads.SpellFx p = fx.p;
            if (fx.ended >= 0 || !e.getUUID().equals(p.caster()) || (p.mods() & GlyphService.WORN_FX) == 0) continue;
            if (FORMS[Math.floorMod(p.form() & 15, FORMS.length)] == Form.LIFT) return true;
        }
        return false;
    }

    private static final class Bird {
        Payloads.BirdFx p;
        double seen, born;
        final ArrayDeque<Vec3> trail = new ArrayDeque<>();
    }

    private static final Map<Long, Bird> BIRDS = new HashMap<>();
    private static final int TRAIL = 14;

    public static void acceptBird(Payloads.BirdFx p) {
        var level = Minecraft.getInstance().level;
        double now = level == null ? 0 : level.getGameTime();
        long key = p.spell() * 64 + p.bird();
        Bird b = BIRDS.computeIfAbsent(key, k -> { Bird n = new Bird(); n.born = now; return n; });
        if (now < b.born) b.born = now;
        b.p = p;
        b.seen = now;
        b.trail.addFirst(new Vec3(p.x(), p.y(), p.z()));
        while (b.trail.size() > TRAIL) b.trail.removeLast();
    }
    private static final Form[] FORMS = Form.values();

    private static final String OFF = System.getProperty("witchsatelier.fxoff", "");

    private SpellFxClient() {}

    public static void accept(Payloads.SpellFx p) {
        var level = Minecraft.getInstance().level;
        double now = level == null ? 0 : level.getGameTime();
        Fx fx = FX.get(p.id());
        if (fx == null) {
            if (p.ticksLeft() == 0) return;
            fx = new Fx();
            fx.born = now;
            FX.put(p.id(), fx);

            var me = Minecraft.getInstance().player;
            if (me != null && p.handheld() && p.id() > 0 && me.getUUID().equals(p.caster())) {
                boolean off = PageKind.of(me.getOffhandItem()) != null;
                me.swing(off ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            }
        }
        fx.p = p;

        if (now < fx.born) fx.born = now;
        fx.seen = now;
        if (p.ticksLeft() == 0 && fx.ended < 0) fx.ended = now;
    }

    public static void clear() {
        FX.clear();
        BIRDS.clear();
    }

    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || (FX.isEmpty() && BIRDS.isEmpty())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = mc.level.getGameTime() + pt;
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        Matrix4f m = ps.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(WitchRenderTypes.GLOW);
        LocalPlayer me = mc.player;
        List<Object[]> waterJets = new ArrayList<>(), rains = new ArrayList<>(), clouds = new ArrayList<>();
        Vec3 camLook = new Vec3(e.getCamera().getLookVector());

        for (Iterator<Fx> it = FX.values().iterator(); it.hasNext(); ) {
            Fx fx = it.next();
            Payloads.SpellFx p = fx.p;
            double fade = fx.ended < 0 ? 1 : 1 - (time - fx.ended) / 10.0;
            if (fade <= 0 || (fx.ended < 0 && time - fx.seen > 40)) { it.remove(); continue; }
            double left = p.duration() > 0 ? Math.max(0, (p.ticksLeft() - (time - fx.seen)) / p.duration()) : 1;
            double life = fx.ended < 0 ? 0.35 + 0.65 * left : 0.35;
            int color = p.color();
            Vec3 o = new Vec3(p.ox(), p.oy(), p.oz());
            Vec3 n = new Vec3(p.nx(), p.ny(), p.nz());
            Vec3 d = new Vec3(p.dx(), p.dy(), p.dz());

            var onShip = p.handheld() ? null : Ships.toWorld(mc.level, BlockPos.containing(o.subtract(n.scale(0.2))), pt);
            if (onShip != null) {
                Vec3 so = onShip.apply(o);
                n = onShip.apply(o.add(n)).subtract(so).normalize();
                d = onShip.apply(o.add(d)).subtract(so).normalize();
                o = so;
            }
            boolean mine = me != null && p.handheld() && me.getUUID().equals(p.caster());
            if (mine) {
                Vec3 look = me.getViewVector(pt);
                o = me.getEyePosition(pt).add(look.scale(0.9)).subtract(0, 0.25, 0);
                d = look.add(d.subtract(n)).normalize();
                n = look;
            }
            Vec3 lo = o.subtract(cam);
            Vec3[] ab = basis(n);
            double radius = p.handheld() ? 0.32 : p.radius();
            Vec3 ringAt = lo.add(n.scale(0.012));
            double age = Math.max(0, time - fx.born);

            boolean worn = (p.mods() & GlyphService.WORN_FX) != 0;
            if (worn) {
                if (FORMS[Math.floorMod(p.form() & 15, FORMS.length)] == Form.LIFT && !OFF.contains("cloud")) {
                    var wearer = mc.level.getPlayerByUUID(p.caster());
                    Vec3 feet = wearer != null ? wearer.getPosition(pt).subtract(cam) : lo;

                    clouds.add(new Object[]{feet.add(0, -0.36 * 1.5 * 0.55, 0), 0.36, color, life * fade, (double) p.id()});
                }
                continue;
            }
            if (OFF.contains("rings")) {  } else if (age < 18) {
                double f = Math.min(1, Math.max(0, age / 18.0));
                ring(vc, m, ringAt, ab, radius * (0.85 + 0.75 * f), radius * (0.08 + 0.12 * (1 - f)), color, (1 - f) * 0.95, 1, 0);
            }
            double spin = time * 0.02;
            if (!OFF.contains("rings")) {
                ring(vc, m, ringAt, ab, radius, radius * 0.035, color, 0.55 * life * fade, 4, spin);
                ring(vc, m, ringAt, ab, radius * 0.82, radius * 0.025, color, 0.4 * life * fade, 6, -spin * 1.4);
            }

            Form form = FORMS[Math.floorMod(p.form() & 15, FORMS.length)];
            if (!OFF.contains("mods")) mods(vc, m, lo, n, ab, radius, p, color, life * fade, time, form);
            int el = ((p.form() & 0xFF) >> 4) - 1;
            boolean water = el == Sigil.WATER.ordinal();
            double flicker = 0.82 + 0.18 * Math.sin(time * 1.3 + p.id());
            switch (form) {
                case BEAM -> {
                    if (p.length() > 0) {
                        if (water) waterJets.add(new Object[]{lo, d, (double) p.length(), (double) p.width(), fade});
                        else if (!OFF.contains("beam")) beam(vc, m, lo, d, p.length(), p.width(), color, fade * flicker, time);
                    }
                }
                case LIFT -> {
                    if (!OFF.contains("column")) column(vc, m, lo, n, p.width(), p.length(), color, 0.22 * life * fade);
                    if (!OFF.contains("cloud")) clouds.add(new Object[]{lo.add(n.scale(0.05)), Math.min(0.9, Math.max(0.4, radius * 0.45)), color, life * fade, (double) p.id()});
                }
                case RAIN -> {
                    if (water) rains.add(new Object[]{lo, n, (double) Math.max(radius, p.length()), (double) radius, fade * life, (double) p.id()});
                    else {
                        double reach = Math.max(radius, p.length());
                        for (int k = 0; k < 3; k++) {
                            double ph = ((time * 0.03 + k / 3.0) % 1.0);
                            double rr = radius + (reach - radius) * ph;
                            ring(vc, m, lo.add(n.scale(0.05)), ab, rr, 0.05, color, 0.35 * Math.sin(Math.PI * ph) * fade, 1, 0);
                        }
                    }
                }
                case BOLTS -> {
                    double ph = (age % 4) / 4.0;
                    ring(vc, m, lo.add(d.scale(0.2)), basis(d), radius * (0.6 + 0.9 * ph), 0.06, color, 0.7 * (1 - ph) * fade, 1, 0);
                }
                case PETRIFY -> {
                    for (int k = 0; k < 2; k++) {
                        double ph = ((time * 0.025 + k * 0.5) % 1.0);
                        double rr = Math.max(radius, p.length()) * (1.6 - 0.9 * ph);
                        ring(vc, m, lo.add(n.scale(0.06)), ab, rr, 0.08, 0xFF9A9A96, 0.5 * Math.sin(Math.PI * ph) * fade, 6, -time * 0.03);
                    }
                }
                case PULL, SPILL -> {
                    double reach = Math.max(radius, p.length());
                    for (int k = 0; k < 3; k++) {
                        double ph = ((time * 0.03 + k / 3.0) % 1.0);
                        double rr = form == Form.PULL ? reach * (1 - ph) : radius + (reach - radius) * ph;
                        ring(vc, m, lo.add(n.scale(0.05)), ab, rr, 0.05, color, 0.35 * Math.sin(Math.PI * ph) * fade, 1, 0);
                    }
                }
                default -> {}
            }
        }
        for (Iterator<Bird> it = BIRDS.values().iterator(); it.hasNext(); ) {
            Bird b = it.next();
            double since = Math.max(0, time - b.seen);
            double alpha = Math.min(1, Math.max(0.2, (time - b.born) / 6.0)) * (since < 2 ? 1 : 1 - (since - 2) / 6.0);
            Payloads.BirdFx p = b.p;
            double left = p.life() - p.age() - since;
            alpha *= Math.min(1, left / 12.0);
            if (alpha <= 0.01) { it.remove(); continue; }
            Vec3 vel = new Vec3(p.vx(), p.vy(), p.vz());
            Vec3 at = new Vec3(p.x(), p.y(), p.z()).add(vel.scale(Math.min(since, 2)));
            bird(vc, m, at.subtract(cam), vel, p.color(), alpha, time + p.bird() * 7.3, p.age() + since);

            Vec3 prev = at;
            int k = 0;
            for (Vec3 q : b.trail) {
                double f = 1 - (double) k++ / TRAIL;
                Vec3 side = q.subtract(prev).cross(new Vec3(0, 1, 0));
                if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
                side = side.normalize();
                fan(vc, m, prev.subtract(cam), q.subtract(cam), side, 0.09 * f, 0.09 * Math.max(0, f - 1.0 / TRAIL), p.color(), alpha * 0.5 * f, alpha * 0.5 * (f - 1.0 / TRAIL));
                fan(vc, m, prev.subtract(cam), q.subtract(cam), new Vec3(0, 1, 0), 0.09 * f, 0.09 * Math.max(0, f - 1.0 / TRAIL), p.color(), alpha * 0.5 * f, alpha * 0.5 * (f - 1.0 / TRAIL));
                prev = q;
            }
        }
        buffers.endBatch(WitchRenderTypes.GLOW);
        if (!clouds.isEmpty()) {
            Vec3 up = new Vec3(e.getCamera().getUpVector()), left = new Vec3(e.getCamera().getLeftVector());
            if (glassShader != null && !OFF.contains("glass")) {
                glassClouds(mc, m, clouds, up, left, time);
            } else {
                VertexConsumer cv = buffers.getBuffer(WitchRenderTypes.CLOUD);
                for (Object[] c : clouds) liftCloud(cv, m, (Vec3) c[0], up, left, (double) c[1], (int) c[2], (double) c[3], (double) c[4], time);
                buffers.endBatch(WitchRenderTypes.CLOUD);
            }
        }
        if (!waterJets.isEmpty() || !rains.isEmpty()) {
            VertexConsumer veil = buffers.getBuffer(WitchRenderTypes.VEIL);
            for (Object[] j : waterJets) waterJet(veil, m, (Vec3) j[0], (Vec3) j[1], (double) j[2], (double) j[3], (double) j[4], time);
            for (Object[] r : rains) rain(veil, m, (Vec3) r[0], (Vec3) r[1], (double) r[2], (double) r[3], (double) r[4], (double) r[5], time);
            buffers.endBatch(WitchRenderTypes.VEIL);
        }
    }

    private static void mods(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 n, Vec3[] ab, double radius, Payloads.SpellFx p, int color,
                             double alpha, double time, Form form) {
        int mods = p.mods();
        double r = Math.max(2.5, radius * 2.2);
        if ((mods & SpellProgram.WARD) != 0) {
            for (int k = 1; k <= 4; k++) {
                double phi = k * Math.PI / 10;
                ring(vc, m, c.add(n.scale(Math.sin(phi) * r)), ab, Math.cos(phi) * r, 0.04, color, 0.28 * alpha, 1, 0);
            }
            ring(vc, m, c.add(n.scale(0.02)), ab, r, 0.06, color, 0.45 * alpha, 1, 0);
            for (int k = 0; k < 8; k++) {
                double th = k * Math.PI / 4 + time * 0.01;
                Vec3 dir = ab[0].scale(Math.cos(th)).add(ab[1].scale(Math.sin(th)));
                Vec3 prev = c.add(dir.scale(r));
                for (int i = 1; i <= 6; i++) {
                    double phi = i * Math.PI / 12;
                    Vec3 at = c.add(dir.scale(Math.cos(phi) * r)).add(n.scale(Math.sin(phi) * r));
                    fan(vc, m, prev, at, dir, 0.03, 0.03, color, 0.3 * alpha, 0.3 * alpha);
                    prev = at;
                }
            }
        }
        if ((mods & SpellProgram.BIND) != 0) {
            for (int k = 0; k < 6; k++) {
                Vec3 prev = null;
                for (int i = 0; i <= 14; i++) {
                    double h = i * 0.15, a = k * Math.PI / 3 + h * 2.2 + time * 0.08;
                    double rr = radius * (1 - h / 3.0);
                    Vec3 at = c.add(ab[0].scale(Math.cos(a) * rr)).add(ab[1].scale(Math.sin(a) * rr)).add(n.scale(h));
                    if (prev != null) fan(vc, m, prev, at, n, 0.025, 0.025, color, 0.6 * alpha * (1 - h / 2.4), 0.6 * alpha * (1 - (h + 0.15) / 2.4));
                    prev = at;
                }
            }
        }
        if ((mods & SpellProgram.PUPPET) != 0) {
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4 + Math.sin(time * 0.05 + k) * 0.2;
                Vec3 foot = c.add(ab[0].scale(Math.cos(a) * r * 0.6)).add(ab[1].scale(Math.sin(a) * r * 0.6));
                Vec3 top = foot.add(n.scale(4)).add(ab[0].scale(Math.sin(time * 0.07 + k) * 0.3));
                fan(vc, m, foot, top, ab[0], 0.015, 0.015, color, 0.55 * alpha, 0);
            }
        }
        if ((mods & SpellProgram.GATHER) != 0) {
            for (int k = 0; k < 4; k++) {
                Vec3 prev = null;
                for (int i = 0; i <= 20; i++) {
                    double t = i / 20.0;
                    double a = k * Math.PI / 2 + t * 4 - time * 0.12;
                    double rr = r * 2 * (1 - t);
                    Vec3 at = c.add(ab[0].scale(Math.cos(a) * rr)).add(ab[1].scale(Math.sin(a) * rr)).add(n.scale(0.15 + t * 0.4));
                    if (prev != null) fan(vc, m, prev, at, n, 0.04 * t, 0.04 * t, color, 0.5 * alpha * t, 0.5 * alpha * t);
                    prev = at;
                }
            }
        }
        if ((mods & SpellProgram.AIM) != 0) {
            Vec3 d = new Vec3(p.dx(), p.dy(), p.dz());
            Vec3[] db = basis(d);
            Vec3 at = c.add(d.scale(1.2));
            ring(vc, m, at, db, 0.35, 0.04, color, 0.7 * alpha, 4, time * 0.05);
            for (int k = 0; k < 4; k++) {
                Vec3 dir = db[0].scale(Math.cos(k * Math.PI / 2)).add(db[1].scale(Math.sin(k * Math.PI / 2)));
                fan(vc, m, at.add(dir.scale(0.2)), at.add(dir.scale(0.5)), d, 0.02, 0.02, color, 0.7 * alpha, 0.7 * alpha);
            }
        }
        if (form == Form.MANIFEST && ((p.form() & 0xFF) >> 4) - 1 == Sigil.LIGHT.ordinal()) {
            Vec3 at = c.add(n.scale(0.9 + Math.sin(time * 0.06) * 0.08));
            for (int k = 0; k < 3; k++) {
                double rr = (0.18 + k * 0.16) * (1 + 0.05 * Math.sin(time * 0.3 + k));
                Vec3[] b = basis(n.add(new Vec3(Math.sin(k), 0.3 * k, Math.cos(k))).normalize());
                ring(vc, m, at, b, rr * 0.5, rr, k == 0 ? 0xFFFFFFFF : color, (0.5 - k * 0.12) * alpha, 1, 0);
            }
        }
    }

    private static void waterJet(VertexConsumer vc, Matrix4f m, Vec3 from, Vec3 dir, double len, double width, double fade, double time) {
        Vec3[] ab = basis(dir);
        int sections = Math.max(6, (int) Math.ceil(len / 0.6));
        for (int i = 0; i < sections; i++) {
            double t0 = (double) i / sections, t1 = (double) (i + 1) / sections;

            Vec3 a = from.add(dir.scale(len * t0)).add(0, -0.25 * t0 * t0 * len / 10, 0);
            Vec3 b = from.add(dir.scale(len * t1)).add(0, -0.25 * t1 * t1 * len / 10, 0);
            double band0 = 0.75 + 0.25 * Math.sin(t0 * len * 4 - time * 1.6), band1 = 0.75 + 0.25 * Math.sin(t1 * len * 4 - time * 1.6);
            double w0 = width * 0.32 * (0.8 + 0.4 * t0), w1 = width * 0.32 * (0.8 + 0.4 * t1);
            double f0 = fade * fadeOut(t0), f1 = fade * fadeOut(t1);
            for (int k = 0; k < 3; k++) {
                double ang = k * Math.PI / 3 + time * 0.05;
                Vec3 side = ab[0].scale(Math.cos(ang)).add(ab[1].scale(Math.sin(ang)));
                fan(vc, m, a, b, side, w0 * 0.45, w1 * 0.45, 0xFFE8F8FF, 0.55 * f0 * band0, 0.55 * f1 * band1);
                fan(vc, m, a, b, side, w0, w1, 0xFF3C9EE8, 0.35 * f0 * band0, 0.35 * f1 * band1);
            }
        }
    }

    private static double fadeOut(double t) {
        return t < 0.85 ? 1 : Math.max(0, 1 - (t - 0.85) / 0.15);
    }

    private static void rain(VertexConsumer vc, Matrix4f m, Vec3 base, Vec3 n, double reach, double radius, double fade, double seed, double time) {
        if (fade <= 0.01) return;
        double height = 4 + radius;
        Vec3 top = base.add(n.scale(height));
        Vec3[] ab = basis(n);

        Random rnd = new Random((long) seed * 31 + 7);
        int puffs = (int) Math.min(40, 8 + reach * reach * 0.8);
        for (int i = 0; i < puffs; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * reach * 0.9;
            double drift = Math.sin(time * 0.01 + i) * 0.3;
            Vec3 c = top.add(ab[0].scale(Math.cos(a) * d + drift)).add(ab[1].scale(Math.sin(a) * d)).add(n.scale(rnd.nextDouble() * 0.6));
            double pr = 0.9 + rnd.nextDouble() * 1.2;
            int shade = d < reach * 0.4 ? 0xFF6E747C : 0xFF9AA1A9;
            puff(vc, m, c, n, ab, pr, shade, 0.35 * fade);
        }

        int streaks = (int) Math.min(220, 30 + reach * reach * 6);
        for (int i = 0; i < streaks; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * reach;
            double speed = 0.45 + rnd.nextDouble() * 0.2;
            double fall = ((time * speed + rnd.nextDouble() * height) % height);
            Vec3 at = top.add(ab[0].scale(Math.cos(a) * d)).add(ab[1].scale(Math.sin(a) * d)).subtract(n.scale(fall));
            Vec3 tail = at.add(n.scale(0.55));
            Vec3 side = ab[0].scale(-Math.sin(a)).add(ab[1].scale(Math.cos(a)));
            fan(vc, m, at, tail, side, 0.025, 0.02, 0xFFB8D8F2, 0.55 * fade, 0.0);
        }
    }

    private static void liftCloud(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 up, Vec3 left, double r, int color, double alpha, double seed, double time) {
        double swell = 1 + 0.05 * Math.sin(time * 0.2 + seed);
        cloudQuad(vc, m, c, up, left, r * 1.5 * swell, r * 1.5 * swell, color, alpha, false);
    }

    private static void cloudQuad(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 up, Vec3 left, double w, double h, int color, double alpha, boolean flip) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255, a = (int) (Math.min(1, alpha) * 255);
        float u0 = flip ? 1 : 0, u1 = flip ? 0 : 1;
        Vec3 l = left.scale(w), u = up.scale(h);
        Vec3[] corners = {c.add(l).add(u), c.add(l).subtract(u), c.subtract(l).subtract(u), c.subtract(l).add(u)};
        float[][] uv = {{u0, 0}, {u0, 1}, {u1, 1}, {u1, 0}};
        for (int i = 0; i < 4; i++)
            vc.addVertex(m, (float) corners[i].x, (float) corners[i].y, (float) corners[i].z).setUv(uv[i][0], uv[i][1]).setColor(r, g, b, a);
    }

    private static ShaderInstance glassShader;
    private static RenderTarget sceneCopy;
    private static final ResourceLocation CLOUD_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "textures/fx/lift_cloud.png");

    public static void registerShaders(RegisterShadersEvent e) {
        try {
            e.registerShader(new ShaderInstance(e.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "cloud_glass"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), s -> glassShader = s);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void glassClouds(Minecraft mc, Matrix4f m, List<Object[]> clouds, Vec3 up, Vec3 left, double time) {
        var main = mc.getMainRenderTarget();
        if (sceneCopy == null) {
            sceneCopy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            sceneCopy.setClearColor(0, 0, 0, 0);
        }
        if (sceneCopy.width != main.width || sceneCopy.height != main.height) sceneCopy.resize(main.width, main.height, Minecraft.ON_OSX);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneCopy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(false);

        var b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Object[] c : clouds) liftCloud(b, m, (Vec3) c[0], up, left, (double) c[1], (int) c[2], (double) c[3], (double) c[4], time);
        var mesh = b.build();
        if (mesh == null) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> glassShader);
        RenderSystem.setShaderTexture(0, CLOUD_TEXTURE);
        RenderSystem.setShaderTexture(1, sceneCopy.getColorTextureId());
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private static int mixColor(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static void puff(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 n, Vec3[] ab, double r, int color, double alpha) {
        int seg = 10;
        int a = (int) (Math.min(1, alpha) * 255);
        for (int i = 0; i < seg; i++) {
            double t0 = i * Math.PI * 2 / seg, t1 = (i + 1) * Math.PI * 2 / seg;
            Vec3 p0 = c.add(ab[0].scale(Math.cos(t0) * r)).add(ab[1].scale(Math.sin(t0) * r));
            Vec3 p1 = c.add(ab[0].scale(Math.cos(t1) * r)).add(ab[1].scale(Math.sin(t1) * r));
            v(vc, m, c, color, a);
            v(vc, m, c, color, a);
            v(vc, m, p1, color, 0);
            v(vc, m, p0, color, 0);
        }
    }

    private static void bird(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 vel, int color, double alpha, double time, double age) {
        Vec3 f = vel.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : vel.normalize();
        Vec3 r = f.cross(new Vec3(0, 1, 0));
        r = r.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : r.normalize();
        Vec3 u = r.cross(f).normalize();
        double s = 0.55;
        double beat = age < 34 ? 0.55 : 0.28;
        double flap = Math.sin(time * (age < 34 ? 0.9 : 0.35)) * beat;
        Vec3 head = c.add(f.scale(0.45 * s)), tailRoot = c.subtract(f.scale(0.35 * s));

        fan(vc, m, tailRoot, head, r, 0.09 * s, 0.05 * s, 0xFFFFFFFF, alpha * 0.9, alpha * 0.9);
        fan(vc, m, tailRoot, head, u, 0.09 * s, 0.05 * s, 0xFFFFFFFF, alpha * 0.9, alpha * 0.9);
        fan(vc, m, tailRoot, head, r, 0.25 * s, 0.15 * s, color, alpha * 0.5, alpha * 0.5);
        for (int side = -1; side <= 1; side += 2) {
            Vec3 out = r.scale(side * Math.cos(flap)).add(u.scale(Math.sin(flap)));
            Vec3 elbow = c.add(out.scale(0.6 * s)).add(f.scale(0.05 * s));
            Vec3 out2 = r.scale(side * Math.cos(flap * 1.6)).add(u.scale(Math.sin(flap * 1.6)));
            Vec3 tip = elbow.add(out2.scale(0.75 * s)).subtract(f.scale(0.45 * s));
            Vec3 front = c.add(f.scale(0.15 * s)), back = c.subtract(f.scale(0.2 * s));
            Vec3 elbowBack = elbow.subtract(f.scale(0.35 * s));

            quad(vc, m, front, elbow, elbowBack, back, color, alpha * 0.85, alpha * 0.35);
            quad(vc, m, elbow, tip, tip, elbowBack, color, alpha * 0.6, 0);

            fan(vc, m, front, elbow, f, 0.035 * s, 0.03 * s, 0xFFFFFFFF, alpha * 0.8, alpha * 0.6);
            fan(vc, m, elbow, tip, f, 0.03 * s, 0.0, 0xFFFFFFFF, alpha * 0.6, 0);

            Vec3 tail = tailRoot.subtract(f.scale(0.4 * s)).add(r.scale(side * 0.16 * s)).add(u.scale(-flap * 0.1 * s));
            quad(vc, m, tailRoot.add(r.scale(side * 0.03 * s)), tailRoot, tail, tail, color, alpha * 0.7, 0);
        }

        Vec3[] ab = {r, u};
        ring(vc, m, c, ab, 0.28 * s, 0.5 * s, color, alpha * 0.18, 1, 0);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int color, double a0, double a1) {
        int x = (int) (Math.min(1, a0) * 255), y = (int) (Math.min(1, Math.max(0, a1)) * 255);
        v(vc, m, p0, color, x);
        v(vc, m, p1, color, x);
        v(vc, m, p2, color, y);
        v(vc, m, p3, color, y);
    }

    private static Vec3[] basis(Vec3 n) {
        Vec3 up = Math.abs(n.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 a = n.cross(up).normalize();
        Vec3 b = n.cross(a).normalize();
        return new Vec3[]{a, b};
    }

    private static void ring(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3[] ab, double r, double thick, int color,
                             double alpha, int dashes, double rotate) {
        if (alpha <= 0.01) return;
        int seg = 64;
        double r0 = Math.max(0, r - thick / 2), r1 = r + thick / 2;
        int a = (int) (Math.min(1, alpha) * 255);
        for (int i = 0; i < seg; i++) {
            if (dashes > 1 && (i * dashes * 2 / seg) % 2 == 1) continue;
            double t0 = rotate + i * Math.PI * 2 / seg, t1 = rotate + (i + 1) * Math.PI * 2 / seg;
            Vec3 d0 = ab[0].scale(Math.cos(t0)).add(ab[1].scale(Math.sin(t0)));
            Vec3 d1 = ab[0].scale(Math.cos(t1)).add(ab[1].scale(Math.sin(t1)));
            v(vc, m, c.add(d0.scale(r0)), color, a);
            v(vc, m, c.add(d0.scale(r1)), color, a);
            v(vc, m, c.add(d1.scale(r1)), color, a);
            v(vc, m, c.add(d1.scale(r0)), color, a);
        }
    }

    private static void beam(VertexConsumer vc, Matrix4f m, Vec3 from, Vec3 dir, double len, double width, int color, double alpha, double time) {
        Vec3[] ab = basis(dir);
        int sections = Math.max(4, (int) Math.ceil(len / 1.5));
        for (int i = 0; i < sections; i++) {
            double t0 = (double) i / sections, t1 = (double) (i + 1) / sections;
            double f0 = fade(t0), f1 = fade(t1);
            Vec3 a = from.add(dir.scale(len * t0)), b = from.add(dir.scale(len * t1));
            for (int k = 0; k < 3; k++) {
                double t = k * Math.PI / 3;
                Vec3 side = ab[0].scale(Math.cos(t)).add(ab[1].scale(Math.sin(t)));
                fan(vc, m, a, b, side, width * 0.22 * taper(t0), width * 0.22 * taper(t1), 0xFFFFFFFF, alpha * 0.9 * f0, alpha * 0.9 * f1);
                fan(vc, m, a, b, side, width * 1.1 * taper(t0), width * 1.1 * taper(t1), color, alpha * 0.45 * f0, alpha * 0.45 * f1);
            }
        }
        for (int k = 0; k < 4; k++) {
            double at = ((time * 0.6 + k * len / 4) % len);
            double f = fade(at / len);
            ring(vc, m, from.add(dir.scale(at)), ab, width * 0.55 * taper(at / len), width * 0.12, color, alpha * 0.5 * f, 1, 0);
        }

        double pulse = 0.6 + 0.4 * Math.sin(time * 0.9);
        Vec3 tip = from.add(dir.scale(len * 0.97));
        for (int k = 0; k < 3; k++) {
            double t = k * Math.PI / 3;
            Vec3 side = ab[0].scale(Math.cos(t)).add(ab[1].scale(Math.sin(t)));
            fan(vc, m, tip.subtract(dir.scale(width * 0.6)), tip.add(dir.scale(width * 0.6)), side, width * 0.5, width * 0.5, color, alpha * 0.35 * pulse, 0);
        }
    }

    private static double fade(double t) {
        if (t < 0.6) return 1;
        double x = (t - 0.6) / 0.4;
        return 1 - x * x * (3 - 2 * x);
    }

    private static double taper(double t) {
        return t < 0.7 ? 1 : 1 - 0.55 * (t - 0.7) / 0.3;
    }

    private static void fan(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 side, double wa, double wb, int color, double alphaA, double alphaB) {
        int aa = (int) (Math.min(1, alphaA) * 255), ab = (int) (Math.min(1, alphaB) * 255);
        for (int s = -1; s <= 1; s += 2) {
            v(vc, m, a, color, aa);
            v(vc, m, b, color, ab);
            v(vc, m, b.add(side.scale(wb * s)), color, 0);
            v(vc, m, a.add(side.scale(wa * s)), color, 0);
        }
    }

    private static void fan(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 side, double w, int color, double alpha) {
        int al = (int) (Math.min(1, alpha) * 255);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 off = side.scale(w * s);
            v(vc, m, a, color, al);
            v(vc, m, b, color, al);
            v(vc, m, b.add(off), color, 0);
            v(vc, m, a.add(off), color, 0);
        }
    }

    private static void column(VertexConsumer vc, Matrix4f m, Vec3 base, Vec3 n, double r, double h, int color, double alpha) {
        Vec3[] ab = basis(n);
        int seg = 32;
        int a = (int) (Math.min(1, alpha) * 255);
        for (int i = 0; i < seg; i++) {
            double t0 = i * Math.PI * 2 / seg, t1 = (i + 1) * Math.PI * 2 / seg;
            Vec3 p0 = base.add(ab[0].scale(Math.cos(t0) * r)).add(ab[1].scale(Math.sin(t0) * r));
            Vec3 p1 = base.add(ab[0].scale(Math.cos(t1) * r)).add(ab[1].scale(Math.sin(t1) * r));
            v(vc, m, p0, color, a);
            v(vc, m, p1, color, a);
            v(vc, m, p1.add(n.scale(h)), color, 0);
            v(vc, m, p0.add(n.scale(h)), color, 0);
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, int color, int alpha) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha);
    }
}
