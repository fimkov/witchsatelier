package com.fimkov.witchsatelier.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The server's count of how long a player has hung in the air: past four seconds it kicks them for flying. */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerAccess {
    @Accessor("aboveGroundTickCount")
    void witchsatelier$setAboveGroundTickCount(int ticks);
}
