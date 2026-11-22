package com.fimkov.witchsatelier.glyph.surface;

public enum StrokeState {
    IDLE,

    ACTIVE,

    SPENT;

    public static StrokeState byId(int id) {
        StrokeState[] v = values();
        return id >= 0 && id < v.length ? v[id] : IDLE;
    }
}
