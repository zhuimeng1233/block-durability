package top.dreamfall.blockdurability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 核心事件处理器：监听爆炸（全模组通用 + TaCZ 爆炸）、自愈与区块生命周期。
 * 不依赖任何 TaCZ 特有类，无 TaCZ 时亦可独立稳健运行。
 */
public class BlockHPEventHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!BlockHPConfig.ENABLED.get() || !BlockHPConfig.EXPLOSION_ENABLED.get()) return;

        Level level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;

        Explosion explosion = event.getExplosion();
        Entity directSource = explosion.getDirectSourceEntity();

        // 识别是否为 TaCZ 爆炸
        boolean isTacz = isTaczExplosion(directSource, explosion);
        if (!isTacz && !BlockHPConfig.HANDLE_VANILLA_EXPLOSIONS.get()) {
            return;
        }

        Vec3 center = explosion.center();
        float radius = explosion.radius();
        float maxRadius = BlockHPConfig.EXPLOSION_MAX_RADIUS.get().floatValue();
        float effectiveRadius = Math.min(radius, maxRadius);
        if (effectiveRadius <= 0f) return;

        // 原版已被选定直接摧毁的方块集合（双轨制：原版该炸掉的维持原版行为）
        List<BlockPos> affectedBlocks = event.getAffectedBlocks();
        Set<BlockPos> vanillaDestroyed = new HashSet<>(affectedBlocks);

        int minX = (int) Math.floor(center.x - effectiveRadius);
        int maxX = (int) Math.ceil(center.x + effectiveRadius);
        int minY = Math.max(serverLevel.getMinBuildHeight(), (int) Math.floor(center.y - effectiveRadius));
        int maxY = Math.min(serverLevel.getMaxBuildHeight(), (int) Math.ceil(center.y + effectiveRadius));
        int minZ = (int) Math.floor(center.z - effectiveRadius);
        int maxZ = (int) Math.ceil(center.z + effectiveRadius);

        float multiplier = BlockHPConfig.EXPLOSION_DAMAGE_MULTIPLIER.get().floatValue();
        float baseDamage = effectiveRadius * 4.0f * multiplier;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    // 已被原版炸掉的，不需要重复扣耐久
                    if (vanillaDestroyed.contains(cursor)) continue;

                    double dx = (x + 0.5) - center.x;
                    double dy = (y + 0.5) - center.y;
                    double dz = (z + 0.5) - center.z;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > effectiveRadius * effectiveRadius) continue;

                    BlockState state = serverLevel.getBlockState(cursor);
                    if (state.isAir()) continue;
                    if (!BlockHPManager.isBreakable(state)) continue;

                    // 视线阻挡检测（如果隔着未破坏的实心厚掩体，阻挡冲击波）
                    Vec3 targetCenter = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    BlockHitResult hit = serverLevel.clip(new ClipContext(
                            center,
                            targetCenter,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            CollisionContext.empty()
                    ));
                    if (hit.getType() != HitResult.Type.MISS
                            && !hit.getBlockPos().equals(cursor)
                            && !vanillaDestroyed.contains(hit.getBlockPos())) {
                        continue;
                    }

                    double dist = Math.sqrt(distSq);
                    // 线性距离衰减
                    float falloff = (float) Math.max(0.0f, 1.0f - (dist / effectiveRadius));
                    float damage = baseDamage * falloff;
                    if (!Float.isFinite(damage) || damage <= 0.1f) continue;

                    BlockPos immutablePos = cursor.immutable();
                    Optional<Float> remaining = BlockHPManager.damageBlock(serverLevel, immutablePos, state, damage);
                    if (remaining.isEmpty()) {
                        spawnBreakEffects(serverLevel, immutablePos, state);
                    } else if (remaining.get() > 0) {
                        float maxHP = BlockHPManager.getMaxHP(state);
                        float ratio = maxHP > 0 ? (1f - (remaining.get() / maxHP)) : 0f;
                        spawnHitEffects(serverLevel, immutablePos, state, targetCenter, ratio);
                    }
                }
            }
        }

        // --- 物理载具/物理化方块残存波及扣血（机械动力 Contraption / 航空学物理化方块 / 飞艇等） ---
        handlePhysicsVehicleExplosion(serverLevel, center, effectiveRadius, baseDamage, vanillaDestroyed);
    }

    /**
     * 针对物理载具（机械动力 Contraption / 航空学 Sable 物理化方块 / 飞艇等）执行残存波及耐久伤害。
     * 原版/物理引擎直接摧毁的方块保持放行；仅对幸存的白名单方块按距离衰减扣血。
     */
    private void handlePhysicsVehicleExplosion(ServerLevel serverLevel, Vec3 center, float effectiveRadius,
                                               float baseDamage, Set<BlockPos> vanillaDestroyed) {
        AABB explosionBox = new AABB(
                center.x - effectiveRadius, center.y - effectiveRadius, center.z - effectiveRadius,
                center.x + effectiveRadius, center.y + effectiveRadius, center.z + effectiveRadius
        );

        List<Entity> vehicles = serverLevel.getEntities((Entity) null, explosionBox,
                e -> isPhysicsVehicle(e) && e.getBoundingBox().intersects(explosionBox));
        if (vehicles.isEmpty()) return;

        Set<BlockPos> processedPositions = new HashSet<>();

        for (Entity vehicle : vehicles) {
            AABB vehicleBox = vehicle.getBoundingBox().intersect(explosionBox);
            if (vehicleBox == null) continue;

            // 采样探测：在载具与爆炸交集的包围盒内生成探测采样点
            double step = Math.max(1.0, Math.min(2.0, effectiveRadius / 4.0));
            int processedCount = 0;
            final int maxProcessedPerVehicle = 64; // 性能上限硬安全锁，防极端情况卡服

            for (double x = vehicleBox.minX + 0.5; x <= vehicleBox.maxX; x += step) {
                for (double y = vehicleBox.minY + 0.5; y <= vehicleBox.maxY; y += step) {
                    for (double z = vehicleBox.minZ + 0.5; z <= vehicleBox.maxZ; z += step) {
                        if (processedCount >= maxProcessedPerVehicle) break;

                        Vec3 target = new Vec3(x, y, z);
                        double distSq = center.distanceToSqr(target);
                        if (distSq > effectiveRadius * effectiveRadius) continue;

                        BlockHitResult hit = serverLevel.clip(new ClipContext(
                                center,
                                target,
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                CollisionContext.empty()
                        ));

                        if (hit.getType() != HitResult.Type.BLOCK) continue;

                        BlockPos hitPos = hit.getBlockPos();
                        // 已被原版/模组直接炸碎的放行，不重复扣耐久；同一个方块在同一次爆炸中只结算一次
                        if (vanillaDestroyed.contains(hitPos) || !processedPositions.add(hitPos)) {
                            continue;
                        }

                        BlockState state = serverLevel.getBlockState(hitPos);
                        if (state.isAir() || !BlockHPManager.isBreakable(state)) continue;

                        double actualDist = Math.sqrt(center.distanceToSqr(hit.getLocation()));
                        if (actualDist > effectiveRadius) continue;

                        float falloff = (float) Math.max(0.0f, 1.0f - (actualDist / effectiveRadius));
                        float damage = baseDamage * falloff;
                        if (!Float.isFinite(damage) || damage <= 0.1f) continue;

                        processedCount++;
                        Optional<Float> remaining = BlockHPManager.damageBlock(serverLevel, hitPos, state, damage);
                        if (remaining.isEmpty()) {
                            spawnBreakEffects(serverLevel, hitPos, state);
                        } else if (remaining.get() > 0) {
                            float maxHP = BlockHPManager.getMaxHP(state);
                            float ratio = maxHP > 0 ? (1f - (remaining.get() / maxHP)) : 0f;
                            spawnHitEffects(serverLevel, hitPos, state, hit.getLocation(), ratio);
                        }
                    }
                }
            }
        }
    }

    private static boolean isPhysicsVehicle(Entity entity) {
        if (entity == null || entity instanceof LivingEntity || entity instanceof Projectile || entity instanceof ItemEntity) {
            return false;
        }
        String className = entity.getClass().getName();
        return className.contains("Contraption")
                || className.contains("Ship")
                || className.contains("SubLevel")
                || className.contains("Physics")
                || className.startsWith("dev.ryanhcode.sable")
                || className.startsWith("com.simibubi.create")
                || className.startsWith("org.valkyrienskies");
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            // 每 2 秒 (40 ticks) 检查一次残血方块的自愈与内存清理
            if (serverLevel.getGameTime() % 40 == 0) {
                BlockHPManager.tick(serverLevel);
            }
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            BlockHPManager.onChunkUnload(serverLevel, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            BlockHPManager.clearBlock(serverLevel, event.getPos());
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            BlockHPManager.clearBlock(serverLevel, event.getPos());
        }
    }

    public static void spawnBreakEffects(ServerLevel level, BlockPos pos, BlockState state) {
        if (BlockHPConfig.SPAWN_BREAK_PARTICLES.get()) {
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    20, 0.25, 0.25, 0.25, 0.5);
        }
        if (BlockHPConfig.PLAY_BREAK_SOUND.get()) {
            level.playSound(null, pos,
                    state.getSoundType().getBreakSound(),
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    public static void spawnHitEffects(ServerLevel level, BlockPos pos, BlockState state,
                                       Vec3 hitVec, float damageRatio) {
        if (BlockHPConfig.SPAWN_BREAK_PARTICLES.get()) {
            int count = 1 + (int) (damageRatio * 8);
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, state),
                    hitVec.x, hitVec.y, hitVec.z,
                    count, 0.1, 0.1, 0.1, 0.2);
        }
        if (BlockHPConfig.PLAY_BREAK_SOUND.get()) {
            level.playSound(null, pos,
                    state.getSoundType().getHitSound(),
                    SoundSource.BLOCKS,
                    0.5F + damageRatio * 0.5F,
                    0.8F + level.random.nextFloat() * 0.4F);
        }
    }

    private static boolean isTaczExplosion(Entity directSource, Explosion explosion) {
        if (directSource != null && directSource.getClass().getName().contains("com.tacz")) {
            return true;
        }
        return explosion != null && explosion.getClass().getName().contains("com.tacz");
    }
}
