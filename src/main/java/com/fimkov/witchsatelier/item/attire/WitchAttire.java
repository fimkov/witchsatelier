package com.fimkov.witchsatelier.item.attire;

import com.fimkov.witchsatelier.WitchsAtelier;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

/**
 * Witch's attire: protects like leather, wears like netherite. It gives no powers of its own;
 * it only makes you feel like a real witch.
 */
public final class WitchAttire {
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, WitchsAtelier.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> WITCH_CLOTH = MATERIALS.register("witch_cloth", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                m.put(ArmorItem.Type.HELMET, 1);
                m.put(ArmorItem.Type.CHESTPLATE, 3);
                m.put(ArmorItem.Type.LEGGINGS, 2);
                m.put(ArmorItem.Type.BOOTS, 1);
                m.put(ArmorItem.Type.BODY, 3);
            }),
            18, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(ItemTags.WOOL),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "witch"))), 0f, 0f));

    /** A Brimhat's porcelain mask. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> BRIMHAT_PORCELAIN = MATERIALS.register("brimhat_porcelain", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                m.put(ArmorItem.Type.HELMET, 1);
                m.put(ArmorItem.Type.CHESTPLATE, 1);
                m.put(ArmorItem.Type.LEGGINGS, 1);
                m.put(ArmorItem.Type.BOOTS, 1);
                m.put(ArmorItem.Type.BODY, 1);
            }),
            10, SoundEvents.ARMOR_EQUIP_GENERIC, () -> Ingredient.of(net.minecraft.world.item.Items.BONE_MEAL),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "brimhat"))), 0f, 0f));

    public static Holder<ArmorMaterial> material() {
        return WITCH_CLOTH;
    }

    private WitchAttire() {}

}
