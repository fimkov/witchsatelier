package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.compat.Ships;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.recognize.ImaginedRing;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.recognize.RingFinder;
import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.item.InkPenItem;
import com.fimkov.witchsatelier.item.InkwellItem;
import com.fimkov.witchsatelier.item.RagItem;
import com.fimkov.witchsatelier.network.Payloads;
import com.fimkov.witchsatelier.registry.ModSounds;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ClientDrawing {
    private static final double MIN_STEP = 0.012;
    private static final int MAX_POINTS = InkStroke.MAX_POINTS - 8;

    private static SurfacePlane plane;
    private static boolean erasing;
    private static double[] buf = new double[256];
    private static int count;
    private static int eraseCooldown;
    private static Pt hover;

    private static float lastYaw, lastPitch;
    private static long lastNanos;
    static float swayX, swayY;
    static float scratch;
    private static double lastCursorU, lastCursorV;

    private static double budget;
    private static long lastScratch;
    private static boolean dryWarned;

    private ClientDrawing() {}

    public static SurfacePlane plane() { return plane; }

    public static boolean imaginedRing;

    private static boolean imagining;
    private static RingFinder.Circle imagined;
    private static int imaginedAt;

    private static final double IMAGINE_MIN = 0.45, IMAGINE_MAX = 12;

    public static boolean imagining() { return imagining; }

    public static void debugImagine(SurfacePlane on, double[] uv) {
        plane = on;
        erasing = false;
        buf = Arrays.copyOf(uv, Math.max(uv.length, 256));
        count = uv.length / 2;
        imagining = true;
        imagined = null;
        debugHold = true;
    }

    private static boolean debugHold;

    private static BlockPos planeBlock = BlockPos.ZERO;

    private static boolean laid;

    public static RingFinder.Circle imaginedCircle() {
        if (!imagining) return null;
        if (imagined == null || count - imaginedAt >= 6) {
            List<Pt> pts = new ArrayList<>(count);
            for (int i = 0; i < count; i++) pts.add(new Pt(u(i), v(i)));
            var c = RingFinder.fit(pts);
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

    public static double eraseRadius() {
        LocalPlayer p = Minecraft.getInstance().player;
        boolean rag = p != null && (p.getMainHandItem().getItem() instanceof RagItem || p.getOffhandItem().getItem() instanceof RagItem);
        return rag ? ragRadius : GlyphService.PEN_ERASE_RADIUS;
    }

    private static double ragRadius = GlyphService.RAG_RADIUS;

    static void onScroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null || !p.isShiftKeyDown()) return;
        if (!(p.getMainHandItem().getItem() instanceof RagItem || p.getOffhandItem().getItem() instanceof RagItem)) return;
        ragRadius = Mth.clamp(ragRadius * (e.getScrollDeltaY() > 0 ? 1.15 : 1 / 1.15), GlyphService.RAG_MIN, GlyphService.RAG_MAX);
        e.setCanceled(true);
    }

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

    static void onInteract(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (!e.isUseItem() || mc.screen != null || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (heldPen(mc.player).isEmpty()) return;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) return;
        if (ItemCanvas.findPage(mc.player) != null) {
            e.setCanceled(true);
            e.setSwingHand(false);
            ItemCanvas.open(mc.player);
        }
    }

    static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.getConnection() == null) {
            plane = null;
            count = 0;
            imagining = false;
            laid = false;
            imagined = null;
            hover = null;
            return;
        }
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

    static void sample(float partialTick) {
        LocalPlayer p = Minecraft.getInstance().player;
        long nowNs = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (nowNs - lastNanos) / 1e9f);
        lastNanos = nowNs;
        if (p != null && dt > 0) {
            float yaw = p.getViewYRot(partialTick), pitch = p.getViewXRot(partialTick);
            float dy = Mth.wrapDegrees(yaw - lastYaw) / dt, dp = (pitch - lastPitch) / dt;
            lastYaw = yaw;
            lastPitch = pitch;
            float k = 1 - (float) Math.exp(-dt * 7);
            swayX += (Mth.clamp(dy / 240f, -1, 1) - swayX) * k;
            swayY += (Mth.clamp(dp / 240f, -1, 1) - swayY) * k;
        }
        if (plane == null) {
            hover = null;
            return;
        }
        Pt at = cursor(partialTick);
        hover = at;
        if (at != null && dt > 0) {
            double speed = Math.hypot(at.x() - lastCursorU, at.y() - lastCursorV) / dt;
            lastCursorU = at.x();
            lastCursorV = at.y();
            float target = erasing ? 0 : (float) Math.min(1, speed / 2.5);
            scratch += (target - scratch) * (1 - (float) Math.exp(-dt * 10));
        }
        if (erasing || laid) return;
        if (at == null) {
            if (count > 0 && !imagining) flush();
            return;
        }
        double seg = 0;
        if (count > 0) {
            double du = at.x() - u(count - 1), dv = at.y() - v(count - 1);
            if (du * du + dv * dv < MIN_STEP * MIN_STEP) return;
            seg = Math.sqrt(du * du + dv * dv);
        }

        if (seg * GlyphService.INK_PER_BLOCK > budget) {
            if (!dryWarned) {
                Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("message.witchsatelier.pen_dry"), false);
                dryWarned = true;
            }
            return;
        }
        if (!imagining) budget -= seg * GlyphService.INK_PER_BLOCK;

        long now = Util.getMillis();
        if (now - lastScratch > 120 && plane != null) {
            lastScratch = now;
            var w = plane.toWorld(at, 0);
            var mc = Minecraft.getInstance();
            if (mc.level != null) mc.level.playLocalSound(w.x, w.y, w.z, ModSounds.QUILL_SCRATCH.get(),
                    SoundSource.PLAYERS, erasing ? 0.15f : 0.35f, 0.85f + (float) Math.random() * 0.3f, false);
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
                && ImaginedRing.noticed(buf, count, IMAGINE_MIN, IMAGINE_MAX)) {
            imagining = true;
            budget = inkAvailable(Minecraft.getInstance().player);
        }
        if (imagining && count % 3 == 0 && ImaginedRing.circledTwice(buf, count)) {
            layImaginedRing();
            laid = true;
            imagining = false;
            imagined = null;
            return;
        }
        if (count % 4 == 0 && !imagining) inkSpatter(at);
        if (imagining && count >= MAX_POINTS * 3) return;
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

    static double inkAvailable(LocalPlayer p) {
        if (p.getAbilities().instabuild) return Double.MAX_VALUE;
        ItemStack pen = heldPen(p);
        if (pen.isEmpty()) return 0;
        if (((InkPenItem) pen.getItem()).selfInked()) return Math.max(0, (pen.getMaxDamage() - pen.getDamageValue()) * 4 - 2);
        InkType ink = InkPenItem.inkFor(p, pen);
        var nib = InkPenItem.loaded(pen);
        double units = nib != null && nib.type() == ink ? nib.amount() : 0;
        ItemStack wellStack = InkPenItem.findInkwell(p, pen, ink);
        var well = wellStack.isEmpty() ? null : InkwellItem.contents(wellStack);
        if (well != null && well.type() == ink) units += well.amount();
        return Math.max(0, units - 2);
    }

    private static Pt cursor(float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || plane == null) return null;
        Vec3 eye = p.getEyePosition(pt), look = p.getViewVector(pt);

        var toPlot = Ships.toPlot(mc.level, planeBlock, pt, false);
        if (toPlot != null) {
            eye = toPlot.apply(eye);
            look = Ships.toPlot(mc.level, planeBlock, pt, true).apply(look);
        }
        Vec3 hit = plane.intersect(eye, look);
        if (hit == null || hit.distanceTo(eye) > GlyphService.REACH - 0.5) return null;
        Pt uv = plane.toUV(hit);
        if (!GlyphService.isDrawable(mc.level, plane, uv.x(), uv.y())) return null;
        return uv;
    }

    private static void finish() {
        if (!erasing && count > 0 && !laid) flush();
        imagining = false;
        imagined = null;
        plane = null;
        hover = null;
        count = 0;
    }

    private static void layImaginedRing() {
        double[] ring = ImaginedRing.trueRing(buf, count, IMAGINE_MIN, IMAGINE_MAX, 0.35, 0.08);
        if (ring == null) {
            count = 0;
            return;
        }
        int n = ring.length / 2;
        var mc = Minecraft.getInstance();
        if (mc.level != null && plane != null) {
            var w = plane.toWorld(new Pt(ring[0], ring[1]), 0);
            mc.level.playLocalSound(w.x, w.y, w.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5f, 1.6f, false);
        }

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

    public record Sent(SurfacePlane plane, double[] uv, int n, int color, long until) {}
    private static final List<Sent> SENT = new ArrayList<>();

    public static List<Sent> sent() {
        long now = Util.getMillis();
        SENT.removeIf(s -> s.until() < now);
        return SENT;
    }

    static void arrived(SurfacePlane on, InkStroke s) {
        if (SENT.isEmpty() || s.size() == 0) return;
        SENT.removeIf(x -> x.plane().equals(on) && Math.abs(x.uv()[0] - s.u(0)) < 0.01 && Math.abs(x.uv()[1] - s.v(0)) < 0.01);
    }

    private static void flush() {
        if (count > 0 && plane != null && !erasing) {
            SENT.add(new Sent(plane, Arrays.copyOf(buf, count * 2), count, currentInk().color, Util.getMillis() + 1000));
        }
        if (count > 0 && plane != null && Minecraft.getInstance().getConnection() != null) {
            for (int start = 0; start < count; start += MAX_POINTS - 1) {
                int end = Math.min(count, start + MAX_POINTS);
                int au = (int) Math.floor(buf[start * 2]), av = (int) Math.floor(buf[start * 2 + 1]);
                float[] rel = new float[(end - start) * 2];
                for (int i = start; i < end; i++) {
                    rel[(i - start) * 2] = (float) (buf[i * 2] - au);
                    rel[(i - start) * 2 + 1] = (float) (buf[i * 2 + 1] - av);
                }
                PacketDistributor.sendToServer(new Payloads.DrawStroke(plane, au, av, rel));
                if (end >= count) break;
            }
        }
        count = 0;
    }
}
