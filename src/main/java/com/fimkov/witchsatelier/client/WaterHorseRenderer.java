package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.entity.WaterHorseEntity;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class WaterHorseRenderer extends AbstractHorseRenderer<WaterHorseEntity, HorseModel<WaterHorseEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "textures/entity/water_horse.png");

    public WaterHorseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HorseModel<>(ctx.bakeLayer(ModelLayers.HORSE)), 1.1f);
    }

    @Override
    public ResourceLocation getTextureLocation(WaterHorseEntity entity) {
        return TEXTURE;
    }

    @Override
    protected RenderType getRenderType(WaterHorseEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(TEXTURE);
    }
}
