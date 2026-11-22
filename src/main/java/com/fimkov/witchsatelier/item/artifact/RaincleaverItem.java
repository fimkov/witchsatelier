package com.fimkov.witchsatelier.item.artifact;

import com.fimkov.witchsatelier.block.PlantedRaincleaverBlock;
import com.fimkov.witchsatelier.util.TimedBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class RaincleaverItem extends SwordItem {
    public RaincleaverItem(Tier tier, Properties props) {
        super(tier, props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        if (ctx.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        ItemStack stack = ctx.getItemInHand();
        if (!PlantedRaincleaverBlock.plant(ctx.getLevel(), at, stack.copyWithCount(1))) return InteractionResult.FAIL;
        if (!ctx.getLevel().isClientSide) stack.shrink(1);
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(stack);
        if (level instanceof ServerLevel sl) {
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
            Vec3 side = new Vec3(-flat.z, 0, flat.x);
            int cut = 0;
            for (int d = 1; d <= 10; d++) {
                for (int w = -1; w <= 1; w++) {
                    for (int h = -3; h <= 2; h++) {
                        BlockPos p = BlockPos.containing(player.position().add(flat.scale(d)).add(side.scale(w)).add(0, h + 0.5, 0));
                        BlockState s = sl.getBlockState(p);
                        if (s.getFluidState().isEmpty() || !(s.getBlock() instanceof LiquidBlock)) continue;
                        TimedBlocks.set(sl, p, Blocks.AIR.defaultBlockState(), 20 * 8 + d * 2);
                        if (h == 0 || cut % 3 == 0) sl.sendParticles(ParticleTypes.SPLASH, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.1);
                        cut++;
                    }
                }
            }
            sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.7f);
            if (cut > 0) {
                sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1f, 0.8f);
                stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }
        player.swing(hand, true);
        player.getCooldowns().addCooldown(this, 30);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.witchsatelier.raincleaver.desc").withStyle(ChatFormatting.GRAY));
    }
}
