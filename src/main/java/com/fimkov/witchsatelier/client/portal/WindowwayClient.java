package com.fimkov.witchsatelier.client.portal;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.network.Payloads;
import com.fimkov.witchsatelier.windowway.PortalFrame;
import com.fimkov.witchsatelier.windowway.WindowwayService;
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
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WindowwayClient {
    private WindowwayClient() {}

    private static final Map<Long, PortalFrame> PORTALS = new HashMap<>();
    private static ShaderInstance viewShader;

    private static final int MAX_VIEWS = 2;
    private static final RenderTarget[] VIEWS = new RenderTarget[MAX_VIEWS];
    private static final PortalCamera CAMERA = new PortalCamera();

    private static final long[] VIEWING = new long[MAX_VIEWS];

    private static long viewPartner = -1;
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
            e.registerShader(new ShaderInstance(e.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "portal_view"),
                    DefaultVertexFormat.POSITION_COLOR), s -> viewShader = s);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || PORTALS.isEmpty() || p.isPassenger()) return;
        double h = p.getBbHeight() / 2;
        Vec3 prev = new Vec3(p.xo, p.yo + h, p.zo), cur = p.position().add(0, h, 0);
        if (prev.distanceToSqr(cur) > 25) return;
        for (PortalFrame f : PORTALS.values()) {
            PortalFrame to = f.link() < 0 ? null : PORTALS.get(f.link());
            if (to == null || !WindowwayService.crossed(f, prev, cur)) continue;
            PacketDistributor.sendToServer(new Payloads.PortalCross(f.id()));
            if (to.center().distanceTo(p.position()) <= 96) carry(p, f, to);
            return;
        }
    }

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

    public static void onFramePre(RenderFrameEvent.Pre e) {
        Arrays.fill(VIEWING, -1);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || PORTALS.isEmpty() || lastProjection == null || mc.noRender) return;
        if (System.getProperty("witchsatelier.fxoff", "").contains("portalpass")) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        var type = mc.options.getCameraType();

        PortalCamera.copyEyeHeight(mc.gameRenderer.getMainCamera(), CAMERA);
        CAMERA.setup(mc.level, mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity(), !type.isFirstPerson(), type.isMirrored(), pt);
        Vec3 eye = CAMERA.getPosition();
        Quaternionf rot = new Quaternionf(CAMERA.rotation());

        double reach = mc.options.getEffectiveRenderDistance() * 16 - 24;
        List<PortalFrame> seen = new ArrayList<>();
        for (PortalFrame f : PORTALS.values()) {
            PortalFrame to = f.link() < 0 ? null : PORTALS.get(f.link());
            if (to == null || f.side(eye) <= 0.01) continue;
            if (f.center().distanceTo(eye) >= 48 || to.center().distanceTo(mc.player.position()) > reach) continue;
            if (lastFrustum != null && !lastFrustum.isVisible(new AABB(f.center(), f.center()).inflate(f.radius()))) continue;
            seen.add(f);
        }
        seen.sort(Comparator.comparingDouble(f -> f.center().distanceTo(eye)));
        for (int k = 0; k < Math.min(MAX_VIEWS, seen.size()); k++) {
            PortalFrame f = seen.get(k);
            if (picture(mc, e, pt, eye, rot, f, PORTALS.get(f.link()), k)) VIEWING[k] = f.id();
        }
    }

    private static boolean picture(Minecraft mc, RenderFrameEvent.Pre e, float pt, Vec3 eye, Quaternionf rot, PortalFrame best, PortalFrame partner, int k) {
        viewPartner = partner.id();

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
            if (!System.getProperty("witchsatelier.fxoff", "").contains("noswap")) graph.swapIn(lr);
            lr.prepareCullFrustum(vEye, modelView, lastProjection);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            if (!System.getProperty("witchsatelier.fxoff", "").contains("portalrender"))
                lr.renderLevel(e.getPartialTick(), false, CAMERA, mc.gameRenderer, mc.gameRenderer.lightTexture(), modelView, projection);
        } catch (Throwable t) {
            WitchsAtelier.LOGGER.warn("windowway view failed", t);
        } finally {
            graph.swapOut(lr);
            inPortalPass = false;
            viewPartner = -1;
        }

        RenderTarget view = VIEWS[k];
        if (view == null) {
            view = VIEWS[k] = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            view.setClearColor(0, 0, 0, 0);
        }
        if (view.width != main.width || view.height != main.height) view.resize(main.width, main.height, Minecraft.ON_OSX);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, view.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, view.width, view.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        return true;
    }

    private static final double CUT = 0.03;

    private static Matrix4f oblique(Matrix4f proj, Matrix4f modelView, PortalFrame at, Vec3 eye) {
        Vec3 n = at.n();
        Vec3 rel = at.center().subtract(eye);
        Vector3f nv = modelView.transformDirection(new Vector3f((float) n.x, (float) n.y, (float) n.z));
        Vector4f c = new Vector4f(nv.x, nv.y, nv.z, (float) -(n.dot(rel) + CUT));
        if (c.w > -0.01f) return new Matrix4f(proj);
        Matrix4f inv = new Matrix4f(proj).invert();

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

            if (inPortalPass && f.id() == viewPartner) continue;
            int k = inPortalPass || !front || viewShader == null ? -1 : pictureOf(f.id());
            disc(f, cam, k, f.link() >= 0, time + e.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
    }

    static int pairColour(PortalFrame f) {
        if (f.link() < 0) return 0x8A8898;
        long key = Math.min(f.id(), f.link()) * 0x9E3779B97F4A7C15L;
        float hue = ((key >>> 40) & 0xFFFF) / 65536f;
        return java.awt.Color.HSBtoRGB(hue, 0.55f, 1f) & 0xFFFFFF;
    }

    private static int pictureOf(long id) {
        for (int k = 0; k < MAX_VIEWS; k++) if (VIEWING[k] == id && VIEWS[k] != null) return k;
        return -1;
    }

    private static void disc(PortalFrame f, Vec3 cam, int k, boolean joined, double time) {
        boolean picture = k >= 0;
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
                vtx(b, c, pair, 0);
                vtx(b, p0, pair, 120);
                vtx(b, p1, pair, 120);
            } else {
                double sw = Math.sin(time * (joined ? 0.05 : 0.025) + i * 0.4) * 0.5 + 0.5;
                int rim = joined ? lerp(lerp(0x07050F, pair, 0.35), pair, sw) : lerp(0x18161F, 0x4A4858, sw);
                vtx(b, c, 0x07050F, 255);
                vtx(b, p0, rim, 255);
                vtx(b, p1, rim, 255);
            }
        }
        if (picture) {
            RenderSystem.setShader(() -> viewShader);
            RenderSystem.setShaderTexture(0, VIEWS[k].getColorTextureId());
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
