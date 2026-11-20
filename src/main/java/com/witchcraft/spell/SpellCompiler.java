package com.witchcraft.spell;

import com.witchcraft.glyph.model.Orientation;
import com.witchcraft.glyph.model.Sigil;
import com.witchcraft.glyph.model.SignType;
import com.witchcraft.glyph.recognize.GlyphReading;
import com.witchcraft.glyph.recognize.GlyphReading.ReadSign;
import com.witchcraft.glyph.recognize.Pt;
import com.witchcraft.glyph.surface.SurfacePlane;
import com.witchcraft.spell.SpellProgram.Form;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Turns what was read from a drawing into a runnable spell. The rules follow the source material:
 * size gives power, neatness gives duration and stability, unbalanced signs push the effect towards the side
 * that has more of them, reversed signs invert their effect.
 */
public final class SpellCompiler {
    private SpellCompiler() {}

    public static SpellProgram compile(GlyphReading r, SurfacePlane plane, double inkPotency) {
        return compile(r, plane, inkPotency, 1.0, 0.0);
    }

    /** Glyph drawn on a world surface. */
    public static SpellProgram compile(GlyphReading r, SurfacePlane plane, double inkPotency, double inkDuration, double inkMisfire) {
        return compile(r, plane, plane.toWorld(r.ring().center(), 0.05), 1.0, inkPotency, inkDuration, inkMisfire);
    }

