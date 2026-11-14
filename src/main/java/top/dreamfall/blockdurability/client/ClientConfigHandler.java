package top.dreamfall.blockdurability.client;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 客户端配置界面与指令注册处理器（物理端隔离，防服务端崩溃）。
 */
public class ClientConfigHandler {

    public static void register(ModContainer container) {
        // 注册模组菜单 (Mods List) 中的 [配置] 按钮为 NeoForge 原生配置界面
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // 注册游戏内客户端快捷指令 /bd config 与 /blockdurability 直接呼出原生配置界面
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> {
            event.getDispatcher().register(
                    Commands.literal("blockdurability")
                            .then(Commands.literal("config")
                                    .executes(ctx -> {
                                        openConfigScreen(container);
                                        return 1;
                                    }))
                            .executes(ctx -> {
                                openConfigScreen(container);
                                return 1;
                            })
            );

            event.getDispatcher().register(
                    Commands.literal("bd")
                            .then(Commands.literal("config")
                                    .executes(ctx -> {
                                        openConfigScreen(container);
                                        return 1;
                                    }))
                            .executes(ctx -> {
                                openConfigScreen(container);
                                return 1;
                            })
            );
        });
    }

    private static void openConfigScreen(ModContainer container) {
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> mc.setScreen(new ConfigurationScreen(container, mc.screen)));
    }
}
