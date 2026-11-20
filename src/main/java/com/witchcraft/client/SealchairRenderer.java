package com.witchcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.witchcraft.WitchCraft;
import com.witchcraft.entity.SealchairEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The Sealchair: seat, carved back, and four slender hoofed legs that step like a goat's. */
public class SealchairRenderer extends MobRenderer<SealchairEntity, SealchairRenderer.Model> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "sealchair"), "main");
    static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "textures/entity/sealchair.png");

    public SealchairRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(LAYER)), 0f); // a chair throws no creature's shadow
    }

    @Override
    public ResourceLocation getTextureLocation(SealchairEntity e) {
        return TEX;
    }

    public static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0, 13, 0));
        body.addOrReplaceChild("seat", CubeListBuilder.create().texOffs(0, 0).addBox(-6, -2, -6, 12, 2, 12), PartPose.ZERO);
        body.addOrReplaceChild("rim", CubeListBuilder.create().texOffs(0, 14).addBox(-6.5f, -2.5f, -6.5f, 13, 1, 13), PartPose.ZERO);
        body.addOrReplaceChild("back", CubeListBuilder.create().texOffs(0, 28).addBox(-6, -14, 0, 12, 12, 2), PartPose.offsetAndRotation(0, -2, 4.5f, -0.12f, 0, 0));
        body.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(28, 28).addBox(-4, -3, 0, 8, 3, 2), PartPose.offsetAndRotation(0, -15.5f, 4.5f, -0.12f, 0, 0));
        body.addOrReplaceChild("horn_l", CubeListBuilder.create().texOffs(48, 0).addBox(-1, -4, -1, 2, 4, 2), PartPose.offsetAndRotation(5, -16, 5.5f, -0.3f, 0, 0.5f));
        body.addOrReplaceChild("horn_r", CubeListBuilder.create().texOffs(48, 0).addBox(-1, -4, -1, 2, 4, 2), PartPose.offsetAndRotation(-5, -16, 5.5f, -0.3f, 0, -0.5f));
        for (String n : new String[]{"leg_fl", "leg_fr", "leg_bl", "leg_br"}) {
            float x = n.endsWith("l") ? 4.5f : -4.5f, z = n.contains("f") ? -4.5f : 4.5f;
            PartDefinition leg = root.addOrReplaceChild(n, CubeListBuilder.create().texOffs(48, 8).addBox(-1, 0, -1, 2, 8, 2), PartPose.offset(x, 13, z));
            leg.addOrReplaceChild("hoof", CubeListBuilder.create().texOffs(48, 20).addBox(-1.5f, 8, -1.5f, 3, 3, 3), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** The chair at rest, as a block: the same carved model, standing still. */
    public static class AtRest implements net.minecraft.client.renderer.blockentity.BlockEntityRenderer<com.witchcraft.block.SealchairBlock.Entity> {
        private final Model model;

        public AtRest(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context ctx) {
            model = new Model(ctx.bakeLayer(LAYER));
        }

        @Override
        public void render(com.witchcraft.block.SealchairBlock.Entity be, float pt, PoseStack ps, net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay) {
            float yaw = be.getBlockState().getValue(com.witchcraft.block.SealchairBlock.FACING).toYRot();
            ps.pushPose();
            ps.translate(0.5, 0, 0.5);
            ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180 - yaw));
            ps.scale(-1, -1, 1);
            ps.translate(0, -1.501, 0);
            model.setupAnim(null, 0, 0, 0, 0, 0);
            model.renderToBuffer(ps, buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(TEX)), light, overlay, -1);
            ps.popPose();
        }
    }

    public static class Model extends EntityModel<SealchairEntity> {
        private final ModelPart body, fl, fr, bl, br;

        public Model(ModelPart root) {
            body = root.getChild("body");
            fl = root.getChild("leg_fl");
            fr = root.getChild("leg_fr");
            bl = root.getChild("leg_bl");
            br = root.getChild("leg_br");
        }

        @Override
        public void setupAnim(SealchairEntity e, float limbSwing, float amount, float age, float yaw, float pitch) {
            float s = Mth.cos(limbSwing * 0.9f) * 1.1f * amount;
            fl.xRot = s;
            br.xRot = s;
            fr.xRot = -s;
            bl.xRot = -s;
            body.y = 13 - Math.abs(Mth.sin(limbSwing * 0.9f)) * 0.6f * amount;
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, int color) {
            body.render(pose, vc, light, overlay, color);
            fl.render(pose, vc, light, overlay, color);
            fr.render(pose, vc, light, overlay, color);
            bl.render(pose, vc, light, overlay, color);
            br.render(pose, vc, light, overlay, color);
        }
    }
}
