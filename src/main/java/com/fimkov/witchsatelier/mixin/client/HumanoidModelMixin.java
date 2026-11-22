package com.fimkov.witchsatelier.mixin.client;

import com.fimkov.witchsatelier.client.fx.SpellFxClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void witchsatelier$soaring(T entity, float limbSwing, float amount, float age, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player) || !SpellFxClient.soaring(entity)) return;

        double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
        double yaw = Math.toRadians(entity.yBodyRot);
        double forward = -dx * Math.sin(yaw) + dz * Math.cos(yaw);
        double right = -(dx * Math.cos(yaw) + dz * Math.sin(yaw));
        float turn = Mth.wrapDegrees(entity.yBodyRot - entity.yBodyRotO);

        float trail = (float) Mth.clamp(forward * 1.6, -0.25, 0.7);
        float lean = (float) Mth.clamp(right * 1.8 + turn * 0.025, -0.45, 0.45);

        float bob = (float) Math.sin(age * 0.22f);
        float tuck = 0.18f + trail + 0.08f * bob;
        leftLeg.xRot = tuck;
        rightLeg.xRot = tuck + 0.05f * (float) Math.sin(age * 0.22f + 1.2f);
        leftLeg.yRot = 0;
        rightLeg.yRot = 0;
        leftLeg.zRot = -0.03f + lean;
        rightLeg.zRot = 0.03f + lean;
        float sway = (float) Math.sin(age * 0.12f) * 0.06f;

        leftArm.zRot = -0.5f - sway - Math.max(0, lean) * 0.6f;
        rightArm.zRot = 0.5f + sway - Math.min(0, lean) * 0.6f;
        leftArm.xRot = -0.1f;
        rightArm.xRot = -0.1f;
    }
}
