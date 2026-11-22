package com.fimkov.witchsatelier.item.artifact;

import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary.Recipe;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary.Signs;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.List;
import java.util.function.Supplier;

/**
 * Artifacts are made in two steps. The crafting table gives the thing itself, but dormant: it looks finished and
 * does nothing. It wakes when its glyph is drawn on it (pen in one hand, the artifact in the other, drawn on the
 * canvas, where the glyph it wants shows faintly to be traced) and the ring closes.
 */
public final class Awakening {
    private Awakening() {}

    /** One artifact and the glyph that wakes it. */
    public record Entry(Supplier<? extends ItemLike> artifact, Recipe glyph) {}

    private static Recipe glyph(String name, Sigil sigil, SignType sign, int count) {
        return new Recipe(name, sigil, 0.55, sign == null ? List.of() : List.of(new Signs(sign, count, count == 2 ? 90 : 45, 0.28)));
    }

    private static Recipe centre(String name, SignType centre, SignType sign, int count) {
        return new Recipe(name, null, centre, 0.55, List.of(new Signs(sign, count, 45, 0.28)), null);
    }

    public static final List<Entry> ALL = List.of(
            new Entry(ModItems.WASHING_BARREL, glyph("washing_barrel", Sigil.WATER, SignType.REPETITION, 4)),
            new Entry(ModItems.GLOWSTONE_PATH, glyph("glowstone_path", Sigil.LIGHT, SignType.FLOAT, 4)),
            new Entry(ModItems.VOID_BASIN, centre("void_basin", SignType.WINDOW, SignType.PULL, 4)),
            new Entry(ModItems.DRAGON_TEACUP, glyph("dragon_teacup", Sigil.WATER, SignType.COLUMN, 4)),
            new Entry(ModItems.SEALCHAIR, glyph("sealchair", Sigil.WIND, SignType.FLOAT, 4)),
            new Entry(ModItems.BEAST_WARDING_TOWER, centre("beast_warding_tower", SignType.DIAMOND, SignType.COLUMN, 4)),
            new Entry(ModItems.ARCANE_LENS, glyph("arcane_lens", Sigil.LIGHT, SignType.CONVERGENCE, 4)),
            new Entry(ModItems.SNUGSTONE, glyph("snugstone", Sigil.FIRE, SignType.RADIAL, 4)),
            new Entry(ModItems.RAINCLEAVER, glyph("raincleaver", Sigil.WATER, SignType.CRUSH, 4)),
            new Entry(ModItems.MIRROR_MANTLE, centre("mirror_mantle", SignType.VISION, SignType.WEAVE, 4)),
            new Entry(ModItems.WINGCLOAK, glyph("wingcloak", Sigil.WIND_UNDERFOOT, null, 0)),
            new Entry(ModItems.TENT_STAFF, glyph("tent_staff", Sigil.EARTH, SignType.LEVITATION, 4)),
            new Entry(ModItems.CAPTURE_PENNANT, glyph("capture_pennant", Sigil.LIGHT, SignType.WEAVE, 4)),
            new Entry(ModItems.LIGHT_TRACER_BANGLE, glyph("light_tracer_bangle", Sigil.LIGHT, SignType.PULL, 4)),
            new Entry(ModItems.VAPOR_BUBBLE, glyph("vapor_bubble", Sigil.WATER, SignType.PULL, 4)));

    /** The artifact as the crafting table gives it: dormant. */
    public static ItemStack dormantStack(Entry e) {
        ItemStack s = new ItemStack(e.artifact().get());
        s.set(ModComponents.DORMANT.get(), true);
        return s;
    }

    public static boolean dormant(ItemStack stack) {
        return !stack.isEmpty() && Boolean.TRUE.equals(stack.get(ModComponents.DORMANT.get()));
    }

    /** The glyph that wakes this artifact, or null if it is none. */
    public static Recipe glyphOf(ItemStack stack) {
        Item item = stack.getItem();
        for (Entry e : ALL) if (e.artifact().get().asItem() == item) return e.glyph();
        return null;
    }

    /** The glyph shown faintly on a canvas to be traced: what wakes a dormant artifact, or the flight glyph on shoes. */
    public static Recipe guideFor(ItemStack stack) {
        if (dormant(stack)) return glyphOf(stack);
        if (com.fimkov.witchsatelier.glyph.page.PageKind.of(stack) == com.fimkov.witchsatelier.glyph.page.PageKind.BOOTS)
            return GlyphLibrary.byName("wind_underfoot");
        return null;
    }

    /** Wakes it: it works from now on, and the drawing has sunk into it. */
    public static void wake(ItemStack stack) {
        stack.remove(ModComponents.DORMANT.get());
        stack.remove(ModComponents.GLYPH_PAGES.get());
    }
}
