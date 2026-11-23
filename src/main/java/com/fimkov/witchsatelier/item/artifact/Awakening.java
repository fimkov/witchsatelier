package com.fimkov.witchsatelier.item.artifact;

import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary.Recipe;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary.Signs;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import com.fimkov.witchsatelier.glyph.page.PageKind;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.List;
import java.util.function.Supplier;

public final class Awakening {
    private Awakening() {}

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
            new Entry(ModItems.VAPOR_BUBBLE, glyph("vapor_bubble", Sigil.WATER, SignType.PULL, 4)),
            new Entry(ModItems.DOORKNOB, centre("doorknob", SignType.WINDOW, SignType.DIRECTION, 4)),
            new Entry(ModItems.GUIDANCE_ORB, glyph("guidance_orb", Sigil.LIGHT, SignType.DIRECTION, 4)),
            new Entry(ModItems.FLUTTERGRAM, glyph("fluttergram", Sigil.WIND, SignType.BIRD, 2)));

    /** Artifacts crafted several at a time that one drawing wakes together, instead of splitting one off the stack. */
    public static boolean wakesAsStack(ItemStack stack) {
        return dormant(stack) && (stack.is(ModItems.DOORKNOB.get()) || stack.is(ModItems.FLUTTERGRAM.get()));
    }

    public static ItemStack dormantStack(Entry e) {
        ItemStack s = new ItemStack(e.artifact().get());
        s.set(ModComponents.DORMANT.get(), true);
        return s;
    }

    public static boolean dormant(ItemStack stack) {
        return !stack.isEmpty() && Boolean.TRUE.equals(stack.get(ModComponents.DORMANT.get()));
    }

    public static Recipe glyphOf(ItemStack stack) {
        Item item = stack.getItem();
        for (Entry e : ALL) if (e.artifact().get().asItem() == item) return e.glyph();
        return null;
    }

    public static Recipe guideFor(ItemStack stack) {
        if (dormant(stack)) return glyphOf(stack);
        if (PageKind.of(stack) == PageKind.BOOTS)
            return GlyphLibrary.byName("wind_underfoot");
        return null;
    }

    public static void wake(ItemStack stack) {
        stack.remove(ModComponents.DORMANT.get());
        stack.remove(ModComponents.GLYPH_PAGES.get());
    }
}
