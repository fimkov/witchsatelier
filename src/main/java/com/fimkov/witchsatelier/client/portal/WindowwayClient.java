package com.fimkov.witchsatelier.client.portal;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.network.Payloads;
import com.fimkov.witchsatelier.windowway.FarFrame;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WindowwayClient {
    private WindowwayClient() {}

    private static final Map<Long, PortalFrame> PORTALS = new HashMap<>();
    /** The far ends of windows here that look somewhere not among {@link #PORTALS}, keyed by the window they belong to. */
    private static final Map<Long, FarFrame> FAR = new HashMap<>();
    /** How each window has turned of late, and when it last turned someone away. */
    private static final Map<Long, Look> LOOKS = new HashMap<>();

    /** Ticks a marked window takes to close before it turns, and as many again to open onto the new place. */
    private static final double IRIS_TICKS = 4;
    /** A marked window is only stepped through ahead of the server once it has looked the same way this long. */
    private static final long SETTLED_TICKS = WindowwayService.SETTLE_TICKS;
    private static final double FLICKER_TICKS = 12;

    /** Where a window leads, as this client can see it; {@code here} when that is in this client's own dimension. */
    record Dest(PortalFrame frame, boolean here) {}

    private static final class Look {
        long since = Long.MIN_VALUE / 4;
        double turnedAt = -1e9;
        Dest before;
        double refusedAt = -1e9;
    }

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
    /** The window a picture is being drawn out of, while it is drawn. */
    private static PortalFrame viewPartnerFrame;
    private static boolean inPortalPass;
    private static Matrix4f lastProjection;
    private static Frustum lastFrustum;

    /** Which pair of windows a section graph was built for. */
    private record Pair(long near, long far) {}

    /**
     * The section graphs of the pictures, one for each pair of windows seen of late, the least lately used first. A
     * graph is kept with its pair rather than with a picture slot, so two windows trading places in the sort order,
     * or one leaving sight for a frame, does not throw away what was seen through them.
     */
    private static final int MAX_GRAPHS = MAX_VIEWS * 2;
    private static final LinkedHashMap<Pair, Graph> GRAPHS = new LinkedHashMap<>(8, 0.75f, true);

    private static Graph graphFor(PortalFrame near, PortalFrame far) {
        Pair key = new Pair(near.id(), far.id());
        Graph g = GRAPHS.get(key);
        if (g != null) return g;
        if (GRAPHS.size() >= MAX_GRAPHS) {
            var eldest = GRAPHS.entrySet().iterator().next();
            g = eldest.getValue();
            GRAPHS.remove(eldest.getKey());
            g.reset();
        } else {
            g = new Graph();
        }
        GRAPHS.put(key, g);
        return g;
    }

    public static void accept(List<PortalFrame> frames, List<FarFrame> far) {
        long now = now();
        Map<Long, Dest> before = new HashMap<>();
        Map<Long, PortalFrame> old = new HashMap<>(PORTALS);
        for (PortalFrame f : frames) {
            PortalFrame was = old.get(f.id());
            if (was != null && was.link() != f.link()) before.put(f.id(), dest(was));
        }
        PORTALS.clear();
        FAR.clear();
        if (frames.isEmpty()) {
            FarSide.clear();
            LOOKS.clear();
        }
        for (PortalFrame f : frames) PORTALS.put(f.id(), f);
        for (FarFrame f : far) FAR.put(f.id(), f);
        LOOKS.keySet().retainAll(PORTALS.keySet());
        for (PortalFrame f : frames) {
            PortalFrame was = old.get(f.id());
            if (was != null && was.link() != f.link()) turned(f, before.get(f.id()), now, 0);
        }
    }

    /** One window turned; those near it hear of it at once. */
    public static void link(Payloads.WindowwayLink p) {
        PortalFrame f = PORTALS.get(p.id());
        if (f == null) return;
        Dest before = dest(f);
        boolean changed = f.link() != p.link();
        PortalFrame turnedTo = f.withLink(p.link()).withLook(f.tint(), p.flags());
        PORTALS.put(turnedTo.id(), turnedTo);
        if (p.far().isPresent()) FAR.put(turnedTo.id(), p.far().get());
        else FAR.remove(turnedTo.id());
        if (changed) turned(turnedTo, before, now(), p.age());
    }

    /** A window would not take someone: it dims and flickers for a moment. */
    public static void refused(long id) {
        if (!PORTALS.containsKey(id)) return;
        LOOKS.computeIfAbsent(id, k -> new Look()).refusedAt = now();
    }

    private static void turned(PortalFrame f, Dest before, long now, int age) {
        Look l = LOOKS.computeIfAbsent(f.id(), k -> new Look());
        l.since = now - age;
        if (!f.marked()) return;
        l.before = before;
        // with nothing to close over, the iris only opens
        l.turnedAt = before == null ? now - IRIS_TICKS : now;
    }

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    /** Where a window leads: one of the windows here, else the far end the server told of. */
    static Dest dest(PortalFrame f) {
        if (f == null || f.link() < 0) return null;
        PortalFrame to = PORTALS.get(f.link());
        if (to != null) return new Dest(to, true);
        FarFrame far = FAR.get(f.id());
        if (far == null || far.frame().id() != f.link()) return null;
        Minecraft mc = Minecraft.getInstance();
        return new Dest(far.frame(), mc.level != null && far.dim().equals(mc.level.dimension()));
    }

    /** What a window shows at time {@code t}: while its iris closes, still the place it looked at before. */
    private static Dest shown(PortalFrame f, double t) {
        Look l = LOOKS.get(f.id());
        if (l != null && t - l.turnedAt < IRIS_TICKS) return l.before;
        return dest(f);
    }

    /** How far open a window's iris is, from 0 (shut) to 1. */
    static double open(PortalFrame f, double t) {
        Look l = LOOKS.get(f.id());
        if (l == null) return 1;
        double x = t - l.turnedAt;
        if (x >= 2 * IRIS_TICKS || x < 0) return 1;
        double k = x < IRIS_TICKS ? 1 - x / IRIS_TICKS : (x - IRIS_TICKS) / IRIS_TICKS;
        return k * k * (3 - 2 * k);
    }

    /** How dim a window is from turning someone away, flickering as it fades; 0 when it is not. */
    private static double dim(PortalFrame f, double t) {
        Look l = LOOKS.get(f.id());
        if (l == null) return 0;
        double x = t - l.refusedAt;
        if (x < 0 || x >= FLICKER_TICKS) return 0;
        double fade = 1 - x / FLICKER_TICKS;
        return fade * (((int) (x * 1.5) & 1) == 0 ? 0.7 : 0.35);
    }

    private static boolean settled(PortalFrame f, long now) {
        if (!f.marked()) return true;
        Look l = LOOKS.get(f.id());
        return l == null || now - l.since >= SETTLED_TICKS;
    }

    /**
     * Whether the far side can be shown as it is: it lies in this dimension, its blocks are here on this client,
     * the window would take someone in, and its iris is not shut.
     */
    private static boolean live(Minecraft mc, PortalFrame f, Dest to, double t) {
        if (to == null || !to.here() || f.has(PortalFrame.SHUT) || mc.level == null || open(f, t) <= 0.02) return false;
        BlockPos at = BlockPos.containing(to.frame().center());
        return mc.level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(at.getX()), SectionPos.blockToSectionCoord(at.getZ()));
    }

    public static net.minecraft.client.renderer.SectionOcclusionGraph mainGraphDuringPass() {
        return Graph.mainDuringPass;
    }

    /** A disc whose far side was drawn into the world, and how far its iris was open. */
    private record Opened(PortalFrame frame, double open) {}

    /** Discs whose far side was drawn into the world in the last frame. */
    private static List<Opened> openedLast = List.of();

    /**
     * Ink lying wholly inside an opened disc is on wall that is not there any more: the far side shows instead. The
     * ring itself runs outside the opening and stays.
     */
    /** Whether a point lies on the face of a window whose far side, picture or fog shows now (with some slack). */
    public static boolean openedAt(Vec3 p) {
        PortalFrame partner = inPortalPass ? viewPartnerFrame : null;
        if (partner != null && Math.abs(partner.side(p)) < 0.2 && partner.inPlane(p) < 0.25) return true;
        for (Opened o : openedLast) if (Math.abs(o.frame().side(p)) < 0.2 && o.frame().inPlane(p) < 0.25) return true;
        return false;
    }

    public static boolean hidesInk(com.fimkov.witchsatelier.glyph.surface.SurfacePlane plane, double[] uv, int n) {
        if (n <= 0) return false;
        if (inPortalPass) {
            // a picture is drawn from behind the window it looks out of: that window's ink faces away from the eye
            // and would lie over the picture, mirrored, wherever it is drawn inside the opening
            PortalFrame p = viewPartnerFrame;
            if (p == null) return false;
            double r = p.discRadius();
            for (int i = 0; i < n; i++) {
                Vec3 q = plane.toWorld(uv[i * 2], uv[i * 2 + 1], 0);
                if (Math.abs(p.side(q)) < 0.1 && p.inPlane(q) < r) return true;
            }
            return false;
        }
        if (openedLast.isEmpty()) return false;
        for (Opened o : openedLast) {
            PortalFrame f = o.frame();
            double r = f.discRadius() * o.open() * 0.97;
            boolean all = true;
            for (int i = 0; i < n && all; i++) {
                Vec3 p = plane.toWorld(uv[i * 2], uv[i * 2 + 1], 0);
                all = Math.abs(f.side(p)) < 0.1 && f.inPlane(p) < r;
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

    /**
     * Where the eye of the level pass being drawn stands. The stages that follow a block layer (translucent blocks
     * among them) are handed the main camera even while a picture is drawn from another, so whatever is drawn there
     * by the event's camera would be set off by the distance between the two.
     */
    public static Vec3 eye(net.minecraft.client.Camera given) {
        return inPortalPass ? CAMERA.getPosition() : given.getPosition();
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
        long now = now();
        for (PortalFrame f : PORTALS.values()) {
            if (f.link() < 0 || !WindowwayService.crossed(f, prev, cur)) continue;
            Dest to = dest(f);
            if (to == null && !f.marked()) continue;
            // stepping through ahead of the server only where it cannot be turned back: here, near, and settled
            boolean ahead = to != null && to.here() && !f.has(PortalFrame.SHUT) && settled(f, now) && to.frame().center().distanceTo(p.position()) <= 96
                    && (!f.marked() || roomAt(p, f, to.frame()));
            PacketDistributor.sendToServer(new Payloads.PortalCross(f.id(), f.link(), ahead));
            if (ahead) carry(p, f, to.frame());
            return;
        }
    }

    /**
     * Whether she could stand where stepping through would set her down. Where she could not, the server finds her
     * somewhere else or turns her away, and stepping through ahead of it would only show the far side for a moment
     * before she is put back. Only for marked windows: a plain one has the client step through and the server follow.
     */
    private static boolean roomAt(LocalPlayer p, PortalFrame from, PortalFrame to) {
        double h = p.getBbHeight() / 2;
        Vec3 feet = PortalFrame.mapPoint(from, to, p.position().add(0, h, 0)).subtract(0, h, 0);
        AABB box = p.getDimensions(p.getPose()).makeBoundingBox(feet);
        return p.level().noCollision(p, box) && !p.level().containsAnyLiquid(box);
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

    private record Seen(PortalFrame near, PortalFrame far) {}

    public static void onFramePre(RenderFrameEvent.Pre e) {
        Arrays.fill(VIEWING, -1);
        // lips left over from a pass that stopped short would otherwise be drawn with the next pass' camera
        LIPS.clear();
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
        double t = mc.level.getGameTime() + pt;
        List<Seen> seen = new ArrayList<>();
        for (PortalFrame f : PORTALS.values()) {
            if (f.side(eye) <= 0.01) continue;
            Dest to = shown(f, t);
            if (!live(mc, f, to, t)) continue;
            if (f.center().distanceTo(eye) >= 48 || to.frame().center().distanceTo(mc.player.position()) > reach) continue;
            if (lastFrustum != null && !lastFrustum.isVisible(new AABB(f.center(), f.center()).inflate(f.radius()))) continue;
            seen.add(new Seen(f, to.frame()));
        }
        seen.sort(Comparator.comparingDouble(s -> s.near().center().distanceTo(eye)));
        for (int k = 0; k < Math.min(MAX_VIEWS, seen.size()); k++) {
            Seen s = seen.get(k);
            if (picture(mc, e, pt, eye, rot, s.near(), s.far(), k, graphFor(s.near(), s.far()))) VIEWING[k] = s.near().id();
        }
    }

    private static boolean picture(Minecraft mc, RenderFrameEvent.Pre e, float pt, Vec3 eye, Quaternionf rot, PortalFrame best, PortalFrame partner, int k, Graph graph) {
        viewPartner = partner.id();
        viewPartnerFrame = partner;

        Vec3 vEye = PortalFrame.mapPoint(best, partner, eye);
        Quaternionf vRot = PortalFrame.rotation(best, partner).mul(rot);
        CAMERA.setup(mc.level, mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity(), true, false, pt);
        CAMERA.place(vEye, vRot);
        Matrix4f modelView = new Matrix4f().rotation(new Quaternionf(vRot).conjugate());
        if (!cullable(modelView, lastProjection, vEye)) {
            if (WARNED.size() > 64) WARNED.clear();
            if (WARNED.add(best.id() ^ (partner.id() * 31))) WitchsAtelier.LOGGER.warn("windowway view skipped, its camera cannot be culled: eye {} from {} through {} to {}",
                    vEye, eye, best, partner);
            viewPartner = -1;
            viewPartnerFrame = null;
            return false;
        }
        Matrix4f projection = oblique(lastProjection, modelView, partner, vEye);

        RenderTarget main = mc.getMainRenderTarget();
        LevelRenderer lr = mc.levelRenderer;
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
            viewPartnerFrame = null;
            LIPS.clear();
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

    private static final java.util.Set<Long> WARNED = new java.util.HashSet<>();

    /** Iris lips waiting for the block entities to be drawn, with the plain renderer. */
    private static final List<Runnable> LIPS = new ArrayList<>();

    /**
     * Whether the level renderer can cull from this camera. It steps a frustum back along its view until the cube
     * round the eye lies wholly inside, with no end to the stepping: a camera that is not a number, or one that never
     * gets there, would hang the game. The same steps are taken here, a bounded number of times.
     */
    private static boolean cullable(Matrix4f modelView, Matrix4f projection, Vec3 eye) {
        if (!Double.isFinite(eye.x) || !Double.isFinite(eye.y) || !Double.isFinite(eye.z) || !modelView.isFinite() || !projection.isFinite()) return false;
        Matrix4f m = new Matrix4f(projection).mul(modelView);
        org.joml.FrustumIntersection in = new org.joml.FrustumIntersection(m);
        Vector4f view = m.transformTranspose(new Vector4f(0, 0, 1, 0));
        double x0 = Math.floor(eye.x / 8) * 8, y0 = Math.floor(eye.y / 8) * 8, z0 = Math.floor(eye.z / 8) * 8;
        double x1 = Math.ceil(eye.x / 8) * 8, y1 = Math.ceil(eye.y / 8) * 8, z1 = Math.ceil(eye.z / 8) * 8;
        double cx = eye.x, cy = eye.y, cz = eye.z;
        for (int i = 0; i < 256; i++) {
            if (in.intersectAab((float) (x0 - cx), (float) (y0 - cy), (float) (z0 - cz), (float) (x1 - cx), (float) (y1 - cy), (float) (z1 - cz))
                    == org.joml.FrustumIntersection.INSIDE) return true;
            cx -= view.x * 4f;
            cy -= view.y * 4f;
            cz -= view.z * 4f;
        }
        return false;
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
        if (!inline && e.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            // the lips of turning irises go over the wall's stand-in blocks, which are drawn after the discs
            List<Runnable> lips = List.copyOf(LIPS);
            LIPS.clear();
            for (Runnable r : lips) r.run();
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
        Map<Long, PortalFrame> open = inline ? opened(mc, cam, viewFrustum(e, cam), t) : Map.of();
        List<Opened> drawnNow = new ArrayList<>();

        for (PortalFrame f : PORTALS.values()) {
            if (f.center().distanceTo(cam) > 128) continue;
            boolean front = f.side(cam) > 0;

            if (inPortalPass && f.id() == viewPartner) continue;
            double iris = open(f, t), dim = dim(f, t);
            if (f.marked() && iris < 0.05) {
                // all but shut: only the lip shows, closing to a point, with no sliver of a face behind it
                if (inline) lip(f, cam, dim, iris);
                else LIPS.add(() -> lip(f, cam, dim, iris));
                continue;
            }
            PortalFrame far = open.get(f.id());
            if (far != null) {
                boolean drawn = false;
                try {
                    drawn = FarSide.draw(f, far, e, iris);
                } catch (Throwable ex) {
                    WitchsAtelier.LOGGER.warn("windowway far side failed", ex);
                }
                if (drawn) {
                    drawnNow.add(new Opened(f, iris));
                    rim(f, cam, t, dim, iris);
                    continue;
                }
            }
            int k = inPortalPass || !front || viewShader == null ? -1 : pictureOf(f.id());
            Dest to = shown(f, t);
            // a marked window that looks somewhere this client is not told of shows fog, as it does for any far place
            Face face = k >= 0 ? Face.PICTURE : to == null ? (f.marked() && f.link() >= 0 ? Face.FOG : Face.IDLE)
                    : f.marked() && !live(mc, f, to, t) ? Face.FOG : Face.JOINED;
            if (face == Face.FOG) fog(f, to, cam, t, iris, dim);
            else disc(f, to, cam, k, face, t, iris, dim);
            // the wall is not there inside a picture or a fog: the ink drawn on it hides, as it does where the far
            // side is drawn into the world
            if (face == Face.PICTURE || face == Face.FOG) drawnNow.add(new Opened(f, iris));
            if (inline) lip(f, cam, dim, iris);
            else if (iris < 0.999 && f.marked()) LIPS.add(() -> lip(f, cam, dim, iris));
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

    /** The joined discs, nearest first, whose far side is drawn into the world this frame, each with what it shows. */
    private static Map<Long, PortalFrame> opened(Minecraft mc, Vec3 eye, Frustum frustum, double t) {
        if (mc.player == null) return Map.of();
        double reach = mc.options.getEffectiveRenderDistance() * 16 - 24;
        List<Seen> seen = new ArrayList<>();
        for (PortalFrame f : PORTALS.values()) {
            if (f.side(eye) <= 0.01) continue;
            Dest to = shown(f, t);
            if (!live(mc, f, to, t) || open(f, t) < 0.05) continue;
            if (f.center().distanceTo(eye) >= 64 || to.frame().center().distanceTo(mc.player.position()) > reach) continue;
            if (frustum != null && !frustum.isVisible(new AABB(f.center(), f.center()).inflate(f.radius()))) continue;
            seen.add(new Seen(f, to.frame()));
        }
        seen.sort(Comparator.comparingDouble(s -> s.near().center().distanceTo(eye)));
        Map<Long, PortalFrame> out = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(3, seen.size()); i++) out.put(seen.get(i).near().id(), seen.get(i).far());
        return out;
    }

    /** The glowing edge of an opened disc, in the colour the pair shares; drawn as light, so shader packs keep it. */
    private static void rim(PortalFrame f, Vec3 cam, double time, double dim, double open) {
        if (System.getProperty("witchsatelier.fxoff", "").contains("norim")) return;
        int seg = 96;
        double r = f.discRadius();
        Vec3 c = f.center().add(f.n().scale(f.side(cam) > 0 ? 0.006 : -0.006)).subtract(cam);
        int pair = pairColour(f);
        // a marked window wears its mark's colour more boldly, so it is told from a plain one across a hall, where
        // the ink ring round both would otherwise look alike
        boolean marked = f.marked();
        double inner = marked ? 0.74 : 0.9;
        int base = marked ? 85 : 40, swing = marked ? 85 : 70;
        int edge = lerp(pair, 0xFFFFFF, marked ? 0.3 : 0.5);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var b = buffers.getBuffer(net.minecraft.client.renderer.RenderType.lightning());
        int flash = lerp(pair, 0xFFFFFF, 0.55);
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            double sw0 = Math.sin(time * 0.08 + i * 0.35) * 0.5 + 0.5, sw1 = Math.sin(time * 0.08 + (i + 1) * 0.35) * 0.5 + 0.5;
            ring(b, c, f, a0, a1, r * inner, r * 1.0, lerp(pair, 0xFFFFFF, 0.25), (int) (base + swing * sw0), (int) (base + swing * sw1), 0);
            ring(b, c, f, a0, a1, r * 1.0, r * 1.06, edge, (int) (150 + 100 * sw0), (int) (150 + 100 * sw1), 1);
            if (marked) ring(b, c, f, a0, a1, r * 1.06, r * 1.16, pair, (int) (60 + 40 * sw0), (int) (60 + 40 * sw1), 1);
            if (dim > 0) {
                // turning someone away, the window flashes pale from its edge inward, over the far side
                band(b, c, f, a0, a1, 0, r * 0.45, flash, (int) (70 * dim), (int) (110 * dim));
                band(b, c, f, a0, a1, r * 0.45, r, flash, (int) (110 * dim), (int) (230 * dim));
            }
        }
        buffers.endBatch(net.minecraft.client.renderer.RenderType.lightning());
        lip(f, cam, dim, open);
    }

    /**
     * The bright lip of a turning window's iris: a thin band of light just inside the edge where the wall closes in,
     * and a soft glow spilling out over the wall from it. Drawn as light, so shader packs keep it.
     */
    private static void lip(PortalFrame f, Vec3 cam, double dim, double open) {
        if (open >= 0.999 || !f.marked()) return;
        double r = f.discRadius() * open;
        double strength = Math.min(1, (1 - open) * 6) * (1 - dim);
        if (strength <= 0.01) return;
        Vec3 c = f.center().add(f.n().scale(f.side(cam) > 0 ? 0.007 : -0.007)).subtract(cam);
        int pair = pairColour(f);
        int band = lerp(pair, 0xFFFFFF, 0.6), glow = lerp(pair, 0xFFFFFF, 0.35);
        int seg = 96;
        // with the plain renderer this comes before the block entities, among them the wall's stand-in blocks round
        // the opening: light that wrote depth would keep them from being drawn behind it, so it is laid on by hand
        boolean byHand = !inline();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        com.mojang.blaze3d.vertex.VertexConsumer b = byHand ? Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
                : buffers.getBuffer(net.minecraft.client.renderer.RenderType.lightning());
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            int in = (int) (235 * strength), out = (int) (150 * strength);
            ring(b, c, f, a0, a1, Math.max(0, r - 0.07), r, band, in, in, 0);
            ring(b, c, f, a0, a1, r, r + 0.22, glow, out, out, 1);
        }
        if (!byHand) {
            buffers.endBatch(net.minecraft.client.renderer.RenderType.lightning());
            return;
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(((BufferBuilder) b).buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    /** One slice of a band from radius {@code r0} to {@code r1}, its alpha running from {@code alphaIn} to {@code alphaOut}. */
    private static void band(com.mojang.blaze3d.vertex.VertexConsumer b, Vec3 c, PortalFrame f, double a0, double a1, double r0, double r1, int rgb, int alphaIn, int alphaOut) {
        Vec3 i0 = c.add(f.u().scale(Math.cos(a0) * r0)).add(f.v().scale(Math.sin(a0) * r0));
        Vec3 i1 = c.add(f.u().scale(Math.cos(a1) * r0)).add(f.v().scale(Math.sin(a1) * r0));
        Vec3 o0 = c.add(f.u().scale(Math.cos(a0) * r1)).add(f.v().scale(Math.sin(a0) * r1));
        Vec3 o1 = c.add(f.u().scale(Math.cos(a1) * r1)).add(f.v().scale(Math.sin(a1) * r1));
        quad(b, i0, rgb, alphaIn); quad(b, o0, rgb, alphaOut); quad(b, o1, rgb, alphaOut); quad(b, i1, rgb, alphaIn);
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

    /** A marked window shows its mark's colour; a plain one the colour of its pair, or grey while it waits. */
    static int pairColour(PortalFrame f) {
        if (f.marked()) return f.tint() & 0xFFFFFF;
        if (f.link() < 0) return 0x8A8898;
        long key = Math.min(f.id(), f.link()) * 0x9E3779B97F4A7C15L;
        float hue = ((key >>> 40) & 0xFFFF) / 65536f;
        return java.awt.Color.HSBtoRGB(hue, 0.55f, 1f) & 0xFFFFFF;
    }

    private static int pictureOf(long id) {
        for (int k = 0; k < MAX_VIEWS; k++) if (VIEWING[k] == id && VIEWS[k] != null) return k;
        return -1;
    }

    /**
     * What a disc shows when nothing is drawn through it: a picture of the far side; a plain dark opening with a
     * glowing rim; a slow fog in the window's colour, when the far side is too far off to be seen; or the quiet rim
     * of a window that looks nowhere.
     */
    private enum Face { PICTURE, JOINED, FOG, IDLE }

    /**
     * The fog of a window whose far side cannot be seen from here: a dark recess, evenly deep and darkest at its very
     * edge, where the mist thins, with slow banks of mist in the colour of the place it looks at drifting across it. The mist is laid on as
     * light rather than painted, so it glows the same with a shader pack as without one (a pack lights plain coloured
     * shapes as surfaces, by the sun, which turned painted mist into a pastel ball by day and black by night). A
     * window that turns someone away flashes pale through its fog, from the edge inward.
     */
    private static void fog(PortalFrame f, Dest to, Vec3 cam, double time, double open, double dim) {
        double r = f.discRadius() * open;
        if (r < 0.01) return;
        double side = f.side(cam) > 0 ? 1 : -1;
        Vec3 c = f.center().add(f.n().scale(0.004 * side)).subtract(cam);
        // the colour of the place the window looks at, so a retuned window shows where it looks now
        int tone = to != null && to.frame().tint() >= 0 ? to.frame().tint() & 0xFFFFFF : pairColour(f);
        boolean shut = f.has(PortalFrame.SHUT);
        boolean near = c.length() < 24;
        int seg = near ? 64 : 32, rings = near ? 14 : 7;

        // the recess: opaque, so nothing of the wall shows through, and of one colour, as a shader pack shades plain
        // shapes flat; it darkens toward the edge only where the mist thins out over it
        int deep = lerp(0x030207, tone, 0.06);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            vtx(b, c, deep, 255); vtx(b, at(c, f, a0, r), deep, 255); vtx(b, at(c, f, a1, r), deep, 255);
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.enableCull();

        // the mist, worked out once for each corner of the mesh (neighbouring cells share them), across the disc
        // rather than round it so the middle has no seam or spokes
        int lo = brightest(tone, 120), hi = lerp(brightest(tone, 200), 0xFFFFFF, 0.2);
        if (shut) {
            lo = lerp(lo, grey(lo), 0.6);
            hi = lerp(hi, grey(hi), 0.6);
        }
        int flashTone = lerp(brightest(tone, 230), 0xFFFFFF, 0.6);
        int[] col = new int[(rings + 1) * seg], alpha = new int[(rings + 1) * seg];
        double s = time * 0.03;
        for (int j = 0; j <= rings; j++) {
            double q = (double) j / rings;
            double edge = 1 - 0.8 * smooth(0.7, 1.0, q);
            for (int i = 0; i < seg; i++) {
                double a = i * Math.PI * 2 / seg;
                double k = smooth(0.25, 0.85, mist(Math.cos(a) * q, Math.sin(a) * q, s));
                double al = (0.16 + 0.62 * k) * edge * (shut ? 0.3 : 1);
                int cc = lerp(lo, hi, k);
                if (dim > 0) {
                    double fl = dim * (0.35 + 0.65 * q);
                    cc = lerp(cc, flashTone, Math.min(1, fl * 1.2));
                    al = Math.min(1, al + 0.85 * fl);
                }
                col[j * seg + i] = cc;
                alpha[j * seg + i] = (int) (255 * al);
            }
        }
        Vec3 cm = f.center().add(f.n().scale(0.005 * side)).subtract(cam);
        boolean byHand = !inline();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        com.mojang.blaze3d.vertex.VertexConsumer m = byHand ? Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
                : buffers.getBuffer(net.minecraft.client.renderer.RenderType.lightning());
        for (int j = 0; j < rings; j++) {
            double r0 = r * j / rings, r1 = r * (j + 1) / rings;
            for (int i = 0; i < seg; i++) {
                int i1 = (i + 1) % seg;
                double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
                int p00 = j * seg + i, p01 = j * seg + i1, p10 = (j + 1) * seg + i, p11 = (j + 1) * seg + i1;
                quad(m, at(cm, f, a0, r0), col[p00], alpha[p00]);
                quad(m, at(cm, f, a0, r1), col[p10], alpha[p10]);
                quad(m, at(cm, f, a1, r1), col[p11], alpha[p11]);
                quad(m, at(cm, f, a1, r0), col[p01], alpha[p01]);
            }
        }
        if (!byHand) {
            buffers.endBatch(net.minecraft.client.renderer.RenderType.lightning());
            return;
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(((BufferBuilder) m).buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    /** How thick the mist is at a point of the disc ({@code x}, {@code y} within the unit circle), from about 0 to 1. */
    private static double mist(double x, double y, double s) {
        double wx = x + 0.25 * Math.sin(2.6 * y + 0.6 * s + 0.4), wy = y + 0.25 * Math.sin(2.3 * x - 0.5 * s + 2.1);
        double banks = Math.sin(3.4 * wx + 1.5 * wy + s) + Math.sin(-2.1 * wx + 3.2 * wy - 0.7 * s + 1.7);
        double wisps = Math.sin(7.1 * wx - 3.4 * wy - 0.4 * s + 0.8) + Math.sin(2.9 * wx + 6.6 * wy + 0.3 * s + 2.6);
        return 0.5 + 0.2 * banks + 0.13 * wisps;
    }

    private static Vec3 at(Vec3 c, PortalFrame f, double a, double r) {
        return c.add(f.u().scale(Math.cos(a) * r)).add(f.v().scale(Math.sin(a) * r));
    }

    /** {@code rgb} scaled so its brightest channel is {@code max}. */
    private static int brightest(int rgb, int max) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int m = Math.max(r, Math.max(g, b));
        if (m == 0) return (max << 16) | (max << 8) | max;
        double k = max / (double) m;
        return ((int) Math.min(255, r * k) << 16) | ((int) Math.min(255, g * k) << 8) | (int) Math.min(255, b * k);
    }

    private static int grey(int rgb) {
        int y = (int) (0.3 * ((rgb >> 16) & 255) + 0.59 * ((rgb >> 8) & 255) + 0.11 * (rgb & 255));
        return (y << 16) | (y << 8) | y;
    }

    private static final int DARK = 0x07050F;

    /**
     * Draws a disc whose iris is {@code open} of the way open: past the iris the wall itself shows, with a bright lip
     * at the iris' edge. {@code dim} darkens the whole while the window turns someone away.
     */
    private static void disc(PortalFrame f, Dest to, Vec3 cam, int k, Face face, double time, double open, double dim) {
        boolean picture = face == Face.PICTURE;
        int seg = 64, rings = 1;
        double full = f.discRadius();
        double r = picture ? full : full * open;
        if (r < 0.01) return;
        Vec3 c = f.center().add(f.n().scale(f.side(cam) > 0 ? 0.004 : -0.004)).subtract(cam);
        int pair = pairColour(f);
        double lit = 1 - dim;
        boolean shut = f.has(PortalFrame.SHUT);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            if (picture) {
                Vec3 p0 = c.add(f.u().scale(Math.cos(a0) * r)).add(f.v().scale(Math.sin(a0) * r));
                Vec3 p1 = c.add(f.u().scale(Math.cos(a1) * r)).add(f.v().scale(Math.sin(a1) * r));
                vtx(b, c, pair, 0);
                vtx(b, p0, pair, 120);
                vtx(b, p1, pair, 120);
                continue;
            }
            for (int j = 0; j < rings; j++) {
                double r0 = r * j / rings, r1 = r * (j + 1) / rings;
                double q0 = (double) j / rings, q1 = (double) (j + 1) / rings;
                Vec3 i0 = c.add(f.u().scale(Math.cos(a0) * r0)).add(f.v().scale(Math.sin(a0) * r0));
                Vec3 i1 = c.add(f.u().scale(Math.cos(a1) * r0)).add(f.v().scale(Math.sin(a1) * r0));
                Vec3 o0 = c.add(f.u().scale(Math.cos(a0) * r1)).add(f.v().scale(Math.sin(a0) * r1));
                Vec3 o1 = c.add(f.u().scale(Math.cos(a1) * r1)).add(f.v().scale(Math.sin(a1) * r1));
                int ci0 = shade(face, f, pair, shut, i, a0, q0, time, lit), ci1 = shade(face, f, pair, shut, i, a1, q0, time, lit);
                int co0 = shade(face, f, pair, shut, i, a0, q1, time, lit), co1 = shade(face, f, pair, shut, i, a1, q1, time, lit);
                vtx(b, o0, co0, 255); vtx(b, o1, co1, 255); vtx(b, i1, ci1, 255);
                if (j > 0) { vtx(b, o0, co0, 255); vtx(b, i1, ci1, 255); vtx(b, i0, ci0, 255); }
            }
            if (open < 0.999) {
                // the lip of the iris
                int lip = scale(lerp(pair, 0xFFFFFF, 0.6), lit);
                double l0 = Math.max(0, r - Math.min(0.08, r * 0.5));
                Vec3 cl = c.add(f.n().scale(f.side(cam) > 0 ? 0.001 : -0.001));
                Vec3 i0 = cl.add(f.u().scale(Math.cos(a0) * l0)).add(f.v().scale(Math.sin(a0) * l0));
                Vec3 i1 = cl.add(f.u().scale(Math.cos(a1) * l0)).add(f.v().scale(Math.sin(a1) * l0));
                Vec3 o0 = cl.add(f.u().scale(Math.cos(a0) * r)).add(f.v().scale(Math.sin(a0) * r));
                Vec3 o1 = cl.add(f.u().scale(Math.cos(a1) * r)).add(f.v().scale(Math.sin(a1) * r));
                vtx(b, o0, lip, 255); vtx(b, o1, lip, 255); vtx(b, i1, lip, 255);
                vtx(b, o0, lip, 255); vtx(b, i1, lip, 255); vtx(b, i0, lip, 255);
            }
        }
        if (picture) {
            RenderSystem.setShader(() -> viewShader);
            RenderSystem.setShaderTexture(0, VIEWS[k].getColorTextureId());
            viewShader.safeGetUniform("Iris").set((float) open);
            viewShader.safeGetUniform("Dim").set((float) dim);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.enableCull();
    }

    /** The colour of a disc at angle {@code a} and {@code q} of the way out from its centre. */
    private static int shade(Face face, PortalFrame f, int pair, boolean shut, int i, double a, double q, double time, double lit) {
        int rim;
        switch (face) {
            case JOINED -> {
                double sw = Math.sin(time * 0.05 + i * 0.4) * 0.5 + 0.5;
                rim = lerp(lerp(DARK, pair, 0.35), pair, sw);
                if (shut) rim = lerp(DARK, rim, 0.45);
            }
            default -> {
                double sw = Math.sin(time * 0.025 + i * 0.4) * 0.5 + 0.5;
                rim = f.marked() ? lerp(0x18161F, lerp(0x18161F, pair, 0.6), sw) : lerp(0x18161F, 0x4A4858, sw);
            }
        }
        return scale(lerp(DARK, rim, q), lit);
    }

    private static double smooth(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    private static int scale(int rgb, double k) {
        return lerp(0, rgb, Math.max(0, Math.min(1, k)));
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
