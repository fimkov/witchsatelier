package com.fimkov.witchsatelier.worldgen;

import com.fimkov.witchsatelier.registry.ModFeatures;
import com.fimkov.witchsatelier.registry.ModStructures;
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
        if (!chunkPos.equals(new ChunkPos(origin))) return;
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ());
        var ctx = new FeaturePlaceContext<>(Optional.empty(), level, generator, RandomSource.create(seed),
                new BlockPos(origin.getX(), y, origin.getZ()), NoneFeatureConfiguration.INSTANCE);

        switch (kind) {
            default -> ModFeatures.LIBRARY.get().build(ctx, false);
        }
    }
}
