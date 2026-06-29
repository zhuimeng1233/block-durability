package top.dreamfall.blockdurability;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BlockDurability.MOD_ID)
public class BlockDurability {
    public static final String MOD_ID = "blockdurability";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public BlockDurability() {
        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BlockHPConfig.SPEC);

        // 安全注册 TACZ 事件监听（TACZ 未安装时静默跳过）
        try {
            Class.forName("com.tacz.guns.api.event.server.AmmoHitBlockEvent");
            MinecraftForge.EVENT_BUS.register(new BlockHPEventHandler());
            LOGGER.info("TACZ detected - Block Durability event handler registered!");
        } catch (ClassNotFoundException e) {
            LOGGER.info("TACZ not found - Block Durability will wait for TACZ to be installed.");
        }
    }
}
