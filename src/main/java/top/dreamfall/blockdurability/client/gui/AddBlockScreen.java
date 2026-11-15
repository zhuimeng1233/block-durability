package top.dreamfall.blockdurability.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 仿 Advanced XRay 风格的全局方块检索与添加弹窗 (Forge 1.20.1)。
 */
public class AddBlockScreen extends Screen {

    public record BlockItemInfo(Block block, String id, String name, ItemStack stack) {}

    private final BlockDurabilityScreen parentScreen;
    private final List<BlockItemInfo> allBlocks = new ArrayList<>();
    private final List<BlockItemInfo> filteredBlocks = new ArrayList<>();

    private EditBox searchBox;
    private int scrollOffset = 0;
    private static final int ITEM_ROW_HEIGHT = 26;

    public AddBlockScreen(BlockDurabilityScreen parentScreen) {
        super(Component.literal("选择要添加的方块"));
        this.parentScreen = parentScreen;
        buildBlockDatabase();
    }

    private void buildBlockDatabase() {
        allBlocks.clear();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block == Blocks.AIR) continue;
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
            if (key == null) continue;

            String id = key.toString();
            ItemStack stack = new ItemStack(block.asItem());
            String name = stack.isEmpty() ? block.getName().getString() : stack.getHoverName().getString();

            allBlocks.add(new BlockItemInfo(block, id, name, stack));
        }
        filterBlocks("");
    }

    private void filterBlocks(String query) {
        filteredBlocks.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (BlockItemInfo info : allBlocks) {
            if (q.isEmpty()
                    || info.id.toLowerCase(Locale.ROOT).contains(q)
                    || info.name.toLowerCase(Locale.ROOT).contains(q)) {
                filteredBlocks.add(info);
            }
        }
        scrollOffset = 0;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int winWidth = Math.min(320, this.width - 40);
        int winHeight = Math.min(260, this.height - 40);
        int winX = (this.width - winWidth) / 2;
        int winY = (this.height - winHeight) / 2;

        int bottomY = winY + winHeight - 26;
        int searchWidth = winWidth - 75;

        searchBox = new EditBox(this.font, winX + 8, bottomY, searchWidth, 18, Component.literal("搜索方块"));
        searchBox.setHint(Component.literal("输入方块名称或 ID..."));
        searchBox.setResponder(this::filterBlocks);
        this.addRenderableWidget(searchBox);

        this.addRenderableWidget(Button.builder(Component.literal("取消"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parentScreen);
            }
        }).bounds(winX + 8 + searchWidth + 6, bottomY, 52, 18).build());

        this.setInitialFocus(searchBox);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染标准背景底色
        this.renderBackground(guiGraphics);

        int winWidth = Math.min(320, this.width - 40);
        int winHeight = Math.min(260, this.height - 40);
        int winX = (this.width - winWidth) / 2;
        int winY = (this.height - winHeight) / 2;

        // 边框和黑色容器
        guiGraphics.fill(winX - 2, winY - 2, winX + winWidth + 2, winY + winHeight + 2, 0xFF444444);
        guiGraphics.fill(winX - 1, winY - 1, winX + winWidth + 1, winY + winHeight + 1, 0xFFB0B0B0);
        guiGraphics.fill(winX, winY, winX + winWidth, winY + winHeight, 0xFF141414);

        // 顶栏标题
        guiGraphics.drawString(this.font, "§l选择要添加的方块 (" + filteredBlocks.size() + ")", winX + 10, winY + 8, 0xFFFFFF, true);

        // 分割线
        guiGraphics.fill(winX + 8, winY + 22, winX + winWidth - 8, winY + 23, 0xFF333333);

        // 列表剪裁区域
        int listTop = winY + 25;
        int listBottom = winY + winHeight - 32;
        int listHeight = listBottom - listTop;
        int visibleCount = listHeight / ITEM_ROW_HEIGHT;

        int totalHeight = filteredBlocks.size() * ITEM_ROW_HEIGHT;
        int maxScroll = Math.max(0, totalHeight - listHeight);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        guiGraphics.enableScissor(winX + 4, listTop, winX + winWidth - 4, listBottom);

        int startIndex = scrollOffset / ITEM_ROW_HEIGHT;
        int endIndex = Math.min(filteredBlocks.size(), startIndex + visibleCount + 2);

        for (int i = startIndex; i < endIndex; i++) {
            BlockItemInfo info = filteredBlocks.get(i);
            int rowY = listTop + (i * ITEM_ROW_HEIGHT) - scrollOffset;

            if (rowY + ITEM_ROW_HEIGHT < listTop || rowY > listBottom) continue;

            boolean isHovered = mouseX >= winX + 6 && mouseX <= winX + winWidth - 14
                    && mouseY >= rowY && mouseY < rowY + ITEM_ROW_HEIGHT
                    && mouseY >= listTop && mouseY <= listBottom;

            if (isHovered) {
                guiGraphics.fill(winX + 6, rowY, winX + winWidth - 14, rowY + ITEM_ROW_HEIGHT, 0x2AFFFFFF);
            }

            // 渲染方块物品图标
            if (!info.stack.isEmpty()) {
                guiGraphics.renderItem(info.stack, winX + 8, rowY + 5);
            }

            // 渲染方块中文名称与 ID
            guiGraphics.drawString(this.font, info.name, winX + 30, rowY + 4, isHovered ? 0xFFFFAA : 0xFFFFFF, true);
            guiGraphics.drawString(this.font, info.id, winX + 30, rowY + 14, 0xFFAAAAAA, true);
        }

        guiGraphics.disableScissor();

        // 滚动条
        if (totalHeight > listHeight) {
            int scrollbarX = winX + winWidth - 10;
            guiGraphics.fill(scrollbarX, listTop, scrollbarX + 4, listBottom, 0xFF222222);

            int thumbHeight = Math.max(16, (int) ((float) listHeight / totalHeight * listHeight));
            int thumbY = listTop + (int) ((float) scrollOffset / maxScroll * (listHeight - thumbHeight));
            guiGraphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, 0xFF888888);
        }

        // 渲染输入框和取消按钮
        for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int winWidth = Math.min(320, this.width - 40);
        int winHeight = Math.min(260, this.height - 40);
        int winX = (this.width - winWidth) / 2;
        int winY = (this.height - winHeight) / 2;

        int listTop = winY + 25;
        int listBottom = winY + winHeight - 32;

        if (button == 0 && mouseX >= winX + 6 && mouseX <= winX + winWidth - 14
                && mouseY >= listTop && mouseY <= listBottom) {
            int clickedIndex = (int) (mouseY - listTop + scrollOffset) / ITEM_ROW_HEIGHT;
            if (clickedIndex >= 0 && clickedIndex < filteredBlocks.size()) {
                BlockItemInfo selected = filteredBlocks.get(clickedIndex);
                parentScreen.addBlockEntry(selected.id);
                if (this.minecraft != null) {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    this.minecraft.setScreen(this.parentScreen);
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0) {
            int winWidth = Math.min(320, this.width - 40);
            int winHeight = Math.min(260, this.height - 40);
            int listHeight = winHeight - 57;
            int totalHeight = filteredBlocks.size() * ITEM_ROW_HEIGHT;
            int maxScroll = Math.max(0, totalHeight - listHeight);

            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (delta * 26)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }
}
