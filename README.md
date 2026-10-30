# [BD][BDur] Block Durability (v2.0.0)

给 Minecraft 方块加上耐久值——用枪械与爆炸打碎它们！

<p align="center">
  <a href="assets/block-durability-demo.gif">
    <img src="assets/block-durability-demo.gif" alt="Block Durability 游戏内演示" width="720">
  </a>
</p>

## 🌿 Git 分支与多版本架构

Block Durability 采用多分支结构维护不同 Minecraft 与模组加载器版本：

| 分支名 | 目标游戏版本 | 模组加载器 | 分支定位 | 对应依赖 |
| :--- | :--- | :--- | :--- | :--- |
| **`master`** | **Minecraft 1.21.1** | **NeoForge** (>= 21.1.80) | **主开发分支（默认）** | TaCZ 1.1.6 ~ 1.1.8+（可选） |
| **`forge-1.20.1`** | Minecraft 1.20.1 | MinecraftForge (>= 47.3.0) | 移植与长期维护分支 | TaCZ 1.0.2 ~ 1.1.5+（可选） |
| **`neoforge-1.21.11`** | Minecraft 1.21.11 | NeoForge (>= 21.11.48) | 社区 CalVer 前瞻分支 | TaCZ 1.21.11 移植版（可选） |

---

## 依赖与运行环境

