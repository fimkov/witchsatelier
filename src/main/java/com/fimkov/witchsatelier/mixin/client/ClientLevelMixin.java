package com.fimkov.witchsatelier.mixin.client;

import com.fimkov.witchsatelier.client.portal.WindowwayClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the far sides of open Windowways when a block or the light around it changes, so they re-mesh. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void witchsatelier$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        WindowwayClient.blockChanged(pos);
    }

    @Inject(method = "setSectionDirtyWithNeighbors", at = @At("HEAD"))
    private void witchsatelier$sectionChanged(int x, int y, int z, CallbackInfo ci) {
        WindowwayClient.sectionChanged(x, y, z);
    }
}
