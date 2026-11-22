package com.fimkov.witchsatelier.glyph.page;

import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.item.artifact.Awakening;
import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public enum PageKind {
    PAPER(1, 1, 0.9, false, true),
    BOOK(8, 8, 1.0, false, true),

    PALM_QUIRE(16, 32, 0.8, true, false),

    TORN_PAGE(1, 1, 0.8, true, false),

    BOOTS(1, 1, 0.7, false, true),

    ARTIFACT(1, 1, 0.7, false, true),

    GRIMOIRE(10, 16, 1.0, false, false),

    WITCH_GRIMOIRE(16, 16, 1.0, false, true);

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

    public static final float SOLE_GAP = 0.14f;

    public static List<List<Pt>> joinSoles(List<List<Pt>> strokes) {
        List<List<Pt>> out = new ArrayList<>();
        for (var s : strokes) {
            List<Pt> q = new ArrayList<>(s.size());
            for (var p : s) q.add(new Pt(p.x() - Math.signum(p.x()) * Math.min(Math.abs(p.x()), SOLE_GAP), p.y()));
            out.add(q);
        }
        return out;
    }

    public static List<float[]> splitForSoles(float[] pts, float scale) {
        List<float[]> parts = new ArrayList<>();
        List<Float> cur = new ArrayList<>();
        float prevX = 0, prevY = 0;
        for (int i = 0; i + 1 < pts.length; i += 2) {
            float x = pts[i] * scale, y = pts[i + 1] * scale;
            if (i > 0 && Math.signum(x) != Math.signum(prevX) && x != 0 && prevX != 0) {
                float t = prevX / (prevX - x), ym = prevY + (y - prevY) * t;
                cur.add(Math.signum(prevX) * SOLE_GAP); cur.add(ym);
                parts.add(toArray(cur));
                cur = new ArrayList<>();
                cur.add(Math.signum(x) * SOLE_GAP); cur.add(ym);
            }
            cur.add(x + Math.signum(x) * SOLE_GAP); cur.add(y);
            prevX = x; prevY = y;
        }
        if (cur.size() >= 2) parts.add(toArray(cur));
        return parts;
    }

    private static float[] toArray(List<Float> l) {
        float[] f = new float[l.size()];
        for (int i = 0; i < f.length; i++) f[i] = l.get(i);
        return f;
    }

    public boolean readOnly() {
        return this == GRIMOIRE;
    }

    public boolean book() {
        return this == GRIMOIRE || this == WITCH_GRIMOIRE;
    }

    public static PageKind of(ItemStack stack) {
        if (Awakening.dormant(stack)) return ARTIFACT;
        if (stack.is(ModItems.GRIMOIRE.get())) return GRIMOIRE;
        if (stack.is(ModItems.WITCH_GRIMOIRE.get())) return WITCH_GRIMOIRE;
        if (stack.is(Items.PAPER)) return PAPER;
        if (stack.is(ModItems.PALM_QUIRE.get())) return PALM_QUIRE;
        if (stack.is(ModItems.TORN_PAGE.get())) return TORN_PAGE;
        if (stack.getItem() instanceof ArmorItem a && a.getType() == ArmorItem.Type.BOOTS) return BOOTS;
        return null;
    }
}