- **运行平台**：NeoForge 1.21.1 (推荐 NeoForge 21.1.80 或更高版本)
- **可选依赖**：[TaCZ](https://modrinth.com/mod/tacz-1.21.1) (Timeless & Classics Guns: Zero, NeoForge 移植版) 1.1.6 - 1.1.8+
  > **独立运行声明**：TaCZ 为**可选软依赖**。若未安装 TaCZ，Block Durability 可作为纯净的原版/模组**爆炸范围耐久 Addon** 独立运行，不抛出任何类加载异常。

---

## ✨ 核心特性 (v2.0.0)

- **💥 混合双轨制爆炸破坏（v2.0.0 重磅）**：
  - **TNT / 苦力怕等爆炸**：原版能够直接摧毁的内圈方块继续正常摧毁；外圈或高防爆未被摧毁的方块，自动转为**线性距离衰减的耐久扣血**！
  - **TaCZ 爆炸武器无缝破拆**：TaCZ 的 RPG、榴弹等爆炸物默认不炸坑（`BlockInteraction.KEEP`），在 Block Durability 接管下，爆炸范围内的所有白名单方块将**全自动享受范围耐久破拆**！
  - **物理视线遮挡（Raycast Occlusion）**：采用轻量级碰撞箱光线投射算法，实体掩体后方的方块受到阻隔保护，不会发生隔墙炸毁。
- **🔫 枪械射击破坏**：
  - 子弹击中白名单方块扣除对应耐久，支持固定伤害或子弹实体伤害（`useBulletDamage`）。
  - 事件监听采用 `EventPriority.LOWEST`，尊重领地、地皮、FTB Chunks 等保护插件与模组的防破坏判断。
- **🪵 原生栅栏与门穿透支持（零 Mixin）**：
  - 通过数据包覆盖 `data/tacz/tags/block/bullet_ignore.json`，解决原版栅栏和栅栏门被 TaCZ 无视穿透的问题。
  - 彻底移除了旧版侵入式 Mixin，零冲突、启动零风险。
- **⏱️ 超时自愈机制与 GameRule 动态控制**：
  - 方块受损后在一定时间内未再次受损，将自动自愈并清除开裂视觉效果，避免长期缓存占用内存。
  - 默认自愈时间提升至 **120 秒**。
  - 提供游戏内管理员 GameRule：`/gamerule blockDurabilityHealSeconds <秒数>`（支持随时动态调节，设为 `0` 则彻底关闭自愈）。
- **🎨 细腻视听反馈**：
  - 方块受损时按比例显示 Minecraft 原版 10 级破坏裂纹（0-9 阶段）。
  - 受损播放方块打击粒子与声音，碎裂崩解时生成方块破坏特效与音效。
- **🛡️ 线程安全与热重载**：
  - 白名单通配符正则编译缓存化，在配置重载（`ModConfigEvent.Reloading`）时自动刷新缓存，保障高射速多线程下的稳定性。

---

## 默认支持的方块类型

| 类别 | 格式 / Tag / ID |
|---|---|
| 玻璃与玻璃板 | `tag:c:glass_blocks`, `tag:c:glass_panes` |
| 木板、原木与树叶 | `tag:minecraft:planks`, `tag:minecraft:logs`, `tag:minecraft:leaves` |
| 木门与木活板门 | `tag:minecraft:wooden_doors`, `tag:minecraft:wooden_trapdoors` |
| 栅栏与栅栏门 | `tag:minecraft:fences`, `tag:minecraft:fence_gates` |
| 楼梯、台阶与红石配件 | `tag:minecraft:wooden_stairs`, `tag:minecraft:wooden_slabs`, `tag:minecraft:wooden_buttons`, `tag:minecraft:wooden_pressure_plates` |
| 告示牌与吊挂告示牌 | `tag:minecraft:standing_signs`, `tag:minecraft:ceiling_hanging_signs` 等 |
| 地表与基础方块 | 可在配置中随意追加，如 `minecraft:grass_block`, `minecraft:dirt`, `minecraft:stone` 等 |

---

## 配置文件说明

配置文件位于服务端的 `config/blockdurability-common.toml`：

```toml
[general]
    # 是否启用模组功能
    enabled = true
    # 每发子弹命中的固定伤害值
    damagePerHit = 1.0
    # 是否使用 TaCZ 子弹实体的真实伤害数值（设为 true 时 damagePerHit 失效）
    useBulletDamage = false
    # 子弹伤害倍率
    bulletDamageMultiplier = 1.0

[whitelist]
    # 允许被枪械与爆炸破坏的方块列表
    # 支持三种格式：
    #   1. tag: 前缀（如 "tag:minecraft:planks"，推荐，自动兼容其他模组木头）
    #   2. 精确方块 ID（如 "minecraft:grass_block"）
    #   3. 通配符模式（如 "minecraft:*stained_glass"）
    blockWhitelist = [
        "tag:c:glass_blocks",
        "tag:c:glass_panes",
        "tag:minecraft:planks",
        "tag:minecraft:logs",
        "tag:minecraft:leaves",
        "tag:minecraft:wooden_doors",
        "tag:minecraft:wooden_trapdoors",
        "tag:minecraft:fences",
        "tag:minecraft:fence_gates",
        "tag:minecraft:wooden_stairs",
        "tag:minecraft:wooden_slabs",
        "tag:minecraft:wooden_buttons",
        "tag:minecraft:wooden_pressure_plates",
        "tag:minecraft:standing_signs",
        "tag:minecraft:wall_signs",
        "tag:minecraft:ceiling_hanging_signs",
        "tag:minecraft:wall_hanging_signs"
    ]

[hp_calculation]
    # 方块血量计算公式：HP = max(minBlockHP, 方块原版硬度 * hardnessMultiplier)
    hardnessMultiplier = 1.5
    # 方块最低血量保底
    minBlockHP = 1.0
    # 手动指定特定方块的固定血量覆盖，优先级最高（格式：["minecraft:obsidian=50.0"]）
    blockHPOverrides = []

[explosion]
    # 是否启用爆炸范围耐久伤害
    enabled = true
    # 爆炸耐久伤害倍率
    damageMultiplier = 1.0
    # 爆炸扫描半径上限（米），防止超大爆炸卡顿
    maxRadius = 16.0
    # 是否接管原版 TNT/苦力怕未炸毁方块的波及扣血
    handleVanillaExplosions = true

[healing]
    # 受损方块未继续受损时的自愈冷却时间（秒，0 代表完全禁用自愈）
    # 游戏内亦可通过 /gamerule blockDurabilityHealSeconds 动态调整
    healTimeoutSeconds = 120

[effects]
    # 方块碎裂时是否生成破坏粒子
    spawnBreakParticles = true
    # 方块受损与崩解时是否播放音效
    playBreakSound = true
    # 方块被枪械/爆炸打碎后是否掉落物品
    dropItems = false
```

---

## 游戏内指令

- **修改自动恢复冷却秒数**：
  ```
  /gamerule blockDurabilityHealSeconds <秒数>
  ```
  *例：`/gamerule blockDurabilityHealSeconds 60`（设为 60 秒）；设为 `0` 代表彻底禁用自愈。*

---

## 构建与测试

需要 JDK 21 环境：

```bash
# 构建 JAR
./gradlew build

# 运行自动化单元测试
./gradlew test
```

构建产物位于 `build/libs/blockdurability-2.0.0-neoforge.jar`。

---

## 📜 许可证

本项目基于 [MIT 许可证](LICENSE) 开源。
