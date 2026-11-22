package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.item.InkPenItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public final class PenAnimation implements IClientItemExtensions {
    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        if (entity.isUsingItem() && entity.getUsedItemHand() == hand && stack.getItem() instanceof InkPenItem) {
            return HumanoidModel.ArmPose.BRUSH;
        }
        return null;
    }
}
