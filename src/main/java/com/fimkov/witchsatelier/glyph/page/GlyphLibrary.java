package com.fimkov.witchsatelier.glyph.page;

import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphPages.Page;
import com.fimkov.witchsatelier.glyph.page.GlyphPages.PageStroke;
import com.fimkov.witchsatelier.glyph.recognize.GlyphAnalyzer;
import com.fimkov.witchsatelier.glyph.recognize.GlyphReading;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.recognize.Templates;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class GlyphLibrary {
    private GlyphLibrary() {}

    public record Signs(SignType type, int count, double startDeg, double size) {}

    public record Band(Sigil sigil, List<Signs> signs) {}

    public record Recipe(String name, Sigil sigil, SignType centerSign, double sigilSize, List<Signs> signs, Band band) {
        public Recipe(String name, Sigil sigil, double sigilSize, List<Signs> signs) {
            this(name, sigil, null, sigilSize, signs, null);
        }
    }

    public static final List<Recipe> ALL = List.of(
            new Recipe("pillar_of_flame", Sigil.FIRE, 0.55, List.of(new Signs(SignType.COLUMN, 4, 45, 0.28))),
            new Recipe("wind_underfoot", Sigil.WIND, 0.55, List.of(new Signs(SignType.LEVITATION, 4, 45, 0.28))),
            new Recipe("birds_of_light", Sigil.LIGHT, 0.55, List.of(new Signs(SignType.BIRD, 2, 90, 0.3))),
            new Recipe("beacon_of_birds", Sigil.LIGHT, 0.5, List.of(new Signs(SignType.COLUMN, 4, 45, 0.24), new Signs(SignType.BIRD, 2, 0, 0.24))),
            new Recipe("spilling_water", Sigil.WATER, 0.55, List.of(new Signs(SignType.DISPERSION, 4, 45, 0.28))),
            new Recipe("stone_breaker", Sigil.EARTH, 0.55, List.of(new Signs(SignType.CRUSH, 4, 45, 0.28))),
            new Recipe("fire_bolts", Sigil.FIRE, 0.55, List.of(new Signs(SignType.BOLT, 4, 45, 0.28))),
            new Recipe("gathering_wind", Sigil.WIND, 0.55, List.of(new Signs(SignType.PULL, 4, 45, 0.28))),
            new Recipe("rain_of_light", Sigil.LIGHT, 0.55, List.of(new Signs(SignType.RAIN, 4, 45, 0.28))),
            new Recipe("lantern", Sigil.LIGHT, 0.6, List.of()),
            new Recipe("water_jet", Sigil.WATER, 0.55, List.of(new Signs(SignType.COLUMN, 4, 45, 0.28))),
            new Recipe("windowway", null, SignType.WINDOW, 0.6, List.of(), null),
            new Recipe("bed_of_sand", Sigil.EARTH, null, 0.55, List.of(new Signs(SignType.CRUSH, 4, 45, 0.28)),
                    new Band(null, List.of(new Signs(SignType.BILLOWING, 4, 0, 0.14)))),
            new Recipe("everburning_pillar", Sigil.FIRE, null, 0.55, List.of(new Signs(SignType.COLUMN, 4, 45, 0.28)),
                    new Band(null, List.of(new Signs(SignType.REPETITION, 4, 0, 0.14)))),
            new Recipe("tidal_ring", Sigil.WATER, null, 0.55, List.of(new Signs(SignType.DISPERSION, 4, 45, 0.28)),
                    new Band(null, List.of(new Signs(SignType.REPETITION, 4, 0, 0.14)))),
            new Recipe("sentinel_bolts", Sigil.FIRE, null, 0.5, List.of(new Signs(SignType.BOLT, 3, 90, 0.26), new Signs(SignType.CROSSHAIR, 1, 270, 0.26)), null),
            new Recipe("crystal_ward", null, SignType.DIAMOND, 0.55, List.of(), null),
            new Recipe("pillar_and_volley", Sigil.FIRE, null, 0.5, List.of(new Signs(SignType.COLUMN, 2, 45, 0.26), new Signs(SignType.BOLT, 2, 135, 0.26)), null),
            new Recipe("earthen_wall", Sigil.EARTH, 0.55, List.of(new Signs(SignType.DISPERSION, 4, 45, 0.28))),
            new Recipe("rising_earth", Sigil.EARTH, 0.55, List.of(new Signs(SignType.LEVITATION, 4, 45, 0.28))),
            new Recipe("mire", Sigil.EARTH, 0.55, List.of(new Signs(SignType.PULL, 4, 45, 0.28))),
            new Recipe("quickening_soil", Sigil.EARTH, 0.6, List.of()),
            new Recipe("sunlit_spill", Sigil.LIGHT, 0.55, List.of(new Signs(SignType.DISPERSION, 4, 45, 0.28))),
            new Recipe("fan_of_flame", Sigil.FIRE, 0.55, List.of(new Signs(SignType.DIRECTION, 4, 45, 0.28))),
            new Recipe("lance_of_light", Sigil.LIGHT, null, 0.5, List.of(new Signs(SignType.COLUMN, 3, 90, 0.26), new Signs(SignType.CONVERGENCE, 1, 270, 0.26)), null),
            new Recipe("great_lantern", Sigil.LIGHT, null, 0.6, List.of(), new Band(null, List.of(new Signs(SignType.ENLARGE, 4, 0, 0.14)))),
            new Recipe("night_eye", null, SignType.VISION, 0.55, List.of(), null),
            new Recipe("veil_of_unseeing", null, SignType.VISION, 0.5, List.of(new Signs(SignType.BEND, 4, 45, 0.26)), null),
            new Recipe("feather_fall", Sigil.WIND, 0.55, List.of(new Signs(SignType.FLOAT, 4, 45, 0.28))),
            new Recipe("warming_hearth", Sigil.FIRE, 0.55, List.of(new Signs(SignType.RADIAL, 4, 45, 0.28))),
            new Recipe("sylph_step", Sigil.WIND_UNDERFOOT, 0.6, List.of()),
            new Recipe("breath_of_the_deep", Sigil.AERIFORMS, 0.6, List.of()),
            new Recipe("snare_of_flame", Sigil.FIRE, null, 0.5, List.of(new Signs(SignType.COLUMN, 3, 90, 0.26), new Signs(SignType.TREAD, 1, 270, 0.26)), null),
            new Recipe("binding_threads", Sigil.LIGHT, null, 0.5, List.of(new Signs(SignType.WEAVE, 4, 45, 0.26)), null),
            new Recipe("puppet_dance", null, SignType.DANCING_PUPPET, 0.6, List.of(), null),
            new Recipe("gathering_breeze", Sigil.WIND, null, 0.5, List.of(new Signs(SignType.COLLECTION, 4, 45, 0.26)), null),
            new Recipe("steam_pillar", Sigil.FIRE, null, 0.55, List.of(new Signs(SignType.COLUMN, 4, 45, 0.28)),
                    new Band(Sigil.WATER, List.of(new Signs(SignType.COLUMN, 3, 30, 0.14)))));

    public static final List<Recipe> FORBIDDEN = List.of(
            new Recipe("petrification", Sigil.EARTH, 0.55, List.of(new Signs(SignType.EYE, 4, 45, 0.28))));

    public static Recipe byName(String name) {
        for (Recipe r : ALL) if (r.name().equals(name)) return r;
        for (Recipe r : FORBIDDEN) if (r.name().equals(name)) return r;
        return null;
    }

    public static ItemStack forbiddenPageItem(RandomSource rnd) {
        Recipe r = FORBIDDEN.get(rnd.nextInt(FORBIDDEN.size()));
        ItemStack page = new ItemStack(ModItems.TORN_PAGE.get());
        page.set(ModComponents.GLYPH_PAGES.get(), new GlyphPages(List.of(draw(r, rnd, true, InkType.BLOOD)), 0));
        page.set(ModComponents.GRIMOIRE_ENTRIES.get(), List.of(r.name()));
        return page;
    }

    public static Page draw(Recipe recipe, RandomSource rnd, boolean gap, InkType ink) {
        List<PageStroke> out = new ArrayList<>();
        double radius = 0.85;
        double from = gap ? 25 + rnd.nextInt(360) : 0, to = gap ? from + 310 : 360;
        out.add(new PageStroke(ring(radius, from, to, rnd), ink));
        if (recipe.band() == null) {
            core(out, recipe, radius, 1, rnd, ink);
        } else {
            double inner = radius * 0.6;
            out.add(new PageStroke(ring(inner, 0, 360, rnd), ink));
            core(out, recipe, inner, inner / radius, rnd, ink);
            double mid = (radius + inner) / 2;
            for (Signs s : recipe.band().signs()) signs(out, s, mid, rnd, ink);
            if (recipe.band().sigil() != null) {
                Pt at = new Pt(0, mid);
                put(out, sigilTemplate(recipe.band().sigil()), at, 0.2, 0, rnd, ink);
            }
        }
        return new Page(List.copyOf(out));
    }

    private static void core(List<PageStroke> out, Recipe recipe, double radius, double scale, RandomSource rnd, InkType ink) {
        if (recipe.sigil() != null) {
            put(out, sigilTemplate(recipe.sigil()), new Pt(0, 0), recipe.sigilSize() * scale, 0, rnd, ink);
        } else if (recipe.centerSign() != null) {
            put(out, signTemplate(recipe.centerSign()), new Pt(0, 0), recipe.sigilSize() * scale, 0, rnd, ink);
        }
        for (Signs s : recipe.signs()) signs(out, new Signs(s.type(), s.count(), s.startDeg(), s.size() * scale), radius * 0.62, rnd, ink);
    }

    private static void signs(List<PageStroke> out, Signs s, double at, RandomSource rnd, InkType ink) {
        List<List<Pt>> t = signTemplate(s.type());
        for (int k = 0; k < s.count(); k++) {
            double pos = Math.toRadians(s.startDeg() + k * 360.0 / s.count());
            put(out, t, new Pt(Math.cos(pos) * at, Math.sin(pos) * at), s.size(), pos - Math.PI / 2, rnd, ink);
        }
    }

    private static List<List<Pt>> sigilTemplate(Sigil sigil) {
        return Templates.SIGILS.stream().filter(t -> t.sigil() == sigil).findFirst().orElseThrow().strokes();
    }

    private static List<List<Pt>> signTemplate(SignType type) {
        return Templates.SIGNS.stream().filter(x -> x.sign() == type && !x.reversed()).findFirst().orElseThrow().strokes();
    }

    private static float[] ring(double radius, double a0, double a1, RandomSource rnd) {
        int n = (int) Math.ceil(Math.abs(a1 - a0) / 360 * Math.PI * 2 * radius / 0.03);
        float[] f = new float[(n + 1) * 2];
        double wob = rnd.nextDouble() * 6;
        for (int i = 0; i <= n; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / n);
            double r = radius * (1 + 0.01 * Math.sin(a * 5 + wob));
            f[i * 2] = (float) (Math.cos(a) * r);
            f[i * 2 + 1] = (float) (Math.sin(a) * r);
        }
        return f;
    }

    private static void put(List<PageStroke> out, List<List<Pt>> template, Pt at, double size, double rot, RandomSource rnd, InkType ink) {
        for (List<Pt> s : template) {
            float[] f = new float[s.size() * 2];
            for (int i = 0; i < s.size(); i++) {
                Pt p = s.get(i).rotate(rot).scale(size / 2).plus(at);
                f[i * 2] = (float) (p.x() + rnd.nextGaussian() * 0.004);
                f[i * 2 + 1] = (float) (p.y() + rnd.nextGaussian() * 0.004);
            }
            out.add(new PageStroke(f, ink));
        }
    }

    public static Recipe recipeOf(ItemStack book, int page) {
        List<String> names = book.get(ModComponents.GRIMOIRE_ENTRIES.get());
        return names == null || page < 0 || page >= names.size() ? null : byName(names.get(page));
    }

    public static Page fresh(Recipe r) {
        return draw(r, RandomSource.create(r.name().hashCode()), false, InkType.LASTING);
    }

    public static List<List<Pt>> strokesOf(Page page) {
        List<List<Pt>> out = new ArrayList<>();
        for (PageStroke st : page.strokes()) {
            float[] f = st.pts();
            List<Pt> q = new ArrayList<>(f.length / 2);
            for (int i = 0; i + 1 < f.length; i += 2) q.add(new Pt(f[i], f[i + 1]));
            out.add(q);
        }
        return out;
    }

    public static boolean readsAs(Recipe r, GlyphReading g) {
        if (g.ring() == null) return false;
        GlyphReading core = g;
        if (r.band() != null) {
            if (g.core() == null || g.sigil() != r.band().sigil() || !sameSigns(r.band().signs(), g)) return false;
            core = g.core();
        } else if (g.core() != null) {
            return false;
        }
        return core.sigil() == r.sigil() && core.centerSign() == r.centerSign() && sameSigns(r.signs(), core);
    }

    private static boolean sameSigns(List<Signs> want, GlyphReading g) {
        Map<SignType, Integer> a = new EnumMap<>(SignType.class), b = new EnumMap<>(SignType.class);
        for (Signs s : want) a.merge(s.type(), s.count(), Integer::sum);
        for (var s : g.signs()) b.merge(s.type(), 1, Integer::sum);
        return a.equals(b);
    }

    public static boolean refreshPage(ItemStack book) {
        GlyphPages gp = book.get(ModComponents.GLYPH_PAGES.get());
        if (gp == null || gp.pages().isEmpty()) return false;
        int page = gp.current();
        Recipe r = recipeOf(book, page);
        if (r == null) return false;
        var reading = GlyphAnalyzer.analyze(strokesOf(gp.page(page)), PageService.PAGE_INK);
        if (readsAs(r, reading)) return false;
        book.set(ModComponents.GLYPH_PAGES.get(), gp.withPage(page, fresh(r)));
        return true;
    }

    public static final String BASICS = "basics";

    public static final String IMAGINED_RING = "imagined_ring";

    public static ItemStack grimoireBook(RandomSource rnd, int count, String... first) {
        List<Recipe> pool = new ArrayList<>(ALL);
        List<Page> pages = new ArrayList<>();
        List<String> names = new ArrayList<>();
        pages.add(Page.EMPTY);
        names.add(BASICS);
        pages.add(Page.EMPTY);
        names.add(IMAGINED_RING);
        for (String f : first) {
            Recipe r = byName(f);
            if (r == null || (!pool.remove(r) && !FORBIDDEN.contains(r))) continue;
            pages.add(draw(r, rnd, false, InkType.LASTING));
            names.add(r.name());
        }
        while (pages.size() < Math.min(PageKind.GRIMOIRE.pages, count + 2) && !pool.isEmpty()) {
            Recipe r = pool.remove(rnd.nextInt(pool.size()));
            pages.add(draw(r, rnd, false, InkType.LASTING));
            names.add(r.name());
        }
        ItemStack book = new ItemStack(ModItems.GRIMOIRE.get());
        book.set(ModComponents.GLYPH_PAGES.get(), new GlyphPages(List.copyOf(pages), 0));
        book.set(ModComponents.GRIMOIRE_ENTRIES.get(), List.copyOf(names));
        return book;
    }

    public record Volume(String id, List<String> entries) {
        public String titleKey() {
            return "item.witchsatelier.grimoire." + id;
        }
    }

    public static final Volume PRIMER = new Volume("primer", List.of(BASICS, "lantern", "birds_of_light", "pillar_of_flame", "petrification"));

    public static final Volume RING_NOTES = new Volume("imagined_ring", List.of(IMAGINED_RING));

    public static final Volume FORBIDDEN_BOOK = new Volume("forbidden", List.of("forbidden_magic", "the_pact", "blood_ink", "petrification", "brimhats", "knights_moralis"));

    public static final List<Volume> VOLUMES = List.of(
            new Volume("flame", List.of("fan_of_flame", "fire_bolts", "warming_hearth", "snare_of_flame", "steam_pillar")),
            new Volume("waters", List.of("spilling_water", "water_jet", "tidal_ring", "breath_of_the_deep")),
            new Volume("earth", List.of("stone_breaker", "bed_of_sand", "earthen_wall", "rising_earth", "mire", "quickening_soil")),
            new Volume("wind", List.of("wind_underfoot", "gathering_wind", "gathering_breeze", "feather_fall", "sylph_step")),
            new Volume("light", List.of("great_lantern", "rain_of_light", "sunlit_spill", "lance_of_light", "beacon_of_birds")),
            new Volume("rings_within_rings", List.of("everburning_pillar", "sentinel_bolts", "pillar_and_volley", "crystal_ward", "binding_threads")),
            new Volume("odd_signs", List.of("windowway", "night_eye", "veil_of_unseeing", "puppet_dance")));

    public static ItemStack volumeBook(Volume v, RandomSource rnd) {
        List<Page> pages = new ArrayList<>();
        for (String e : v.entries()) {
            Recipe r = byName(e);
            Page picture = r == null ? Illustrations.of(e, rnd) : null;
            pages.add(r != null ? draw(r, rnd, false, FORBIDDEN.contains(r) ? InkType.BLOOD : InkType.LASTING) : picture != null ? picture : Page.EMPTY);
        }
        ItemStack book = new ItemStack(ModItems.GRIMOIRE.get());
        book.set(ModComponents.GLYPH_PAGES.get(), new GlyphPages(List.copyOf(pages), 0));
        book.set(ModComponents.GRIMOIRE_ENTRIES.get(), List.copyOf(v.entries()));
        book.set(DataComponents.ITEM_NAME, Component.translatable(v.titleKey()));
        return book;
    }

    public static ItemStack randomVolume(RandomSource rnd) {
        return volumeBook(VOLUMES.get(rnd.nextInt(VOLUMES.size())), rnd);
    }

    public static final List<String> CONTRAPTIONS = List.of("contraptions", "beast_warding_tower", "washing_barrel", "light_tracer_bangle",
            "raincleaver", "mirror_mantle", "arcane_lens", "drawing_tools", "glowstone_path", "dragon_teacup", "wingcloak", "tent_staff", "capture_pennant", "linked_glyphs", "forbidden_magic");

    public static ItemStack contraptionsBook() {
        List<Page> pages = new ArrayList<>();
        for (int i = 0; i < CONTRAPTIONS.size(); i++) pages.add(Page.EMPTY);
        ItemStack book = new ItemStack(ModItems.GRIMOIRE.get());
        book.set(ModComponents.GLYPH_PAGES.get(), new GlyphPages(List.copyOf(pages), 0));
        book.set(ModComponents.GRIMOIRE_ENTRIES.get(), CONTRAPTIONS);
        book.set(DataComponents.CUSTOM_NAME,
                Component.translatable("item.witchsatelier.book_of_contraptions").withStyle(ChatFormatting.ITALIC));
        return book;
    }

    public static ItemStack preparedPageItem(RandomSource rnd) {
        Recipe r = ALL.get(rnd.nextInt(ALL.size()));
        ItemStack page = new ItemStack(ModItems.TORN_PAGE.get());
        page.set(ModComponents.GLYPH_PAGES.get(), new GlyphPages(List.of(draw(r, rnd, true, InkType.CONJURING)), 0));
        page.set(ModComponents.GRIMOIRE_ENTRIES.get(), List.of(r.name()));
        return page;
    }

    public static GlyphPages grimoire(RandomSource rnd, int count) {
        List<Recipe> pool = new ArrayList<>(ALL);
        List<Page> pages = new ArrayList<>();
        for (int i = 0; i < PageKind.GRIMOIRE.pages; i++) {
            if (i < count && !pool.isEmpty()) pages.add(draw(pool.remove(rnd.nextInt(pool.size())), rnd, false, InkType.LASTING));
            else pages.add(Page.EMPTY);
        }
        return new GlyphPages(List.copyOf(pages), 0);
    }

    public static GlyphPages preparedPage(RandomSource rnd) {
        return new GlyphPages(List.of(draw(ALL.get(rnd.nextInt(ALL.size())), rnd, true, InkType.CONJURING)), 0);
    }
}
