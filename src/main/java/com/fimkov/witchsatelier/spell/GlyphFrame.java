package com.fimkov.witchsatelier.spell;

import net.minecraft.world.phys.Vec3;

public interface GlyphFrame {
    Vec3 normal();

    Vec3 planeVector(double du, double dv);

    static GlyphFrame facing(Vec3 look) {
        Vec3 n = look.normalize();
        Vec3 right = n.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(n).normalize();
        Vec3 r = right;
        return new GlyphFrame() {
            @Override public Vec3 normal() { return n; }
            @Override public Vec3 planeVector(double du, double dv) { return r.scale(du).add(up.scale(dv)); }
        };
    }
}
