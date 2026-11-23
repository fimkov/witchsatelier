package com.fimkov.witchsatelier.client.fx;

import com.fimkov.witchsatelier.network.Payloads;
import com.fimkov.witchsatelier.world.PassageStars;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Draws the Passage Stars: long, slow streaks falling away from one point high in the sky, drawn right after the sky
 * (so terrain, clouds and weather all cover them) as a few hundred additive quads on the sky dome. Every meteor is a
 * pure function of the shower's seed and the game time, so nothing is stored per meteor and every player sees the same sky.
 */
public final class PassageStarsSky {
    private PassageStarsSky() {}

    private static final int SLOTS = 56, SEGMENTS = 10, MAX_LIFE = 90, MAX_DURATION = PassageStars.DAY;
    private static final float DOME = 100f;

    private static long start = -1, seed;
    private static int duration;

    public static void init(IEventBus game) {
        PassageStars.clientSink = PassageStarsSky::accept;
        game.addListener(PassageStarsSky::render);
        game.addListener(PassageStarsSky::onClientTick);
        game.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> clear());
    }

    static void accept(Payloads.StarShower p) {
        if (p.duration() <= 0 || p.duration() > MAX_DURATION) {
            clear();
            return;
        }
        start = p.start();
        duration = p.duration();
        seed = p.seed();
    }

    public static void clear() {
        start = -1;
    }

    private static long mix(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double envelope(double x) {
        if (x < 0 || x > 1) return 0;
        double rise = Mth.clamp(x / 0.15, 0, 1), fall = Mth.clamp((1 - x) / 0.3, 0, 1);
        return rise * rise * (3 - 2 * rise) * fall * fall * (3 - 2 * fall) * (0.55 + 0.45 * Math.sin(Math.PI * x));
    }

    private static long slotHash(int k) {
        return mix(seed ^ (k + 1) * 0x632BE59BD9B4E019L);
    }

    private static int period(long slot) {
        return 70 + (int) (unit(slot) * 120);
    }

    private static int offset(long slot, int period) {
        return (int) (unit(mix(slot)) * period);
    }

    /** The meteor of slot {@code k} in its cycle {@code c}, or {@code null} while the slot rests. */
    private static double[] meteor(int k, long cycle, int bornAt) {
        if (cycle < 0 || bornAt > duration) return null;
        if (envelope(bornAt / (double) duration) <= (k + 0.5) / SLOTS) return null;
        long h = mix(slotHash(k) + cycle * 0x9E3779B97F4A7C15L);
        double life = 40 + 50 * unit(h);
        h = mix(h);
        double az = Math.PI * 2 * unit(h);
        h = mix(h);
        double sinEl = 0.3 + 0.62 * unit(h), cosEl = Math.sqrt(1 - sinEl * sinEl);
        double sx = cosEl * Math.cos(az), sy = sinEl, sz = cosEl * Math.sin(az);

        long r = mix(seed + 1);
        double raz = Math.PI * 2 * unit(r), rsin = 0.75 + 0.2 * unit(mix(r)), rcos = Math.sqrt(1 - rsin * rsin);
        double rx = rcos * Math.cos(raz), ry = rsin, rz = rcos * Math.sin(raz);

        double dot = sx * rx + sy * ry + sz * rz;
        double tx = sx * dot - rx, ty = sy * dot - ry, tz = sz * dot - rz;
        double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (tl < 1e-3) return null;
        h = mix(h);
        double sweep = 0.35 + 0.35 * unit(h);
        h = mix(h);
        double tail = 0.1 + 0.12 * unit(h);
        h = mix(h);
        double size = 0.75 + 0.6 * unit(h);
        h = mix(h);
        double chime = unit(h);
        h = mix(h);
        double pitch = unit(h);
        return new double[]{life, sx, sy, sz, tx / tl, ty / tl, tz / tl, sweep, tail, size, chime, pitch};
    }

    private static float sky(ClientLevel level, float pt) {
        return level.getStarBrightness(pt) * 2f * (1f - level.getRainLevel(pt));
    }

    private static boolean showsHere(ClientLevel level) {
        return level != null && level.dimension() == Level.OVERWORLD && level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL;
    }

    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || start < 0) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!showsHere(level) || e.getCamera().getFluidInCamera() != FogType.NONE) return;
        if (e.getCamera().getEntity() instanceof LivingEntity viewer
                && (viewer.hasEffect(MobEffects.BLINDNESS) || viewer.hasEffect(MobEffects.DARKNESS))) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        double t = level.getGameTime() - start + pt;
        if (t < 0 || t > duration + MAX_LIFE) return;
        float sky = sky(level, pt);
        if (sky <= 0.02f) return;

        Matrix4f m = e.getModelViewMatrix();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int k = 0; k < SLOTS; k++) {
            long slot = slotHash(k);
            int period = period(slot), offset = offset(slot, period);
            long cycle = (long) Math.floor((t - offset) / period);
            int bornAt = (int) (offset + cycle * period);
            double[] mt = meteor(k, cycle, bornAt);
            if (mt == null) continue;
            double age = t - bornAt, life = mt[0];
            if (age < 0 || age > life) continue;
            double fade = Math.min(1, age / (0.12 * life)) * Math.min(1, (life - age) / (0.35 * life));
            streak(bb, m, mt, age / life, (float) (fade * sky));
        }
        MeshData mesh = bb.build();
        if (mesh == null) return;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void streak(BufferBuilder bb, Matrix4f m, double[] mt, double progress, float bright) {
        if (bright <= 0.01f) return;
        double sx = mt[1], sy = mt[2], sz = mt[3], tx = mt[4], ty = mt[5], tz = mt[6];
        double head = mt[7] * progress, from = Math.max(0, head - mt[8]), size = mt[9];
        double width = 0.95 * size;
        float[] prev = null;
        for (int i = 0; i <= SEGMENTS; i++) {
            double f = i / (double) SEGMENTS, a = from + (head - from) * f;
            double c = Math.cos(a), s = Math.sin(a);
            double px = sx * c + tx * s, py = sy * c + ty * s, pz = sz * c + tz * s;
            double dx = -sx * s + tx * c, dy = -sy * s + ty * c, dz = -sz * s + tz * c;
            double wx = py * dz - pz * dy, wy = pz * dx - px * dz, wz = px * dy - py * dx;
            double w = width * (0.15 + 0.85 * f);
            float alpha = (float) (bright * Math.pow(f, 1.6) * horizon(py));
            float[] cur = {(float) (px * DOME), (float) (py * DOME), (float) (pz * DOME),
                    (float) (wx * w), (float) (wy * w), (float) (wz * w), (float) f, alpha};
            if (prev != null) {
                half(bb, m, prev, cur, -1);
                half(bb, m, prev, cur, 1);
            }
            prev = cur;
        }
        if (prev != null) glow(bb, m, prev, mt, (float) (bright * horizon(prev[1] / DOME)), (float) (1.1 * size));
    }

    private static double horizon(double y) {
        return Mth.clamp((y + 0.02) / 0.12, 0, 1);
    }

    private static void half(BufferBuilder bb, Matrix4f m, float[] a, float[] b, int side) {
        vertex(bb, m, a[0], a[1], a[2], a[6], a[7]);
        vertex(bb, m, a[0] + side * a[3], a[1] + side * a[4], a[2] + side * a[5], a[6], 0f);
        vertex(bb, m, b[0] + side * b[3], b[1] + side * b[4], b[2] + side * b[5], b[6], 0f);
        vertex(bb, m, b[0], b[1], b[2], b[6], b[7]);
    }

    private static void glow(BufferBuilder bb, Matrix4f m, float[] h, double[] mt, float alpha, float r) {
        if (alpha <= 0.01f) return;
        float wl = (float) Math.sqrt(h[3] * h[3] + h[4] * h[4] + h[5] * h[5]);
        if (wl < 1e-5f) return;
        float ux = h[3] / wl * r, uy = h[4] / wl * r, uz = h[5] / wl * r;
        float len = (float) Math.sqrt(h[0] * h[0] + h[1] * h[1] + h[2] * h[2]);
        float nx = h[0] / len, ny = h[1] / len, nz = h[2] / len;
        float vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
        float[][] rim = {{ux, uy, uz}, {vx, vy, vz}, {-ux, -uy, -uz}, {-vx, -vy, -vz}};
        for (int i = 0; i < 4; i++) {
            float[] p = rim[i], q = rim[(i + 1) % 4];
            vertex(bb, m, h[0], h[1], h[2], 1f, alpha);
            vertex(bb, m, h[0], h[1], h[2], 1f, alpha);
            vertex(bb, m, h[0] + p[0], h[1] + p[1], h[2] + p[2], 1f, 0f);
            vertex(bb, m, h[0] + q[0], h[1] + q[1], h[2] + q[2], 1f, 0f);
        }
    }

    private static void vertex(BufferBuilder bb, Matrix4f m, float x, float y, float z, float f, float alpha) {
        float r = 0.55f + 0.45f * f, g = 0.7f + 0.26f * f, b = 1f - 0.14f * f;
        bb.addVertex(m, x, y, z).setColor(r, g, b, Mth.clamp(alpha, 0f, 1f));
    }

    static void onClientTick(ClientTickEvent.Post e) {
        if (start < 0) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        long t = level.getGameTime() - start;
        if (t > duration + MAX_LIFE) {
            clear();
            return;
        }
        if (t < 0 || mc.isPaused() || !showsHere(level)) return;
        float sky = sky(level, 1f);
        if (sky <= 0.1f) return;
        for (int k = 0; k < SLOTS; k++) {
            long slot = slotHash(k);
            int period = period(slot), offset = offset(slot, period);
            if (t < offset || (t - offset) % period != 0) continue;
            long cycle = (t - offset) / period;
            double[] mt = meteor(k, cycle, (int) t);
            if (mt == null || mt[10] > 0.1) continue;
            mc.level.playLocalSound(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.AMBIENT, (float) (0.12 * mt[9] * sky), (float) (1.2 + 0.7 * mt[11]), false);
        }
    }
}
