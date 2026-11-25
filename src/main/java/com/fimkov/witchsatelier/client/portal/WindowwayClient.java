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

    /**
     * There are two ways to show the far side of a joined Windowway. With the plain level renderer it is drawn as a
     * picture: the renderer runs a second time from the matching eye with its section graph swapped out. Mods that
     * replace the level renderer keep section lists of their own, which a second pass scrambles, and a shader pack
     * cannot run its pipeline twice a frame; with one of them installed the far side is meshed and drawn into the
     * world itself instead (see {@link FarSide}), where the pack lights it like everything else.
     */
    private static final List<String> OWN_RENDERERS = List.of("sodium", "embeddium", "rubidium", "iris", "oculus", "nvidium");
    private static Boolean inline;

    /** Whether the far side is drawn into the world itself rather than as a picture from a second level pass. */
    static boolean inline() {
        String off = System.getProperty("witchsatelier.fxoff", "");
        if (off.contains("forcepass")) return false;
        if (off.contains("inline")) return true;
        if (inline == null) {
            String other = OWN_RENDERERS.stream().filter(net.neoforged.fml.ModList.get()::isLoaded).findFirst().orElse(null);
            inline = other != null;
            if (other != null) WitchsAtelier.LOGGER.info("{} draws the world itself; Windowways draw their far side into it", other);
        }
        return inline;
    }

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
        if (frames.isEmpty()) FarSide.clear();
        for (PortalFrame f : frames) PORTALS.put(f.id(), f);
    }

    public static net.minecraft.client.renderer.SectionOcclusionGraph mainGraphDuringPass() {
        return Graph.mainDuringPass;
    }

    /** Discs whose far side was drawn into the world in the last frame. */
    private static List<PortalFrame> openedLast = List.of();

    /**
     * Ink lying wholly inside an opened disc is on wall that is not there any more: the far side shows instead. The
     * ring itself runs outside the opening and stays.
     */
    public static boolean hidesInk(com.fimkov.witchsatelier.glyph.surface.SurfacePlane plane, double[] uv, int n) {
        if (openedLast.isEmpty() || n <= 0) return false;
        for (PortalFrame f : openedLast) {
            boolean all = true;
            for (int i = 0; i < n && all; i++) {
                Vec3 p = plane.toWorld(uv[i * 2], uv[i * 2 + 1], 0);
                all = Math.abs(f.side(p)) < 0.1 && f.inPlane(p) < f.discRadius() * 0.97;
            }
            if (all) return true;
        }
        return false;
    }

    public static void blockChanged(net.minecraft.core.BlockPos pos) {
        FarSide.blockChanged(pos);
    }

    public static void sectionChanged(int x, int y, int z) {
        FarSide.sectionChanged(x, y, z);
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
        FarSide.endFrame();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || PORTALS.isEmpty() || lastProjection == null || mc.noRender) return;
        if (System.getProperty("witchsatelier.fxoff", "").contains("portalpass") || inline()) return;
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
        boolean inline = inline();
        if (inline && e.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS && !FarSide.shadowPass()) {
            try {
                FarSide.drawTranslucent();
            } catch (Throwable ex) {
                WitchsAtelier.LOGGER.warn("windowway far side failed", ex);
            }
            return;
        }
        if (e.getStage() != (inline ? RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES : RenderLevelStageEvent.Stage.AFTER_ENTITIES) || PORTALS.isEmpty()) return;
        if (inline && FarSide.shadowPass()) return;
        if (!inPortalPass) {
            lastProjection = new Matrix4f(e.getProjectionMatrix());
            lastFrustum = e.getFrustum();
        }
        Vec3 cam = e.getCamera().getPosition();
        Minecraft mc = Minecraft.getInstance();
        long time = mc.level == null ? 0 : mc.level.getGameTime();
        double t = time + e.getPartialTick().getGameTimeDeltaPartialTick(false);
        List<PortalFrame> open = inline ? opened(mc, cam, viewFrustum(e, cam)) : List.of();
        List<PortalFrame> drawnNow = new ArrayList<>();

        for (PortalFrame f : PORTALS.values()) {
            if (f.center().distanceTo(cam) > 128) continue;
            boolean front = f.side(cam) > 0;

            if (inPortalPass && f.id() == viewPartner) continue;
            if (open.contains(f)) {
                boolean drawn = false;
                try {
                    drawn = FarSide.draw(f, PORTALS.get(f.link()), e);
                } catch (Throwable ex) {
                    WitchsAtelier.LOGGER.warn("windowway far side failed", ex);
                }
                if (drawn) {
                    drawnNow.add(f);
                    rim(f, cam, t);
                    continue;
                }
            }
            int k = inPortalPass || !front || viewShader == null ? -1 : pictureOf(f.id());
            disc(f, cam, k, f.link() >= 0, t);
        }
        if (!inPortalPass) openedLast = drawnNow;
    }

    /**
     * The view of this frame as a frustum of our own: Sodium culls with its own and leaves the level renderer's
     * stale, so the event's frustum cannot be trusted with one installed.
     */
    private static Frustum viewFrustum(RenderLevelStageEvent e, Vec3 cam) {
        Frustum f = new Frustum(e.getModelViewMatrix(), e.getProjectionMatrix());
        f.prepare(cam.x, cam.y, cam.z);
        return f;
    }

    /** The joined discs, nearest first, whose far side is drawn into the world this frame. */
    private static List<PortalFrame> opened(Minecraft mc, Vec3 eye, Frustum frustum) {
        if (mc.player == null) return List.of();
        double reach = mc.options.getEffectiveRenderDistance() * 16 - 24;
        List<PortalFrame> seen = new ArrayList<>();
        for (PortalFrame f : PORTALS.values()) {
            PortalFrame to = f.link() < 0 ? null : PORTALS.get(f.link());
            if (to == null || f.side(eye) <= 0.01) continue;
            if (f.center().distanceTo(eye) >= 64 || to.center().distanceTo(mc.player.position()) > reach) continue;
            if (frustum != null && !frustum.isVisible(new AABB(f.center(), f.center()).inflate(f.radius()))) continue;
            seen.add(f);
        }
        seen.sort(Comparator.comparingDouble(f -> f.center().distanceTo(eye)));
        return seen.size() > 3 ? seen.subList(0, 3) : seen;
    }

    /** The glowing edge of an opened disc, in the colour the pair shares; drawn as light, so shader packs keep it. */
    private static void rim(PortalFrame f, Vec3 cam, double time) {
        if (System.getProperty("witchsatelier.fxoff", "").contains("norim")) return;
        int seg = 96;
        double r = f.discRadius();
        Vec3 c = f.center().add(f.n().scale(f.side(cam) > 0 ? 0.006 : -0.006)).subtract(cam);
        int pair = pairColour(f);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var b = buffers.getBuffer(net.minecraft.client.renderer.RenderType.lightning());
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            double sw0 = Math.sin(time * 0.08 + i * 0.35) * 0.5 + 0.5, sw1 = Math.sin(time * 0.08 + (i + 1) * 0.35) * 0.5 + 0.5;
            ring(b, c, f, a0, a1, r * 0.9, r * 1.0, lerp(pair, 0xFFFFFF, 0.25), (int) (40 + 70 * sw0), (int) (40 + 70 * sw1), 0);
            ring(b, c, f, a0, a1, r * 1.0, r * 1.06, lerp(pair, 0xFFFFFF, 0.5), (int) (150 + 100 * sw0), (int) (150 + 100 * sw1), 1);
        }
        buffers.endBatch(net.minecraft.client.renderer.RenderType.lightning());
    }

    /** One slice of a band from radius {@code r0} to {@code r1}; the glow fades towards the inside or the outside. */
    private static void ring(com.mojang.blaze3d.vertex.VertexConsumer b, Vec3 c, PortalFrame f, double a0, double a1, double r0, double r1, int rgb, int alpha0, int alpha1, int fadeIn) {
        Vec3 i0 = c.add(f.u().scale(Math.cos(a0) * r0)).add(f.v().scale(Math.sin(a0) * r0));
        Vec3 i1 = c.add(f.u().scale(Math.cos(a1) * r0)).add(f.v().scale(Math.sin(a1) * r0));
        Vec3 o0 = c.add(f.u().scale(Math.cos(a0) * r1)).add(f.v().scale(Math.sin(a0) * r1));
        Vec3 o1 = c.add(f.u().scale(Math.cos(a1) * r1)).add(f.v().scale(Math.sin(a1) * r1));
        int in0 = fadeIn == 0 ? 0 : alpha0, in1 = fadeIn == 0 ? 0 : alpha1, out0 = fadeIn == 0 ? alpha0 : 0, out1 = fadeIn == 0 ? alpha1 : 0;
        quad(b, i0, rgb, in0); quad(b, o0, rgb, out0); quad(b, o1, rgb, out1); quad(b, i1, rgb, in1);
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

    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer b, Vec3 p, int rgb, int a) {
        b.addVertex((float) p.x, (float) p.y, (float) p.z).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a);
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
