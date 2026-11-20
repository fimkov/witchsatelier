package com.witchcraft.glyph.surface;

import com.witchcraft.glyph.recognize.Pt;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * An infinite drawing plane made of block faces that all face the same way at the same depth:
 * for example every top face at y = 64, or every north face at z = 10. A glyph can span many blocks of one plane.
 * <p>
 * Points on the plane are (u, v) in blocks, with axes chosen so that u x v points out of the face,
 * so drawings read the same way as seen from in front of the wall or floor.
 *
 * @param face  which way the drawn-on faces point
 * @param layer block coordinate along the face's axis of the blocks being drawn on
 */
public record SurfacePlane(Direction face, int layer) implements com.witchcraft.spell.GlyphFrame {
    public static final StreamCodec<ByteBuf, SurfacePlane> STREAM_CODEC = StreamCodec.composite(
            Direction.STREAM_CODEC, SurfacePlane::face,
            ByteBufCodecs.VAR_INT, SurfacePlane::layer,
            SurfacePlane::new);

    /** World coordinate of the plane along the face axis. */
    public double depth() {
        return layer + (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
    }

    public static SurfacePlane of(BlockPos pos, Direction face) {
        return new SurfacePlane(face, pos.get(face.getAxis()));
    }

    public Pt toUV(Vec3 w) {
        return switch (face) {
            case UP -> new Pt(w.x, -w.z);
            case DOWN -> new Pt(w.x, w.z);
            case NORTH -> new Pt(-w.x, w.y);
            case SOUTH -> new Pt(w.x, w.y);
            case EAST -> new Pt(-w.z, w.y);
            case WEST -> new Pt(w.z, w.y);
        };
    }

    /** World position of (u, v), lifted {@code offset} blocks off the face. */
    public Vec3 toWorld(double u, double v, double offset) {
        double d = depth() + offset * face.getAxisDirection().getStep();
        return switch (face) {
            case UP, DOWN -> new Vec3(u, d, face == Direction.UP ? -v : v);
            case NORTH -> new Vec3(-u, v, d);
            case SOUTH -> new Vec3(u, v, d);
            case EAST -> new Vec3(d, v, -u);
            case WEST -> new Vec3(d, v, u);
        };
    }

    public Vec3 toWorld(Pt p, double offset) {
        return toWorld(p.x(), p.y(), offset);
    }

    /** The block whose face contains the point (u, v). */
    public BlockPos blockAt(double u, double v) {
        Vec3 w = toWorld(u, v, -0.5);
        return BlockPos.containing(w.x, w.y, w.z);
    }

    /** The in-plane direction (u, v) expressed as a world vector. */
    @Override
    public Vec3 planeVector(double du, double dv) {
        return toWorld(du, dv, 0).subtract(toWorld(0, 0, 0));
    }

    @Override
    public Vec3 normal() {
        return Vec3.atLowerCornerOf(face.getNormal());
    }

    /** Intersection of a ray with the plane, or null if parallel or behind. */
    public Vec3 intersect(Vec3 origin, Vec3 dir) {
        Direction.Axis axis = face.getAxis();
        double o = axis.choose(origin.x, origin.y, origin.z);
        double d = axis.choose(dir.x, dir.y, dir.z);
        if (Math.abs(d) < 1e-6) return null;
        double t = (depth() - o) / d;
        if (t < 0) return null;
        return origin.add(dir.scale(t));
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putByte("face", (byte) face.get3DDataValue());
        t.putInt("layer", layer);
        return t;
    }

    public static SurfacePlane load(CompoundTag t) {
        return new SurfacePlane(Direction.from3DDataValue(t.getByte("face")), t.getInt("layer"));
    }
}