    /**
     * @param plane       how the glyph sits in the world
     * @param origin      where its centre is
     * @param scale       blocks per drawing unit (pages are drawn in page units)
     * @param inkDuration duration multiplier of the ink mix
     * @param inkMisfire  extra backfire chance of the ink mix
     */
    public static SpellProgram compile(GlyphReading r, GlyphFrame plane, Vec3 origin, double scale, double inkPotency,
                                       double inkDuration, double inkMisfire) {
        if (r.core() != null) return nested(r, plane, origin, scale, inkPotency, inkDuration, inkMisfire);
        double radius = r.ring().radius() * scale;
        Vec3 normal = plane.normal();

        // ---- quality ----
        double ringQuality = Mth.clamp((r.ring().roundness() - 0.80) / 0.18, 0, 1);
        double symmetry = r.symmetry();
        double neat = r.neatness();
        long columns = r.count(SignType.COLUMN);
        double power = Mth.clamp(radius * radius * 4, 0.5, 60) * (0.5 + 0.5 * neat) * inkPotency
                * (1 + 0.1 * Math.max(0, columns - 2));
        double quality = (0.3 + 0.7 * ringQuality) * (0.5 + 0.5 * symmetry) * (0.5 + 0.5 * neat);
        double misfire = Mth.clamp(0.55 - neat, 0, 0.5) + 0.1 * r.unknown() + (symmetry < 0.3 ? 0.1 : 0) + inkMisfire;

        // ---- direction ----
        Vec3 dir = normal;
        boolean spread = false;
        double vortex = 0;
        double px = 0, py = 0;
        int dirSigns = 0, inward = 0, outward = 0;
        for (ReadSign s : r.signs()) {
            double ox = Math.cos(s.angle()), oy = Math.sin(s.angle());
            double tx = -oy, ty = ox;
            double sx, sy;
            switch (s.orientation()) {
                case OUTWARD -> { sx = ox; sy = oy; }
                case INWARD -> { sx = -ox; sy = -oy; }
                case CCW -> { sx = tx; sy = ty; }
                default -> { sx = -tx; sy = -ty; }
            }
            if (s.orientation() == Orientation.CCW) vortex += 1;
            if (s.orientation() == Orientation.CW) vortex -= 1;
            if (s.type() == SignType.DIRECTION) {
                dirSigns++;
                px += sx;
                py += sy;
                if (s.orientation() == Orientation.INWARD) inward++;
                if (s.orientation() == Orientation.OUTWARD) outward++;
            }
        }
        boolean focused = r.has(SignType.CONVERGENCE);
        if (dirSigns > 0) {
            double agree = Math.hypot(px, py) / dirSigns;
            if (agree > 0.5) {
                // all pointing the same way along the surface: shoot that way
                dir = normal.scale(0.5).add(plane.planeVector(px / dirSigns, py / dirSigns).normalize());
            } else if (outward > inward) {
                spread = true;
            } else if (inward > 0) {
                focused = true;
            }
        }
        Pt imb = r.imbalance();
        dir = dir.add(plane.planeVector(imb.x(), imb.y()).scale(1.2)).normalize();
        vortex = r.signs().isEmpty() ? 0 : vortex / r.signs().size();

        // ---- form ----
        Sigil element = r.sigil();
        SignType center = r.centerSign();
        boolean inverted = majorityInverted(r);
        Form form;
        if (element == null && center == null && r.signs().isEmpty() && r.unknown() == 0) {
            form = Form.BURST;
        } else if (element == null && center != null) {
            form = Form.CENTER_SIGN;
        } else if (element == null) {
            form = Form.FIZZLE;
        } else if (element == Sigil.EARTH && r.count(SignType.EYE) >= 2) {
            form = Form.PETRIFY;
        } else if (r.has(SignType.CRUSH)) {
            form = element == Sigil.EARTH ? (invertedOf(r, SignType.CRUSH) ? Form.RESTORE : Form.CRUSH) : Form.FIZZLE;
        } else if (r.has(SignType.BOLT)) {
            form = Form.BOLTS;
        } else if (r.has(SignType.RAIN)) {
            form = Form.RAIN;
        } else if (r.has(SignType.COLUMN)) {
            form = Form.BEAM;
        } else if (r.has(SignType.BIRD)) {
            form = Form.BIRD;
        } else if (r.has(SignType.DISPERSION)) {
            form = Form.SPILL;
        } else if (r.has(SignType.LEVITATION) || r.has(SignType.FLOAT)) {
            form = Form.LIFT;
            inverted = invertedOf(r, SignType.LEVITATION) || invertedOf(r, SignType.FLOAT);
        } else if (r.has(SignType.PULL)) {
            form = Form.PULL;
            inverted = invertedOf(r, SignType.PULL);
        } else if (dirSigns > 0) {
            form = Form.BEAM;
        } else {
            form = Form.MANIFEST;
        }

        int bolts = (int) Math.max(1, Math.round(r.count(SignType.BOLT) * (1 + power / 20)));
        int duration = duration(form, center, r, quality, radius, inkDuration, bolts);
        boolean gentle = r.has(SignType.RADIAL);
        boolean hidden = r.has(SignType.EYE) || r.has(SignType.BEND);
        int birds = (int) r.count(SignType.BIRD) + (r.centerSign() == SignType.BIRD ? 1 : 0);

        SpellProgram main = new SpellProgram(element, center, form, inverted, origin, normal, dir, radius, power, duration,
                focused, gentle, spread, vortex, bolts, misfire, hidden, birds).withMods(mods(r));

        // more than one way of shaping in the same ring: the first one leads, every other one drawn at least twice
        // works alongside it with the same element (Columns and Bolts: a pillar that also throws fire)
        java.util.List<SpellProgram> layers = new java.util.ArrayList<>();
        if (element != null && SHAPEABLE.contains(form)) {
            for (Object[] f : SHAPES) {
                Form other = (Form) f[0];
                if (other == form) continue;
                SignType[] signs = (SignType[]) f[1];
                long n = 0;
                boolean inv = false;
                for (SignType st : signs) { n += r.count(st); inv |= invertedOf(r, st); }
                if (n < 2) continue;
                int b = other == Form.BOLTS ? (int) Math.max(1, Math.round(n * (1 + power / 20))) : bolts;
                layers.add(new SpellProgram(element, null, other, inv, origin, normal, dir, radius, power * 0.7,
                        duration(other, null, r, quality, radius, inkDuration, b), focused, gentle, spread, vortex, b, misfire, hidden, 0));
            }
        }
        if (layers.isEmpty()) return main;
        return main.layered(element, radius, power * 0.85, duration, focused, birds, bolts, java.util.List.copyOf(layers), false)
                .withMods(mods(r));
    }

    /** Forms that can share a ring with another form. */
    private static final java.util.Set<Form> SHAPEABLE = java.util.EnumSet.of(Form.BEAM, Form.SPILL, Form.BOLTS, Form.LIFT, Form.PULL, Form.RAIN, Form.BIRD);
    /** Each shaping form and the signs that call it. */
    private static final Object[][] SHAPES = {
            {Form.BOLTS, new SignType[]{SignType.BOLT}},
            {Form.RAIN, new SignType[]{SignType.RAIN}},
            {Form.BEAM, new SignType[]{SignType.COLUMN}},
            {Form.SPILL, new SignType[]{SignType.DISPERSION}},
            {Form.LIFT, new SignType[]{SignType.LEVITATION, SignType.FLOAT}},
            {Form.PULL, new SignType[]{SignType.PULL}},
    };

    private static int duration(Form form, SignType center, GlyphReading r, double quality, double radius, double inkDuration, int bolts) {
        int base = switch (form) {
            case BEAM, MANIFEST -> 100;
            case SPILL, PULL -> 120;
            case LIFT -> 160;
            case RAIN -> 300;
            case BIRD -> 200;
            case CENTER_SIGN -> center == SignType.WINDOW ? 12000 : 600;
            case PETRIFY -> 90;
            case BOLTS -> 8 * (int) r.count(SignType.BOLT);
            default -> 1;
        };
        if (form == Form.BOLTS) return bolts * 4;
        return Math.max(1, (int) Math.round(base * quality * (1 + radius * 0.3) * inkDuration));
    }

