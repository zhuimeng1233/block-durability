# [BD][BDur] Block Durability (Forge 1.20.1 - v2.0.0)

给 Minecraft 方块加上耐久值——用枪械与爆炸打碎它们！

<p align="center">
  <a href="assets/block-durability-demo.gif">
    <img src="assets/block-durability-demo.gif" alt="Block Durability Forge 1.20.1 游戏内演示" width="720">
  </a>
</p>

> 📌 **分支定位**：此分支为 **Forge 1.20.1** 长期维护分支。主开发分支（NeoForge 1.21.1）见 [`master`](https://github.com/zhuimeng1233/block-durability/tree/master) 分支。

---

## 依赖与运行环境

- **运行平台**：Minecraft Forge 1.20.1 (推荐 Forge 47.3.0 或更高版本)
- **可选依赖**：[TaCZ](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero) (Timeless & Classics Guns: Zero, Forge 版) 1.0.2 - 1.1.8+
  > **独立运行声明**：TaCZ 为**可选软依赖**。若未安装 TaCZ，Block Durability 可作为纯净的原版/模组**爆炸范围耐久 Addon** 独立运行，不抛出任何类加载异常。

---

## ✨ 核心特性 (v2.0.0)

- **💥 混合双轨制爆炸破坏（v2.0.0 重磅）**：
  - **TNT / 苦力怕等爆炸**：原版能够直接摧毁的内圈方块继续正常摧毁；外圈或高防爆未被摧毁的方块，自动转为**线性距离衰减的耐久扣血**！
  - **TaCZ 爆炸武器无缝破拆**：TaCZ 的 RPG、榴弹等爆炸物默认不炸方块（`BlockInteraction.KEEP`），在 Block Durability 接管下，爆炸范围内的所有白名单方块将**全自动享受范围耐久破拆**！
  - **物理视线遮挡（Raycast Occlusion）**：采用轻量级碰撞箱光线投射算法，实体掩体后方的方块受到阻隔保护，不会发生隔墙炸毁。
- **🔫 枪械射击破坏**：
  - 子弹击中白名单方块扣除对应耐久，支持固定伤害或子弹实体伤害（`useBulletDamage`）。
  - 事件监听采用 `EventPriority.LOWEST`，尊重领地、地皮、FTB Chunks 等保护插件与模组的防破坏判断。
- **🪵 原生栅栏与门穿透支持（零 Mixin）**：
  - 通过数据包覆盖 `data/tacz/tags/blocks/bullet_ignore.json`，解决原版栅栏和栅栏门被 TaCZ 无视穿透的问题。
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

## 配置文件说明

配置文件位于服务端的 `config/blockdurability-common.toml`：

```toml
[general]
    enabled = true
    damagePerHit = 1.0
    useBulletDamage = false
    bulletDamageMultiplier = 1.0

[whitelist]
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
    hardnessMultiplier = 1.5
    minBlockHP = 1.0
    blockHPOverrides = []

[explosion]
    enabled = true
    damageMultiplier = 1.0
    maxRadius = 16.0
    handleVanillaExplosions = true

[healing]
    healTimeoutSeconds = 120

[effects]
    spawnBreakParticles = true
    playBreakSound = true
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

需要 JDK 17 环境：

```bash
# 运行单元测试并构建 JAR
./gradlew test build
```

产物在 `build/libs/blockdurability-1.20.1-2.0.0-forge.jar`。

---

## 许可

本项目基于 [MIT 许可证](LICENSE) 开源。
