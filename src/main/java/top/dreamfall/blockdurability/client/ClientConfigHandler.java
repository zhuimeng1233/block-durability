package top.dreamfall.blockdurability.client;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import top.dreamfall.blockdurability.client.gui.BlockDurabilityScreen;

/**
 * 客户端配置界面与指令注册处理器 (Forge 1.20.1，物理端隔离，防服务端崩溃)。
 */
public class ClientConfigHandler {

    public static void register() {
        // 注册模组菜单 (Mods List) 中的 [配置] 按钮为仿 XRay 风格可视化管理面板
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parentScreen) -> new BlockDurabilityScreen(parentScreen)
                )
        );

        // 注册客户端快捷指令 /bd config 与 /blockdurability
        MinecraftForge.EVENT_BUS.addListener(ClientConfigHandler::onRegisterClientCommands);
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("blockdurability")
                        .then(Commands.literal("config")
                                .executes(ctx -> {
                                    openConfigScreen();
                                    return 1;
                                }))
                        .executes(ctx -> {
                            openConfigScreen();
                            return 1;
                        })
        );

        event.getDispatcher().register(
                Commands.literal("bd")
                        .then(Commands.literal("config")
                                .executes(ctx -> {
                                    openConfigScreen();
                                    return 1;
                                }))
                        .executes(ctx -> {
                            openConfigScreen();
                            return 1;
                        })
        );
    }

    private static void openConfigScreen() {
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> mc.setScreen(new BlockDurabilityScreen(mc.screen)));
    }
}
