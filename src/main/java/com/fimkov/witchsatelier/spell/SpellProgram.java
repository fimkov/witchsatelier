package com.fimkov.witchsatelier.spell;

import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public record SpellProgram(
        Sigil element,
        SignType centerSign,
        Form form,
        boolean inverted,
        Vec3 origin,
        Vec3 normal,
        Vec3 direction,
        double radius,
        double power,
        int duration,
        boolean focused,
        boolean gentle,
        boolean spread,
        double vortex,
        int bolts,
        double misfire,
        boolean hidden,
        int birds,
        List<SpellProgram> layers,
        boolean cushion,
        int mods
) {
    public static final int AIM = 1;

    public static final int WARD = 2;

    public static final int BIND = 4;

    public static final int PUPPET = 8;

    public static final int GATHER = 16;

    public static final int TREAD = 32;

    public static final int SOFT = 64;

    public static final int MIST = 128;

    public static final int DRY = 256;

    public static final int REPEL = 512;

    public static final int WALL = 1024;

    public static final int BEND = 2048;

    public static final int CAGE = 4096;

    public static final int STEED = 8192;

    public static final int REGION_IN = 1 << 22, REGION_OUT = 1 << 23, REGION_SIDE = 1 << 24, REGION_OCTANT = 25, TREAD_INVERTED = 1 << 28;

    public boolean has(int mod) {
        return (mods & mod) != 0;
    }

    public SpellProgram(Sigil element, SignType centerSign, Form form, boolean inverted, Vec3 origin, Vec3 normal, Vec3 direction,
                        double radius, double power, int duration, boolean focused, boolean gentle, boolean spread, double vortex,
                        int bolts, double misfire, boolean hidden, int birds, List<SpellProgram> layers, boolean cushion) {
        this(element, centerSign, form, inverted, origin, normal, direction, radius, power, duration, focused, gentle, spread,
                vortex, bolts, misfire, hidden, birds, layers, cushion, 0);
    }

    public SpellProgram withMods(int mods) {
        return new SpellProgram(element, centerSign, form, inverted, origin, normal, direction, radius, power, duration,
                focused, gentle, spread, vortex, bolts, misfire, hidden, birds, layers, cushion, mods);
    }

    public SpellProgram(Sigil element, SignType centerSign, Form form, boolean inverted, Vec3 origin, Vec3 normal, Vec3 direction,
                        double radius, double power, int duration, boolean focused, boolean gentle, boolean spread, double vortex,
                        int bolts, double misfire, boolean hidden, int birds) {
        this(element, centerSign, form, inverted, origin, normal, direction, radius, power, duration, focused, gentle, spread,
                vortex, bolts, misfire, hidden, birds, List.of(), false, 0);
    }

    public enum Form {
        BEAM,

        SPILL,

        BOLTS,

        LIFT,

        PULL,

        CRUSH,

        RESTORE,

        BIRD,

        RAIN,

        MANIFEST,

        CENTER_SIGN,

        BURST,

        FIZZLE,

        PETRIFY,

        OBLIVION
    }

    public SpellProgram withPose(Vec3 origin, Vec3 normal, Vec3 direction) {
        List<SpellProgram> posed = layers.isEmpty() ? layers
                : layers.stream().map(l -> l.withPose(origin, normal, direction)).toList();
        return new SpellProgram(element, centerSign, form, inverted, origin, normal, direction, radius, power, duration,
                focused, gentle, spread, vortex, bolts, misfire, hidden, birds, posed, cushion, mods);
    }

    public SpellProgram withForm(Form f) {
        return new SpellProgram(element, centerSign, f, inverted, origin, normal, direction, radius, power, duration,
                focused, gentle, spread, vortex, bolts, misfire, hidden, birds, layers, cushion, mods);
    }

    public SpellProgram amplified(double factor) {
        List<SpellProgram> amp = layers.stream().map(l -> l.amplified(factor)).toList();
        return new SpellProgram(element, centerSign, form, inverted, origin, normal, direction, radius, power * factor,
                (int) (duration * (1 + (factor - 1) * 0.4)), focused, gentle, spread, vortex,
                form == Form.BOLTS ? (int) Math.ceil(bolts * factor) : bolts, misfire, hidden, (int) Math.ceil(birds * factor), amp, cushion, mods);
    }

    public SpellProgram layered(Sigil element, double radius, double power, int duration, boolean focused, int birds, int bolts,
                                List<SpellProgram> layers, boolean cushion) {
        return new SpellProgram(element, centerSign, form, inverted, origin, normal, direction, radius, power, duration,
                focused, gentle, spread, vortex, bolts, misfire, hidden, birds, layers, cushion, mods);
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        if (form == Form.BURST) return "empty ring";
        if (form == Form.FIZZLE) return "the ring closes, but nothing answers";
        sb.append(element != null ? pretty(element.name()) : centerSign != null ? pretty(centerSign.name()) : "?");
        sb.append(" · ").append(pretty(form.name()));
        if (inverted) sb.append(" (reversed)");
        if (focused) sb.append(" · focused");
        if (gentle) sb.append(" · gentle");
        if (spread) sb.append(" · fanned");
        if (Math.abs(vortex) > 0.01) sb.append(" · swirling");
        if (form == Form.BOLTS) sb.append(" ×").append(bolts);
        if (birds > 0) sb.append(" · birds ×").append(birds);
        if (cushion) sb.append(" · cushioned");
        if (has(AIM)) sb.append(" · aiming");
        if (has(WARD)) sb.append(" · warding");
        if (has(BIND)) sb.append(" · binding");
        if (has(PUPPET)) sb.append(" · puppets");
        if (has(GATHER)) sb.append(" · gathering");
        for (SpellProgram l : layers) sb.append(" + [").append(l.element() == null ? "?" : pretty(l.element().name())).append(' ').append(pretty(l.form().name())).append(']');
        sb.append(String.format(" · power %.1f · %.1fs", power, duration / 20.0));
        return sb.toString();
    }

    private static String pretty(String s) {
        String l = s.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }
}
