package com.fimkov.witchsatelier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.client.model.WitchModels;
import com.fimkov.witchsatelier.entity.BrimhatEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class BrimhatRenderer extends HumanoidMobRenderer<BrimhatEntity, BrimhatRenderer.Model> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "brimhat"), "main");
    public static final ModelLayerLocation HAT = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "brimhat"), "hat");
    private static final ResourceLocation SKIN = tex("brimhat");
    private static final ResourceLocation HAT_TEX = tex("brimhat_hat");
    private static final RenderType EYES = RenderType.eyes(tex("brimhat_eyes"));

    static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "textures/entity/" + name + ".png");
    }

    public static LayerDefinition layer() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0), 64, 64);
    }

    public static LayerDefinition hat() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        PartDefinition head = mesh.getRoot().getChild("head");
        head.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -12.5f, -4.5f, 9, 5, 9, new CubeDeformation(0.05f)), PartPose.ZERO);
        head.addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 14).addBox(-4.6f, -9.0f, -4.6f, 9.2f, 1.2f, 9.2f), PartPose.ZERO);

        float droop = 0.12f;
        head.addOrReplaceChild("brim_front", CubeListBuilder.create().texOffs(0, 26).addBox(-9, 0, -5, 18, 0.6f, 5), PartPose.offsetAndRotation(0, -7.6f, -4.5f, droop, 0, 0));
        head.addOrReplaceChild("brim_back", CubeListBuilder.create().texOffs(0, 26).addBox(-9, 0, 0, 18, 0.6f, 5), PartPose.offsetAndRotation(0, -7.6f, 4.5f, -droop, 0, 0));
        head.addOrReplaceChild("brim_left", CubeListBuilder.create().texOffs(0, 34).addBox(0, 0, -4.5f, 5, 0.6f, 9), PartPose.offsetAndRotation(4.5f, -7.6f, 0, 0, 0, -droop));
        head.addOrReplaceChild("brim_right", CubeListBuilder.create().texOffs(0, 34).addBox(-5, 0, -4.5f, 5, 0.6f, 9), PartPose.offsetAndRotation(-4.5f, -7.6f, 0, 0, 0, droop));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public BrimhatRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(LAYER)), 0.5f);
        addLayer(new AttireLayer<>(this, WitchModels.MANTLE, tex("brimhat_mantle")));
        addLayer(new HatLayer(this, new HumanoidModel<>(ctx.bakeLayer(HAT))));
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(BrimhatEntity e) {
        return SKIN;
    }

    public static class Model extends HumanoidModel<BrimhatEntity> {
        public Model(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(BrimhatEntity e, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
            super.setupAnim(e, limbSwing, limbSwingAmount, age, yaw, pitch);

            rightArm.xRot *= 0.35f;
            leftArm.xRot *= 0.35f;
            rightArm.zRot += 0.08f;
            leftArm.zRot -= 0.08f;
            body.xRot = 0.12f * limbSwingAmount + Mth.sin(age * 0.05f) * 0.015f;
            head.y = Mth.sin(age * 0.05f) * 0.25f;
            hat.y = head.y;
            if (e.isAggressive() && e.casting() <= 0) {
                head.xRot += 0.15f;
                rightArm.xRot = -0.35f;
                rightArm.yRot = -0.2f;
            }
            int c = e.casting();
            if (c > 0) {
                float t = c / 18f;

                rightArm.xRot = -1.45f + Mth.sin(age * 0.9f) * 0.25f * t;
                rightArm.yRot = -0.15f + Mth.cos(age * 0.9f) * 0.25f * t;
                leftArm.xRot = -0.9f;
                leftArm.yRot = 0.4f;
            }
        }
    }

    private static class HatLayer extends RenderLayer<BrimhatEntity, Model> {
        private final HumanoidModel<BrimhatEntity> hat;

        HatLayer(RenderLayerParent<BrimhatEntity, Model> parent, HumanoidModel<BrimhatEntity> hat) {
            super(parent);
            this.hat = hat;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, BrimhatEntity e, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            getParentModel().copyPropertiesTo(hat);
            hat.setAllVisible(false);
            hat.head.visible = true;
            hat.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(HAT_TEX)), light, LivingEntityRenderer.getOverlayCoords(e, 0));
        }
    }
}
