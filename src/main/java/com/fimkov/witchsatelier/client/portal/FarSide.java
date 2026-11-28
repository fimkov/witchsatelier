package com.fimkov.witchsatelier.client.portal;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.windowway.PortalFrame;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The far side of a joined Windowway, drawn into the world itself instead of into a picture.
 *
 * <p>The blocks around the partner are meshed here, section by section, straight into the space behind the near
 * disc. Each frame the disc punches a hole in the depth buffer (only where nothing stands in front of it), and the
 * meshes are sent through the ordinary block render types, cut on the CPU to the cone from the eye through the rim.
 * Nothing of the level renderer is swapped or run twice, so Sodium keeps its own section lists, and under an Iris
 * shader pack the far side is lit, fogged and shaded by the pack like everything else in the frame.</p>
 */
final class FarSide {
    private FarSide() {}

    static final int RADIUS = 30;
    private static final long BUILD_NANOS = 3_000_000L;
    private static final int REFRESH_TICKS = 160;
    private static final int RIM_SEGMENTS = 32;
    private static final int STRIDE = 10;
    /** At most this many faces a frame; the sections nearest the far disc come first, so a crowded view thins out at its far end. */
    private static final int MAX_QUADS = 24_000;

    private static final RenderType[] LAYERS = {RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout(), RenderType.translucent(), RenderType.tripwire()};

    private static final Map<Long, View> VIEWS = new HashMap<>();
    private static int depthCopy = -1, depthW, depthH;
    private static long frame;

