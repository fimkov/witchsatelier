package com.fimkov.witchsatelier.mixin.client;

import com.fimkov.witchsatelier.client.fx.SpellFxClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flying on sylph shoes the legs do not walk: they hang together, toes back, borne up in a slow soft rhythm,
 * trailing behind as it flies and swinging out of turns; the arms are held a little out, for balance.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void witchsatelier$soaring(T entity, float limbSwing, float amount, float age, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player) || !SpellFxClient.soaring(entity)) return;
        // how it is moving, as the body faces: forward, and to its right; and how fast it is turning
        double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
        double yaw = Math.toRadians(entity.yBodyRot);
        double forward = -dx * Math.sin(yaw) + dz * Math.cos(yaw);
        double right = -(dx * Math.cos(yaw) + dz * Math.sin(yaw));
        float turn = net.minecraft.util.Mth.wrapDegrees(entity.yBodyRot - entity.yBodyRotO);
        // the legs trail behind the way it goes, and swing out of a turn or a sidestep (left behind, as it were)
        float trail = (float) net.minecraft.util.Mth.clamp(forward * 1.6, -0.25, 0.7);
        float lean = (float) net.minecraft.util.Mth.clamp(right * 1.8 + turn * 0.025, -0.45, 0.45);
        // and the little cloud bears them up in a slow, soft rhythm
        float bob = (float) Math.sin(age * 0.22f);
        float tuck = 0.18f + trail + 0.08f * bob;
        leftLeg.xRot = tuck;
        rightLeg.xRot = tuck + 0.05f * (float) Math.sin(age * 0.22f + 1.2f);
        leftLeg.yRot = 0;
        rightLeg.yRot = 0;
        leftLeg.zRot = -0.03f + lean;
        rightLeg.zRot = 0.03f + lean;
        float sway = (float) Math.sin(age * 0.12f) * 0.06f;
        // arms out for balance, the one outside the turn a little higher
        leftArm.zRot = -0.5f - sway - Math.max(0, lean) * 0.6f;
        rightArm.zRot = 0.5f + sway - Math.min(0, lean) * 0.6f;
        leftArm.xRot = -0.1f;
        rightArm.xRot = -0.1f;
    }
}
