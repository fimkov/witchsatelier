package com.fimkov.witchsatelier.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.network.Payloads;
import com.fimkov.witchsatelier.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.InflaterInputStream;

/**
 * The apple no book shows. Its glyph's lines fade into a clean ring, and inside it the ink plays out a shadow
 * film; then the ring and all of it are gone.
 *
 * <p>The film is {@code assets/witchsatelier/badapple/frames.bin}, made by {@code tools/bad_apple.py} from a video:
 * "WCBA", then version, width, height (ints), frames per second (float), frame count (int), then zlib-deflated
 * frames, each a 1-bit picture (rows of whole bytes, high bit first, set = ink) XORed with the frame before it.
 * Without it the glyph only fades away.
 */
public final class BadAppleShow {
    private BadAppleShow() {}

    private static final ResourceLocation FILM = ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "badapple/frames.bin");
    /** Seconds the glyph takes to fade into the ring, and the ring and last picture to fade away at the end. */
    private static final double MORPH = 2.0, FADE = 2.0;
    private static final int INK = 0x16121C;
    private static final double LIFT = 0.0032;

    record Film(int w, int h, float fps, byte[][] frames) {
        double seconds() { return frames.length / (double) fps; }
    }

    private static final Film NONE = new Film(1, 1, 1, new byte[0][]);
    private static CompletableFuture<Film> film;

    /** The film, once it has been read (null while it still is). */
    private static Film film() {
        if (film == null) film = CompletableFuture.supplyAsync(BadAppleShow::load);
        return film.isDone() ? film.getNow(NONE) : null;
    }

    private static Film load() {
        var res = Minecraft.getInstance().getResourceManager().getResource(FILM);
        if (res.isEmpty()) return NONE;
        try (InputStream raw = res.get().open(); DataInputStream in = new DataInputStream(new BufferedInputStream(raw))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!new String(magic, java.nio.charset.StandardCharsets.US_ASCII).equals("WCBA") || in.readInt() != 1) return NONE;
            int w = in.readInt(), h = in.readInt();
            float fps = in.readFloat();
            int count = in.readInt();
            if (w <= 0 || h <= 0 || w > 1024 || h > 1024 || fps <= 0 || count <= 0 || count > 100_000) return NONE;
            int size = (w + 7) / 8 * h;
            byte[][] frames = new byte[count][];
            try (DataInputStream z = new DataInputStream(new InflaterInputStream(in))) {
                byte[] prev = new byte[size];
                for (int i = 0; i < count; i++) {
                    byte[] cur = new byte[size];
                    z.readFully(cur);
                    for (int k = 0; k < size; k++) cur[k] ^= prev[k];
                    frames[i] = cur;
                    prev = cur;
                }
            }
            return new Film(w, h, fps, frames);
        } catch (Exception e) {
            WitchsAtelier.LOGGER.warn("Could not read {}: {}", FILM, e.toString());
            return NONE;
        }
    }

    private record Line(SurfacePlane plane, double[] uv, int n, int color) {}

    private static final class Show {
        SurfacePlane plane;
        double cu, cv, radius;
        /** Which way is up and which is right on the plane, as the drawer saw it. */
        double upU, upV, rightU, rightV;
        long start;
        final List<Line> lines = new ArrayList<>();
        SoundInstance sound;
        boolean playing;
    }

    private static final List<Show> SHOWS = new ArrayList<>();

    static void start(Payloads.BadApple p) {
        // one at a time: a new apple takes over from any still playing (several at once is only noise)
        clear();
        Show s = new Show();
        s.plane = p.plane();
        s.cu = p.u();
        s.cv = p.v();
        s.radius = p.radius();
        s.start = System.nanoTime();
        for (long id : p.ids()) {
            ClientGlyphs.Entry e = ClientGlyphs.INSTANCE.get(id);
            if (e != null) s.lines.add(new Line(e.plane(), e.abs(), e.stroke().size(), e.stroke().ink().color & 0xFFFFFF));
        }
        // up as the drawer saw it; right is up turned the way a viewer facing the surface sees it (never mirrored)
        Vec3 up = s.plane.planeVector(Math.cos(p.up()), Math.sin(p.up())).normalize();
        Vec3 right = s.plane.normal().reverse().cross(up);
        Vec3 pu = s.plane.planeVector(1, 0), pv = s.plane.planeVector(0, 1);
        s.upU = Math.cos(p.up());
        s.upV = Math.sin(p.up());
        double ru = right.dot(pu) / pu.lengthSqr(), rv = right.dot(pv) / pv.lengthSqr();
        double rl = Math.hypot(ru, rv);
        s.rightU = rl > 1e-6 ? ru / rl : s.upV;
        s.rightV = rl > 1e-6 ? rv / rl : -s.upU;
        SHOWS.add(s);
        film(); // start reading it now
    }

    static void clear() {
        for (Show s : SHOWS) stopSound(s);
        SHOWS.clear();
    }

    private static void stopSound(Show s) {
        if (s.sound != null) Minecraft.getInstance().getSoundManager().stop(s.sound);
        s.sound = null;
    }

    static void render(VertexConsumer vc, Matrix4f pose, Vec3 cam, float inkWidth) {
        if (SHOWS.isEmpty()) return;
        Film f = film();
        long now = System.nanoTime();
        for (Iterator<Show> it = SHOWS.iterator(); it.hasNext(); ) {
            Show s = it.next();
            if (f == null) s.start = now; // still reading the film: wait for it
            else if (!s.playing) begin(s, f);
            double t = (now - s.start) / 1e9;
            double length = f == null || f == NONE ? 0 : f.seconds();
            double end = Math.max(MORPH, length);
            if (t > end + FADE) {
                stopSound(s);
                it.remove();
                continue;
            }
            if (s.plane.toWorld(s.cu, s.cv, 0).distanceToSqr(cam) > 96 * 96) continue;
            double in = Math.min(1, t / MORPH);
            double out = t <= end ? 1 : Math.max(0, 1 - (t - end) / FADE);
            // the glyph's own lines lift away as the ring and the shadows come
            for (Line l : s.lines) {
                int a = (int) (255 * (1 - in) * (1 - in));
                if (a > 0) GlyphRenderer.drawStroke(vc, pose, cam, l.plane(), l.uv(), l.n(), inkWidth, (a << 24) | l.color(), LIFT * 1.4);
            }
            int alpha = (int) (255 * in * out);
            if (alpha <= 0) continue;
            ring(vc, pose, cam, s, inkWidth, (alpha << 24) | INK);
            if (length > 0) {
                int frame = (int) Math.min(f.frames().length - 1, Math.floor(Math.max(0, t) * f.fps()));
                picture(vc, pose, cam, s, f, f.frames()[frame], (alpha << 24) | INK);
            }
        }
    }

    private static void begin(Show s, Film f) {
        s.playing = true;
        s.start = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation loc = ModSounds.BAD_APPLE.getId();
        if (f == NONE || mc.getSoundManager().getSoundEvent(loc) == null) return;
        Vec3 at = s.plane.toWorld(s.cu, s.cv, 0.1);
        s.sound = new SimpleSoundInstance(loc, SoundSource.RECORDS, 3f, 1f, RandomSource.create(), false, 0,
                SoundInstance.Attenuation.LINEAR, at.x, at.y, at.z, false);
        mc.getSoundManager().play(s.sound);
    }

    private static void ring(VertexConsumer vc, Matrix4f pose, Vec3 cam, Show s, float width, int argb) {
        int seg = 96;
        double[] uv = new double[(seg + 1) * 2];
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg;
            uv[i * 2] = s.cu + Math.cos(a) * s.radius;
            uv[i * 2 + 1] = s.cv + Math.sin(a) * s.radius;
        }
        GlyphRenderer.drawStroke(vc, pose, cam, s.plane, uv, seg + 1, width * 1.3f, argb, LIFT);
    }

    /** One picture of the film, its corners on the ring, each run of ink in a row one quad. */
    private static void picture(VertexConsumer vc, Matrix4f pose, Vec3 cam, Show s, Film f, byte[] bits, int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        double aspect = f.w() / (double) f.h();
        double diag = s.radius * 0.97;
        double hw = diag * aspect / Math.sqrt(1 + aspect * aspect), hh = diag / Math.sqrt(1 + aspect * aspect);
        double pw = 2 * hw / f.w(), ph = 2 * hh / f.h();
        int stride = (f.w() + 7) / 8;
        for (int y = 0; y < f.h(); y++) {
            double top = hh - y * ph, bottom = top - ph;
            int x = 0;
            while (x < f.w()) {
                if (!set(bits, stride, x, y)) { x++; continue; }
                int x0 = x;
                while (x < f.w() && set(bits, stride, x, y)) x++;
                double left = -hw + x0 * pw, right = -hw + x * pw;
                GlyphRenderer.quad(vc, pose, cam, s.plane,
                        u(s, left, top), v(s, left, top), u(s, right, top), v(s, right, top),
                        u(s, right, bottom), v(s, right, bottom), u(s, left, bottom), v(s, left, bottom),
                        LIFT, r, g, b, a);
            }
        }
    }

    private static boolean set(byte[] bits, int stride, int x, int y) {
        return (bits[y * stride + (x >> 3)] & (0x80 >> (x & 7))) != 0;
    }

    private static double u(Show s, double x, double y) { return s.cu + s.rightU * x + s.upU * y; }
    private static double v(Show s, double x, double y) { return s.cv + s.rightV * x + s.upV * y; }
}
