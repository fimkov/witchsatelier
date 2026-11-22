package com.fimkov.witchsatelier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.fimkov.witchsatelier.glyph.ink.LoadedInk;
import com.fimkov.witchsatelier.item.InkPenItem;
import com.fimkov.witchsatelier.item.InkwellItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * First-person hands while holding the pen: the real arms are drawn holding the pen (and the inkwell in the other
 * hand). The writing hand reaches to the surface while drawing, trails the view a little and scratches; when the
 * nib gets new ink the hand dips into the inkwell and comes back.
 */
public final class HandsRenderer {
    // pose tuning (blocks / degrees, applied before the vanilla arm transform)
    private static final float REACH_X = -0.16f, REACH_Y = 0.10f, REACH_Z = -0.12f, REACH_PITCH = -14f;
    private static final float DIP_X = -0.52f, DIP_Y = -0.06f, DIP_DEPTH = 0.10f;
    private static final int DIP_TICKS = 14;
    // where the inkwell stands in view space (right of centre is +x; it is mirrored for the left hand)
    private static final float WELL_X = 0.43f, WELL_Y = -0.17f, WELL_Z = -0.75f, WELL_SCALE = 0.42f;

    private static int dipTick = -1;
    private static int lastNib = -1;
    private static float reach;
    private static long lastFrame;

    private HandsRenderer() {}

    static void onClientTick(ClientTickEvent.Post e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        ItemStack pen = p.getMainHandItem();
        int nib = -1;
        if (pen.getItem() instanceof InkPenItem) {
            LoadedInk l = InkPenItem.loaded(pen);
            nib = l == null ? 0 : l.amount();
        }
        if (lastNib >= 0 && nib > lastNib) dipTick = 0; // the nib was refilled: play the dip
        lastNib = nib;
        if (dipTick >= 0 && ++dipTick > DIP_TICKS) dipTick = -1;
    }

    /** Eased per frame (not per tick), so the hand glides instead of stepping. */
    private static void updateReach() {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float target = ClientDrawing.active() && !ClientDrawing.erasingNow() ? 1f : 0f;
        reach += (target - reach) * (1 - (float) Math.exp(-dt * 9));
    }

    static void onRenderHand(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isInvisible()) return;
        ItemStack main = player.getMainHandItem();
        if (!(main.getItem() instanceof InkPenItem)) return;
        ItemStack stack = e.getItemStack();
        boolean right = (e.getHand() == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT);
        HumanoidArm arm = right ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        float pt = e.getPartialTick();
        float t = player.tickCount + pt;
        PoseStack ps = e.getPoseStack();
        float side = right ? 1 : -1;

