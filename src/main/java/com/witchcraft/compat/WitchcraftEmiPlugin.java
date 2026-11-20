package com.witchcraft.compat;

import com.witchcraft.WitchCraft;
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
public class WitchcraftEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry reg) {
        for (ItemInfo.Entry e : ItemInfo.entries()) {
            String name = BuiltInRegistries.ITEM.getKey(e.item()).getPath();
            reg.addRecipe(new EmiInfoRecipe(List.of(EmiStack.of(e.item())), List.of(e.lines()),
                    ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "/info/" + name)));
        }
    }
}
