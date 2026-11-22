package com.fimkov.witchsatelier.entity;

import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A stranger in a broad-brimmed hat and a long dark mantle who sometimes passes by villages, rarely, and never
 * for long. The first time someone speaks to them they press an old grimoire and a pen into that person's
 * hands without a word; after that they trade in the things witches need.
 */
public class StrangerEntity extends WanderingTrader {
    public static final String GIFT_TAG = "witchsatelier_stranger_gift";

    public StrangerEntity(EntityType<? extends WanderingTrader> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND && !player.getPersistentData().getBoolean(GIFT_TAG)) {
            player.getPersistentData().putBoolean(GIFT_TAG, true);
            // a primer: how glyphs work, and three to start with; the rest is out there to be found
            give(player, GlyphLibrary.volumeBook(GlyphLibrary.PRIMER, random));
            give(player, new ItemStack(ModItems.INK_PEN.get()));
            give(player, new ItemStack(ModItems.CONJURING_INK.get()));
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1f, 0.6f);
            if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 1.5, getZ(), 40, 0.5, 0.6, 0.5, 0.6);
            getLookControl().setLookAt(player);
            com.fimkov.witchsatelier.advancement.MagicTrigger.fire(player, "stranger_gift");
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    /** They say nothing at all. */
    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return null;
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    @Override
    protected void updateTrades() {
        MerchantOffers offers = getOffers();
        ItemStack book = GlyphLibrary.randomVolume(random);
        ItemStack page = GlyphLibrary.preparedPageItem(random);
        ItemStack page2 = GlyphLibrary.preparedPageItem(random);
        for (MerchantOffer o : List.of(
                new MerchantOffer(new ItemCost(Items.EMERALD, 12), book, 1, 10, 0.05f),
                new MerchantOffer(new ItemCost(Items.EMERALD, 8), GlyphLibrary.contraptionsBook(), 1, 8, 0.05f),
                new MerchantOffer(new ItemCost(Items.EMERALD, 16), GlyphLibrary.volumeBook(GlyphLibrary.RING_NOTES, random), 1, 12, 0.05f),
                new MerchantOffer(new ItemCost(Items.EMERALD, 2), page, 3, 2, 0.05f),
                new MerchantOffer(new ItemCost(Items.EMERALD, 2), page2, 3, 2, 0.05f),
                new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(ModItems.INK_PEN.get()), 2, 4, 0.05f),
                new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(ModItems.CONJURING_INK.get()), 6, 2, 0.05f),
                new MerchantOffer(new ItemCost(Items.EMERALD, 5), new ItemStack(ModItems.WATERPROOF_INK.get()), 3, 3, 0.05f),
                new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(ModItems.PALM_QUIRE.get()), 1, 5, 0.05f),
                new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(ModItems.SILVERWOOD_SAPLING.get()), 2, 5, 0.05f),
                new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(ModItems.BLACKENED_TWIG.get(), 4), 4, 2, 0.05f))) {
            offers.add(o);
        }
    }

    /** Steps come with the swish of the long robe. */
    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        super.playStepSound(pos, state);
        if (random.nextInt(2) == 0) playSound(com.fimkov.witchsatelier.registry.ModSounds.CLOTH_RUSTLE.get(), 0.35f, 0.85f + random.nextFloat() * 0.3f);
    }
}
