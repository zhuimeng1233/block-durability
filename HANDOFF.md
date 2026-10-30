# Block Durability (BD) 跨版本维护与交接手册 (v2.0.0)

> **模组定位**：`block-durability` 作为独立伴生模组（Addon）与 Timeless and Classics Zero (TaCZ) 协同工作，亦可脱离 TaCZ 作为独立的原版/模组爆炸耐久破坏模组运行。为 Minecraft 方块赋予基于硬度的耐久机制，配合枪械与爆炸实现动态破坏、裂纹开裂与残余波及。  
> **模组作者**：坠梦 (Copyright © 2025-2026 坠梦)  
> **开源协议**：MIT  
> **当前版本**：`v2.0.0`（1.21.1 NeoForge 作为主开发分支首发）

---

## 1. 工程目录与 Git 分支管理矩阵

为了兼顾各版本的 Gradle 工具链（MinecraftForge 6.x 与 NeoForge ModDev 插件），当前本地采用了 **Git 专属分支 + Worktree 隔离** 架构：

| 目标游戏版本 | 模组加载器 | 本地工作区绝对路径 | Git 分支名 | 当前定位 | 产出 JAR 路径 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **MC 1.21.1** | **NeoForge**<br>(>= 21.1.80) | `E:\Gnew\block-durability` | **`master`** | **核心主开发分支（默认）** | `build/libs/blockdurability-2.0.0-neoforge.jar` |
| **MC 1.20.1** | **Forge**<br>(47.3.0) | `E:\New\MC\block-durability-forge-1.20.1` | `forge-1.20.1` | 长期维护分支 (Worktree) | `build/libs/blockdurability-1.20.1-2.0.0-forge.jar` |
| **MC 1.21.11** | **NeoForge**<br>(21.11.48) | `E:\Gnew\block-durability` | `neoforge-1.21.11` | 社区 CalVer 前瞻分支 | `build/libs/blockdurability-1.21.11-1.2.0-neoforge.jar` |

> 📌 **主分支说明**：`master` 分支代表当前官方生态活跃度最高的 **Minecraft 1.21.1 (NeoForge)** 版本，所有全新重大架构特性（如 v2.0.0 爆炸系统）均首先在此验证并发布，随后向各移植分支反向回移植。

---

## 2. v2.0.0 架构升级与核心特性

从 v1.2.0 跃迁至 v2.0.0，模组进行了全面重构，解决了 6 项核心痛点并加入了全新的爆炸系统：

### 2.1 混合双轨制爆炸耐久系统 (Dual-Track Explosion)
- **机制**：监听 `ExplosionEvent.Detonate`。
- **双轨分流**：
  - 原版 TNT / 苦力怕等破坏型爆炸：原版 `getAffectedBlocks()` 中已被炸碎的内圈方块继续正常炸掉；不在其中的外圈与高防爆白名单方块，自动转入**线性距离衰减耐久扣血**。
  - TaCZ 爆炸物（RPG、榴弹等）：TaCZ 默认采用 `BlockInteraction.KEEP`，受影响方块列表为空，此时范围内所有白名单方块**全自动转入范围耐久破坏**！
- **视线碰撞遮挡**：
  - 使用 `serverLevel.clip(new ClipContext(center, blockCenter, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))` 进行视线穿透检测。
  - **重要踩坑防范**：在 1.21.1 中 `ClipContext` 若传入 `(Entity) null`，会导致 `EntityCollisionContext` 内部调用 `entity.isDescending()` 引发致命空指针崩溃；必须使用 `CollisionContext.empty()`！

### 2.2 彻底摆脱 Mixin（零注入、零侵入）
- **旧版风险**：旧版包含 `MixinBlockRayTrace`，在不同 TaCZ 构建版本或衍生版中存在注入签名失效和崩溃隐患。
- **v2.0.0 方案**：完全删除 Mixin 类及配置，纯靠数据包覆写 `data/tacz/tags/block/bullet_ignore.json`（设置 `"replace": true`，从忽略列表中移除木栅栏和栅栏门）。

