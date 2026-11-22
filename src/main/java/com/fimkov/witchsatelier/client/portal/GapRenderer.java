package com.fimkov.witchsatelier.client.portal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.fimkov.witchsatelier.windowway.WindowwayGapBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The wall a windowway has stepped aside still looks like wall: only the disc of the window shows the way through.
 * (The opening is cut in whole blocks and so runs a little past the disc's round edge; seen as it was, nobody can
 * tell.) From the far side, looking out of the window, the whole block lies behind the cut and is not drawn.
 */
public final class GapRenderer implements BlockEntityRenderer<WindowwayGapBlockEntity> {
    public GapRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(WindowwayGapBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState st = be.original();
        if (st == null || st.getRenderShape() != RenderShape.MODEL) return;
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(st, pose, buffers, light, overlay, ModelData.EMPTY, null);
    }
}
