package com.fimkov.witchsatelier.glyph;

import com.fimkov.witchsatelier.network.Payloads;
import com.fimkov.witchsatelier.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

/** Drawing techniques a witch learns by reading about them. */
public final class Techniques {
    private Techniques() {}

    public static boolean knowsImaginedRing(ServerPlayer p) {
        return p.getData(ModAttachments.IMAGINED_RING.get());
    }

    /** The page on the Imagined Ring was read to the end of its spread: from now on rings come out true. */
    public static void learnImaginedRing(ServerPlayer p) {
        if (knowsImaginedRing(p)) return;
        p.setData(ModAttachments.IMAGINED_RING.get(), true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.4f);
        com.fimkov.witchsatelier.advancement.MagicTrigger.fire(p, "imagined_ring");
        sync(p);
    }

    public static void sync(ServerPlayer p) {
        // test players and other fakes have no client to tell
        if (p.connection == null || !p.connection.hasChannel(Payloads.Techniques.TYPE)) return;
        PacketDistributor.sendToPlayer(p, new Payloads.Techniques(knowsImaginedRing(p)));
    }
}
