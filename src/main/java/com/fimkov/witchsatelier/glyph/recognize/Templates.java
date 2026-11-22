package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;

import java.util.ArrayList;
import java.util.List;

public final class Templates {
    public record Template(String id, Sigil sigil, SignType sign, boolean reversed, List<List<Pt>> strokes, Pt[] cloud) {
        static Template sigil(Sigil s, Shapes shape) {
            List<List<Pt>> st = shape.build();
            return new Template(s.name().toLowerCase(), s, null, false, st, PointCloud.normalize(st));
        }

        static Template sign(SignType t, boolean reversed, Shapes shape) {
            List<List<Pt>> st = shape.build();
            String id = t.name().toLowerCase() + (reversed ? "_reversed" : "");
            return new Template(id, null, t, reversed, st, PointCloud.normalize(st));
        }
    }

    public static final List<Template> SIGILS = new ArrayList<>();
    public static final List<Template> SIGNS = new ArrayList<>();

    static {
        SIGILS.add(Template.sigil(Sigil.FIRE, Shapes.of()
                .polygon(-0.7, -0.4, 0.7, -0.4, 0, 0.8)
                .line(0, 0.8, 0, -1)));

        SIGILS.add(Template.sigil(Sigil.FIRE, Shapes.of()
                .arc(0, -0.4, 0.75, 0, 180)
                .line(-0.75, -0.4, 0.75, -0.4)
                .line(0, 0.45, 0, -1)));
        SIGILS.add(Template.sigil(Sigil.FIRE, Shapes.of()
                .arc(0, -0.35, 0.7, 0, 180)
                .line(-0.7, -0.35, 0.7, -0.35)
                .line(0, 0.6, 0, -0.95)));
        SIGILS.add(Template.sigil(Sigil.WATER, Shapes.of()
                .wave(0, 0.3, -0.9, 0.9)
                .drop(-0.75, -0.2, 0.18, 90)
                .drop(0.75, 0.2, 0.18, -90)));
        SIGILS.add(Template.sigil(Sigil.EARTH, Shapes.of()
                .line(-0.8, 0.6, 0.8, 0.6)
                .poly(-0.5, 0.6, 0, -0.2, 0.5, 0.6)
                .line(0, 0.6, 0, -0.9)
                .dot(-0.95, 0.05)
                .dot(0.95, 0.05)));

        SIGILS.add(Template.sigil(Sigil.EARTH, Shapes.of()
                .polygon(-0.8, 0.6, 0.8, 0.6, 0, -0.22)
                .line(0, 0.6, 0, -0.95)
                .dot(-0.68, -0.22)
                .dot(0.68, -0.22)));
        SIGILS.add(Template.sigil(Sigil.WIND, Shapes.of()
                .arc(0, 0.3, 0.3, 0, 270)
                .arc(0, -0.3, 0.3, 90, -180)
                .line(-1, 0.4, -0.55, 0.15).line(-1, 0, -0.5, 0).line(-1, -0.4, -0.55, -0.15)
                .line(1, 0.4, 0.55, 0.15).line(1, 0, 0.5, 0).line(1, -0.4, 0.55, -0.15)));

        SIGILS.add(Template.sigil(Sigil.WIND, Shapes.of()
                .arc(0, 0.32, 0.28, 0, 270)
                .arc(0, -0.32, 0.28, 90, -180)
                .line(-0.95, 0.7, -0.45, 0.35).line(-1, 0, -0.5, 0).line(-0.95, -0.7, -0.45, -0.35)
                .line(0.95, 0.7, 0.45, 0.35).line(1, 0, 0.5, 0).line(0.95, -0.7, 0.45, -0.35)));
        SIGILS.add(Template.sigil(Sigil.LIGHT, Shapes.of()
                .polygon(0, 0.6, 0.6, 0, 0, -0.6, -0.6, 0)
                .line(-1, 0, 1, 0)
                .line(0, -1, 0, 1)));
        SIGILS.add(Template.sigil(Sigil.WIND_UNDERFOOT, Shapes.of()
                .circle(0, 0, 1)
                .spiral(0.3, 0, 0.45, 0.05, 1.2, 180)
                .spiral(-0.3, 0, 0.45, 0.05, 1.2, 0)));
        SIGILS.add(Template.sigil(Sigil.AERIFORMS, Shapes.of()
                .arc(0, 0.3, 0.3, 0, 270)
                .arc(0, -0.3, 0.3, 90, -180)
                .line(-1, 0, -0.45, 0).poly(-0.62, 0.17, -0.45, 0, -0.62, -0.17)
                .line(1, 0, 0.45, 0).poly(0.62, 0.17, 0.45, 0, 0.62, -0.17)
                .dot(-0.75, 0.6).dot(0.75, -0.6)));

        SIGILS.add(Template.sigil(Sigil.APPLE, Shapes.of().polygon(appleBody(true)).line(0, 0.4, 0.1, 0.95).drop(0.27, 0.78, 0.12, 15)));
        SIGILS.add(Template.sigil(Sigil.CRYSTAL, Shapes.of()
                .line(-1, -0.5, 0.5, 1).line(-1, -1, 1, 1).line(-0.5, -1, 1, 0.5)
                .line(-1, 0.5, 0.5, -1).line(-1, 1, 1, -1).line(-0.5, 1, 1, -0.5)));

        sign(SignType.COLUMN, Shapes.of().line(0, -1, 0, 1).line(-0.5, 1, 0.5, 1));
        sign(SignType.COLUMN, Shapes.of().line(0, -0.8, 0, 0.8).line(-0.8, 0.8, 0.8, 0.8));
        sign(SignType.COLUMN, Shapes.of().line(0, -0.6, 0, 0.6).line(-1, 0.6, 1, 0.6));
        sign(SignType.DISPERSION, Shapes.of().line(0, -0.2, 0, 1).line(-0.5, -0.2, 0.5, -0.2).arc(0, -0.2, 0.5, 180, 360));
        sign(SignType.LEVITATION, Shapes.of().line(0, -1, 0, 1).poly(-0.4, 0.6, 0, 1, 0.4, 0.6).line(-0.4, -1, 0.4, -1));
        sign(SignType.PULL, Shapes.of().line(0, 1, 0, -1)
                .poly(-0.45, -0.15, 0, -0.55, 0.45, -0.15)
                .poly(-0.45, -0.6, 0, -1, 0.45, -0.6));
        sign(SignType.CRUSH, Shapes.of().poly(-1, -0.3, -0.5, 0.3, 0, -0.3, 0.5, 0.3, 1, -0.3));
        sign(SignType.FLOAT, Shapes.of().wave(-0.35, 0.22, -1, 1).wave(0.35, 0.22, -1, 1));
        sign(SignType.DIRECTION, Shapes.of().poly(-0.8, -0.5, 0, 0.5, 0.8, -0.5));
        sign(SignType.CONVERGENCE, Shapes.of().polygon(-0.8, 0.6, 0.8, 0.6, 0, -0.8));
        sign(SignType.DIAMOND, Shapes.of().polygon(0, 1, 0.5, 0, 0, -1, -0.5, 0));
        sign(SignType.WINDOW, Shapes.of().polygon(-0.6, -0.6, 0.6, -0.6, 0.6, 0.6, -0.6, 0.6).line(-1, 0, 1, 0).line(0, -1, 0, 1));
        sign(SignType.COLLECTION, Shapes.of().poly(-0.7, 1, 0.7, 1, -0.7, -1, 0.7, -1, -0.7, 1));
        sign(SignType.CROSSHAIR, Shapes.of().line(0, 0.3, 0, 1).line(0, -0.3, 0, -1).line(0.3, 0, 1, 0).line(-0.3, 0, -1, 0));
        sign(SignType.RADIAL, Shapes.of().arc(0, -0.5, 1, 0, 180).arc(0, -0.5, 0.55, 0, 180));
        sign(SignType.BOLT, Shapes.of().line(0, -1, 0, 1).polygon(0, 0.35, 0.22, 0, 0, -0.35, -0.22, 0));
        sign(SignType.BILLOWING, Shapes.of().circle(0, 0.5, 0.35).circle(0, -0.5, 0.35).circle(0.5, 0, 0.35).circle(-0.5, 0, 0.35));
        sign(SignType.EYE, Shapes.of().arc(0, -0.6, 1.17, 31, 149).arc(0, 0.6, 1.17, 211, 329).circle(0, 0, 0.15));
        sign(SignType.BEND, Shapes.of().poly(-0.35, 0.65, 0, 1, 0, -1));
        sign(SignType.REPETITION, Shapes.of()
                .arc(0, -0.5, 0.95, 30, 150).arc(0, 0.5, 0.95, 210, 330)
                .spiral(0, 0, 0.32, 0.05, 1.3, 0)
                .line(-0.3, -0.45, -0.45, -0.85).line(0, -0.5, 0, -0.95).line(0.3, -0.45, 0.45, -0.85));
        sign(SignType.VISION, Shapes.of().line(-1, 0, 1, 0).line(0, -1, 0, 1).line(-0.7, -0.7, 0.7, 0.7).line(-0.7, 0.7, 0.7, -0.7));
        sign(SignType.WEAVE, Shapes.of().arc(0, 0.1, 0.8, -60, 240).line(-0.4, -0.59, -0.75, -0.9).line(0.4, -0.59, 0.75, -0.9));

        sign(SignType.ENLARGE, false, enlarge(true));
        sign(SignType.ENLARGE, true, enlarge(false));
        sign(SignType.RAIN, Shapes.of()
                .polygon(0, 1, 0.3, 0.3, 1, 0, 0.3, -0.3, 0, -1, -0.3, -0.3, -1, 0, -0.3, 0.3)
                .line(0.45, 0.45, 0.75, 0.75).line(-0.45, 0.45, -0.75, 0.75)
                .line(0.45, -0.45, 0.75, -0.75).line(-0.45, -0.45, -0.75, -0.75));

        sign(SignType.BIRD, Shapes.of()
                .arc(0, -0.35, 0.95, 20, 160)
                .line(0, 0.95, 0, -0.55)
                .poly(-0.32, -0.95, 0, -0.55, 0.32, -0.95));

        sign(SignType.TREAD, Shapes.of().poly(-0.9, -0.9, -0.9, -0.3, -0.3, -0.3, -0.3, 0.3, 0.3, 0.3, 0.3, 0.9, 0.9, 0.9));
        sign(SignType.DANCING_PUPPET, Shapes.of()
                .circle(0, 0.75, 0.2).circle(0, -0.75, 0.2).circle(0.75, 0, 0.2).circle(-0.75, 0, 0.2)
                .arc(0, 0, 0.75, 15, 75).arc(0, 0, 0.75, 105, 165).arc(0, 0, 0.75, 195, 255).arc(0, 0, 0.75, 285, 345));
    }

