package com.witchcraft.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.witchcraft.WitchCraft;
import com.witchcraft.glyph.page.GlyphLibrary;
import com.witchcraft.registry.ModComponents;
import com.witchcraft.registry.ModItems;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Old dungeons, libraries and temples sometimes hold a grimoire, or a few crumpled pages with glyphs
 * someone prepared and never used (their rings still open, ready to close).
 */
public class SpellLootModifier extends LootModifier {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, WitchCraft.MODID);

    public static final MapCodec<SpellLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst).and(inst.group(
            Codec.FLOAT.fieldOf("grimoire_chance").forGetter(m -> m.grimoireChance),
            Codec.FLOAT.fieldOf("page_chance").forGetter(m -> m.pageChance),
            Codec.FLOAT.optionalFieldOf("forbidden_chance", 0f).forGetter(m -> m.forbiddenChance))).apply(inst, SpellLootModifier::new));

    public static final Supplier<MapCodec<SpellLootModifier>> SPELL_LOOT = SERIALIZERS.register("spell_loot", () -> CODEC);

    private final float grimoireChance, pageChance, forbiddenChance;

    public SpellLootModifier(LootItemCondition[] conditions, float grimoireChance, float pageChance, float forbiddenChance) {
        super(conditions);
        this.grimoireChance = grimoireChance;
        this.pageChance = pageChance;
        this.forbiddenChance = forbiddenChance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        var rnd = ctx.getRandom();
        if (rnd.nextFloat() < grimoireChance) {
            loot.add(GlyphLibrary.grimoireBook(rnd, 2 + rnd.nextInt(4)));
        }
        if (rnd.nextFloat() < pageChance) {
            int n = 1 + rnd.nextInt(3);
            for (int i = 0; i < n; i++) {
                loot.add(GlyphLibrary.preparedPageItem(rnd));
            }
        }
        if (rnd.nextFloat() < forbiddenChance) loot.add(GlyphLibrary.forbiddenPageItem(rnd));
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
