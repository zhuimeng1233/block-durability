# Block Durability (BD) 跨版本维护与交接手册 (v1.2.0)

> **模组定位**：`block-durability` 作为独立伴生模组（Addon）与 Timeless and Classics Zero (TaCZ) 协同工作，为 Minecraft 中的方块赋予基于硬度的耐久机制，配合枪械实现动态射击破坏与裂纹呈现。
> **模组作者**：坠梦 (Copyright © 2025-2026 坠梦)  
> **开源协议**：MIT  
> **当前版本**：`v1.2.0`（已实现 1.20.1 / 1.21.1 / 1.21.11 全版本特性对齐）

---

## 1. 工程目录与 Git 分支管理矩阵

为了兼顾各版本的 Gradle 工具链（MinecraftForge 6.x 与 NeoForge ModDev 插件），当前本地采用了 **Git 专属分支 + Worktree 隔离** 的架构：

| 目标游戏版本 | 模组加载器 | 本地工作区绝对路径 | Git 分支名 | 产出 JAR 路径 | 当前 Git 状态 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **MC 1.20.1** | **Forge**<br>(47.3.0) | `E:\New\MC\block-durability-forge-1.20.1` | `forge-1.20.1` | `E:\New\MC\block-durability-forge-1.20.1\build\libs\blockdurability-1.20.1-1.2.0-forge.jar` | 已提交 (Ahead 1) |
| **MC 1.21.1** | **NeoForge**<br>(21.1.80) | `E:\Gnew\block-durability` | `master` | `E:\Gnew\block-durability\build\libs\blockdurability-1.2.0-neoforge.jar` | 已提交 (Ahead 1) |
| **MC 1.21.11** | **NeoForge**<br>(21.11.48) | `E:\Gnew\block-durability` | `neoforge-1.21.11` | `E:\Gnew\block-durability\build\libs\blockdurability-1.21.11-1.2.0-neoforge.jar` | 已提交新分支 |

---

## 2. v1.2.0 全版本统一架构与特性

所有三个版本均已统一落地以下 5 大核心机制：

### 2.1 多维度安全隔离 (Multi-Dimension Safety)
- **旧版缺陷**：仅以 `BlockPos` 为 Key 存储破坏状态，主世界、地狱、末地同坐标方块互相串血。
- **v1.2.0 实现**：统一使用 `BlockKey(ResourceKey<Level> dimension, BlockPos pos)` 复合键进行状态索引。

### 2.2 超时自愈与内存回收 (Auto-healing & Memory Cleanup)
- **机制**：残血方块若在 **20 秒 (20000ms)** 内未受到连续枪击，服务端定时器自动触发回收。
- **表现**：向附近玩家下发进度为 `-1` 的 `ClientboundBlockDestructionPacket` 清除视觉裂纹，并从 `damagedBlocks` 缓存中彻底移除，消除服务器内存泄漏。

### 2.3 区块卸载生命周期协同 (ChunkUnload Lifecycle)
- **机制**：监听各端原生的 `ChunkEvent.Unload`，自动清理卸载区块内的受损方块缓存，防止跨区块卸载造成的无效网络包和内存常驻。

### 2.4 弹性 Mixin 防护 (Soft-inject Resilience)
- **机制**：`MixinBlockRayTrace` 注入增加 `require = 0`。
- **作用**：即使未来玩家搭配修改了 `BlockRayTrace` 内部结构的 TACZ 社区衍生版（如 Renovated / Reforged），Mixin 也只会静默跳过，绝不引发游戏启动崩溃。

### 2.5 数据包 Tag 路径规范化
- **1.21+ / 1.21.11**：标准路径为 `data/tacz/tags/block/bullet_ignore.json`（单数 `block`）。
- **1.20.1**：维持 `data/tacz/tags/blocks/bullet_ignore.json`（复数 `blocks`）。
- 保证栅栏和栅栏门能被子弹正常击中而不被直接忽略。

---

## 3. 跨版本核心代码适配差异表

下一个开发者接手维护时，请注意以下平台差异，严禁直接跨平台盲目复制代码：

| 差异项 | MC 1.20.1 (Forge) | MC 1.21.1 (NeoForge) | MC 1.21.11 (NeoForge) |
| :--- | :--- | :--- | :--- |
| **Java 工具链** | Java 17 (`JavaLanguageVersion.of(17)`) | Java 21 | Java 21 |
| **事件总线** | `MinecraftForge.EVENT_BUS` | `NeoForge.EVENT_BUS` | `NeoForge.EVENT_BUS` |
| **Tick 监听** | `TickEvent.LevelTickEvent`<br>(需判断 `Phase.END`) | `LevelTickEvent.Post` | `LevelTickEvent.Post` |
| **标识符类** | `ResourceLocation`<br>(使用 `new ResourceLocation(...)`) | `ResourceLocation`<br>(使用 `ResourceLocation.parse(...)`) | `Identifier`<br>(使用 `Identifier.parse(...)`) |
| **数值 Clamp** | `net.minecraft.util.Mth.clamp(...)` | `Math.clamp(...)` 或 `Mth.clamp` | `Math.clamp(...)` |
| **TACZ 依赖** | CurseMaven (`7745481`) | Modrinth (`1.1.8-hotfix-r2`) | 本地 `TACZ-1.21.1` 构建 JAR |

---

## 4. 本地构建与打包命令

本地 JDK 21 推荐路径：`C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot`。

### 4.1 构建 1.21.11 (NeoForge)
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Set-Location "E:\Gnew\block-durability"
git checkout neoforge-1.21.11
.\gradlew.bat build --no-daemon
```

### 4.2 构建 1.21.1 (NeoForge)
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Set-Location "E:\Gnew\block-durability"
git checkout master
.\gradlew.bat build --no-daemon
```

### 4.3 构建 1.20.1 (Forge)
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Set-Location "E:\New\MC\block-durability-forge-1.20.1"
git checkout forge-1.20.1
.\gradlew.bat build --no-daemon
```

---

## 5. Git 推送指令指南

所有工作区目前代码树均已提交为干净状态，可直接运行以下命令推送到远端：

```powershell
# 1. 推送 1.21.1 master 分支
git -C "E:\Gnew\block-durability" push origin master

# 2. 推送 1.21.11 新分支
git -C "E:\Gnew\block-durability" push -u origin neoforge-1.21.11

# 3. 推送 1.20.1 forge-1.20.1 分支
git -C "E:\New\MC\block-durability-forge-1.20.1" push origin forge-1.20.1
```

---

## 6. 后续演进规划（针对 26.x / 26.4）

1. **观察 26.4 正式版**：
   - 待 Mojang 正式发布默认搭载 Vulkan 的 26.4 稳定版后，再启动 26.x 分支移植。
2. **免 Mixin 策略**：
   - 26.x 社区移植版（Renovated / Reforged）对私有射线检测类 `BlockRayTrace` 改动极大，建议在 26.x 中彻底废除 `MixinBlockRayTrace`，单纯依靠 `tacz:bullet_ignore` 数据包覆写与 `AmmoHitBlockEvent` 实现 100% 零冲突兼容。
3. **原生真穿透预留**：
   - 如未来 TACZ 官方或社区开放了子弹贯穿方块后的能量损耗 API，可在此架构上接入子弹不销毁的击穿玩法。

---
*交接手册创建时间：2026-10-24 | 整理人：小郑*
