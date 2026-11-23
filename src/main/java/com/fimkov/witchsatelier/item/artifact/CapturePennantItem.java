package com.fimkov.witchsatelier.item.artifact;

import com.fimkov.witchsatelier.network.Net;
import com.fimkov.witchsatelier.network.Payloads;
import com.fimkov.witchsatelier.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class CapturePennantItem extends Item {
    public static final int BIND_TICKS = 100;

    public CapturePennantItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(stack);
        if (level instanceof ServerLevel sl) {
            Vec3 eye = player.getEyePosition(), look = player.getLookAngle(), end = eye.add(look.scale(16));
            var wall = level.clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            if (wall.getType() != net.minecraft.world.phys.HitResult.Type.MISS) end = wall.getLocation();
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end,
                    new AABB(eye, end).inflate(1.5), e -> e instanceof LivingEntity && e != player && e.isAlive(), 0.6f);
            Vec3 to = hit != null ? hit.getLocation() : end;

            Net.toNear(sl, player.position(), 96, new Payloads.PennantFx(player.getId(),
                    hit != null ? hit.getEntity().getId() : -1, (float) to.x, (float) to.y, (float) to.z, BIND_TICKS));
            sl.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.KNIGHT_BIND.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
            if (hit != null && hit.getEntity() instanceof LivingEntity t) {
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BIND_TICKS, 5));
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, BIND_TICKS, 2));
                t.addEffect(new MobEffectInstance(MobEffects.GLOWING, BIND_TICKS, 0));
                if (t instanceof Mob m) m.setTarget(null);
                stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }
        player.swing(hand, true);
        player.getCooldowns().addCooldown(this, 80);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity.isOnFire() && level.getGameTime() % 10 == 0 && entity instanceof LivingEntity le) {
            stack.hurtAndBreak(8, le, EquipmentSlot.MAINHAND);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.6f);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.witchsatelier.capture_pennant.desc").withStyle(ChatFormatting.GRAY));
    }
}
