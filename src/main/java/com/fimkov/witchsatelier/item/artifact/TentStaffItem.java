package com.fimkov.witchsatelier.item.artifact;

import com.fimkov.witchsatelier.util.TimedBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Ins-tent Staff (Tetia's): hook it into the ground and lift, and a layer of the earth rises into a tent around
 * you, the staff holding it up. It settles back down after a few minutes. Made so that people who are not
 * witches can camp safely too.
 */
public class TentStaffItem extends Item {
    private static final int R = 3, TICKS = 20 * 60 * 4;

    public TentStaffItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getClickedFace() != Direction.UP || ctx.getPlayer() == null || ctx.getPlayer().getCooldowns().isOnCooldown(this)) return InteractionResult.PASS;
        if (!(ctx.getLevel() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        BlockPos ground = ctx.getClickedPos();
        BlockState earth = sl.getBlockState(ground);
        if (!(earth.is(BlockTags.DIRT) || earth.is(BlockTags.SAND) || earth.is(Blocks.GRAVEL) || earth.is(BlockTags.BASE_STONE_OVERWORLD))) earth = Blocks.PACKED_MUD.defaultBlockState();
        if (earth.is(Blocks.GRASS_BLOCK)) earth = Blocks.DIRT.defaultBlockState();
        Direction door = ctx.getHorizontalDirection().getOpposite();
        BlockPos c = ground.above();
        int placed = 0;
        for (int dx = -R; dx <= R; dx++) for (int dy = 0; dy <= R; dy++) for (int dz = -R; dz <= R; dz++) {
            double d = Math.sqrt(dx * dx + dy * dy * 1.3 + dz * dz);
            if (d < R - 0.6 || d > R + 0.4) continue;
            // a doorway on the side the witch came in from
            if (dy <= 1 && door.getStepX() * dx + door.getStepZ() * dz >= R - 1 && Math.abs(door.getStepX() == 0 ? dx : dz) <= 0) continue;
            BlockPos p = c.offset(dx, dy, dz);
            if (!sl.getBlockState(p).canBeReplaced()) continue;
            TimedBlocks.set(sl, p, earth, TICKS + sl.random.nextInt(60));
            if (placed++ % 4 == 0) sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, earth), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
        }
        // the staff itself holds the middle up
        TimedBlocks.set(sl, c, Blocks.OAK_FENCE.defaultBlockState(), TICKS);
        TimedBlocks.set(sl, c.above(), Blocks.OAK_FENCE.defaultBlockState(), TICKS);
        sl.playSound(null, c, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
        sl.playSound(null, c, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1f, 0.5f);
        ctx.getItemInHand().hurtAndBreak(1, ctx.getPlayer(), ctx.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        ctx.getPlayer().getCooldowns().addCooldown(this, 20 * 60);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.witchsatelier.tent_staff.desc").withStyle(ChatFormatting.GRAY));
    }
}
