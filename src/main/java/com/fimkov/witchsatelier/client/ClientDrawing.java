package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.item.InkPenItem;
import com.fimkov.witchsatelier.item.RagItem;
import com.fimkov.witchsatelier.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * Drawing on world surfaces by moving the camera, so a glyph can be as large as you like.
 * Holding use with the pen on a block "uses" the pen (like a brush): the plane is fixed when the stroke starts and
 * the view ray is intersected with it every frame. Sneak when starting to rub ink out instead.
 */
public final class ClientDrawing {
    private static final double MIN_STEP = 0.012;
    private static final int MAX_POINTS = InkStroke.MAX_POINTS - 8;

    private static SurfacePlane plane;
    private static boolean erasing;
    private static double[] buf = new double[256];
    private static int count;
    private static int eraseCooldown;
    private static Pt hover;

    // smoothed view turning (-1..1) and pen speed (0..1) for the hand animation, frame-rate independent
    private static float lastYaw, lastPitch;
    private static long lastNanos;
    static float swayX, swayY;
    static float scratch;
    private static double lastCursorU, lastCursorV;
    /** Ink left for the line being drawn, in ink units (what the nib and the inkwell in hand hold). */
    private static double budget;
    private static long lastScratch;
    private static boolean dryWarned;

    private ClientDrawing() {}

    public static SurfacePlane plane() { return plane; }

    /** Learned from a grimoire (the server tells us): rings are seen before they are drawn. */
    public static boolean imaginedRing;
    /** The line being drawn has been taken for a ring: it is only imagined until the pen lifts. */
    private static boolean imagining;
    private static com.fimkov.witchsatelier.glyph.recognize.RingFinder.Circle imagined;
    private static int imaginedAt;
    /** Rings smaller or larger than this (radius, blocks) are not worth imagining. */
    private static final double IMAGINE_MIN = 0.45, IMAGINE_MAX = 12;

    public static boolean imagining() { return imagining; }

    /** Screenshot harness only: show a line as if it were being imagined on {@code on}. */
    public static void debugImagine(SurfacePlane on, double[] uv) {
        plane = on;
        erasing = false;
        buf = java.util.Arrays.copyOf(uv, Math.max(uv.length, 256));
        count = uv.length / 2;
        imagining = true;
        imagined = null;
        debugHold = true;
    }

    private static boolean debugHold;
    /** The block the line was started on: on a ship, it says which ship's plot the line is kept in. */
    private static net.minecraft.core.BlockPos planeBlock = net.minecraft.core.BlockPos.ZERO;

    /** The ring of this stroke is already down: the rest of the hold draws nothing. */
    private static boolean laid;

