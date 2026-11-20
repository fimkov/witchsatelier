package com.witchcraft.glyph.model;

/** How a sign is turned relative to the glyph centre. Templates are defined pointing OUTWARD. */
public enum Orientation {
    /** As in the template: its "top" faces away from the centre. */
    OUTWARD,
    /** Flipped: its "top" faces the centre. A reversed sign gives the opposite effect. */
    INWARD,
    /** Turned along the ring, counter-clockwise. */
    CCW,
    /** Turned along the ring, clockwise. */
    CW
}
