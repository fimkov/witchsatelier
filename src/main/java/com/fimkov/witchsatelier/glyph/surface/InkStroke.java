package com.fimkov.witchsatelier.glyph.surface;

import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record InkStroke(long id, int au, int av, float[] rel, InkType ink, UUID author, StrokeState state, int color) {
    public static final UUID NOBODY = new UUID(0, 0);

    public static final int MAX_POINTS = 4096;

    public static final StreamCodec<ByteBuf, InkStroke> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public InkStroke decode(ByteBuf buf) {
            long id = buf.readLong();
            int au = buf.readInt(), av = buf.readInt();
            float[] rel = readFloats(buf);
            InkType ink = InkType.byId(buf.readByte());
            UUID author = new UUID(buf.readLong(), buf.readLong());
            StrokeState state = StrokeState.byId(buf.readByte());
            int color = buf.readInt();
            return new InkStroke(id, au, av, rel, ink, author, state, color);
        }

        @Override
        public void encode(ByteBuf buf, InkStroke s) {
            buf.writeLong(s.id);
            buf.writeInt(s.au);
            buf.writeInt(s.av);
            writeFloats(buf, s.rel);
            buf.writeByte(s.ink.ordinal());
            buf.writeLong(s.author.getMostSignificantBits());
            buf.writeLong(s.author.getLeastSignificantBits());
            buf.writeByte(s.state.ordinal());
            buf.writeInt(s.color);
        }
    };

    public static InkStroke of(long id, double[] uv, InkType ink, UUID author, StrokeState state, int color) {
        int au = (int) Math.floor(uv[0]), av = (int) Math.floor(uv[1]);
        float[] rel = new float[uv.length];
        for (int i = 0; i + 1 < uv.length; i += 2) {
            rel[i] = (float) (uv[i] - au);
            rel[i + 1] = (float) (uv[i + 1] - av);
        }
        return new InkStroke(id, au, av, rel, ink, author == null ? NOBODY : author, state, color);
    }

    public static float[] readFloats(ByteBuf buf) {
        int n = buf.readInt();
        if (n < 0 || n > MAX_POINTS * 2) throw new IllegalArgumentException("stroke too long: " + n);
        float[] f = new float[n];
        for (int i = 0; i < n; i++) f[i] = buf.readFloat();
        return f;
    }

    public static void writeFloats(ByteBuf buf, float[] f) {
        buf.writeInt(f.length);
        for (float x : f) buf.writeFloat(x);
    }

    public int size() {
        return rel.length / 2;
    }

    public double u(int i) { return au + (double) rel[i * 2]; }
    public double v(int i) { return av + (double) rel[i * 2 + 1]; }

    public double[] absolute() {
        double[] out = new double[rel.length];
        for (int i = 0; i < size(); i++) {
            out[i * 2] = u(i);
            out[i * 2 + 1] = v(i);
        }
        return out;
    }

    public List<Pt> points() {
        List<Pt> out = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) out.add(new Pt(u(i), v(i)));
        return out;
    }

    public InkStroke withState(StrokeState s, int c) {
        return new InkStroke(id, au, av, rel, ink, author, s, c);
    }

    public double length() {
        double d = 0;
        for (int i = 1; i < size(); i++) d += Math.hypot(u(i) - u(i - 1), v(i) - v(i - 1));
        return d;
    }

    public Pt centroid() {
        double x = 0, y = 0;
        for (int i = 0; i < size(); i++) { x += u(i); y += v(i); }
        return new Pt(x / size(), y / size());
    }

    public boolean touchesSquare(double minU, double minV, double maxU, double maxV) {
        for (int i = 0; i < size(); i++) {
            double u = u(i), v = v(i);
            if (u >= minU && u <= maxU && v >= minV && v <= maxV) return true;
        }
        return false;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("id", id);
        t.putInt("au", au);
        t.putInt("av", av);
        int[] bits = new int[rel.length];
        for (int i = 0; i < rel.length; i++) bits[i] = Float.floatToRawIntBits(rel[i]);
        t.putIntArray("rel", bits);
        t.putByte("state", (byte) state.ordinal());
        t.putInt("color", color);
        t.putByte("ink", (byte) ink.ordinal());
        t.putUUID("author", author);
        return t;
    }

    public static InkStroke load(CompoundTag t) {
        int[] bits = t.getIntArray("rel");
        float[] rel = new float[bits.length];
        for (int i = 0; i < bits.length; i++) rel[i] = Float.intBitsToFloat(bits[i]);
        StrokeState state = StrokeState.byId(t.getByte("state"));

        if (state == StrokeState.ACTIVE) state = StrokeState.IDLE;
        UUID author = t.hasUUID("author") ? t.getUUID("author") : NOBODY;
        return new InkStroke(t.getLong("id"), t.getInt("au"), t.getInt("av"), rel, InkType.byId(t.getByte("ink")), author,
                state, t.getInt("color"));
    }
}
