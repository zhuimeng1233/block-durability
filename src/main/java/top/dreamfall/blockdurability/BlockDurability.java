package top.dreamfall.blockdurability;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

@Mod(BlockDurability.MOD_ID)
public class BlockDurability {
    public static final String MOD_ID = "blockdurability";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static GameRules.Key<GameRules.IntegerValue> RULE_HEAL_TIMEOUT_SECONDS =
            GameRules.register("blockDurabilityHealSeconds", GameRules.Category.MISC, GameRules.IntegerValue.create(120));

    public BlockDurability() {
        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BlockHPConfig.SPEC);

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

        // 补写配置文件中缺失的新增配置项
        updateConfigFile();
    }

    /**
     * 用当前 spec 修正配置文件，把新增的配置项补进去。
     */
    private void updateConfigFile() {
        try {
            Path configPath = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve(MOD_ID + "-common.toml");
            if (configPath == null) return;

            CommentedFileConfig config = CommentedFileConfig.builder(configPath)
                    .sync()
                    .autosave()
                    .build();

            config.load();
            BlockHPConfig.SPEC.correct(config);
            config.save();
            config.close();
            LOGGER.info("Config file synced with current specification.");
        } catch (Exception e) {
            LOGGER.warn("Failed to auto-update config file: {}", e.getMessage());
        }
    }
}
