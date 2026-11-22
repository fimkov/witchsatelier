package com.fimkov.witchsatelier.compat;

import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.function.Supplier;

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
                of(ModItems.INK_PEN, "item.witchsatelier.ink_pen.hint_draw", "item.witchsatelier.ink_pen.hint_dip", "jei.witchsatelier.info.ink_pen"),
                of(ModItems.INKWELL, "item.witchsatelier.inkwell.hint"),
                of(ModItems.CONJURING_INK, "item.witchsatelier.conjuring_ink.desc", "item.witchsatelier.ink.hint"),
                of(ModItems.WATERPROOF_INK, "item.witchsatelier.waterproof_ink.desc", "item.witchsatelier.ink.hint"),
                of(ModItems.INVISIBLE_INK, "item.witchsatelier.invisible_ink.desc", "item.witchsatelier.ink.hint"),
                of(ModItems.LASTING_INK, "item.witchsatelier.lasting_ink.desc", "item.witchsatelier.ink.hint"),
                of(ModItems.BLOOD_INK, "item.witchsatelier.blood_ink.desc", "item.witchsatelier.ink.hint"),
                of(ModItems.WOODCRUOR_WAND, "item.witchsatelier.woodcruor_wand.desc"),
                of(ModItems.SEARNEEDLE, "item.witchsatelier.searneedle.desc"),
                of(ModItems.PALM_QUIRE, "item.witchsatelier.palm_quire.hint"),
                of(ModItems.GRIMOIRE, "item.witchsatelier.grimoire.desc", "item.witchsatelier.grimoire.hint", "jei.witchsatelier.info.grimoire"),
                of(ModItems.SNUGSTONE, "item.witchsatelier.snugstone.desc", "jei.witchsatelier.info.awakening"),
                of(ModItems.VAPOR_BUBBLE, "item.witchsatelier.vapor_bubble.desc", "jei.witchsatelier.info.awakening"),
                of(ModItems.RAINCLEAVER, "item.witchsatelier.raincleaver.desc", "jei.witchsatelier.info.awakening"),
                of(ModItems.MIRROR_MANTLE, "item.witchsatelier.mirror_mantle.desc", "jei.witchsatelier.info.awakening"),
                of(ModItems.SEALCHAIR, "item.witchsatelier.sealchair.desc", "jei.witchsatelier.info.awakening"),
                of(ModItems.WINGCLOAK, "item.witchsatelier.wingcloak.desc", "jei.witchsatelier.info.awakening"),
                of(ModItems.CAPTURE_PENNANT, "item.witchsatelier.capture_pennant.desc", "jei.witchsatelier.info.capture_pennant"),
                of(ModItems.TENT_STAFF, "item.witchsatelier.tent_staff.desc", "jei.witchsatelier.info.awakening"),
                of(ModItems.BRIMHAT_MASK, "item.witchsatelier.brimhat_mask.desc", "jei.witchsatelier.info.brimhat_mask"),
                of(ModItems.WOODCRUOR, "jei.witchsatelier.info.woodcruor"),
                of(ModItems.WITCH_GRIMOIRE, "item.witchsatelier.witch_grimoire.desc", "jei.witchsatelier.info.witch_grimoire"),
                of(ModItems.GLYPH_ARROW, "item.witchsatelier.glyph_arrow.desc", "jei.witchsatelier.info.glyph_arrow"),
                of(ModItems.GLOWSTONE_PATH, "item.witchsatelier.glowstone_path.desc", "jei.witchsatelier.info.awakening"),
                of(ModItems.WASHING_BARREL, "item.witchsatelier.washing_barrel.desc", "jei.witchsatelier.info.awakening"),
                of(ModItems.VOID_BASIN, "item.witchsatelier.void_basin.desc", "jei.witchsatelier.info.awakening"),
                of(ModItems.ARCANE_LENS, "item.witchsatelier.arcane_lens.desc", "jei.witchsatelier.info.awakening"),
                of(ModItems.DRAGON_TEACUP, "item.witchsatelier.dragon_teacup.desc", "jei.witchsatelier.info.awakening"),
                of(ModItems.BEAST_WARDING_TOWER, "item.witchsatelier.beast_warding_tower.desc", "jei.witchsatelier.info.awakening"),
                of(ModItems.LIGHT_TRACER_BANGLE, "glyph.witchsatelier.light_tracer_bangle.desc", "jei.witchsatelier.info.awakening"),
                of(ModItems.BLACKENED_TWIG, "jei.witchsatelier.info.blackened_silverwood_twig"),
                of(ModItems.TORN_PAGE, "jei.witchsatelier.info.torn_page"),
                of(ModItems.BRIMHAT_MANTLE, "jei.witchsatelier.info.brimhat_mantle"));
    }
}
