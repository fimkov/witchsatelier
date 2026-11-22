package com.fimkov.witchsatelier.item.artifact;

import com.fimkov.witchsatelier.util.TimedBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Raincleaver: a sword whose blade is covered in countless tiny linked glyphs, so it cuts through any liquid it
 * meets. Swing it at water or lava (use) and a cleft opens along the stroke, wide enough to walk through, that
 * closes again after a while.
 */
public class RaincleaverItem extends SwordItem {
    public RaincleaverItem(Tier tier, Properties props) {
        super(tier, props);
    }

    /** Sneak and use it on the ground: it is planted point-down there and keeps the water back round it. */
    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return net.minecraft.world.InteractionResult.PASS;
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        if (ctx.getClickedFace() != net.minecraft.core.Direction.UP) return net.minecraft.world.InteractionResult.PASS;
        ItemStack stack = ctx.getItemInHand();
        if (!com.fimkov.witchsatelier.block.PlantedRaincleaverBlock.plant(ctx.getLevel(), at, stack.copyWithCount(1))) return net.minecraft.world.InteractionResult.FAIL;
        if (!ctx.getLevel().isClientSide) stack.shrink(1);
        return net.minecraft.world.InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
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
                        if (s.getFluidState().isEmpty() || !(s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)) continue;
                        TimedBlocks.set(sl, p, Blocks.AIR.defaultBlockState(), 20 * 8 + d * 2);
                        if (h == 0 || cut % 3 == 0) sl.sendParticles(ParticleTypes.SPLASH, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.1);
                        cut++;
                    }
                }
            }
            sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.7f);
            if (cut > 0) {
                sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1f, 0.8f);
                stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
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
