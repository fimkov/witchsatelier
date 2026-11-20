package com.witchcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.witchcraft.WitchCraft;
import com.witchcraft.entity.BrimhatElderEntity;
import com.witchcraft.entity.BrimhatElderEntity.Anim;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The Brimhat Elder, drawn as itself: a towering floating figure in a ragged robe, a hat as wide as a cart with
 * charms swinging from its brim, a porcelain mask with red light in its eyes, a brush as long as a witch is tall,
 * and a halo of its grimoire's pages turning behind it. Every move it makes has its own gesture, and the glyph it
 * draws in the air shows in front of it as it draws.
 */
public class ElderRenderer extends MobRenderer<BrimhatElderEntity, ElderRenderer.Model> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "brimhat_elder"), "main");
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "textures/entity/brimhat_elder_body.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "textures/entity/brimhat_elder_glow.png");
    static final int PANELS = 12, PAGES = 6, CHARMS = 4;

    public ElderRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(LAYER)), 1.1f);
        addLayer(new Glow(this));
    }

    @Override
    public ResourceLocation getTextureLocation(BrimhatElderEntity e) {
        return TEX;
    }

    /** No toppling over on death: it has its own way of dying. */
    @Override
    protected float getFlipDegrees(BrimhatElderEntity e) {
        return 0;
    }

    /** It floats: bobbing a little over the ground, rising as it roars, sinking as it dies. */
    @Override
    protected void scale(BrimhatElderEntity e, PoseStack ps, float pt) {
        float t = e.tickCount + pt;
        float p = progress(e, pt);
        double hover = 0.35 + 0.08 * Math.sin(t * 0.08);
        switch (e.anim()) {
            case PHASE -> hover += 0.9 * Math.sin(Math.PI * Math.min(1, p * 1.4));
            case INSCRIBE -> hover += 0.6 * Math.min(1, p * 6);
            case STAGGER -> hover *= 0.3;
            case DYING -> hover = hover * (1 - p) - 0.5 * p * p;
            default -> {}
        }
        ps.translate(0, -hover, 0); // (the model's y runs downward here)
    }

    static float progress(BrimhatElderEntity e, float pt) {
        float age = e.animAge(pt);
        int len = switch (e.anim()) {
            case DRAW_AIR -> 26;
            case INSCRIBE -> 96;
            case SUMMON -> 24;
            case PHASE -> 44;
            case STAGGER -> 70;
            case DYING -> 70;
            default -> 1;
        };
        return Mth.clamp(age / len, 0, 1);
    }

    @Override
    public void render(BrimhatElderEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        super.render(e, yaw, pt, ps, buffers, light);
        if (e.anim() == Anim.DRAW_AIR && e.isAlive()) airGlyph(e, pt, ps, buffers);
    }

    /**
     * The glyph it draws in the air, in front of it, as the brush goes round: first the ring, then the fire sigil in
     * it, flaring as it goes off.
     */
    private static void airGlyph(BrimhatElderEntity e, float pt, PoseStack ps, MultiBufferSource buffers) {
        float p = progress(e, pt);
        float yawRad = (float) Math.toRadians(Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot));
        Vec3 fwd = new Vec3(-Mth.sin(yawRad), 0, Mth.cos(yawRad)), right = new Vec3(-fwd.z, 0, fwd.x), up = new Vec3(0, 1, 0);
        Vec3 c = fwd.scale(1.8).add(0, 2.4, 0);
        double r = 0.95;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        float ring = Mth.clamp((p - 0.12f) / 0.5f, 0, 1), sigil = Mth.clamp((p - 0.55f) / 0.25f, 0, 1);
        float flare = p > 0.85f ? (p - 0.85f) / 0.15f : 0;
        int alpha = (int) (150 * (1 - flare * 0.6f));
        // ring
        int n = 48;
        double start = Math.PI / 2;
        Vec3 prev = null;
        for (int i = 0; i <= n * ring; i++) {
            double a = start - Math.PI * 2 * i / n;
            Vec3 q = c.add(right.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
            if (prev != null) strip(vc, m, prev, q, fwd, 0.07 + flare * 0.08, 220, 40, 25, alpha);
            prev = q;
        }
        // the fire sigil: a triangle and its stem, drawn after the ring
        Vec3[] tri = {c.add(up.scale(0.45)), c.add(right.scale(0.4)).add(up.scale(-0.25)), c.add(right.scale(-0.4)).add(up.scale(-0.25)), c.add(up.scale(0.45))};
        double total = sigil * 4;
        for (int k = 0; k < 3 && total > 0; k++, total -= 1) {
            double f = Math.min(1, total);
            strip(vc, m, tri[k], tri[k].add(tri[k + 1].subtract(tri[k]).scale(f)), fwd, 0.07 + flare * 0.08, 230, 90, 30, alpha);
        }
        if (total > 0) {
            Vec3 s0 = c.add(up.scale(0.45)), s1 = c.add(up.scale(0.45 - 1.05 * Math.min(1, total)));
            strip(vc, m, s0, s1, fwd, 0.07 + flare * 0.08, 230, 90, 30, alpha);
        }
    }

    /** A flat strip of light from a to b, facing along {@code facing}, drawn both ways round. */
    private static void strip(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 facing, double width, int r, int g, int bl, int al) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-8) return;
        Vec3 side = d.cross(facing).normalize().scale(width / 2);
        Vec3 p0 = a.subtract(side), p1 = a.add(side), p2 = b.add(side), p3 = b.subtract(side);
        for (Vec3[] q : new Vec3[][]{{p0, p1, p2, p3}, {p3, p2, p1, p0}}) {
            for (Vec3 v : q) vc.addVertex(m, (float) v.x, (float) v.y, (float) v.z).setColor(r, g, bl, al);
        }
    }

    /** The eyes, the pages' glyphs, the brush's wet tip and the charms, alight in the dark. */
    static class Glow extends RenderLayer<BrimhatElderEntity, Model> {
        Glow(RenderLayerParent<BrimhatElderEntity, Model> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buffers, int light, BrimhatElderEntity e, float limbSwing, float amount, float pt, float age, float yaw, float pitch) {
            float fade = e.anim() == Anim.DYING ? 1 - progress(e, pt) : 1;
            int a = (int) (255 * fade * (e.anim() == Anim.STAGGER ? 0.4f : 1));
            getParentModel().renderToBuffer(ps, buffers.getBuffer(RenderType.eyes(GLOW)), 0xF000F0, OverlayTexture.NO_OVERLAY, (a << 24) | 0xFFFFFF);
        }
    }

    public static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6, -18, -3.5f, 12, 18, 7)
                .texOffs(0, 26).addBox(-8, -18, -5, 16, 7, 10), PartPose.offset(0, 4, 0));
        for (int i = 0; i < PANELS; i++) {
            double a = i * Math.PI * 2 / PANELS;
            body.addOrReplaceChild("panel_" + i, CubeListBuilder.create().texOffs(64, 0).addBox(-2.5f, 0, -0.5f, 5, 19, 1),
                    PartPose.offsetAndRotation((float) Math.sin(a) * 5.6f, -1, (float) Math.cos(a) * 5.6f, 0.12f, (float) a, 0));
        }
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 44).addBox(-5, -10, -5, 10, 10, 10)
                .texOffs(40, 44).addBox(-4, -9, -5.8f, 8, 9, 1), PartPose.offset(0, -18, 0));
        PartDefinition hat = head.addOrReplaceChild("hat", CubeListBuilder.create()
                .texOffs(0, 64).addBox(-15, -1, -15, 30, 1, 30)
                .texOffs(0, 96).addBox(-5.5f, -7, -5.5f, 11, 6, 11), PartPose.offset(0, -9, 0));
        PartDefinition c2 = hat.addOrReplaceChild("crown2", CubeListBuilder.create().texOffs(44, 96).addBox(-4, -6, -4, 8, 6, 8),
                PartPose.offsetAndRotation(0, -7, 0, -0.12f, 0, 0.05f));
        PartDefinition c3 = c2.addOrReplaceChild("crown3", CubeListBuilder.create().texOffs(76, 96).addBox(-2.5f, -5, -2.5f, 5, 5, 5),
                PartPose.offsetAndRotation(0, -6, 0, -0.22f, 0, 0.1f));
        c3.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(96, 96).addBox(-1, -4, -1, 2, 4, 2),
                PartPose.offsetAndRotation(0, -5, 0, -0.45f, 0, 0.15f));
        for (int k = 0; k < CHARMS; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2;
            hat.addOrReplaceChild("charm_" + k, CubeListBuilder.create()
                    .texOffs(104, 96).addBox(-0.5f, 0, -0.5f, 1, 5, 1)
                    .texOffs(108, 96).addBox(-1, 5, -1, 2, 2, 2), PartPose.offset((float) Math.cos(a) * 13.5f, 0, (float) Math.sin(a) * 13.5f));
        }
        for (int side = 0; side < 2; side++) {
            float sx = side == 0 ? -7.5f : 7.5f;
            PartDefinition arm = body.addOrReplaceChild(side == 0 ? "arm_r" : "arm_l", CubeListBuilder.create()
                    .texOffs(80, 0).addBox(-2.5f, -1, -2.5f, 5, 15, 5)
                    .texOffs(80, 20).addBox(-3.5f, 11, -3.5f, 7, 5, 7)
                    .texOffs(108, 20).addBox(-1.5f, 16, -1.5f, 3, 3, 3), PartPose.offset(sx, -16, 0));
            if (side == 0) {
                arm.addOrReplaceChild("brush", CubeListBuilder.create()
                        .texOffs(120, 0).addBox(-0.75f, -16, -0.75f, 1.5f, 30, 1.5f)
                        .texOffs(108, 26).addBox(-1.5f, -21, -1.5f, 3, 5, 3), PartPose.offset(0, 17.5f, -0.5f));
            }
        }
        PartDefinition halo = body.addOrReplaceChild("halo", CubeListBuilder.create(), PartPose.offset(0, -22, 7.5f));
        for (int k = 0; k < PAGES; k++) {
            double a = k * Math.PI * 2 / PAGES;
            halo.addOrReplaceChild("page_" + k, CubeListBuilder.create().texOffs(64, 22).addBox(-4, -5.5f, 0, 8, 11, 0),
                    PartPose.offsetAndRotation((float) Math.cos(a) * 15, (float) Math.sin(a) * 15, 0, 0, 0, (float) a));
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    public static class Model extends EntityModel<BrimhatElderEntity> {
        private final ModelPart root, body, head, hat, armR, armL, brush, halo;
        private final ModelPart[] panels = new ModelPart[PANELS], pages = new ModelPart[PAGES], charms = new ModelPart[CHARMS];

        public Model(ModelPart root) {
            this.root = root;
            body = root.getChild("body");
            head = body.getChild("head");
            hat = head.getChild("hat");
            armR = body.getChild("arm_r");
            armL = body.getChild("arm_l");
            brush = armR.getChild("brush");
            halo = body.getChild("halo");
            for (int i = 0; i < PANELS; i++) panels[i] = body.getChild("panel_" + i);
            for (int k = 0; k < PAGES; k++) pages[k] = halo.getChild("page_" + k);
            for (int k = 0; k < CHARMS; k++) charms[k] = hat.getChild("charm_" + k);
        }

        @Override
        public void setupAnim(BrimhatElderEntity e, float limbSwing, float amount, float age, float yaw, float pitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            float pt = age - e.tickCount;
            float p = progress(e, pt);
            Anim a = e.anim();
            amount = Mth.clamp(amount, 0, 1);
            int stage = e.stage();

            // at rest: floating, breathing, the robe stirring, the hat's charms swinging
            body.xRot = 0.05f + 0.04f * Mth.sin(age * 0.05f) + amount * 0.25f;
            head.yRot = yaw * Mth.DEG_TO_RAD;
            head.xRot = pitch * Mth.DEG_TO_RAD * 0.6f;
            hat.zRot = 0.03f * Mth.sin(age * 0.04f);
            for (int k = 0; k < CHARMS; k++) {
                charms[k].zRot = 0.22f * Mth.sin(age * 0.1f + k * 1.7f) + amount * 0.3f;
                charms[k].xRot = 0.15f * Mth.cos(age * 0.09f + k);
            }
            for (int i = 0; i < PANELS; i++) {
                double ang = i * Math.PI * 2 / PANELS;
                float behind = (float) Math.max(0, Math.cos(ang)); // the side away from where it faces
                panels[i].xRot = 0.12f + 0.07f * Mth.sin(age * 0.12f + i * 0.9f) + amount * 0.55f * behind;
            }
            armR.xRot = -0.35f + 0.05f * Mth.sin(age * 0.06f);
            armR.zRot = 0.12f;
            armL.xRot = -0.1f + 0.05f * Mth.sin(age * 0.05f + 1);
            armL.zRot = -0.15f;
            float spin = age * 0.02f * (1 + stage * 0.4f);
            float radius = 15;
            float pageLift = 0;

            switch (a) {
                case DRAW_AIR -> {
                    // the brush lifts and goes round, drawing the ring, then thrusts it home
                    float raise = Mth.clamp(p / 0.15f, 0, 1);
                    armR.xRot = Mth.lerp(raise, armR.xRot, -1.75f);
                    if (p > 0.15f && p < 0.85f) {
                        float th = (p - 0.15f) / 0.7f * Mth.TWO_PI;
                        armR.xRot = -1.75f + 0.32f * Mth.sin(th);
                        armR.yRot = 0.32f * Mth.cos(th);
                    } else if (p >= 0.85f) {
                        armR.xRot = -1.95f;
                    }
                    armL.xRot = Mth.lerp(raise, armL.xRot, -1.0f);
                    armL.zRot = Mth.lerp(raise, armL.zRot, -0.5f);
                    body.xRot += 0.1f;
                    spin *= 1.8f;
                }
                case INSCRIBE -> {
                    // bent over the floor, the brush sweeping the great ring round
                    body.xRot = 0.35f;
                    head.xRot = 0.5f;
                    armR.xRot = -0.95f + 0.25f * Mth.sin(age * 0.25f);
                    armR.zRot = 0.2f * Mth.cos(age * 0.25f);
                    armL.xRot = -0.6f;
                    armL.zRot = -0.45f;
                    spin *= 3;
                    radius = 17;
                }
                case SUMMON -> {
                    float s = Mth.sin(Math.min(1, p * 1.6f) * Mth.HALF_PI);
                    armR.zRot = 0.12f + 1.2f * s;
                    armL.zRot = -0.15f - 1.2f * s;
                    armR.xRot = armL.xRot = -0.4f * s;
                    head.xRot = -0.3f * s;
                    radius = 15 + 5 * s;
                }
                case PHASE -> {
                    // gathers itself, then flings its arms up and roars, the pages bursting out
                    float gather = Mth.clamp(p / 0.45f, 0, 1), burst = Mth.clamp((p - 0.45f) / 0.15f, 0, 1), settle = Mth.clamp((p - 0.7f) / 0.3f, 0, 1);
                    body.xRot = Mth.lerp(burst, 0.3f * gather, -0.3f) * (1 - settle);
                    armR.xRot = armL.xRot = Mth.lerp(burst, 0.3f * gather, -2.8f) * (1 - settle) + armR.xRot * settle;
                    armR.zRot = Mth.lerp(burst, 0.5f * gather, 0.45f);
                    armL.zRot = -armR.zRot;
                    head.xRot = Mth.lerp(burst, 0.4f * gather, -0.6f) * (1 - settle);
                    hat.xRot = -0.35f * burst * (1 - settle);
                    radius = 15 - 4 * gather + 16 * burst * (1 - settle);
                    spin *= 1 + 6 * burst * (1 - settle);
                }
                case STAGGER -> {
                    body.xRot = 0.5f;
                    head.xRot = 0.6f;
                    armR.xRot = armL.xRot = 0.2f;
                    armR.zRot = 0.1f;
                    armL.zRot = -0.1f;
                    hat.zRot = 0.25f;
                    hat.xRot = 0.15f;
                    spin = 0.3f;
                    pageLift = 6;
                }
                case DYING -> {
                    body.xRot = 0.15f + 0.8f * p;
                    head.xRot = 0.7f * p;
                    armR.xRot = armL.xRot = 0.3f * p;
                    armR.zRot = 0.1f + 0.4f * p;
                    armL.zRot = -0.1f - 0.4f * p;
                    // the hat slides off; the pages drift down
                    hat.y += p * 18;
                    hat.z += p * 10;
                    hat.xRot = 1.2f * p;
                    hat.zRot = 0.6f * p;
                    spin = spin * (1 - p);
                    pageLift = p * p * 40;
                }
                default -> {}
            }
            halo.zRot = spin;
            for (int k = 0; k < PAGES; k++) {
                double ang = k * Math.PI * 2 / PAGES;
                pages[k].x = (float) Math.cos(ang) * radius;
                pages[k].y = (float) Math.sin(ang) * radius + pageLift * (1 + k % 3 * 0.3f);
                pages[k].yRot = 0.25f * Mth.sin(age * 0.07f + k);
            }
            // the brush held like a staff whatever the arm does: upright at rest, tip forward to draw in the air,
            // tip down to the floor for the great ring
            float hold = switch (a) {
                case DRAW_AIR -> 1.35f * Mth.clamp(p / 0.15f, 0, 1);
                case INSCRIBE -> 2.4f;
                case PHASE, SUMMON -> 0.25f;
                case STAGGER, DYING -> 0.6f;
                default -> 0.08f;
            };
            brush.xRot = -armR.xRot + hold;
        }

        @Override
        public void renderToBuffer(PoseStack ps, VertexConsumer vc, int light, int overlay, int color) {
            body.render(ps, vc, light, overlay, color);
        }
    }
}
