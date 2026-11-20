package com.witchcraft.client;

import com.witchcraft.WitchCraft;
import com.witchcraft.client.model.WitchModels;
import com.witchcraft.entity.KnightMoralisEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** A knight of the Knights Moralis: silver-white plate under a pale mantle, a plumed helm. */
public class KnightMoralisRenderer extends HumanoidMobRenderer<KnightMoralisEntity, HumanoidModel<KnightMoralisEntity>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "knight_moralis"), "main");
    private static final ResourceLocation SKIN = BrimhatRenderer.tex("knight_moralis");

    public static final ModelLayerLocation GEAR = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "knight_moralis"), "gear");
    private static final ResourceLocation GEAR_TEX = BrimhatRenderer.tex("knight_moralis_gear");

    /** The helm's white crest and broad pauldrons. */
    public static LayerDefinition gear() {
        var mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        var root = mesh.getRoot();
        root.getChild("head").addOrReplaceChild("crest", net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.5f, -12.5f, -4.5f, 1, 5, 10), net.minecraft.client.model.geom.PartPose.ZERO);
        root.getChild("right_arm").addOrReplaceChild("pauldron", net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(0, 16)
                .addBox(-4.2f, -2.6f, -3, 5, 4, 6), net.minecraft.client.model.geom.PartPose.rotation(0, 0, 0.15f));
        root.getChild("left_arm").addOrReplaceChild("pauldron", net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(0, 16).mirror()
                .addBox(-0.8f, -2.6f, -3, 5, 4, 6), net.minecraft.client.model.geom.PartPose.rotation(0, 0, -0.15f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition layer() {
        return LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(0.0f), 0), 64, 64);
    }

    public KnightMoralisRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(LAYER)), 0.5f);
        addLayer(new AttireLayer<>(this, WitchModels.CAPELET, BrimhatRenderer.tex("knight_moralis_capelet")));
        HumanoidModel<KnightMoralisEntity> gear = new HumanoidModel<>(ctx.bakeLayer(GEAR));
        addLayer(new net.minecraft.client.renderer.entity.layers.RenderLayer<>(this) {
            @Override
            public void render(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, KnightMoralisEntity e,
                               float ls, float la, float pt, float age, float yaw, float pitch) {
                getParentModel().copyPropertiesTo(gear);
                gear.setAllVisible(true);
                gear.body.visible = false;
                gear.rightLeg.visible = gear.leftLeg.visible = false;
                gear.renderToBuffer(pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(GEAR_TEX)), light,
                        net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(e, 0));
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(KnightMoralisEntity e) {
        return SKIN;
    }

    @Override
    protected void scale(KnightMoralisEntity e, com.mojang.blaze3d.vertex.PoseStack pose, float partialTick) {
        pose.scale(1.05f, 1.05f, 1.05f);
    }

    /** A knight's bearing: a measured march, and the pennant levelled at whoever it binds. */
    public static class Model extends HumanoidModel<KnightMoralisEntity> {
        public Model(net.minecraft.client.model.geom.ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(KnightMoralisEntity e, float limbSwing, float amount, float age, float yaw, float pitch) {
            super.setupAnim(e, limbSwing, amount, age, yaw, pitch);
            // upright and stiff-backed: the off arm swings less, the body barely sways
            leftArm.xRot *= 0.6f;
            body.yRot = net.minecraft.util.Mth.sin(limbSwing * 0.6662f) * 0.05f * amount;
            if (e.bindPose > 0) {
                float t = Math.min(1, e.bindPose / 8f);
                // the pennant arm thrust forward at the target, head behind it
                leftArm.xRot = net.minecraft.util.Mth.lerp(t, leftArm.xRot, -1.5f + head.xRot);
                leftArm.yRot = net.minecraft.util.Mth.lerp(t, leftArm.yRot, 0.1f + head.yRot);
                rightArm.xRot = net.minecraft.util.Mth.lerp(t, rightArm.xRot, -0.3f);
                body.xRot = 0.08f * t;
            }
        }
    }
}
