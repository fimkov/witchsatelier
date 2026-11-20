package com.witchcraft.item.artifact;

import com.witchcraft.util.TimedBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Vapor Bubble: two trays with a water glyph in the lower one. Pull them apart and the moisture of the air
 * gathers into a big bubble of water: pour it out where you point (it soaks away again after a while) or into
 * an empty bottle you carry. It needs a little while to gather again.
 */
public class VaporBubbleItem extends Item {
    public VaporBubbleItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player p = ctx.getPlayer();
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        if (p == null || p.getCooldowns().isOnCooldown(this)) return InteractionResult.PASS;
        if (!level.getBlockState(at).canBeReplaced()) return InteractionResult.FAIL;
        if (level instanceof ServerLevel sl) {
            if (sl.dimensionType().ultraWarm()) {
                sl.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
                sl.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.4f);
            } else {
                TimedBlocks.set(sl, at, Blocks.WATER.defaultBlockState(), 20 * 30);
                sl.sendParticles(ParticleTypes.SPLASH, at.getX() + 0.5, at.getY() + 0.9, at.getZ() + 0.5, 20, 0.3, 0.1, 0.3, 0.1);
                sl.playSound(null, at, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1f, 1.1f);
            }
            ctx.getItemInHand().hurtAndBreak(1, p, ctx.getHand() == InteractionHand.MAIN_HAND
                    ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }
        p.getCooldowns().addCooldown(this, 100);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Used in the air: the bubble goes into an empty bottle, if you have one. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack self = p.getItemInHand(hand);
        int slot = p.getInventory().findSlotMatchingItem(new ItemStack(Items.GLASS_BOTTLE));
        if (slot < 0 || p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(self);
        if (!level.isClientSide) {
            p.getInventory().getItem(slot).shrink(1);
            ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            if (!p.getInventory().add(water)) p.drop(water, false);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1f, 1f);
            if (level instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.BUBBLE_POP, p.getX(), p.getEyeY() - 0.3, p.getZ(), 10, 0.2, 0.2, 0.2, 0.02);
        }
        p.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.sidedSuccess(self, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.witchcraft.vapor_bubble.desc").withStyle(ChatFormatting.GRAY));
    }
}