    /** The whole ring as the witch sees it so far (refitted as the line goes on). */
    public static com.fimkov.witchsatelier.glyph.recognize.RingFinder.Circle imaginedCircle() {
        if (!imagining) return null;
        if (imagined == null || count - imaginedAt >= 6) {
            java.util.List<Pt> pts = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) pts.add(new Pt(u(i), v(i)));
            var c = com.fimkov.witchsatelier.glyph.recognize.RingFinder.fit(pts);
            if (c != null) imagined = c;
            imaginedAt = count;
        }
        return imagined;
    }
    public static int pointCount() { return count; }
    public static double u(int i) { return buf[i * 2]; }
    public static double v(int i) { return buf[i * 2 + 1]; }
    public static Pt hover() { return hover; }
    public static boolean active() { return plane != null; }
    public static boolean erasingNow() { return erasing; }

    /** How much the current tool wipes (must match GlyphService.handleErase). */
    public static double eraseRadius() {
        LocalPlayer p = Minecraft.getInstance().player;
        boolean rag = p != null && (p.getMainHandItem().getItem() instanceof RagItem || p.getOffhandItem().getItem() instanceof RagItem);
        return rag ? ragRadius : GlyphService.PEN_ERASE_RADIUS;
    }

    /** Shift + mouse wheel while holding the rag: wipe wider or finer. */
    private static double ragRadius = GlyphService.RAG_RADIUS;

    static void onScroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null || !p.isShiftKeyDown()) return;
        if (!(p.getMainHandItem().getItem() instanceof RagItem || p.getOffhandItem().getItem() instanceof RagItem)) return;
        ragRadius = net.minecraft.util.Mth.clamp(ragRadius * (e.getScrollDeltaY() > 0 ? 1.15 : 1 / 1.15), GlyphService.RAG_MIN, GlyphService.RAG_MAX);
        e.setCanceled(true);
    }

    /** The rag's wipe circle where the player looks, even before wiping, to aim and size it. */
    static Pt ragHover(SurfacePlane[] planeOut) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || plane != null) return null;
        if (!(p.getMainHandItem().getItem() instanceof RagItem || p.getOffhandItem().getItem() instanceof RagItem)) return null;
        if (!(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) return null;
        SurfacePlane pl = SurfacePlane.of(bh.getBlockPos(), bh.getDirection());
        planeOut[0] = pl;
        return pl.toUV(bh.getLocation());
    }

    static ItemStack heldPen(LocalPlayer p) {
        if (p == null) return ItemStack.EMPTY;
        if (p.getMainHandItem().getItem() instanceof InkPenItem) return p.getMainHandItem();
        if (p.getOffhandItem().getItem() instanceof InkPenItem) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /** The ink the next line will use, for the preview colour. */
    static InkType currentInk() {
        LocalPlayer p = Minecraft.getInstance().player;
        ItemStack pen = heldPen(p);
        if (pen.isEmpty()) return InkType.CONJURING;
        InkPenItem tool = (InkPenItem) pen.getItem();
        return tool.selfInked() ? InkType.CHALK : tool.lineInk(InkPenItem.inkFor(p, pen));
    }

    private static boolean usingTool(LocalPlayer p) {
        return p != null && p.isUsingItem() && (p.getUseItem().getItem() instanceof InkPenItem || p.getUseItem().getItem() instanceof RagItem);
    }

    /** Pen used in the air with paper, a book or a palm quire in the other hand: draw on the page. */
    static void onInteract(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (!e.isUseItem() || mc.screen != null || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (heldPen(mc.player).isEmpty()) return;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) return; // the pen's own useOn draws
        if (ItemCanvas.findPage(mc.player) != null) {
            e.setCanceled(true);
            e.setSwingHand(false);
            ItemCanvas.open(mc.player);
        }
    }

    static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean using = mc.screen == null && usingTool(p);
        if (debugHold) return;
        if (!using) {
            if (plane != null) finish();
            return;
        }
        if (plane == null) {
            if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
                plane = SurfacePlane.of(bh.getBlockPos(), bh.getDirection());
                planeBlock = bh.getBlockPos().immutable();
                erasing = p.isShiftKeyDown() || p.getUseItem().getItem() instanceof RagItem;
                count = 0;
                imagining = false;
                laid = false;
                imagined = null;
                eraseCooldown = 0;
                budget = inkAvailable(p);
                dryWarned = false;
            }
        } else if (erasing && --eraseCooldown <= 0) {
            Pt at = cursor(1f);
            if (at != null) {
                PacketDistributor.sendToServer(new Payloads.EraseAt(plane, at.x(), at.y(), (float) eraseRadius()));
                eraseCooldown = 2;
            }
        }
    }

    /** Called every frame before rendering. */
    static void sample(float partialTick) {
        LocalPlayer p = Minecraft.getInstance().player;
        long nowNs = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (nowNs - lastNanos) / 1e9f);
        lastNanos = nowNs;
        if (p != null && dt > 0) {
            float yaw = p.getViewYRot(partialTick), pitch = p.getViewXRot(partialTick);
            float dy = net.minecraft.util.Mth.wrapDegrees(yaw - lastYaw) / dt, dp = (pitch - lastPitch) / dt; // degrees per second
            lastYaw = yaw;
            lastPitch = pitch;
            float k = 1 - (float) Math.exp(-dt * 7);
            swayX += (net.minecraft.util.Mth.clamp(dy / 240f, -1, 1) - swayX) * k;
            swayY += (net.minecraft.util.Mth.clamp(dp / 240f, -1, 1) - swayY) * k;
        }
        if (plane == null) {
            hover = null;
            return;
        }
        Pt at = cursor(partialTick);
        hover = at;
        if (at != null && dt > 0) {
            double speed = Math.hypot(at.x() - lastCursorU, at.y() - lastCursorV) / dt; // blocks per second
            lastCursorU = at.x();
            lastCursorV = at.y();
            float target = erasing ? 0 : (float) Math.min(1, speed / 2.5);
            scratch += (target - scratch) * (1 - (float) Math.exp(-dt * 10));
        }
        if (erasing || laid) return;
        if (at == null) {
            // the line left the surface: end this piece, keep the pen down for the next one
            // (an imagined ring hangs over the gap and is laid down whole at the end)
            if (count > 0 && !imagining) flush();
            return;
        }
        double seg = 0;
        if (count > 0) {
            double du = at.x() - u(count - 1), dv = at.y() - v(count - 1);
            if (du * du + dv * dv < MIN_STEP * MIN_STEP) return;
            seg = Math.sqrt(du * du + dv * dv);
        }
        // stop exactly where the ink runs out, so the preview never runs ahead of what the server will accept
        if (seg * GlyphService.INK_PER_BLOCK > budget) {
            if (!dryWarned) {
                Minecraft.getInstance().gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable("message.witchsatelier.pen_dry"), false);
                dryWarned = true;
            }
            return;
        }
        if (!imagining) budget -= seg * GlyphService.INK_PER_BLOCK;
        // the nib scratches on the surface, quieter for the chalk-like wand, never more than every 120 ms
        long now = net.minecraft.Util.getMillis();
        if (now - lastScratch > 120 && plane != null) {
            lastScratch = now;
            var w = plane.toWorld(at, 0);
            var mc = Minecraft.getInstance();
            if (mc.level != null) mc.level.playLocalSound(w.x, w.y, w.z, com.fimkov.witchsatelier.registry.ModSounds.QUILL_SCRATCH.get(),
                    net.minecraft.sounds.SoundSource.PLAYERS, erasing ? 0.15f : 0.35f, 0.85f + (float) Math.random() * 0.3f, false);
        }
        if (count * 2 + 2 > buf.length) {
            double[] n = new double[buf.length * 2];
            System.arraycopy(buf, 0, n, 0, buf.length);
            buf = n;
        }
        buf[count * 2] = at.x();
        buf[count * 2 + 1] = at.y();
        count++;
        if (!imagining && imaginedRing && count % 4 == 0
                && com.fimkov.witchsatelier.glyph.recognize.ImaginedRing.noticed(buf, count, IMAGINE_MIN, IMAGINE_MAX)) {
            imagining = true;
            budget = inkAvailable(Minecraft.getInstance().player); // lines in the air cost no ink
        }
        if (imagining && count % 3 == 0 && com.fimkov.witchsatelier.glyph.recognize.ImaginedRing.circledTwice(buf, count)) {
            // twice round in the air: the third time the pen comes down, and the ring is laid in one stroke
            layImaginedRing();
            laid = true;
            imagining = false;
            imagined = null;
            return;
        }
        if (count % 4 == 0 && !imagining) inkSpatter(at);
        if (imagining && count >= MAX_POINTS * 3) return; // more than any ring needs; the rest is not followed
        if (count >= MAX_POINTS && !imagining) {
            double lu = u(count - 1), lv = v(count - 1);
            flush();
            buf[0] = lu;
            buf[1] = lv;
            count = 1;
        }
    }

    private static void inkSpatter(Pt at) {
        Minecraft mc = Minecraft.getInstance();
        InkType ink = currentInk();
        if (mc.level == null || ink.invisible) return;
        Vec3 w = plane.toWorld(at, 0.04);
        int c = ink.color;
        Vector3f rgb = new Vector3f(((c >> 16) & 0xFF) / 255f * 1.6f + 0.05f, ((c >> 8) & 0xFF) / 255f * 1.6f + 0.05f, (c & 0xFF) / 255f * 1.6f + 0.05f);
        mc.level.addParticle(new DustParticleOptions(rgb, 0.35f), w.x, w.y, w.z, 0, 0, 0);
    }

    /** Ink the player can draw with: the nib plus an inkwell of the same ink in the other hand; infinite in creative. */
    static double inkAvailable(LocalPlayer p) {
        if (p.getAbilities().instabuild) return Double.MAX_VALUE;
        ItemStack pen = heldPen(p);
        if (pen.isEmpty()) return 0;
        if (((InkPenItem) pen.getItem()).selfInked()) return Math.max(0, (pen.getMaxDamage() - pen.getDamageValue()) * 4 - 2);
        InkType ink = InkPenItem.inkFor(p, pen);
        var nib = InkPenItem.loaded(pen);
        double units = nib != null && nib.type() == ink ? nib.amount() : 0;
        ItemStack wellStack = InkPenItem.findInkwell(p, pen, ink);
        var well = wellStack.isEmpty() ? null : com.fimkov.witchsatelier.item.InkwellItem.contents(wellStack);
        if (well != null && well.type() == ink) units += well.amount();
        return Math.max(0, units - 2); // the server rounds each piece up
    }

    private static Pt cursor(float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || plane == null) return null;
        Vec3 eye = p.getEyePosition(pt), look = p.getViewVector(pt);
        // drawing on a ship: look at its deck from where the eye is in the ship's own plot
        var toPlot = com.fimkov.witchsatelier.compat.Ships.toPlot(mc.level, planeBlock, pt, false);
        if (toPlot != null) {
            eye = toPlot.apply(eye);
            look = com.fimkov.witchsatelier.compat.Ships.toPlot(mc.level, planeBlock, pt, true).apply(look);
        }
        Vec3 hit = plane.intersect(eye, look);
        if (hit == null || hit.distanceTo(eye) > GlyphService.REACH - 0.5) return null;
        Pt uv = plane.toUV(hit);
        if (!GlyphService.isDrawable(mc.level, plane, uv.x(), uv.y())) return null;
        return uv;
    }

    private static void finish() {
        // lifted before two laps: what was imagined goes down as it was drawn
        if (!erasing && count > 0 && !laid) flush();
        imagining = false;
        imagined = null;
        plane = null;
        hover = null;
        count = 0;
    }

    /** Two laps in the air: lay down the true ring they showed. */
    private static void layImaginedRing() {
        double[] ring = com.fimkov.witchsatelier.glyph.recognize.ImaginedRing.trueRing(buf, count, IMAGINE_MIN, IMAGINE_MAX, 0.35, 0.08);
        if (ring == null) {
            count = 0; // the laps were no ring: nothing goes down
            return;
        }
        int n = ring.length / 2;
        var mc = Minecraft.getInstance();
        if (mc.level != null && plane != null) {
            var w = plane.toWorld(new Pt(ring[0], ring[1]), 0);
            mc.level.playLocalSound(w.x, w.y, w.z, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.6f, false);
        }
        // in pieces that share their end points, as long lines always are
        int start = 0;
        while (start < n - 1) {
            int end = Math.min(n - 1, start + MAX_POINTS - 1);
            count = 0;
            for (int i = start; i <= end; i++) {
                if (count * 2 + 2 > buf.length) {
                    double[] nb = new double[buf.length * 2];
                    System.arraycopy(buf, 0, nb, 0, buf.length);
                    buf = nb;
                }
                buf[count * 2] = ring[i * 2];
                buf[count * 2 + 1] = ring[i * 2 + 1];
                count++;
            }
            flush();
            start = end;
        }
    }

    /**
     * Lines sent to the server but not yet back: kept on screen until their ink arrives (or a second has passed), so
     * a finished line never blinks out for the moment the round trip takes.
     */
    public record Sent(SurfacePlane plane, double[] uv, int n, int color, long until) {}
    private static final java.util.List<Sent> SENT = new java.util.ArrayList<>();

    public static java.util.List<Sent> sent() {
        long now = net.minecraft.Util.getMillis();
        SENT.removeIf(s -> s.until() < now);
        return SENT;
    }

    /** The server's ink for a sent line has arrived: stop showing our copy of it. */
    static void arrived(SurfacePlane on, InkStroke s) {
        if (SENT.isEmpty() || s.size() == 0) return;
        SENT.removeIf(x -> x.plane().equals(on) && Math.abs(x.uv()[0] - s.u(0)) < 0.01 && Math.abs(x.uv()[1] - s.v(0)) < 0.01);
    }

    private static void flush() {
        if (count > 0 && plane != null && !erasing) {
            SENT.add(new Sent(plane, java.util.Arrays.copyOf(buf, count * 2), count, currentInk().color, net.minecraft.Util.getMillis() + 1000));
        }
        if (count > 0 && plane != null) {
            int au = (int) Math.floor(buf[0]), av = (int) Math.floor(buf[1]);
            float[] rel = new float[count * 2];
            for (int i = 0; i < count; i++) {
                rel[i * 2] = (float) (buf[i * 2] - au);
                rel[i * 2 + 1] = (float) (buf[i * 2 + 1] - av);
            }
            PacketDistributor.sendToServer(new Payloads.DrawStroke(plane, au, av, rel));
        }
        count = 0;
    }
}
