package com.fimkov.witchsatelier.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerAccess {
    @Accessor("aboveGroundTickCount")
    void witchsatelier$setAboveGroundTickCount(int ticks);
}
