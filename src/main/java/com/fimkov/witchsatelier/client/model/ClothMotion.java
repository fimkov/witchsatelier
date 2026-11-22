package com.fimkov.witchsatelier.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

public final class ClothMotion {
    private ClothMotion() {}

    private static final Map<ModelPart, float[]> REST = new WeakHashMap<>();

    private static float[] rest(ModelPart p) {
        return REST.computeIfAbsent(p, k -> new float[]{k.xRot, k.yRot, k.zRot});
    }

    public static void apply(HumanoidModel<?> m, LivingEntity e, float limbSwing, float amount, float age, float flare) {
        amount = Mth.clamp(amount, 0, 1);
        boolean airborne = e != null && !e.onGround() && !e.isPassenger();
        float fall = e == null ? 0 : (float) Mth.clamp(-e.getDeltaMovement().y * 1.5, 0, 1);

        float left = -m.leftLeg.xRot, right = -m.rightLeg.xRot;
        if (e != null && e.isPassenger()) { left = 0.6f; right = 0.6f; }

        for (String prefix : new String[]{"skirt_", "tunic_"}) {
            for (int i = 0; i < 12; i++) {
                String name = prefix + i;
                if (!m.body.hasChild(name)) continue;
                ModelPart p = m.body.getChild(name);
                float[] r = rest(p);
                double th = Math.toRadians(i * 30);
                float front = (float) Math.cos(th), side = (float) Math.sin(th);
                float leg = left * (0.5f + 0.5f * side) + right * (0.5f - 0.5f * side);
                float push = front > 0 ? front * Math.max(0, leg) : -front * Math.max(0, -leg);
                float d = 0.1f * amount + push * 0.7f + Mth.sin(age * 0.07f + i * 0.9f) * 0.012f + flare * 0.35f + fall * 0.5f;
                p.xRot = r[0] - d;
            }
        }

        for (int i = 0; i <= 10; i++) {
            String name = "cape_" + i;
            if (!m.body.hasChild(name)) continue;
            ModelPart p = m.body.getChild(name);
            float[] r = rest(p);
            boolean back = r[0] > 1.5f;
            boolean sideways = Math.abs(Math.abs(r[1]) - Mth.HALF_PI) < 0.3f;
            float breathe = Mth.sin(age * 0.06f + i * 0.7f) * 0.02f;
            float stream = back ? amount * 0.55f + fall * 0.8f : sideways ? amount * 0.22f + fall * 0.35f : amount * 0.08f;
            float flutter = amount > 0.2f ? Mth.sin(age * 0.45f + i * 1.3f) * 0.05f * amount : 0;

            float lift = Math.min(back ? 0.95f : 0.5f, stream + flutter + flare * (back ? 0.3f : 0.15f) + (airborne ? 0.12f : 0));
            p.xRot = r[0] - (lift + breathe);
        }

        for (String name : new String[]{"ribbon_l", "ribbon_r"}) {
            if (!m.body.hasChild(name)) continue;
            ModelPart p = m.body.getChild(name);
            float[] r = rest(p);
            float sgn = name.endsWith("l") ? 1 : -1;
            p.xRot = r[0] + amount * 0.8f + fall * 0.9f + Mth.sin(age * 0.11f + sgn) * 0.04f + flare * 0.5f;
            p.zRot = r[2] + sgn * Mth.sin(age * 0.17f) * 0.05f * (0.3f + amount);
        }

        for (String name : new String[]{"tie_l", "tie_r"}) {
            if (!m.body.hasChild(name)) continue;
            ModelPart p = m.body.getChild(name);
            float[] r = rest(p);
            float sgn = name.endsWith("l") ? 1 : -1;
            p.xRot = r[0] + Mth.cos(limbSwing * 0.6662f + (sgn > 0 ? 0 : Mth.PI)) * 0.35f * amount;
            p.zRot = r[2] + sgn * (0.1f * amount + flare * 0.3f);
        }

        if (m.head.hasChild("tip")) {
            ModelPart tip = m.head.getChild("tip");
            float[] r = rest(tip);
            float bob = Mth.cos(limbSwing * 1.3324f) * 0.06f * amount;
            tip.xRot = r[0] + amount * 0.12f + bob + Mth.sin(age * 0.05f) * 0.02f + fall * 0.2f;
            tip.zRot = r[2] + Mth.sin(limbSwing * 0.6662f) * 0.05f * amount;
            if (tip.hasChild("cord")) {
                ModelPart cord = tip.getChild("cord");
                float[] c = rest(cord);
                cord.zRot = c[2] + Mth.sin(age * 0.2f) * 0.08f + Mth.cos(limbSwing * 0.6662f) * 0.2f * amount;
            }
        }
    }
}
