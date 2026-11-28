package com.fimkov.witchsatelier.glyph.page;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageFlowTest {
    /** Lines from a sketch: '.' a wrapped text line, 'P' a line that starts a paragraph, ' ' a blank line. */
    private static List<int[]> flow(String sketch, int first, int rest) {
        boolean[] blank = new boolean[sketch.length()], para = new boolean[sketch.length()];
        for (int i = 0; i < blank.length; i++) {
            blank[i] = sketch.charAt(i) == ' ';
            para[i] = sketch.charAt(i) != '.' || i == 0;
        }
        return PageFlow.parts(blank, para, first, rest);
    }

    private static void coversEveryLine(String sketch, List<int[]> parts) {
        int next = 0;
        for (int[] p : parts) {
            assertTrue(p[1] > p[0], "empty spread");
            for (int i = next; i < p[0]; i++) assertEquals(' ', sketch.charAt(i), "line " + i + " skipped");
            next = p[1];
        }
        for (int i = next; i < sketch.length(); i++) assertEquals(' ', sketch.charAt(i), "line " + i + " dropped");
    }

    @Test
    void textThatFitsStaysOnOneSpread() {
        List<int[]> parts = flow("P....  P...", 15, 16);
        assertEquals(1, parts.size());
        assertArrayEquals(new int[]{0, 11}, parts.get(0));
    }

    @Test
    void exactlyFullPageNeedsNoContinuation() {
        assertEquals(1, flow("P..............", 15, 16).size());
    }

    @Test
    void longTextRunsOnAndLosesNothing() {
        String sketch = "P........ P......... P.......... P....... P.....";
        for (int cap = 3; cap <= 20; cap++) {
            List<int[]> parts = flow(sketch, cap, cap + 1);
            coversEveryLine(sketch, parts);
            assertTrue(parts.get(0)[1] - parts.get(0)[0] <= Math.max(1, cap - (parts.size() > 1 ? 1 : 0)));
            for (int i = 1; i < parts.size(); i++) {
                int room = cap + 1 - (i < parts.size() - 1 ? 1 : 0);
                assertTrue(parts.get(i)[1] - parts.get(i)[0] <= Math.max(1, room), "spread " + i + " overfull at " + cap);
            }
        }
    }

    @Test
    void continuationNeverStartsWithBlankLine() {
        String sketch = "P..... P..... P..... P.....";
        for (int cap = 3; cap <= 12; cap++) {
            for (int[] p : flow(sketch, cap, cap)) {
                assertNotEquals(' ', sketch.charAt(p[0]));
                assertNotEquals(' ', sketch.charAt(p[1] - 1));
            }
        }
    }

    @Test
    void shortTailOfParagraphMovesWhole() {
        // The second paragraph's first line would be left alone at the bottom: it moves on with the rest.
        List<int[]> parts = flow("P..........  P......", 15, 16);
        assertEquals(2, parts.size());
        assertArrayEquals(new int[]{0, 11}, parts.get(0));
        assertEquals(13, parts.get(1)[0]);
    }

    @Test
    void shortRemainderBreaksBetweenParagraphs() {
        // Cutting at the page edge would split the last paragraph; it fits whole on the next spread instead.
        List<int[]> parts = flow("P........ P......", 15, 16);
        assertEquals(2, parts.size());
        assertArrayEquals(new int[]{0, 9}, parts.get(0));
        assertArrayEquals(new int[]{10, 17}, parts.get(1));
    }

    @Test
    void longRemainderFillsThePageFirst() {
        // Much still to come: the page is filled to its last free line rather than left half empty.
        List<int[]> parts = flow("P........ P..................", 15, 16);
        assertArrayEquals(new int[]{0, 14}, parts.get(0));
        assertEquals(14, parts.get(1)[0]);
    }

    @Test
    void oneLineAlwaysFitsEvenOnATinyPage() {
        List<int[]> parts = flow("P.....", 0, 0);
        coversEveryLine("P.....", parts);
        assertEquals(6, parts.size());
    }
    @Test
    void randomTextsKeepEveryLineAndFitEverySpread() {
        java.util.Random rnd = new java.util.Random(7);
        for (int trial = 0; trial < 4000; trial++) {
            StringBuilder b = new StringBuilder();
            int n = 1 + rnd.nextInt(60);
            for (int i = 0; i < n; i++) {
                int c = rnd.nextInt(10);
                b.append(c == 0 ? ' ' : c == 1 ? 'P' : '.');
            }
            String sketch = b.toString();
            int first = rnd.nextInt(18), rest = rnd.nextInt(18);
            List<int[]> parts = flow(sketch, first, rest);
            if (sketch.isBlank()) {
                assertTrue(parts.isEmpty(), sketch);
                continue;
            }
            coversEveryLine(sketch, parts);
            for (int i = 0; i < parts.size(); i++) {
                int[] p = parts.get(i);
                if (i > 0) assertTrue(p[0] >= parts.get(i - 1)[1], "spreads overlap: " + sketch);
                int cap = Math.max(1, i == 0 ? first : rest);
                int room = i < parts.size() - 1 ? Math.max(1, cap - 1) : cap;
                assertTrue(p[1] - p[0] <= room, "spread " + i + " overfull: '" + sketch + "' " + first + "/" + rest);
                assertNotEquals(' ', sketch.charAt(p[0]), "blank at top: " + sketch);
                assertNotEquals(' ', sketch.charAt(p[1] - 1), "blank at bottom: " + sketch);
            }
        }
    }
}
