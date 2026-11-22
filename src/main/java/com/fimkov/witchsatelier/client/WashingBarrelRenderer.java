package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.block.sealed.SealedBlock;
import com.fimkov.witchsatelier.block.sealed.WashingBarrelBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** What lies in a Washing Barrel, turning slowly over the water while the seal works on it. */
public class WashingBarrelRenderer implements BlockEntityRenderer<WashingBarrelBlock.Entity> {
    public WashingBarrelRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(WashingBarrelBlock.Entity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        ItemStack held = be.held();
        if (held.isEmpty() || be.getLevel() == null) return;
        boolean working = be.getBlockState().getValue(SealedBlock.LIT) && held.isDamaged();
        float t = be.getLevel().getGameTime() + pt;
        ps.pushPose();
        ps.translate(0.5, 1.12 + (working ? Math.sin(t * 0.12) * 0.04 : 0), 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(working ? t * 2.5f : 20));
        ps.scale(0.55f, 0.55f, 0.55f);
        Minecraft.getInstance().getItemRenderer().renderStatic(held, ItemDisplayContext.FIXED, light, overlay, ps, buffers, be.getLevel(), 0);
        ps.popPose();
    }
}
