package com.fimkov.witchsatelier.network;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.windowway.PortalFrame;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Payloads {
    private Payloads() {}

    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, name));
    }

    static final StreamCodec<ByteBuf, float[]> FLOATS = new StreamCodec<>() {
        @Override
        public float[] decode(ByteBuf buf) {
            int n = buf.readInt();
            if (n < 0 || n > InkStroke.MAX_POINTS * 2) throw new IllegalArgumentException("too many points: " + n);
            float[] f = new float[n];
            for (int i = 0; i < n; i++) f[i] = buf.readFloat();
            return f;
        }

        @Override
        public void encode(ByteBuf buf, float[] f) {
            buf.writeInt(f.length);
            for (float x : f) buf.writeFloat(x);
        }
    };

    static final StreamCodec<ByteBuf, long[]> LONGS = new StreamCodec<>() {
        @Override
        public long[] decode(ByteBuf buf) {
            int n = buf.readInt();
            if (n < 0 || n > 65536) throw new IllegalArgumentException("too many ids: " + n);
            long[] l = new long[n];
            for (int i = 0; i < n; i++) l[i] = buf.readLong();
            return l;
        }

        @Override
        public void encode(ByteBuf buf, long[] l) {
            buf.writeInt(l.length);
            for (long x : l) buf.writeLong(x);
        }
    };

    public record DrawStroke(SurfacePlane plane, int au, int av, float[] rel) implements CustomPacketPayload {
        public static final Type<DrawStroke> TYPE = typeOf("draw_stroke");
        public static final StreamCodec<ByteBuf, DrawStroke> CODEC = StreamCodec.composite(
                SurfacePlane.STREAM_CODEC, DrawStroke::plane, ByteBufCodecs.INT, DrawStroke::au, ByteBufCodecs.INT, DrawStroke::av,
                FLOATS, DrawStroke::rel, DrawStroke::new);

        public double[] absolute() {
            double[] out = new double[rel.length];
            for (int i = 0; i + 1 < rel.length; i += 2) {
                out[i] = au + (double) rel[i];
                out[i + 1] = av + (double) rel[i + 1];
            }
            return out;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record EraseAt(SurfacePlane plane, double u, double v, float radius) implements CustomPacketPayload {
        public static final Type<EraseAt> TYPE = typeOf("erase_at");
        public static final StreamCodec<ByteBuf, EraseAt> CODEC = StreamCodec.composite(
                SurfacePlane.STREAM_CODEC, EraseAt::plane, ByteBufCodecs.DOUBLE, EraseAt::u, ByteBufCodecs.DOUBLE, EraseAt::v,
                ByteBufCodecs.FLOAT, EraseAt::radius, EraseAt::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static final StreamCodec<ByteBuf, InteractionHand> HAND = ByteBufCodecs.BOOL.map(
            b -> b ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, h -> h == InteractionHand.OFF_HAND);

    public record PageStroke(InteractionHand hand, int page, float[] pts) implements CustomPacketPayload {
        public static final Type<PageStroke> TYPE = typeOf("page_stroke");
        public static final StreamCodec<ByteBuf, PageStroke> CODEC = StreamCodec.composite(
                HAND, PageStroke::hand, ByteBufCodecs.VAR_INT, PageStroke::page, FLOATS, PageStroke::pts, PageStroke::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record PageErase(InteractionHand hand, int page, float u, float v) implements CustomPacketPayload {
        public static final Type<PageErase> TYPE = typeOf("page_erase");
        public static final StreamCodec<ByteBuf, PageErase> CODEC = StreamCodec.composite(
                HAND, PageErase::hand, ByteBufCodecs.VAR_INT, PageErase::page, ByteBufCodecs.FLOAT, PageErase::u,
                ByteBufCodecs.FLOAT, PageErase::v, PageErase::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record PageNote(InteractionHand hand, int page, String title, String notes) implements CustomPacketPayload {
        public static final Type<PageNote> TYPE = typeOf("page_note");
        public static final StreamCodec<ByteBuf, PageNote> CODEC = StreamCodec.composite(
                HAND, PageNote::hand, ByteBufCodecs.VAR_INT, PageNote::page,
                ByteBufCodecs.stringUtf8(256), PageNote::title, ByteBufCodecs.stringUtf8(2048), PageNote::notes, PageNote::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record PageTurn(InteractionHand hand, int page) implements CustomPacketPayload {
        public static final Type<PageTurn> TYPE = typeOf("page_turn");
        public static final StreamCodec<ByteBuf, PageTurn> CODEC = StreamCodec.composite(
                HAND, PageTurn::hand, ByteBufCodecs.VAR_INT, PageTurn::page, PageTurn::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record PageTear(InteractionHand hand, int page) implements CustomPacketPayload {
        public static final Type<PageTear> TYPE = typeOf("page_tear");
        public static final StreamCodec<ByteBuf, PageTear> CODEC = StreamCodec.composite(
                HAND, PageTear::hand, ByteBufCodecs.VAR_INT, PageTear::page, PageTear::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record QuickClose() implements CustomPacketPayload {
        public static final Type<QuickClose> TYPE = typeOf("quick_close");
        public static final StreamCodec<ByteBuf, QuickClose> CODEC = StreamCodec.unit(new QuickClose());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record StrokesAdded(SurfacePlane plane, List<InkStroke> strokes) implements CustomPacketPayload {
        public static final Type<StrokesAdded> TYPE = typeOf("strokes_added");
        public static final StreamCodec<ByteBuf, StrokesAdded> CODEC = StreamCodec.composite(
                SurfacePlane.STREAM_CODEC, StrokesAdded::plane,
                InkStroke.STREAM_CODEC.apply(ByteBufCodecs.list(4096)), StrokesAdded::strokes,
                StrokesAdded::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record StrokeWear(long[] ids, List<byte[]> wear) implements CustomPacketPayload {
        public static final Type<StrokeWear> TYPE = typeOf("stroke_wear");
        public static final StreamCodec<ByteBuf, StrokeWear> CODEC = new StreamCodec<>() {
            @Override
            public StrokeWear decode(ByteBuf buf) {
                long[] ids = LONGS.decode(buf);
                List<byte[]> w = new ArrayList<>(ids.length);
                for (int i = 0; i < ids.length; i++) {
                    int n = buf.readInt();
                    if (n < 0 || n > InkStroke.MAX_POINTS) throw new IllegalArgumentException("wear too long: " + n);
                    byte[] b = new byte[n];
                    buf.readBytes(b);
                    w.add(b);
                }
                return new StrokeWear(ids, w);
            }

            @Override
            public void encode(ByteBuf buf, StrokeWear p) {
                LONGS.encode(buf, p.ids());
                for (byte[] b : p.wear()) {
                    buf.writeInt(b.length);
                    buf.writeBytes(b);
                }
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record StrokesRemoved(long[] ids) implements CustomPacketPayload {
        public static final Type<StrokesRemoved> TYPE = typeOf("strokes_removed");
        public static final StreamCodec<ByteBuf, StrokesRemoved> CODEC = LONGS.map(StrokesRemoved::new, StrokesRemoved::ids);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record StrokeStates(long[] ids, byte state, int color, int ticksLeft) implements CustomPacketPayload {
        public static final Type<StrokeStates> TYPE = typeOf("stroke_states");
        public static final StreamCodec<ByteBuf, StrokeStates> CODEC = StreamCodec.composite(
                LONGS, StrokeStates::ids, ByteBufCodecs.BYTE, StrokeStates::state, ByteBufCodecs.INT, StrokeStates::color,
                ByteBufCodecs.VAR_INT, StrokeStates::ticksLeft, StrokeStates::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SpellFx(long id, int color, byte form, boolean handheld, UUID caster,
                          double ox, double oy, double oz, float nx, float ny, float nz, float dx, float dy, float dz,
                          float radius, float length, float width, int ticksLeft, int duration, int mods) implements CustomPacketPayload {
        public static final Type<SpellFx> TYPE = typeOf("spell_fx");
        public static final StreamCodec<ByteBuf, SpellFx> CODEC = StreamCodec.of((buf, f) -> {
            buf.writeLong(f.id); buf.writeInt(f.color); buf.writeByte(f.form); buf.writeBoolean(f.handheld);
            buf.writeLong(f.caster.getMostSignificantBits()); buf.writeLong(f.caster.getLeastSignificantBits());
            buf.writeDouble(f.ox); buf.writeDouble(f.oy); buf.writeDouble(f.oz);
            buf.writeFloat(f.nx); buf.writeFloat(f.ny); buf.writeFloat(f.nz);
            buf.writeFloat(f.dx); buf.writeFloat(f.dy); buf.writeFloat(f.dz);
            buf.writeFloat(f.radius); buf.writeFloat(f.length); buf.writeFloat(f.width);
            buf.writeInt(f.ticksLeft); buf.writeInt(f.duration); buf.writeInt(f.mods);
        }, buf -> new SpellFx(buf.readLong(), buf.readInt(), buf.readByte(), buf.readBoolean(), new UUID(buf.readLong(), buf.readLong()),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readInt(), buf.readInt(), buf.readInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BirdFx(long spell, int bird, int color, float x, float y, float z, float vx, float vy, float vz, int age, int life)
            implements CustomPacketPayload {
        public static final Type<BirdFx> TYPE = typeOf("bird_fx");
        public static final StreamCodec<ByteBuf, BirdFx> CODEC = StreamCodec.of((buf, f) -> {
            buf.writeLong(f.spell); buf.writeInt(f.bird); buf.writeInt(f.color);
            buf.writeFloat(f.x); buf.writeFloat(f.y); buf.writeFloat(f.z);
            buf.writeFloat(f.vx); buf.writeFloat(f.vy); buf.writeFloat(f.vz);
            buf.writeInt(f.age); buf.writeInt(f.life);
        }, buf -> new BirdFx(buf.readLong(), buf.readInt(), buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record PennantFx(int caster, int target, float x, float y, float z, int hold) implements CustomPacketPayload {
        public static final Type<PennantFx> TYPE = typeOf("pennant_fx");
        public static final StreamCodec<ByteBuf, PennantFx> CODEC = StreamCodec.of((buf, f) -> {
            buf.writeInt(f.caster); buf.writeInt(f.target);
            buf.writeFloat(f.x); buf.writeFloat(f.y); buf.writeFloat(f.z); buf.writeInt(f.hold);
        }, buf -> new PennantFx(buf.readInt(), buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TracePin(boolean pinned, SurfacePlane plane, double u, double v, double turn, double scale) implements CustomPacketPayload {
        public static final Type<TracePin> TYPE = typeOf("trace_pin");
        public static final StreamCodec<ByteBuf, TracePin> CODEC = StreamCodec.of((buf, f) -> {
            buf.writeBoolean(f.pinned);
            SurfacePlane.STREAM_CODEC.encode(buf, f.plane);
            buf.writeDouble(f.u); buf.writeDouble(f.v); buf.writeDouble(f.turn); buf.writeDouble(f.scale);
        }, buf -> new TracePin(buf.readBoolean(), SurfacePlane.STREAM_CODEC.decode(buf), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Techniques(boolean imaginedRing) implements CustomPacketPayload {
        public static final Type<Techniques> TYPE = typeOf("techniques");
        public static final StreamCodec<ByteBuf, Techniques> CODEC = ByteBufCodecs.BOOL.map(Techniques::new, Techniques::imaginedRing);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record WindowwaySync(List<PortalFrame> frames) implements CustomPacketPayload {
        public static final Type<WindowwaySync> TYPE = typeOf("windowway_sync");
        public static final StreamCodec<ByteBuf, WindowwaySync> CODEC = PortalFrame.CODEC
                .apply(ByteBufCodecs.list(256)).map(WindowwaySync::new, WindowwaySync::frames);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record PortalCross(long id) implements CustomPacketPayload {
        public static final Type<PortalCross> TYPE = typeOf("portal_cross");
        public static final StreamCodec<ByteBuf, PortalCross> CODEC = ByteBufCodecs.VAR_LONG.map(PortalCross::new, PortalCross::id);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BootsJoin() implements CustomPacketPayload {
        public static final Type<BootsJoin> TYPE = typeOf("boots_join");
        public static final StreamCodec<ByteBuf, BootsJoin> CODEC = StreamCodec.unit(new BootsJoin());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ResetGlyphs() implements CustomPacketPayload {
        public static final Type<ResetGlyphs> TYPE = typeOf("reset_glyphs");
        public static final StreamCodec<ByteBuf, ResetGlyphs> CODEC = StreamCodec.unit(new ResetGlyphs());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BadApple(SurfacePlane plane, double u, double v, float radius, float up, long[] ids) implements CustomPacketPayload {
        public static final Type<BadApple> TYPE = typeOf("bad_apple");
        public static final StreamCodec<ByteBuf, BadApple> CODEC = StreamCodec.of((buf, f) -> {
            SurfacePlane.STREAM_CODEC.encode(buf, f.plane);
            buf.writeDouble(f.u); buf.writeDouble(f.v); buf.writeFloat(f.radius); buf.writeFloat(f.up);
            LONGS.encode(buf, f.ids);
        }, buf -> new BadApple(SurfacePlane.STREAM_CODEC.decode(buf), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), LONGS.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
