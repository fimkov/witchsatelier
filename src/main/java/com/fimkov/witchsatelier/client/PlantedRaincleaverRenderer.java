package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.block.PlantedRaincleaverBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;

/** The planted Raincleaver: the sword standing point-down in the ground. */
public class PlantedRaincleaverRenderer implements BlockEntityRenderer<PlantedRaincleaverBlock.Entity> {
    public PlantedRaincleaverRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(PlantedRaincleaverBlock.Entity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.sword().isEmpty()) return;
        ps.pushPose();
        ps.translate(0.5, 0.62, 0.5);
        ps.mulPose(Axis.ZP.rotationDegrees(135)); // the item is drawn tip up-right: turned so the tip points down
        ps.scale(1.1f, 1.1f, 1.1f);
        Minecraft.getInstance().getItemRenderer().renderStatic(be.sword(), ItemDisplayContext.FIXED, light, overlay, ps, buffers, be.getLevel(), 0);
        ps.popPose();
    }
}
