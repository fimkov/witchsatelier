package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.block.StatueBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

public class StatueRenderer implements BlockEntityRenderer<StatueBlock.Entity> {
    private static final ResourceLocation STONE = ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "textures/entity/statue_crystal.png");

    public StatueRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(StatueBlock.Entity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (be.data() == null || mc.level == null) return;
        if (be.shown == null) {
            be.shown = EntityType.create(be.data(), mc.level).orElse(null);
            if (be.shown == null) return;
            be.shown.setYRot(be.yaw());
            be.shown.yRotO = be.yaw();
            be.shown.setXRot(0);
            be.shown.setCustomNameVisible(false);
            if (be.shown instanceof LivingEntity le) {
                le.yBodyRot = le.yBodyRotO = le.yHeadRot = le.yHeadRotO = be.yaw();
                le.hurtTime = 0;
                le.deathTime = 0;
            }
        }
        EntityRenderDispatcher d = mc.getEntityRenderDispatcher();

        MultiBufferSource stone = type -> buffers.getBuffer(RenderType.entityCutoutNoCull(STONE));
        boolean shadow = mc.options.entityShadows().get();
        d.setRenderShadow(false);
        try {
            d.render(be.shown, 0.5, 0, 0.5, be.yaw(), 0, pose, stone, LightTexture.FULL_BRIGHT);
        } finally {
            d.setRenderShadow(shadow);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(StatueBlock.Entity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(StatueBlock.Entity be) {
        return new AABB(be.getBlockPos()).inflate(1, 2, 1);
    }
}
