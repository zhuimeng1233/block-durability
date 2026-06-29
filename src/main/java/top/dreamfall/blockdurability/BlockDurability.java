package top.dreamfall.blockdurability;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BlockDurability.MOD_ID)
public class BlockDurability {
    public static final String MOD_ID = "blockdurability";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public BlockDurability(IEventBus modEventBus, ModContainer container) {
        // 注册配置
        container.registerConfig(ModConfig.Type.COMMON, BlockHPConfig.SPEC);

        // 安全注册 TACZ 事件监听（TACZ 未安装时静默跳过）
        try {
            Class.forName("com.tacz.guns.api.event.server.AmmoHitBlockEvent");
            NeoForge.EVENT_BUS.register(new BlockHPEventHandler());
            LOGGER.info("TACZ detected - Block Durability event handler registered!");
        } catch (ClassNotFoundException e) {
            LOGGER.info("TACZ not found - Block Durability will wait for TACZ to be installed.");
        }
    }
}
