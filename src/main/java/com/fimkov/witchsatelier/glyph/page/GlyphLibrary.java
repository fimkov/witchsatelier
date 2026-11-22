package com.fimkov.witchsatelier.glyph.page;

import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.recognize.Templates;
import com.fimkov.witchsatelier.glyph.page.GlyphPages.Page;
import com.fimkov.witchsatelier.glyph.page.GlyphPages.PageStroke;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Glyphs known from old books: what turns up in grimoires and on loose pages in ruins, and what the stranger
 * carries. Each is drawn by a steady but human hand (small wobbles), the signs evenly around the sigil.
 */
public final class GlyphLibrary {
    private GlyphLibrary() {}

    /** One sign group: {@code count} copies of a sign placed evenly, starting at {@code startDeg}. */
    public record Signs(SignType type, int count, double startDeg, double size) {}

    /** The band of a nested glyph: what is drawn between its inner and outer ring. */
    public record Band(Sigil sigil, List<Signs> signs) {}

    /**
     * @param centerSign a sign drawn in the middle instead of a sigil (the Window of a windowway), or null
     * @param band       for a nested glyph: what goes around the inner ring, or null
     */
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

    /** Glyphs the Pact forbids. They are not in honest grimoires; Brimhats carry them, and one stranger. */
    public static final List<Recipe> FORBIDDEN = List.of(
            new Recipe("petrification", Sigil.EARTH, 0.55, List.of(new Signs(SignType.EYE, 4, 45, 0.28))));

    public static Recipe byName(String name) {
        for (Recipe r : ALL) if (r.name().equals(name)) return r;
        for (Recipe r : FORBIDDEN) if (r.name().equals(name)) return r;
        return null;
    }

    /** A page torn from a forbidden book, its ring left open. */
    public static net.minecraft.world.item.ItemStack forbiddenPageItem(RandomSource rnd) {
        Recipe r = FORBIDDEN.get(rnd.nextInt(FORBIDDEN.size()));
        net.minecraft.world.item.ItemStack page = new net.minecraft.world.item.ItemStack(com.fimkov.witchsatelier.registry.ModItems.TORN_PAGE.get());
        page.set(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get(), new GlyphPages(List.of(draw(r, rnd, true, InkType.BLOOD)), 0));
        page.set(com.fimkov.witchsatelier.registry.ModComponents.GRIMOIRE_ENTRIES.get(), List.of(r.name()));
        return page;
    }

