package com.fimkov.witchsatelier.compat;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.item.artifact.Awakening;
import com.fimkov.witchsatelier.registry.ModItems;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

@EmiEntrypoint
public class WitchsAtelierEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry reg) {
        reg.addCategory(AwakeningEmiRecipe.CATEGORY);
        reg.addWorkstation(AwakeningEmiRecipe.CATEGORY, EmiStack.of(ModItems.INK_PEN.get()));
        for (var e : Awakening.ALL) reg.addRecipe(new AwakeningEmiRecipe(e));
        for (ItemInfo.Entry e : ItemInfo.entries()) {
            String name = BuiltInRegistries.ITEM.getKey(e.item()).getPath();
            reg.addRecipe(new EmiInfoRecipe(List.of(EmiStack.of(e.item())), List.of(e.lines()),
                    ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "/info/" + name)));
        }
    }
}
