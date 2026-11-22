package com.fimkov.witchsatelier.registry;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.entity.StrangerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, WitchsAtelier.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<StrangerEntity>> STRANGER = ENTITIES.register("stranger",
            () -> EntityType.Builder.<StrangerEntity>of(StrangerEntity::new, MobCategory.CREATURE).sized(0.6f, 1.95f)
                    .clientTrackingRange(10).build("witchsatelier:stranger"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.fimkov.witchsatelier.entity.BrimhatEntity>> BRIMHAT = ENTITIES.register("brimhat",
            () -> EntityType.Builder.<com.fimkov.witchsatelier.entity.BrimhatEntity>of(com.fimkov.witchsatelier.entity.BrimhatEntity::new, MobCategory.MONSTER).sized(0.6f, 1.95f)
                    .clientTrackingRange(8).build("witchsatelier:brimhat"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.fimkov.witchsatelier.entity.KnightMoralisEntity>> KNIGHT_MORALIS = ENTITIES.register("knight_moralis",
            () -> EntityType.Builder.<com.fimkov.witchsatelier.entity.KnightMoralisEntity>of(com.fimkov.witchsatelier.entity.KnightMoralisEntity::new, MobCategory.CREATURE).sized(0.6f, 1.95f)
                    .clientTrackingRange(10).build("witchsatelier:knight_moralis"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.fimkov.witchsatelier.entity.SealchairEntity>> SEALCHAIR = ENTITIES.register("sealchair",
            () -> EntityType.Builder.<com.fimkov.witchsatelier.entity.SealchairEntity>of(com.fimkov.witchsatelier.entity.SealchairEntity::new, MobCategory.MISC).sized(0.9f, 1.4f)
                    .clientTrackingRange(10).build("witchsatelier:sealchair"));

    public static void spawnPlacements(net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent e) {
        e.register(BRIMHAT.get(), net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND, net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules, net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private ModEntities() {}

    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(STRANGER.get(), StrangerEntity.attributes().build());
        e.put(BRIMHAT.get(), com.fimkov.witchsatelier.entity.BrimhatEntity.attributes().build());
        e.put(KNIGHT_MORALIS.get(), com.fimkov.witchsatelier.entity.KnightMoralisEntity.attributes().build());
        e.put(SEALCHAIR.get(), com.fimkov.witchsatelier.entity.SealchairEntity.attributes().build());
    }
}
