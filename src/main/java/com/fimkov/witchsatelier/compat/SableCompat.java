package com.fimkov.witchsatelier.compat;

import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.spell.ActiveSpell;
import com.fimkov.witchsatelier.spell.SpellProgram;
import com.fimkov.witchsatelier.spell.SpellProgram.Form;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * With Sable installed, ships and other physics bodies (Create Aeronautics) feel the magic like everything
 * else: a levitation glyph under a ship lifts it (and holds it up against its own weight), reversed it lets it
 * sink gently; wind beams and spills push it. Only loaded when Sable is present.
 */
public final class SableCompat {
    private SableCompat() {}

    public static void init() {
        SableEventPlatform.INSTANCE.onPhysicsTick(SableCompat::physicsTick);
    }

    private static final java.util.function.Predicate<SpellProgram> FLIGHT = Ships.FLIGHT;

    private static void physicsTick(SubLevelPhysicsSystem physics, double dt) {
        ServerLevel level = physics.getLevel();
        for (ActiveSpell spell : GlyphService.activeSpells(level)) {
            SpellProgram p = spell.program;
            // a flight glyph drawn on a ship's own underside: the ship flies
            if (FLIGHT.test(p)) {
                SubLevel own = Sable.HELPER.getContaining(level, net.minecraft.core.BlockPos.containing(p.origin().subtract(p.normal().scale(0.2))));
                if (own instanceof ServerSubLevel ship) {
                    fly(level, physics, ship, spell, dt);
                    continue;
                }
            }
            boolean lift = p.form() == Form.LIFT;
            boolean wind = (p.form() == Form.BEAM || p.form() == Form.SPILL) && (p.element() == Sigil.WIND || p.element() == Sigil.WIND_UNDERFOOT);
            if (!lift && !wind) continue;
            Vec3 o = p.origin();
            Vec3 axis = lift ? p.normal() : p.direction();
            double reach = lift ? 4 + p.power() * 0.4 : Math.max(spell.fxLength, 4);
            double width = lift ? Math.max(1.5, p.radius() * 1.5) : Math.max(1, spell.fxWidth * 1.5);
            Vec3 end = o.add(axis.scale(reach));
            BoundingBox3d box = new BoundingBox3d(Math.min(o.x, end.x) - width, Math.min(o.y, end.y) - width, Math.min(o.z, end.z) - width,
                    Math.max(o.x, end.x) + width, Math.max(o.y, end.y) + width, Math.max(o.z, end.z) + width);
            for (SubLevel sub : Sable.HELPER.getAllIntersecting(level, box)) {
                if (!(sub instanceof ServerSubLevel ship)) continue;
                MassData mass = ship.getMassTracker();
                Vector3dc com = mass.getCenterOfMass();
                if (com == null) continue;
                Pose3d pose = sub.logicalPose();
                RigidBodyHandle handle = physics.getPhysicsHandle(ship);
                Vector3d v = handle.getLinearVelocity(new Vector3d());
                Vector3d ax = new Vector3d(axis.x, axis.y, axis.z);
                double m = mass.getMass();
                // the closer to the glyph, the stronger (spell power keeps it from fading out too soon)
                Vector3d at = pose.position();
                double along = new Vec3(at.x, at.y, at.z).subtract(o).dot(axis);
                double falloff = Math.max(0.25, 1 - Math.max(0, along) / (reach + 8));
                Vector3d impulse;
                if (lift) {
                    // hold it up against gravity, then steer its speed along the column towards a gentle rise
                    Vector3d g = DimensionPhysicsData.getGravity(level, at);
                    double target = p.inverted() ? -1.5 : 2.0 + Math.min(p.power() * 0.08, 6);   // m/s
                    double now = v.dot(ax);
                    double hold = Math.min(1.2, (0.6 + p.power() * 0.02) * falloff);
                    impulse = g.negate(new Vector3d()).mul(m * dt * hold)
                            .add(new Vector3d(ax).mul((target - now) * m * Math.min(1, dt * 2.5) * falloff));
                } else {
                    double push = Math.min(4 + p.power() * 0.4, 30) * falloff;  // m/s^2
                    impulse = new Vector3d(ax).mul(push * m * dt);
                }
                // through the centre of mass: lifted or pushed, never spun
                handle.applyLinearImpulse(pose.transformNormalInverse(impulse));
            }
        }
    }

