# [BD][BDur] Block Durability (Forge 1.20.1 - v2.0.0)

给 Minecraft 方块加上耐久值——用枪械与爆炸打碎它们！

<p align="center">
  <a href="assets/block-durability-demo.gif">
    <img src="assets/block-durability-demo.gif" alt="Block Durability Forge 1.20.1 游戏内演示" width="720">
  </a>
</p>

> 📌 **分支定位**：此分支为 **Forge 1.20.1** 长期维护分支。核心主开发分支（NeoForge 1.21.1）见 [`master`](https://github.com/zhuimeng1233/block-durability/tree/master) 分支。

---

## 🚀 v2.0.0 重大更新日志 (What's New in v2.0.0)

> v2.0.0 是里程碑式的跨版本架构升级，将 1.21.1 的全部新架构完整移植至 1.20.1 Forge：

1. **💥 混合双轨制爆炸破坏（全新核心系统）**：
   - **原版 TNT / 苦力怕等爆炸**：内圈威力区正常炸碎方块；外圈未能炸碎的高防爆方块，自动转入**基于距离线性衰减的耐久扣血**！
   - **TaCZ 爆炸武器全面接管**：TaCZ 的 RPG、榴弹等默认不破坏地形（`KEEP` 模式），在 Block Durability 接管下，范围内所有白名单方块**全自动享受范围破拆扣血**！
   - **物理视线遮挡（Raycast Occlusion）**：冲击波受实体掩体阻隔，躲在防爆掩体后的玻璃窗安全无恙，杜绝隔墙炸毁。
2. **🛡️ TaCZ 独立解耦与纯净运行（Standalone Addon）**：
   - 枪械命中监听独立封装为软依赖；未安装 TaCZ 时模组可作为**独立的原版/通用模组爆炸耐久 Addon** 稳定运行，绝不抛出任何类加载异常。
3. **🪵 彻底摆脱 Mixin（零注入、零侵入）**：
   - 完全删除旧版 `MixinBlockRayTrace`，改为官方标准数据包标签覆盖（覆写 TaCZ 的 `bullet_ignore` 标签并移除栅栏和栅栏门）。
   - 彻底消除了不同 TaCZ 衍生构建版本下的启动冲突崩溃隐患。
4. **⏱️ 120 秒超时自愈与 GameRule 动态调整**：
   - 残血方块未受伤害超时自愈时间提升至 **120 秒**。
   - 新增游戏内管理员指令：`/gamerule blockDurabilityHealSeconds <秒数>`（支持随时动态调节，设为 `0` 则彻底关闭自愈）。
5. **🧹 方块破坏与放置全生命周期闭环（解决幽灵裂纹）**：
   - 新增监听原版方块破坏（`BreakEvent`）与放置（`EntityPlaceEvent`），以及心跳空气检测。
   - 彻底解决了方块被玩家手动挖除或水冲推走后，原地放置新方块“继承旧裂纹/秒碎”的幽灵进度 Bug。
6. **🤝 领地保护生态友好（Claim Protection）**：
   - 命中事件监听优先级调整至 `EventPriority.LOWEST`，尊重领地、地皮、FTB Chunks 等圈地防破坏拦截。
7. **⚡ 配置热重载与线程安全正则缓存**：
   - 白名单通配符正则编译并加入缓存，在配置重载（`ModConfigEvent.Reloading`）时自动刷新缓存，杜绝非法数值/NaN 崩溃。

---

## 依赖与运行环境

- **运行平台**：Minecraft Forge 1.20.1 (推荐 Forge 47.3.0 或更高版本)
- **可选依赖**：[TaCZ](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero) (Timeless & Classics Guns: Zero, Forge 版) 1.0.2 - 1.1.8+
  > **独立运行声明**：TaCZ 为可选软依赖。若未安装 TaCZ，Block Durability 可作为纯净的原版/模组**爆炸范围耐久 Addon** 独立运行。

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
| 地表与基础方块 | 可在配置中随意追加，如 `minecraft:grass_block`, `minecraft:dirt`, `minecraft:stone`, `minecraft:cobblestone`, `minecraft:obsidian` 等 |

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
