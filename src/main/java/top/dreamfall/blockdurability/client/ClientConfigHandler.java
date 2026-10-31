package top.dreamfall.blockdurability.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 客户端配置界面注册处理器（物理端隔离，防服务端崩溃）。
 */
public class ClientConfigHandler {

    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
