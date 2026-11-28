package com.fimkov.witchsatelier.spell;

import com.fimkov.witchsatelier.advancement.MagicTrigger;
import com.fimkov.witchsatelier.block.PetrifiedBlock;
import com.fimkov.witchsatelier.block.StatueBlock;
import com.fimkov.witchsatelier.entity.BrimhatEntity;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.item.InkPenItem;
import com.fimkov.witchsatelier.registry.ModBlocks;
import com.fimkov.witchsatelier.registry.ModSounds;
import com.fimkov.witchsatelier.spell.SpellProgram.Form;
import com.fimkov.witchsatelier.util.TimedBlocks;
import com.fimkov.witchsatelier.windowway.WindowwayService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class ActiveSpell {
    public final long id;

    public final SurfacePlane plane;

    public GlyphFrame frame;

    public final boolean handheld;

    public boolean worn;

    public double toughness = 1;

    public boolean mending;
    private Vec3 wornLocal;

    public static GlyphFrame wornFrame(Vec3 right, Vec3 fwd) {
        return new GlyphFrame() {
            @Override public Vec3 normal() { return new Vec3(0, 1, 0); }
            @Override public Vec3 planeVector(double du, double dv) { return right.scale(du).add(fwd.scale(dv)); }
        };
    }
    private final List<Bird> birds = new ArrayList<>();
    private int birdSerial;

    public static final class Bird {
        public final int index;
        public Vec3 pos, vel, home;
        public int age;
        public final int life;
        Bird(int index, Vec3 pos, Vec3 vel, int life) { this.index = index; this.pos = pos; this.vel = vel; this.life = life; }
    }

    public List<Bird> birds() { return birds; }

    public float[] layerFx = new float[0];

    private void cushion(ServerLevel level) {
        Vec3 o = program.origin();
        double r = Math.max(3, program.radius() * 3);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(o, o).inflate(r, r * 2, r))) {
            e.resetFallDistance();
            if (e.getDeltaMovement().y < -0.25) {
                e.setDeltaMovement(e.getDeltaMovement().multiply(1, 0.6, 1));
                e.hurtMarked = true;
                if (age % 3 == 0) level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY(), e.getZ(), 2, 0.3, 0.1, 0.3, 0.01);
            }
        }
        if (age % 6 == 0) level.sendParticles(ParticleTypes.CLOUD, o.x, o.y + 0.3, o.z, 3, r * 0.4, 0.2, r * 0.4, 0.005);
    }
    public final Set<Long> strokes;
    public final UUID caster;
    public SpellProgram program;
    public int ticksLeft;
    private int age;
    private final List<TempBlock> temps = new ArrayList<>();
    private final Set<BlockPos> tempAt = new HashSet<>();

    private static final int MAX_TEMPS = 256;

    public float fxLength, fxWidth;

    public int age() { return age; }

    private record TempBlock(BlockPos pos, BlockState placed) {}

    public ActiveSpell(long id, SurfacePlane plane, Set<Long> strokes, UUID caster, SpellProgram program) {
        this(id, plane, plane, false, strokes, caster, program);
    }

    public static ActiveSpell handheld(long id, UUID caster, SpellProgram program) {
        return new ActiveSpell(id, null, GlyphFrame.facing(program.normal()), true, Set.of(), caster, program);
    }

    private ActiveSpell(long id, SurfacePlane plane, GlyphFrame frame, boolean handheld, Set<Long> strokes, UUID caster, SpellProgram program) {
        this.id = id;
        this.plane = plane;
        this.frame = frame;
        this.handheld = handheld;
        this.strokes = new HashSet<>(strokes);
        this.caster = caster;
        this.program = program;
        this.ticksLeft = program.duration();
    }

    public long chain;
    public int hop;
    public boolean held;
    public long releaseAt;

    public long expiresAt;
    public boolean quiet;
    public double ringRadius;

    /** The name drawn around a Window in signs (see {@link com.fimkov.witchsatelier.windowway.WindowMark}); empty for a plain Window. */
    public String windowMark = "";

    /** Set for glyphs cast from a page laid on a surface rather than drawn there: such a window only ever visits. */
    public boolean fromPage;

    /** Set for glyphs cast from a page drawn mostly in blood ink; glyphs on a surface are judged by their own strokes. */
    public boolean bloodInk;

    /** Whether this glyph was drawn mostly in blood ink, the only ink that can undo forbidden magic. */
    public boolean drawnInBlood(ServerLevel level) {
        if (bloodInk) return true;
        var data = com.fimkov.witchsatelier.glyph.storage.GlyphLevelData.get(level);
        double all = 0, blood = 0;
        for (long id : strokes) {
            var s = data.get(id);
            if (s == null) continue;
            double l = Math.max(0.05, s.length());
            all += l;
            if (s.ink() == com.fimkov.witchsatelier.glyph.ink.InkType.BLOOD) blood += l;
        }
        return all > 0 && blood * 2 > all;
    }

    private boolean stopped;

    public boolean fxOff;
    private int unpressed, pressTick;
    private boolean lastPressed;

    public boolean waiting() {
        return held || (program.has(SpellProgram.TREAD) && !handheld && !sprung);
    }

    public boolean tick(ServerLevel level) {
        if (stopped || (expiresAt > 0 && level.getGameTime() >= expiresAt)) return false;
        if (handheld && !followCaster(level)) return false;
        if (held) {
            if (releaseAt <= 0 || level.getGameTime() < releaseAt) return true;
            held = false;
            releaseAt = 0;
            GlyphService.released(level, this);
        }
        if (program.has(SpellProgram.TREAD) && !handheld) {
            boolean pressed = pressed(level);
            if (!sprung) {
                if (!pressed) { armed++; return true; }
                sprung = true;
                unpressed = 0;
                GlyphService.sprung(level, this);
            } else if (pressed) {
                unpressed = 0;
            } else if (++unpressed > 40) {
                rearm(level);
                return true;
            }
        }
        if (age == 0) start(level);
        age++;
        if (program.has(SpellProgram.AIM) && !handheld) aim(level);
        run(level);
        if (program.mods() != 0) mods(level);

        SpellProgram main = program;
        float mainLength = fxLength, mainWidth = fxWidth;
        List<SpellProgram> layers = main.layers();
        if (layerFx.length != layers.size() * 2) layerFx = new float[layers.size() * 2];
        for (int k = 0; k < layers.size(); k++) {
            if (age > layers.get(k).duration() && layers.get(k).form() == Form.BOLTS) continue;
            program = layers.get(k);
            try {
                run(level);
            } finally {
                program = main;
            }
            layerFx[k * 2] = fxLength;
            layerFx[k * 2 + 1] = fxWidth;
        }
        fxLength = mainLength;
        fxWidth = mainWidth;
        if (main.cushion()) cushion(level);
        boolean fire = main.element() == Sigil.FIRE, water = main.element() == Sigil.WATER;
        for (SpellProgram l : layers) { fire |= l.element() == Sigil.FIRE; water |= l.element() == Sigil.WATER; }
        if (fire && water) steam(level);
        if (program.birds() > 0) birds(level);
        if (--ticksLeft > 0) return true;

        if (program.has(SpellProgram.TREAD) && !handheld) {
            rearm(level);
            return true;
        }
        return false;
    }

    public UUID key;

    private boolean pressed(ServerLevel level) {
        if ((pressTick++ & 3) != 0) return lastPressed;
        boolean on = steppedOn(level) || powered(level);
        lastPressed = program.has(SpellProgram.TREAD_INVERTED) != on;
        return lastPressed;
    }

    public UUID standingInside(ServerLevel level) {
        Vec3 o = program.origin(), n = program.normal();
        double r = Math.max(0.8, program.radius());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(o, o.add(n.scale(1.5))).inflate(r), LivingEntity::isAlive)) {
            if (e.isSpectator()) continue;
            Vec3 rel = e.position().subtract(o);
            double along = rel.dot(n);
            if (along < -0.6 || along > 1.8) continue;
            if (rel.subtract(n.scale(along)).length() <= r) return e.getUUID();
        }
        return null;
    }

    private boolean powered(ServerLevel level) {
        if (plane == null) return false;
        BlockPos on = BlockPos.containing(program.origin().subtract(program.normal().scale(0.5)));
        return level.hasNeighborSignal(on) || level.hasNeighborSignal(on.relative(plane.face()));
    }

    public void rearm(ServerLevel level) {
        sprung = false;
        unpressed = 0;
        lastPressed = false;
        pause(level);
        GlyphService.rearmed(level, this);
    }

    public void pause(ServerLevel level) {
        WindowwayService.close(level, id, WindowwayService.Reason.PAUSED);
        for (TempBlock t : temps) {
            if (level.getBlockState(t.pos()) == t.placed()) level.setBlock(t.pos(), Blocks.AIR.defaultBlockState(), 3);
        }
        temps.clear();
        tempAt.clear();
        age = 0;
        ticksLeft = program.duration();
    }

    private boolean sprung;
    private int armed;

    private boolean steppedOn(ServerLevel level) {
        Vec3 o = program.origin(), n = program.normal();
        double r = Math.max(0.8, program.radius());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(o, o.add(n.scale(1.5))).inflate(r), LivingEntity::isAlive)) {
            if (e.isSpectator()) continue;
            if (key != null ? !e.getUUID().equals(key) : e.getUUID().equals(caster)) continue;
            Vec3 rel = e.position().subtract(o);
            double along = rel.dot(n);
            if (along < -0.6 || along > 1.8) continue;
            if (rel.subtract(n.scale(along)).length() <= r) return true;
        }
        return false;
    }

    private void start(ServerLevel level) {
        Vec3 o = program.origin();
        level.playSound(null, o.x, o.y, o.z, ModSounds.RING_IGNITE.get(), SoundSource.PLAYERS, 1.0f, 0.9f + level.random.nextFloat() * 0.2f);
        if (!handheld) level.playSound(null, o.x, o.y, o.z, ModSounds.GLYPH_HUM.get(), SoundSource.PLAYERS, 0.6f, 0.9f + level.random.nextFloat() * 0.2f);
        var voice = ModSounds.element(program.element());
        if (voice != null) level.playSound(null, o.x, o.y, o.z, voice, SoundSource.PLAYERS, 0.8f, 0.9f + level.random.nextFloat() * 0.2f);
        burstParticles(level, o, ElementLook.particle(program.element()), 30, program.radius() * 0.6);

        for (int k = 0; k < 18; k++) {
            double a = k * Math.PI * 2 / 18;
            Vec3 at = o.add(frame.planeVector(Math.cos(a), Math.sin(a)).scale(program.radius()));
            Vec3 v = program.normal().scale(0.04);
            level.sendParticles(ElementLook.rune(program.element(), program.centerSign()), at.x, at.y, at.z, 0, v.x, v.y, v.z, 1);
        }

        BlockPos front = BlockPos.containing(o.add(program.normal().scale(0.3)));
        if (program.element() == Sigil.FIRE && level.getFluidState(front).is(FluidTags.WATER)) {
            level.playSound(null, o.x, o.y, o.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 1.2f);
            level.sendParticles(ParticleTypes.CLOUD, o.x, o.y, o.z, 25, 0.4, 0.4, 0.4, 0.05);
            ticksLeft = 1;
            return;
        }
        if (!quiet && level.random.nextDouble() < program.misfire()) {
            misfire(level);
            ticksLeft = 1;
            return;
        }
        if (program.form() == Form.BURST) {
            float strength = (float) Math.min(1 + program.radius() * 1.5, 5);
            level.explode(null, o.x, o.y, o.z, strength, Level.ExplosionInteraction.NONE);
            ticksLeft = 1;
        } else if (program.form() == Form.CRUSH) {
            Earthworks.crush(level, this);
            ticksLeft = 1;
        } else if (program.form() == Form.RESTORE) {
            Earthworks.restore(level, program.origin(), program.radius() * 2 + 4 + program.power() * 0.1);

            PetrifiedBlock.restore(level, program.origin(), program.radius() * 3 + 10);
            if (!drawnInBlood(level)) StatueBlock.stir(level, program.origin(), program.radius() * 3 + 10);
            else if (StatueBlock.revive(level, program.origin(), program.radius() * 3 + 10) > 0 && caster != null) {
                Player who = level.getPlayerByUUID(caster);
                if (who != null) MagicTrigger.fire(who, "revive");
            }
            ticksLeft = 1;
        } else if (program.form() == Form.FIZZLE) {
            burstParticles(level, o, ParticleTypes.SMOKE, 20, program.radius() * 0.5);
            ticksLeft = 1;
        }
    }

    private void misfire(ServerLevel level) {
        Vec3 o = program.origin();
        level.playSound(null, o.x, o.y, o.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1f, 0.6f);
        burstParticles(level, o, ElementLook.trail(program.element()), 60, program.radius());
        double r = 1 + program.radius();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(o, o).inflate(r))) {
            e.hurt(level.damageSources().magic(), (float) Math.min(1 + program.power() * 0.1, 6));
            if (program.element() == Sigil.FIRE) e.igniteForSeconds(3);
        }
    }

    private LivingEntity casterEntity(ServerLevel level) {
        if (caster == null) return null;
        Player pl = level.getPlayerByUUID(caster);
        if (pl != null) return pl;
        return level.getEntity(caster) instanceof LivingEntity le ? le : null;
    }

    private boolean followCaster(ServerLevel level) {
        LivingEntity p = casterEntity(level);
        if (p == null || !p.isAlive()) return false;
        Vec3 look = p.getLookAngle();
        if (p instanceof Mob m && m.getTarget() != null) {
            look = m.getTarget().getBoundingBox().getCenter().subtract(p.getEyePosition()).normalize();
        }
        if (worn) {
            if (!(p instanceof Player pl) || !com.fimkov.witchsatelier.glyph.page.PageService.hasBootsGlyph(pl)) return false;
            Vec3 flat = new Vec3(look.x, 0, look.z);
            Vec3 fwd = flat.lengthSqr() > 1e-4 ? flat.normalize() : new Vec3(0, 0, 1);
            Vec3 right = fwd.cross(new Vec3(0, 1, 0)).normalize();
            if (wornLocal == null) {
                Vec3 d = program.direction();
                wornLocal = new Vec3(d.dot(right), d.dot(fwd), d.y);
            }
            Vec3 dir = right.scale(wornLocal.x).add(fwd.scale(wornLocal.y)).add(0, wornLocal.z, 0).normalize();
            frame = wornFrame(right, fwd);
            program = program.withPose(p.position().add(0, 0.03, 0), new Vec3(0, 1, 0), dir);
            return true;
        }
        frame = GlyphFrame.facing(look);
        Vec3 origin = p.getEyePosition().add(look.scale(0.9)).subtract(0, 0.25, 0);
        program = program.withPose(origin, look, aim(level, p, origin, look));
        return true;
    }

    private static Vec3 aim(ServerLevel level, LivingEntity p, Vec3 origin, Vec3 look) {
        double range = 40, cone = Math.cos(Math.toRadians(8));
        LivingEntity best = null;
        double bestDot = cone;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().expandTowards(look.scale(range)).inflate(4),
                e -> e != p && e.isAlive() && !e.isSpectator())) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(origin);
            double d = to.length();
            if (d > range || d < 0.5) continue;
            double dot = to.normalize().dot(look);
            if (dot > bestDot && p.hasLineOfSight(e)) { bestDot = dot; best = e; }
        }
        return best == null ? look : best.getBoundingBox().getCenter().subtract(origin).normalize();
    }

    /**
     * Ends the spell. A Windowway forgets its place in the network only when {@code reason} says its ink is gone: a
     * world that stops with the ink still lit, or a window read again, keeps every window where it was.
     */
    public void stop(ServerLevel level, WindowwayService.Reason reason) {
        stopped = true;
        if (steed != null && level.getEntity(steed) instanceof com.fimkov.witchsatelier.entity.WaterHorseEntity w) w.splash();
        WindowwayService.close(level, id, reason);

        for (TempBlock t : temps) {
            for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(t.pos().above()).inflate(0.2, 1, 0.2))) {
                le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0));
            }
        }
        for (TempBlock t : temps) {
            if (level.getBlockState(t.pos()) == t.placed()) level.setBlock(t.pos(), Blocks.AIR.defaultBlockState(), 3);
        }
        temps.clear();
        tempAt.clear();
    }

    private void run(ServerLevel level) {
        if (worn && wornRun(level)) return;
        switch (program.form()) {
            case BEAM -> { if (program.inverted() && !program.has(SpellProgram.WALL) && !handheld) spill(level); else beam(level); }
            case SPILL -> spill(level);
            case BOLTS -> bolts(level);
            case LIFT -> lift(level);
            case PULL -> pull(level);
            case RAIN -> rain(level);
            case PETRIFY -> petrify(level);
            case BIRD -> {}
            case MANIFEST -> manifest(level);
            case CENTER_SIGN -> centerSign(level);
            case OBLIVION -> Obliviation.run(level, this);
            default -> {}
        }
    }

    private void beam(ServerLevel level) {
        SpellProgram p = program;
        if (p.has(SpellProgram.WALL)) { windWall(level); return; }
        if (p.element() == Sigil.WATER) { waterJet(level); return; }

        double len = Math.min(3 + p.power() * 0.35 + p.radius() * 3, 48) * (p.focused() ? 1.5 : 1);
        double width = Math.min(0.2 + p.radius() * 0.45, 5) * (p.focused() ? 0.6 : 1) * (p.spread() ? 1.8 : 1);
        Vec3 from = p.origin();
        Vec3 dir = p.direction();
        Vec3 to = from.add(dir.scale(len));

        ClipContext.Fluid fluids = p.element() == Sigil.FIRE || p.element() == Sigil.WATER ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE;
        BlockHitResult hit = level.clip(new ClipContext(from.add(dir.scale(0.1)), to, ClipContext.Block.COLLIDER, fluids, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.BLOCK) {
            to = hit.getLocation();
            len = to.distanceTo(from);
        }

        fxLength = (float) len;
        fxWidth = (float) width;
        flourish(level, from, dir, len, width, hit.getType() == HitResult.Type.BLOCK ? hit : null);
        ParticleOptions part = ElementLook.particle(p.element());
        int n = (int) Math.ceil(len * 0.8);
        for (int i = 0; i < n; i++) {
            Vec3 at = from.add(dir.scale(len * level.random.nextDouble()));
            double s = width * 0.35;
            level.sendParticles(part, at.x, at.y, at.z, 1, s, s, s, p.element() == Sigil.WIND ? 0.05 : 0.01);
        }

        double strength = Math.min(0.08 + p.power() * 0.006, 0.6);
        for (Entity e : entitiesAlong(level, from, dir, len, width)) {
            switch (p.element()) {
                case FIRE -> {
                    if (!p.gentle()) {
                        e.igniteForSeconds(4);
                        if (age % 10 == 0) e.hurt(level.damageSources().onFire(), (float) (1 + p.power() * 0.05));
                    }
                }
                case WATER -> {
                    push(e, dir.scale(strength));
                    e.clearFire();
                }
                case WIND, WIND_UNDERFOOT -> {
                    push(e, dir.scale(strength * 2));
                    e.resetFallDistance();
                }
                case LIGHT, CRYSTAL -> {
                    if (e instanceof LivingEntity le) {
                        le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0));
                        if (le.isInvertedHealAndHarm()) {
                            le.igniteForSeconds(3);
                            if (age % 10 == 0) le.hurt(level.damageSources().magic(), (float) (1 + p.power() * 0.05));
                        }
                    }
                }
                case AERIFORMS -> {
                    if (e instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100, 0));
                }
                case EARTH -> {
                    if (age <= 6) push(e, dir.scale(0.35));
                }
            }
        }

        if (hit.getType() == HitResult.Type.BLOCK && age % 20 == 1) {
            BlockPos hp = hit.getBlockPos();
            BlockPos front = hp.relative(hit.getDirection());
            if (p.element() == Sigil.FIRE && level.getFluidState(hp).is(FluidTags.WATER) && !p.gentle()) {
                if (level.getBlockState(hp).is(Blocks.WATER)) level.setBlock(hp, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.CLOUD, hp.getX() + 0.5, hp.getY() + 1, hp.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.03);
                level.playSound(null, hp, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.4f);
            }
            switch (p.element()) {
                case FIRE -> {
                    if (p.gentle()) melt(level, hp);
                    else if (level.isEmptyBlock(front) && BaseFireBlock.canBePlacedAt(level, front, hit.getDirection()) && Earthworks.mayChange(level, front, caster, false)) {
                        level.setBlock(front, BaseFireBlock.getState(level, front), 11);
                    }
                }
                case WATER -> douse(level, hp, front);
                default -> {}
            }
        }

        if (age == 1) {
            if (p.element() == Sigil.LIGHT || p.element() == Sigil.CRYSTAL) {
                for (double d = 1; d < len; d += 2.5) placeTemp(level, BlockPos.containing(from.add(dir.scale(d))), light(15));
            } else if (p.element() == Sigil.EARTH) {
                raisePillar(level, from, dir, Math.min(len, 1 + p.power() / 2));
            }
        }
    }

    private void spill(ServerLevel level) {
        SpellProgram p = program;
        if (p.element() == Sigil.WATER) { waterWave(level); return; }
        double r = 1.5 + p.radius() * 1.2 + p.power() * 0.05;
        fxLength = (float) r;
        Vec3 o = p.origin();
        if (p.element() == Sigil.FIRE || p.element() == Sigil.WIND || p.element() == Sigil.EARTH) {
            double front = ((age - 1) % 30) * r / 26;
            if (front <= r) {
                int steps = (int) Math.max(8, front * Math.PI * 2 / 0.7);
                for (int k = 0; k < steps; k++) {
                    double a = k * Math.PI * 2 / steps + age * 0.05;
                    Vec3 at = o.add(p.normal().scale(0.15)).add(ring(p, a).scale(front));
                    if (!allowed(at)) continue;
                    Vec3 v = ring(p, a).scale(0.05).add(p.normal().scale(0.04));
                    ParticleOptions part = switch (p.element()) {
                        case FIRE -> p.gentle() ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME;
                        case WIND -> ParticleTypes.CLOUD;
                        default -> new BlockParticleOption(ParticleTypes.BLOCK,
                                level.getBlockState(BlockPos.containing(o.subtract(p.normal().scale(0.5)))).isAir() ? Blocks.DIRT.defaultBlockState()
                                        : level.getBlockState(BlockPos.containing(o.subtract(p.normal().scale(0.5)))));
                    };
                    level.sendParticles(part, at.x, at.y, at.z, 0, v.x, v.y, v.z, 1);
                }
                if ((age - 1) % 30 == 0) level.playSound(null, o.x, o.y, o.z, p.element() == Sigil.FIRE ? SoundEvents.BLAZE_SHOOT
                        : p.element() == Sigil.WIND ? SoundEvents.BREEZE_WIND_CHARGE_BURST.value() : SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 0.7f, 0.8f);
            }
        }
        for (int i = 0; i < 6; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double d = level.random.nextDouble() * r;
            Vec3 at = o.add(p.normal().scale(0.3)).add(ring(p, a).scale(d));
            if (allowed(at)) level.sendParticles(ElementLook.particle(p.element()), at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.02);
        }
        if (p.element() == Sigil.EARTH && age <= 24 && age % 6 == 0) earthWall(level, o, r, age / 6);
        if (age % 10 != 0) return;
        AABB box = new AABB(o, o).inflate(r);
        for (Entity e : level.getEntities((Entity) null, box, Entity::isAlive)) {
            if (e.position().distanceTo(o) > r || !allowed(e.position())) continue;
            switch (p.element()) {
                case FIRE -> { if (!p.gentle()) e.igniteForSeconds(3); }
                case WATER -> e.clearFire();
                case WIND, WIND_UNDERFOOT -> push(e, e.position().subtract(o).normalize().scale(0.6));
                case EARTH -> {
                    if (e instanceof LivingEntity le && e.onGround()) {
                        le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                        push(e, new Vec3(0, 0.25, 0));
                    }
                }
                case LIGHT, CRYSTAL -> { if (e instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0)); }
                case AERIFORMS -> { if (e instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100, 0)); }
            }
        }
        if ((p.element() == Sigil.LIGHT || p.element() == Sigil.CRYSTAL) && age % 20 == 0) {
            for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(o, o).inflate(r))) {
                if (le.isInvertedHealAndHarm() && le.position().distanceTo(o) <= r) {
                    le.hurt(level.damageSources().magic(), (float) (2 + p.power() * 0.08));
                    le.igniteForSeconds(2);
                }
            }
        }
        if (age == 10) {
            BlockPos front = BlockPos.containing(o.add(p.normal().scale(0.5)));
            switch (p.element()) {
                case WATER -> placeTemp(level, front, Blocks.WATER.defaultBlockState());
                case LIGHT, CRYSTAL -> {
                    for (int i = 0; i < 4; i++) {
                        double a = i * Math.PI / 2;
                        Vec3 spot = o.add(p.normal().scale(1)).add(ring(p, a).scale(r * 0.6));
                        if (allowed(spot)) placeTemp(level, BlockPos.containing(spot), light(14));
                    }
                    if (allowed(Vec3.atCenterOf(front))) placeTemp(level, front, light(15));
                }
                default -> {}
            }
        }
        if (p.element() == Sigil.FIRE && age % 20 == 0) {
            BlockPos center = BlockPos.containing(o);
            for (BlockPos bp : BlockPos.betweenClosed(center.offset(-(int) r, -1, -(int) r), center.offset((int) r, 1, (int) r))) {
                if (bp.distToCenterSqr(o) > r * r || !allowed(Vec3.atCenterOf(bp))) continue;
                if (regional() && Math.sqrt(bp.distToCenterSqr(o)) > p.radius() + 1.0) continue;
                if (p.gentle()) melt(level, bp);
                else if ((level.random.nextInt(12) == 0 || (regional() && level.random.nextInt(4) == 0)) && level.isEmptyBlock(bp) && BaseFireBlock.canBePlacedAt(level, bp, Direction.UP) && Earthworks.mayChange(level, bp, caster, false)) {
                    level.setBlock(bp.immutable(), BaseFireBlock.getState(level, bp), 11);
                }
            }
        }
        if (p.element() == Sigil.WATER && age % 20 == 0) {
            GlyphService.washArea(level, o, r, strokes);
            BlockPos center = BlockPos.containing(o);
            for (BlockPos bp : BlockPos.betweenClosed(center.offset(-(int) r, -1, -(int) r), center.offset((int) r, 1, (int) r))) {
                if (bp.distToCenterSqr(o) <= r * r) douse(level, bp.immutable(), bp.immutable());
            }
        }
    }

    private void bolts(ServerLevel level) {
        if ((age - 1) % 4 != 0) return;
        SpellProgram p = program;
        Vec3 dir = p.direction();
        if (p.spread()) dir = dir.add(ring(p, level.random.nextDouble() * Math.PI * 2).scale(0.35)).normalize();
        Vec3 from = p.origin().add(dir.scale(0.6));
        double speed = 0.8 + Math.min(p.power() * 0.03, 1.2);
        switch (p.element()) {
            case FIRE -> {
                SmallFireball fb = new SmallFireball(level, from.x, from.y, from.z, dir.scale(speed));
                fb.setOwner(casterEntity(level));
                level.addFreshEntity(fb);
            }
            case WIND, WIND_UNDERFOOT, AERIFORMS -> {
                WindCharge wc = new WindCharge(level, from.x, from.y, from.z, dir.scale(speed));
                wc.setOwner(casterEntity(level));
                level.addFreshEntity(wc);
            }
            case EARTH -> {
                hitscan(level, from, dir, 18, (float) (5 + p.power() * 0.2));
                level.playSound(null, from.x, from.y, from.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.8f, 0.7f);
                return;
            }
            default -> hitscan(level, from, dir, 24, (float) (3 + p.power() * 0.15));
        }
        level.playSound(null, from.x, from.y, from.z, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    private void hitscan(ServerLevel level, Vec3 from, Vec3 dir, double range, float damage) {
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult bh = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (bh.getType() == HitResult.Type.BLOCK) to = bh.getLocation();
        double len = to.distanceTo(from);
        Entity target = null;
        double best = len;
        for (Entity e : level.getEntities((Entity) null, new AABB(from, to).inflate(1), en -> en instanceof LivingEntity && en.isAlive())) {
            var clip = e.getBoundingBox().inflate(0.3).clip(from, to);
            if (clip.isPresent()) {
                double d = clip.get().distanceTo(from);
                if (d < best) { best = d; target = e; }
            }
        }
        for (double d = 0; d < best; d += 0.4) {
            Vec3 at = from.add(dir.scale(d));
            level.sendParticles(ElementLook.trail(program.element()), at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0);
        }
        if (target != null) {
            LivingEntity by = casterEntity(level);
            target.hurt(by == null ? level.damageSources().magic() : level.damageSources().indirectMagic(by, by), damage);
            push(target, dir.scale(0.5));
            if (program.element() == Sigil.WATER) target.clearFire();
            if (program.element() == Sigil.LIGHT && target instanceof LivingEntity le) {
                le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
                if (le.isInvertedHealAndHarm()) { le.hurt(level.damageSources().magic(), damage); le.igniteForSeconds(3); }
            }
            if (program.element() == Sigil.EARTH && target instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        }
    }

    private void lift(ServerLevel level) {
        SpellProgram p = program;
        if (p.element() == Sigil.EARTH && !p.inverted() && p.normal().y > 0.5) { earthRise(level); return; }
        if (!p.inverted() && !worn) {
            switch (p.element()) {
                case FIRE -> { pyreball(level); return; }
                case LIGHT -> { floatglow(level); return; }
                case WATER -> { if (handheld || p.normal().y > 0.5) { waterRise(level); return; } }
                default -> {}
            }
        }
        double r = Math.max(0.6, p.radius());

        boolean soft = p.has(SpellProgram.SOFT) && !p.inverted();
        double h = soft ? 6 + p.power() * 0.3 + r * 2 : 2 + p.power() * 0.15 + r * 1.2;
        fxLength = (float) h;
        fxWidth = (float) r;
        Vec3 o = p.origin();
        Vec3 n = p.normal();
        AABB box = new AABB(o, o.add(n.scale(h))).inflate(r);
        if (age % 3 == 0) {
            Vec3 at = o.add(ring(p, level.random.nextDouble() * Math.PI * 2).scale(level.random.nextDouble() * r));
            level.sendParticles(ElementLook.particle(p.element()), at.x, at.y, at.z, 1, 0, 0, 0, 0.02);
        }
        for (Entity e : level.getEntities((Entity) null, box, Entity::isAlive)) {
            Vec3 rel = e.position().subtract(o);
            double along = rel.dot(n);
            double side = rel.subtract(n.scale(along)).length();
            if (side > r || along < -0.5 || along > h || !allowed(e.position())) continue;
            Vec3 v = e.getDeltaMovement();
            double target = p.inverted() ? -0.6 : soft ? -0.08 : 0.25 + Math.min(p.power() * 0.01, 0.4);
            double cur = v.dot(n);
            if (p.inverted() ? cur > target : cur < target) {
                e.setDeltaMovement(v.add(n.scale((target - cur) * (soft ? 0.5 : 0.35))));
                e.hurtMarked = true;
            }
            if (!p.inverted()) e.resetFallDistance();
            if (e instanceof ServerPlayer sp) Airborne.hold(sp);
            if (p.vortex() != 0) swirl(e, o, n, p.vortex() * 0.15);
        }
    }

    private void pull(ServerLevel level) {
        SpellProgram p = program;
        if (p.has(SpellProgram.BEND)) { wallBend(level); return; }
        double r = 3 + p.radius() * 1.5 + p.power() * 0.1;
        fxLength = (float) r;
        Vec3 o = p.origin().add(p.normal().scale(0.5));
        double accel = 0.04 + Math.min(p.power() * 0.004, 0.12);
        for (int i = 0; i < 4; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            Vec3 at = o.add(ring(p, a).scale(r * (0.4 + level.random.nextDouble() * 0.6)));
            Vec3 v = o.subtract(at).normalize().scale(p.inverted() ? -0.2 : 0.2);
            level.sendParticles(ElementLook.particle(p.element()), at.x, at.y, at.z, 0, v.x, v.y, v.z, 1);
        }
        for (Entity e : level.getEntities((Entity) null, new AABB(o, o).inflate(r), Entity::isAlive)) {
            Vec3 to = o.subtract(e.position());
            double d = to.length();
            if (d > r || d < 0.4 || !allowed(e.position())) continue;
            Vec3 f = to.normalize().scale(p.inverted() ? -accel * 1.5 : accel);
            push(e, f);
            if (p.vortex() != 0) swirl(e, o, p.normal(), p.vortex() * accel * 2);
            if (p.element() == Sigil.FIRE && !p.gentle() && d < 1.5 && age % 10 == 0) e.igniteForSeconds(2);
            if (p.element() == Sigil.WATER) e.clearFire();
            if (p.element() == Sigil.EARTH && e instanceof LivingEntity le && e.onGround()) {
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, p.inverted() ? 0 : 2));
                if (!p.inverted()) e.setDeltaMovement(e.getDeltaMovement().multiply(0.6, 1, 0.6));
            }
        }
    }

    private void rain(ServerLevel level) {
        SpellProgram p = program;
        double r = 3 + p.radius() * 2 + p.power() * 0.1;
        fxLength = (float) r;
        Vec3 o = p.origin();
        Vec3 top = o.add(p.normal().scale(4 + p.radius()));
        ParticleOptions drop = switch (p.element()) {
            case WATER -> ElementLook.droplet();
            case FIRE -> ParticleTypes.FALLING_LAVA;
            case LIGHT, CRYSTAL -> ParticleTypes.END_ROD;
            case EARTH -> ParticleTypes.ASH;
            default -> ParticleTypes.FALLING_WATER;
        };
        if (p.element() == Sigil.WATER) {
            for (int i = 0; i < 5; i++) {
                double a = level.random.nextDouble() * Math.PI * 2, d = Math.sqrt(level.random.nextDouble()) * r;
                Vec3 at = top.add(ring(p, a).scale(d));
                level.sendParticles(drop, at.x, at.y, at.z, 0, 0, -0.6, 0, 1);
            }
            if (age % 40 == 1) level.playSound(null, o.x, o.y + 2, o.z, SoundEvents.WEATHER_RAIN, SoundSource.WEATHER, 0.5f, 1f);
        } else {
            Vec3 down = p.normal().scale(-1);
            double speed = p.element() == Sigil.LIGHT || p.element() == Sigil.CRYSTAL ? 0.45 : 0.2;
            for (int i = 0; i < 12; i++) {
                double a = level.random.nextDouble() * Math.PI * 2, d = Math.sqrt(level.random.nextDouble()) * r;
                Vec3 at = top.add(ring(p, a).scale(d));
                level.sendParticles(drop, at.x, at.y, at.z, 0, down.x, down.y, down.z, speed * (0.8 + level.random.nextDouble() * 0.4));
            }
        }
        if (age % 20 != 0) return;
        if (p.element() == Sigil.WATER) GlyphService.washArea(level, top.add(p.normal().scale(-4 - p.radius())), r, strokes);
        BlockPos c = BlockPos.containing(o);
        int ri = (int) Math.ceil(r);
        for (BlockPos bp : BlockPos.betweenClosed(c.offset(-ri, -2, -ri), c.offset(ri, 2, ri))) {
            if (bp.distToCenterSqr(o) > r * r || !allowed(Vec3.atCenterOf(bp))) continue;
            BlockPos at = bp.immutable();
            BlockState st = level.getBlockState(at);
            if (p.element() == Sigil.WATER) {
                douse(level, at, at);
                if (st.is(BlockTags.CROPS) && level.random.nextInt(6) == 0) st.randomTick(level, at, level.random);
                if (st.is(Blocks.CAULDRON) && level.random.nextInt(4) == 0) level.setBlock(at, Blocks.WATER_CAULDRON.defaultBlockState(), 3);
                else if (st.is(Blocks.WATER_CAULDRON) && st.getValue(LayeredCauldronBlock.LEVEL) < 3 && level.random.nextInt(4) == 0)
                    level.setBlock(at, st.cycle(LayeredCauldronBlock.LEVEL), 3);
                if (level.random.nextInt(30) == 0 && st.isAir() && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP))
                    level.sendParticles(ParticleTypes.SPLASH, at.getX() + 0.5, at.getY() + 0.05, at.getZ() + 0.5, 3, 0.3, 0, 0.3, 0.05);
            }
        }
        for (Entity e : level.getEntities((Entity) null, new AABB(o, top).inflate(r, 1, r), Entity::isAlive)) {
            if (!allowed(e.position())) continue;
            switch (p.element()) {
                case WATER -> e.clearFire();
                case FIRE -> { if (!p.gentle()) e.igniteForSeconds(2); }
                case LIGHT, CRYSTAL -> {
                    if (e instanceof LivingEntity le) {
                        le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0));
                        if (le.isInvertedHealAndHarm()) le.hurt(level.damageSources().magic(), 1.5f);
                    }
                }
                case EARTH -> {
                    if (e instanceof LivingEntity le && !(le instanceof Player pl && pl.getUUID().equals(caster))) le.hurt(level.damageSources().fallingBlock(e), 1f);
                }
                default -> {}
            }
        }
        if ((p.element() == Sigil.LIGHT || p.element() == Sigil.CRYSTAL) && age % 40 == 0) {
            for (int i = 0; i < 3; i++) {
                double a = level.random.nextDouble() * Math.PI * 2, d = Math.sqrt(level.random.nextDouble()) * r;
                BlockPos at = BlockPos.containing(o.add(ring(p, a).scale(d)).add(0, 0.5, 0));
                for (int k = 0; k < 4 && level.getBlockState(at.below()).isAir(); k++) at = at.below();
                placeTemp(level, at, light(12));
            }
        }
    }

    private void manifest(ServerLevel level) {
        SpellProgram p = program;
        if (p.has(SpellProgram.CAGE)) { waterCage(level); return; }
        if (p.has(SpellProgram.STEED)) { waterHorse(level); return; }
        Vec3 o = p.origin().add(p.normal().scale(0.4));
        if (age % 2 == 0) level.sendParticles(ElementLook.particle(p.element()), o.x, o.y, o.z, 2, p.radius() * 0.2, 0.1, p.radius() * 0.2, 0.01);
        if (age == 1) {
            BlockPos front = BlockPos.containing(o);
            switch (p.element()) {
                case LIGHT, CRYSTAL -> placeTemp(level, front, light(Math.min(15, 8 + (int) p.power())));
                case WATER -> { if (p.normal().y > 0.5) placeTemp(level, front, Blocks.WATER.defaultBlockState()); }
                case FIRE -> { if (!p.gentle() && p.normal().y > 0.5 && BaseFireBlock.canBePlacedAt(level, front, Direction.UP)) placeTemp(level, front, BaseFireBlock.getState(level, front)); }
                default -> {}
            }
        }
        if (p.element() == Sigil.EARTH && age % 20 == 0) {
            double r = 2 + p.radius() * 2;
            BlockPos c = BlockPos.containing(p.origin());
            int ri = (int) Math.ceil(r);

            List<BlockPos> growing = new ArrayList<>();
            for (BlockPos bp : BlockPos.betweenClosed(c.offset(-ri, -1, -ri), c.offset(ri, 2, ri))) {
                if (bp.distToCenterSqr(p.origin()) > r * r) continue;
                BlockState st = level.getBlockState(bp);
                if (st.getBlock() instanceof BonemealableBlock b && b.isValidBonemealTarget(level, bp, st)
                        && !(st.getBlock() instanceof GrassBlock)) growing.add(bp.immutable());
            }
            Collections.shuffle(growing, new Random(level.random.nextLong()));
            for (BlockPos at : growing.subList(0, Math.min(3, growing.size()))) {
                BlockState st = level.getBlockState(at);
                ((BonemealableBlock) st.getBlock()).performBonemeal(level, level.random, at, st);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0);
            }
        }
        if (p.element() == Sigil.FIRE && p.gentle() && age % 10 == 0) {
            double r = 1.5 + p.radius() * 1.5;
            BlockPos c = BlockPos.containing(p.origin());
            int ri = (int) Math.ceil(r);
            List<BlockPos> cold = new ArrayList<>();
            for (BlockPos bp : BlockPos.betweenClosed(c.offset(-ri, -1, -ri), c.offset(ri, 2, ri))) {
                if (bp.distToCenterSqr(p.origin()) > r * r) continue;

                Vec3 rel = Vec3.atCenterOf(bp).subtract(p.origin());
                double along = rel.dot(p.normal());
                if (along < 0 && rel.subtract(p.normal().scale(along)).length() < p.radius() + 1) continue;
                BlockState st = level.getBlockState(bp);
                if (st.is(Blocks.SNOW) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.POWDER_SNOW) || st.is(Blocks.ICE)) cold.add(bp.immutable());
            }
            cold.sort(Comparator.comparingDouble(bp -> bp.distToCenterSqr(p.origin())));
            for (BlockPos bp : cold.subList(0, Math.min(3, cold.size()))) {
                melt(level, bp);
                level.sendParticles(ParticleTypes.CLOUD, bp.getX() + 0.5, bp.getY() + 0.8, bp.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.01);
            }
        }
        if (p.element() == Sigil.WIND_UNDERFOOT || p.element() == Sigil.AERIFORMS) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(o, o).inflate(Math.max(1, p.radius()) + 1))) {
                e.addEffect(new MobEffectInstance(p.element() == Sigil.AERIFORMS ? MobEffects.WATER_BREATHING : MobEffects.JUMP, 40, 1));
                if (p.element() == Sigil.WIND_UNDERFOOT) e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0));
            }
        }
    }

    private void centerSign(ServerLevel level) {
        SpellProgram p = program;
        if (p.centerSign() == SignType.WINDOW) {
            if (age == 1) WindowwayService.open(level, this);
            return;
        }
        Vec3 o = p.origin().add(p.normal().scale(0.5));
        double r = Math.max(1.5, p.radius() * 2);
        if (age % 4 == 0) level.sendParticles(ElementLook.rune(null, p.centerSign()), o.x, o.y, o.z, 1, r * 0.4, 0.2, r * 0.4, 0);
        if (age % 20 != 0) return;
        AABB box = new AABB(o, o).inflate(r);
        if (p.centerSign() == SignType.REPETITION) {
            for (Player pl : level.getEntitiesOfClass(Player.class, box)) {
                for (ItemStack st : new ItemStack[]{pl.getMainHandItem(), pl.getOffhandItem()}) {
                    if (st.isDamaged() && !(st.getItem() instanceof InkPenItem)) {
                        st.setDamageValue(Math.max(0, st.getDamageValue() - (1 + (int) (p.power() / 10))));
                    }
                }
            }
            for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, box)) it.setExtendedLifetime();
        } else if (p.centerSign() == SignType.VISION) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
                e.addEffect(p.hidden() ? new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0) : new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0));
            }
        } else if (p.centerSign() == SignType.BILLOWING) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
                e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0));
                e.resetFallDistance();
            }
            level.sendParticles(ParticleTypes.CLOUD, o.x, o.y, o.z, 10, r * 0.4, 0.2, r * 0.4, 0);
        }
    }

    private final Map<Integer, Integer> stoneGaze = new HashMap<>();

    private double stoneFront;

    private void petrify(ServerLevel level) {
        SpellProgram p = program;
        double reach = Math.min(10, 2.5 + p.radius() * 2.5 + p.power() * 0.05);
        Vec3 o = p.origin();
        fxLength = (float) Math.max(1, stoneFront);
        var dust = new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.PETRIFIED.get().defaultBlockState());
        for (int i = 0; i < 4; i++) {
            double a = age * 0.25 + i * Math.PI / 2, d = Math.max(0.5, stoneFront) * ((age % 20) / 20.0);
            Vec3 at = o.add(ring(p, a).scale(d)).add(p.normal().scale(0.2 + (age % 20) * 0.05));
            level.sendParticles(dust, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0);
        }
        if (age % 20 == 1) level.playSound(null, o.x, o.y, o.z, SoundEvents.GRINDSTONE_USE, SoundSource.HOSTILE, 0.5f, 0.4f);
        if (age < 10) return;

        if (age % 5 == 0 && stoneFront < reach) {
            double from = stoneFront;
            stoneFront = Math.min(reach, stoneFront + 1);
            spreadStone(level, o, from, stoneFront);
            if (stoneFront >= reach) spikes(level, o, reach);
            level.playSound(null, o.x, o.y, o.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.HOSTILE, 0.9f, 0.5f + (float) (stoneFront / reach) * 0.3f);
        }
        double r = stoneFront + 0.5;
        boolean brimhatCast = caster != null && level.getEntity(caster) instanceof BrimhatEntity;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(o, o).inflate(r), e -> e.isAlive() && !e.isSpectator())) {
            if (caster != null && e.getUUID().equals(caster)) continue;
            if (brimhatCast && e instanceof BrimhatEntity) continue;
            if (e.getBoundingBox().getCenter().distanceTo(o) > r) continue;
            int t = stoneGaze.merge(e.getId(), 1, Integer::sum);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, Math.min(6, t / 3), false, false));
            e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20, 2, false, false));
            if (t % 4 == 0) level.sendParticles(dust, e.getX(), e.getY() + e.getBbHeight() * level.random.nextDouble(), e.getZ(), 3, 0.25, 0.1, 0.25, 0.02);
            if (t >= 20 && e instanceof Mob mob && !(e instanceof WitherBoss)
                    && !(e instanceof EnderDragon)) {
                if (StatueBlock.petrify(level, mob) && caster != null) {
                    Player who = level.getPlayerByUUID(caster);
                    if (who != null) MagicTrigger.fire(who, "petrify");
                }
                stoneGaze.remove(e.getId());
            }
        }
    }

    private void spreadStone(ServerLevel level, Vec3 o, double from, double to) {
        BlockPos c = BlockPos.containing(o);
        int r = (int) Math.ceil(to) + 1;
        BlockPos ground = plane != null ? BlockPos.containing(o.subtract(plane.normal().scale(0.3))) : null;
        Vec3 n = plane != null ? plane.normal() : null;
        double keep = program.radius() + 0.8;
        List<AABB> living = level.getEntitiesOfClass(LivingEntity.class, new AABB(o, o).inflate(to + 2), e -> e.isAlive() && !e.isSpectator())
                .stream().map(e -> e.getBoundingBox().inflate(0.3)).toList();
        int taken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            double d = Math.sqrt(pos.distToCenterSqr(o));
            if (d <= from || d > to) continue;
            if (ground != null) {
                Vec3 rel = Vec3.atCenterOf(pos).subtract(Vec3.atCenterOf(ground));
                double along = rel.dot(n);
                if (along > -0.5 && along < 2.5 && rel.subtract(n.scale(along)).length() <= keep) continue;
            }
            AABB cell = new AABB(pos);
            if (level.getBlockState(pos).isAir() && living.stream().anyMatch(cell::intersects)) continue;
            if (!level.getBlockState(pos).isAir() && !Earthworks.mayChange(level, pos, caster, false)) continue;
            if (PetrifiedBlock.petrify(level, pos) && taken++ % 7 == 0)
                level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.4, 0.4, 0.4, 0.02);
        }
    }

    private void spikes(ServerLevel level, Vec3 o, double reach) {
        BlockPos c = BlockPos.containing(o);
        int r = (int) Math.ceil(reach) + 1;
        var crystal = ModBlocks.PETRIFIED.get();
        for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            double d = Math.sqrt(pos.distToCenterSqr(o));
            if (d < reach - 1.2 || d > reach || !level.getBlockState(pos).is(crystal) || level.random.nextFloat() > 0.45f) continue;
            Vec3 out = Vec3.atCenterOf(pos).subtract(o);
            Direction dir = Direction.getNearest(out.x, out.y, out.z);
            PetrifiedBlock.spike(level, pos.relative(dir), dir);
        }
    }

    private void aim(ServerLevel level) {
        if (age % 5 != 1) return;
        SpellProgram p = program;
        Vec3 o = p.origin();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(o, o).inflate(24), e -> e.isAlive() && !e.isSpectator())) {
            if (caster != null && e.getUUID().equals(caster)) continue;
            Vec3 to = e.getBoundingBox().getCenter().subtract(o);
            if (to.normalize().dot(p.normal()) < 0.25) continue;
            double score = to.length() * (e instanceof Enemy ? 0.5 : 1);
            if (score < bestScore) { bestScore = score; best = e; }
        }
        if (best != null) program = p.withPose(p.origin(), p.normal(), best.getBoundingBox().getCenter().subtract(o).normalize());
    }

    private void mods(ServerLevel level) {
        SpellProgram p = program;
        Vec3 o = p.origin();
        double r = Math.max(2.5, p.radius() * 2.2);
        AABB area = new AABB(o, o).inflate(r);
        if (p.has(SpellProgram.WARD)) {
            for (Projectile pr : level.getEntitiesOfClass(Projectile.class, area.inflate(1))) {
                Vec3 rel = pr.position().subtract(o);
                if (rel.length() > r + 0.5 || pr.getDeltaMovement().dot(rel) >= 0) continue;
                if (caster != null && pr.getOwner() != null && pr.getOwner().getUUID().equals(caster)) continue;
                pr.setDeltaMovement(rel.normalize().scale(pr.getDeltaMovement().length() * 0.8));
                pr.hurtMarked = true;
                level.sendParticles(ParticleTypes.END_ROD, pr.getX(), pr.getY(), pr.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
            }
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e -> e instanceof Enemy)) {
                Vec3 rel = e.position().subtract(o);
                if (rel.length() < r) push(e, new Vec3(rel.x, 0, rel.z).normalize().scale(0.3).add(0, 0.1, 0));
            }
            if (age % 4 == 0) {
                double a = level.random.nextDouble() * Math.PI * 2, b = Math.acos(level.random.nextDouble() * 2 - 1);
                Vec3 at = o.add(Math.sin(b) * Math.cos(a) * r, Math.abs(Math.cos(b)) * r, Math.sin(b) * Math.sin(a) * r);
                level.sendParticles(ElementLook.rune(p.element(), SignType.DIAMOND), at.x, at.y, at.z, 1, 0, 0, 0, 0);
            }
        }
        if (p.has(SpellProgram.MIST) && age % 5 == 0) {
            for (int i = 0; i < 6; i++) {
                double a = level.random.nextDouble() * Math.PI * 2, d = r * (0.6 + 0.4 * level.random.nextDouble());
                Vec3 at = o.add(ring(p, a).scale(d)).add(p.normal().scale(0.4 + level.random.nextDouble() * 1.4));
                level.sendParticles(ParticleTypes.FALLING_WATER, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0);
                level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0);
            }
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (e.isOnFire()) e.clearFire();
                e.setTicksFrozen(Math.max(0, e.getTicksFrozen() - 4));
            }
            if (age % 20 == 0) {
                BlockPos c = BlockPos.containing(o);
                int ri = (int) Math.ceil(r);
                for (BlockPos q : BlockPos.betweenClosed(c.offset(-ri, -2, -ri), c.offset(ri, 3, ri))) {
                    var st = level.getBlockState(q);
                    if (st.is(BlockTags.FIRE)) level.removeBlock(q, false);
                    else if (st.getBlock() instanceof FarmBlock && st.getValue(FarmBlock.MOISTURE) < 7)
                        level.setBlock(q, st.setValue(FarmBlock.MOISTURE, 7), 2);
                }
            }
        }
        if (p.has(SpellProgram.DRY) && age % 5 == 0) {
            BlockPos c = BlockPos.containing(o);
            int ri = (int) Math.ceil(r);
            for (BlockPos q : BlockPos.betweenClosed(c.offset(-ri, -ri, -ri), c.offset(ri, ri, ri))) {
                if (q.distToCenterSqr(o) > r * r) continue;
                if (TimedBlocks.extend(level, q, 20)) continue;
                if (!(level.getBlockState(q).getBlock() instanceof LiquidBlock) || !level.getFluidState(q).is(FluidTags.WATER)) continue;
                TimedBlocks.set(level, q, Blocks.AIR.defaultBlockState(), 20);
            }
            if (level.isRaining() && age % 10 == 0) {
                for (int i = 0; i < 8; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    Vec3 at = o.add(Math.cos(a) * r, r * 0.8, Math.sin(a) * r);
                    level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 2, 0.1, 0.05, 0.1, 0.02);
                }
            }
        }
        if (p.has(SpellProgram.REPEL)) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e -> (e instanceof Animal
                    || e instanceof Enemy) && !(e instanceof TamableAnimal t && t.isTame()))) {
                Vec3 rel = e.position().subtract(o);
                if (rel.length() < r) push(e, new Vec3(rel.x, 0, rel.z).normalize().scale(0.25).add(0, 0.05, 0));
            }
        }
        if (p.has(SpellProgram.BIND) && age % 10 == 0) {
            LivingEntity by = casterEntity(level);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (caster != null && e.getUUID().equals(caster)) continue;
                if (by instanceof Mob && e.getType() == by.getType()) continue;
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 4, false, false));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 1, false, false));
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3 + age * 0.1;
                    level.sendParticles(ElementLook.trail(p.element()), e.getX() + Math.cos(a) * 0.5, e.getY() + 0.2 + i * 0.25, e.getZ() + Math.sin(a) * 0.5, 1, 0, 0, 0, 0);
                }
            }
        }
        if (p.has(SpellProgram.PUPPET)) {
            for (Mob m : level.getEntitiesOfClass(Mob.class, area)) {
                m.setTarget(null);
                m.getNavigation().stop();
                m.setYRot(m.getYRot() + 18);
                m.setYBodyRot(m.getYRot());
                m.setYHeadRot(m.getYRot());
                if (m.onGround() && age % 12 == 0) m.setDeltaMovement(m.getDeltaMovement().add(0, 0.35, 0));
                if (age % 6 == 0) level.sendParticles(ParticleTypes.NOTE, m.getX(), m.getY() + m.getBbHeight() + 0.4, m.getZ(), 1, 0.2, 0, 0.2, 1);
            }
        }
        if (p.has(SpellProgram.GATHER)) {
            double reach = r * 2;
            Vec3 into = o.add(p.normal().scale(0.6));
            for (Entity e : level.getEntities((Entity) null, new AABB(o, o).inflate(reach),
                    e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
                Vec3 to = into.subtract(e.position());
                if (to.length() < 0.4) { e.setDeltaMovement(e.getDeltaMovement().scale(0.5)); continue; }
                e.setDeltaMovement(e.getDeltaMovement().scale(0.8).add(to.normalize().scale(0.12)));
                e.hurtMarked = true;
            }
            if (p.element() == Sigil.WATER && age % 40 == 0) {
                BlockPos at = BlockPos.containing(into);
                if (level.getBlockState(at).isAir() && p.normal().y > 0.5) TimedBlocksBridge.water(level, at);
            }
        }
    }

    private void flourish(ServerLevel level, Vec3 from, Vec3 dir, double len, double width, BlockHitResult hit) {
        SpellProgram p = program;
        var rnd = level.random;
        switch (p.element()) {
            case FIRE -> {
                int flames = (int) Math.min(24, 4 + len * 0.8 + width * 4);
                for (int i = 0; i < flames; i++) {
                    double sp = 0.25 + rnd.nextDouble() * 0.5;
                    Vec3 v = dir.scale(sp).add(rnd.nextGaussian() * 0.03 * width, rnd.nextGaussian() * 0.03 * width + 0.01, rnd.nextGaussian() * 0.03 * width);
                    Vec3 at = from.add(dir.scale(rnd.nextDouble() * Math.min(len, 3)));
                    level.sendParticles(p.gentle() ? ParticleTypes.SMALL_FLAME : (i % 3 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME), at.x, at.y, at.z, 0, v.x, v.y, v.z, 1);
                }
                if (age % 3 == 0) {
                    Vec3 end = from.add(dir.scale(len));
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, end.x, end.y, end.z, 1, width * 0.3, width * 0.3, width * 0.3, 0.02);
                    level.sendParticles(ParticleTypes.LAVA, end.x, end.y, end.z, rnd.nextInt(3) == 0 ? 1 : 0, 0.1, 0.1, 0.1, 0);
                }
                if (age % 25 == 1) level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.5f, 0.6f);
                if (age % 12 == 0) level.playSound(null, from.x, from.y, from.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.9f, 0.8f + rnd.nextFloat() * 0.3f);
            }
            case WIND, WIND_UNDERFOOT, AERIFORMS -> {
                if (age % 2 == 0) {
                    Vec3 at = from.add(dir.scale(rnd.nextDouble() * len));
                    level.sendParticles(ParticleTypes.SMALL_GUST, at.x, at.y, at.z, 1, width * 0.3, width * 0.3, width * 0.3, 0);
                }
                for (int i = 0; i < 3; i++) {
                    Vec3 v = dir.scale(0.6 + rnd.nextDouble() * 0.6);
                    Vec3 at = from.add(dir.scale(rnd.nextDouble() * len * 0.3)).add(rnd.nextGaussian() * width * 0.3, rnd.nextGaussian() * width * 0.3, rnd.nextGaussian() * width * 0.3);
                    level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, v.x * 0.5, v.y * 0.5, v.z * 0.5, 1);
                }
                if (age % 20 == 1) level.playSound(null, from.x, from.y, from.z, SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 0.8f, 1.2f);
            }
            case EARTH -> {
                BlockState mat = plane == null ? Blocks.STONE.defaultBlockState() : level.getBlockState(BlockPos.containing(p.origin().subtract(p.normal().scale(0.5))));
                if (mat.isAir()) mat = Blocks.STONE.defaultBlockState();
                var grit = new BlockParticleOption(ParticleTypes.BLOCK, mat);
                Vec3 at = from.add(dir.scale(rnd.nextDouble() * len));
                level.sendParticles(grit, at.x, at.y, at.z, 3, width * 0.3, width * 0.3, width * 0.3, 0.1);
                if (hit != null && age % 4 == 0) {
                    Vec3 h = hit.getLocation();
                    level.sendParticles(grit, h.x, h.y, h.z, 8, 0.3, 0.3, 0.3, 0.15);
                }
                if (age % 15 == 1) level.playSound(null, from.x, from.y, from.z, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 0.7f, 0.6f);
            }
            case LIGHT, CRYSTAL -> {
                if (age % 3 == 0) {
                    Vec3 at = from.add(dir.scale(rnd.nextDouble() * len));
                    level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, width * 0.25, width * 0.25, width * 0.25, 0.01);
                }
                if (age % 30 == 1) level.playSound(null, from.x, from.y, from.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6f, 1.5f);
            }
            default -> {}
        }
    }

    private void steam(ServerLevel level) {
        SpellProgram p = program;
        double len = Math.max(2, fxLength);
        Vec3 dir = p.form() == Form.BEAM ? p.direction() : p.normal();
        for (int i = 0; i < 6; i++) {
            Vec3 at = p.origin().add(dir.scale(level.random.nextDouble() * len));
            level.sendParticles(ElementLook.steam(), at.x, at.y, at.z, 1, 0.4, 0.4, 0.4, 0.03);
        }
        if (age % 15 == 0) level.playSound(null, p.origin().x, p.origin().y, p.origin().z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 0.7f);
        if (age % 10 == 0) {
            for (Entity e : entitiesAlong(level, p.origin(), dir, len, Math.max(1.2, p.radius()))) {
                if (e instanceof LivingEntity le && !le.fireImmune()) le.hurt(level.damageSources().hotFloor(), (float) (1 + p.power() * 0.04));
                e.clearFire();
            }
        }
    }

    private void waterJet(ServerLevel level) {
        SpellProgram p = program;
        double len = Math.min(4 + p.power() * 0.4 + p.radius() * 3, 44) * (p.focused() ? 1.4 : 1);
        double width = Math.min(0.2 + p.radius() * 0.4, 4.5) * (p.focused() ? 0.6 : 1) * (p.spread() ? 1.8 : 1);
        Vec3 from = p.origin().add(p.normal().scale(0.1));
        Vec3 dir = p.direction();
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(len)), ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, CollisionContext.empty()));
        boolean hitBlock = hit.getType() == HitResult.Type.BLOCK;
        if (hitBlock) len = hit.getLocation().distanceTo(from);
        fxLength = (float) len;
        fxWidth = (float) width;

        double speed = Math.min(0.75 + len * 0.045, 1.8);
        int drops = (int) Math.min(30, 6 + width * 8);
        for (int i = 0; i < drops; i++) {
            Vec3 v = dir.scale(speed * (0.85 + level.random.nextDouble() * 0.3))
                    .add(level.random.nextGaussian() * 0.04 * width, level.random.nextGaussian() * 0.04 * width + 0.02 * len / 10, level.random.nextGaussian() * 0.04 * width);
            Vec3 at = from.add(ring(p, level.random.nextDouble() * Math.PI * 2).scale(level.random.nextDouble() * width * 0.3));
            level.sendParticles(ElementLook.droplet(), at.x, at.y, at.z, 0, v.x, v.y, v.z, 1);
        }
        if (age % 2 == 0) {
            Vec3 at = from.add(dir.scale(len * level.random.nextDouble()));
            level.sendParticles(ElementLook.spray(), at.x, at.y, at.z, 1, width * 0.2, width * 0.2, width * 0.2, 0.01);
        }
        if (age == 1 || age % 30 == 0) level.playSound(null, from.x, from.y, from.z, ModSounds.WATER_RUSH.get(), SoundSource.PLAYERS, 0.8f, 0.9f + level.random.nextFloat() * 0.2f);

        if (hitBlock) {
            Vec3 h = hit.getLocation();
            Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
            for (int i = 0; i < 5; i++) {
                Vec3 v = n.scale(0.15 + level.random.nextDouble() * 0.15).add(level.random.nextGaussian() * 0.15, 0.12 + level.random.nextDouble() * 0.15, level.random.nextGaussian() * 0.15);
                level.sendParticles(ElementLook.droplet(), h.x, h.y, h.z, 0, v.x, v.y, v.z, 1);
            }
            level.sendParticles(ElementLook.spray(), h.x, h.y, h.z, 2, 0.2, 0.2, 0.2, 0.02);
            level.sendParticles(ParticleTypes.SPLASH, h.x, h.y, h.z, 4, 0.25, 0.1, 0.25, 0.1);
            if (age % 8 == 0) level.playSound(null, h.x, h.y, h.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.35f, 1.2f + level.random.nextFloat() * 0.3f);
            if (age % 10 == 1) soak(level, hit.getBlockPos(), hit.getBlockPos().relative(hit.getDirection()), h);
        }

        double strength = Math.min(0.12 + p.power() * 0.012, 0.9);
        for (Entity e : entitiesAlong(level, from, dir, len, width + 0.3)) {
            push(e, dir.scale(strength).add(0, strength * 0.15, 0));
            e.clearFire();
            if (e instanceof LivingEntity le && le.isSensitiveToWater() && age % 10 == 0) le.hurt(level.damageSources().drown(), (float) (1 + p.power() * 0.06));
            if (age % 4 == 0) level.sendParticles(ParticleTypes.SPLASH, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
        }
    }

    private void soak(ServerLevel level, BlockPos hit, BlockPos front, Vec3 at) {
        douse(level, hit, front);
        BlockState st = level.getBlockState(hit);
        if (st.is(Blocks.CAULDRON)) {
            level.setBlock(hit, Blocks.WATER_CAULDRON.defaultBlockState(), 3);
        } else if (st.is(Blocks.WATER_CAULDRON) && st.getValue(LayeredCauldronBlock.LEVEL) < 3 && age % 20 == 1) {
            level.setBlock(hit, st.cycle(LayeredCauldronBlock.LEVEL), 3);
        } else if (st.getFluidState().is(FluidTags.LAVA)) {
            level.setBlock(hit, st.getFluidState().isSource() ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState(), 3);
            level.playSound(null, hit, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1f, 1f);
        }
        BlockState f = level.getBlockState(front);
        if (f.getBlock() instanceof TorchBlock || f.is(Blocks.REDSTONE_WIRE) || f.is(BlockTags.SMALL_FLOWERS)) {
            level.destroyBlock(front, true);
        }
        GlyphService.washArea(level, at, 0.8, strokes);
    }

    private void waterWave(ServerLevel level) {
        SpellProgram p = program;
        double r = 1.5 + p.radius() * 1.4 + p.power() * 0.06;
        fxLength = (float) r;
        Vec3 o = p.origin();
        Vec3 n = p.normal();
        boolean floor = n.y > 0.7;
        int period = (int) Math.max(30, r / 0.3 + 12);
        int phase = (age - 1) % period;

        for (int i = 0; i < 4; i++) {
            Vec3 v = n.scale(0.2 + level.random.nextDouble() * 0.2).add(level.random.nextGaussian() * 0.08, 0, level.random.nextGaussian() * 0.08);
            level.sendParticles(ElementLook.droplet(), o.x, o.y + 0.1, o.z, 0, v.x, v.y, v.z, 1);
        }
        if (!floor) {
            BlockPos front = BlockPos.containing(o.add(n.scale(1.5)));
            if (age % 10 == 1 && level.getBlockState(front).isAir()) {
                level.setBlock(front, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1), 3);
            }
            for (int i = 0; i < 6; i++) {
                Vec3 v = n.scale(0.25).add(level.random.nextGaussian() * 0.05, -0.05, level.random.nextGaussian() * 0.05);
                Vec3 at = o.add(ring(p, level.random.nextDouble() * Math.PI * 2).scale(level.random.nextDouble() * p.radius() * 0.5));
                level.sendParticles(ElementLook.droplet(), at.x, at.y, at.z, 0, v.x, v.y, v.z, 1);
            }
            if (age % 20 == 0) level.playSound(null, o.x, o.y, o.z, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.8f, 1f);
        } else {
            double front = phase * 0.3;
            if (phase == 0) {
                level.playSound(null, o.x, o.y, o.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.9f, 0.8f);
                level.playSound(null, o.x, o.y, o.z, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8f, 0.6f);
            }
            if (front <= r) {
                int steps = (int) Math.max(8, Math.ceil(front * Math.PI * 2 / 0.9));
                for (int k = 0; k < steps; k++) {
                    double a = (k + level.random.nextDouble() * 0.5) * Math.PI * 2 / steps;
                    Vec3 at = o.add(ring(p, a).scale(front));
                    level.sendParticles(ElementLook.spray(), at.x, at.y + 0.2, at.z, 1, 0.15, 0.1, 0.15, 0.01);
                    Vec3 v = ring(p, a).scale(0.12).add(0, 0.18 + level.random.nextDouble() * 0.1, 0);
                    level.sendParticles(ElementLook.droplet(), at.x, at.y + 0.1, at.z, 0, v.x, v.y, v.z, 1);

                    if (phase % 2 == 0 && front > p.radius() + 0.6 && allowed(at)) {
                        BlockPos bp = BlockPos.containing(at.x, o.y + 0.2, at.z);
                        if (level.getBlockState(bp).isAir() && level.getBlockState(bp.below()).isFaceSturdy(level, bp.below(), Direction.UP)) {
                            int lvl = Math.min(6, 2 + (int) (front / r * 4));
                            level.setBlock(bp, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, lvl), 3);
                        }
                    }
                }
                for (Entity e : level.getEntities((Entity) null, new AABB(o, o).inflate(front + 1, 2, front + 1), Entity::isAlive)) {
                    Vec3 rel = e.position().subtract(o);
                    double d = Math.hypot(rel.x, rel.z);
                    if (d < front - 1 || d > front + 0.6 || d < 0.3) continue;
                    double force = 0.3 + Math.min(p.power() * 0.01, 0.4);
                    push(e, new Vec3(rel.x / d * force, 0.18, rel.z / d * force));
                    e.clearFire();
                    if (e instanceof LivingEntity le && le.isSensitiveToWater()) le.hurt(level.damageSources().drown(), (float) (1 + p.power() * 0.05));
                }
            }
        }
        if (age % 20 == 0) {
            GlyphService.washArea(level, o, r, strokes);
            BlockPos center = BlockPos.containing(o);
            for (BlockPos bp : BlockPos.betweenClosed(center.offset(-(int) r, -1, -(int) r), center.offset((int) r, 1, (int) r))) {
                if (bp.distToCenterSqr(o) <= r * r) douse(level, bp.immutable(), bp.immutable());
            }
        }
    }

    private boolean wornRun(ServerLevel level) {
        Player w = caster == null ? null : level.getPlayerByUUID(caster);
        if (w == null) return false;
        SpellProgram p = program;
        Vec3 feet = w.position();
        Vec3 look = w.getLookAngle();
        Vec3 v = w.getDeltaMovement();
        ParticleOptions part = ElementLook.particle(p.element());
        switch (p.form()) {
            case LIFT -> {
                if (w.isInWater()) return true;
                if (w instanceof ServerPlayer sp) Airborne.hold(sp);
                if (p.inverted()) {
                    if (v.y < -0.12) w.setDeltaMovement(v.x, v.y * 0.6 - 0.02, v.z);
                    w.addEffect(new MobEffectInstance(MobEffects.JUMP, 10, 0, false, false));
                    w.hurtMarked = true;
                } else {
                    if (age % 1200 == 0) w.getItemBySlot(EquipmentSlot.FEET).hurtAndBreak(1, w, EquipmentSlot.FEET);
                }
                w.resetFallDistance();
                if (age % 2 == 0) level.sendParticles(part, feet.x, feet.y, feet.z, 2, 0.15, 0.02, 0.15, 0.01);

                fxLength = (float) (0.3 + Math.min(p.power() * 0.012, 0.6));
                fxWidth = (float) Math.max(0.3, p.radius());
                return true;
            }
            case BEAM -> {
                double thrust = 0.05 + Math.min(p.power() * 0.004, 0.12);
                w.setDeltaMovement(v.add(look.scale(thrust)).add(0, 0.04, 0));
                w.resetFallDistance();
                w.hurtMarked = true;
                Vec3 back = look.scale(-1);
                for (int i = 0; i < 3; i++) {
                    level.sendParticles(part, feet.x, feet.y + 0.1, feet.z, 0, back.x + level.random.nextGaussian() * 0.1,
                            back.y - 0.3, back.z + level.random.nextGaussian() * 0.1, 0.4);
                }
                if (p.element() == Sigil.FIRE && !p.gentle()) {
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(feet, feet.add(back.scale(3))).inflate(0.6), e -> e != w)) e.igniteForSeconds(3);
                }
                if (age % 6 == 0) level.playSound(null, feet.x, feet.y, feet.z, p.element() == Sigil.FIRE ? SoundEvents.FIRECHARGE_USE : SoundEvents.BREEZE_WIND_CHARGE_BURST.value(),
                        SoundSource.PLAYERS, 0.25f, 1.4f);
                fxLength = 0;
                fxWidth = 0.4f;
                return true;
            }
            case BOLTS -> {
                if ((age - 1) % 4 == 0) {
                    double force = 0.8 + Math.min(p.power() * 0.03, 1.2);
                    w.setDeltaMovement(look.scale(force).add(0, 0.15, 0));
                    w.resetFallDistance();
                    w.hurtMarked = true;
                    level.sendParticles(part, feet.x, feet.y + 0.3, feet.z, 14, 0.25, 0.25, 0.25, 0.05);
                    level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.6f, 1.3f);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void birds(ServerLevel level) {
        SpellProgram p = program;
        int alive = birds.size();

        boolean launch = age == 1 || (age % 50 == 0 && alive < 3 * p.birds());
        if (launch) {
            for (int i = 0; i < (age == 1 ? p.birds() : 1); i++) {
                Vec3 dir = p.direction().add(level.random.nextGaussian() * 0.12, level.random.nextGaussian() * 0.12, level.random.nextGaussian() * 0.12).normalize();
                birds.add(new Bird(birdSerial++, p.origin().add(p.normal().scale(0.3)), dir.scale(0.55), 120 + (int) (p.power() * 2)));
            }
            level.playSound(null, p.origin().x, p.origin().y, p.origin().z, ModSounds.BIRD_CALL.get(), SoundSource.PLAYERS, 1f, 0.9f + level.random.nextFloat() * 0.2f);
        }
        for (var it = birds.iterator(); it.hasNext(); ) {
            Bird b = it.next();
            b.age++;
            if (b.age < 34) {
                b.vel = b.vel.scale(0.985);
            } else {
                if (b.home == null) b.home = b.pos;
                double t = (b.age - 34) * 0.07 + b.index;
                Vec3 target = b.home.add(Math.cos(t) * 3.2, Math.sin(t * 0.5) * 0.6, Math.sin(t) * 3.2);
                b.vel = b.vel.scale(0.8).add(target.subtract(b.pos).scale(0.06));
            }
            b.pos = b.pos.add(b.vel);
            level.sendParticles(ElementLook.trail(p.element()), b.pos.x, b.pos.y, b.pos.z, 1, 0.08, 0.08, 0.08, 0.01);
            if (b.age % 3 == 0) level.sendParticles(ElementLook.particle(p.element()), b.pos.x, b.pos.y, b.pos.z, 1, 0.15, 0.15, 0.15, 0.0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(b.pos, b.pos).inflate(0.8))) {
                if (p.element() == Sigil.FIRE) e.igniteForSeconds(3);
                else if (p.element() == Sigil.LIGHT || p.element() == Sigil.CRYSTAL) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
                else if (p.element() == Sigil.WATER) e.clearFire();
            }
            if (b.age >= b.life) {
                level.sendParticles(ElementLook.rune(p.element(), p.centerSign()), b.pos.x, b.pos.y, b.pos.z, 12, 0.3, 0.3, 0.3, 0.02);
                it.remove();
            }
        }
        if (p.form() == Form.BIRD && birds.isEmpty() && age > 1) ticksLeft = Math.min(ticksLeft, 2);
    }

    private List<Entity> entitiesAlong(ServerLevel level, Vec3 from, Vec3 dir, double len, double width) {
        Vec3 to = from.add(dir.scale(len));
        List<Entity> out = new ArrayList<>();
        for (Entity e : level.getEntities((Entity) null, new AABB(from, to).inflate(width + 1), Entity::isAlive)) {
            Vec3 c = e.getBoundingBox().getCenter();
            double t = c.subtract(from).dot(dir);
            if (t < -0.5 || t > len + 0.5) continue;
            double dist = c.subtract(from.add(dir.scale(t))).length();
            if (dist <= width + e.getBbWidth() / 2) out.add(e);
        }
        return out;
    }

    private static void push(Entity e, Vec3 v) {
        e.setDeltaMovement(e.getDeltaMovement().add(v));
        e.hurtMarked = true;
    }

    private static void swirl(Entity e, Vec3 center, Vec3 axis, double amount) {
        Vec3 rel = e.position().subtract(center);
        Vec3 tangent = axis.cross(rel);
        if (tangent.lengthSqr() < 1e-6) return;
        push(e, tangent.normalize().scale(amount));
    }

    private Vec3 ring(SpellProgram p, double a) {
        return frame.planeVector(Math.cos(a), Math.sin(a));
    }

    private void placeTemp(ServerLevel level, BlockPos pos, BlockState state) {
        if (stopped) return;
        BlockState current = level.getBlockState(pos);

        if (!current.isAir() && !(current.canBeReplaced() && current.getFluidState().isEmpty())) return;
        if (current == state || temps.size() >= MAX_TEMPS) return;
        if (!tempAt.add(pos.immutable())) {
            level.setBlock(pos, state, 3);
            temps.removeIf(t -> t.pos().equals(pos));
            temps.add(new TempBlock(pos.immutable(), state));
            return;
        }
        level.setBlock(pos, state, 3);
        temps.add(new TempBlock(pos.immutable(), state));
    }

    private void earthWall(ServerLevel level, Vec3 o, double r, int step) {
        if (plane == null || program.normal().y < 0.5) return;
        BlockPos below = BlockPos.containing(o.subtract(0, 0.5, 0));
        BlockState mat = Earthworks.conjured(level.getBlockState(below), Blocks.COARSE_DIRT.defaultBlockState());
        int height = Math.min(step, 1 + (int) (program.power() / 12));
        double wr = Math.max(2, r * 0.8);
        int steps = (int) Math.ceil(wr * Math.PI * 2 / 0.7);
        Set<BlockPos> done = new HashSet<>();
        for (int k = 0; k < steps; k++) {
            double a = k * Math.PI * 2 / steps;
            BlockPos col = BlockPos.containing(o.x + Math.cos(a) * wr, o.y + 0.5, o.z + Math.sin(a) * wr);
            if (!done.add(col) || !allowed(Vec3.atCenterOf(col))) continue;
            BlockPos at = col.above(height - 1);
            if (level.getBlockState(at.below()).isAir() && height > 1 && !temps.isEmpty() && temps.stream().noneMatch(t -> t.pos().equals(at.below()))) continue;
            placeTemp(level, at, mat);
            if (height == 1) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, mat), at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.05);
        }
        level.playSound(null, o.x, o.y, o.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 0.9f, 0.6f);
    }

    private void earthRise(ServerLevel level) {
        SpellProgram p = program;
        int h = (int) Math.min(16, 2 + p.power() * 0.15 + p.radius());
        int step = age / 8;
        fxLength = step;
        fxWidth = (float) p.radius();
        if (age % 8 != 0 || step > h) return;
        BlockPos below = BlockPos.containing(p.origin().subtract(0, 0.5, 0));
        BlockState mat = Earthworks.conjured(level.getBlockState(below), Blocks.PACKED_MUD.defaultBlockState());
        int rr = (int) Math.max(0, Math.min(2, Math.round(p.radius() * 0.6)));
        BlockPos c = BlockPos.containing(p.origin().add(0, 0.5, 0)).above(step - 1);
        for (int dx = -rr; dx <= rr; dx++) for (int dz = -rr; dz <= rr; dz++) {
            if (dx * dx + dz * dz > rr * rr + rr) continue;
            BlockPos at = c.offset(dx, 0, dz);

            for (Entity e : level.getEntities((Entity) null, new AABB(at), Entity::isAlive)) {
                e.teleportTo(e.getX(), at.getY() + 1.01, e.getZ());
                e.resetFallDistance();
            }
            placeTemp(level, at, mat);
        }
        level.playSound(null, c, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 0.8f, 0.7f);
    }

    private void raisePillar(ServerLevel level, Vec3 from, Vec3 dir, double height) {
        if (plane == null) return;
        BlockPos base = plane.blockAt(plane.toUV(from).x(), plane.toUV(from).y());
        BlockState mat = Earthworks.conjured(level.getBlockState(base), Blocks.DIRT.defaultBlockState());
        for (int i = 1; i <= (int) height; i++) {
            BlockPos at = BlockPos.containing(from.add(dir.scale(i - 0.5)));
            if (!level.getBlockState(at).canBeReplaced()) break;
            placeTemp(level, at, mat);
        }
    }

    private final java.util.Map<UUID, Integer> cooking = new java.util.HashMap<>();
    private Vec3 lamp;
    private BlockPos lampLight;
    private UUID caged;

    private boolean regional() {
        return (program.mods() & (SpellProgram.REGION_IN | SpellProgram.REGION_OUT | SpellProgram.REGION_SIDE)) != 0;
    }

    boolean allowed(Vec3 at) {
        int m = program.mods();
        if ((m & (SpellProgram.REGION_IN | SpellProgram.REGION_OUT | SpellProgram.REGION_SIDE)) == 0) return true;
        Vec3 n = program.normal();
        Vec3 rel = at.subtract(program.origin());
        rel = rel.subtract(n.scale(rel.dot(n)));
        if ((m & SpellProgram.REGION_SIDE) != 0) {
            double a = ((m >> SpellProgram.REGION_OCTANT) & 7) * Math.PI / 4;
            return rel.dot(frame.planeVector(Math.cos(a), Math.sin(a))) >= -0.3;
        }
        double d = rel.length(), ring = program.radius();
        boolean in = (m & SpellProgram.REGION_IN) != 0, out = (m & SpellProgram.REGION_OUT) != 0;
        if (in && out) return Math.abs(d - ring) <= 0.8;
        return in ? d <= ring + 0.3 : d >= ring - 0.3;
    }

    private void clearTemp(ServerLevel level, BlockPos pos) {
        if (pos == null) return;
        for (var it = temps.iterator(); it.hasNext(); ) {
            TempBlock t = it.next();
            if (!t.pos().equals(pos)) continue;
            if (level.getBlockState(pos) == t.placed()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            it.remove();
        }
        tempAt.remove(pos);
    }

    private Vec3 aboveGlyph(ServerLevel level, double height) {
        SpellProgram p = program;
        Vec3 from = p.origin().add(p.normal().scale(0.3));
        Vec3 to = p.origin().add(p.normal().scale(height));
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.BLOCK) to = hit.getLocation().subtract(p.normal().scale(0.8));
        return to.distanceTo(p.origin()) < 0.6 ? p.origin().add(p.normal().scale(0.6)) : to;
    }

    private Vec3 heldBeside(ServerLevel level) {
        LivingEntity c = casterEntity(level);
        if (c == null) return program.origin();
        Vec3 look = c.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        return c.getEyePosition().add(right.scale(0.7)).add(0, 0.35, 0);
    }

    private void pyreball(ServerLevel level) {
        SpellProgram p = program;
        Vec3 lean = p.direction().subtract(p.normal().scale(p.direction().dot(p.normal())));
        Vec3 ball = handheld ? heldBeside(level) : aboveGlyph(level, 1.2 + p.radius() * 0.3).add(lean.scale(0.8));
        double size = Math.min(0.8, 0.3 + p.radius() * 0.15);
        fxLength = 0;
        fxWidth = (float) size;
        level.sendParticles(ParticleTypes.FLAME, ball.x, ball.y, ball.z, 2, size * 0.4, size * 0.4, size * 0.4, 0.004);
        if (age % 4 == 0) level.sendParticles(ParticleTypes.SMALL_FLAME, ball.x, ball.y + size * 0.4, ball.z, 2, size * 0.3, 0.1, size * 0.3, 0.01);
        if (age % 30 == 1) level.playSound(null, ball.x, ball.y, ball.z, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.6f, 0.9f + level.random.nextFloat() * 0.2f);
        if (!handheld && (age == 1 || age % 40 == 0)) {
            BlockPos at = BlockPos.containing(ball);
            if (!at.equals(lampLight)) { clearTemp(level, lampLight); lampLight = at; }
            placeTemp(level, at, light(13));
        }
        AABB around = new AABB(ball, ball).inflate(size + 0.5);
        for (var it : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around.inflate(0.4))) {
            var input = new net.minecraft.world.item.crafting.SingleRecipeInput(it.getItem());
            var recipe = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CAMPFIRE_COOKING, input, level);
            if (recipe.isEmpty()) continue;
            it.setDeltaMovement(ball.subtract(it.position()).scale(0.2).add(0, 0.04, 0));
            it.hurtMarked = true;
            int done = cooking.merge(it.getUUID(), 1, Integer::sum);
            if (done % 10 == 0) level.sendParticles(ParticleTypes.SMOKE, it.getX(), it.getY() + 0.2, it.getZ(), 2, 0.05, 0.05, 0.05, 0.01);
            if (done >= Math.max(40, recipe.get().value().getCookingTime() / 4)) {
                ItemStack cooked = recipe.get().value().assemble(input, level.registryAccess());
                cooked.setCount(it.getItem().getCount());
                it.setItem(cooked);
                cooking.remove(it.getUUID());
                Vec3 out = it.position().subtract(ball);
                out = new Vec3(out.x, 0, out.z);
                out = out.lengthSqr() < 1e-4 ? new Vec3(0.2, 0, 0) : out.normalize().scale(0.25);
                it.setDeltaMovement(out.add(0, 0.2, 0));
                level.playSound(null, it.getX(), it.getY(), it.getZ(), SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.8f, 1.2f);
            }
        }
        if (age % 100 == 0) cooking.keySet().removeIf(id -> !(level.getEntity(id) instanceof net.minecraft.world.entity.item.ItemEntity));
        if (!p.gentle()) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(ball, ball).inflate(size), LivingEntity::isAlive)) {
                if (caster != null && e.getUUID().equals(caster)) continue;
                e.igniteForSeconds(3);
            }
        }
    }

    private void floatglow(ServerLevel level) {
        SpellProgram p = program;
        Vec3 home = handheld ? heldBeside(level) : aboveGlyph(level, Math.min(12, 2 + p.power() * 0.15 + p.radius() * 1.2));
        Vec3 target = home;
        if (!handheld && !p.focused()) {
            Player near = level.getNearestPlayer(home.x, home.y, home.z, 6, false);
            if (near != null) {
                Vec3 head = near.getEyePosition().add(0, 0.9, 0);
                Vec3 off = head.subtract(home);
                target = off.length() > 5 ? home.add(off.normalize().scale(5)) : head;
            }
            target = target.add(0, Math.sin(age * 0.05) * 0.15, 0);
        }
        lamp = lamp == null ? home : lamp.add(target.subtract(lamp).scale(handheld ? 0.5 : 0.08));
        fxLength = 0;
        fxWidth = (float) Math.min(0.6, 0.25 + p.radius() * 0.1);
        if (age % 2 == 0) level.sendParticles(ParticleTypes.END_ROD, lamp.x, lamp.y, lamp.z, 1, 0.12, 0.12, 0.12, 0.003);
        if (age % 6 == 0) level.sendParticles(ParticleTypes.GLOW, lamp.x, lamp.y, lamp.z, 1, 0.2, 0.2, 0.2, 0.0);
        if (age % 5 == 0) {
            BlockPos at = BlockPos.containing(lamp);
            if (!at.equals(lampLight)) {
                clearTemp(level, lampLight);
                lampLight = at;
            }
            placeTemp(level, at, light(Math.min(15, 10 + (int) (p.power() * 0.2))));
        }
    }

    private void waterRise(ServerLevel level) {
        SpellProgram p = program;
        double r = Math.max(0.7, p.radius() * 0.8);
        double h = handheld ? 3 : Math.min(16, 2 + p.power() * 0.15 + p.radius() * 1.2);
        double top = Math.min(h, age / 4.0);
        Vec3 o = handheld ? heldBeside(level).subtract(0, 1.5, 0) : p.origin();
        Vec3 n = handheld ? new Vec3(0, 1, 0) : p.normal();
        fxLength = (float) top;
        fxWidth = (float) r;
        for (int k = 0; k < 4; k++) {
            double a = level.random.nextDouble() * Math.PI * 2, rr = Math.sqrt(level.random.nextDouble()) * r, along = level.random.nextDouble() * top;
            Vec3 at = o.add(n.scale(along)).add(frame.planeVector(Math.cos(a), Math.sin(a)).scale(rr));
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, at.x, at.y, at.z, 1, 0, 0.1, 0, 0.1);
            if (k == 0) level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.05);
        }
        if (age % 3 == 0) {
            Vec3 crest = o.add(n.scale(top));
            level.sendParticles(ParticleTypes.FALLING_WATER, crest.x, crest.y, crest.z, 6, r * 0.6, 0.05, r * 0.6, 0);
        }
        if (age % 20 == 1) level.playSound(null, o.x, o.y, o.z, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.8f, 1f);
        AABB column = new AABB(o, o.add(n.scale(top + 0.5))).inflate(r);
        for (Entity e : level.getEntities((Entity) null, column, Entity::isAlive)) {
            Vec3 rel = e.position().subtract(o);
            double along = rel.dot(n);
            if (along < -0.5 || along > top + 0.5 || rel.subtract(n.scale(along)).length() > r) continue;
            Vec3 v = e.getDeltaMovement();
            double lift = along < top - 0.6 ? 0.32 : 0.08;
            e.setDeltaMovement(v.x * 0.8, Math.max(v.y, lift), v.z * 0.8);
            e.hurtMarked = true;
            e.resetFallDistance();
            e.clearFire();
        }
        if (age % 10 == 0 && !handheld) {
            BlockPos c = BlockPos.containing(o);
            int rr = (int) Math.ceil(r), hh = (int) Math.ceil(top);
            for (BlockPos bp : BlockPos.betweenClosed(c.offset(-rr, 0, -rr), c.offset(rr, hh, rr))) {
                if (level.getBlockState(bp).is(net.minecraft.tags.BlockTags.FIRE)) level.removeBlock(bp, false);
            }
        }
    }

    private void windWall(ServerLevel level) {
        SpellProgram p = program;
        double ring = Math.min(10, 1.5 + p.radius() * 1.2), height = Math.min(8, 3 + p.radius());
        Vec3 o = p.origin(), n = p.normal();
        fxLength = 0;
        fxWidth = 0;
        int around = (int) Math.max(10, ring * 6);
        for (int k = 0; k < around; k++) {
            double a = (k + level.random.nextDouble()) * Math.PI * 2 / around + age * 0.08, up = level.random.nextDouble() * height;
            Vec3 at = o.add(frame.planeVector(Math.cos(a), Math.sin(a)).scale(ring)).add(n.scale(up));
            Vec3 swirl = frame.planeVector(-Math.sin(a), Math.cos(a)).scale(0.06);
            level.sendParticles(k % 3 == 0 ? ParticleTypes.CLOUD : ParticleTypes.WHITE_ASH, at.x, at.y, at.z, 0, swirl.x, swirl.y + 0.02, swirl.z, 1);
        }
        if (age % 6 == 0) {
            double a = level.random.nextDouble() * Math.PI * 2;
            Vec3 at = o.add(frame.planeVector(Math.cos(a), Math.sin(a)).scale(ring)).add(n.scale(level.random.nextDouble() * height));
            level.sendParticles(ParticleTypes.SMALL_GUST, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        if (age % 30 == 1) level.playSound(null, o.x, o.y, o.z, SoundEvents.BREEZE_IDLE_AIR, SoundSource.BLOCKS, 1f, 0.6f);
        AABB box = new AABB(o, o.add(n.scale(height))).inflate(ring + 2);
        for (Entity e : level.getEntities((Entity) null, box, Entity::isAlive)) {
            if (caster != null && e.getUUID().equals(caster)) continue;
            if (e instanceof net.minecraft.world.entity.TamableAnimal t && caster != null && caster.equals(t.getOwnerUUID())) continue;
            Vec3 now = e.position().subtract(o), was = new Vec3(e.xo, e.yo, e.zo).subtract(o);
            double along = now.dot(n);
            if (along < -1 || along > height) continue;
            double dNow = now.subtract(n.scale(along)).length(), dWas = was.subtract(n.scale(was.dot(n))).length();
            boolean inside = dWas < ring;
            boolean near = inside ? dNow > ring - 0.7 : dNow < ring + 0.7;
            if (!near) continue;
            Vec3 radial = now.subtract(n.scale(along));
            radial = radial.lengthSqr() < 1e-4 ? frame.planeVector(1, 0) : radial.normalize();
            Vec3 back = radial.scale(inside ? -0.45 : 0.45);
            if (inside ? dNow > ring - 0.2 : dNow < ring + 0.2) {
                Vec3 keep = o.add(n.scale(along)).add(radial.scale(inside ? ring - 0.6 : ring + 0.6));
                e.teleportTo(keep.x, keep.y, keep.z);
            }
            if (e instanceof net.minecraft.world.entity.projectile.Projectile) {
                e.setDeltaMovement(back.scale(0.2).add(0, -0.1, 0));
            } else {
                e.setDeltaMovement(e.getDeltaMovement().multiply(0.2, 1, 0.2).add(back));
            }
            e.hurtMarked = true;
        }
    }

    private void wallBend(ServerLevel level) {
        SpellProgram p = program;
        if (plane == null) return;
        Vec3 n = p.normal();
        Vec3 along = p.direction().subtract(n.scale(p.direction().dot(n)));
        if (along.lengthSqr() < 1e-4) return;
        along = along.normalize();
        int length = (int) Math.min(16, 3 + 2 * p.radius() + 0.1 * p.power());
        int height = (int) Math.min(6, 2 + p.radius());
        int step = age / 4;
        fxLength = step;
        fxWidth = height;
        if (age % 4 != 0 || step < 1 || step > length) return;
        BlockPos under = BlockPos.containing(p.origin().subtract(n.scale(0.5)));
        BlockState mat = Earthworks.conjured(level.getBlockState(under), Blocks.PACKED_MUD.defaultBlockState());
        Vec3 base = p.origin().add(along.scale(step));
        boolean any = false;
        for (int j = 0; j < height; j++) {
            BlockPos at = BlockPos.containing(base.add(n.scale(j + 0.5)));
            if (!level.getBlockState(at).canBeReplaced() || !Earthworks.mayChange(level, at, caster, false)) continue;
            if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(at)).isEmpty()) continue;
            placeTemp(level, at, mat);
            any = true;
        }
        if (any) {
            Vec3 s = base.add(n.scale(0.5));
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, mat), s.x, s.y, s.z, 10, 0.3, 0.3, 0.3, 0.05);
            level.playSound(null, s.x, s.y, s.z, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.8f, 0.7f);
        }
    }

    private UUID steed;

    private void waterHorse(ServerLevel level) {
        SpellProgram p = program;
        fxLength = 0;
        fxWidth = (float) p.radius();
        var horse = steed == null ? null : level.getEntity(steed) instanceof com.fimkov.witchsatelier.entity.WaterHorseEntity w && w.isAlive() ? w : null;
        if (horse == null && steed != null) {
            ticksLeft = 1;
            return;
        }
        if (horse == null) {
            horse = com.fimkov.witchsatelier.registry.ModEntities.WATER_HORSE.get().create(level);
            if (horse == null) return;
            Vec3 at = handheld ? casterEntity(level) == null ? p.origin() : casterEntity(level).position() : p.origin().add(p.normal().scale(0.2));
            horse.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
            horse.conjure(caster == null ? null : level.getPlayerByUUID(caster));
            level.addFreshEntity(horse);
            steed = horse.getUUID();
            level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 1, at.z, 40, 0.6, 0.6, 0.6, 0.2);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 1f, 1.3f);
        }
    }

    private void waterCage(ServerLevel level) {
        SpellProgram p = program;
        Vec3 c = p.origin().add(p.normal().scale(1.4));
        double reach = 2 + p.radius() * 1.5;
        fxLength = 0;
        fxWidth = (float) Math.min(1.4, 0.8 + p.radius() * 0.2);
        LivingEntity target = caged == null ? null : level.getEntity(caged) instanceof LivingEntity le && le.isAlive() ? le : null;
        if (target == null || target.distanceToSqr(c) > (reach + 3) * (reach + 3)) {
            target = null;
            caged = null;
            double best = reach * reach;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(reach), LivingEntity::isAlive)) {
                if (caster != null && e.getUUID().equals(caster) || e.isSpectator()) continue;
                double d = e.distanceToSqr(c);
                if (d < best) { best = d; target = e; }
            }
            if (target != null) {
                caged = target.getUUID();
                level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1f, 0.8f);
            }
        }
        Vec3 hold = target == null ? c : c.subtract(0, target.getBbHeight() / 2, 0);
        Vec3 mid = target == null ? c : target.getBoundingBox().getCenter();
        double size = target == null ? 0.7 : Math.max(0.8, target.getBbWidth() * 0.8 + 0.5);
        for (int k = 0; k < 6; k++) {
            double a = level.random.nextDouble() * Math.PI * 2, b = Math.acos(2 * level.random.nextDouble() - 1);
            Vec3 at = mid.add(Math.sin(b) * Math.cos(a) * size, Math.cos(b) * size, Math.sin(b) * Math.sin(a) * size);
            level.sendParticles(k % 2 == 0 ? ParticleTypes.DRIPPING_WATER : ParticleTypes.BUBBLE, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        if (target == null) return;
        if (target.position().distanceTo(hold) > size + 0.4) target.teleportTo(hold.x, hold.y, hold.z);
        target.setDeltaMovement(hold.subtract(target.position()).scale(0.3));
        target.hurtMarked = true;
        target.resetFallDistance();
        target.clearFire();
        if (target instanceof net.minecraft.world.entity.Mob m) m.getNavigation().stop();
        if (!target.canBreatheUnderwater()) {
            target.setAirSupply(Math.max(-20, target.getAirSupply() - 5));
            if (target.getAirSupply() <= -20 && age % 20 == 0) target.hurt(level.damageSources().drown(), 2f);
        }
        if (target.isSensitiveToWater() && age % 10 == 0) target.hurt(level.damageSources().drown(), 1f);
        if (age % 20 == 0) com.fimkov.witchsatelier.glyph.GlyphService.washArea(level, mid, size + 0.5, strokes);
    }

    private static BlockState light(int lvl) {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, Math.max(0, Math.min(15, lvl)));
    }

    private static void melt(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.is(Blocks.SNOW) || s.is(Blocks.POWDER_SNOW)) level.removeBlock(pos, false);
        else if (s.is(Blocks.ICE)) level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        else if (s.is(Blocks.SNOW_BLOCK)) level.removeBlock(pos, false);
    }

    private static void douse(ServerLevel level, BlockPos hit, BlockPos front) {
        for (BlockPos bp : new BlockPos[]{hit, front, hit.above()}) {
            BlockState s = level.getBlockState(bp);
            if (s.is(BlockTags.FIRE)) level.removeBlock(bp, false);
            else if (s.getFluidState().is(Fluids.LAVA)) level.setBlock(bp, Blocks.OBSIDIAN.defaultBlockState(), 3);
            else if (s.getBlock() instanceof FarmBlock && s.getValue(FarmBlock.MOISTURE) < 7) level.setBlock(bp, s.setValue(FarmBlock.MOISTURE, 7), 3);
        }
    }

    static void burstParticles(ServerLevel level, Vec3 o, ParticleOptions part, int count, double spread) {
        level.sendParticles(part, o.x, o.y, o.z, count, spread, spread * 0.5, spread, 0.05);
    }
}
