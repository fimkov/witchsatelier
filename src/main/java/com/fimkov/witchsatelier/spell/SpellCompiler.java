package com.fimkov.witchsatelier.spell;

import com.fimkov.witchsatelier.glyph.model.Orientation;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.recognize.GlyphReading.ReadSign;
import com.fimkov.witchsatelier.glyph.recognize.GlyphReading;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.spell.SpellProgram.Form;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SpellCompiler {
    private SpellCompiler() {}

    public static SpellProgram compile(GlyphReading r, SurfacePlane plane, double inkPotency) {
        return compile(r, plane, inkPotency, 1.0, 0.0);
    }

    public static SpellProgram compile(GlyphReading r, SurfacePlane plane, double inkPotency, double inkDuration, double inkMisfire) {
        return compile(r, plane, plane.toWorld(r.ring().center(), 0.05), 1.0, inkPotency, inkDuration, inkMisfire);
    }

    public static SpellProgram compile(GlyphReading r, GlyphFrame plane, Vec3 origin, double scale, double inkPotency,
                                       double inkDuration, double inkMisfire) {
        if (r.core() != null) return nested(r, plane, origin, scale, inkPotency, inkDuration, inkMisfire);
        double radius = r.ring().radius() * scale;
        Vec3 normal = plane.normal();

        double ringQuality = Mth.clamp((r.ring().roundness() - 0.80) / 0.18, 0, 1);
        double symmetry = r.symmetry();
        double neat = r.neatness();
        long columns = r.count(SignType.COLUMN);
        double power = Mth.clamp(radius * radius * 4, 0.5, 60) * (0.5 + 0.5 * neat) * inkPotency
                * (1 + 0.1 * Math.max(0, columns - 2)) * signWeight(r);
        double quality = (0.3 + 0.7 * ringQuality) * (0.5 + 0.5 * symmetry) * (0.5 + 0.5 * neat);
        double misfire = Mth.clamp(0.55 - neat, 0, 0.5) + 0.1 * r.unknown() + (symmetry < 0.3 ? 0.1 : 0) + inkMisfire;

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

        Sigil element = r.sigil();
        SignType center = r.centerSign();
        boolean inverted = majorityInverted(r);
        Form form;
        if (element == null && center == null && r.signs().isEmpty() && r.unknown() == 0) {
            form = Form.BURST;
        } else if (element == null && center == SignType.VISION && r.has(SignType.PULL)) {
            form = Form.OBLIVION;
        } else if (element == null && center != null) {
            form = Form.CENTER_SIGN;
        } else if (element == null || element == Sigil.APPLE) {
            form = Form.FIZZLE;
        } else if (element == Sigil.EARTH && r.count(SignType.EYE) >= 2) {
            form = Form.PETRIFY;
        } else if (element == Sigil.WATER && r.has(SignType.CRUSH)) {
            form = Form.MANIFEST;
        } else if (r.has(SignType.HORSE)) {
            form = Form.MANIFEST;
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

        List<SpellProgram> layers = new ArrayList<>();
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

        for (Sigil side : new LinkedHashSet<>(r.sides())) {
            if (element == null || side == element || side == Sigil.APPLE || !SHAPEABLE.contains(form)) continue;
            layers.add(new SpellProgram(side, null, form, inverted, origin, normal, dir, radius, power * 0.6, duration,
                    focused, gentle, spread, vortex, bolts, misfire, hidden, 0));
        }
        int sideMods = sideMods(element, r) | shapeMods(element, r, dirSigns) | regionMods(r);
        if (r.has(SignType.TREAD) && invertedOf(r, SignType.TREAD)) sideMods |= SpellProgram.TREAD_INVERTED;
        if (layers.isEmpty()) return main.withMods(mods(r) | sideMods);
        return main.layered(element, radius, power * 0.85, duration, focused, birds, bolts, List.copyOf(layers), false)
                .withMods(mods(r) | sideMods);
    }

    static int regionMods(GlyphReading r) {
        int n = 0, in = 0;
        double sx = 0, sy = 0;
        for (ReadSign s : r.signs()) {
            if (s.type() != SignType.REGION) continue;
            n++;
            if (s.inverted()) in++;
            double ox = Math.cos(s.angle()), oy = Math.sin(s.angle());
            switch (s.orientation()) {
                case OUTWARD -> { sx += ox; sy += oy; }
                case INWARD -> { sx -= ox; sy -= oy; }
                case CCW -> { sx -= oy; sy += ox; }
                default -> { sx += oy; sy -= ox; }
            }
        }
        if (n == 0) return 0;
        if (n >= 2 && Math.hypot(sx, sy) / n > 0.7) {
            int octant = (int) Math.round(Math.atan2(sy, sx) / (Math.PI / 4)) & 7;
            return SpellProgram.REGION_SIDE | (octant << SpellProgram.REGION_OCTANT);
        }
        if (in == n) return SpellProgram.REGION_IN;
        if (in == 0) return SpellProgram.REGION_OUT;
        return SpellProgram.REGION_IN | SpellProgram.REGION_OUT;
    }

    static int shapeMods(Sigil element, GlyphReading r, int dirSigns) {
        int m = 0;
        if (element == Sigil.WATER && r.has(SignType.CRUSH)) m |= SpellProgram.CAGE;
        if (element == Sigil.WATER && r.has(SignType.HORSE)) m |= SpellProgram.STEED;
        if (element == Sigil.WIND && r.count(SignType.COLUMN) >= 2 && r.count(SignType.CONVERGENCE) >= 2) m |= SpellProgram.WALL;
        if (element == Sigil.EARTH && r.has(SignType.PULL) && dirSigns > 0) m |= SpellProgram.BEND;
        return m;
    }

    static int sideMods(Sigil middle, GlyphReading r) {
        if (r.sides().isEmpty()) return 0;
        int m = 0;
        boolean airy = middle == Sigil.WIND || middle == Sigil.AERIFORMS || middle == Sigil.WIND_UNDERFOOT;
        if (airy && r.sides().contains(Sigil.WATER)) m |= SpellProgram.MIST;
        if (middle == Sigil.WATER && r.sides().contains(Sigil.FIRE)) m |= SpellProgram.DRY;
        if (r.sides().contains(Sigil.LIGHT) && middle != Sigil.LIGHT) m |= SpellProgram.REPEL;
        return m;
    }

    private static final Set<Form> SHAPEABLE = EnumSet.of(Form.BEAM, Form.SPILL, Form.BOLTS, Form.LIFT, Form.PULL, Form.RAIN, Form.BIRD);

    private static final Object[][] SHAPES = {
            {Form.BOLTS, new SignType[]{SignType.BOLT}},
            {Form.RAIN, new SignType[]{SignType.RAIN}},
            {Form.BEAM, new SignType[]{SignType.COLUMN}},
            {Form.SPILL, new SignType[]{SignType.DISPERSION}},
            {Form.LIFT, new SignType[]{SignType.LEVITATION, SignType.FLOAT}},
            {Form.PULL, new SignType[]{SignType.PULL}},
    };

    private static int duration(Form form, SignType center, GlyphReading r, double quality, double radius, double inkDuration, int bolts) {
        return switch (form) {
            case BEAM, SPILL, PULL, LIFT, RAIN, BIRD, MANIFEST, CENTER_SIGN -> WHILE_WHOLE;
            case OBLIVION -> WHILE_WHOLE;
            case PETRIFY -> Math.max(1, (int) Math.round(90 * quality * (1 + radius * 0.3) * inkDuration));
            case BOLTS -> bolts * 4;
            default -> 1;
        };
    }

    public static final int WHILE_WHOLE = Integer.MAX_VALUE / 4;

    private static SpellProgram nested(GlyphReading r, GlyphFrame plane, Vec3 origin, double scale, double inkPotency,
                                       double inkDuration, double inkMisfire) {
        SpellProgram core = compile(r.core(), plane, origin, scale, inkPotency, inkDuration, inkMisfire);
        if (core.form() == Form.FIZZLE || core.form() == Form.BURST) return core;
        double outerR = r.ring().radius() * scale;

        double power = core.power() * Math.min(1.6, Math.pow(outerR / Math.max(0.1, core.radius()), 0.8));
        long rep = r.count(SignType.REPETITION), enl = r.count(SignType.ENLARGE);

        int duration = core.duration() >= WHILE_WHOLE ? WHILE_WHOLE : (int) Math.min(WHILE_WHOLE, Math.round(core.duration() * (0.85 + 0.15 * r.neatness() / 0.5)));
        power *= 1 + 0.3 * Math.min(enl, 3);
        boolean focused = core.focused() || r.has(SignType.CONVERGENCE);
        int birds = core.birds() + (int) r.count(SignType.BIRD);
        boolean cushion = r.has(SignType.BILLOWING);

        List<SpellProgram> layers = new ArrayList<>(core.layers());
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
        return core.layered(core.element(), core.radius(), power, duration, focused, birds, core.bolts(), List.copyOf(layers), cushion)
                .withMods(core.mods() | mods(r));
    }

    static final double USUAL_SIGN = 0.42;

    static double signWeight(GlyphReading r) {
        if (r.signs().isEmpty() || r.ring() == null || r.ring().radius() <= 0) return 1;
        double sum = 0;
        for (ReadSign s : r.signs()) sum += s.size() / r.ring().radius();
        double ratio = sum / r.signs().size() / USUAL_SIGN;
        return Math.sqrt(Mth.clamp(ratio, 0.5, 1.8));
    }

    static int mods(GlyphReading r) {
        int m = 0;
        if (r.has(SignType.CROSSHAIR)) m |= SpellProgram.AIM;
        if (r.has(SignType.DIAMOND) || r.centerSign() == SignType.DIAMOND) m |= SpellProgram.WARD;
        if (r.has(SignType.WEAVE)) m |= SpellProgram.BIND;
        if (r.has(SignType.DANCING_PUPPET) || r.centerSign() == SignType.DANCING_PUPPET) m |= SpellProgram.PUPPET;
        if (r.has(SignType.COLLECTION)) m |= SpellProgram.GATHER;
        if (r.has(SignType.TREAD)) m |= SpellProgram.TREAD;
        if (r.has(SignType.FLOAT) && !r.has(SignType.LEVITATION)) m |= SpellProgram.SOFT;
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
