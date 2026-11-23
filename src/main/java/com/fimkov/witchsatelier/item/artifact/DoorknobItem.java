package com.fimkov.witchsatelier.item.artifact;

import com.fimkov.witchsatelier.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/** A brass doorknob from a bonded pair. Clicked onto a door it makes the door lead to the door wearing its partner. */
public class DoorknobItem extends Item {
    public DoorknobItem(Properties props) {
        super(props);
    }

    public static UUID bond(ItemStack s) {
        return s.get(ModComponents.BOND.get());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (bond(stack) != null || stack.getCount() != 2) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            stack.set(ModComponents.BOND.get(), UUID.randomUUID());
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1f, 1.2f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        if (bond(stack) == null || ctx.getPlayer() == null || !Doorknobs.isDoor(ctx.getLevel().getBlockState(ctx.getClickedPos())))
            return InteractionResult.PASS;
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        return Doorknobs.attach(level, ctx.getPlayer(), ctx.getClickedPos(), stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return bond(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        if (Awakening.dormant(stack)) return;
        lines.add(Component.translatable(bond(stack) == null ? "item.witchsatelier.doorknob.unbound" : "item.witchsatelier.doorknob.bound")
                .withStyle(ChatFormatting.GRAY));
    }
}
