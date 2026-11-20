package com.witchcraft.client.portal;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.witchcraft.WitchCraft;
import com.witchcraft.network.Payloads;
import com.witchcraft.windowway.PortalFrame;
import com.witchcraft.windowway.WindowwayService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Windowways on the client. Seen from the front, a joined window shows what is really on the other side: before
 * each frame the world is drawn a second time from the matching place behind the partner window (with
 * everything between that eye and the partner's surface cut away), and that picture is laid into the disc.
 * Walking through is done here too, the moment our own player crosses, so the view carries straight on.
 */
public final class WindowwayClient {
    private WindowwayClient() {}

    private static final Map<Long, PortalFrame> PORTALS = new HashMap<>();
    private static ShaderInstance viewShader;
    private static RenderTarget view;
    private static final PortalCamera CAMERA = new PortalCamera();
    /** Which window the picture in {@link #view} belongs to this frame (-1: none). */
    private static long viewing = -1, viewPartner = -1;
    private static boolean inPortalPass;
    private static Matrix4f lastProjection;
    private static Frustum lastFrustum;
    private static Graph graph;

    public static void accept(List<PortalFrame> frames) {
        PORTALS.clear();
        for (PortalFrame f : frames) PORTALS.put(f.id(), f);
    }

    public static boolean inPortalPass() {
        return inPortalPass;
    }

    public static void registerShaders(RegisterShadersEvent e) {
        try {
            e.registerShader(new ShaderInstance(e.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "portal_view"),
                    DefaultVertexFormat.POSITION_COLOR), s -> viewShader = s);
        } catch (java.io.IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    // ------------------------------------------------------------------ crossing

    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || PORTALS.isEmpty() || p.isPassenger()) return;
        double h = p.getBbHeight() / 2;
        Vec3 prev = new Vec3(p.xo, p.yo + h, p.zo), cur = p.position().add(0, h, 0);
        if (prev.distanceToSqr(cur) > 25) return; // a teleport, not a step
        for (PortalFrame f : PORTALS.values()) {
            PortalFrame to = f.link() < 0 ? null : PORTALS.get(f.link());
            if (to == null || !WindowwayService.crossed(f, prev, cur)) continue;
            PacketDistributor.sendToServer(new Payloads.PortalCross(f.id()));
            if (to.center().distanceTo(p.position()) <= 96) carry(p, f, to);
            return;
        }
    }

    /** Moves our player through, the remembered previous position and turn too, so nothing visibly jumps. */
    private static void carry(LocalPlayer p, PortalFrame from, PortalFrame to) {
        double h = p.getBbHeight() / 2;
        Vec3 now = PortalFrame.mapPoint(from, to, p.position().add(0, h, 0)).subtract(0, h, 0);
        Vec3 old = PortalFrame.mapPoint(from, to, new Vec3(p.xo, p.yo + h, p.zo)).subtract(0, h, 0);
        float[] yp = PortalFrame.yawPitch(PortalFrame.mapDir(from, to, p.getViewVector(1)));
        float[] ypOld = PortalFrame.yawPitch(PortalFrame.mapDir(from, to, p.getViewVector(0)));
        float dYaw = yp[0] - p.getYRot();
        Vec3 vel = PortalFrame.mapDir(from, to, p.getDeltaMovement());
        p.setPos(now);
        p.xo = old.x; p.yo = old.y; p.zo = old.z;
        p.xOld = old.x; p.yOld = old.y; p.zOld = old.z;
        p.setYRot(yp[0]);
        p.setXRot(yp[1]);
        p.yRotO = ypOld[0];
        p.xRotO = ypOld[1];
        p.yHeadRot += dYaw; p.yHeadRotO += dYaw;
        p.yBodyRot += dYaw; p.yBodyRotO += dYaw;
        p.yBob += dYaw; p.yBobO += dYaw;
        p.setDeltaMovement(vel);
    }

    // ------------------------------------------------------------------ the second pass

    /** Before the frame: if a joined window is in view, draw the world as seen through it into {@link #view}. */
    public static void onFramePre(RenderFrameEvent.Pre e) {
        viewing = -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || PORTALS.isEmpty() || lastProjection == null || mc.noRender) return;
        if (System.getProperty("witchcraft.fxoff", "").contains("portalpass")) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        var type = mc.options.getCameraType();
        // the camera's eye height is eased along by its tick, which only the main camera gets: borrow it, or the eye
        // would sit at the player's feet
        PortalCamera.copyEyeHeight(mc.gameRenderer.getMainCamera(), CAMERA);
        CAMERA.setup(mc.level, mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity(), !type.isFirstPerson(), type.isMirrored(), pt);
        Vec3 eye = CAMERA.getPosition();
        Quaternionf rot = new Quaternionf(CAMERA.rotation());

        PortalFrame best = null, partner = null;
        double bestD = 48;
        double reach = mc.options.getEffectiveRenderDistance() * 16 - 24;
        for (PortalFrame f : PORTALS.values()) {
            PortalFrame to = f.link() < 0 ? null : PORTALS.get(f.link());
            if (to == null || f.side(eye) <= 0.01) continue;
            double d = f.center().distanceTo(eye);
            if (d >= bestD || to.center().distanceTo(mc.player.position()) > reach) continue;
            if (lastFrustum != null && !lastFrustum.isVisible(new AABB(f.center(), f.center()).inflate(f.radius()))) continue;
            best = f;
            partner = to;
            bestD = d;
        }
        if (best == null) return;
        viewPartner = partner.id();

        // the eye behind the partner window, turned the same way
        Vec3 vEye = PortalFrame.mapPoint(best, partner, eye);
        Quaternionf vRot = PortalFrame.rotation(best, partner).mul(rot);
        CAMERA.setup(mc.level, mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity(), true, false, pt);
        CAMERA.place(vEye, vRot);
        Matrix4f modelView = new Matrix4f().rotation(new Quaternionf(vRot).conjugate());
        Matrix4f projection = oblique(lastProjection, modelView, partner, vEye);

        RenderTarget main = mc.getMainRenderTarget();
        LevelRenderer lr = mc.levelRenderer;
        if (graph == null) graph = new Graph();
        inPortalPass = true;
        try {
            if (!System.getProperty("witchcraft.fxoff", "").contains("noswap")) graph.swapIn(lr);
            lr.prepareCullFrustum(vEye, modelView, lastProjection);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            if (!System.getProperty("witchcraft.fxoff", "").contains("portalrender"))
                lr.renderLevel(e.getPartialTick(), false, CAMERA, mc.gameRenderer, mc.gameRenderer.lightTexture(), modelView, projection);
        } catch (Throwable t) {
            WitchCraft.LOGGER.warn("windowway view failed", t);
        } finally {
            graph.swapOut(lr);
            inPortalPass = false;
        }

        // keep the picture, then give the frame back a clean target
        if (view == null) {
            view = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            view.setClearColor(0, 0, 0, 0);
        }
        if (view.width != main.width || view.height != main.height) view.resize(main.width, main.height, Minecraft.ON_OSX);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, view.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, view.width, view.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        viewing = best.id();
    }

    /**
     * How far in front of the partner window's surface the picture is cut: past its own ink (lifted a few
     * hundredths off the wall), which would otherwise show from behind, mirrored, in the middle of the picture.
     */
    private static final double CUT = 0.03;

    /**
     * The projection with its near plane laid onto the partner window (Lengyel's oblique clipping), so whatever
     * is behind that window, between it and our eye, is not drawn. Worked out for any projection (the game's own
     * carries the view bobbing in it, so its last row is not the plain perspective one).
     */
    private static Matrix4f oblique(Matrix4f proj, Matrix4f modelView, PortalFrame at, Vec3 eye) {
        Vec3 n = at.n();
        Vec3 rel = at.center().subtract(eye);
        org.joml.Vector3f nv = modelView.transformDirection(new org.joml.Vector3f((float) n.x, (float) n.y, (float) n.z));
        Vector4f c = new Vector4f(nv.x, nv.y, nv.z, (float) -(n.dot(rel) + CUT));
        if (c.w > -0.01f) return new Matrix4f(proj); // eye too close to the plane: nothing sensible to cut
        Matrix4f inv = new Matrix4f(proj).invert();
        // the plane in clip space, then the far corner of the view on the plane's far side
        Vector4f cc = new Matrix4f(inv).transpose().transform(new Vector4f(c));
        Vector4f q = inv.transform(new Vector4f(Math.signum(cc.x), Math.signum(cc.y), 1f, 1f));
        Matrix4f m = new Matrix4f(proj);
        Vector4f row3 = new Vector4f(m.m03(), m.m13(), m.m23(), m.m33());
        float a = 2f * row3.dot(q) / c.dot(q);
        m.m02(a * c.x - row3.x);
        m.m12(a * c.y - row3.y);
        m.m22(a * c.z - row3.z);
        m.m32(a * c.w - row3.w);
        return m;
    }

    // ------------------------------------------------------------------ drawing the windows

    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || PORTALS.isEmpty()) return;
        if (!inPortalPass) {
            lastProjection = new Matrix4f(e.getProjectionMatrix());
            lastFrustum = e.getFrustum();
        }
        Vec3 cam = e.getCamera().getPosition();
        long time = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        for (PortalFrame f : PORTALS.values()) {
            if (f.center().distanceTo(cam) > 128) continue;
            boolean front = f.side(cam) > 0;
            // the far window we are looking out of is the cut plane itself: leave it out
            if (inPortalPass && f.id() == viewPartner) continue;
            boolean picture = !inPortalPass && front && f.id() == viewing && viewShader != null && view != null;
            disc(f, cam, picture, f.link() >= 0, time + e.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
    }

    /**
     * The colour a joined pair shares, the same on both windows (worked out from the pair, not from either one),
     * so with several windows about it is plain which goes with which.
     */
    static int pairColour(PortalFrame f) {
        if (f.link() < 0) return 0x8A8898;
        long key = Math.min(f.id(), f.link()) * 0x9E3779B97F4A7C15L;
        float hue = ((key >>> 40) & 0xFFFF) / 65536f;
        return java.awt.Color.HSBtoRGB(hue, 0.55f, 1f) & 0xFFFFFF;
    }

    private static void disc(PortalFrame f, Vec3 cam, boolean picture, boolean joined, double time) {
        int seg = 64;
        double r = f.discRadius();
        Vec3 c = f.center().add(f.n().scale(f.side(cam) > 0 ? 0.004 : -0.004)).subtract(cam);
        int pair = pairColour(f);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            Vec3 p0 = c.add(f.u().scale(Math.cos(a0) * r)).add(f.v().scale(Math.sin(a0) * r));
            Vec3 p1 = c.add(f.u().scale(Math.cos(a1) * r)).add(f.v().scale(Math.sin(a1) * r));
            if (picture) {
                // colour carries a tint (rgb) and how much of it (alpha): none in the middle, the pair's colour at the rim
                vtx(b, c, pair, 0);
                vtx(b, p0, pair, 120);
                vtx(b, p1, pair, 120);
            } else {
                // a dark, slowly turning membrane: rimmed in the pair's colour, or a dim grey while still waiting
                double sw = Math.sin(time * (joined ? 0.05 : 0.025) + i * 0.4) * 0.5 + 0.5;
                int rim = joined ? lerp(lerp(0x07050F, pair, 0.35), pair, sw) : lerp(0x18161F, 0x4A4858, sw);
                vtx(b, c, 0x07050F, 255);
                vtx(b, p0, rim, 255);
                vtx(b, p1, rim, 255);
            }
        }
        if (picture) {
            RenderSystem.setShader(() -> viewShader);
            RenderSystem.setShaderTexture(0, view.getColorTextureId());
        } else {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.enableCull();
    }

    private static void vtx(BufferBuilder b, Vec3 p, int rgb, int a) {
        b.addVertex((float) p.x, (float) p.y, (float) p.z).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a);
    }

    private static int lerp(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
