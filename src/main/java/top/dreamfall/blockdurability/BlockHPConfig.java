package top.dreamfall.blockdurability;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BlockHPConfig {
    public static final ForgeConfigSpec SPEC;
    public static final Config CONFIG;

    // ========================
    // 通用
    // ========================
    public static ForgeConfigSpec.BooleanValue ENABLED;
    public static ForgeConfigSpec.DoubleValue DAMAGE_PER_HIT;
    public static ForgeConfigSpec.BooleanValue USE_BULLET_DAMAGE;
    public static ForgeConfigSpec.DoubleValue BULLET_DAMAGE_MULTIPLIER;
    public static ForgeConfigSpec.IntValue HEAL_TIMEOUT_SECONDS;

    // ========================
    // 白名单：只有匹配的方块才能被破坏
    // ========================
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_WHITELIST;

    // ========================
    // 血量计算：基于方块的硬度（与爆炸抗性正相关）
    // ========================
    public static ForgeConfigSpec.DoubleValue HARDNESS_MULTIPLIER;
    public static ForgeConfigSpec.DoubleValue MIN_BLOCK_HP;

    // ========================
    // 可选的按方块 HP 覆盖
    // ========================
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_HP_OVERRIDES;

    // ========================
    // 爆炸耐久机制（TaCZ 爆炸、原版 TNT、苦力怕、模组爆炸）
    // ========================
    public static ForgeConfigSpec.BooleanValue EXPLOSION_ENABLED;
    public static ForgeConfigSpec.DoubleValue EXPLOSION_DAMAGE_MULTIPLIER;
    public static ForgeConfigSpec.DoubleValue EXPLOSION_MAX_RADIUS;
    public static ForgeConfigSpec.BooleanValue HANDLE_VANILLA_EXPLOSIONS;

    // ========================
    // 特效
    // ========================
    public static ForgeConfigSpec.BooleanValue SPAWN_BREAK_PARTICLES;
    public static ForgeConfigSpec.BooleanValue PLAY_BREAK_SOUND;
    public static ForgeConfigSpec.BooleanValue DROP_ITEMS;

    static {
        Pair<Config, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder()
                .configure(BlockHPConfig::build);
        SPEC = pair.getRight();
        CONFIG = pair.getLeft();
    }

    private static Config build(ForgeConfigSpec.Builder builder) {
        builder.translation("blockdurability.configuration.section.general").push("general");
        ENABLED = builder
                .comment("Enable the block durability system")
                .translation("blockdurability.configuration.general.enabled")
                .define("enabled", true);
        DAMAGE_PER_HIT = builder
                .comment("Damage dealt to a block per bullet hit (used when useBulletDamage is false)")
                .translation("blockdurability.configuration.general.damagePerHit")
                .defineInRange("damagePerHit", 1.0, 0.1, 100.0);
        USE_BULLET_DAMAGE = builder
                .comment("If true, use the bullet's entity damage * bulletDamageMultiplier as block damage")
                .translation("blockdurability.configuration.general.useBulletDamage")
                .define("useBulletDamage", false);
        BULLET_DAMAGE_MULTIPLIER = builder
                .comment("Multiplier when useBulletDamage is true")
                .translation("blockdurability.configuration.general.bulletDamageMultiplier")
                .defineInRange("bulletDamageMultiplier", 1.0, 0.0, 100.0);
        HEAL_TIMEOUT_SECONDS = builder
                .comment("Seconds of inactivity before a damaged block heals and clears cracks (0 to disable auto-healing)")
                .translation("blockdurability.configuration.general.healTimeoutSeconds")
                .defineInRange("healTimeoutSeconds", 120, 0, 86400);
        builder.pop();

        builder.translation("blockdurability.configuration.section.whitelist").push("whitelist");
        BLOCK_WHITELIST = builder
                .comment(
                        "Whitelist: only blocks matching these patterns can be damaged by bullets and explosions.",
                        "Three formats supported:",
                        "  \"tag:namespace:path\"  → Block tag matching (auto-compatible with all mods!)",
                        "  \"modid:block_id\"     → Exact name match",
                        "  \"modid:*pattern*\"    → Wildcard name match",
                        "Blocks NOT in this list will ignore block durability damage entirely.",
                        "",
                        "Default covers: glass, wood planks, logs, wooden doors/trapdoors/fences"
                )
                .translation("blockdurability.configuration.whitelist.blockWhitelist")
                .defineListAllowEmpty("blockWhitelist",
                        Arrays.asList(
                                // === 玻璃（Common Tags，兼容所有模组） ===
                                "tag:c:glass_blocks",
                                "tag:c:glass_panes",
                                // === 木板 ===
                                "tag:minecraft:planks",
                                // === 原木 ===
                                "tag:minecraft:logs",
                                // === 树叶 ===
                                "tag:minecraft:leaves",
                                // === 木门 ===
                                "tag:minecraft:wooden_doors",
                                // === 木活板门 ===
                                "tag:minecraft:wooden_trapdoors",
                                // === 木栅栏 ===
                                "tag:minecraft:fences",
                                // === 栅栏门 ===
                                "tag:minecraft:fence_gates",
                                // === 木楼梯 ===
                                "tag:minecraft:wooden_stairs",
                                // === 木半砖 ===
                                "tag:minecraft:wooden_slabs",
                                // === 木按钮 ===
                                "tag:minecraft:wooden_buttons",
                                // === 木压力板 ===
                                "tag:minecraft:wooden_pressure_plates",
                                // === 告示牌 ===
                                "tag:minecraft:standing_signs",
                                "tag:minecraft:wall_signs",
                                "tag:minecraft:ceiling_hanging_signs",
                                "tag:minecraft:wall_hanging_signs"
                        ),
                        BlockHPConfig::isValidWhitelistEntry);
        builder.pop();

        builder.translation("blockdurability.configuration.section.hp_calculation").push("hp_calculation");
        HARDNESS_MULTIPLIER = builder
                .comment(
                        "Block HP = max(minBlockHP, blockHardness * hardnessMultiplier).",
                        "Block hardness correlates strongly with explosion resistance.",
                        "Examples with multiplier=1.5:",
                        "  Glass (hardness 0.3) → HP = max(1, 0.3*1.5) = 1",
                        "  Oak Planks (2.0) → HP = max(1, 2.0*1.5) = 3",
                        "  Oak Door (3.0)   → HP = max(1, 3.0*1.5) = 4.5",
                        "  Stone (1.5)      → HP = max(1, 1.5*1.5) = 2.25",
                        "  Obsidian (50.0)  → HP = max(1, 50*1.5) = 75"
                )
                .translation("blockdurability.configuration.hp_calculation.hardnessMultiplier")
                .defineInRange("hardnessMultiplier", 1.5, 0.1, 100.0);
        MIN_BLOCK_HP = builder
                .comment("Minimum HP for any breakable block (prevents zero-HP blocks)")
                .translation("blockdurability.configuration.hp_calculation.minBlockHP")
                .defineInRange("minBlockHP", 1.0, 0.5, Double.MAX_VALUE);
        BLOCK_HP_OVERRIDES = builder
                .comment(
                        "Per-block HP overrides. Takes priority over the hardness formula.",
                        "Format: \"modid:block_id=hp\", supports wildcards.",
                        "Examples:",
                        "  \"minecraft:obsidian=20\"  - reduce obsidian to 20 HP",
                        "  \"minecraft:*glass*=1\"    - force all glass to 1 HP"
                )
                .translation("blockdurability.configuration.hp_calculation.blockHPOverrides")
                .defineListAllowEmpty("blockHPOverrides", Arrays.asList(),
                        BlockHPConfig::isValidHpOverride);
        builder.pop();

        builder.translation("blockdurability.configuration.section.explosion").push("explosion");
        EXPLOSION_ENABLED = builder
                .comment("Whether explosions (TaCZ explosives, TNT, Creepers) deal AoE durability damage to blocks")
                .translation("blockdurability.configuration.explosion.enabled")
                .define("enabled", true);
        EXPLOSION_DAMAGE_MULTIPLIER = builder
                .comment("Damage multiplier for explosion AoE damage")
                .translation("blockdurability.configuration.explosion.damageMultiplier")
                .defineInRange("damageMultiplier", 1.0, 0.0, 100.0);
        EXPLOSION_MAX_RADIUS = builder
                .comment("Maximum explosion scan radius (in blocks) to prevent lag from huge custom explosions")
                .translation("blockdurability.configuration.explosion.maxRadius")
                .defineInRange("maxRadius", 16.0, 1.0, 64.0);
        HANDLE_VANILLA_EXPLOSIONS = builder
                .comment("Whether to handle vanilla explosions (TNT, Creepers, etc.) that did not break blocks directly")
                .translation("blockdurability.configuration.explosion.handleVanillaExplosions")
                .define("handleVanillaExplosions", true);
        builder.pop();

        builder.translation("blockdurability.configuration.section.effects").push("effects");
        SPAWN_BREAK_PARTICLES = builder
                .comment("Spawn block break particles when damaged")
                .translation("blockdurability.configuration.effects.spawnBreakParticles")
                .define("spawnBreakParticles", true);
        PLAY_BREAK_SOUND = builder
                .comment("Play block hit sound when damaged")
                .translation("blockdurability.configuration.effects.playBreakSound")
                .define("playBreakSound", true);
        DROP_ITEMS = builder
                .comment("Whether broken blocks drop items")
                .translation("blockdurability.configuration.effects.dropItems")
                .define("dropItems", false);
        builder.pop();

        return new Config();
    }

    public static boolean isValidWhitelistEntry(Object obj) {
        if (!(obj instanceof String str)) return false;
        String trimmed = str.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.startsWith("tag:")) {
            String tagId = trimmed.substring(4).trim();
            if (tagId.isEmpty()) return false;
            ResourceLocation rl = ResourceLocation.tryParse(tagId);
            return rl != null && !rl.getPath().isEmpty();
        }
        return true;
    }

    public static boolean isValidHpOverride(Object obj) {
        if (!(obj instanceof String str)) return false;
        String[] parts = str.split("=", 2);
        if (parts.length != 2) return false;
        String key = parts[0].trim();
        if (key.isEmpty()) return false;
        try {
            float hp = Float.parseFloat(parts[1].trim());
            return Float.isFinite(hp) && hp > 0f;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static class Config {}

    /**
     * 获取 Forge 正在运行环境的真实配置文件绝对路径。
     */
    public static Path getConfigPath() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve(BlockDurability.MOD_ID + "-common.toml");
        } catch (Throwable t) {
            return Path.of("config", BlockDurability.MOD_ID + "-common.toml");
        }
    }

    static boolean migrateLegacyConfig(CommentedFileConfig config) {
        Object legacyTimeout = config.get("healing.healTimeoutSeconds");
        if (!(legacyTimeout instanceof Number number)) {
            return false;
        }

        if (config.get("general.healTimeoutSeconds") == null) {
            config.set("general.healTimeoutSeconds", number.intValue());
        }
        config.remove("healing.healTimeoutSeconds");
        return true;
    }

    /**
     * 强力持久化配置到磁盘文件 (config/blockdurability-common.toml) 并同步刷新内存及缓存。
     */
    public static synchronized boolean saveAndPersist(List<String> whitelist,
                                                      boolean enabled,
                                                      boolean explosion,
                                                      boolean bulletDamage,
                                                      double hardness,
                                                      int healTimeout) {
        return saveAndPersist(getConfigPath(), whitelist, enabled, explosion, bulletDamage, hardness, healTimeout);
    }

    static synchronized boolean saveAndPersist(Path path,
                                               List<String> whitelist,
                                               boolean enabled,
                                               boolean explosion,
                                               boolean bulletDamage,
                                               double hardness,
                                               int healTimeout) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.notExists(path)) {
                Files.createFile(path);
            }

            CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build();
            try {
                config.load();
                config.set("whitelist.blockWhitelist", new ArrayList<>(whitelist));
                config.set("general.enabled", enabled);
                config.set("general.useBulletDamage", bulletDamage);
                config.set("general.healTimeoutSeconds", healTimeout);
                config.set("hp_calculation.hardnessMultiplier", hardness);
                config.set("explosion.enabled", explosion);
                config.save();
            } finally {
                config.close();
            }

            if (SPEC != null && SPEC.isLoaded()) {
                BLOCK_WHITELIST.set(new ArrayList<>(whitelist));
                ENABLED.set(enabled);
                EXPLOSION_ENABLED.set(explosion);
                USE_BULLET_DAMAGE.set(bulletDamage);
                HARDNESS_MULTIPLIER.set(hardness);
                HEAL_TIMEOUT_SECONDS.set(healTimeout);
            }

            BlockDurability.LOGGER.info("Block Durability config successfully persisted to {}", path.toAbsolutePath());
            BlockHPManager.invalidateCache();
            return true;
        } catch (Exception e) {
            BlockDurability.LOGGER.error("Failed to persist config to disk: ", e);
            return false;
        }
    }
}
