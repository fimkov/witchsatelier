package com.fimkov.witchsatelier.glyph.page;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Glyphs drawn on the pages of an item (a sheet of paper, a book, a palm quire).
 * Page coordinates run from -1 to 1 across the page, v pointing up.
 */
public record GlyphPages(List<Page> pages, int current) {

    public record PageStroke(float[] pts, InkType ink) {
        static final Codec<float[]> FLOATS = Codec.FLOAT.listOf().xmap(l -> {
            float[] f = new float[l.size()];
            for (int i = 0; i < f.length; i++) f[i] = l.get(i);
            return f;
        }, f -> {
            List<Float> l = new ArrayList<>(f.length);
            for (float x : f) l.add(x);
            return l;
        });
        public static final Codec<PageStroke> CODEC = RecordCodecBuilder.create(i -> i.group(
                FLOATS.fieldOf("pts").forGetter(PageStroke::pts),
                InkType.CODEC.fieldOf("ink").forGetter(PageStroke::ink)
        ).apply(i, PageStroke::new));
        public static final StreamCodec<ByteBuf, PageStroke> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> { InkStroke.writeFloats(buf, s.pts); InkType.STREAM_CODEC.encode(buf, s.ink); },
                buf -> new PageStroke(InkStroke.readFloats(buf), InkType.STREAM_CODEC.decode(buf)));

        public List<Pt> points() {
            List<Pt> out = new ArrayList<>(pts.length / 2);
            for (int i = 0; i + 1 < pts.length; i += 2) out.add(new Pt(pts[i], pts[i + 1]));
            return out;
        }

        public double length() {
            double d = 0;
            for (int i = 2; i + 1 < pts.length; i += 2) d += Math.hypot(pts[i] - pts[i - 2], pts[i + 1] - pts[i - 1]);
            return d;
        }

        @Override public boolean equals(Object o) {
            return o instanceof PageStroke p && p.ink == ink && java.util.Arrays.equals(p.pts, pts);
        }

        @Override public int hashCode() {
            return java.util.Arrays.hashCode(pts) * 31 + ink.hashCode();
        }
    }

    public record Page(List<PageStroke> strokes) {
        public static final Page EMPTY = new Page(List.of());
        public static final Codec<Page> CODEC = PageStroke.CODEC.listOf().xmap(Page::new, Page::strokes);
        public static final StreamCodec<ByteBuf, Page> STREAM_CODEC =
                PageStroke.STREAM_CODEC.apply(ByteBufCodecs.list(512)).map(Page::new, Page::strokes);

        public boolean isEmpty() { return strokes.isEmpty(); }

        public Page with(PageStroke s) {
            List<PageStroke> l = new ArrayList<>(strokes);
            l.add(s);
            return new Page(List.copyOf(l));
        }
    }

    public static final Codec<GlyphPages> CODEC = RecordCodecBuilder.create(i -> i.group(
            Page.CODEC.listOf().fieldOf("pages").forGetter(GlyphPages::pages),
            Codec.INT.fieldOf("current").forGetter(GlyphPages::current)
    ).apply(i, GlyphPages::new));
    public static final StreamCodec<ByteBuf, GlyphPages> STREAM_CODEC = StreamCodec.composite(
            Page.STREAM_CODEC.apply(ByteBufCodecs.list(64)), GlyphPages::pages,
            ByteBufCodecs.VAR_INT, GlyphPages::current, GlyphPages::new);

    public static GlyphPages blank(int count) {
        List<Page> l = new ArrayList<>();
        for (int i = 0; i < count; i++) l.add(Page.EMPTY);
        return new GlyphPages(List.copyOf(l), 0);
    }

    public Page page(int i) {
        return i >= 0 && i < pages.size() ? pages.get(i) : Page.EMPTY;
    }

    public GlyphPages withPage(int i, Page p) {
        List<Page> l = new ArrayList<>(pages);
        l.set(i, p);
        return new GlyphPages(List.copyOf(l), current);
    }

    public GlyphPages withCurrent(int c) {
        return new GlyphPages(pages, Math.floorMod(c, pages.size()));
    }
}
