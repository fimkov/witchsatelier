package com.witchcraft.compat;

import com.witchcraft.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.function.Supplier;

/**
 * Information pages for recipe viewers (JEI, EMI): every item whose use is not a crafting recipe (inks, pens, the
 * quire and the old books, the artifacts), built from the descriptions the items already carry.
 */
public final class ItemInfo {
    private ItemInfo() {}

    public record Entry(Item item, Component[] lines) {}

    private static Entry of(Supplier<? extends Item> item, String... keys) {
        Component[] lines = new Component[keys.length];
        for (int i = 0; i < keys.length; i++) lines[i] = Component.translatable(keys[i]);
        return new Entry(item.get(), lines);
    }

    public static List<Entry> entries() {
        return List.of(
                of(ModItems.INK_PEN, "item.witchcraft.ink_pen.hint_draw", "item.witchcraft.ink_pen.hint_dip", "jei.witchcraft.info.ink_pen"),
                of(ModItems.INKWELL, "item.witchcraft.inkwell.hint"),
                of(ModItems.CONJURING_INK, "item.witchcraft.conjuring_ink.desc", "item.witchcraft.ink.hint"),
                of(ModItems.WATERPROOF_INK, "item.witchcraft.waterproof_ink.desc", "item.witchcraft.ink.hint"),
                of(ModItems.INVISIBLE_INK, "item.witchcraft.invisible_ink.desc", "item.witchcraft.ink.hint"),
                of(ModItems.LASTING_INK, "item.witchcraft.lasting_ink.desc", "item.witchcraft.ink.hint"),
                of(ModItems.BLOOD_INK, "item.witchcraft.blood_ink.desc", "item.witchcraft.ink.hint"),
                of(ModItems.WOODCRUOR_WAND, "item.witchcraft.woodcruor_wand.desc"),
                of(ModItems.SEARNEEDLE, "item.witchcraft.searneedle.desc"),
                of(ModItems.PALM_QUIRE, "item.witchcraft.palm_quire.hint"),
                of(ModItems.GRIMOIRE, "item.witchcraft.grimoire.desc", "item.witchcraft.grimoire.hint", "jei.witchcraft.info.grimoire"),
                of(ModItems.SNUGSTONE, "item.witchcraft.snugstone.desc"),
                of(ModItems.VAPOR_BUBBLE, "item.witchcraft.vapor_bubble.desc"),
                of(ModItems.RAINCLEAVER, "item.witchcraft.raincleaver.desc"),
                of(ModItems.MIRROR_MANTLE, "item.witchcraft.mirror_mantle.desc"),
                of(ModItems.SEALCHAIR, "item.witchcraft.sealchair.desc"),
                of(ModItems.WINGCLOAK, "item.witchcraft.wingcloak.desc"),
                of(ModItems.CAPTURE_PENNANT, "item.witchcraft.capture_pennant.desc"),
                of(ModItems.TENT_STAFF, "item.witchcraft.tent_staff.desc"),
                of(ModItems.BRIMHAT_MASK, "item.witchcraft.brimhat_mask.desc"),
                of(ModItems.WOODCRUOR, "jei.witchcraft.info.woodcruor"));
    }
}
