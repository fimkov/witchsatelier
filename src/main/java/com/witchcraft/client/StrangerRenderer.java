package com.witchcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.witchcraft.WitchCraft;
import com.witchcraft.client.model.WitchModels;
import com.witchcraft.entity.StrangerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** The stranger: a dark figure, face lost under a broad hat, only the eyes catching the light. */
public class StrangerRenderer extends HumanoidMobRenderer<StrangerEntity, HumanoidModel<StrangerEntity>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "stranger"), "main");
    private static final ResourceLocation SKIN = tex("stranger");
    private static final RenderType EYES = RenderType.eyes(tex("stranger_eyes"));

    public static LayerDefinition layer() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0), 64, 64);
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "textures/entity/" + name + ".png");
    }

    public StrangerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(LAYER)), 0.5f);
        addLayer(new Attire(this, WitchModels.MANTLE, tex("stranger_mantle")));
        addLayer(new Attire(this, WitchModels.HAT, tex("stranger_hat")));
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(StrangerEntity entity) {
        return SKIN;
    }

    @Override
    protected void scale(StrangerEntity e, PoseStack pose, float partialTick) {
        pose.scale(1.04f, 1.06f, 1.04f); // a touch taller than most
    }

    /** One of the witch's garments, in the stranger's own dark cloth. */
    private static final class Attire extends RenderLayer<StrangerEntity, HumanoidModel<StrangerEntity>> {
        private final ModelLayerLocation loc;
        private final ResourceLocation texture;
        private HumanoidModel<StrangerEntity> model;

        Attire(RenderLayerParent<StrangerEntity, HumanoidModel<StrangerEntity>> parent, ModelLayerLocation loc, ResourceLocation texture) {
            super(parent);
            this.loc = loc;
            this.texture = texture;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void render(PoseStack pose, MultiBufferSource buffers, int light, StrangerEntity e, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (model == null) model = (HumanoidModel<StrangerEntity>) WitchModels.bake(loc);
            getParentModel().copyPropertiesTo(model);
            model.setAllVisible(true);
            com.witchcraft.client.model.ClothMotion.apply(model, e, limbSwing, limbSwingAmount, ageInTicks, 0);
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light,
                    LivingEntityRenderer.getOverlayCoords(e, 0));
        }
    }

    /** The stranger stands with hands clasped behind the back, and walks unhurried. */
    public static class Model extends HumanoidModel<StrangerEntity> {
        public Model(net.minecraft.client.model.geom.ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(StrangerEntity e, float limbSwing, float amount, float age, float yaw, float pitch) {
            super.setupAnim(e, limbSwing, amount, age, yaw, pitch);
            float still = net.minecraft.util.Mth.clamp(1 - amount * 4, 0, 1);
            if (attackTime <= 0) {
                rightArm.xRot = net.minecraft.util.Mth.lerp(still, rightArm.xRot * 0.5f, 0.42f);
                leftArm.xRot = net.minecraft.util.Mth.lerp(still, leftArm.xRot * 0.5f, 0.42f);
                rightArm.zRot = net.minecraft.util.Mth.lerp(still, rightArm.zRot, 0.32f);
                leftArm.zRot = net.minecraft.util.Mth.lerp(still, leftArm.zRot, -0.32f);
            }
            head.xRot += still * net.minecraft.util.Mth.sin(age * 0.03f) * 0.05f;
        }
    }
}
