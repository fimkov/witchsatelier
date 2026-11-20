package com.witchcraft.glyph.page;

import com.witchcraft.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Things you can draw a glyph on and hold in your hand.
 *
 * @param pages  how many pages it has
 * @param scale  blocks per page unit: how big (and so how strong) a full-page glyph counts as
 * @param round  round pages (the palm quire) or square ones
 */
public enum PageKind {
    PAPER(1, 1, 0.9, false, true),
    BOOK(8, 8, 1.0, false, true),
    /** Pages can be torn out and new ones put in, up to 32. Ink on its pages cannot be rubbed out. */
    PALM_QUIRE(16, 32, 0.8, true, false),
    /** A page torn out of a palm quire, crumpled up. */
    TORN_PAGE(1, 1, 0.8, true, false),
    /** The soles of a pair of boots: one glyph across both, cast by clicking the heels together. */
    BOOTS(1, 1, 0.7, false, true),
    /** An old book of glyphs to learn from: its pages can be read and copied, not drawn on or cast. */
    GRIMOIRE(10, 16, 1.0, false, false);

    /** Pages of a fresh one. */
    public final int pages;
    public final int maxPages;
    public final double scale;
    public final boolean round;
    public final boolean erasable;

    PageKind(int pages, int maxPages, double scale, boolean round, boolean erasable) {
        this.pages = pages;
        this.maxPages = maxPages;
        this.scale = scale;
        this.round = round;
        this.erasable = erasable;
    }

    public boolean readOnly() {
        return this == GRIMOIRE;
    }

    public static PageKind of(ItemStack stack) {
        if (stack.is(ModItems.GRIMOIRE.get())) return GRIMOIRE;
        if (stack.is(Items.PAPER)) return PAPER;
        if (stack.is(ModItems.PALM_QUIRE.get())) return PALM_QUIRE;
        if (stack.is(ModItems.TORN_PAGE.get())) return TORN_PAGE;
        if (stack.getItem() instanceof net.minecraft.world.item.ArmorItem a && a.getType() == net.minecraft.world.item.ArmorItem.Type.BOOTS) return BOOTS;
        return null;
    }
}
