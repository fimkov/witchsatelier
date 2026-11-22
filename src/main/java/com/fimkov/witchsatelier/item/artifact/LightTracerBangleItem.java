package com.fimkov.witchsatelier.item.artifact;

import com.fimkov.witchsatelier.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LightTracerBangleItem extends Item {
    private static final Map<UUID, Map<UUID, GlobalPos>> SEEN = new HashMap<>();

    public LightTracerBangleItem(Properties props) {
        super(props);
    }

    private static UUID bond(ItemStack s) {
        return s.get(ModComponents.BOND.get());
    }

    private static UUID self(ItemStack s) {
        UUID id = s.get(ModComponents.SELF_ID.get());
        if (id == null) {
            id = UUID.randomUUID();
            s.set(ModComponents.SELF_ID.get(), id);
        }
        return id;
    }

    private static void seen(ItemStack s, Level level, Vec3 at) {
        UUID b = bond(s);
        if (b == null || level.isClientSide) return;
        SEEN.computeIfAbsent(b, k -> new HashMap<>()).put(self(s), GlobalPos.of(level.dimension(), BlockPos.containing(at)));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.getGameTime() % 20 == 0) seen(stack, level, entity.position());
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.tickCount % 20 == 0) seen(stack, entity.level(), entity.position());
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (bond(stack) == null) {
            if (stack.getCount() < 2) return InteractionResultHolder.fail(stack);

            if (!level.isClientSide) {
                stack.set(ModComponents.BOND.get(), UUID.randomUUID());
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1f, 1.4f);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel sl) || remaining % 5 != 0) return;
        UUID b = bond(stack);
        if (b == null) return;
        seen(stack, level, user.position());
        UUID me = self(stack);
        GlobalPos best = null;
        double bestD = Double.MAX_VALUE;
        for (Map.Entry<UUID, GlobalPos> e : SEEN.getOrDefault(b, Map.of()).entrySet()) {
            if (e.getKey().equals(me) || !e.getValue().dimension().equals(level.dimension())) continue;
            double d = e.getValue().pos().distToCenterSqr(user.position());
            if (d < bestD) { bestD = d; best = e.getValue(); }
        }
        Vec3 from = user.getEyePosition().add(user.getLookAngle().scale(0.6)).subtract(0, 0.35, 0);
        if (best == null) {
            sl.sendParticles(new DustParticleOptions(new Vector3f(0.8f, 0.7f, 1f), 0.6f), from.x, from.y, from.z, 4, 0.1, 0.1, 0.1, 0.01);
            return;
        }
        Vec3 to = Vec3.atCenterOf(best.pos());
        Vec3 dir = to.subtract(from);
        double dist = dir.length();
        dir = dir.normalize();

        double reach = Math.min(dist, 20);
        double t0 = level.getGameTime() * 0.3;
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        for (double d = 0.3; d < reach; d += 0.35) {
            Vec3 at = from.add(dir.scale(d)).add(side.scale(Math.sin(d * 0.8 + t0) * 0.12)).add(0, Math.cos(d * 0.6 + t0) * 0.08, 0);
            float fade = (float) (1 - d / 22);
            sl.sendParticles(new DustParticleOptions(new Vector3f(0.75f + 0.25f * fade, 0.65f + 0.3f * fade, 1f), 0.55f + 0.4f * fade),
                    at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        if (remaining % 20 == 0) level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5f, 1.8f);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return bond(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(bond(stack) == null ? "item.witchsatelier.light_tracer_bangle.unbound" : "item.witchsatelier.light_tracer_bangle.bound")
                .withStyle(ChatFormatting.GRAY));
    }
}
