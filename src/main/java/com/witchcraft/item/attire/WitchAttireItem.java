package com.witchcraft.item.attire;

import com.witchcraft.WitchCraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** One piece of the witch's outfit. Hats and the robe have their own 3D models and textures. */
public class WitchAttireItem extends ArmorItem {
    public enum Style {
        APPRENTICE_HAT(Type.HELMET, "apprentice_hat"),
        WITCH_HAT(Type.HELMET, "witch_hat"),
        ROBE(Type.CHESTPLATE, "witch_capelet"),
        MANTLE(Type.CHESTPLATE, "witch_mantle"),
        /** Mirror Mantle of Borrowshade: Mirror and Gathering Shadows glyphs woven into the lining. */
        MIRROR_MANTLE(Type.CHESTPLATE, "mirror_mantle"),
        /** The ragged black mantle of a Brimhat. */
        BRIMHAT_MANTLE(Type.CHESTPLATE, "brimhat_mantle"),
        /** Coco and Tartah's wingcloak: a flight glyph in the lining, and one to keep the rain off. */
        WINGCLOAK(Type.CHESTPLATE, "wingcloak"),
        STOCKINGS(Type.LEGGINGS, "witch_skirt"),
        BOOTS(Type.BOOTS, null);

        public final Type type;
        /** Texture of the custom model, or null for the regular armour layers. */
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
        return ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "textures/models/armor/" + style.texture + ".png");
    }

    /**
     * The Mirror Mantle: crouch and keep still, and the mantle borrows the shade around you; you fade from sight
     * and creatures lose track of you.
     */
    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.world.level.Level level, Entity entity, int slot, boolean selected) {
        if (style != Style.MIRROR_MANTLE || level.isClientSide || !(entity instanceof net.minecraft.world.entity.LivingEntity le)) return;
        if (le.getItemBySlot(EquipmentSlot.CHEST) != stack) return;
        boolean still = le.getDeltaMovement().horizontalDistanceSqr() < 0.004;
        if (le.isCrouching() && still) {
            le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, 30, 0, true, false, false));
            hidden(le);
        }
    }

    /** The wingcloak glides like wings; taking off throws a gust of wind behind it. */
    @Override
    public boolean canElytraFly(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        return style == Style.WINGCLOAK && stack.getDamageValue() < stack.getMaxDamage() - 1;
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, net.minecraft.world.entity.LivingEntity entity, int flightTicks) {
        if (style != Style.WINGCLOAK) return false;
        if (!entity.level().isClientSide) {
            if (flightTicks == 2) {
                // the take-off gust
                net.minecraft.world.phys.Vec3 look = entity.getLookAngle();
                entity.setDeltaMovement(entity.getDeltaMovement().add(look.scale(0.9)).add(0, 0.25, 0));
                entity.hurtMarked = true;
                ((net.minecraft.server.level.ServerLevel) entity.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.GUST,
                        entity.getX(), entity.getY() + 0.5, entity.getZ(), 1, 0, 0, 0, 0);
                entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), net.minecraft.sounds.SoundEvents.BREEZE_WIND_CHARGE_BURST.value(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.2f);
            }
            if ((flightTicks + 1) % 40 == 0) stack.hurtAndBreak(1, entity, EquipmentSlot.CHEST);
            if (flightTicks % 3 == 0) ((net.minecraft.server.level.ServerLevel) entity.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    entity.getX(), entity.getY() + 1, entity.getZ(), 1, 0.3, 0.1, 0.3, 0);
        }
        return true;
    }

    /** True while the wearer of a Mirror Mantle is hidden in its borrowed shade. */
    public static boolean hidden(net.minecraft.world.entity.LivingEntity le) {
        return le.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof WitchAttireItem w && w.style == Style.MIRROR_MANTLE
                && le.isCrouching() && le.getDeltaMovement().horizontalDistanceSqr() < 0.004;
    }

    /** Creatures cannot pick a target hidden by a Mirror Mantle. */
    public static void onChangeTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent e) {
        if (e.getNewAboutToBeSetTarget() != null && hidden(e.getNewAboutToBeSetTarget())) e.setCanceled(true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        // only a pointed hat makes you feel like a real witch
        if (style.type == Type.HELMET) {
            lines.add(Component.translatable("item.witchcraft.attire.flavor").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        }
        if (style == Style.WINGCLOAK) lines.add(Component.translatable("item.witchcraft.wingcloak.desc").withStyle(ChatFormatting.GRAY));
        if (style == Style.MIRROR_MANTLE) lines.add(Component.translatable("item.witchcraft.mirror_mantle.desc").withStyle(ChatFormatting.GRAY));
    }
}
