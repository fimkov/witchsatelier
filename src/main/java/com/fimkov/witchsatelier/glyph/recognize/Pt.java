package com.fimkov.witchsatelier.glyph.recognize;

public record Pt(double x, double y) {
    public Pt plus(Pt o) { return new Pt(x + o.x, y + o.y); }
    public Pt minus(Pt o) { return new Pt(x - o.x, y - o.y); }
    public Pt scale(double s) { return new Pt(x * s, y * s); }
    public double dist(Pt o) { return Math.hypot(x - o.x, y - o.y); }
    public double len() { return Math.hypot(x, y); }

    public Pt rotate(double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        return new Pt(x * c - y * s, x * s + y * c);
    }
}