    /**
     * The glyph on a page (page units, radius 0.85). With {@code gap} the ring is left open by about 50°, the way
     * witches prepare pages to close in a hurry.
     */
    public static Page draw(Recipe recipe, RandomSource rnd, boolean gap, InkType ink) {
        List<PageStroke> out = new ArrayList<>();
        double radius = 0.85;
        double from = gap ? 25 + rnd.nextInt(360) : 0, to = gap ? from + 310 : 360;
        out.add(new PageStroke(ring(radius, from, to, rnd), ink));
        if (recipe.band() == null) {
            core(out, recipe, radius, 1, rnd, ink);
        } else {
            // a glyph inside a glyph: the inner ring at about 0.52, the band's marks between the two
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

    /** The recipe written on a grimoire's page, or null (the opening pages, a blank one, a book that is no grimoire). */
    public static Recipe recipeOf(net.minecraft.world.item.ItemStack book, int page) {
        List<String> names = book.get(com.fimkov.witchsatelier.registry.ModComponents.GRIMOIRE_ENTRIES.get());
        return names == null || page < 0 || page >= names.size() ? null : byName(names.get(page));
    }

    /** The recipe drawn as the grimoire draws it today (the same hand every time). */
    public static Page fresh(Recipe r) {
        return draw(r, RandomSource.create(r.name().hashCode()), false, InkType.LASTING);
    }

    /** A page's strokes as plane points (page units). */
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

    /** True if {@code g} reads as {@code r} means: its sigil, its signs (as many of each), its band if nested. */
    public static boolean readsAs(Recipe r, com.fimkov.witchsatelier.glyph.recognize.GlyphReading g) {
        if (g.ring() == null) return false;
        com.fimkov.witchsatelier.glyph.recognize.GlyphReading core = g;
        if (r.band() != null) {
            if (g.core() == null || g.sigil() != r.band().sigil() || !sameSigns(r.band().signs(), g)) return false;
            core = g.core();
        } else if (g.core() != null) {
            return false;
        }
        return core.sigil() == r.sigil() && core.centerSign() == r.centerSign() && sameSigns(r.signs(), core);
    }

    private static boolean sameSigns(List<Signs> want, com.fimkov.witchsatelier.glyph.recognize.GlyphReading g) {
        java.util.Map<SignType, Integer> a = new java.util.EnumMap<>(SignType.class), b = new java.util.EnumMap<>(SignType.class);
        for (Signs s : want) a.merge(s.type(), s.count(), Integer::sum);
        for (var s : g.signs()) b.merge(s.type(), 1, Integer::sum);
        return a.equals(b);
    }

    /**
     * Redraws the page at {@code page} if it no longer reads as its recipe (drawn by an older hand, before a sign
     * was redrawn): true if it changed.
     */
    public static boolean refreshPage(net.minecraft.world.item.ItemStack book) {
        GlyphPages gp = book.get(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get());
        if (gp == null || gp.pages().isEmpty()) return false;
        int page = gp.current();
        Recipe r = recipeOf(book, page);
        if (r == null) return false;
        var reading = com.fimkov.witchsatelier.glyph.recognize.GlyphAnalyzer.analyze(strokesOf(gp.page(page)), PageService.PAGE_INK);
        if (readsAs(r, reading)) return false;
        book.set(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get(), gp.withPage(page, fresh(r)));
        return true;
    }

    /** The opening page of every grimoire: how glyphs work at all. */
    public static final String BASICS = "basics";
    /** The second page of every grimoire: the technique of seeing a ring before drawing it. */
    public static final String IMAGINED_RING = "imagined_ring";

    /**
     * A grimoire: the opening page on how glyphs work, then {@code count} different glyphs (complete rings, to
     * copy from), each explained. {@code first} entries come right after the opening page.
     */
    public static net.minecraft.world.item.ItemStack grimoireBook(RandomSource rnd, int count, String... first) {
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
        net.minecraft.world.item.ItemStack book = new net.minecraft.world.item.ItemStack(com.fimkov.witchsatelier.registry.ModItems.GRIMOIRE.get());
        book.set(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get(), new GlyphPages(List.copyOf(pages), 0));
        book.set(com.fimkov.witchsatelier.registry.ModComponents.GRIMOIRE_ENTRIES.get(), List.copyOf(names));
        return book;
    }

    /**
     * One of the old books a witch can come across. Each is its own: a title, and a handful of entries on one theme
     * (glyph recipes, or text pages such as {@link #BASICS}), with no blank pages. Nobody finds every glyph in one book.
     */
    public record Volume(String id, List<String> entries) {
        /** The book's name: {@code item.witchsatelier.grimoire.<id>}. */
        public String titleKey() {
            return "item.witchsatelier.grimoire." + id;
        }
    }

    /**
     * The first book, the one the Stranger gives: how glyphs work and three glyphs to start with, and at the very end,
     * in blood ink, a page that should never have been in it (as in the book a stranger once gave a girl named Coco).
     */
    public static final Volume PRIMER = new Volume("primer", List.of(BASICS, "lantern", "birds_of_light", "pillar_of_flame", "petrification"));
    /** A thin notebook on the one technique: seeing the ring before drawing it. Reading it teaches it. */
    public static final Volume RING_NOTES = new Volume("imagined_ring", List.of(IMAGINED_RING));
    /** A black-bound book of what the Pact forbids. */
    public static final Volume FORBIDDEN_BOOK = new Volume("forbidden", List.of("forbidden_magic", "the_pact", "blood_ink", "petrification", "brimhats", "knights_moralis"));
    /** The books found in libraries and sold by the Stranger, each on its own theme. */
    public static final List<Volume> VOLUMES = List.of(
            new Volume("flame", List.of("fan_of_flame", "fire_bolts", "warming_hearth", "snare_of_flame", "steam_pillar")),
            new Volume("waters", List.of("spilling_water", "water_jet", "tidal_ring", "breath_of_the_deep")),
            new Volume("earth", List.of("stone_breaker", "bed_of_sand", "earthen_wall", "rising_earth", "mire", "quickening_soil")),
            new Volume("wind", List.of("wind_underfoot", "gathering_wind", "gathering_breeze", "feather_fall", "sylph_step")),
            new Volume("light", List.of("great_lantern", "rain_of_light", "sunlit_spill", "lance_of_light", "beacon_of_birds")),
            new Volume("rings_within_rings", List.of("everburning_pillar", "sentinel_bolts", "pillar_and_volley", "crystal_ward", "binding_threads")),
            new Volume("odd_signs", List.of("windowway", "night_eye", "veil_of_unseeing", "puppet_dance")));

    /** The book itself: each glyph entry drawn complete, to copy from; text entries face a blank page. */
    public static net.minecraft.world.item.ItemStack volumeBook(Volume v, RandomSource rnd) {
        List<Page> pages = new ArrayList<>();
        for (String e : v.entries()) {
            Recipe r = byName(e);
            Page picture = r == null ? Illustrations.of(e, rnd) : null;
            pages.add(r != null ? draw(r, rnd, false, FORBIDDEN.contains(r) ? InkType.BLOOD : InkType.LASTING) : picture != null ? picture : Page.EMPTY);
        }
        net.minecraft.world.item.ItemStack book = new net.minecraft.world.item.ItemStack(com.fimkov.witchsatelier.registry.ModItems.GRIMOIRE.get());
        book.set(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get(), new GlyphPages(List.copyOf(pages), 0));
        book.set(com.fimkov.witchsatelier.registry.ModComponents.GRIMOIRE_ENTRIES.get(), List.copyOf(v.entries()));
        book.set(net.minecraft.core.component.DataComponents.ITEM_NAME, net.minecraft.network.chat.Component.translatable(v.titleKey()));
        return book;
    }

    /** One of the themed books, at random. */
    public static net.minecraft.world.item.ItemStack randomVolume(RandomSource rnd) {
        return volumeBook(VOLUMES.get(rnd.nextInt(VOLUMES.size())), rnd);
    }

    /** Pages of the Book of Contraptions: what witches make for everyone else, and the tools they draw with. */
    public static final List<String> CONTRAPTIONS = List.of("contraptions", "beast_warding_tower", "washing_barrel", "light_tracer_bangle",
            "raincleaver", "mirror_mantle", "arcane_lens", "drawing_tools", "glowstone_path", "dragon_teacup", "wingcloak", "tent_staff", "capture_pennant", "linked_glyphs", "forbidden_magic");

    /** A grimoire that explains contraptions (text only). */
    public static net.minecraft.world.item.ItemStack contraptionsBook() {
        List<Page> pages = new ArrayList<>();
        for (int i = 0; i < CONTRAPTIONS.size(); i++) pages.add(Page.EMPTY);
        net.minecraft.world.item.ItemStack book = new net.minecraft.world.item.ItemStack(com.fimkov.witchsatelier.registry.ModItems.GRIMOIRE.get());
        book.set(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get(), new GlyphPages(List.copyOf(pages), 0));
        book.set(com.fimkov.witchsatelier.registry.ModComponents.GRIMOIRE_ENTRIES.get(), CONTRAPTIONS);
        book.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.translatable("item.witchsatelier.book_of_contraptions").withStyle(net.minecraft.ChatFormatting.ITALIC));
        return book;
    }

    /** A crumpled page with a prepared glyph (ring left open), remembering which glyph it is. */
    public static net.minecraft.world.item.ItemStack preparedPageItem(RandomSource rnd) {
        Recipe r = ALL.get(rnd.nextInt(ALL.size()));
        net.minecraft.world.item.ItemStack page = new net.minecraft.world.item.ItemStack(com.fimkov.witchsatelier.registry.ModItems.TORN_PAGE.get());
        page.set(com.fimkov.witchsatelier.registry.ModComponents.GLYPH_PAGES.get(), new GlyphPages(List.of(draw(r, rnd, true, InkType.CONJURING)), 0));
        page.set(com.fimkov.witchsatelier.registry.ModComponents.GRIMOIRE_ENTRIES.get(), List.of(r.name()));
        return page;
    }

    /** A grimoire with {@code count} different glyphs, complete rings (a reference to copy from). */
    public static GlyphPages grimoire(RandomSource rnd, int count) {
        List<Recipe> pool = new ArrayList<>(ALL);
        List<Page> pages = new ArrayList<>();
        for (int i = 0; i < PageKind.GRIMOIRE.pages; i++) {
            if (i < count && !pool.isEmpty()) pages.add(draw(pool.remove(rnd.nextInt(pool.size())), rnd, false, InkType.LASTING));
            else pages.add(Page.EMPTY);
        }
        return new GlyphPages(List.copyOf(pages), 0);
    }

    /** A crumpled page with a prepared glyph, ring left open: close it (V) to cast. */
    public static GlyphPages preparedPage(RandomSource rnd) {
        return new GlyphPages(List.of(draw(ALL.get(rnd.nextInt(ALL.size())), rnd, true, InkType.CONJURING)), 0);
    }
}
