package com.fimkov.witchsatelier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The Capture Pennant's banner in flight: a ribbon of silver-white silk that streams out from the pole, rippling and
 * turning over as it goes, reaches whoever it was levelled at and winds round them, then lets go of the pole and
 * holds them bound until it loosens and fades. Aimed at nothing, it streams out and falls back.
 */
public final class PennantRibbons {
    private PennantRibbons() {}

    private static final ResourceLocation SILK = ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "textures/entity/capture_pennant_ribbon.png");
    private static final int UNFURL = 7, LET_GO = 22, LET_GO_TIME = 8, MISS = 18;
    private static final float WIDTH = 0.3f;

    private record Ribbon(int caster, int target, Vec3 end, int hold, long start, Vec3[] last) {}
    private static final List<Ribbon> RIBBONS = new ArrayList<>();

    public static void add(Payloads.PennantFx p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        RIBBONS.add(new Ribbon(p.caster(), p.target(), new Vec3(p.x(), p.y(), p.z()), p.hold(), mc.level.getGameTime(), new Vec3[2]));
    }

    public static void clear() {
        RIBBONS.clear();
    }

    static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || RIBBONS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { RIBBONS.clear(); return; }
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + pt;
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucent(SILK);
        VertexConsumer vc = buffers.getBuffer(type);
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose pose = ps.last();
        RIBBONS.removeIf(r -> !draw(vc, pose, mc, r, now, pt));
        ps.popPose();
        buffers.endBatch(type);
    }

    /** Draws one ribbon; false once it is gone. */
    private static boolean draw(VertexConsumer vc, PoseStack.Pose pose, Minecraft mc, Ribbon r, double now, float pt) {
        double age = now - r.start;
        boolean hit = r.target >= 0;
        double life = hit ? r.hold : MISS;
        if (age > life || age < -5) return false;

        Entity caster = mc.level.getEntity(r.caster);
        Vec3 from = caster != null ? tip(mc, caster, pt) : r.last[0];
        if (from == null) from = r.end;
        r.last[0] = from;
        Entity target = hit ? mc.level.getEntity(r.target) : null;
        Vec3 at = target != null ? target.getPosition(pt) : r.last[1] != null ? r.last[1] : r.end;
        r.last[1] = at;

        // the path: out from the pole in a rising arc, then round and round the one it binds
        List<Vec3> path = new ArrayList<>();
        List<Vec3> across = new ArrayList<>();
        int wrapFrom;
        double tw = now * 0.35;
        if (hit) {
            float bw = target != null ? target.getBbWidth() : 0.6f, bh = target != null ? target.getBbHeight() : 1.8f;
            double rad = bw * 0.62 + 0.1;
            Vec3 toCaster = from.subtract(at).multiply(1, 0, 1);
            double a0 = Math.atan2(toCaster.z, toCaster.x);
            Vec3 first = at.add(Math.cos(a0) * rad, bh * 0.78, Math.sin(a0) * rad);
            stream(path, across, from, first, now, 34);
            wrapFrom = path.size();
            int turns = 3, steps = 72;
            for (int i = 1; i <= steps; i++) {
                double s = (double) i / steps;
                double a = a0 + s * turns * Math.PI * 2;
                double y = bh * (0.78 - 0.62 * s) + 0.03 * Math.sin(a * 2 + tw);
                double rr = rad * (1 + 0.04 * Math.sin(now * 0.2 + s * 9));
                path.add(at.add(Math.cos(a) * rr, y, Math.sin(a) * rr));
                across.add(new Vec3(0, 1, 0).scale(0.8));
            }
        } else {
            stream(path, across, from, r.end, now, 40);
            wrapFrom = path.size();
        }

        // how much of it is out: it unfurls fast, and a miss draws back in
        double total = 0;
        double[] at_ = new double[path.size()];
        for (int i = 1; i < path.size(); i++) at_[i] = total += path.get(i).distanceTo(path.get(i - 1));
        double head = total * Mth.clamp(age / UNFURL, 0, 1);
        double tail = 0;
        if (!hit && age > MISS - 8) head = total * Mth.clamp((MISS - age) / 8, 0, 1);
        // once wound round, it lets go of the pole and the loose length draws in to the binding
        if (hit && age > LET_GO) tail = at_[Math.max(0, wrapFrom - 1)] * Mth.clamp((age - LET_GO) / LET_GO_TIME, 0, 1);
        float alpha = 1;
        if (hit && age > life - 12) alpha = (float) Mth.clamp((life - age) / 12, 0, 1);

        // sparkles where the silk runs out ahead, and where it loosens at the end
        if (mc.level.random.nextFloat() < 0.6f && head < total && head > 0) {
            Vec3 h = pointAt(path, at_, head);
            mc.level.addParticle(ParticleTypes.END_ROD, h.x, h.y, h.z, 0, 0.01, 0);
        }
        if (hit && alpha < 1 && wrapFrom < path.size() && mc.level.random.nextFloat() < 0.4f) {
            Vec3 h = path.get(wrapFrom + mc.level.random.nextInt(path.size() - wrapFrom));
            mc.level.addParticle(ParticleTypes.END_ROD, h.x, h.y, h.z, 0, 0.03, 0);
        }

        int a = (int) (alpha * 255);
        Vec3 prevL = null, prevR = null;
        double prevV = 0;
        int prevLight = 0;
        Vec3 prevN = null;
        for (int i = 0; i < path.size(); i++) {
            if (at_[i] > head) break;
            if (at_[i] < tail) { prevL = null; continue; }
            Vec3 p = path.get(i);
            Vec3 t = path.get(Math.min(path.size() - 1, i + 1)).subtract(path.get(Math.max(0, i - 1))).normalize();
            Vec3 w = across.get(i);
            // keep the width at a right angle to the line
            w = w.subtract(t.scale(w.dot(t)));
            if (w.lengthSqr() < 1e-6) w = t.cross(new Vec3(0, 1, 0));
            w = w.normalize().scale(WIDTH / 2);
            Vec3 n = t.cross(w).normalize();
            Vec3 l = p.subtract(w), rr = p.add(w);
            double v = at_[i] / (WIDTH * 4);
            BlockPos bp = BlockPos.containing(p);
            int packed = LevelRenderer.getLightColor(mc.level, bp);
            // the silk gives off a little light of its own
            int light = LightTexture.pack(Math.max(LightTexture.block(packed), 9), LightTexture.sky(packed));
            if (prevL != null) {
                vertex(vc, pose, prevL, 0, prevV, a, prevLight, prevN);
                vertex(vc, pose, prevR, 1, prevV, a, prevLight, prevN);
                vertex(vc, pose, rr, 1, v, a, light, n);
                vertex(vc, pose, l, 0, v, a, light, n);
            }
            prevL = l; prevR = rr; prevV = v; prevLight = light; prevN = n;
        }
        return true;
    }

    /** The streaming length: a rising arc with a ripple travelling down it and the silk turning over as it goes. */
    private static void stream(List<Vec3> path, List<Vec3> across, Vec3 from, Vec3 to, double now, int steps) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        Vec3 dir = len < 1e-4 ? new Vec3(0, 0, 1) : d.scale(1 / len);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(dir).normalize();
        for (int i = 0; i <= steps; i++) {
            double s = (double) i / steps;
            double env = Math.sin(Math.PI * s);
            Vec3 p = from.add(d.scale(s))
                    .add(up.scale(env * Math.min(1.6, len * 0.12)))
                    .add(side.scale(env * 0.35 * Math.sin(s * 9 - now * 0.5)))
                    .add(up.scale(env * 0.18 * Math.sin(s * 13 - now * 0.7)));
            path.add(p);
            double turn = 1.1 * Math.sin(s * 5 - now * 0.3);
            across.add(side.scale(Math.cos(turn)).add(up.scale(Math.sin(turn))));
        }
    }

    private static Vec3 pointAt(List<Vec3> path, double[] at, double d) {
        for (int i = 1; i < path.size(); i++) if (at[i] >= d) return path.get(i);
        return path.get(path.size() - 1);
    }

    /** Where the pole's tip is: over the right hand, or before the eyes in first person. */
    private static Vec3 tip(Minecraft mc, Entity e, float pt) {
        if (e == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Vec3 eye = e.getEyePosition(pt), look = e.getViewVector(pt);
            Vec3 right = look.cross(new Vec3(0, 1, 0));
            right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
            return eye.add(look.scale(0.9)).add(right.scale(0.42)).add(0, 0.12, 0);
        }
        float yaw = (float) Math.toRadians(e instanceof LivingEntity le ? Mth.lerp(pt, le.yBodyRotO, le.yBodyRot) : e.getYRot());
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        // the hand that holds the pennant (a knight carries it in the off hand)
        if (e instanceof LivingEntity le) {
            boolean inMain = le.getMainHandItem().getItem() instanceof com.fimkov.witchsatelier.item.artifact.CapturePennantItem;
            boolean rightHand = (le.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) == inMain;
            if (!rightHand) right = right.scale(-1);
        }
        return e.getPosition(pt).add(0, e.getBbHeight() * 0.62 + 0.9, 0).add(right.scale(0.4)).add(fwd.scale(0.9));
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, float u, double v, int a, int light, Vec3 n) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(255, 255, 255, a).setUv(u, (float) v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }
}
