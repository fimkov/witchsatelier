package com.fimkov.witchsatelier.compat;

import com.fimkov.witchsatelier.WitchsAtelier;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Only loaded when EMI is installed: the same information pages JEI gets. */
@EmiEntrypoint
public class WitchsAtelierEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry reg) {
        reg.addCategory(AwakeningEmiRecipe.CATEGORY);
        reg.addWorkstation(AwakeningEmiRecipe.CATEGORY, dev.emi.emi.api.stack.EmiStack.of(com.fimkov.witchsatelier.registry.ModItems.INK_PEN.get()));
        for (var e : com.fimkov.witchsatelier.item.artifact.Awakening.ALL) reg.addRecipe(new AwakeningEmiRecipe(e));
        for (ItemInfo.Entry e : ItemInfo.entries()) {
            String name = BuiltInRegistries.ITEM.getKey(e.item()).getPath();
            reg.addRecipe(new EmiInfoRecipe(List.of(EmiStack.of(e.item())), List.of(e.lines()),
                    ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "/info/" + name)));
        }
    }
}
