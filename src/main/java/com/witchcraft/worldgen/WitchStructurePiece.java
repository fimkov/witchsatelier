package com.witchcraft.worldgen;

import com.witchcraft.registry.ModFeatures;
import com.witchcraft.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.Optional;

/**
 * The one piece of a {@link WitchStructure}: the whole place, built in one go by its builder when the chunk it
 * stands in is decorated (it reaches no further than the chunks next to it, as any feature may).
 */
public class WitchStructurePiece extends StructurePiece {
    private final String kind;
    private final BlockPos origin;
    private final long seed;

    public WitchStructurePiece(String kind, BlockPos origin, long seed) {
        super(ModStructures.WITCH_PIECE.get(), 0, new BoundingBox(origin.getX() - 14, origin.getY() - 6, origin.getZ() - 14,
                origin.getX() + 14, origin.getY() + 24, origin.getZ() + 14));
        this.kind = kind;
        this.origin = origin;
        this.seed = seed;
    }

    public WitchStructurePiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(ModStructures.WITCH_PIECE.get(), tag);
        this.kind = tag.getString("Kind");
        this.origin = NbtUtils.readBlockPos(tag, "Origin").orElse(BlockPos.ZERO);
        this.seed = tag.getLong("Seed");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putString("Kind", kind);
        tag.put("Origin", NbtUtils.writeBlockPos(origin));
        tag.putLong("Seed", seed);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        if (!chunkPos.equals(new ChunkPos(origin))) return; // built once, from its own chunk
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ());
        var ctx = new FeaturePlaceContext<>(Optional.empty(), level, generator, RandomSource.create(seed),
                new BlockPos(origin.getX(), y, origin.getZ()), NoneFeatureConfiguration.INSTANCE);
        // the structure already judged the site fit: build, whatever small things have changed on it since
        switch (kind) {
            case "brimhat_hideout" -> ModFeatures.HIDEOUT.get().build(ctx, false);
            case "library_ruin" -> ModFeatures.LIBRARY.get().build(ctx, false);
            default -> ModFeatures.ATELIER.get().build(ctx, false);
        }
    }
}
