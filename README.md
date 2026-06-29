# Block Durability

给 Minecraft 方块加上耐久值——用枪打碎它们！

## 依赖

- **NeoForge** 1.21.1
- **[TaCZ](https://modrinth.com/mod/tacz-1.21.1)** (Timeless & Classics Guns: Zero, NeoForge 移植版) ≥ 1.1.6（可选，没有 TaCZ 时 mod 不会报错但也不会生效）

## 功能

- **白名单制**：只有配置中列出的方块类型才会被子弹伤害
- **血量 = 硬度 × 倍率**：方块血量根据原版硬度自动计算，可手动覆盖
- **裂纹进度**：方块被击中后显示破坏进度裂纹（类似玩家挖矿）
- **粒子 & 音效**：击中时播放方块破坏粒子和打击音效
- **Tag 匹配**：支持方块标签（`tag:minecraft:planks`），自动兼容其他模组的同类方块

## 默认可破坏的方块

| 类别 | Tag |
|---|---|
| 玻璃块 | `tag:c:glass_blocks` |
| 玻璃板 | `tag:c:glass_panes` |
| 木板 | `tag:minecraft:planks` |
| 原木 | `tag:minecraft:logs` |
| 木门 | `tag:minecraft:wooden_doors` |
| 木活板门 | `tag:minecraft:wooden_trapdoors` |
| 木楼梯 | `tag:minecraft:wooden_stairs` |
| 木半砖 | `tag:minecraft:wooden_slabs` |
| 木按钮 | `tag:minecraft:wooden_buttons` |
| 木压力板 | `tag:minecraft:wooden_pressure_plates` |
| 告示牌 | `tag:minecraft:standing_signs` 等 |

## 配置

配置文件自动生成在 `config/blockdurability-common.toml`：

```toml
[general]
    enabled = true
    damagePerHit = 1.0          # 每发子弹的固定伤害
    useBulletDamage = false     # 使用子弹实体伤害

[whitelist]
    blockWhitelist = [
        "tag:c:glass_blocks",
        "tag:minecraft:planks",
        # ... 支持 tag: 前缀和名字通配符两种格式
    ]

[hp_calculation]
    hardnessMultiplier = 1.5    # HP = max(minHP, 硬度 × 倍率)
    minBlockHP = 1.0
    blockHPOverrides = []       # "minecraft:glass=2" 格式的手动覆盖

[effects]
    spawnBreakParticles = true
    playBreakSound = true
    dropItems = false
```

### 白名单格式

```toml
# Tag 匹配（推荐，自动兼容所有模组）
"tag:minecraft:planks"

# 精确名字匹配
"minecraft:glass"

# 通配符名字匹配
"minecraft:*stained_glass"
```

## 构建

```bash
./gradlew build
```

产物在 `build/libs/blockdurability-1.0.0.jar`。

## ⚠️ 兼容性声明

本 mod 仅经过人工代码审查和验证，未在所有模组环境下进行全覆盖测试。与其他模组的兼容性不保证，如遇问题请提交 Issue。

## 🤖 AI 声明

本项目代码部分由 AI 辅助生成，已通过人工审查。如有疑虑请自行审计源码。

## 许可

MIT
