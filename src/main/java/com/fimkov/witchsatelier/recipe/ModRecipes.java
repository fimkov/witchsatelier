package com.fimkov.witchsatelier.recipe;

import com.fimkov.witchsatelier.WitchsAtelier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    private ModRecipes() {}

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, WitchsAtelier.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<WitchGrimoireCloning>> WITCH_GRIMOIRE_CLONING =
            SERIALIZERS.register("witch_grimoire_cloning", () -> new SimpleCraftingRecipeSerializer<>(WitchGrimoireCloning::new));
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<GlyphArrowRecipe>> GLYPH_ARROW =
            SERIALIZERS.register("glyph_arrow", () -> new SimpleCraftingRecipeSerializer<>(GlyphArrowRecipe::new));
}
