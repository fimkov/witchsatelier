package com.fimkov.witchsatelier.glyph.model;

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

    TREAD(false);

    public final boolean centerCapable;

    SignType(boolean centerCapable) {
        this.centerCapable = centerCapable;
    }
}
