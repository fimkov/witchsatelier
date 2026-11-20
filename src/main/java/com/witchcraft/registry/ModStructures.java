package com.witchcraft.registry;

import com.witchcraft.WitchCraft;
import com.witchcraft.worldgen.WitchStructure;
import com.witchcraft.worldgen.WitchStructurePiece;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The witches' places as structures (see {@link WitchStructure}). */
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, WitchCraft.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, WitchCraft.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<WitchStructure>> WITCH_PLACE = TYPES.register("witch_place", () -> () -> WitchStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> WITCH_PIECE = PIECES.register("witch_place",
            () -> (StructurePieceType) WitchStructurePiece::new);

    private ModStructures() {}
}
