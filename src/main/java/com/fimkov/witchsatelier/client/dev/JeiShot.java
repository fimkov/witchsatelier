package com.fimkov.witchsatelier.client.dev;

/** Screenshot harness only, and only with JEI loaded: opens JEI on the Awakening recipes. */
final class JeiShot {
    private JeiShot() {}

    static void open() {
        var rt = com.fimkov.witchsatelier.compat.WitchsAtelierJeiPlugin.runtime;
        if (rt != null) rt.getRecipesGui().showTypes(java.util.List.of(com.fimkov.witchsatelier.compat.AwakeningJeiCategory.TYPE));
    }
}
