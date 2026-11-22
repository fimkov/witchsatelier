package com.fimkov.witchsatelier.glyph.model;

/**
 * Signs (keystones): how and where the element manifests. Names follow the fan wiki's "Signs Explained".
 * {@code centerCapable} signs may take the place of a sigil in the middle of the glyph.
 */
public enum SignType {
    COLUMN(false),
    DISPERSION(false),
    LEVITATION(false),
    PULL(false),
    CRUSH(false),
    FLOAT(false),
    DIRECTION(false),
    CONVERGENCE(false),
    DIAMOND(true),
    WINDOW(true),
    COLLECTION(false),
    CROSSHAIR(false),
    RADIAL(false),
    BOLT(false),
    BILLOWING(true),
    EYE(false),
    BEND(false),
    REPETITION(true),
    VISION(true),
    WEAVE(false),
    ENLARGE(true),
    RAIN(true),
    BIRD(true),
    DANCING_PUPPET(true),
    /** Tread: the glyph wakes but holds its spell until a creature steps onto it (a snare). */
    TREAD(false);

    public final boolean centerCapable;

    SignType(boolean centerCapable) {
        this.centerCapable = centerCapable;
    }
}
