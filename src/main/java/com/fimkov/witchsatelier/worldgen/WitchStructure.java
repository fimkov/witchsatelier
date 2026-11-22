package com.fimkov.witchsatelier.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.fimkov.witchsatelier.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * The witches' places as real structures, so they can be found with {@code /locate structure} and placed with
 * {@code /place structure}: a library ruin (the one kind left), chosen by {@code kind}, raised by the
 * same builder as before in the middle of its chunk, never on water.
 */
public class WitchStructure extends Structure {
    public static final MapCodec<WitchStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(settingsCodec(i),
            Codec.STRING.fieldOf("kind").forGetter(s -> s.kind)).apply(i, WitchStructure::new));

    final String kind;

    public WitchStructure(StructureSettings settings, String kind) {
        super(settings);
        this.kind = kind;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        ChunkPos cp = ctx.chunkPos();
        int x = cp.getMiddleBlockX(), z = cp.getMiddleBlockZ();
        int y = surface(ctx, x, z);
        if (y == Integer.MIN_VALUE || !fits(ctx, x, z)) return Optional.empty();
        BlockPos at = new BlockPos(x, y, z);
        long seed = ctx.random().nextLong();
        return Optional.of(new GenerationStub(at, b -> b.addPiece(new WitchStructurePiece(kind, at, seed))));
    }

    /** Height of the dry ground at (x, z), or MIN_VALUE where it is water. */
    private static int surface(GenerationContext ctx, int x, int z) {
        int y = ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
        NoiseColumn column = ctx.chunkGenerator().getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
        return column.getBlock(y - 1).getFluidState().isEmpty() ? y : Integer.MIN_VALUE;
    }

    /**
     * The same judgement of the ground its builder makes, made before the site is chosen, so that what /locate finds
     * is always built: a library ruin wants fairly level ground.
     */
    private boolean fits(GenerationContext ctx, int x, int z) {
        int step, rx, rz, most;
        switch (kind) {
            case "library_ruin" -> { step = 5; rx = 5; rz = 5; most = 4; }
            default -> { return true; }
        }
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int dx = -rx; dx <= rx; dx += step) for (int dz = -rz; dz <= rz; dz += step) {
            int h = surface(ctx, x + dx, z + dz);
            if (h == Integer.MIN_VALUE) return false;
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        return max - min <= most;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.WITCH_PLACE.get();
    }
}