    /**
     * A glyph inside another ring: the inner glyph is the spell; what is drawn in the band around it shapes it.
     * Repetition keeps it going far longer, Enlarge makes it stronger, Convergence focuses it, Bird sends birds,
     * Billowing softens every fall around it. A shaping sign in the band (Column, Dispersion, Levitation, Pull,
     * Rain, Bolt) adds a second effect of the same element; a sigil in the band adds that element too. Nothing
     * of it works before the outermost ring is closed.
     */
    private static SpellProgram nested(GlyphReading r, GlyphFrame plane, Vec3 origin, double scale, double inkPotency,
                                       double inkDuration, double inkMisfire) {
        SpellProgram core = compile(r.core(), plane, origin, scale, inkPotency, inkDuration, inkMisfire);
        if (core.form() == Form.FIZZLE || core.form() == Form.BURST) return core;
        double outerR = r.ring().radius() * scale;
        // the extra ring is extra ink: the whole thing is stronger than the inner glyph alone
        double power = core.power() * Math.min(1.6, Math.pow(outerR / Math.max(0.1, core.radius()), 0.8));
        long rep = r.count(SignType.REPETITION), enl = r.count(SignType.ENLARGE);
        int duration = (int) Math.round(core.duration() * (rep > 0 ? 3 : 1) * (0.85 + 0.15 * r.neatness() / 0.5));
        power *= 1 + 0.3 * Math.min(enl, 3);
        boolean focused = core.focused() || r.has(SignType.CONVERGENCE);
        int birds = core.birds() + (int) r.count(SignType.BIRD);
        boolean cushion = r.has(SignType.BILLOWING);

        java.util.List<SpellProgram> layers = new java.util.ArrayList<>(core.layers());
        Sigil layerElement = r.sigil() != null ? r.sigil() : core.element();
        Form layerForm = null;
        if (r.has(SignType.COLUMN)) layerForm = Form.BEAM;
        else if (r.has(SignType.DISPERSION)) layerForm = Form.SPILL;
        else if (r.has(SignType.LEVITATION) || r.has(SignType.FLOAT)) layerForm = Form.LIFT;
        else if (r.has(SignType.PULL)) layerForm = Form.PULL;
        else if (r.has(SignType.RAIN)) layerForm = Form.RAIN;
        else if (r.has(SignType.BOLT)) layerForm = Form.BOLTS;
        if (layerForm == null && r.sigil() != null && r.sigil() != core.element()) layerForm = core.form();
        if (layerForm != null && layerElement != null && !(layerForm == core.form() && layerElement == core.element())) {
            int bolts = layerForm == Form.BOLTS ? (int) Math.max(1, r.count(SignType.BOLT)) : core.bolts();
            SpellProgram layer = new SpellProgram(layerElement, null, layerForm, false, core.origin(), core.normal(), core.direction(),
                    outerR, power * 0.8, Math.max(duration, layerForm == Form.BOLTS ? bolts * 4 : 1), focused, core.gentle(),
                    core.spread(), core.vortex(), bolts, core.misfire(), core.hidden(), 0);
            layers.add(layer);
        }
        return core.layered(core.element(), core.radius(), power, duration, focused, birds, core.bolts(), java.util.List.copyOf(layers), cushion)
                .withMods(core.mods() | mods(r));
    }

    /** The signs that add a behaviour on top of the form. */
    static int mods(GlyphReading r) {
        int m = 0;
        if (r.has(SignType.CROSSHAIR)) m |= SpellProgram.AIM;
        if (r.has(SignType.DIAMOND) || r.centerSign() == SignType.DIAMOND) m |= SpellProgram.WARD;
        if (r.has(SignType.WEAVE)) m |= SpellProgram.BIND;
        if (r.has(SignType.DANCING_PUPPET) || r.centerSign() == SignType.DANCING_PUPPET) m |= SpellProgram.PUPPET;
        if (r.has(SignType.COLLECTION)) m |= SpellProgram.GATHER;
        if (r.has(SignType.TREAD)) m |= SpellProgram.TREAD;
        return m;
    }

    private static boolean invertedOf(GlyphReading r, SignType type) {
        int inv = 0, all = 0;
        for (ReadSign s : r.signs()) {
            if (s.type() != type) continue;
            all++;
            if (s.inverted()) inv++;
        }
        return all > 0 && inv * 2 > all;
    }

    private static boolean majorityInverted(GlyphReading r) {
        int inv = 0;
        for (ReadSign s : r.signs()) if (s.inverted()) inv++;
        return !r.signs().isEmpty() && inv * 2 > r.signs().size();
    }
}
