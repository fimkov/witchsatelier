package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.client.model.ClothMotion;
import com.fimkov.witchsatelier.client.model.WitchModels;
import com.fimkov.witchsatelier.entity.BrimhatEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public class AttireLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private final ModelLayerLocation loc;
    private final ResourceLocation texture;
    private HumanoidModel<T> model;

    public AttireLayer(RenderLayerParent<T, M> parent, ModelLayerLocation loc, ResourceLocation texture) {
        super(parent);
        this.loc = loc;
        this.texture = texture;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T e, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (model == null) model = (HumanoidModel<T>) WitchModels.bake(loc);
        getParentModel().copyPropertiesTo(model);
        model.setAllVisible(true);
        float flare = e instanceof BrimhatEntity b ? Math.min(1, b.casting() / 18f) : 0;
        ClothMotion.apply(model, e, limbSwing, limbSwingAmount, ageInTicks, flare);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, LivingEntityRenderer.getOverlayCoords(e, 0));
    }
}
