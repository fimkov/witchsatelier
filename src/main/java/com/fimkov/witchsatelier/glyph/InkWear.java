package com.fimkov.witchsatelier.glyph;

/**
 * How worn a point of ink is: one byte a point. The low seven bits say how far gone it is (0 fresh, {@link #MAX} gone);
 * the high bit says rain washed it (the ink runs and bleeds) rather than feet scuffing it (the ink smears and breaks up).
 * A point worn to {@link #CUT} is gone, and the line has a gap there.
 */
public final class InkWear {
    public static final int MAX = 127, CUT = 120;

    private InkWear() {}

    public static int amount(byte b) {
        return b & 0x7F;
    }

    public static boolean washed(byte b) {
        return (b & 0x80) != 0;
    }

    /** How far gone the point is, 0..1. */
    public static double frac(byte b) {
        return amount(b) / (double) MAX;
    }

    /** Adds wear to a point; it keeps the look of whatever wore it first, as long as that is most of the wear. */
    public static byte add(byte b, double by, boolean washed) {
        int old = amount(b);
        int now = (int) Math.min(MAX, Math.round(old + by));
        boolean kind = old == 0 || by > old ? washed : washed(b);
        return (byte) (now | (kind ? 0x80 : 0));
    }
}
