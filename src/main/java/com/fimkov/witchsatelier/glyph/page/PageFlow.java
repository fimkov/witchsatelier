package com.fimkov.witchsatelier.glyph.page;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays a grimoire entry's wrapped lines out over as many spreads as it needs. The screen wraps the
 * text with the real font at the real page width and counts how many lines a page holds; this only
 * decides where each spread ends. Cuts fall between lines, so a word is never split, and a cut moves
 * back to the start of a paragraph when only a line or two of it would be left behind, or when only a
 * short remainder would run on and the whole paragraph then fits on the next spread.
 */
public final class PageFlow {
    private PageFlow() {}

    /**
     * @param blank which lines are empty (the gap between paragraphs)
     * @param para  which lines begin after a hard line break rather than a wrap
     * @param first how many lines the first spread holds
     * @param rest  how many lines a continuation spread holds
     * @return {start, end} line ranges, one per spread; a spread that is not the last keeps its
     *         bottom line free for the mark that says the text goes on
     */
    public static List<int[]> parts(boolean[] blank, boolean[] para, int first, int rest) {
        List<int[]> out = new ArrayList<>();
        int end = blank.length;
        while (end > 0 && blank[end - 1]) end--;
        int s = 0;
        while (s < end && blank[s]) s++;
        while (s < end) {
            int cap = Math.max(1, out.isEmpty() ? first : rest);
            if (end - s <= cap) {
                out.add(new int[]{s, end});
                break;
            }
            int room = Math.max(1, cap - 1);
            int cut = cut(blank, para, s, s + room, end, Math.max(1, rest));
            int e = cut;
            while (e > s + 1 && blank[e - 1]) e--;
            out.add(new int[]{s, e});
            s = cut;
            while (s < end && blank[s]) s++;
        }
        return out;
    }

    private static boolean starts(boolean[] blank, boolean[] para, int i) {
        return blank[i] || blank[i - 1] || para[i];
    }

    private static int cut(boolean[] blank, boolean[] para, int s, int cut, int end, int rest) {
        if (starts(blank, para, cut)) return cut;
        int b = -1;
        for (int i = cut - 1; i > s; i--) {
            if (starts(blank, para, i)) { b = i; break; }
        }
        int next = cut;
        while (next < end && !starts(blank, para, next)) next++;
        int spill = next - cut;
        if (b > s) {
            int left = cut - b, from = b;
            while (from < end && blank[from]) from++;
            boolean shortRemainder = end - cut <= Math.max(2, rest / 4);
            if (left <= 2 || (shortRemainder && end - from <= rest && left * 2 <= cut - s)) return b;
        }
        if (spill == 1 && cut - Math.max(s, b) >= 3) return cut - 1;
        return cut;
    }
}