        if (e.getHand() == InteractionHand.MAIN_HAND) {
            e.setCanceled(true);
            updateReach();
            ps.pushPose();
            float breathe = Mth.sin(t * 0.08f) * 0.006f;
            float r = reach;
            // the hand trails the view a little as it turns, and the nib trembles faintly while ink flows
            float sx = ClientDrawing.swayX * r, sy = ClientDrawing.swayY * r;
            float secs = (System.nanoTime() % 1_000_000_000_000L) / 1e9f;
            float scratch = ClientDrawing.scratch * r * Mth.sin(secs * 21f) * 0.0035f;
            ps.translate(side * REACH_X * r - sx * 0.035f, REACH_Y * r + breathe + sy * 0.03f + scratch, REACH_Z * r);
            ps.mulPose(Axis.XP.rotationDegrees(REACH_PITCH * r + sy * 4f));
            ps.mulPose(Axis.YP.rotationDegrees(-sx * 5f));
            if (dipTick >= 0) {
                float d = (dipTick + pt) / DIP_TICKS;
                float go = Mth.sin(d * Mth.PI);                  // there and back
                float dunk = Math.max(0, Mth.sin(d * Mth.PI * 2 - Mth.HALF_PI)) * DIP_DEPTH; // down at the middle
                ps.translate(side * DIP_X * go, DIP_Y * go - dunk, 0);
                ps.mulPose(Axis.ZP.rotationDegrees(side * 18f * go));
            }
            renderArmWithItem(ps, e.getMultiBufferSource(), e.getPackedLight(), player, arm, stack, e.getEquipProgress());
            ps.popPose();
        } else if (stack.getItem() instanceof InkwellItem) {
            e.setCanceled(true);
            float lift = dipTick >= 0 ? Mth.sin((dipTick + pt) / DIP_TICKS * Mth.PI) : 0;
            float bob = Mth.sin(t * 0.08f + 1) * 0.005f;
            // the arm, a little lowered, with the inkwell standing upright on its palm
            ps.pushPose();
            ps.translate(side * (-0.06f - 0.06f * lift), -0.08f + 0.04f * lift + bob, 0.02f);
            renderArm(ps, e.getMultiBufferSource(), e.getPackedLight(), player, arm, e.getEquipProgress());
            ps.popPose();
            ps.pushPose();
            ps.translate(side * (WELL_X - 0.06f * lift), WELL_Y + 0.04f * lift + bob - e.getEquipProgress() * 0.6f, WELL_Z);
            ps.mulPose(Axis.YP.rotationDegrees(side * -25f));
            ps.scale(WELL_SCALE, WELL_SCALE, WELL_SCALE);
            mc.getItemRenderer().renderStatic(player, stack, ItemDisplayContext.FIXED, false, ps, e.getMultiBufferSource(),
                    player.level(), e.getPackedLight(), OverlayTexture.NO_OVERLAY, player.getId());
            ps.popPose();
        }
    }

    /** Just the vanilla first-person arm. */
    private static void renderArm(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer player, HumanoidArm arm, float equip) {
        Minecraft mc = Minecraft.getInstance();
        boolean right = arm == HumanoidArm.RIGHT;
        float f = right ? 1 : -1;
        ps.translate(f * 0.64f, -0.6f + equip * -0.6f, -0.72f);
        ps.mulPose(Axis.YP.rotationDegrees(f * 45f));
        ps.translate(f * -1.0f, 3.6f, 3.5f);
        ps.mulPose(Axis.ZP.rotationDegrees(f * 120f));
        ps.mulPose(Axis.XP.rotationDegrees(200f));
        ps.mulPose(Axis.YP.rotationDegrees(f * -135f));
        ps.translate(f * 5.6f, 0, 0);
        PlayerRenderer pr = (PlayerRenderer) mc.getEntityRenderDispatcher().<AbstractClientPlayer>getRenderer(player);
        if (right) pr.renderRightHand(ps, buffers, light, player);
        else pr.renderLeftHand(ps, buffers, light, player);
    }

    /** The vanilla first-person arm, with the held item placed in its hand the way the third-person view does. */
    private static void renderArmWithItem(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                          HumanoidArm arm, ItemStack stack, float equip) {
        Minecraft mc = Minecraft.getInstance();
        boolean right = arm == HumanoidArm.RIGHT;
        float f = right ? 1 : -1;
        ps.translate(f * 0.64f, -0.6f + equip * -0.6f, -0.72f);
        ps.mulPose(Axis.YP.rotationDegrees(f * 45f));
        ps.translate(f * -1.0f, 3.6f, 3.5f);
        ps.mulPose(Axis.ZP.rotationDegrees(f * 120f));
        ps.mulPose(Axis.XP.rotationDegrees(200f));
        ps.mulPose(Axis.YP.rotationDegrees(f * -135f));
        ps.translate(f * 5.6f, 0, 0);
        PlayerRenderer pr = (PlayerRenderer) mc.getEntityRenderDispatcher().<AbstractClientPlayer>getRenderer(player);
        if (right) pr.renderRightHand(ps, buffers, light, player);
        else pr.renderLeftHand(ps, buffers, light, player);

        PlayerModel<AbstractClientPlayer> model = pr.getModel();
        ps.pushPose();
        (right ? model.rightArm : model.leftArm).translateAndRotate(ps);
        ps.mulPose(Axis.XP.rotationDegrees(-90f));
        ps.mulPose(Axis.YP.rotationDegrees(180f));
        ps.translate(f / 16f, 0.125f, -0.625f);
        mc.getItemRenderer().renderStatic(player, stack, right ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                !right, ps, buffers, player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
        ps.popPose();
    }
}
