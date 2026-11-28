package com.fimkov.witchsatelier.windowway;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The far end of window {@code id}, told to a client that cannot find it among the windows of its own dimension:
 * it lies in another dimension, or it is a Sill that is not lit just now.
 */
public record FarFrame(long id, ResourceKey<Level> dim, PortalFrame frame) {
    public static final StreamCodec<ByteBuf, FarFrame> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, FarFrame::id,
            ResourceKey.streamCodec(Registries.DIMENSION), FarFrame::dim,
            PortalFrame.CODEC, FarFrame::frame,
            FarFrame::new);
}
