package com.witchcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.witchcraft.item.InkPenItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * How the pen looks while drawing. First person: the hand reaches forward, tilts the nib onto the surface and
 * trails a little behind the view as it turns, with a faint scratching wobble while ink flows.
 * Third person: the arm is held out like a brush.
 */
public final class PenAnimation implements IClientItemExtensions {
    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        if (entity.isUsingItem() && entity.getUsedItemHand() == hand && stack.getItem() instanceof InkPenItem) {
            return HumanoidModel.ArmPose.BRUSH;
        }
        return null;
    }

}