    /** A block changed on the client; any far side that shows it re-meshes the sections around it. */
    static void blockChanged(BlockPos pos) {
        if (VIEWS.isEmpty()) return;
        for (View v : VIEWS.values()) {
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                Section s = v.sections.get(SectionPos.asLong(SectionPos.blockToSectionCoord(pos.getX() + dx),
                        SectionPos.blockToSectionCoord(pos.getY() + dy), SectionPos.blockToSectionCoord(pos.getZ() + dz)));
                if (s != null) s.dirty = true;
            }
        }
    }

    static void sectionChanged(int sx, int sy, int sz) {
        if (VIEWS.isEmpty()) return;
        for (View v : VIEWS.values()) {
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                Section s = v.sections.get(SectionPos.asLong(sx + dx, sy + dy, sz + dz));
                if (s != null) s.dirty = true;
            }
        }
    }

    static void clear() {
        VIEWS.clear();
        LATER.clear();
    }

    /** Iris draws its shadow map with the level's own stage events; the far side stays out of it. */
    static boolean shadowPass() {
        return IrisHook.shadowPass();
    }

    /** Whether a shader pack is drawing the level right now. */
    static boolean packActive() {
        return IrisHook.packActive();
    }

    // ---- per frame --------------------------------------------------------------------------------------------

    /**
     * Draws the far side of {@code near} (whose partner is {@code far}) into the current frame, through an opening
     * {@code open} of the disc wide: while a window turns, its iris narrows the hole and the wall closes in round it.
     * Returns false when nothing could be drawn yet, so the caller falls back to the plain disc.
     */
    static boolean draw(PortalFrame near, PortalFrame far, RenderLevelStageEvent e, double open) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return false;
        View view = VIEWS.get(near.id());
        if (view == null || !view.near.samePlace(near) || !view.far.samePlace(far)) {
            view = new View(near, far);
            VIEWS.put(near.id(), view);
        }
        view.usedAt = frame;
        view.build(level, mc.getBlockRenderer());
        if (!view.anyBuilt) return false;

        long started = System.nanoTime();
        Vec3 cam = e.getCamera().getPosition();
        Vec3 c = near.center().subtract(cam);
        double r = near.discRadius() * Math.max(0.02, Math.min(1, open));
        Cone cone = new Cone(near, c, r);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // a shader pack holds block entities and creatures back and draws them in one go later; whatever stands in
        // front of the opening (the wall's own stand-in blocks among it) has to be in the depth buffer first
        buffers.endBatch();
        punch(near, cam, mc, r);

        float ox = (float) c.x, oy = (float) c.y, oz = (float) c.z;
        int[] budget = {MAX_QUADS};
        for (int l : OPAQUE) layer(view, cone, l, ox, oy, oz, buffers, budget);
        LATER.add(new Later(view, cone, ox, oy, oz, budget[0]));
        things(view, cone, cam, e, mc, buffers);
        if (TIMING) {
            spent += System.nanoTime() - started;
            if (++timed % 120 == 0) {
                WitchsAtelier.LOGGER.info("[visualcheck] far side: {} ms a frame over {} sections", String.format("%.2f", spent / 120e6), view.sections.size());
                spent = 0;
            }
        }
        return true;
    }

    private static final boolean TIMING = System.getProperty("witchsatelier.visualcheck") != null;
    private static long spent, timed;

    private static final int[] OPAQUE = {0, 1, 2}, SEE_THROUGH = {3, 4};

    /** What waits for the translucent part of the frame: water and glass panes are drawn after the world's own. */
    private record Later(View view, Cone cone, float ox, float oy, float oz, int budget) {}

    private static final List<Later> LATER = new ArrayList<>();

    /**
     * The see-through layers of every far side drawn this frame. A shader pack draws translucent things in a pass
     * of their own after it has lit the solid world, so they have to come at the same point of the frame.
     */
    static void drawTranslucent() {
        if (LATER.isEmpty()) return;
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        for (Later l : LATER) {
            int[] budget = {l.budget()};
            for (int layer : SEE_THROUGH) layer(l.view(), l.cone(), layer, l.ox(), l.oy(), l.oz(), buffers, budget);
        }
        LATER.clear();
    }

    private static void layer(View view, Cone cone, int l, float ox, float oy, float oz, MultiBufferSource.BufferSource buffers, int[] budget) {
        // under a shader pack plain cutout blocks come out blank outside of terrain; the mipped kind draws the same
        RenderType type = l == 2 && IrisHook.packActive() ? LAYERS[1] : LAYERS[l];
        float[] poly = new float[STRIDE * 64], scratch = new float[STRIDE * 64];
        VertexConsumer out = null;
        for (Section s : view.byDistance) {
            if (s.data == null || s.counts[l] == 0) continue;
            int box = cone.classify(s.minX + ox, s.minY + oy, s.minZ + oz, s.maxX + ox, s.maxY + oy, s.maxZ + oz);
            if (box < 0) continue;
            float[] d = s.data[l];
            for (int q = 0; q < s.counts[l] && budget[0] > 0; q += 4) {
                int base = q * STRIDE;
                if (facesAway(d, base, ox, oy, oz)) continue;
                int at = box > 0 ? 1 : cone.classifyQuad(d, base, ox, oy, oz);
                if (at < 0) continue;
                budget[0]--;
                if (out == null) out = buffers.getBuffer(type);
                if (at > 0) {
                    for (int k = 0; k < 4; k++) emit(out, d, base + k * STRIDE, ox, oy, oz);
                    continue;
                }
                System.arraycopy(d, base, poly, 0, 4 * STRIDE);
                int n = cone.clip(poly, 4, scratch, ox, oy, oz);
                if (n < 3) continue;
                for (int i = 1; i + 1 < n; ) {
                    if (i + 2 < n) {
                        emit(out, poly, 0, ox, oy, oz); emit(out, poly, i * STRIDE, ox, oy, oz);
                        emit(out, poly, (i + 1) * STRIDE, ox, oy, oz); emit(out, poly, (i + 2) * STRIDE, ox, oy, oz);
                        i += 2;
                    } else {
                        emit(out, poly, 0, ox, oy, oz); emit(out, poly, i * STRIDE, ox, oy, oz);
                        emit(out, poly, (i + 1) * STRIDE, ox, oy, oz); emit(out, poly, (i + 1) * STRIDE, ox, oy, oz);
                        i += 1;
                    }
                }
            }
        }
        if (out != null) buffers.endBatch(type);
    }

    /** Back faces are culled by every block render type anyway; dropping them here halves the work of the cut. */
    private static boolean facesAway(float[] d, int b, float ox, float oy, float oz) {
        float x0 = d[b] + ox, y0 = d[b + 1] + oy, z0 = d[b + 2] + oz;
        float ax = d[b + STRIDE] - d[b], ay = d[b + STRIDE + 1] - d[b + 1], az = d[b + STRIDE + 2] - d[b + 2];
        float bx = d[b + 2 * STRIDE] - d[b], by = d[b + 2 * STRIDE + 1] - d[b + 1], bz = d[b + 2 * STRIDE + 2] - d[b + 2];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        return nx * x0 + ny * y0 + nz * z0 > 1e-4f;
    }

    private static void emit(VertexConsumer out, float[] d, int i, float ox, float oy, float oz) {
        int color = Float.floatToRawIntBits(d[i + 3]);
        int light = Float.floatToRawIntBits(d[i + 6]);
        out.addVertex(d[i] + ox, d[i + 1] + oy, d[i + 2] + oz)
                .setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255)
                .setUv(d[i + 4], d[i + 5])
                .setLight(light)
                .setNormal(d[i + 7], d[i + 8], d[i + 9]);
    }

    /** Creatures and block entities on the far side that can be seen through the opening. */
    private static void things(View view, Cone cone, Vec3 cam, RenderLevelStageEvent e, Minecraft mc, MultiBufferSource.BufferSource buffers) {
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        PortalFrame near = view.near, far = view.far;
        Quaternionf rot = PortalFrame.rotation(far, near);
        PoseStack pose = new PoseStack();
        var entities = mc.getEntityRenderDispatcher();
        AABB region = new AABB(far.center(), far.center()).inflate(RADIUS);
        boolean any = false;
        for (Entity en : mc.level.getEntities((Entity) null, region, x -> !x.isRemoved() && far.side(x.position()) > 0)) {
            Vec3 at = en.getPosition(pt);
            AABB box = en.getBoundingBox().move(at.subtract(en.position()));
            if (!cone.touches(mapBox(far, near, box, cam))) continue;
            Vec3 there = PortalFrame.mapPoint(far, near, at).subtract(cam);
            pose.pushPose();
            pose.translate(there.x, there.y, there.z);
            pose.mulPose(rot);
            int light = entities.getPackedLightCoords(en, pt);
            try {
                entities.render(en, 0, 0, 0, en.getYRot(), pt, pose, buffers, light);
                any = true;
            } catch (Throwable t) {
                WitchsAtelier.LOGGER.debug("far side entity {} failed", en, t);
            }
            pose.popPose();
        }
        var blockEntities = mc.getBlockEntityRenderDispatcher();
        for (Section s : view.sections.values()) {
            if (s.blockEntities == null) continue;
            for (BlockPos p : s.blockEntities) {
                BlockEntity be = mc.level.getBlockEntity(p);
                if (be == null || blockEntities.getRenderer(be) == null) continue;
                if (!cone.touches(mapBox(far, near, new AABB(p), cam))) continue;
                Vec3 there = PortalFrame.mapPoint(far, near, Vec3.atLowerCornerOf(p)).subtract(cam);
                pose.pushPose();
                pose.translate(there.x, there.y, there.z);
                pose.mulPose(rot);
                try {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    net.minecraft.client.renderer.blockentity.BlockEntityRenderer<BlockEntity> renderer =
                            (net.minecraft.client.renderer.blockentity.BlockEntityRenderer) blockEntities.getRenderer(be);
                    renderer.render(be, pt, pose, buffers, net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level, p),
                            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                    any = true;
                } catch (Throwable t) {
                    WitchsAtelier.LOGGER.debug("far side block entity at {} failed", p, t);
                }
                pose.popPose();
            }
        }
        if (any) buffers.endBatch();
    }

    /** The eight corners of a far-side box, mapped through the pair and made camera-relative. */
    private static float[] mapBox(PortalFrame far, PortalFrame near, AABB b, Vec3 cam) {
        float[] out = new float[24];
        int k = 0;
        for (int i = 0; i < 8; i++) {
            Vec3 p = new Vec3((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ);
            Vec3 m = PortalFrame.mapPoint(far, near, p).subtract(cam);
            out[k++] = (float) m.x; out[k++] = (float) m.y; out[k++] = (float) m.z;
        }
        return out;
    }

    /**
     * Clears the depth inside the disc to the far plane, but only where the disc itself is the nearest thing:
     * whatever stands in front of the opening keeps its depth and is not painted over. The colour left behind
     * is the sky, as seen from here.
     *
     * <p>This is drawn with a small GL program of its own rather than a {@link ShaderInstance}: a shader pack skips
     * every shader it does not know, and it draws the level into its own targets, so the pass binds the target the
     * pack draws blocks into, draws, and hands the main target back the way the pack itself does.</p>
     */
    private static void punch(PortalFrame f, Vec3 cam, Minecraft mc, double r) {
        if (!PunchProgram.ready()) return;
        boolean packTarget = IrisHook.bindBlockTarget();

        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        int w = vp[2], h = vp[3];
        if (depthCopy < 0) depthCopy = GlStateManager._genTexture();
        GlStateManager._activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(depthCopy);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        if (read != draw) GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, draw);
        if (w != depthW || h != depthH) {
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
            GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F, vp[0], vp[1], w, h, 0);
            depthW = w;
            depthH = h;
        } else {
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, vp[0], vp[1], w, h);
        }
        if (read != draw) GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);

        Vec3 sky = mc.level.getSkyColor(cam, 1f);
        float[] fog = RenderSystem.getShaderFogColor();
        Vec3 c = f.center().add(f.n().scale(f.side(cam) > 0 ? 0.004 : -0.004)).subtract(cam);
        int seg = 48;
        float[] verts = new float[seg * 3 * 7];
        int k = 0;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            k = skyVertex(verts, k, c, sky, fog);
            k = skyVertex(verts, k, c.add(f.u().scale(Math.cos(a0) * r)).add(f.v().scale(Math.sin(a0) * r)), sky, fog);
            k = skyVertex(verts, k, c.add(f.u().scale(Math.cos(a1) * r)).add(f.v().scale(Math.sin(a1) * r)), sky, fog);
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        PunchProgram.draw(verts, seg * 3, RenderSystem.getProjectionMatrix(), RenderSystem.getModelViewMatrix(), vp, depthCopy);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableCull();
        if (packTarget) mc.getMainRenderTarget().bindWrite(false);
    }

    private static int skyVertex(float[] out, int k, Vec3 p, Vec3 sky, float[] fog) {
        double up = p.normalize().y;
        float t = (float) Math.max(0, Math.min(1, up * 2.2 + 0.15));
        out[k++] = (float) p.x; out[k++] = (float) p.y; out[k++] = (float) p.z;
        out[k++] = (float) (fog[0] + (sky.x - fog[0]) * t);
        out[k++] = (float) (fog[1] + (sky.y - fog[1]) * t);
        out[k++] = (float) (fog[2] + (sky.z - fog[2]) * t);
        out[k++] = 1f;
        return k;
    }

    /** The punch pass as a bare GL program, so nothing on the way can skip or replace it. */
    private static final class PunchProgram {
        private static int program = -2, vao, vbo, uProj, uView, uScreen, uOrigin, uDepth;

        private static final String VERTEX = """
                #version 150
                in vec3 Position;
                in vec4 Color;
                uniform mat4 ProjMat;
                uniform mat4 ModelViewMat;
                out vec4 tint;
                void main() {
                    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                    tint = Color;
                }
                """;

        // Wherever the disc is the nearest thing on screen, the depth goes back to the far plane and the colour to
        // the sky, so the far side can be drawn into the hole. Anything standing in front keeps its place.
        private static final String FRAGMENT = """
                #version 150
                uniform sampler2D Depth;
                uniform vec2 ScreenSize;
                uniform vec2 ScreenOrigin;
                in vec4 tint;
                out vec4 fragColor;
                void main() {
                    float stored = texture(Depth, (gl_FragCoord.xy - ScreenOrigin) / ScreenSize).r;
                    if (stored < gl_FragCoord.z - 0.00001) discard;
                    fragColor = vec4(tint.rgb, 1.0);
                    gl_FragDepth = 1.0;
                }
                """;

        static boolean ready() {
            if (program == -2) {
                program = -1;
                try {
                    int vs = compile(org.lwjgl.opengl.GL20.GL_VERTEX_SHADER, VERTEX), fs = compile(org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER, FRAGMENT);
                    int p = org.lwjgl.opengl.GL20.glCreateProgram();
                    org.lwjgl.opengl.GL20.glAttachShader(p, vs);
                    org.lwjgl.opengl.GL20.glAttachShader(p, fs);
                    org.lwjgl.opengl.GL20.glBindAttribLocation(p, 0, "Position");
                    org.lwjgl.opengl.GL20.glBindAttribLocation(p, 1, "Color");
                    GL30.glBindFragDataLocation(p, 0, "fragColor");
                    org.lwjgl.opengl.GL20.glLinkProgram(p);
                    if (org.lwjgl.opengl.GL20.glGetProgrami(p, org.lwjgl.opengl.GL20.GL_LINK_STATUS) == 0)
                        throw new IllegalStateException(org.lwjgl.opengl.GL20.glGetProgramInfoLog(p));
                    org.lwjgl.opengl.GL20.glDeleteShader(vs);
                    org.lwjgl.opengl.GL20.glDeleteShader(fs);
                    uProj = org.lwjgl.opengl.GL20.glGetUniformLocation(p, "ProjMat");
                    uView = org.lwjgl.opengl.GL20.glGetUniformLocation(p, "ModelViewMat");
                    uScreen = org.lwjgl.opengl.GL20.glGetUniformLocation(p, "ScreenSize");
                    uOrigin = org.lwjgl.opengl.GL20.glGetUniformLocation(p, "ScreenOrigin");
                    uDepth = org.lwjgl.opengl.GL20.glGetUniformLocation(p, "Depth");
                    vao = GL30.glGenVertexArrays();
                    vbo = org.lwjgl.opengl.GL15.glGenBuffers();
                    program = p;
                } catch (Throwable t) {
                    WitchsAtelier.LOGGER.warn("windowway punch program failed; far sides stay closed", t);
                }
            }
            return program > 0;
        }

        private static int compile(int type, String src) {
            int s = org.lwjgl.opengl.GL20.glCreateShader(type);
            org.lwjgl.opengl.GL20.glShaderSource(s, src);
            org.lwjgl.opengl.GL20.glCompileShader(s);
            if (org.lwjgl.opengl.GL20.glGetShaderi(s, org.lwjgl.opengl.GL20.GL_COMPILE_STATUS) == 0)
                throw new IllegalStateException(org.lwjgl.opengl.GL20.glGetShaderInfoLog(s));
            return s;
        }

        static void draw(float[] verts, int count, Matrix4f proj, Matrix4f view, int[] vp, int depthTexture) {
            BufferUploader.invalidate();
            GlStateManager._glUseProgram(program);
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                org.lwjgl.opengl.GL20.glUniformMatrix4fv(uProj, false, proj.get(stack.mallocFloat(16)));
                org.lwjgl.opengl.GL20.glUniformMatrix4fv(uView, false, view.get(stack.mallocFloat(16)));
            }
            org.lwjgl.opengl.GL20.glUniform2f(uScreen, vp[2], vp[3]);
            org.lwjgl.opengl.GL20.glUniform2f(uOrigin, vp[0], vp[1]);
            org.lwjgl.opengl.GL20.glUniform1i(uDepth, 0);
            GlStateManager._activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(depthTexture);
            GL30.glBindVertexArray(vao);
            GlStateManager._glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, vbo);
            org.lwjgl.opengl.GL15.glBufferData(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, verts, org.lwjgl.opengl.GL15.GL_STREAM_DRAW);
            org.lwjgl.opengl.GL20.glEnableVertexAttribArray(0);
            org.lwjgl.opengl.GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 28, 0);
            org.lwjgl.opengl.GL20.glEnableVertexAttribArray(1);
            org.lwjgl.opengl.GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, 28, 12);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, count);
            GL30.glBindVertexArray(0);
            GlStateManager._glUseProgram(0);
            BufferUploader.invalidate();
        }
    }

    /** Forget views that have not been drawn for a while. */
    static void endFrame() {
        frame++;
        LATER.clear();
        if (frame % 120 != 0) return;
        Iterator<View> it = VIEWS.values().iterator();
        while (it.hasNext()) if (frame - it.next().usedAt > 600) it.remove();
    }

    // ---- the cone through the opening --------------------------------------------------------------------------

    /** Planes through the eye and each edge of the disc, plus the disc's own plane; all camera-relative. */
    private static final class Cone {
        final float[] nx, ny, nz, d;
        final int count;

        Cone(PortalFrame f, Vec3 c, double r) {
            int seg = RIM_SEGMENTS;
            count = seg + 1;
            nx = new float[count]; ny = new float[count]; nz = new float[count]; d = new float[count];
            Vec3[] p = new Vec3[seg];
            for (int i = 0; i < seg; i++) {
                double a = (i + 0.5) * Math.PI * 2 / seg;
                // the polygon circumscribes the disc so the cut never shows inside the rim
                double rr = r / Math.cos(Math.PI / seg);
                p[i] = c.add(f.u().scale(Math.cos(a) * rr)).add(f.v().scale(Math.sin(a) * rr));
            }
            for (int i = 0; i < seg; i++) {
                Vec3 n = p[i].cross(p[(i + 1) % seg]);
                if (n.dot(c) < 0) n = n.scale(-1);
                n = n.normalize();
                nx[i] = (float) n.x; ny[i] = (float) n.y; nz[i] = (float) n.z; d[i] = 0;
            }
            // keep what lies behind the disc, as seen from the eye
            Vec3 back = f.side(f.center().subtract(c)) > 0 ? f.n().scale(-1) : f.n();
            nx[seg] = (float) back.x; ny[seg] = (float) back.y; nz[seg] = (float) back.z;
            d[seg] = (float) -back.dot(c);
        }

        private float dist(int i, float x, float y, float z) {
            return nx[i] * x + ny[i] * y + nz[i] * z + d[i];
        }

        /** One face, by the sphere around it: -1 entirely outside, 1 entirely inside, 0 to be cut. */
        int classifyQuad(float[] q, int b, float ox, float oy, float oz) {
            float cx = (q[b] + q[b + STRIDE] + q[b + 2 * STRIDE] + q[b + 3 * STRIDE]) * 0.25f + ox;
            float cy = (q[b + 1] + q[b + STRIDE + 1] + q[b + 2 * STRIDE + 1] + q[b + 3 * STRIDE + 1]) * 0.25f + oy;
            float cz = (q[b + 2] + q[b + STRIDE + 2] + q[b + 2 * STRIDE + 2] + q[b + 3 * STRIDE + 2]) * 0.25f + oz;
            float r2 = 0;
            for (int k = 0; k < 4; k++) {
                float dx = q[b + k * STRIDE] + ox - cx, dy = q[b + k * STRIDE + 1] + oy - cy, dz = q[b + k * STRIDE + 2] + oz - cz;
                r2 = Math.max(r2, dx * dx + dy * dy + dz * dz);
            }
            float r = (float) Math.sqrt(r2);
            int result = 1;
            for (int i = 0; i < count; i++) {
                float dd = dist(i, cx, cy, cz);
                if (dd < -r) return -1;
                if (dd < r) result = 0;
            }
            return result;
        }

        /** -1 entirely outside, 1 entirely inside, 0 crossing. */
        int classify(float x0, float y0, float z0, float x1, float y1, float z1) {
            boolean inside = true;
            for (int i = 0; i < count; i++) {
                int in = 0;
                for (int k = 0; k < 8; k++) {
                    float x = (k & 1) == 0 ? x0 : x1, y = (k & 2) == 0 ? y0 : y1, z = (k & 4) == 0 ? z0 : z1;
                    if (dist(i, x, y, z) >= 0) in++;
                }
                if (in == 0) return -1;
                if (in < 8) inside = false;
            }
            return inside ? 1 : 0;
        }

        /**
         * Whether any part of the box (given by its corners) can show through the opening. What sticks out past
         * the rim lies behind the wall the disc is drawn on, so the wall hides it.
         */
        boolean touches(float[] corners) {
            for (int i = 0; i < count; i++) {
                boolean any = false;
                for (int k = 0; k < corners.length && !any; k += 3) any = dist(i, corners[k], corners[k + 1], corners[k + 2]) >= 0;
                if (!any) return false;
            }
            return true;
        }

        /** Sutherland-Hodgman in place; vertex positions in {@code poly} are relative to the disc centre. */
        int clip(float[] poly, int n, float[] tmp, float ox, float oy, float oz) {
            for (int i = 0; i < count && n >= 3; i++) {
                int m = 0;
                for (int a = 0; a < n; a++) {
                    int b = (a + 1) % n;
                    int ia = a * STRIDE, ib = b * STRIDE;
                    float da = dist(i, poly[ia] + ox, poly[ia + 1] + oy, poly[ia + 2] + oz);
                    float db = dist(i, poly[ib] + ox, poly[ib + 1] + oy, poly[ib + 2] + oz);
                    if (da >= 0) {
                        if (m >= 60) return 0;
                        System.arraycopy(poly, ia, tmp, m++ * STRIDE, STRIDE);
                    }
                    if ((da >= 0) != (db >= 0)) {
                        if (m >= 60) return 0;
                        lerp(poly, ia, ib, da / (da - db), tmp, m++ * STRIDE);
                    }
                }
                System.arraycopy(tmp, 0, poly, 0, m * STRIDE);
                n = m;
            }
            return n;
        }

        private static void lerp(float[] s, int a, int b, float t, float[] out, int o) {
            for (int k = 0; k < 3; k++) out[o + k] = s[a + k] + (s[b + k] - s[a + k]) * t;
            int ca = Float.floatToRawIntBits(s[a + 3]), cb = Float.floatToRawIntBits(s[b + 3]);
            int col = 0;
            for (int sh = 0; sh < 32; sh += 8) {
                int x = (ca >>> sh) & 255, y = (cb >>> sh) & 255;
                col |= (Math.round(x + (y - x) * t) & 255) << sh;
            }
            out[o + 3] = Float.intBitsToFloat(col);
            out[o + 4] = s[a + 4] + (s[b + 4] - s[a + 4]) * t;
            out[o + 5] = s[a + 5] + (s[b + 5] - s[a + 5]) * t;
            int la = Float.floatToRawIntBits(s[a + 6]), lb = Float.floatToRawIntBits(s[b + 6]);
            int lu = Math.round((la & 0xFFFF) + ((lb & 0xFFFF) - (la & 0xFFFF)) * t);
            int lv = Math.round(((la >>> 16) & 0xFFFF) + (((lb >>> 16) & 0xFFFF) - ((la >>> 16) & 0xFFFF)) * t);
            out[o + 6] = Float.intBitsToFloat((lv << 16) | (lu & 0xFFFF));
            for (int k = 7; k < 10; k++) out[o + k] = s[a + k];
        }
    }

    // ---- meshes ------------------------------------------------------------------------------------------------

    private static final class Section {
        final long key;
        float[][] data;
        int[] counts;
        List<BlockPos> blockEntities;
        float minX, minY, minZ, maxX, maxY, maxZ;
        boolean dirty = true;
        long builtAt = Long.MIN_VALUE;
        final double order;

        Section(long key, double order) {
            this.key = key;
            this.order = order;
        }
    }

    private static final class View {
        final PortalFrame near, far;
        final Matrix4f toNear;
        final Matrix3f normals;
        final Map<Long, Section> sections = new HashMap<>();
        final Section[] byDistance;
        long usedAt;
        boolean anyBuilt;

        View(PortalFrame near, PortalFrame far) {
            this.near = near;
            this.far = far;
            Vec3 x = PortalFrame.mapDir(far, near, new Vec3(1, 0, 0)), y = PortalFrame.mapDir(far, near, new Vec3(0, 1, 0)),
                    z = PortalFrame.mapDir(far, near, new Vec3(0, 0, 1));
            normals = new Matrix3f(new Vector3f((float) x.x, (float) x.y, (float) x.z), new Vector3f((float) y.x, (float) y.y, (float) y.z),
                    new Vector3f((float) z.x, (float) z.y, (float) z.z));
            toNear = new Matrix4f(normals).translate((float) -far.center().x, (float) -far.center().y, (float) -far.center().z);

            Vec3 c = far.center();
            int r = RADIUS + 8;
            List<Section> list = new ArrayList<>();
            for (int sx = SectionPos.blockToSectionCoord(c.x - r); sx <= SectionPos.blockToSectionCoord(c.x + r); sx++)
                for (int sy = SectionPos.blockToSectionCoord(c.y - r); sy <= SectionPos.blockToSectionCoord(c.y + r); sy++)
                    for (int sz = SectionPos.blockToSectionCoord(c.z - r); sz <= SectionPos.blockToSectionCoord(c.z + r); sz++) {
                        Vec3 mid = new Vec3(sx * 16 + 8, sy * 16 + 8, sz * 16 + 8);
                        if (mid.distanceTo(c) > RADIUS + 14) continue;
                        if (far.side(mid) < -14) continue;
                        Section s = new Section(SectionPos.asLong(sx, sy, sz), mid.distanceTo(c));
                        list.add(s);
                        sections.put(s.key, s);
                    }
            list.sort((a, b) -> Double.compare(a.order, b.order));
            byDistance = list.toArray(new Section[0]);
        }

        void build(ClientLevel level, BlockRenderDispatcher blocks) {
            long until = System.nanoTime() + BUILD_NANOS;
            long now = level.getGameTime();
            for (Section s : byDistance) {
                if (!s.dirty && now - s.builtAt < REFRESH_TICKS) continue;
                if (System.nanoTime() > until) return;
                mesh(s, level, blocks);
                s.dirty = false;
                s.builtAt = now;
                anyBuilt = true;
            }
        }

        private void mesh(Section s, ClientLevel level, BlockRenderDispatcher blocks) {
            int sx = SectionPos.x(s.key), sy = SectionPos.y(s.key), sz = SectionPos.z(s.key);
            if (level.isOutsideBuildHeight(sy * 16) && level.isOutsideBuildHeight(sy * 16 + 15)) {
                s.data = null;
                return;
            }
            if (!level.getChunkSource().hasChunk(sx, sz)) {
                s.dirty = true;
                return;
            }
            Sink sink = new Sink(new Matrix4f(toNear).translate(sx * 16f, sy * 16f, sz * 16f), normals);
            PoseStack pose = new PoseStack();
            RandomSource random = RandomSource.create();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            List<BlockPos> bes = null;
            Vec3 c = far.center();
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                p.set(sx * 16 + x, sy * 16 + y, sz * 16 + z);
                double dx = p.getX() + 0.5 - c.x, dy = p.getY() + 0.5 - c.y, dz = p.getZ() + 0.5 - c.z;
                if (dx * dx + dy * dy + dz * dz > (double) RADIUS * RADIUS) continue;
                if (far.side(new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)) < 0.2) continue;
                BlockState st = level.getBlockState(p);
                if (st.isAir()) continue;
                FluidState fluid = st.getFluidState();
                if (!fluid.isEmpty()) {
                    sink.layer = layer(ItemBlockRenderTypes.getRenderLayer(fluid));
                    blocks.renderLiquid(p, level, sink, st, fluid);
                }
                if (st.hasBlockEntity()) {
                    if (bes == null) bes = new ArrayList<>();
                    bes.add(p.immutable());
                }
                if (st.getRenderShape() != RenderShape.MODEL) continue;
                var model = blocks.getBlockModel(st);
                ModelData data = model.getModelData(level, p, st, level.getModelData(p));
                random.setSeed(st.getSeed(p));
                for (RenderType type : model.getRenderTypes(st, random, data)) {
                    sink.layer = layer(type);
                    pose.pushPose();
                    pose.translate(x, y, z);
                    try {
                        blocks.renderBatched(st, p, level, pose, sink, true, random, data, type);
                    } catch (Throwable t) {
                        WitchsAtelier.LOGGER.debug("far side mesh of {} failed", st, t);
                    }
                    pose.popPose();
                }
            }
            sink.finish(s);
            s.blockEntities = bes;
        }
    }

    private static int layer(RenderType t) {
        for (int i = 0; i < LAYERS.length; i++) if (LAYERS[i] == t) return i;
        return t.sortOnUpload() ? 3 : 0;
    }

    /** Collects block vertices, already carried into the space behind the near disc (relative to its centre). */
    private static final class Sink implements VertexConsumer {
        private final Matrix4f m;
        private final Matrix3f n;
        private final float[][] data = new float[LAYERS.length][];
        private final int[] counts = new int[LAYERS.length];
        int layer;
        private float[] cur;
        private int at = -1;
        private final Vector3f v = new Vector3f();

        Sink(Matrix4f m, Matrix3f n) {
            this.m = m;
            this.n = n;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            float[] d = data[layer];
            int c = counts[layer];
            if (d == null) d = data[layer] = new float[STRIDE * 256];
            if ((c + 1) * STRIDE > d.length) d = data[layer] = Arrays.copyOf(d, d.length * 2);
            m.transformPosition(x, y, z, v);
            at = c * STRIDE;
            d[at] = v.x; d[at + 1] = v.y; d[at + 2] = v.z;
            d[at + 3] = Float.intBitsToFloat(0xFFFFFFFF);
            d[at + 4] = 0; d[at + 5] = 0;
            d[at + 6] = Float.intBitsToFloat(LightTexture.FULL_BRIGHT);
            d[at + 7] = 0; d[at + 8] = 1; d[at + 9] = 0;
            counts[layer] = c + 1;
            cur = d;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            cur[at + 3] = Float.intBitsToFloat((a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            cur[at + 4] = u;
            cur[at + 5] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            cur[at + 6] = Float.intBitsToFloat((v & 0xFFFF) << 16 | (u & 0xFFFF));
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            n.transform(x, y, z, v);
            cur[at + 7] = v.x; cur[at + 8] = v.y; cur[at + 9] = v.z;
            return this;
        }

        void finish(Section s) {
            float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
            boolean any = false;
            for (int l = 0; l < LAYERS.length; l++) {
                counts[l] -= counts[l] % 4;
                float[] d = data[l];
                for (int i = 0; i < counts[l]; i++) {
                    int o = i * STRIDE;
                    minX = Math.min(minX, d[o]); maxX = Math.max(maxX, d[o]);
                    minY = Math.min(minY, d[o + 1]); maxY = Math.max(maxY, d[o + 1]);
                    minZ = Math.min(minZ, d[o + 2]); maxZ = Math.max(maxZ, d[o + 2]);
                    any = true;
                }
            }
            if (!any) {
                s.data = null;
                s.counts = null;
                return;
            }
            s.data = data;
            s.counts = counts;
            s.minX = minX; s.minY = minY; s.minZ = minZ;
            s.maxX = maxX; s.maxY = maxY; s.maxZ = maxZ;
        }
    }

    /** Iris, reached by name so the mod neither needs it nor breaks without it. */
    private static final class IrisHook {
        private static final java.lang.invoke.MethodHandle SHADOW;

        static {
            java.lang.invoke.MethodHandle h = null;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                h = java.lang.invoke.MethodHandles.publicLookup().findVirtual(api, "isRenderingShadowPass",
                        java.lang.invoke.MethodType.methodType(boolean.class)).bindTo(instance);
            } catch (Throwable ignored) {
            }
            SHADOW = h;
        }

        private static java.lang.reflect.Field blockTarget;
        private static java.lang.reflect.Method bind;
        private static boolean looked;

        /**
         * With a pack running, binds the target its block programs draw into (before translucent things) and returns
         * true; otherwise leaves the main target bound and returns false.
         */
        static boolean bindBlockTarget() {
            ShaderInstance solid = net.minecraft.client.renderer.GameRenderer.getRendertypeSolidShader();
            if (solid == null || !solid.getClass().getName().startsWith("net.irisshaders.")) return false;
            try {
                if (!looked) {
                    looked = true;
                    for (Class<?> k = solid.getClass(); k != null && blockTarget == null; k = k.getSuperclass()) {
                        try {
                            blockTarget = k.getDeclaredField("writingToBeforeTranslucent");
                        } catch (NoSuchFieldException ignored) {
                        }
                    }
                    if (blockTarget != null) {
                        blockTarget.setAccessible(true);
                        Object fb = blockTarget.get(solid);
                        bind = fb == null ? null : fb.getClass().getMethod("bind");
                    }
                }
                if (blockTarget == null || bind == null) return false;
                Object fb = blockTarget.get(solid);
                if (fb == null) return false;
                bind.invoke(fb);
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        /** Whether a shader pack is drawing the level right now. */
        static boolean packActive() {
            ShaderInstance solid = net.minecraft.client.renderer.GameRenderer.getRendertypeSolidShader();
            return solid != null && solid.getClass().getName().startsWith("net.irisshaders.");
        }

        static boolean shadowPass() {
            if (SHADOW == null) return false;
            try {
                return (boolean) SHADOW.invokeExact();
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
