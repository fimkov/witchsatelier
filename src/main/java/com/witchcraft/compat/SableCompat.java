package com.witchcraft.compat;

import com.witchcraft.glyph.GlyphService;
import com.witchcraft.glyph.model.Sigil;
import com.witchcraft.spell.ActiveSpell;
import com.witchcraft.spell.SpellProgram;
import com.witchcraft.spell.SpellProgram.Form;
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

    private static void physicsTick(SubLevelPhysicsSystem physics, double dt) {
        ServerLevel level = physics.getLevel();
        for (ActiveSpell spell : GlyphService.activeSpells(level)) {
            SpellProgram p = spell.program;
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
}