### 2.3 TaCZ 解耦与独立运行 (Standalone Addon)
- **机制**：枪械事件监听器抽离至独立的 `TaczHitHandler` 类中；在 `BlockDurability.java` 主类中通过反射 `Class.forName("com.tacz.guns.api.event.server.AmmoHitBlockEvent")` 检测。
- **效果**：若未安装 TaCZ，模组安全跳过 TaCZ 事件注册，作为纯净的原版爆炸耐久破坏 Addon 运行，不再产生任何 `NoClassDefFoundError`。

### 2.4 超时自愈扩展至 120 秒与 GameRule 支持
- **机制**：默认自愈超时由 20 秒提升为 **120 秒**。
- **配置与规则双通道**：
  - 配置文件：`healTimeoutSeconds = 120`。
  - 游戏内 GameRule：注册了 `blockDurabilityHealSeconds`，可在游戏内通过 `/gamerule blockDurabilityHealSeconds 120` 即时调整（设为 0 完全关闭自愈）。

### 2.5 领地保护生态兼容 (Claim Protection Friendliness)
- **机制**：`TaczHitHandler` 的事件监听优先级设为 `@SubscribeEvent(priority = EventPriority.LOWEST)`。
- **效果**：领地（GriefPrevention、FTB Chunks 等）可在 `HIGH` / `NORMAL` 优先级拦截事件，Block Durability 不会在被保护区域内扣除方块耐久。

### 2.6 配置热重载与线程安全正则缓存
- **机制**：通配符白名单采用预编译正则表达式缓存 (`compiledPatterns`)，监听 `ModConfigEvent.Loading` 和 `ModConfigEvent.Reloading` 自动调用 `invalidateCache()` 刷新缓存。
- **健壮性**：严格校验 `Float.isFinite`、非空 Tag ID，杜绝 NaN / 异常数值导致的数据损坏。

---

## 3. 跨版本核心代码适配差异表

在向其他分支移植时，请注意以下关键 API 差异：

| 差异项 | MC 1.20.1 (Forge) | MC 1.21.1 (NeoForge) | MC 1.21.11 (NeoForge) |
| :--- | :--- | :--- | :--- |
| **Java 工具链** | Java 17 | Java 21 | Java 21 |
| **事件总线** | `MinecraftForge.EVENT_BUS` | `NeoForge.EVENT_BUS` | `NeoForge.EVENT_BUS` |
| **Tick 监听** | `TickEvent.LevelTickEvent` (需判断 `Phase.END`) | `LevelTickEvent.Post` | `LevelTickEvent.Post` |
| **爆炸位置** | `explosion.getPosition()` | `explosion.center()` | `explosion.center()` |
| **爆炸半径** | 反射获取 `Explosion.radius` (`f_46017_`，唯一 float 字段) | `explosion.radius()` | `explosion.radius()` |
| **射线上下文** | `ClipContext(..., null)` 或 `CollisionContext.empty()` | 必须使用 `CollisionContext.empty()` | 必须使用 `CollisionContext.empty()` |
| **数据包 Tag** | `tags/blocks/bullet_ignore.json` (复数) | `tags/block/bullet_ignore.json` (单数) | `tags/block/bullet_ignore.json` (单数) |

---

## 4. 本地构建命令

推荐环境：JDK 21 (`JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot"`）。

### 构建 1.21.1 主分支 (NeoForge)
```powershell
Set-Location "E:\Gnew\block-durability"
git checkout master
.\gradlew.bat build --no-daemon
```

### 构建 1.20.1 移植分支 (Forge)
```powershell
Set-Location "E:\New\MC\block-durability-forge-1.20.1"
git checkout forge-1.20.1
.\gradlew.bat build --no-daemon
```

---

## 5. Git 远端与分支规范

- **默认主分支**：`master` (即 1.21.1 NeoForge)。
- **同步推送指令**：
```powershell
git -C "E:\Gnew\block-durability" push origin master
git -C "E:\Gnew\block-durability" push mirror master
```
