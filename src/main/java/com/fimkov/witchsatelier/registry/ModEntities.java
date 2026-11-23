package com.fimkov.witchsatelier.registry;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.entity.BrimhatEntity;
import com.fimkov.witchsatelier.entity.GlyphArrow;
import com.fimkov.witchsatelier.entity.KnightMoralisEntity;
import com.fimkov.witchsatelier.entity.SealchairEntity;
import com.fimkov.witchsatelier.entity.StrangerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, WitchsAtelier.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<StrangerEntity>> STRANGER = ENTITIES.register("stranger",
            () -> EntityType.Builder.<StrangerEntity>of(StrangerEntity::new, MobCategory.CREATURE).sized(0.6f, 1.95f)
                    .clientTrackingRange(10).build("witchsatelier:stranger"));

    public static final DeferredHolder<EntityType<?>, EntityType<BrimhatEntity>> BRIMHAT = ENTITIES.register("brimhat",
            () -> EntityType.Builder.<BrimhatEntity>of(BrimhatEntity::new, MobCategory.MONSTER).sized(0.6f, 1.95f)
                    .clientTrackingRange(8).build("witchsatelier:brimhat"));
    public static final DeferredHolder<EntityType<?>, EntityType<KnightMoralisEntity>> KNIGHT_MORALIS = ENTITIES.register("knight_moralis",
            () -> EntityType.Builder.<KnightMoralisEntity>of(KnightMoralisEntity::new, MobCategory.CREATURE).sized(0.6f, 1.95f)
                    .clientTrackingRange(10).build("witchsatelier:knight_moralis"));

    public static final DeferredHolder<EntityType<?>, EntityType<SealchairEntity>> SEALCHAIR = ENTITIES.register("sealchair",
            () -> EntityType.Builder.<SealchairEntity>of(SealchairEntity::new, MobCategory.MISC).sized(0.9f, 1.4f)
                    .clientTrackingRange(10).build("witchsatelier:sealchair"));

    public static final DeferredHolder<EntityType<?>, EntityType<GlyphArrow>> GLYPH_ARROW = ENTITIES.register("glyph_arrow",
            () -> EntityType.Builder.<GlyphArrow>of(GlyphArrow::new, MobCategory.MISC).sized(0.5f, 0.5f)
                    .eyeHeight(0.13f).clientTrackingRange(4).updateInterval(20).build("witchsatelier:glyph_arrow"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.fimkov.witchsatelier.entity.WaterHorseEntity>> WATER_HORSE = ENTITIES.register("water_horse",
            () -> EntityType.Builder.<com.fimkov.witchsatelier.entity.WaterHorseEntity>of(com.fimkov.witchsatelier.entity.WaterHorseEntity::new, MobCategory.MISC)
                    .sized(1.3965f, 1.6f).clientTrackingRange(10).noSave().build("witchsatelier:water_horse"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.fimkov.witchsatelier.entity.DoorknobEntity>> DOORKNOB = ENTITIES.register("doorknob",
            () -> EntityType.Builder.<com.fimkov.witchsatelier.entity.DoorknobEntity>of(com.fimkov.witchsatelier.entity.DoorknobEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(8).updateInterval(20).noSummon().build("witchsatelier:doorknob"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.fimkov.witchsatelier.entity.FluttergramEntity>> FLUTTERGRAM = ENTITIES.register("fluttergram",
            () -> EntityType.Builder.<com.fimkov.witchsatelier.entity.FluttergramEntity>of(com.fimkov.witchsatelier.entity.FluttergramEntity::new, MobCategory.MISC)
                    .sized(0.4f, 0.3f).clientTrackingRange(8).updateInterval(2).noSave().noSummon().build("witchsatelier:fluttergram"));

    public static void spawnPlacements(RegisterSpawnPlacementsEvent e) {
        e.register(BRIMHAT.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private ModEntities() {}

    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(STRANGER.get(), StrangerEntity.attributes().build());
        e.put(BRIMHAT.get(), BrimhatEntity.attributes().build());
        e.put(KNIGHT_MORALIS.get(), KnightMoralisEntity.attributes().build());
        e.put(SEALCHAIR.get(), SealchairEntity.attributes().build());
        e.put(WATER_HORSE.get(), com.fimkov.witchsatelier.entity.WaterHorseEntity.attributes().build());
    }
}
