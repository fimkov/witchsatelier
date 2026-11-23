package com.fimkov.witchsatelier.item.artifact;

import com.fimkov.witchsatelier.entity.BrimhatEntity;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import com.fimkov.witchsatelier.registry.ModBlocks;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.registry.ModItems;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A glass orb that holds a fragment of something and, held up, spins out a thread of light towards where the fragment came
 * from: the nearest block of its kind, the ore of a raw metal or gem, the creature that drops it, or the keeper of a grimoire.
 */
public class GuidanceOrbItem extends Item {
    public static final int BLOCK_RANGE = 24, ENTITY_RANGE = 48, RESEEK_TICKS = 40;

    /** The fragment sealed in the orb. Wrapped so two orbs holding equal fragments compare equal. */
    public record Fragment(ItemStack stack) {
        public static final Codec<Fragment> CODEC = ItemStack.CODEC.xmap(Fragment::new, Fragment::stack);
        public static final StreamCodec<RegistryFriendlyByteBuf, Fragment> STREAM_CODEC = ItemStack.STREAM_CODEC.map(Fragment::new, Fragment::stack);

        public Fragment {
            stack = stack.copy();
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Fragment f && ItemStack.matches(stack, f.stack);
        }

        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(stack) * 31 + stack.getCount();
        }
    }

    private static final Map<Item, EntityType<?>> CREATURES = new HashMap<>();
    private static final Map<Item, Predicate<BlockState>> ORES = new HashMap<>();

    static {
        creature(EntityType.CHICKEN, Items.FEATHER, Items.EGG, Items.CHICKEN);
        creature(EntityType.COW, Items.LEATHER, Items.BEEF);
        creature(EntityType.PIG, Items.PORKCHOP);
        creature(EntityType.SHEEP, Items.MUTTON);
        creature(EntityType.RABBIT, Items.RABBIT_HIDE, Items.RABBIT_FOOT, Items.RABBIT);
        creature(EntityType.SPIDER, Items.STRING, Items.SPIDER_EYE);
        creature(EntityType.SKELETON, Items.BONE);
        creature(EntityType.ZOMBIE, Items.ROTTEN_FLESH);
        creature(EntityType.CREEPER, Items.GUNPOWDER);
        creature(EntityType.ENDERMAN, Items.ENDER_PEARL);
        creature(EntityType.SLIME, Items.SLIME_BALL);
        creature(EntityType.BLAZE, Items.BLAZE_ROD);
        creature(EntityType.BREEZE, Items.BREEZE_ROD);
        creature(EntityType.GHAST, Items.GHAST_TEAR);
        creature(EntityType.MAGMA_CUBE, Items.MAGMA_CREAM);
        creature(EntityType.SQUID, Items.INK_SAC);
        creature(EntityType.GLOW_SQUID, Items.GLOW_INK_SAC);
        creature(EntityType.PHANTOM, Items.PHANTOM_MEMBRANE);
        creature(EntityType.SHULKER, Items.SHULKER_SHELL);
        creature(EntityType.GUARDIAN, Items.PRISMARINE_SHARD, Items.PRISMARINE_CRYSTALS);
        creature(EntityType.TURTLE, Items.TURTLE_SCUTE);
        creature(EntityType.ARMADILLO, Items.ARMADILLO_SCUTE);
        creature(EntityType.COD, Items.COD);
        creature(EntityType.SALMON, Items.SALMON);
        creature(EntityType.PUFFERFISH, Items.PUFFERFISH);
        creature(EntityType.TROPICAL_FISH, Items.TROPICAL_FISH);

        ore(Tags.Blocks.ORES_IRON, Items.RAW_IRON);
        ore(Tags.Blocks.ORES_GOLD, Items.RAW_GOLD, Items.GOLD_NUGGET);
        ore(Tags.Blocks.ORES_COPPER, Items.RAW_COPPER);
        ore(Tags.Blocks.ORES_COAL, Items.COAL);
        ore(Tags.Blocks.ORES_DIAMOND, Items.DIAMOND);
        ore(Tags.Blocks.ORES_EMERALD, Items.EMERALD);
        ore(Tags.Blocks.ORES_LAPIS, Items.LAPIS_LAZULI);
        ore(Tags.Blocks.ORES_REDSTONE, Items.REDSTONE);
        ore(Tags.Blocks.ORES_QUARTZ, Items.QUARTZ);
        ORES.put(Items.AMETHYST_SHARD, s -> s.is(Blocks.AMETHYST_CLUSTER) || s.is(Blocks.BUDDING_AMETHYST));
        ORES.put(Items.FLINT, s -> s.is(Blocks.GRAVEL));
        ORES.put(Items.CLAY_BALL, s -> s.is(Blocks.CLAY));
        ORES.put(Items.GLOWSTONE_DUST, s -> s.is(Blocks.GLOWSTONE));
    }

    private static void creature(EntityType<?> type, Item... drops) {
        for (Item i : drops) CREATURES.put(i, type);
    }

    private static void ore(TagKey<Block> tag, Item... drops) {
        for (Item i : drops) ORES.put(i, s -> s.is(tag));
    }

    private record Lock(long until, Item item, BlockPos block, Entity entity) {}

    private static final Map<UUID, Lock> LOCKS = new HashMap<>();

    public GuidanceOrbItem(Properties props) {
        super(props);
    }

    public static void forget() {
        LOCKS.clear();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack orb = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack other = player.getItemInHand(otherHand);
        Fragment held = orb.get(ModComponents.FRAGMENT.get());
        if (player.isSecondaryUseActive()) {
            if (held != null) {
                if (!level.isClientSide) {
                    orb.remove(ModComponents.FRAGMENT.get());
                    ItemStack out = held.stack();
                    if (other.isEmpty()) player.setItemInHand(otherHand, out);
                    else if (!player.getInventory().add(out)) player.drop(out, false);
                    LOCKS.remove(player.getUUID());
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8f, 1.4f);
                }
                return InteractionResultHolder.sidedSuccess(orb, level.isClientSide);
            }
            if (other.isEmpty() || other.getItem() instanceof GuidanceOrbItem) return InteractionResultHolder.pass(orb);
            if (!level.isClientSide) {
                orb.set(ModComponents.FRAGMENT.get(), new Fragment(other.split(1)));
                LOCKS.remove(player.getUUID());
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8f, 1.6f);
            }
            return InteractionResultHolder.sidedSuccess(orb, level.isClientSide);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(orb);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel sl) || remaining % 5 != 0) return;
        Vec3 from = user.getEyePosition().add(user.getLookAngle().scale(0.6)).subtract(0, 0.35, 0);
        Fragment f = stack.get(ModComponents.FRAGMENT.get());
        Vec3 to = f == null ? null : target(sl, user, f.stack());
        if (to == null || to.distanceToSqr(from) < 1) {
            double a = level.getGameTime() * 0.6;
            for (int i = 0; i < 3; i++) {
                double b = a + i * Math.PI * 2 / 3;
                sl.sendParticles(new DustParticleOptions(new Vector3f(0.85f, 0.75f, 1f), 0.4f),
                        from.x + Math.cos(b) * 0.18, from.y, from.z + Math.sin(b) * 0.18, 1, 0, 0, 0, 0);
            }
            return;
        }
        thread(sl, from, to);
        if ((getUseDuration(stack, user) - remaining) % 20 == 0) {
            level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.4f, 2f);
            boolean last = stack.getDamageValue() + 1 >= stack.getMaxDamage() && !(user instanceof Player p && p.hasInfiniteMaterials());
            if (last && user instanceof Player holder) {
                ItemStack out = f.stack();
                if (!holder.getInventory().add(out)) holder.drop(out, false);
            }
            stack.hurtAndBreak(1, user, user.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
    }

    public static void showThread(ServerLevel sl, Vec3 from, Vec3 to) {
        thread(sl, from, to);
    }

    private static void thread(ServerLevel sl, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        double reach = Math.min(dir.length(), 16);
        dir = dir.normalize();
        double t0 = sl.getGameTime() * 0.25;
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        for (double d = 0.25; d < reach; d += 0.3) {
            Vec3 at = from.add(dir.scale(d)).add(side.scale(Math.sin(d * 1.1 + t0) * 0.05));
            float fade = (float) (1 - d / 18);
            sl.sendParticles(new DustParticleOptions(new Vector3f(0.8f + 0.2f * fade, 0.55f + 0.35f * fade, 1f), 0.6f + 0.4f * fade),
                    at.x, at.y, at.z, 1, 0, 0, 0, 0);
            if (((int) (d / 0.3)) % 3 == 0) sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    /** Where the thread points for this user and fragment, looked up anew every couple of seconds. Null if nothing is in range. */
    public static Vec3 target(ServerLevel level, LivingEntity user, ItemStack fragment) {
        long now = level.getGameTime();
        Lock l = LOCKS.get(user.getUUID());
        if (l == null || l.item() != fragment.getItem() || now >= l.until()) {
            Entity e = seekCreature(level, user.position(), fragment, user);
            BlockPos b = e == null ? seekBlock(level, user.blockPosition(), fragment) : null;
            l = new Lock(now + RESEEK_TICKS, fragment.getItem(), b, e);
            LOCKS.put(user.getUUID(), l);
        }
        if (l.entity() != null) {
            Entity e = l.entity();
            if (!e.isAlive() || e.level() != level || e.distanceToSqr(user) > ENTITY_RANGE * ENTITY_RANGE) return null;
            return e.position().add(0, e.getBbHeight() * 0.6, 0);
        }
        return l.block() == null ? null : Vec3.atCenterOf(l.block());
    }

    /** The creature or person a fragment belongs to: a grimoire's keeper, Brimhats for a forbidden page, or a creature's drop. */
    public static Entity seekCreature(ServerLevel level, Vec3 from, ItemStack fragment, Entity except) {
        String keeper = fragment.get(ModComponents.KEEPER.get());
        if (keeper != null) {
            Player p = level.getServer().getPlayerList().getPlayerByName(keeper);
            return p != null && p != except && p.level() == level && !p.isSpectator() && p.distanceToSqr(from) <= ENTITY_RANGE * ENTITY_RANGE ? p : null;
        }
        Predicate<Entity> want;
        if (forbiddenPage(fragment)) want = e -> e instanceof BrimhatEntity;
        else if (fragment.getItem() instanceof SpawnEggItem egg) {
            EntityType<?> type = egg.getType(fragment);
            want = e -> e.getType() == type;
        } else {
            EntityType<?> type = CREATURES.get(fragment.getItem());
            if (type == null) return null;
            want = e -> e.getType() == type;
        }
        Entity best = null;
        double bestD = ENTITY_RANGE * ENTITY_RANGE;
        for (Entity e : level.getEntities((Entity) null, AABB.ofSize(from, ENTITY_RANGE * 2, ENTITY_RANGE * 2, ENTITY_RANGE * 2),
                e -> e != except && e.isAlive() && want.test(e))) {
            double d = e.distanceToSqr(from);
            if (d <= bestD) { bestD = d; best = e; }
        }
        return best;
    }

    private static boolean forbiddenPage(ItemStack s) {
        if (!s.is(ModItems.TORN_PAGE.get())) return false;
        List<String> names = s.get(ModComponents.GRIMOIRE_ENTRIES.get());
        if (names == null) return false;
        for (var r : GlyphLibrary.FORBIDDEN) if (names.contains(r.name())) return true;
        return false;
    }

    private static Predicate<BlockState> blockRule(ItemStack fragment) {
        Item item = fragment.getItem();
        if (item == ModItems.BLACKENED_TWIG.get()) return s -> s.is(ModBlocks.SILVERWOOD_LOG.get());
        Predicate<BlockState> ore = ORES.get(item);
        if (ore != null) return ore;
        if (item instanceof BlockItem bi && bi.getBlock() != Blocks.AIR) {
            Block block = bi.getBlock();
            return s -> s.is(block);
        }
        return null;
    }

    /** The nearest block a fragment came from, within {@link #BLOCK_RANGE}; skips chunk sections whose palette cannot hold it. */
    public static BlockPos seekBlock(ServerLevel level, BlockPos c, ItemStack fragment) {
        Predicate<BlockState> want = blockRule(fragment);
        if (want == null) return null;
        int r = BLOCK_RANGE;
        BlockPos best = null;
        double bestD = (double) r * r;
        int minY = Math.max(level.getMinBuildHeight(), c.getY() - r), maxY = Math.min(level.getMaxBuildHeight() - 1, c.getY() + r);
        for (int cx = SectionPos.blockToSectionCoord(c.getX() - r); cx <= SectionPos.blockToSectionCoord(c.getX() + r); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(c.getZ() - r); cz <= SectionPos.blockToSectionCoord(c.getZ() + r); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
                    LevelChunkSection sec = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (sec.hasOnlyAir() || !sec.maybeHas(want)) continue;
                    int bx = SectionPos.sectionToBlockCoord(cx), by = SectionPos.sectionToBlockCoord(sy), bz = SectionPos.sectionToBlockCoord(cz);
                    for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        double dx = bx + x - c.getX(), dy = by + y - c.getY(), dz = bz + z - c.getZ();
                        double d = dx * dx + dy * dy + dz * dz;
                        if (d >= bestD || !want.test(sec.getBlockState(x, y, z))) continue;
                        bestD = d;
                        best = new BlockPos(bx + x, by + y, bz + z);
                    }
                }
            }
        }
        return best;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModComponents.FRAGMENT.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        if (Awakening.dormant(stack)) return;
        lines.add(Component.translatable("item.witchsatelier.guidance_orb.desc").withStyle(ChatFormatting.GRAY));
        Fragment f = stack.get(ModComponents.FRAGMENT.get());
        if (f != null) lines.add(Component.translatable("item.witchsatelier.guidance_orb.holds", f.stack().getHoverName())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
