package com.fimkov.witchsatelier.glyph.page;

import com.fimkov.witchsatelier.registry.ModItems;
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
    /** An artifact made but still dormant: its own glyph is drawn on it once, to wake it (see Awakening). */
    ARTIFACT(1, 1, 0.7, false, true),
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

    /**
     * The two soles are drawn apart, as a pair of shoes is: one glyph across both, each half on its own sole, the
     * gap between them closing when the heels click together. This is how far each half sits out from the middle.
     */
    public static final float SOLE_GAP = 0.14f;

    /** The halves on the two soles pushed together, as the heels do: what the glyph on a pair of shoes reads as. */
    public static java.util.List<java.util.List<com.fimkov.witchsatelier.glyph.recognize.Pt>> joinSoles(java.util.List<java.util.List<com.fimkov.witchsatelier.glyph.recognize.Pt>> strokes) {
        java.util.List<java.util.List<com.fimkov.witchsatelier.glyph.recognize.Pt>> out = new java.util.ArrayList<>();
        for (var s : strokes) {
            java.util.List<com.fimkov.witchsatelier.glyph.recognize.Pt> q = new java.util.ArrayList<>(s.size());
            for (var p : s) q.add(new com.fimkov.witchsatelier.glyph.recognize.Pt(p.x() - Math.signum(p.x()) * Math.min(Math.abs(p.x()), SOLE_GAP), p.y()));
            out.add(q);
        }
        return out;
    }

    /**
     * A whole glyph's stroke (page units), scaled by {@code scale}, cut down the middle and its halves set out onto
     * the two soles: the way it is to be drawn on a pair of shoes.
     */
    public static java.util.List<float[]> splitForSoles(float[] pts, float scale) {
        java.util.List<float[]> parts = new java.util.ArrayList<>();
        java.util.List<Float> cur = new java.util.ArrayList<>();
        float prevX = 0, prevY = 0;
        for (int i = 0; i + 1 < pts.length; i += 2) {
            float x = pts[i] * scale, y = pts[i + 1] * scale;
            if (i > 0 && Math.signum(x) != Math.signum(prevX) && x != 0 && prevX != 0) {
                // crossing the middle: end this half at the edge of its sole, start the other on the other sole
                float t = prevX / (prevX - x), ym = prevY + (y - prevY) * t;
                cur.add(Math.signum(prevX) * SOLE_GAP); cur.add(ym);
                parts.add(toArray(cur));
                cur = new java.util.ArrayList<>();
                cur.add(Math.signum(x) * SOLE_GAP); cur.add(ym);
            }
            cur.add(x + Math.signum(x) * SOLE_GAP); cur.add(y);
            prevX = x; prevY = y;
        }
        if (cur.size() >= 2) parts.add(toArray(cur));
        return parts;
    }

    private static float[] toArray(java.util.List<Float> l) {
        float[] f = new float[l.size()];
        for (int i = 0; i < f.length; i++) f[i] = l.get(i);
        return f;
    }

    public boolean readOnly() {
        return this == GRIMOIRE;
    }

    public static PageKind of(ItemStack stack) {
        if (com.fimkov.witchsatelier.item.artifact.Awakening.dormant(stack)) return ARTIFACT;
        if (stack.is(ModItems.GRIMOIRE.get())) return GRIMOIRE;
        if (stack.is(Items.PAPER)) return PAPER;
        if (stack.is(ModItems.PALM_QUIRE.get())) return PALM_QUIRE;
        if (stack.is(ModItems.TORN_PAGE.get())) return TORN_PAGE;
        if (stack.getItem() instanceof net.minecraft.world.item.ArmorItem a && a.getType() == net.minecraft.world.item.ArmorItem.Type.BOOTS) return BOOTS;
        return null;
    }
}
