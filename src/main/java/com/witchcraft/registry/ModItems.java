package com.witchcraft.registry;

import com.witchcraft.WitchCraft;
import com.witchcraft.glyph.ink.InkType;
import com.witchcraft.item.InkBottleItem;
import com.witchcraft.item.InkPenItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WitchCraft.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WitchCraft.MODID);

    public static final DeferredItem<InkPenItem> INK_PEN = ITEMS.registerItem("ink_pen", InkPenItem::new);

    /** A rod of hardened woodcruor, used like chalk: needs no ink, but its lines are weak and wash off. */
    public static final DeferredItem<com.witchcraft.item.WoodcruorWandItem> WOODCRUOR_WAND = ITEMS.registerItem("woodcruor_wand",
            com.witchcraft.item.WoodcruorWandItem::new, new Item.Properties().durability(512));
    /** A pen with a little fire spell in it: the ink is seared into the surface, safe from water and flame. */
    public static final DeferredItem<com.witchcraft.item.SearneedleItem> SEARNEEDLE = ITEMS.registerItem("searneedle",
            com.witchcraft.item.SearneedleItem::new, new Item.Properties().durability(1024).rarity(Rarity.UNCOMMON).fireResistant());

    public static final DeferredItem<com.witchcraft.item.RagItem> RAG = ITEMS.registerItem("rag", com.witchcraft.item.RagItem::new);

    /** Palm quire: a small round notebook strapped to the hand, its pages ready for glyphs drawn in advance. */
    public static final DeferredItem<com.witchcraft.item.PalmQuireItem> PALM_QUIRE = ITEMS.registerItem("palm_quire", com.witchcraft.item.PalmQuireItem::new);

    public static final DeferredItem<com.witchcraft.item.InkwellItem> INKWELL = ITEMS.registerItem("inkwell",
            p -> new com.witchcraft.item.InkwellItem(ModBlocks.INKWELL.get(), p));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SILVERWOOD_LOG = ITEMS.registerSimpleBlockItem(ModBlocks.SILVERWOOD_LOG);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SILVERWOOD_LEAVES = ITEMS.registerSimpleBlockItem(ModBlocks.SILVERWOOD_LEAVES);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SILVERWOOD_SAPLING = ITEMS.registerSimpleBlockItem(ModBlocks.SILVERWOOD_SAPLING);
    /** A dead silverwood branch, turned pitch black. Brewed with water it gives woodcruor. */
    public static final DeferredItem<Item> BLACKENED_TWIG = ITEMS.registerSimpleItem("blackened_silverwood_twig");
    /** Woodcruor: the only substance that makes a drawn seal come alive. Brew it with glowstone into conjuring ink. */
    public static final DeferredItem<Item> WOODCRUOR = ITEMS.registerSimpleItem("woodcruor", new Item.Properties().stacksTo(16));

    // ---------------- witch's attire
    public static final DeferredItem<com.witchcraft.item.attire.WitchAttireItem> APPRENTICE_HAT = attire("apprentice_hat", com.witchcraft.item.attire.WitchAttireItem.Style.APPRENTICE_HAT);
    public static final DeferredItem<com.witchcraft.item.attire.WitchAttireItem> WITCH_HAT = attire("witch_hat", com.witchcraft.item.attire.WitchAttireItem.Style.WITCH_HAT);
    public static final DeferredItem<com.witchcraft.item.attire.WitchAttireItem> WITCH_ROBE = attire("witch_robe", com.witchcraft.item.attire.WitchAttireItem.Style.ROBE);
    public static final DeferredItem<com.witchcraft.item.attire.WitchAttireItem> WITCH_MANTLE = attire("witch_mantle", com.witchcraft.item.attire.WitchAttireItem.Style.MANTLE);
    public static final DeferredItem<com.witchcraft.item.attire.WitchAttireItem> WITCH_STOCKINGS = attire("witch_stockings", com.witchcraft.item.attire.WitchAttireItem.Style.STOCKINGS);
    public static final DeferredItem<com.witchcraft.item.attire.WitchAttireItem> WITCH_BOOTS = attire("witch_boots", com.witchcraft.item.attire.WitchAttireItem.Style.BOOTS);

    private static DeferredItem<com.witchcraft.item.attire.WitchAttireItem> attire(String name, com.witchcraft.item.attire.WitchAttireItem.Style style) {
        return ITEMS.registerItem(name, p -> new com.witchcraft.item.attire.WitchAttireItem(style, p));
    }

    /** An old grimoire of glyphs: found in ruins, or given by a stranger. Read it, copy what it shows. */
    public static final DeferredItem<com.witchcraft.item.GrimoireItem> GRIMOIRE = ITEMS.registerItem("grimoire", com.witchcraft.item.GrimoireItem::new,
            new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> MENTOR_SPAWN_EGG = ITEMS.registerItem("mentor_spawn_egg",
            p -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.MENTOR, 0x2E5A5C, 0xE8DDB8, p));
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> STRANGER_SPAWN_EGG = ITEMS.registerItem("stranger_spawn_egg",
            p -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.STRANGER, 0x1C1626, 0xC9A65A, p));

    // ---------------- contraptions
    public static final DeferredItem<com.witchcraft.item.artifact.SnugstoneItem> SNUGSTONE = ITEMS.registerItem("snugstone",
            com.witchcraft.item.artifact.SnugstoneItem::new);
    public static final DeferredItem<com.witchcraft.item.artifact.VaporBubbleItem> VAPOR_BUBBLE = ITEMS.registerItem("vapor_bubble",
            com.witchcraft.item.artifact.VaporBubbleItem::new, new Item.Properties().durability(128));
    public static final DeferredItem<com.witchcraft.item.artifact.LightTracerBangleItem> LIGHT_TRACER_BANGLE = ITEMS.registerItem("light_tracer_bangle",
            com.witchcraft.item.artifact.LightTracerBangleItem::new, new Item.Properties().stacksTo(8).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.witchcraft.item.artifact.RaincleaverItem> RAINCLEAVER = ITEMS.registerItem("raincleaver",
            p -> new com.witchcraft.item.artifact.RaincleaverItem(net.minecraft.world.item.Tiers.IRON,
                    p.attributes(net.minecraft.world.item.SwordItem.createAttributes(net.minecraft.world.item.Tiers.IRON, 3, -2.4f)).rarity(Rarity.RARE)));
    public static final DeferredItem<com.witchcraft.item.attire.WitchAttireItem> WINGCLOAK = attire("wingcloak", com.witchcraft.item.attire.WitchAttireItem.Style.WINGCLOAK);
    public static final DeferredItem<com.witchcraft.item.artifact.CapturePennantItem> CAPTURE_PENNANT = ITEMS.registerItem("capture_pennant",
            com.witchcraft.item.artifact.CapturePennantItem::new, new Item.Properties().durability(64).rarity(Rarity.RARE));
    public static final DeferredItem<com.witchcraft.item.artifact.TentStaffItem> TENT_STAFF = ITEMS.registerItem("tent_staff",
            com.witchcraft.item.artifact.TentStaffItem::new, new Item.Properties().durability(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.witchcraft.item.attire.WitchAttireItem> BRIMHAT_MANTLE = attire("brimhat_mantle", com.witchcraft.item.attire.WitchAttireItem.Style.BRIMHAT_MANTLE);
    public static final DeferredItem<com.witchcraft.item.attire.WitchAttireItem> MIRROR_MANTLE = attire("mirror_mantle", com.witchcraft.item.attire.WitchAttireItem.Style.MIRROR_MANTLE);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BEAST_WARDING_TOWER = ITEMS.registerSimpleBlockItem(ModBlocks.WARDING_TOWER);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> WASHING_BARREL = ITEMS.registerSimpleBlockItem(ModBlocks.WASHING_BARREL);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ARCANE_LENS = ITEMS.registerSimpleBlockItem(ModBlocks.ARCANE_LENS);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> VOID_BASIN = ITEMS.registerSimpleBlockItem(ModBlocks.VOID_BASIN);
    public static final DeferredItem<com.witchcraft.item.artifact.SealchairItem> SEALCHAIR = ITEMS.registerItem("sealchair",
            props -> new com.witchcraft.item.artifact.SealchairItem(ModBlocks.SEALCHAIR.get(), props), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> GLOWSTONE_PATH = ITEMS.registerSimpleBlockItem(ModBlocks.GLOWSTONE_PATH);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> DRAGON_TEACUP = ITEMS.registerSimpleBlockItem(ModBlocks.DRAGON_TEACUP);

    /** A Brimhat's mask: Brimhats take you for one of their own; the Knights Moralis take you for a Brimhat. */
    public static final DeferredItem<com.witchcraft.item.artifact.BrimhatMaskItem> BRIMHAT_MASK = ITEMS.registerItem("brimhat_mask",
            p -> new com.witchcraft.item.artifact.BrimhatMaskItem(p.durability(net.minecraft.world.item.ArmorItem.Type.HELMET.getDurability(12)).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> BRIMHAT_SPAWN_EGG = ITEMS.registerItem("brimhat_spawn_egg",
            p -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.BRIMHAT, 0x16101A, 0xB02030, p));
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> BRIMHAT_ELDER_SPAWN_EGG = ITEMS.registerItem("brimhat_elder_spawn_egg",
            p -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.BRIMHAT_ELDER, 0x0E0A10, 0xD4A83A, p));
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> KNIGHT_MORALIS_SPAWN_EGG = ITEMS.registerItem("knight_moralis_spawn_egg",
            p -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.KNIGHT_MORALIS, 0xE8ECF2, 0x6A7A9A, p));

    public static final DeferredItem<com.witchcraft.item.TornPageItem> TORN_PAGE = ITEMS.registerItem("torn_page", com.witchcraft.item.TornPageItem::new);

    public static final DeferredItem<InkBottleItem> CONJURING_INK = ink(InkType.CONJURING, Rarity.COMMON);
    public static final DeferredItem<InkBottleItem> WATERPROOF_INK = ink(InkType.WATERPROOF, Rarity.UNCOMMON);
    public static final DeferredItem<InkBottleItem> INVISIBLE_INK = ink(InkType.INVISIBLE, Rarity.UNCOMMON);
    public static final DeferredItem<InkBottleItem> LASTING_INK = ink(InkType.LASTING, Rarity.UNCOMMON);
    public static final DeferredItem<InkBottleItem> BLOOD_INK = ink(InkType.BLOOD, Rarity.RARE);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("witchcraft", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.witchcraft"))
            .icon(() -> new ItemStack(INK_PEN.get()))
            .displayItems((params, out) -> {
                out.accept(INK_PEN.get());
                out.accept(WOODCRUOR_WAND.get());
                out.accept(SEARNEEDLE.get());
                out.accept(PALM_QUIRE.get());
                out.accept(com.witchcraft.glyph.page.GlyphLibrary.grimoireBook(net.minecraft.util.RandomSource.create(7), 7,
                        "pillar_of_flame", "wind_underfoot", "birds_of_light", "spilling_water", "stone_breaker", "windowway", "bed_of_sand"));
                out.accept(com.witchcraft.glyph.page.GlyphLibrary.grimoireBook(net.minecraft.util.RandomSource.create(8), 7,
                        "beacon_of_birds", "fire_bolts", "gathering_wind", "rain_of_light", "lantern", "water_jet", "tidal_ring"));
                out.accept(RAG.get());
                out.accept(com.witchcraft.glyph.page.GlyphLibrary.contraptionsBook());
                out.accept(STRANGER_SPAWN_EGG.get());
                out.accept(MENTOR_SPAWN_EGG.get());
                out.accept(BRIMHAT_SPAWN_EGG.get());
                out.accept(BRIMHAT_ELDER_SPAWN_EGG.get());
                out.accept(KNIGHT_MORALIS_SPAWN_EGG.get());
                out.accept(BRIMHAT_MASK.get());
                out.accept(BRIMHAT_MANTLE.get());
                out.accept(SNUGSTONE.get());
                out.accept(VAPOR_BUBBLE.get());
                out.accept(LIGHT_TRACER_BANGLE.get());
                out.accept(RAINCLEAVER.get());
                out.accept(MIRROR_MANTLE.get());
                out.accept(WINGCLOAK.get());
                out.accept(CAPTURE_PENNANT.get());
                out.accept(TENT_STAFF.get());
                out.accept(BEAST_WARDING_TOWER.get());
                out.accept(WASHING_BARREL.get());
                out.accept(ARCANE_LENS.get());
                out.accept(VOID_BASIN.get());
                out.accept(GLOWSTONE_PATH.get());
                out.accept(SEALCHAIR.get());
                out.accept(DRAGON_TEACUP.get());
                out.accept(INKWELL.get());
                out.accept(APPRENTICE_HAT.get());
                out.accept(WITCH_HAT.get());
                out.accept(WITCH_ROBE.get());
                out.accept(WITCH_MANTLE.get());
                out.accept(WITCH_STOCKINGS.get());
                out.accept(WITCH_BOOTS.get());
                out.accept(SILVERWOOD_SAPLING.get());
                out.accept(SILVERWOOD_LOG.get());
                out.accept(SILVERWOOD_LEAVES.get());
                out.accept(BLACKENED_TWIG.get());
                out.accept(WOODCRUOR.get());
                out.accept(CONJURING_INK.get());
                out.accept(WATERPROOF_INK.get());
                out.accept(INVISIBLE_INK.get());
                out.accept(LASTING_INK.get());
                out.accept(BLOOD_INK.get());
            })
            .build());

    private static DeferredItem<InkBottleItem> ink(InkType type, Rarity rarity) {
        return ITEMS.registerItem(type.id + "_ink", p -> new InkBottleItem(type, p.rarity(rarity)));
    }

    public static InkBottleItem bottleFor(InkType type) {
        return switch (type) {
            case CONJURING -> CONJURING_INK.get();
            case WATERPROOF -> WATERPROOF_INK.get();
            case INVISIBLE -> INVISIBLE_INK.get();
            case LASTING -> LASTING_INK.get();
            case BLOOD -> BLOOD_INK.get();
            default -> CONJURING_INK.get();
        };
    }

    private ModItems() {}
}
