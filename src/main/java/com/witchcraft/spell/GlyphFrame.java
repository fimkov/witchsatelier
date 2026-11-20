package com.witchcraft.spell;

import net.minecraft.world.phys.Vec3;

/**
 * Orientation of a glyph in the world: which way it faces and how its drawing axes (u right, v up) map to world
 * space. A glyph on a wall uses the wall's plane; a glyph on a page held in the hand faces where the caster looks.
 */
public interface GlyphFrame {
    Vec3 normal();

    /** World vector for an in-glyph direction (du, dv). */
    Vec3 planeVector(double du, double dv);

    /** Frame of a page held up in front of the eyes: facing the look direction, u to the caster's right. */
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
