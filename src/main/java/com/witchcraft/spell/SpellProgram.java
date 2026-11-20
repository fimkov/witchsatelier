package com.witchcraft.spell;

import com.witchcraft.glyph.model.Sigil;
import com.witchcraft.glyph.model.SignType;
import net.minecraft.world.phys.Vec3;

/**
 * A compiled glyph: everything needed to run the spell, already resolved into world space.
 *
 * @param element    the sigil, or null for sign-only centres / empty rings
 * @param centerSign sign standing in the centre (Repetition, Vision, Billowing...) when there is no sigil
 * @param form       how the element manifests
 * @param inverted   reversed signs: push instead of pull, restore instead of crush, press instead of lift
 * @param origin     glyph centre in the world, just off the surface
 * @param normal     the surface normal
 * @param direction  where directed forms go (normal, tilted by unbalanced signs and Direction signs)
 * @param radius     ring radius in blocks
 * @param power      overall strength (grows with the ring's area)
 * @param duration   ticks the spell keeps running
 * @param focused    Convergence: narrower, longer, harder-hitting
 * @param gentle     Radial: a softened effect (fire becomes warmth)
 * @param spread     Direction signs pointing outward: the effect fans out
 * @param vortex     signed swirl from signs turned along the ring (+ counter-clockwise)
 * @param bolts      number of shots for the Bolt form
 * @param misfire    chance that the spell backfires when it starts
 * @param hidden     Eye/Bend signs present (concealment)
 * @param birds      Bird signs: birds of the element's light fly out of the glyph
 * @param layers     outer layers of a nested glyph that run alongside this one (their own element or form)
 * @param cushion    a Billowing layer: everything around falls softly and lands unhurt
 * @param mods       extra behaviours from signs, see the {@code AIM}, {@code WARD}... bits
 */
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
        java.util.List<SpellProgram> layers,
        boolean cushion,
        int mods
) {
    /** Crosshair: the spell turns towards the nearest creature in front of the glyph. */
    public static final int AIM = 1;
    /** Diamond: a ward; arrows and other missiles turn back, hostile creatures are kept out. */
    public static final int WARD = 2;
    /** Weave: whatever is caught in the spell is bound where it stands. */
    public static final int BIND = 4;
    /** Dancing Puppet: creatures around forget what they meant to do and dance on strings. */
    public static final int PUPPET = 8;
    /** Collection: loose things nearby are gathered in to the glyph. */
    public static final int GATHER = 16;
    /** Holds the spell until something steps onto the glyph. */
    public static final int TREAD = 32;

    public boolean has(int mod) {
        return (mods & mod) != 0;
    }

    public SpellProgram(Sigil element, SignType centerSign, Form form, boolean inverted, Vec3 origin, Vec3 normal, Vec3 direction,
                        double radius, double power, int duration, boolean focused, boolean gentle, boolean spread, double vortex,
                        int bolts, double misfire, boolean hidden, int birds, java.util.List<SpellProgram> layers, boolean cushion) {
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
                vortex, bolts, misfire, hidden, birds, java.util.List.of(), false, 0);
    }

    public enum Form {
        /** Column: a beam or jet. */
        BEAM,
        /** Dispersion: pours out all around. */
        SPILL,
        /** Bolt: a volley of projectiles. */
        BOLTS,
        /** Levitation / Float: lifts what is over the glyph (presses down when reversed). */
        LIFT,
        /** Pull: draws things in (pushes when reversed, swirls when angled). */
        PULL,
        /** Crush with earth: pulverises the surface it is drawn on. */
        CRUSH,
        /** Reversed Crush with earth (Integration): puts pulverised blocks back. */
        RESTORE,
        /** Bird: birds made of the element fly out and circle overhead (a beacon). */
        BIRD,
        /** Rain: falls over the area. */
        RAIN,
        /** Element with no shaping sign: a small, steady manifestation at the glyph. */
        MANIFEST,
        /** A sign in the centre instead of a sigil (Repetition, Vision, Billowing). */
        CENTER_SIGN,
        /** Closed ring with nothing inside: the ink's energy is released as a blast. */
        BURST,
        /** Something is there but it reads as nothing usable. */
        FIZZLE,
        /** Forbidden: Earth under the gaze of Eyes. Living things before the glyph turn to stone. */
        PETRIFY
    }

    public SpellProgram withPose(Vec3 origin, Vec3 normal, Vec3 direction) {
        java.util.List<SpellProgram> posed = layers.isEmpty() ? layers
                : layers.stream().map(l -> l.withPose(origin, normal, direction)).toList();
        return new SpellProgram(element, centerSign, form, inverted, origin, normal, direction, radius, power, duration,
                focused, gentle, spread, vortex, bolts, misfire, hidden, birds, posed, cushion, mods);
    }

    public SpellProgram withForm(Form f) {
        return new SpellProgram(element, centerSign, f, inverted, origin, normal, direction, radius, power, duration,
                focused, gentle, spread, vortex, bolts, misfire, hidden, birds, layers, cushion, mods);
    }

    /** Made stronger by something outside the glyph (an Arcane Lense). */
    public SpellProgram amplified(double factor) {
        java.util.List<SpellProgram> amp = layers.stream().map(l -> l.amplified(factor)).toList();
        return new SpellProgram(element, centerSign, form, inverted, origin, normal, direction, radius, power * factor,
                (int) (duration * (1 + (factor - 1) * 0.4)), focused, gentle, spread, vortex,
                form == Form.BOLTS ? (int) Math.ceil(bolts * factor) : bolts, misfire, hidden, (int) Math.ceil(birds * factor), amp, cushion, mods);
    }

    /** The same spell, shaped by the outer layer of a nested glyph. */
    public SpellProgram layered(Sigil element, double radius, double power, int duration, boolean focused, int birds, int bolts,
                                java.util.List<SpellProgram> layers, boolean cushion) {
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
