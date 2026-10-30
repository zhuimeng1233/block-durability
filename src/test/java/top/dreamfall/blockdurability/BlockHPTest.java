package top.dreamfall.blockdurability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class BlockHPTest {

    @Test
    @DisplayName("通配符 Glob 转 Pattern 匹配测试")
    void testGlobPatternMatching() {
        Pattern exactPattern = BlockHPManager.globToPattern("minecraft:stone");
        assertTrue(exactPattern.matcher("minecraft:stone").matches());
        assertFalse(exactPattern.matcher("minecraft:stone_stairs").matches());
        assertFalse(exactPattern.matcher("other:minecraft:stone").matches());

        Pattern wildcardPattern = BlockHPManager.globToPattern("minecraft:*glass*");
        assertTrue(wildcardPattern.matcher("minecraft:glass").matches());
        assertTrue(wildcardPattern.matcher("minecraft:stained_glass").matches());
        assertTrue(wildcardPattern.matcher("minecraft:glass_pane").matches());
        assertFalse(wildcardPattern.matcher("custom:stained_glass").matches());

        Pattern prefixWildcard = BlockHPManager.globToPattern("*glass");
        assertTrue(prefixWildcard.matcher("minecraft:glass").matches());
        assertTrue(prefixWildcard.matcher("tinted_glass").matches());
        assertFalse(prefixWildcard.matcher("glass_pane").matches());
    }

    @Test
    @DisplayName("白名单条目合法性校验测试")
    void testWhitelistEntryValidation() {
        assertTrue(BlockHPConfig.isValidWhitelistEntry("tag:minecraft:planks"));
        assertTrue(BlockHPConfig.isValidWhitelistEntry("tag:c:glass_blocks"));
        assertTrue(BlockHPConfig.isValidWhitelistEntry("minecraft:stone"));
        assertTrue(BlockHPConfig.isValidWhitelistEntry("minecraft:*glass*"));

        // 非法项应被拒绝
        assertFalse(BlockHPConfig.isValidWhitelistEntry(""));
        assertFalse(BlockHPConfig.isValidWhitelistEntry("   "));
        assertFalse(BlockHPConfig.isValidWhitelistEntry("tag:"));
        assertFalse(BlockHPConfig.isValidWhitelistEntry("tag:invalid colon:spaced"));
        assertFalse(BlockHPConfig.isValidWhitelistEntry(123));
    }

    @Test
    @DisplayName("HP 覆盖条目合法性校验测试（防 NaN / 负数 / 无穷）")
    void testHpOverrideValidation() {
        assertTrue(BlockHPConfig.isValidHpOverride("minecraft:obsidian=20"));
        assertTrue(BlockHPConfig.isValidHpOverride("minecraft:*glass*=1.5"));
        assertTrue(BlockHPConfig.isValidHpOverride("tag:c:stone=10.0"));

        // 非法数值应被拒绝
        assertFalse(BlockHPConfig.isValidHpOverride("minecraft:obsidian=-5"));
        assertFalse(BlockHPConfig.isValidHpOverride("minecraft:obsidian=0"));
        assertFalse(BlockHPConfig.isValidHpOverride("minecraft:obsidian=NaN"));
        assertFalse(BlockHPConfig.isValidHpOverride("minecraft:obsidian=Infinity"));
        assertFalse(BlockHPConfig.isValidHpOverride("minecraft:obsidian=-Infinity"));

        // 非法格式应被拒绝
        assertFalse(BlockHPConfig.isValidHpOverride("minecraft:obsidian"));
        assertFalse(BlockHPConfig.isValidHpOverride("=20"));
        assertFalse(BlockHPConfig.isValidHpOverride("minecraft:obsidian="));
        assertFalse(BlockHPConfig.isValidHpOverride("minecraft:obsidian=abc"));
        assertFalse(BlockHPConfig.isValidHpOverride(""));
    }

    @Test
    @DisplayName("爆炸范围衰减伤害公式测试")
    void testExplosionDamageCalculation() {
        float effectiveRadius = 4.0f;
        float multiplier = 1.0f;
        float baseDamage = effectiveRadius * 4.0f * multiplier; // 16.0

        // 爆心（距离 0）
        float falloffCenter = (float) Math.max(0.0f, 1.0f - (0.0 / effectiveRadius));
        assertEquals(16.0f, baseDamage * falloffCenter, 0.001f);

        // 半径一半（距离 2.0）
        float falloffMid = (float) Math.max(0.0f, 1.0f - (2.0 / effectiveRadius));
        assertEquals(8.0f, baseDamage * falloffMid, 0.001f);

        // 边缘（距离 4.0）
        float falloffEdge = (float) Math.max(0.0f, 1.0f - (4.0 / effectiveRadius));
        assertEquals(0.0f, baseDamage * falloffEdge, 0.001f);

        // 范围外（距离 5.0）
        float falloffOut = (float) Math.max(0.0f, 1.0f - (5.0 / effectiveRadius));
        assertEquals(0.0f, baseDamage * falloffOut, 0.001f);
    }
}
