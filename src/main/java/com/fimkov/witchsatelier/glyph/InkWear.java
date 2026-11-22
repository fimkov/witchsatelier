package com.fimkov.witchsatelier.glyph;

public final class InkWear {
    public static final int MAX = 127, CUT = 120;

    private InkWear() {}

    public static int amount(byte b) {
        return b & 0x7F;
    }

    public static boolean washed(byte b) {
        return (b & 0x80) != 0;
    }

    public static double frac(byte b) {
        return amount(b) / (double) MAX;
    }

    public static byte add(byte b, double by, boolean washed) {
        int old = amount(b);
        int now = (int) Math.min(MAX, Math.round(old + by));
        boolean kind = old == 0 || by > old ? washed : washed(b);
        return (byte) (now | (kind ? 0x80 : 0));
    }
}
