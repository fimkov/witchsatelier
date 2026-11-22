package com.fimkov.witchsatelier.glyph.ink;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum InkType implements StringRepresentable {
    CONJURING("conjuring", 0xEB140F2A, 1.0, 1.0, 0, false, false, false, true),
    WATERPROOF("waterproof", 0xEB06323A, 1.0, 1.0, 0, true, false, false, true),
    INVISIBLE("invisible", 0x70342A66, 1.0, 1.0, 0, false, true, false, true),
    LASTING("lasting", 0xEB3A2208, 1.0, 1.75, 0, false, false, false, true),
    BLOOD("blood", 0xF04C0710, 3.0, 1.0, 0.25, false, false, false, true),

    CHALK("chalk", 0xD8B9A3C8, 0.7, 0.6, 0.05, false, false, false, false),

    SEARED("seared", 0xF0201008, 1.05, 1.1, 0, true, false, true, false);

    public static final Codec<InkType> CODEC = StringRepresentable.fromEnum(InkType::values);
    public static final StreamCodec<ByteBuf, InkType> STREAM_CODEC = ByteBufCodecs.BYTE.map(InkType::byId, t -> (byte) t.ordinal());

    public final String id;
    public final int color;
    public final double potency;
    public final double duration;
    public final double misfire;
    public final boolean waterproof;
    public final boolean invisible;
    public final boolean fireproof;
    public final boolean bottled;

    InkType(String id, int color, double potency, double duration, double misfire, boolean waterproof, boolean invisible,
            boolean fireproof, boolean bottled) {
        this.fireproof = fireproof;
        this.bottled = bottled;
        this.id = id;
        this.color = color;
        this.potency = potency;
        this.duration = duration;
        this.misfire = misfire;
        this.waterproof = waterproof;
        this.invisible = invisible;
    }

    public static InkType byName(String name) {
        for (InkType t : values()) if (t.id.equals(name)) return t;
        return CONJURING;
    }

    public static InkType byId(int i) {
        InkType[] v = values();
        return i >= 0 && i < v.length ? v[i] : CONJURING;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
