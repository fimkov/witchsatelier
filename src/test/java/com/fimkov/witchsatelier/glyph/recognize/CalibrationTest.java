package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.glyph.recognize.Templates.Template;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class CalibrationTest {
    @Test
    void printNearestOtherTemplateDistances() {
        List<Template> all = new ArrayList<>(Templates.SIGILS);
        all.addAll(Templates.SIGNS);
        double globalMin = Double.POSITIVE_INFINITY;
        for (Template a : all) {
            double min = Double.POSITIVE_INFINITY;
            String who = "";
            for (Template b : all) {
                if (a == b || (a.sign() != null && a.sign() == b.sign())) continue;
                for (int rot = 0; rot < 4; rot++) {
                    double d = PointCloud.distance(PointCloud.rotate(a.cloud(), rot * Math.PI / 2), b.cloud());
                    if (d < min) { min = d; who = b.id() + "@" + rot * 90; }
                }
            }
            globalMin = Math.min(globalMin, min);
            System.out.printf("CAL %-20s nearest other %-24s d=%.2f%n", a.id(), who, min);
        }
        Random rnd = new Random(7);
        for (int k = 0; k < 10; k++) {
            List<List<Pt>> scribble = new ArrayList<>();
            List<Pt> s = new ArrayList<>();
            Pt p = new Pt(0, 0);
            for (int i = 0; i < 30; i++) { p = p.plus(new Pt(rnd.nextGaussian() * 0.2, rnd.nextGaussian() * 0.2)); s.add(p); }
            scribble.add(s);
            SymbolRecognizer.Match m = SymbolRecognizer.classifySign(scribble, Math.PI / 2);
            SymbolRecognizer.Match c = SymbolRecognizer.classifyCenter(scribble);
            System.out.printf("CAL scribble %d sign-> %s d=%.2f   centre-> %s d=%.2f%n", k, m.template().id(), m.distance(), c.template().id(), c.distance());
        }
        System.out.printf("CAL global min cross distance %.2f%n", globalMin);
    }
}
