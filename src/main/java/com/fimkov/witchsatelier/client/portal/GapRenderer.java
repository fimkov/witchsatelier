package com.fimkov.witchsatelier.client.portal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.fimkov.witchsatelier.windowway.WindowwayGapBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

public final class GapRenderer implements BlockEntityRenderer<WindowwayGapBlockEntity> {
    public GapRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(WindowwayGapBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState st = be.original();
        if (st == null || st.getRenderShape() != RenderShape.MODEL) return;
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(st, pose, buffers, faceLight(be, light), overlay, ModelData.EMPTY, null);
    }

    /**
     * The light the stand-in shows: the brightest of the open spaces round it on the side the eye is on, as the face
     * the wall showed before was lit by the open air in front of it. Its own place is inside the wall, and drew it
     * darker than the bricks beside it; and a lit room behind the wall must not light its front.
     */
    private static int faceLight(WindowwayGapBlockEntity be, int light) {
        var level = be.getLevel();
        if (level == null) return light;
        var camera = Minecraft.getInstance().getBlockEntityRenderDispatcher().camera;
        Vec3 eye = camera == null ? null : camera.getPosition().subtract(Vec3.atCenterOf(be.getBlockPos()));
        int block = LightTexture.block(light), sky = LightTexture.sky(light);
        for (Direction d : Direction.values()) {
            BlockPos p = be.getBlockPos().relative(d);
            if (eye != null && eye.x * d.getStepX() + eye.y * d.getStepY() + eye.z * d.getStepZ() <= 0.5) continue;
            if (level.getBlockState(p).isSolidRender(level, p)) continue;
            int l = LevelRenderer.getLightColor(level, p);
            block = Math.max(block, LightTexture.block(l));
            sky = Math.max(sky, LightTexture.sky(l));
        }
        return LightTexture.pack(block, sky);
    }
}
