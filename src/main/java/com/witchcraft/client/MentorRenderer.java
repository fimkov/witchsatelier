package com.witchcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.witchcraft.WitchCraft;
import com.witchcraft.client.model.ClothMotion;
import com.witchcraft.client.model.WitchModels;
import com.witchcraft.entity.MentorEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Mistress Wren: a tall witch in a sea-green mantle and pointed hat, a pen always somewhere about her. */
public class MentorRenderer extends HumanoidMobRenderer<MentorEntity, HumanoidModel<MentorEntity>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "mentor"), "main");
    private static final ResourceLocation SKIN = tex("mentor");

    public static LayerDefinition layer() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0), 64, 64);
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "textures/entity/" + name + ".png");
    }

    public MentorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(LAYER)), 0.5f);
        addLayer(new Garment(this, WitchModels.MANTLE, tex("mentor_mantle")));
        addLayer(new Garment(this, WitchModels.HAT, tex("mentor_hat")));
    }

    @Override
    public ResourceLocation getTextureLocation(MentorEntity e) {
        return SKIN;
    }

    /** A teacher's patience: hands folded in front when still, a slow look about. */
    static class Model extends HumanoidModel<MentorEntity> {
        Model(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(MentorEntity e, float limbSwing, float amount, float age, float yaw, float pitch) {
            super.setupAnim(e, limbSwing, amount, age, yaw, pitch);
            float still = Mth.clamp(1 - amount * 4, 0, 1);
            rightArm.xRot = Mth.lerp(still, rightArm.xRot * 0.6f, -0.55f);
            leftArm.xRot = Mth.lerp(still, leftArm.xRot * 0.6f, -0.5f);
            rightArm.yRot = Mth.lerp(still, rightArm.yRot, -0.45f);
            leftArm.yRot = Mth.lerp(still, leftArm.yRot, 0.45f);
            body.xRot = Mth.sin(age * 0.04f) * 0.012f;
        }
    }

    private static class Garment extends RenderLayer<MentorEntity, HumanoidModel<MentorEntity>> {
        private final ModelLayerLocation loc;
        private final ResourceLocation texture;
        private HumanoidModel<MentorEntity> model;

        Garment(RenderLayerParent<MentorEntity, HumanoidModel<MentorEntity>> parent, ModelLayerLocation loc, ResourceLocation texture) {
            super(parent);
            this.loc = loc;
            this.texture = texture;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void render(PoseStack pose, MultiBufferSource buffers, int light, MentorEntity e, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (model == null) model = (HumanoidModel<MentorEntity>) WitchModels.bake(loc);
            getParentModel().copyPropertiesTo(model);
            model.setAllVisible(true);
            ClothMotion.apply(model, e, limbSwing, limbSwingAmount, ageInTicks, 0);
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, LivingEntityRenderer.getOverlayCoords(e, 0));
        }
    }
}
