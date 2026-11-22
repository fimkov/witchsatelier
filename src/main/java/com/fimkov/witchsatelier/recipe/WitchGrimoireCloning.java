package com.fimkov.witchsatelier.recipe;

import com.fimkov.witchsatelier.item.WitchGrimoireItem;
import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class WitchGrimoireCloning extends CustomRecipe {
    public WitchGrimoireCloning(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return source(input) >= 0 && blanks(input) > 0;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        int s = source(input), n = blanks(input);
        if (s < 0 || n == 0) return ItemStack.EMPTY;
        return input.getItem(s).copyWithCount(n);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> left = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        int s = source(input);
        if (s >= 0) left.set(s, input.getItem(s).copyWithCount(1));
        return left;
    }

    private static int source(CraftingInput input) {
        int found = -1;
        for (int i = 0; i < input.size(); i++) {
            ItemStack st = input.getItem(i);
            if (st.isEmpty()) continue;
            if (!st.is(ModItems.WITCH_GRIMOIRE.get())) return -1;
            if (WitchGrimoireItem.written(st)) {
                if (found >= 0) return -1;
                found = i;
            }
        }
        return found;
    }

    private static int blanks(CraftingInput input) {
        int n = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack st = input.getItem(i);
            if (st.is(ModItems.WITCH_GRIMOIRE.get()) && !WitchGrimoireItem.written(st)) n++;
        }
        return n;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w * h >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.WITCH_GRIMOIRE_CLONING.get();
    }
}
