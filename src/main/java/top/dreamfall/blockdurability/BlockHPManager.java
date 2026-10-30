package top.dreamfall.blockdurability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 方块耐久管理。白名单制（支持 Tag）+ 硬度计算血量。
 * 具备多维度安全隔离与超时自动愈合/内存清理机制。
 */
public class BlockHPManager {

    public record BlockKey(ResourceKey<Level> dimension, BlockPos pos) {}

    private record DamageState(float currentHP, float maxHP, int crackId, long lastHitTime) {}

    private record HPOverride(Pattern pattern, float hp) {}

    private static final ConcurrentHashMap<BlockKey, DamageState> damagedBlocks = new ConcurrentHashMap<>();
    private static int nextCrackId = 0;

    /** 预编译：Tag 白名单 */
    private static final List<TagKey<Block>> tagWhitelist = new ArrayList<>();
    /** 预编译：名字白名单正则表达式 */
    private static final List<Pattern> nameWhitelistPatterns = new ArrayList<>();
    /** 预编译的 HP 覆盖：Pattern → hp */
    private static final List<HPOverride> hpOverrides = new ArrayList<>();
    private static volatile boolean compiled = false;

    // ==================== 公开 API ====================

    /**
     * 重置预编译缓存（配置热重载时调用）
     */
    public static synchronized void invalidateCache() {
        compiled = false;
        BlockDurability.LOGGER.info("Block Durability cache invalidated due to config reload.");
    }

    /**
     * @return 方块是否在白名单中（可被破坏）
     */
    public static boolean isBreakable(BlockState state) {
        compileIfNeeded();

        // Tag 匹配（O(1)，优先）
        for (TagKey<Block> tag : tagWhitelist) {
            if (state.is(tag)) return true;
        }

        // 名字匹配（正则 & 通配符）
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        for (Pattern pattern : nameWhitelistPatterns) {
            if (pattern.matcher(id).matches()) return true;
        }
        return false;
    }

    /**
     * 对方块造成伤害。不在白名单的方块会被忽略。
     * @return Optional.empty() = 方块已破坏；否则返回剩余 HP。
     */
    public static Optional<Float> damageBlock(Level level, BlockPos pos, BlockState state, float damage) {
        if (!(level instanceof ServerLevel serverLevel)) return Optional.of(0f);
        if (!isBreakable(state)) return Optional.of(0f);
        if (!Float.isFinite(damage) || damage <= 0) return Optional.of(0f);

        BlockKey key = new BlockKey(level.dimension(), pos.immutable());
        DamageState existing = damagedBlocks.get(key);
        float maxHP = getMaxHP(state);
        float currentHP;

        if (existing != null) {
            if (Math.abs(existing.maxHP - maxHP) > 0.01f) {
                clearCrackOverlay(serverLevel, pos, existing.crackId);
                damagedBlocks.remove(key);
                existing = null;
            }
        }

        currentHP = (existing != null) ? existing.currentHP : maxHP;
        float newHP = currentHP - damage;

        if (newHP <= 0) {
            damagedBlocks.remove(key);
            if (existing != null) clearCrackOverlay(serverLevel, pos, existing.crackId);
            level.destroyBlock(pos, BlockHPConfig.DROP_ITEMS.get());
            return Optional.empty();
        }

        int crackId = (existing != null) ? existing.crackId : allocateCrackId();
        damagedBlocks.put(key, new DamageState(newHP, maxHP, crackId, System.currentTimeMillis()));
        sendCrackProgress(serverLevel, pos, crackId, newHP, maxHP);
        return Optional.of(newHP);
    }

    /**
     * 获取方块最大 HP，优先采用覆盖项，否则基于硬度公式。
     */
    public static float getMaxHP(BlockState state) {
        compileIfNeeded();
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        for (HPOverride override : hpOverrides) {
            if (override.pattern().matcher(id).matches()) {
                return override.hp();
            }
        }

        float hardness = state.getBlock().defaultDestroyTime();
        float multiplier = BlockHPConfig.HARDNESS_MULTIPLIER.get().floatValue();
        float minHP = BlockHPConfig.MIN_BLOCK_HP.get().floatValue();
        float computed = hardness * multiplier;
        if (!Float.isFinite(computed) || computed < minHP) {
            return minHP;
        }
        return computed;
    }

    public static float getCurrentHP(Level level, BlockPos pos, BlockState state) {
        DamageState existing = damagedBlocks.get(new BlockKey(level.dimension(), pos));
        return (existing != null) ? existing.currentHP : getMaxHP(state);
    }

    public static void onBlockRemoved(Level level, BlockPos pos) {
        damagedBlocks.remove(new BlockKey(level.dimension(), pos));
    }

    public static void clearAll() {
        damagedBlocks.clear();
    }

