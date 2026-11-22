package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import com.fimkov.witchsatelier.glyph.page.PageKind;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.item.InkPenItem;
import com.fimkov.witchsatelier.network.Payloads;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

public final class TraceGuide {
    private TraceGuide() {}

    static double scale = 2.0;
    private static final double MIN_SCALE = 0.6, MAX_SCALE = 6;

    private static SurfacePlane lockPlane;
    private static Pt lockAt;
    private static double lockTurn;

    static ItemStack grimoire(LocalPlayer p) {
        if (p == null) return ItemStack.EMPTY;
        ItemStack main = p.getMainHandItem(), off = p.getOffhandItem();
        if (main.getItem() instanceof InkPenItem && isBook(PageKind.of(off))) return off;
        if (off.getItem() instanceof InkPenItem && isBook(PageKind.of(main))) return main;
        return ItemStack.EMPTY;
    }

    static void onScroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.player.isShiftKeyDown() || grimoire(mc.player).isEmpty()) return;
        scale = Mth.clamp(scale * (e.getScrollDeltaY() > 0 ? 1.1 : 1 / 1.1), MIN_SCALE, MAX_SCALE);
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
            GlyphRenderer.drawStroke(vc, pose, cam, plane, uv, n, inkWidth * 0.8f, argb, 0.0012);
        }
    }

    private static SurfacePlane sentPlane;
    private static double sentU, sentV, sentTurn, sentScale;
    private static long sentAt;

    private static int shown;

    private static void sync(boolean holding) {
        Minecraft mc = Minecraft.getInstance();
        int state = !holding ? 0 : lockPlane == null ? 1 : 2;
        if (state != shown) {
            if (state == 1) mc.gui.setOverlayMessage(Component.translatable("message.witchsatelier.trace_follow"), false);
            if (state == 2) mc.gui.setOverlayMessage(Component.translatable("message.witchsatelier.trace_pinned"), false);
            shown = state;
        }
        if (mc.getConnection() == null) return;
        long now = Util.getMillis();
        if (lockPlane == null) {
            if (sentPlane != null) {
                PacketDistributor.sendToServer(new Payloads.TracePin(false,
                        new SurfacePlane(Direction.UP, 0), 0, 0, 0, 1));
                sentPlane = null;
            }
            return;
        }
        boolean changed = !lockPlane.equals(sentPlane) || lockAt.x() != sentU || lockAt.y() != sentV || lockTurn != sentTurn || scale != sentScale;
        if (!changed || now - sentAt < 100) return;
        PacketDistributor.sendToServer(new Payloads.TracePin(true, lockPlane, lockAt.x(), lockAt.y(), lockTurn, scale));
        sentPlane = lockPlane;
        sentU = lockAt.x();
        sentV = lockAt.y();
        sentTurn = lockTurn;
        sentScale = scale;
        sentAt = now;
    }

    static double upAngle(SurfacePlane plane, Vec3 look) {
        Vec3 n = plane.normal();
        Vec3 up = Math.abs(n.y) < 0.5 ? new Vec3(0, 1, 0) : new Vec3(look.x, 0, look.z);
        if (up.lengthSqr() < 1e-6) return Math.PI / 2;
        return Math.atan2(up.dot(plane.planeVector(0, 1)), up.dot(plane.planeVector(1, 0)));
    }

    private static boolean isBook(PageKind k) {
        return k != null && k.book();
    }
}
