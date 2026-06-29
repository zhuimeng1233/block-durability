package top.dreamfall.blockdurability;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;

public class BlockHPConfig {
    public static final ModConfigSpec SPEC;
    public static final Config CONFIG;

    // ========================
    // 通用
    // ========================
    public static ModConfigSpec.BooleanValue ENABLED;
    public static ModConfigSpec.DoubleValue DAMAGE_PER_HIT;
    public static ModConfigSpec.BooleanValue USE_BULLET_DAMAGE;
    public static ModConfigSpec.DoubleValue BULLET_DAMAGE_MULTIPLIER;

    // ========================
    // 白名单：只有匹配的方块才能被破坏
    // ========================
    public static ModConfigSpec.ConfigValue<List<? extends String>> BLOCK_WHITELIST;

    // ========================
    // 血量计算：基于方块的硬度（与爆炸抗性正相关）
    // ========================
    public static ModConfigSpec.DoubleValue HARDNESS_MULTIPLIER;
    public static ModConfigSpec.DoubleValue MIN_BLOCK_HP;

    // ========================
    // 可选的按方块 HP 覆盖
    // ========================
    public static ModConfigSpec.ConfigValue<List<? extends String>> BLOCK_HP_OVERRIDES;

    // ========================
    // 特效
    // ========================
    public static ModConfigSpec.BooleanValue SPAWN_BREAK_PARTICLES;
    public static ModConfigSpec.BooleanValue PLAY_BREAK_SOUND;
    public static ModConfigSpec.BooleanValue DROP_ITEMS;

    static {
        Pair<Config, ModConfigSpec> pair = new ModConfigSpec.Builder()
                .configure(BlockHPConfig::build);
        SPEC = pair.getRight();
        CONFIG = pair.getLeft();
    }

    private static Config build(ModConfigSpec.Builder builder) {
        builder.push("general");
        ENABLED = builder
                .comment("Enable the block durability system")
                .define("enabled", true);
        DAMAGE_PER_HIT = builder
                .comment("Damage dealt to a block per bullet hit (used when useBulletDamage is false)")
                .defineInRange("damagePerHit", 1.0, 0.1, 100.0);
        USE_BULLET_DAMAGE = builder
                .comment("If true, use the bullet's entity damage * bulletDamageMultiplier as block damage")
                .define("useBulletDamage", false);
        BULLET_DAMAGE_MULTIPLIER = builder
                .comment("Multiplier when useBulletDamage is true")
                .defineInRange("bulletDamageMultiplier", 1.0, 0.0, 100.0);
        builder.pop();

        builder.push("whitelist");
        BLOCK_WHITELIST = builder
                .comment(
                        "Whitelist: only blocks matching these patterns can be damaged by bullets.",
                        "Two formats supported:",
                        "  \"tag:namespace:path\"  → Block tag matching (auto-compatible with all mods!)",
                        "  \"modid:block_id\"     → Exact name match",
                        "  \"modid:*pattern*\"    → Wildcard name match",
                        "Blocks NOT in this list will ignore bullet damage entirely.",
                        "",
                        "Default covers: glass, wood planks, logs, wooden doors/trapdoors/fences, leaves"
                )
                .defineListAllowEmpty("blockWhitelist",
                        Arrays.asList(
                                // === 玻璃（Common Tags，兼容所有模组） ===
                                "tag:c:glass_blocks",
                                "tag:c:glass_panes",
                                // === 木板 ===
                                "tag:minecraft:planks",
                                // === 原木 ===
                                "tag:minecraft:logs",
                                // === 木门 ===
                                "tag:minecraft:wooden_doors",
                                // === 木活板门 ===
                                "tag:minecraft:wooden_trapdoors",
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
                        obj -> obj instanceof String && !((String) obj).isEmpty());
        builder.pop();

        builder.push("hp_calculation");
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
                .defineInRange("hardnessMultiplier", 1.5, 0.1, 100.0);
        MIN_BLOCK_HP = builder
                .comment("Minimum HP for any breakable block (prevents zero-HP blocks)")
                .defineInRange("minBlockHP", 1.0, 0.5, Double.MAX_VALUE);
        BLOCK_HP_OVERRIDES = builder
                .comment(
                        "Per-block HP overrides. Takes priority over the hardness formula.",
                        "Format: \"modid:block_id=hp\", supports wildcards.",
                        "Examples:",
                        "  \"minecraft:obsidian=20\"  - reduce obsidian to 20 HP",
                        "  \"minecraft:*glass*=1\"    - force all glass to 1 HP"
                )
                .defineListAllowEmpty("blockHPOverrides", Arrays.asList(),
                        obj -> obj instanceof String && ((String) obj).contains("="));
        builder.pop();

        builder.push("effects");
        SPAWN_BREAK_PARTICLES = builder
                .comment("Spawn block break particles when damaged")
                .define("spawnBreakParticles", true);
        PLAY_BREAK_SOUND = builder
                .comment("Play block hit sound when damaged")
                .define("playBreakSound", true);
        DROP_ITEMS = builder
                .comment("Whether broken blocks drop items")
                .define("dropItems", false);
        builder.pop();

        return new Config();
    }

    public static class Config {}
}
