package com.fimkov.witchsatelier.item.attire;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.item.artifact.Awakening;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

import java.util.List;

public class WitchAttireItem extends ArmorItem {
    public enum Style {
        APPRENTICE_HAT(Type.HELMET, "apprentice_hat"),
        WITCH_HAT(Type.HELMET, "witch_hat"),
        ROBE(Type.CHESTPLATE, "witch_capelet"),
        MANTLE(Type.CHESTPLATE, "witch_mantle"),

        MIRROR_MANTLE(Type.CHESTPLATE, "mirror_mantle"),

        BRIMHAT_MANTLE(Type.CHESTPLATE, "brimhat_mantle"),

        WINGCLOAK(Type.CHESTPLATE, "wingcloak"),
        BOOTS(Type.BOOTS, null);

        public final Type type;

        public final String texture;

        Style(Type type, String texture) {
            this.type = type;
            this.texture = texture;
        }
    }

    public final Style style;

    public WitchAttireItem(Style style, Properties props) {
        super(WitchAttire.material(), style.type, props.durability(style.type.getDurability(37)).fireResistant());
        this.style = style;
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean inner) {
        if (style.texture == null) return null;
        return ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "textures/models/armor/" + style.texture + ".png");
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (style != Style.MIRROR_MANTLE || level.isClientSide || Awakening.dormant(stack) || !(entity instanceof LivingEntity le)) return;
        if (le.getItemBySlot(EquipmentSlot.CHEST) != stack) return;
        boolean still = le.getDeltaMovement().horizontalDistanceSqr() < 0.004;
        if (le.isCrouching() && still) {
            le.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, true, false, false));
            hidden(le);
        }
    }

    @Override
    public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        return style == Style.WINGCLOAK && !Awakening.dormant(stack) && stack.getDamageValue() < stack.getMaxDamage() - 1;
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
        if (style != Style.WINGCLOAK) return false;
        if (!entity.level().isClientSide) {
            if (flightTicks == 2) {
                Vec3 look = entity.getLookAngle();
                entity.setDeltaMovement(entity.getDeltaMovement().add(look.scale(0.9)).add(0, 0.25, 0));
                entity.hurtMarked = true;
                ((ServerLevel) entity.level()).sendParticles(ParticleTypes.GUST,
                        entity.getX(), entity.getY() + 0.5, entity.getZ(), 1, 0, 0, 0, 0);
                entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(),
                        SoundSource.PLAYERS, 0.7f, 1.2f);
            }
            if ((flightTicks + 1) % 40 == 0) stack.hurtAndBreak(1, entity, EquipmentSlot.CHEST);
            if (flightTicks % 3 == 0) ((ServerLevel) entity.level()).sendParticles(ParticleTypes.CLOUD,
                    entity.getX(), entity.getY() + 1, entity.getZ(), 1, 0.3, 0.1, 0.3, 0);
        }
        return true;
    }

    public static boolean hidden(LivingEntity le) {
        return le.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof WitchAttireItem w && w.style == Style.MIRROR_MANTLE
                && !Awakening.dormant(le.getItemBySlot(EquipmentSlot.CHEST))
                && le.isCrouching() && le.getDeltaMovement().horizontalDistanceSqr() < 0.004;
    }

    public static void onChangeTarget(LivingChangeTargetEvent e) {
        if (e.getNewAboutToBeSetTarget() != null && hidden(e.getNewAboutToBeSetTarget())) e.setCanceled(true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        if (style.type == Type.HELMET) {
            lines.add(Component.translatable("item.witchsatelier.attire.flavor").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        }
        if (style == Style.WINGCLOAK) lines.add(Component.translatable("item.witchsatelier.wingcloak.desc").withStyle(ChatFormatting.GRAY));
        if (style == Style.MIRROR_MANTLE) lines.add(Component.translatable("item.witchsatelier.mirror_mantle.desc").withStyle(ChatFormatting.GRAY));
    }
}
