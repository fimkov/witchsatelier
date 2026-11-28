package com.fimkov.witchsatelier.glyph.page;

import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.page.GlyphPages.Page;
import com.fimkov.witchsatelier.glyph.page.GlyphPages.PageStroke;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

public final class Illustrations {
    private Illustrations() {}

    public static Page of(String entry, RandomSource rnd) {
        List<PageStroke> out = new ArrayList<>();
        Pen p = new Pen(out, rnd, GlyphLibrary.FORBIDDEN_BOOK.entries().contains(entry) ? InkType.BLOOD : InkType.LASTING);
        switch (entry) {
            case "forbidden_magic" -> {
                p.arc(0, 0, 0.85, 0, 360);
                p.arc(0, -0.28, 0.48, 35, 145);
                p.arc(0, 0.28, 0.48, 215, 325);
                p.arc(0, 0, 0.13, 0, 360);
                p.line(-0.72, 0.72, 0.72, -0.72);
                p.line(-0.72, -0.72, 0.72, 0.72);
            }
            case "the_pact" -> {
                p.poly(-0.5, -0.7, 0.5, -0.7, 0.5, 0.7, -0.5, 0.7, -0.5, -0.7);
                p.line(-0.36, -0.7, -0.36, 0.7);
                p.arc(0.07, 0, 0.22, 0, 360);
                p.line(0.07, 0.22, 0.07, -0.22);
                p.line(-0.15, 0, 0.29, 0);
                p.poly(0.29, 0.06, 0.62, 0.06, 0.62, -0.06, 0.29, -0.06);
            }
            case "blood_ink" -> {
                p.poly(-0.16, 0.3, -0.16, 0.12, -0.5, -0.12, -0.5, -0.72, 0.5, -0.72, 0.5, -0.12, 0.16, 0.12, 0.16, 0.3);
                p.poly(-0.22, 0.3, 0.22, 0.3, 0.22, 0.44, -0.22, 0.44, -0.22, 0.3);
                p.line(-0.5, -0.36, 0.5, -0.36);
                p.drop(0, 0.72, 0.11);
            }
            case "brimhats" -> {
                p.ellipse(0, 0.2, 0.86, 0.16);
                p.poly(-0.36, 0.28, -0.12, 0.86, 0.08, 0.92, 0.3, 0.8, 0.18, 0.74, 0.36, 0.28);
                p.ellipse(0, -0.42, 0.34, 0.42);
                p.ellipse(-0.14, -0.3, 0.08, 0.05);
                p.ellipse(0.14, -0.3, 0.08, 0.05);
                p.line(-0.1, -0.62, 0.1, -0.62);
            }
            case "knights_moralis" -> {
                p.line(-0.55, -0.88, -0.55, 0.86);
                p.poly(-0.55, 0.78, -0.1, 0.66, 0.3, 0.6, 0.82, 0.46, 0.3, 0.38, -0.1, 0.32, -0.55, 0.22);
                p.arc(-0.15, 0.5, 0.12, 0, 360);
                p.line(-0.55, 0.86, -0.48, 0.92);
            }
            case "page_held_up" -> {
                // a window in a wall, and through it another window far away
                p.line(0.05, -0.75, 0.05, 0.15);
                p.arc(0.45, 0.15, 0.4, 0, 180);
                p.line(0.85, 0.15, 0.85, -0.75);
                p.line(0.0, -0.8, 0.9, -0.8);
                p.line(0.13, -0.45, 0.77, -0.45);
                p.line(0.36, -0.45, 0.36, -0.12);
                p.arc(0.45, -0.12, 0.09, 0, 180);
                p.line(0.54, -0.12, 0.54, -0.45);
                // a page held up before it, with a little window drawn on it
                p.poly(-0.88, -0.25, -0.42, -0.18, -0.36, 0.42, -0.82, 0.35, -0.88, -0.25);
                p.arc(-0.62, 0.09, 0.15, 30, 330);
                p.line(-0.66, 0.02, -0.66, 0.1);
                p.arc(-0.62, 0.1, 0.04, 0, 180);
                p.line(-0.58, 0.1, -0.58, 0.02);
                // the hand that holds it
                p.arc(-0.55, -0.36, 0.13, 200, 340);
                p.poly(-0.49, -0.42, -0.46, -0.27, -0.5, -0.17);
                p.line(-0.67, -0.4, -0.72, -0.95);
                p.line(-0.43, -0.4, -0.4, -0.95);
                // and the look that passes from one to the other
                p.line(-0.3, 0.14, -0.24, 0.13);
                p.line(-0.19, 0.12, -0.13, 0.11);
                p.line(-0.08, 0.1, -0.02, 0.09);
            }
            default -> {
                return null;
            }
        }
        return new Page(List.copyOf(out));
    }

    private record Pen(List<PageStroke> out, RandomSource rnd, InkType ink) {
        float w() {
            return (float) ((rnd.nextDouble() - 0.5) * 0.012);
        }

        void add(List<Float> pts) {
            float[] f = new float[pts.size()];
            for (int i = 0; i < f.length; i++) f[i] = pts.get(i) + w();
            out.add(new PageStroke(f, ink));
        }

        void line(double x0, double y0, double x1, double y1) {
            List<Float> pts = new ArrayList<>();
            int n = Math.max(2, (int) (Math.hypot(x1 - x0, y1 - y0) / 0.04));
            for (int i = 0; i <= n; i++) {
                double t = i / (double) n;
                pts.add((float) (x0 + (x1 - x0) * t));
                pts.add((float) (y0 + (y1 - y0) * t));
            }
            add(pts);
        }

        void poly(double... xy) {
            for (int i = 0; i + 3 < xy.length; i += 2) line(xy[i], xy[i + 1], xy[i + 2], xy[i + 3]);
        }

        void ellipse(double cx, double cy, double rx, double ry) {
            List<Float> pts = new ArrayList<>();
            int n = Math.max(12, (int) ((rx + ry) * 40));
            for (int i = 0; i <= n; i++) {
                double a = Math.PI * 2 * i / n;
                pts.add((float) (cx + Math.cos(a) * rx));
                pts.add((float) (cy + Math.sin(a) * ry));
            }
            add(pts);
        }

        void arc(double cx, double cy, double r, double fromDeg, double toDeg) {
            List<Float> pts = new ArrayList<>();
            int n = Math.max(8, (int) (r * Math.toRadians(toDeg - fromDeg) / 0.04));
            for (int i = 0; i <= n; i++) {
                double a = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / n);
                pts.add((float) (cx + Math.cos(a) * r));
                pts.add((float) (cy + Math.sin(a) * r));
            }
            add(pts);
        }

        void drop(double cx, double cy, double r) {
            List<Float> pts = new ArrayList<>();
            pts.add((float) cx);
            pts.add((float) (cy + r * 2.2));
            for (int i = 0; i <= 16; i++) {
                double a = Math.toRadians(30 - 240 * i / 16.0);
                pts.add((float) (cx + Math.cos(a) * r));
                pts.add((float) (cy + Math.sin(a) * r));
            }
            pts.add((float) cx);
            pts.add((float) (cy + r * 2.2));
            add(pts);
        }
    }
}
