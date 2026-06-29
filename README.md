# Block Durability (Forge 1.20.1)

方块耐久系统——给方块加上血量，用 TaCZ 枪械射击可破坏玻璃、木板等方块。

> 此分支为 **Forge 1.20.1** 移植版。NeoForge 1.21.1 原版见 `master` 分支。

> ⚠️ **AI 生成声明**：此移植由 AI（Claude）辅助完成，代码经过人工审查和验证，已通过编译构建，但未经全面游戏内测试。使用前建议在测试环境先行验证。

> ⚠️ **兼容性声明**：本模组依赖 TACZ 内部 API（`AmmoHitBlockEvent` / `EntityKineticBullet`），API 路径已与 TACZ 1.20.1 源码交叉确认一致。若 TACZ 未来版本变更内部 API，可能导致兼容性问题。

## 功能

- 方块基于硬度自动计算血量
- 支持 Tag / 通配符白名单（默认覆盖玻璃、木板、原木、木门等）
- 裂纹进度条实时显示破坏程度
- 粒子特效 + 音效反馈
- 按方块单独覆盖血量（如 `minecraft:obsidian=20`）
- TACZ 可选依赖，未安装时自动静默

## 构建

```bash
./gradlew build
```

产出位于 `build/libs/blockdurability-1.0.0.jar`。

## 依赖

- **Minecraft Forge** 1.20.1 (47.3+)
- **TaCZ** (Timeless and Classics Zero) 1.20.1 — 可选

## 配置

配置文件位于 `config/blockdurability-common.toml`，可调参数：

| 分类 | 配置项 | 说明 |
|---|---|---|
| general | `enabled` | 是否启用 |
| general | `damagePerHit` | 每发子弹伤害 |
| general | `useBulletDamage` | 使用子弹实际伤害 |
| whitelist | `blockWhitelist` | 可破坏方块白名单 |
| hp_calculation | `hardnessMultiplier` | 硬度→血量倍率 |
| hp_calculation | `blockHPOverrides` | 按方块覆盖血量 |
| effects | `dropItems` | 破坏后是否掉落 |

## 移植说明

| 差异 | NeoForge 1.21.1 (master) | Forge 1.20.1 (本分支) |
|---|---|---|
| 构建 | NeoGradle | ForgeGradle 6.x |
| Java | 21 | 17 |
| 主类构造 | `(IEventBus, ModContainer)` | `()` 无参 |
| 配置类 | `ModConfigSpec` | `ForgeConfigSpec` |
| 事件总线 | `NeoForge.EVENT_BUS` | `MinecraftForge.EVENT_BUS` |
| TACZ 依赖 | Modrinth | CurseMaven |

## License

MIT
