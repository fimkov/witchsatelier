package com.fimkov.witchsatelier.client.dev;

import com.fimkov.witchsatelier.compat.AwakeningJeiCategory;
import com.fimkov.witchsatelier.compat.WitchsAtelierJeiPlugin;

import java.util.List;

final class JeiShot {
    private JeiShot() {}

    static void open() {
        var rt = WitchsAtelierJeiPlugin.runtime;
        if (rt != null) rt.getRecipesGui().showTypes(List.of(AwakeningJeiCategory.TYPE));
    }
}
