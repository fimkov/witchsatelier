package com.fimkov.witchsatelier.windowway;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record PortalFrame(long id, Vec3 center, Vec3 u, Vec3 v, Vec3 n, double radius, long link) {
    public static final StreamCodec<ByteBuf, PortalFrame> CODEC = StreamCodec.of((buf, f) -> {
        buf.writeLong(f.id);
        buf.writeDouble(f.center.x); buf.writeDouble(f.center.y); buf.writeDouble(f.center.z);
        buf.writeFloat((float) f.v.x); buf.writeFloat((float) f.v.y); buf.writeFloat((float) f.v.z);
        buf.writeFloat((float) f.n.x); buf.writeFloat((float) f.n.y); buf.writeFloat((float) f.n.z);
        buf.writeFloat((float) f.radius);
        buf.writeLong(f.link);
    }, buf -> {
        long id = buf.readLong();
        Vec3 c = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 v = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        Vec3 n = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        return of(id, c, v, n, buf.readFloat(), buf.readLong());
    });

    public static PortalFrame of(long id, Vec3 center, Vec3 up, Vec3 n, double radius, long link) {
        Vec3 nn = n.normalize(), vv = up.normalize();
        return new PortalFrame(id, center, vv.cross(nn).normalize(), vv, nn, radius, link);
    }

    public PortalFrame withLink(long link) {
        return new PortalFrame(id, center, u, v, n, radius, link);
    }

    public double side(Vec3 p) {
        return p.subtract(center).dot(n);
    }

    public double inPlane(Vec3 p) {
        Vec3 r = p.subtract(center);
        return r.subtract(n.scale(r.dot(n))).length();
    }

    public double discRadius() {
        return radius * 0.92;
    }

    public boolean opens(Vec3 face) {
        return Math.abs(side(face)) <= 0.02 && inPlane(face) <= discRadius() + 0.15;
    }

    public boolean inOpening(Vec3 hit) {
        if (inPlane(hit) <= discRadius()) return true;
        BlockPos behind = BlockPos.containing(hit.subtract(n.scale(0.25)));
        return opens(Vec3.atCenterOf(behind).add(n.scale(0.5)));
    }

    public static Vec3 mapPoint(PortalFrame from, PortalFrame to, Vec3 p) {
        Vec3 r = p.subtract(from.center);
        return to.center.add(mapLocal(from, to, r));
    }

    public static Vec3 mapDir(PortalFrame from, PortalFrame to, Vec3 d) {
        return mapLocal(from, to, d);
    }

    private static Vec3 mapLocal(PortalFrame from, PortalFrame to, Vec3 r) {
        double a = r.dot(from.u), b = r.dot(from.v), c = r.dot(from.n);
        return to.u.scale(-a).add(to.v.scale(b)).add(to.n.scale(-c));
    }

    public static Quaternionf rotation(PortalFrame from, PortalFrame to) {
        Vec3 x = mapDir(from, to, new Vec3(1, 0, 0)), y = mapDir(from, to, new Vec3(0, 1, 0)), z = mapDir(from, to, new Vec3(0, 0, 1));
        Matrix3f m = new Matrix3f(new Vector3f((float) x.x, (float) x.y, (float) x.z), new Vector3f((float) y.x, (float) y.y, (float) y.z),
                new Vector3f((float) z.x, (float) z.y, (float) z.z));
        return m.getNormalizedRotation(new Quaternionf());
    }

    public static float[] yawPitch(Vec3 look) {
        double h = Math.sqrt(look.x * look.x + look.z * look.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(look.y, h));
        return new float[]{yaw, pitch};
    }
}
