package com.witchcraft.glyph;

import com.witchcraft.registry.ModAttachments;
import com.witchcraft.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

/**
 * Mistress Wren's lessons. Each asks the apprentice to work one kind of magic; once done (anywhere, any time) she
 * hands over a gift the next time they come to her, and sets the next lesson.
 */
public final class Lessons {
    private Lessons() {}

    private record Lesson(String key, String event, Function<ServerPlayer, ItemStack> gift) {}

    private static final Lesson[] LESSONS = {
            new Lesson("lantern", "element_light", p -> com.witchcraft.glyph.page.GlyphLibrary.grimoireBook(p.getRandom(), 4)),
            new Lesson("imagined", "imagined_ring", p -> new ItemStack(ModItems.CONJURING_INK.get(), 3)),
            new Lesson("page", "cast_page", p -> new ItemStack(ModItems.PALM_QUIRE.get())),
            new Lesson("nested", "cast_nested", p -> new ItemStack(ModItems.LASTING_INK.get(), 2)),
            new Lesson("linked", "linked_glyphs", p -> new ItemStack(ModItems.SEARNEEDLE.get())),
    };

    /** Something magical happened (the same events advancements listen to). */
    public static void onEvent(ServerPlayer p, String event) {
        int at = p.getData(ModAttachments.LESSON.get());
        if (at >= LESSONS.length || p.getData(ModAttachments.LESSON_DONE.get())) return;
        if (!LESSONS[at].event().equals(event)) return;
        p.setData(ModAttachments.LESSON_DONE.get(), true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5f, 1.5f);
    }

    /** The apprentice speaks to their teacher. */
    public static void talk(ServerPlayer p, Entity teacher) {
        int at = p.getData(ModAttachments.LESSON.get());
        boolean met = p.getData(ModAttachments.MET_MENTOR.get());
        if (!met) {
            p.setData(ModAttachments.MET_MENTOR.get(), true);
            say(p, teacher, "welcome");
            // nobody learns without a pen: she lends one to a visitor who came without
            boolean hasPen = false;
            for (ItemStack st : p.getInventory().items) if (st.getItem() instanceof com.witchcraft.item.InkPenItem) hasPen = true;
            if (!hasPen) {
                for (ItemStack gift : new ItemStack[]{new ItemStack(ModItems.INK_PEN.get()), new ItemStack(ModItems.CONJURING_INK.get())}) {
                    if (!p.getInventory().add(gift)) p.drop(gift, false);
                }
                say(p, teacher, "lend_pen");
            }
        }
        if (at < LESSONS.length && p.getData(ModAttachments.LESSON_DONE.get())) {
            Lesson done = LESSONS[at];
            say(p, teacher, done.key() + ".done");
            ItemStack gift = done.gift().apply(p);
            if (!p.getInventory().add(gift)) p.drop(gift, false);
            p.level().playSound(null, teacher.getX(), teacher.getY(), teacher.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6f, 1.2f);
            at++;
            p.setData(ModAttachments.LESSON.get(), at);
            p.setData(ModAttachments.LESSON_DONE.get(), false);
            com.witchcraft.advancement.MagicTrigger.fire(p, "lesson");
        }
        if (at >= LESSONS.length) say(p, teacher, "graduated");
        else say(p, teacher, LESSONS[at].key());
    }

    private static void say(ServerPlayer p, Entity teacher, String key) {
        p.sendSystemMessage(Component.translatable("chat.witchcraft.mentor.speaks", teacher.getDisplayName(),
                Component.translatable("lesson.witchcraft." + key).withStyle(ChatFormatting.ITALIC)));
    }
}
