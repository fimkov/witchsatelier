package com.fimkov.witchsatelier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.fimkov.witchsatelier.WitchsAtelier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

public final class PennantItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation POLE = ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "textures/entity/capture_pennant_pole.png");
    private static final ResourceLocation FLAG = ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "textures/entity/capture_pennant_flag.png");

    private static final float SILVER = 0f, GOLD = 0.25f, CORD = 0.5f;

    public PennantItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(false);
        double time = (mc.level != null ? mc.level.getGameTime() : 0) + pt;
        float unfurled = 1;
        if (mc.player != null && (mc.player.getMainHandItem() == stack || mc.player.getOffhandItem() == stack))
            unfurled = 1 - mc.player.getCooldowns().getCooldownPercent(stack.getItem(), pt);

        ps.pushPose();

        ps.translate(0.22, 0.22, 0.5);
        if (ctx.firstPerson()) {
            ps.mulPose(Axis.ZP.rotationDegrees(55));
            ps.scale(0.45f, 0.45f, 0.45f);
        }
        ps.mulPose(Axis.ZP.rotationDegrees(-45));
        VertexConsumer pole = buffers.getBuffer(RenderType.entityCutoutNoCull(POLE));
        PoseStack.Pose pose = ps.last();
        box(pole, pose, 0.032f, -0.30f, 1.46f, SILVER, light, overlay);
        box(pole, pose, 0.038f, -0.02f, 0.26f, CORD, light, overlay);
        box(pole, pose, 0.044f, -0.37f, -0.30f, GOLD, light, overlay);
        box(pole, pose, 0.052f, 1.44f, 1.50f, GOLD, light, overlay);
        box(pole, pose, 0.036f, 1.50f, 1.53f, GOLD, light, overlay);

        for (int k = 0; k < 2; k++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(k * 90));
            blade(pole, ps.last(), light, overlay);
            ps.popPose();
        }
        DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
        int tint = dyed == null ? -1 : 0xFF000000 | dyed.rgb();
        if (unfurled > 0.04f) flag(buffers.getBuffer(RenderType.entityCutoutNoCull(FLAG)), pose, time, unfurled, tint, light, overlay);
        ps.popPose();
    }

    private static void box(VertexConsumer vc, PoseStack.Pose p, float h, float y0, float y1, float u, int light, int overlay) {
        float u0 = u, u1 = u + 0.25f, v1 = Math.min(1, (y1 - y0) * 2);

        quad(vc, p, -h, y0, h, h, y0, h, h, y1, h, -h, y1, h, u0, u1, 0, v1, 0, 0.3f, 1, light, overlay);
        quad(vc, p, h, y0, -h, -h, y0, -h, -h, y1, -h, h, y1, -h, u0, u1, 0, v1, 0, 0.3f, 1, light, overlay);
        quad(vc, p, h, y0, h, h, y0, -h, h, y1, -h, h, y1, h, u0, u1, 0, v1, 0.4f, 0.3f, 0.85f, light, overlay);
        quad(vc, p, -h, y0, -h, -h, y0, h, -h, y1, h, -h, y1, -h, u0, u1, 0, v1, -0.4f, 0.3f, 0.85f, light, overlay);
        quad(vc, p, -h, y1, h, h, y1, h, h, y1, -h, -h, y1, -h, u0, u1, 0, 0.1f, 0, 1, 0, light, overlay);
        quad(vc, p, -h, y0, -h, h, y0, -h, h, y0, h, -h, y0, h, u0, u1, 0, 0.1f, 0, 0.3f, 1, light, overlay);
    }

    private static void blade(VertexConsumer vc, PoseStack.Pose p, int light, int overlay) {
        float b = 1.53f, w = 1.61f, t = 1.86f, hw = 0.07f;

        quad(vc, p, 0, b, 0, hw, w, 0, 0, t, 0, -hw, w, 0, GOLD, GOLD + 0.25f, 0, 0.5f, 0, 0, 1, light, overlay);
        quad(vc, p, -hw, w, 0, 0, t, 0, hw, w, 0, 0, b, 0, GOLD, GOLD + 0.25f, 0, 0.5f, 0, 0.3f, 1, light, overlay);
    }

    private static void flag(VertexConsumer vc, PoseStack.Pose p, double time, float unfurled, int tint, int light, int overlay) {
        int n = 14;
        float len = 1.05f * unfurled, top = 1.42f, bottom = 0.98f;
        float[] x = new float[n + 1], z = new float[n + 1], yt = new float[n + 1], yb = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float s = (float) i / n, d = s * len;
            float amp = 0.015f + 0.085f * s * s;
            x[i] = 0.034f + d * (1 - 0.06f * s);
            z[i] = amp * Mth.sin((float) (d * 6.5 - time * 0.32));
            float sag = 0.06f * s * s;
            yt[i] = top - 0.05f * d - sag + 0.012f * Mth.sin((float) (d * 4 - time * 0.21));
            yb[i] = bottom + 0.07f * d - sag;
        }
        for (int i = 0; i < n; i++) {
            float u0 = (float) i / n * unfurled + (1 - unfurled), u1 = (float) (i + 1) / n * unfurled + (1 - unfurled);

            float dx = x[i + 1] - x[i], dz = z[i + 1] - z[i];
            float l = Mth.sqrt(dx * dx + dz * dz);
            float nx = -dz / l, nz = Math.abs(dx / l);
            quad(vc, p, x[i], yb[i], z[i], x[i + 1], yb[i + 1], z[i + 1], x[i + 1], yt[i + 1], z[i + 1], x[i], yt[i], z[i],
                    u0, u1, 1, 0, nx, 0, nz, tint, light, overlay);
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float u1, float v0, float v1, float nx, float ny, float nz, int light, int overlay) {
        quad(vc, p, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, u0, u1, v0, v1, nx, ny, nz, -1, light, overlay);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float u1, float v0, float v1, float nx, float ny, float nz, int color, int light, int overlay) {
        vc.addVertex(p, ax, ay, az).setColor(color).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
        vc.addVertex(p, bx, by, bz).setColor(color).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
        vc.addVertex(p, cx, cy, cz).setColor(color).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
        vc.addVertex(p, dx, dy, dz).setColor(color).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
    }
}
