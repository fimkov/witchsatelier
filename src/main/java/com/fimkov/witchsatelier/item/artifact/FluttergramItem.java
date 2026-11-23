package com.fimkov.witchsatelier.item.artifact;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.entity.FluttergramEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A sheet of magic paper folded into a bird. Named on an anvil after a player and used with a letter in the other hand, it folds
 * the letter in and flies it to them, wherever they are; it takes longer than a windowway, up to a minute.
 */
public class FluttergramItem extends Item {
    /** What a fluttergram will carry: letters and other sheets of paper, never a parcel of goods. */
    public static final TagKey<Item> LETTERS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "letters"));

    public FluttergramItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bird = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack letter = player.getItemInHand(otherHand);
        Component name = bird.get(DataComponents.CUSTOM_NAME);
        if (!(player instanceof ServerPlayer sp)) {
            return name != null && letter.is(LETTERS) ? InteractionResultHolder.success(bird) : InteractionResultHolder.pass(bird);
        }
        if (name == null || name.getString().isBlank()) {
            sp.displayClientMessage(Component.translatable("message.witchsatelier.fluttergram.unnamed"), true);
            return InteractionResultHolder.fail(bird);
        }
        if (!letter.is(LETTERS)) {
            sp.displayClientMessage(Component.translatable("message.witchsatelier.fluttergram.no_letter"), true);
            return InteractionResultHolder.fail(bird);
        }
        Mailroom.Recipient to = Mailroom.resolve(sp.server, name.getString());
        if (to == null) {
            sp.displayClientMessage(Component.translatable("message.witchsatelier.fluttergram.unknown", name.getString().trim()), true);
            return InteractionResultHolder.fail(bird);
        }
        Mailroom.send(sp, to, letter.split(1));
        if (!sp.hasInfiniteMaterials()) bird.shrink(1);
        ServerPlayer target = sp.server.getPlayerList().getPlayer(to.id());
        FluttergramEntity.depart(sp.serverLevel(), sp, target != null && target.level() == sp.level() && target != sp ? target.position() : null);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1.6f);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PARROT_FLY, SoundSource.PLAYERS, 0.8f, 1.4f);
        sp.displayClientMessage(Component.translatable("message.witchsatelier.fluttergram.sent", to.name()), true);
        return InteractionResultHolder.success(bird);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        if (Awakening.dormant(stack)) return;
        lines.add(Component.translatable("item.witchsatelier.fluttergram.desc").withStyle(ChatFormatting.GRAY));
    }
}
