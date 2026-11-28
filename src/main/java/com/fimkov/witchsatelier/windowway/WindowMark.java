package com.fimkov.witchsatelier.windowway;

import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import com.fimkov.witchsatelier.glyph.page.PageKind;
import com.fimkov.witchsatelier.glyph.page.PageService;
import com.fimkov.witchsatelier.glyph.recognize.GlyphAnalyzer;
import com.fimkov.witchsatelier.glyph.recognize.GlyphReading;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The name of a place, as a witch writes it: the signs drawn around a Window, counted and set in order. Which way a
 * sign faces, how large it is and where on the ring it sits do not matter, only which signs are there and how many.
 * Signs that already change what a glyph does (Tread, Diamond, Crosshair, Weave, Collection, Dancing Puppet, Float)
 * keep doing it and are no part of the name. A Window with fewer than two naming signs, or with a sign the ink could
 * not make out, has no name and is a plain Window.
 */
public final class WindowMark {
    private WindowMark() {}

    public static final Set<SignType> NOT_NAMES = EnumSet.of(SignType.TREAD, SignType.DIAMOND, SignType.CROSSHAIR, SignType.WEAVE,
            SignType.COLLECTION, SignType.DANCING_PUPPET, SignType.FLOAT);

    public static final int MIN_SIGNS = 2;

    private static final int REMEMBERED = 256;

    private static final Map<GlyphPages.Page, String> PAGES = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<GlyphPages.Page, String> eldest) {
            return size() > REMEMBERED;
        }
    };

    /** The mark of a read glyph, written like {@code BIRD*2+EYE}; empty when the glyph is not a marked Window. */
    public static String of(GlyphReading r) {
        if (r == null || !r.closed() || r.sigil() != null || r.nested() || !r.sides().isEmpty()
                || r.centerSign() != SignType.WINDOW || r.unknown() > 0) return "";
        Map<String, Integer> counts = new TreeMap<>();
        int all = 0;
        for (GlyphReading.ReadSign s : r.signs()) {
            if (NOT_NAMES.contains(s.type())) continue;
            counts.merge(s.type().name(), 1, Integer::sum);
            all++;
        }
        if (all < MIN_SIGNS) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (!sb.isEmpty()) sb.append('+');
            sb.append(e.getKey());
            if (e.getValue() > 1) sb.append('*').append(e.getValue());
        }
        return sb.toString();
    }

    /** The mark drawn on a page, remembered for the last pages asked about so a page held up is not read every tick. */
    public static String ofPage(GlyphPages.Page page) {
        if (page == null || page.isEmpty()) return "";
        synchronized (PAGES) {
            String known = PAGES.get(page);
            if (known != null) return known;
        }
        String mark = of(GlyphAnalyzer.analyze(PageService.pointsOf(page), PageService.PAGE_INK));
        synchronized (PAGES) {
            PAGES.put(page, mark);
        }
        return mark;
    }

    /** The mark on the open page of something held. Boots and dormant artifacts carry their own magic, not an address. */
    public static String ofStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        PageKind kind = PageKind.of(stack);
        if (kind == null || kind == PageKind.BOOTS || kind == PageKind.ARTIFACT) return "";
        GlyphPages gp = PageService.pages(stack, kind);
        return ofPage(gp.page(gp.current()));
    }

    /** The mark a player holds up, main hand first. */
    public static String held(Player p) {
        String main = ofStack(p.getMainHandItem());
        return main.isEmpty() ? ofStack(p.getOffhandItem()) : main;
    }

    /** The colour a mark shows in its rim: the same mark always gets the same colour, on every server and after every restart. */
    public static int tint(String mark) {
        if (mark == null || mark.isEmpty()) return 0x8A8898;
        int h = mark.hashCode() * 0x9E3779B1;
        float hue = ((h >>> 16) & 0xFFFF) / 65536f;
        float sat = 0.45f + ((h >>> 8) & 0xFF) / 255f * 0.25f;
        return Mth.hsvToRgb(hue, sat, 0.95f) & 0xFFFFFF;
    }
}
