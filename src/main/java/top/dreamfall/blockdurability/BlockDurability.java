package top.dreamfall.blockdurability;

import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.dreamfall.blockdurability.client.ClientConfigHandler;

@Mod(BlockDurability.MOD_ID)
public class BlockDurability {
    public static final String MOD_ID = "blockdurability";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // 注册游戏规则：/gamerule blockDurabilityHealSeconds <秒数> (-1 表示跟随配置文件)
    public static final GameRules.Key<GameRules.IntegerValue> RULE_BLOCK_HEAL_SECONDS =
            GameRules.register("blockDurabilityHealSeconds", GameRules.Category.MISC, GameRules.IntegerValue.create(-1));

    public BlockDurability() {
        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BlockHPConfig.SPEC);

        // 客户端安全注册游戏内配置界面与快捷指令
        if (FMLEnvironment.dist.isClient()) {
            ClientConfigHandler.register();
        }

        // 监听配置加载与重载事件，确保热重载后白名单和血量覆盖即时生效
        FMLJavaModLoadingContext.get().getModEventBus().addListener((ModConfigEvent.Loading event) -> {
            if (event.getConfig().getSpec() == BlockHPConfig.SPEC) {
                BlockHPManager.invalidateCache();
            }
        });
        FMLJavaModLoadingContext.get().getModEventBus().addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == BlockHPConfig.SPEC) {
                BlockHPManager.invalidateCache();
            }
        });

        // 核心事件监听（爆炸、自愈、区块生命周期）：始终注册，无 TaCZ 时亦可独立运行
        MinecraftForge.EVENT_BUS.register(new BlockHPEventHandler());

        // 枪械命中监听：安全条件注册
        try {
            Class.forName("com.tacz.guns.api.event.server.AmmoHitBlockEvent");
            MinecraftForge.EVENT_BUS.register(new TaczHitHandler());
            LOGGER.info("TACZ detected - Block Durability gun hit handler registered!");
        } catch (ClassNotFoundException e) {
            LOGGER.info("TACZ not detected - Block Durability will run as standalone explosion durability mod.");
        }
    }
}
