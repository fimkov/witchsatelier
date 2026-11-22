package com.fimkov.witchsatelier.registry;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/**
 * Ink is brewed. Water + a blackened silverwood twig gives woodcruor; woodcruor + glowstone gives conjuring ink;
 * conjuring ink takes one more ingredient for each special ink.
 */
public final class ModBrewing {
    private ModBrewing() {}

    public static void register(RegisterBrewingRecipesEvent e) {
        PotionBrewing.Builder b = e.getBuilder();
        Ingredient water = DataComponentIngredient.of(false, PotionContents.createItemStack(Items.POTION, Potions.WATER));
        b.addRecipe(water, Ingredient.of(ModItems.BLACKENED_TWIG.get()), new ItemStack(ModItems.WOODCRUOR.get()));
        b.addRecipe(Ingredient.of(ModItems.WOODCRUOR.get()), Ingredient.of(Items.GLOWSTONE_DUST), new ItemStack(ModItems.CONJURING_INK.get()));
        Ingredient conjuring = Ingredient.of(ModItems.CONJURING_INK.get());
        b.addRecipe(conjuring, Ingredient.of(Items.PRISMARINE_SHARD), new ItemStack(ModItems.WATERPROOF_INK.get()));
        b.addRecipe(conjuring, Ingredient.of(Items.PHANTOM_MEMBRANE), new ItemStack(ModItems.INVISIBLE_INK.get()));
        b.addRecipe(conjuring, Ingredient.of(Items.HONEYCOMB), new ItemStack(ModItems.LASTING_INK.get()));
    }
}