    private static double[] appleBody(boolean dented) {
        int n = 36;
        double[] xy = new double[n * 2];
        for (int i = 0; i < n; i++) {
            double deg = 90 + i * 360.0 / n;
            double top = (deg - 90 + 180) % 360 - 180, bottom = (deg - 270 + 180) % 360 - 180;
            double r = 0.75 * (1 - (dented ? 0.24 * Math.exp(-Math.pow(top / 30, 2)) + 0.07 * Math.exp(-Math.pow(bottom / 30, 2)) : 0));
            xy[i * 2] = Math.cos(Math.toRadians(deg)) * r * 1.08;
            xy[i * 2 + 1] = -0.15 + Math.sin(Math.toRadians(deg)) * r;
        }
        return xy;
    }

    private static Shapes enlarge(boolean outward) {
        Shapes s = Shapes.of();
        for (int qx = -1; qx <= 1; qx += 2) {
            for (int qy = -1; qy <= 1; qy += 2) {
                double vx = outward ? qx * 1.0 : qx * 0.25, vy = outward ? qy * 1.0 : qy * 0.25;
                double dir = outward ? -1 : 1;
                s.poly(vx + dir * qx * 0.55, vy, vx, vy, vx, vy + dir * qy * 0.55);
                double ix = vx + dir * qx * 0.18, iy = vy + dir * qy * 0.18;
                s.poly(ix + dir * qx * 0.37, iy, ix, iy, ix, iy + dir * qy * 0.37);
            }
        }
        return s;
    }

    private static void sign(SignType t, Shapes shape) {
        sign(t, false, shape);
    }

    private static void sign(SignType t, boolean reversed, Shapes shape) {
        SIGNS.add(Template.sign(t, reversed, shape));
    }

    private Templates() {}
}
