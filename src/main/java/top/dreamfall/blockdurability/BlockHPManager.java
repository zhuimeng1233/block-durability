package top.dreamfall.blockdurability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 方块耐久管理。白名单制（支持 Tag）+ 硬度计算血量。
 */
public class BlockHPManager {

    private static final ConcurrentHashMap<BlockPos, DamageState> damagedBlocks = new ConcurrentHashMap<>();
    private static int nextCrackId = 0;

    /** 预编译：Tag 白名单 */
    private static final List<TagKey<Block>> tagWhitelist = new ArrayList<>();
    /** 预编译：名字白名单（精确 & 通配符） */
    private static final List<String> nameWhitelist = new ArrayList<>();
    /** 预编译的 HP 覆盖：名字 pattern → hp */
    private static final Map<String, Float> hpOverridePatterns = new LinkedHashMap<>();
    private static boolean compiled = false;

    private record DamageState(float currentHP, float maxHP, int crackId) {}

    // ==================== 公开 API ====================

    /**
     * @return 方块是否在白名单中（可被破坏）
     */
    public static boolean isBreakable(BlockState state) {
        compileIfNeeded();

        // Tag 匹配（O(1)，优先）
        for (TagKey<Block> tag : tagWhitelist) {
            if (state.is(tag)) return true;
        }

        // 名字匹配（glob & 精确）
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        for (String pattern : nameWhitelist) {
            if (matchesGlob(id, pattern)) return true;
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

        DamageState existing = damagedBlocks.get(pos);
        float maxHP = getMaxHP(state);
        float currentHP;

        if (existing != null) {
            if (Math.abs(existing.maxHP - maxHP) > 0.01f) {
                clearCrackOverlay(serverLevel, pos, existing.crackId);
                damagedBlocks.remove(pos);
                existing = null;
            }
        }

        currentHP = (existing != null) ? existing.currentHP : maxHP;
        float newHP = currentHP - damage;

        if (newHP <= 0) {
            damagedBlocks.remove(pos);
            if (existing != null) clearCrackOverlay(serverLevel, pos, existing.crackId);
            level.destroyBlock(pos, BlockHPConfig.DROP_ITEMS.get());
            return Optional.empty();
        }

        int crackId = (existing != null) ? existing.crackId : allocateCrackId();
        damagedBlocks.put(pos, new DamageState(newHP, maxHP, crackId));
        sendCrackProgress(serverLevel, pos, crackId, newHP, maxHP);
        return Optional.of(newHP);
    }

    /**
     * 获取方块最大 HP，基于硬度公式。
     */
    public static float getMaxHP(BlockState state) {
        compileIfNeeded();
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        for (var entry : hpOverridePatterns.entrySet()) {
            if (matchesGlob(id, entry.getKey())) {
                return entry.getValue();
            }
        }

        float hardness = state.getBlock().defaultDestroyTime();
        return Math.max(
                BlockHPConfig.MIN_BLOCK_HP.get().floatValue(),
                hardness * BlockHPConfig.HARDNESS_MULTIPLIER.get().floatValue()
        );
    }

    public static float getCurrentHP(BlockPos pos, BlockState state) {
        DamageState existing = damagedBlocks.get(pos);
        return (existing != null) ? existing.currentHP : getMaxHP(state);
    }

    public static void onBlockRemoved(BlockPos pos) {
        damagedBlocks.remove(pos);
    }

    public static void clearAll() {
        damagedBlocks.clear();
    }

    // ==================== 内部 ====================

    private static synchronized int allocateCrackId() {
        return nextCrackId++;
    }

    private static void sendCrackProgress(ServerLevel level, BlockPos pos, int crackId,
                                           float currentHP, float maxHP) {
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
        compiled = true;

        tagWhitelist.clear();
        nameWhitelist.clear();

        for (String entry : BlockHPConfig.BLOCK_WHITELIST.get()) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("tag:")) {
                // Tag 匹配：tag:namespace:path
                String tagId = trimmed.substring(4);
                ResourceLocation rl = ResourceLocation.parse(tagId);
                tagWhitelist.add(TagKey.create(Registries.BLOCK, rl));
            } else {
                // 名字匹配（精确 / 通配符）
                nameWhitelist.add(trimmed);
            }
        }

        // 编译 HP 覆盖
        hpOverridePatterns.clear();
        for (String entry : BlockHPConfig.BLOCK_HP_OVERRIDES.get()) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) continue;
            String key = parts[0].trim();
            try {
                float hp = Float.parseFloat(parts[1].trim());
                if (!key.isEmpty()) hpOverridePatterns.put(key, hp);
            } catch (NumberFormatException ignored) {}
        }
    }

    /**
     * 通配符匹配：* 匹配任意字符序列。
     */
    private static boolean matchesGlob(String input, String pattern) {
        String[] parts = pattern.split("\\*", -1);
        int idx = 0;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                if (i == parts.length - 1) return true;
                continue;
            }
            int found = input.indexOf(part, idx);
            if (found < 0) return false;
            idx = found + part.length();
        }
        return parts[parts.length - 1].isEmpty() || idx == input.length();
    }
}
