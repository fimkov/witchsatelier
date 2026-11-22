package com.fimkov.witchsatelier.advancement;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.spell.SpellProgram;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

public class MagicTrigger extends SimpleCriterionTrigger<MagicTrigger.Instance> {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, WitchsAtelier.MODID);
    public static final Supplier<MagicTrigger> MAGIC = TRIGGERS.register("magic", MagicTrigger::new);

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public record Instance(Optional<ContextAwarePredicate> player, String event) implements SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Codec.STRING.fieldOf("event").forGetter(Instance::event)).apply(i, Instance::new));
    }

    public static void fire(Player player, String event) {
        if (player instanceof ServerPlayer sp) MAGIC.get().trigger(sp, inst -> inst.event().equals(event));
    }

    public static void cast(Player player, SpellProgram p, String source) {
        if (!(player instanceof ServerPlayer)) return;
        fire(player, "cast");
        if (source != null) fire(player, "cast_" + source);
        fire(player, "form_" + p.form().name().toLowerCase(Locale.ROOT));
        if (p.element() != null) fire(player, "element_" + p.element().name().toLowerCase(Locale.ROOT));
        if (p.centerSign() != null) fire(player, "center_" + p.centerSign().name().toLowerCase(Locale.ROOT));
        if (!p.layers().isEmpty() || p.cushion()) fire(player, "cast_nested");
        if (p.birds() > 0) fire(player, "cast_birds");
        if (p.power() >= 30) fire(player, "cast_mighty");
    }
}
