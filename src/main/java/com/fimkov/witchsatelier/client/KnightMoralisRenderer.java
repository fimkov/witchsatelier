package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.client.model.WitchModels;
import com.fimkov.witchsatelier.entity.KnightMoralisEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class KnightMoralisRenderer extends HumanoidMobRenderer<KnightMoralisEntity, HumanoidModel<KnightMoralisEntity>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "knight_moralis"), "main");
    private static final ResourceLocation SKIN = BrimhatRenderer.tex("knight_moralis");

    public static final ModelLayerLocation GEAR = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "knight_moralis"), "gear");
    private static final ResourceLocation GEAR_TEX = BrimhatRenderer.tex("knight_moralis_gear");

    public static LayerDefinition gear() {
        var mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        var root = mesh.getRoot();
        root.getChild("head").addOrReplaceChild("crest", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.5f, -12.5f, -4.5f, 1, 5, 10), PartPose.ZERO);
        root.getChild("right_arm").addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(0, 16)
                .addBox(-4.2f, -2.6f, -3, 5, 4, 6), PartPose.rotation(0, 0, 0.15f));
        root.getChild("left_arm").addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(0, 16).mirror()
                .addBox(-0.8f, -2.6f, -3, 5, 4, 6), PartPose.rotation(0, 0, -0.15f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition layer() {
        return LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(0.0f), 0), 64, 64);
    }

    public KnightMoralisRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(LAYER)), 0.5f);
        addLayer(new AttireLayer<>(this, WitchModels.CAPELET, BrimhatRenderer.tex("knight_moralis_capelet")));
        HumanoidModel<KnightMoralisEntity> gear = new HumanoidModel<>(ctx.bakeLayer(GEAR));
        addLayer(new RenderLayer<>(this) {
            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, KnightMoralisEntity e,
                               float ls, float la, float pt, float age, float yaw, float pitch) {
                getParentModel().copyPropertiesTo(gear);
                gear.setAllVisible(true);
                gear.body.visible = false;
                gear.rightLeg.visible = gear.leftLeg.visible = false;
                gear.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(GEAR_TEX)), light,
                        LivingEntityRenderer.getOverlayCoords(e, 0));
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(KnightMoralisEntity e) {
        return SKIN;
    }

    @Override
    protected void scale(KnightMoralisEntity e, PoseStack pose, float partialTick) {
        pose.scale(1.05f, 1.05f, 1.05f);
    }

    public static class Model extends HumanoidModel<KnightMoralisEntity> {
        public Model(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(KnightMoralisEntity e, float limbSwing, float amount, float age, float yaw, float pitch) {
            super.setupAnim(e, limbSwing, amount, age, yaw, pitch);

            leftArm.xRot *= 0.6f;
            body.yRot = Mth.sin(limbSwing * 0.6662f) * 0.05f * amount;
            if (e.bindPose > 0) {
                float t = Math.min(1, e.bindPose / 8f);

                leftArm.xRot = Mth.lerp(t, leftArm.xRot, -1.5f + head.xRot);
                leftArm.yRot = Mth.lerp(t, leftArm.yRot, 0.1f + head.yRot);
                rightArm.xRot = Mth.lerp(t, rightArm.xRot, -0.3f);
                body.xRot = 0.08f * t;
            }
        }
    }
}
