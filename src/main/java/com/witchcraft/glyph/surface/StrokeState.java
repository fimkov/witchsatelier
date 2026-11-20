package com.witchcraft.glyph.surface;

public enum StrokeState {
    /** Fresh ink, not part of a running spell. */
    IDLE,
    /** Part of a glyph whose spell is running. */
    ACTIVE,
    /** The spell ran its course and the ink is exhausted; inert until erased. */
    SPENT;

    public static StrokeState byId(int id) {
        StrokeState[] v = values();
        return id >= 0 && id < v.length ? v[id] : IDLE;
    }
}