    /**
     * A ship with a flight glyph on its own underside, as witches fly what they have drawn on: it holds itself up, and
     * goes where whoever stands on it looks (whoever drew the glyph, if they are aboard; anyone aboard otherwise).
     * Crouching holds it still in the air; nobody aboard, it waits where it is. Reversed signs let it down gently.
     */
    private static void fly(ServerLevel level, SubLevelPhysicsSystem physics, ServerSubLevel ship, ActiveSpell spell, double dt) {
        SpellProgram p = spell.program;
        MassData mass = ship.getMassTracker();
        if (mass.getCenterOfMass() == null) return;
        Pose3d pose = ship.logicalPose();
        RigidBodyHandle handle = physics.getPhysicsHandle(ship);
        double m = mass.getMass();
        Vector3d at = pose.position();
        Vector3d v = handle.getLinearVelocity(new Vector3d());
        Vector3d g = DimensionPhysicsData.getGravity(level, at);

        // the pilot: someone standing on (or in) the ship
        var box = ship.boundingBox();
        net.minecraft.server.level.ServerPlayer pilot = null;
        for (var pl : level.players()) {
            if (pl.isSpectator() || pl.getX() < box.minX() - 1 || pl.getX() > box.maxX() + 1 || pl.getZ() < box.minZ() - 1 || pl.getZ() > box.maxZ() + 1
                    || pl.getY() < box.minY() - 1 || pl.getY() > box.maxY() + 3) continue;
            if (pilot == null || pl.getUUID().equals(spell.caster)) pilot = pl;
        }
        Vector3d want = new Vector3d();
        if (p.inverted()) {
            want.set(0, -1.5, 0);
        } else if (pilot != null && !pilot.isShiftKeyDown()) {
            Vec3 look = pilot.getLookAngle();
            double speed = Math.min(6 + p.power() * 0.35, 20); // m/s
            want.set(look.x * speed, look.y * speed, look.z * speed);
        }
        // hold it up against its own weight, then steer its speed to what the pilot wants
        Vector3d impulse = g.negate(new Vector3d()).mul(m * dt)
                .add(new Vector3d(want).sub(v).mul(m * Math.min(1, dt * 1.8)));
        handle.applyLinearImpulse(pose.transformNormalInverse(impulse));
        // and keep it level: the deck is turned back up, and any roll or pitch it picks up is damped away
        // (it may still turn about the upright, as a boat does)
        Vector3d up = pose.transformNormal(new Vector3d(0, 1, 0), new Vector3d());
        Vector3d tilt = up.cross(new Vector3d(0, 1, 0), new Vector3d()); // axis to turn about, as long as the tilt
        Vector3d spin = handle.getAngularVelocity(new Vector3d());
        Vector3d wantSpin = new Vector3d(tilt).mul(4.0).add(0, spin.y, 0);
        Vector3d change = wantSpin.sub(spin, new Vector3d()).mul(Math.min(1, dt * 6));
        handle.addLinearAndAngularVelocity(new Vector3d(), change);
        if (level.getGameTime() % 4 == 0) {
            Vec3 o = new Vec3(at.x, at.y, at.z);
            level.sendParticles(com.fimkov.witchsatelier.spell.ElementLook.particle(Sigil.WIND), o.x, box.minY(), o.z, 3,
                    (box.maxX() - box.minX()) * 0.3, 0.1, (box.maxZ() - box.minZ()) * 0.3, 0.02);
        }
    }
}