    /**
     * 定时自愈与内存清理：方块在设定时间内未受伤害则清除裂纹并回收内存。
     * 支持通过配置文件或 /gamerule blockDurabilityHealSeconds 动态调整。
     */
    public static void tick(ServerLevel level) {
        int timeoutSeconds = BlockHPConfig.HEAL_TIMEOUT_SECONDS.get();
        if (level.getGameRules() != null) {
            int ruleVal = level.getGameRules().getInt(BlockDurability.RULE_BLOCK_HEAL_SECONDS);
            if (ruleVal != 120) {
                timeoutSeconds = ruleVal;
            }
        }
        if (timeoutSeconds <= 0) {
            return; // 0 或负数：关闭自愈，残血裂纹一直保留
        }

        long now = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        damagedBlocks.entrySet().removeIf(entry -> {
            if (!entry.getKey().dimension().equals(level.dimension())) return false;
            DamageState ds = entry.getValue();
            if (now - ds.lastHitTime() > timeoutMs) {
                clearCrackOverlay(level, entry.getKey().pos(), ds.crackId());
                return true;
            }
            return false;
        });
    }

    /**
     * 区块卸载时清理对应的裂纹缓存，防止内存泄漏。
     */
    public static void onChunkUnload(ServerLevel level, ChunkPos chunkPos) {
        damagedBlocks.entrySet().removeIf(entry -> {
            if (!entry.getKey().dimension().equals(level.dimension())) return false;
            BlockPos p = entry.getKey().pos();
            if ((p.getX() >> 4) == chunkPos.x && (p.getZ() >> 4) == chunkPos.z) {
                clearCrackOverlay(level, p, entry.getValue().crackId());
                return true;
            }
            return false;
        });
    }

    // ==================== 内部 ====================

    private static synchronized int allocateCrackId() {
        return nextCrackId++;
    }

    private static void sendCrackProgress(ServerLevel level, BlockPos pos, int crackId,
                                           float currentHP, float maxHP) {
        if (maxHP <= 0f) return;
        float ratio = 1f - (currentHP / maxHP);
        int progress = Math.clamp((int) (ratio * 10), 0, 9);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 1024) {
                player.connection.send(
                        new net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket(
                                crackId, pos, progress));
            }
        }
    }

    private static void clearCrackOverlay(ServerLevel level, BlockPos pos, int crackId) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 1024) {
                player.connection.send(
                        new net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket(
                                crackId, pos, -1));
            }
        }
    }

    private static synchronized void compileIfNeeded() {
        if (compiled) return;

        tagWhitelist.clear();
        nameWhitelistPatterns.clear();

        for (String entry : BlockHPConfig.BLOCK_WHITELIST.get()) {
            if (entry == null) continue;
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("tag:")) {
                // Tag 匹配：tag:namespace:path
                String tagId = trimmed.substring(4).trim();
                ResourceLocation rl = tagId.isEmpty() ? null : ResourceLocation.tryParse(tagId);
                if (rl != null && !rl.getPath().isEmpty()) {
                    tagWhitelist.add(TagKey.create(Registries.BLOCK, rl));
                } else {
                    BlockDurability.LOGGER.warn("Invalid tag in block whitelist: '{}'. Skipping.", trimmed);
                }
            } else {
                // 名字匹配（精确 / 通配符）
                nameWhitelistPatterns.add(globToPattern(trimmed));
            }
        }

        // 编译 HP 覆盖
        hpOverrides.clear();
        for (String entry : BlockHPConfig.BLOCK_HP_OVERRIDES.get()) {
            if (entry == null) continue;
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) {
                BlockDurability.LOGGER.warn("Invalid format in HP override: '{}' (expected 'modid:block=hp'). Skipping.", entry);
                continue;
            }
            String key = parts[0].trim();
            if (key.isEmpty()) {
                BlockDurability.LOGGER.warn("Empty pattern in HP override: '{}'. Skipping.", entry);
                continue;
            }
            try {
                float hp = Float.parseFloat(parts[1].trim());
                if (!Float.isFinite(hp) || hp <= 0f) {
                    BlockDurability.LOGGER.warn("Invalid HP value in override: '{}' (must be a finite positive number). Skipping.", entry);
                    continue;
                }
                hpOverrides.add(new HPOverride(globToPattern(key), hp));
            } catch (NumberFormatException e) {
                BlockDurability.LOGGER.warn("Failed to parse HP number in override: '{}'. Skipping.", entry);
            }
        }

        compiled = true;
    }

    /**
     * 将通配符 glob 转为正则表达式 Pattern
     */
    public static Pattern globToPattern(String glob) {
        String regex = "^" + Pattern.quote(glob).replace("*", "\\E.*\\Q") + "$";
        return Pattern.compile(regex);
    }
}
