package com.fimkov.witchsatelier.glyph.ink;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record LoadedInk(InkType type, int amount) {
    public static final Codec<LoadedInk> CODEC = RecordCodecBuilder.create(i -> i.group(
            InkType.CODEC.fieldOf("type").forGetter(LoadedInk::type),
            Codec.INT.fieldOf("amount").forGetter(LoadedInk::amount)
    ).apply(i, LoadedInk::new));

    public static final StreamCodec<ByteBuf, LoadedInk> STREAM_CODEC = StreamCodec.composite(
            InkType.STREAM_CODEC, LoadedInk::type, ByteBufCodecs.VAR_INT, LoadedInk::amount, LoadedInk::new);
}
