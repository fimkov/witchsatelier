package com.fimkov.witchsatelier.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import com.fimkov.witchsatelier.glyph.page.PageKind;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.item.InkPenItem;
import com.fimkov.witchsatelier.registry.ModComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import org.joml.Matrix4f;

/**
 * Copying from a grimoire: with a pen in one hand and the open grimoire in the other, the glyph on its current page
 * shows faintly on the surface you look at, the size you choose (scroll), so the hand can follow it. Press Shift to
 * pin it where you look (hold Shift and look to move it); pinned, it stays while you trace. Only the one copying
 * sees it; it lets go when the grimoire leaves the hand.
 */
public final class TraceGuide {
    private TraceGuide() {}

    /** Blocks per page unit (a grimoire ring is about 0.8 units across its radius). */
    static double scale = 2.0;
    private static final double MIN_SCALE = 0.6, MAX_SCALE = 6;

    private static SurfacePlane lockPlane;
    private static Pt lockAt;
    private static double lockTurn;

    /** The grimoire in the hand that does not hold the pen, or empty. */
    static ItemStack grimoire(LocalPlayer p) {
        if (p == null) return ItemStack.EMPTY;
        ItemStack main = p.getMainHandItem(), off = p.getOffhandItem();
        if (main.getItem() instanceof InkPenItem && PageKind.of(off) == PageKind.GRIMOIRE) return off;
        if (off.getItem() instanceof InkPenItem && PageKind.of(main) == PageKind.GRIMOIRE) return main;
        return ItemStack.EMPTY;
    }

    static void onScroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.player.isShiftKeyDown() || grimoire(mc.player).isEmpty()) return;
        scale = net.minecraft.util.Mth.clamp(scale * (e.getScrollDeltaY() > 0 ? 1.1 : 1 / 1.1), MIN_SCALE, MAX_SCALE);
        e.setCanceled(true);
    }

    static void render(VertexConsumer vc, Matrix4f pose, Vec3 cam, float inkWidth) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ItemStack book = grimoire(p);
        if (book.isEmpty()) { lockPlane = null; sync(false); return; }
        GlyphPages gp = book.get(ModComponents.GLYPH_PAGES.get());
        if (gp == null || gp.pages().isEmpty()) return;
        GlyphPages.Page page = gp.page(gp.current());
        if (page.strokes().isEmpty()) return;

        long now = Util.getMillis();
        SurfacePlane hoverPlane = null;
        Pt hoverAt = null;
        if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
            hoverPlane = SurfacePlane.of(bh.getBlockPos(), bh.getDirection());
            hoverAt = hoverPlane.toUV(bh.getLocation());
        }
        // Shift pins the guide where you look (hold it and look to move it); until then it follows your eye
        if (p.isShiftKeyDown() && ClientDrawing.plane() == null && hoverPlane != null) {
            lockPlane = hoverPlane;
            lockAt = hoverAt;
            lockTurn = upAngle(hoverPlane, p.getLookAngle());
        }
        if (lockPlane != null && lockPlane.toWorld(lockAt.x(), lockAt.y(), 0).distanceToSqr(p.position()) > 32 * 32) lockPlane = null;
        sync(true);

        SurfacePlane plane;
        Pt at;
        double turn;
        if (lockPlane != null) {
            plane = lockPlane;
            at = lockAt;
            turn = lockTurn;
        } else {
            if (hoverPlane == null) return;
            plane = hoverPlane;
            at = hoverAt;
            turn = upAngle(plane, p.getLookAngle());
        }
        // page "up" (0, 1) turned to the drawer's up on the surface
        double rot = turn - Math.PI / 2, c = Math.cos(rot), s = Math.sin(rot);
        float pulse = 0.75f + 0.25f * (float) Math.sin(now / 400.0);
        int argb = ((int) (0x70 * pulse) << 24) | 0xD8E6FF;
        for (GlyphPages.PageStroke st : page.strokes()) {
            float[] pts = st.pts();
            int n = pts.length / 2;
            double[] uv = new double[n * 2];
            for (int i = 0; i < n; i++) {
                double x = pts[i * 2] * scale, y = pts[i * 2 + 1] * scale;
                uv[i * 2] = at.x() + x * c - y * s;
                uv[i * 2 + 1] = at.y() + x * s + y * c;
            }
            GlyphRenderer.drawStroke(vc, pose, cam, plane, uv, n, inkWidth * 0.8f, argb, 0.0012); // under the ink (0.0025)
        }
    }

    // what the server was last told, so it is told only of changes (and not every frame while Shift moves the pin)
    private static SurfacePlane sentPlane;
    private static double sentU, sentV, sentTurn, sentScale;
    private static long sentAt;
    /** 0 nothing shown, 1 the guide follows the eye, 2 it is pinned: a hint is shown when this changes. */
    private static int shown;

    /** Tells the server where the guide is pinned (it reads a faithful copy as the page's glyph), and hints how to use it. */
    private static void sync(boolean holding) {
        Minecraft mc = Minecraft.getInstance();
        int state = !holding ? 0 : lockPlane == null ? 1 : 2;
        if (state != shown) {
            if (state == 1) mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable("message.witchsatelier.trace_follow"), false);
            if (state == 2) mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable("message.witchsatelier.trace_pinned"), false);
            shown = state;
        }
        if (mc.getConnection() == null) return;
        long now = Util.getMillis();
        if (lockPlane == null) {
            if (sentPlane != null) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.fimkov.witchsatelier.network.Payloads.TracePin(false,
                        new SurfacePlane(net.minecraft.core.Direction.UP, 0), 0, 0, 0, 1));
                sentPlane = null;
            }
            return;
        }
        boolean changed = !lockPlane.equals(sentPlane) || lockAt.x() != sentU || lockAt.y() != sentV || lockTurn != sentTurn || scale != sentScale;
        if (!changed || now - sentAt < 100) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.fimkov.witchsatelier.network.Payloads.TracePin(true, lockPlane, lockAt.x(), lockAt.y(), lockTurn, scale));
        sentPlane = lockPlane;
        sentU = lockAt.x();
        sentV = lockAt.y();
        sentTurn = lockTurn;
        sentScale = scale;
        sentAt = now;
    }

    /** The witch's "up" on a surface: straight up on a wall, the way they face on a floor or ceiling. */
    static double upAngle(SurfacePlane plane, Vec3 look) {
        Vec3 n = plane.normal();
        Vec3 up = Math.abs(n.y) < 0.5 ? new Vec3(0, 1, 0) : new Vec3(look.x, 0, look.z);
        if (up.lengthSqr() < 1e-6) return Math.PI / 2;
        return Math.atan2(up.dot(plane.planeVector(0, 1)), up.dot(plane.planeVector(1, 0)));
    }
}
