package com.witchcraft.registry;

import com.witchcraft.WitchCraft;
import com.witchcraft.entity.StrangerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, WitchCraft.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<com.witchcraft.entity.MentorEntity>> MENTOR = ENTITIES.register("mentor",
            () -> EntityType.Builder.<com.witchcraft.entity.MentorEntity>of(com.witchcraft.entity.MentorEntity::new, MobCategory.CREATURE).sized(0.6f, 1.95f)
                    .clientTrackingRange(10).build("witchcraft:mentor"));
    public static final DeferredHolder<EntityType<?>, EntityType<StrangerEntity>> STRANGER = ENTITIES.register("stranger",
            () -> EntityType.Builder.<StrangerEntity>of(StrangerEntity::new, MobCategory.CREATURE).sized(0.6f, 1.95f)
                    .clientTrackingRange(10).build("witchcraft:stranger"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.witchcraft.entity.BrimhatEntity>> BRIMHAT = ENTITIES.register("brimhat",
            () -> EntityType.Builder.<com.witchcraft.entity.BrimhatEntity>of(com.witchcraft.entity.BrimhatEntity::new, MobCategory.MONSTER).sized(0.6f, 1.95f)
                    .clientTrackingRange(8).build("witchcraft:brimhat"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.witchcraft.entity.KnightMoralisEntity>> KNIGHT_MORALIS = ENTITIES.register("knight_moralis",
            () -> EntityType.Builder.<com.witchcraft.entity.KnightMoralisEntity>of(com.witchcraft.entity.KnightMoralisEntity::new, MobCategory.CREATURE).sized(0.6f, 1.95f)
                    .clientTrackingRange(10).build("witchcraft:knight_moralis"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.witchcraft.entity.BrimhatElderEntity>> BRIMHAT_ELDER = ENTITIES.register("brimhat_elder",
            () -> EntityType.Builder.<com.witchcraft.entity.BrimhatElderEntity>of(com.witchcraft.entity.BrimhatElderEntity::new, MobCategory.MONSTER).sized(1.4f, 3.4f).fireImmune()
                    .clientTrackingRange(10).build("witchcraft:brimhat_elder"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.witchcraft.entity.SealchairEntity>> SEALCHAIR = ENTITIES.register("sealchair",
            () -> EntityType.Builder.<com.witchcraft.entity.SealchairEntity>of(com.witchcraft.entity.SealchairEntity::new, MobCategory.MISC).sized(0.9f, 1.4f)
                    .clientTrackingRange(10).build("witchcraft:sealchair"));

    public static void spawnPlacements(net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent e) {
        e.register(BRIMHAT.get(), net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND, net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules, net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private ModEntities() {}

    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(STRANGER.get(), StrangerEntity.attributes().build());
        e.put(MENTOR.get(), com.witchcraft.entity.MentorEntity.attributes().build());
        e.put(BRIMHAT.get(), com.witchcraft.entity.BrimhatEntity.attributes().build());
        e.put(KNIGHT_MORALIS.get(), com.witchcraft.entity.KnightMoralisEntity.attributes().build());
        e.put(BRIMHAT_ELDER.get(), com.witchcraft.entity.BrimhatElderEntity.attributes().build());
        e.put(SEALCHAIR.get(), com.witchcraft.entity.SealchairEntity.attributes().build());
    }
}
