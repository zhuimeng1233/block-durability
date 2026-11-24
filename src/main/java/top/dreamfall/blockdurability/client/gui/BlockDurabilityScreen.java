package top.dreamfall.blockdurability.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import top.dreamfall.blockdurability.BlockHPConfig;
import top.dreamfall.blockdurability.BlockHPManager;

import java.util.*;

/**
 * 专为 Block Durability 设计的可视化方块耐久管理中心。
 * 界面交互风格深度参考 Advanced XRay，提供 3D 图标预览、模糊搜索、一键手持/目视添加与直观的配置工具箱。
 */
public class BlockDurabilityScreen extends Screen {

    public record WhitelistItem(String rawEntry, String title, String subtitle, ItemStack icon, String hpText) {}

    private final Screen parentScreen;

    // 白名单列表数据
    private final List<String> currentWhitelist = new ArrayList<>();
    private final List<WhitelistItem> displayItems = new ArrayList<>();
    private final List<WhitelistItem> filteredItems = new ArrayList<>();

    // 核心参数临时变量
    private boolean enabled;
    private boolean explosionEnabled;
    private boolean useBulletDamage;
    private double hardnessMultiplier;
    private int healTimeoutSeconds;

    // UI 组件与状态
    private EditBox searchBox;
    private int scrollOffset = 0;
    private static final int ROW_HEIGHT = 30;

    private String statusFeedback = "";
    private int statusFeedbackColor = 0x55FF55;
    private long statusFeedbackTime = 0;

    public BlockDurabilityScreen(Screen parentScreen) {
        super(Component.literal("Block Durability 管理中心"));
        this.parentScreen = parentScreen;
        loadConfig();
    }

    private void loadConfig() {
        currentWhitelist.clear();
        currentWhitelist.addAll(BlockHPConfig.BLOCK_WHITELIST.get());

        enabled = BlockHPConfig.ENABLED.get();
        explosionEnabled = BlockHPConfig.EXPLOSION_ENABLED.get();
        useBulletDamage = BlockHPConfig.USE_BULLET_DAMAGE.get();
        hardnessMultiplier = BlockHPConfig.HARDNESS_MULTIPLIER.get();
        healTimeoutSeconds = BlockHPConfig.HEAL_TIMEOUT_SECONDS.get();

        rebuildDisplayItems();
    }

    public void addBlockEntry(String entry) {
        String trimmed = entry.trim();
        if (!trimmed.isEmpty() && !currentWhitelist.contains(trimmed)) {
            currentWhitelist.add(0, trimmed); // 插入最前面方便查看
            rebuildDisplayItems();
            saveConfig(); // 立即自动保存并落盘
            showStatus("已添加方块并生效: " + trimmed, 0x55FF55);
        } else {
            showStatus("条目已存在或无效", 0xFFAAAA);
        }
    }

    private void rebuildDisplayItems() {
        displayItems.clear();
        for (String raw : currentWhitelist) {
            displayItems.add(resolveItemInfo(raw));
        }
        filterItems(searchBox != null ? searchBox.getValue() : "");
    }

    private WhitelistItem resolveItemInfo(String raw) {
        if (raw.startsWith("tag:")) {
            String tagId = raw.substring(4).trim();
            ResourceLocation rl = ResourceLocation.tryParse(tagId);
            ItemStack icon = new ItemStack(Items.CHEST);
            String title = "#" + tagId;

            if (rl != null) {
                TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, rl);
                var namedGroup = BuiltInRegistries.BLOCK.getTag(tagKey);
                if (namedGroup.isPresent()) {
                    for (Holder<Block> holder : namedGroup.get()) {
                        ItemStack s = new ItemStack(holder.value().asItem());
                        if (!s.isEmpty()) {
                            icon = s;
                            break;
                        }
                    }
                }
            }
            return new WhitelistItem(raw, title, "标签组 (Tag)", icon, "标签集合");
        }

        // 普通方块 ID 或通配符
        ResourceLocation rl = ResourceLocation.tryParse(raw);
        if (rl != null && BuiltInRegistries.BLOCK.containsKey(rl)) {
            Block block = BuiltInRegistries.BLOCK.get(rl);
            ItemStack stack = new ItemStack(block.asItem());
            String title = stack.isEmpty() ? block.getName().getString() : stack.getHoverName().getString();
            float hardness = block.defaultDestroyTime();
            float hp = (float) Math.max(1.0, hardness * hardnessMultiplier);
            String hpText = String.format("%.1f HP", hp);
            return new WhitelistItem(raw, title, raw, stack, hpText);
        }

        // 通配符或其他
        return new WhitelistItem(raw, "通配符: " + raw, raw, new ItemStack(Items.BARRIER), "公式计算");
    }

    private void filterItems(String query) {
        filteredItems.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (WhitelistItem item : displayItems) {
            if (q.isEmpty()
                    || item.title.toLowerCase(Locale.ROOT).contains(q)
                    || item.subtitle.toLowerCase(Locale.ROOT).contains(q)
                    || item.rawEntry.toLowerCase(Locale.ROOT).contains(q)) {
                filteredItems.add(item);
            }
        }
        scrollOffset = 0;
    }

    private void showStatus(String text, int color) {
        this.statusFeedback = text;
        this.statusFeedbackColor = color;
        this.statusFeedbackTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int margin = 12;
        int toolWidth = 145;
        int leftWidth = this.width - toolWidth - margin * 3;
        int listTop = 32;
        int listBottom = this.height - 14;

        // 左侧搜索框
        searchBox = new EditBox(this.font, margin + 4, listTop + 4, leftWidth - 8, 18, Component.literal("搜索"));
        searchBox.setHint(Component.literal("搜索白名单方块、ID 或 Tag..."));
        searchBox.setResponder(this::filterItems);
        this.addRenderableWidget(searchBox);

        // 右侧工具箱栏
        int toolX = this.width - toolWidth - margin;
        int btnY = listTop + 4;
        int btnHeight = 20;
        int spacing = 4;

        // 1. 添加方块
        this.addRenderableWidget(Button.builder(Component.literal("§b＋ 添加方块..."), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new AddBlockScreen(this));
            }
        }).bounds(toolX, btnY, toolWidth, btnHeight).build());
        btnY += btnHeight + spacing;

        // 2. 添加手持方块
        this.addRenderableWidget(Button.builder(Component.literal("§e✋ 添加手持方块"), btn -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                ItemStack handStack = this.minecraft.player.getMainHandItem();
                if (handStack.getItem() instanceof BlockItem bi) {
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(bi.getBlock());
                    if (id != null) {
                        addBlockEntry(id.toString());
                        return;
                    }
                }
            }
            showStatus("主手未持有任何方块！", 0xFF6666);
        }).bounds(toolX, btnY, toolWidth, btnHeight).build());
        btnY += btnHeight + spacing;

        // 3. 添加目视方块
        this.addRenderableWidget(Button.builder(Component.literal("§a👁 添加目视方块"), btn -> {
            if (this.minecraft != null && this.minecraft.level != null && this.minecraft.hitResult instanceof BlockHitResult bhr) {
                if (bhr.getType() == HitResult.Type.BLOCK) {
                    BlockState state = this.minecraft.level.getBlockState(bhr.getBlockPos());
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (id != null) {
                        addBlockEntry(id.toString());
                        return;
                    }
                }
            }
            showStatus("准星未对准任何方块！", 0xFF6666);
        }).bounds(toolX, btnY, toolWidth, btnHeight).build());
        btnY += btnHeight + spacing;

        // 4. 一键导入常用预设
        this.addRenderableWidget(Button.builder(Component.literal("§d📦 导入常用预设组"), btn -> {
            addPresetTags();
        }).bounds(toolX, btnY, toolWidth, btnHeight).build());
        btnY += btnHeight + spacing + 6;

        // 5. 爆炸破坏开关
        this.addRenderableWidget(Button.builder(
                Component.literal(explosionEnabled ? "爆炸破坏: §a开启" : "爆炸破坏: §c关闭"),
                btn -> {
                    explosionEnabled = !explosionEnabled;
                    saveConfig();
                    init();
                }
        ).bounds(toolX, btnY, toolWidth, btnHeight).build());
        btnY += btnHeight + spacing;

        // 6. 子弹伤害模式
        this.addRenderableWidget(Button.builder(
                Component.literal(useBulletDamage ? "子弹威力: §a真实" : "子弹威力: §7固定"),
                btn -> {
                    useBulletDamage = !useBulletDamage;
                    saveConfig();
                    init();
                }
        ).bounds(toolX, btnY, toolWidth, btnHeight).build());
        btnY += btnHeight + spacing;

        // 7. 硬度乘数调整
        this.addRenderableWidget(Button.builder(
                Component.literal("硬度乘数: §e" + String.format("%.1f", hardnessMultiplier) + "x"),
                btn -> {
                    hardnessMultiplier = Math.round((hardnessMultiplier + 0.5) * 10.0) / 10.0;
                    if (hardnessMultiplier > 5.0) hardnessMultiplier = 0.5;
                    saveConfig();
                    rebuildDisplayItems();
                    init();
                }
        ).bounds(toolX, btnY, toolWidth, btnHeight).build());
        btnY += btnHeight + spacing;

        // 8. 自愈超时调整
        this.addRenderableWidget(Button.builder(
                Component.literal("自愈时间: §e" + healTimeoutSeconds + "s"),
                btn -> {
                    healTimeoutSeconds += 30;
                    if (healTimeoutSeconds > 300) healTimeoutSeconds = 0;
                    saveConfig();
                    init();
                }
        ).bounds(toolX, btnY, toolWidth, btnHeight).build());

        // 底部：保存与关闭
        int bottomActionY = listBottom - btnHeight;
        this.addRenderableWidget(Button.builder(Component.literal("§a§l✔ 保存并生效"), btn -> {
            saveConfig();
        }).bounds(toolX, bottomActionY - btnHeight - 4, toolWidth, btnHeight).build());

        this.addRenderableWidget(Button.builder(Component.literal("✕ 关闭"), btn -> {
            onClose();
        }).bounds(toolX, bottomActionY, toolWidth, btnHeight).build());
    }

    private void addPresetTags() {
        List<String> presets = List.of(
                "tag:c:glass_blocks", "tag:c:glass_panes",
                "tag:minecraft:planks", "tag:minecraft:logs",
                "tag:minecraft:leaves", "tag:minecraft:wooden_doors",
                "tag:minecraft:fences", "tag:minecraft:fence_gates"
        );
        int added = 0;
        for (String p : presets) {
            if (!currentWhitelist.contains(p)) {
                currentWhitelist.add(p);
                added++;
            }
        }
        rebuildDisplayItems();
        saveConfig();
        showStatus("已导入 " + added + " 个常用预设标签并生效", 0x55FF55);
    }

    private void saveConfig() {
        boolean ok = BlockHPConfig.saveAndPersist(
                currentWhitelist,
                enabled,
                explosionEnabled,
                useBulletDamage,
                hardnessMultiplier,
                healTimeoutSeconds
        );

        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, ok ? 1.2F : 0.6F));
        }
        if (ok) {
            showStatus("✔ 配置已保存并立即生效！", 0x55FF55);
        } else {
            showStatus("❌ 保存失败，请检查日志", 0xFFAAAA);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1. 只渲染底层磨砂背景，绝不让前景自定义面板被二次模糊覆盖
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int margin = 12;
        int toolWidth = 145;
        int leftWidth = this.width - toolWidth - margin * 3;
        int listTop = 32;
        int listBottom = this.height - 14;

        // 顶栏主标题（高对比度，开启阴影）
        guiGraphics.drawString(this.font, "§lBlock Durability 方块耐久管理中心", margin + 2, 12, 0xFFFFFF, true);

        // 临时状态反馈通知
        if (!statusFeedback.isEmpty() && System.currentTimeMillis() - statusFeedbackTime < 3000) {
            guiGraphics.drawString(this.font, statusFeedback, margin + 230, 12, statusFeedbackColor, true);
        }

        // ==================== 左侧方块列表容器 ====================
        guiGraphics.fill(margin - 1, listTop - 1, margin + leftWidth + 1, listBottom + 1, 0xFF444444);
        guiGraphics.fill(margin, listTop, margin + leftWidth, listBottom, 0xFF141414);

        int contentTop = listTop + 26;
        int contentHeight = listBottom - contentTop;
        int totalHeight = filteredItems.size() * ROW_HEIGHT;
        int maxScroll = Math.max(0, totalHeight - contentHeight);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        // 列表剪裁
        guiGraphics.enableScissor(margin, contentTop, margin + leftWidth, listBottom);

        int startIndex = scrollOffset / ROW_HEIGHT;
        int visibleCount = contentHeight / ROW_HEIGHT + 2;
        int endIndex = Math.min(filteredItems.size(), startIndex + visibleCount);

        for (int i = startIndex; i < endIndex; i++) {
            WhitelistItem item = filteredItems.get(i);
            int rowY = contentTop + (i * ROW_HEIGHT) - scrollOffset;

            if (rowY + ROW_HEIGHT < contentTop || rowY > listBottom) continue;

            boolean isHovered = mouseX >= margin + 2 && mouseX <= margin + leftWidth - 8
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
                    && mouseY >= contentTop && mouseY <= listBottom;

            if (isHovered) {
                guiGraphics.fill(margin + 2, rowY, margin + leftWidth - 8, rowY + ROW_HEIGHT, 0x2AFFFFFF);
            }

            // 渲染方块 3D 物品图标
            if (!item.icon.isEmpty()) {
                guiGraphics.renderItem(item.icon, margin + 6, rowY + 7);
            }

            // 标题与副标题（开启 dropShadow，高对比度纯白 + 浅灰）
            guiGraphics.drawString(this.font, item.title, margin + 28, rowY + 5, isHovered ? 0xFFFFAA : 0xFFFFFF, true);
            guiGraphics.drawString(this.font, item.subtitle, margin + 28, rowY + 16, 0xFFAAAAAA, true);

            // 耐久指示器（亮绿带阴影）
            int hpWidth = this.font.width(item.hpText);
            int hpX = margin + leftWidth - 36 - hpWidth;
            guiGraphics.drawString(this.font, item.hpText, hpX, rowY + 10, 0xFF55FF55, true);

            // 删除按钮 (✕)
            boolean isDelHovered = mouseX >= margin + leftWidth - 26 && mouseX <= margin + leftWidth - 12
                    && mouseY >= rowY + 7 && mouseY <= rowY + 21
                    && mouseY >= contentTop && mouseY <= listBottom;

            guiGraphics.fill(margin + leftWidth - 26, rowY + 7, margin + leftWidth - 12, rowY + 21,
                    isDelHovered ? 0xCCFF4444 : 0x88FF2222);
            guiGraphics.drawCenteredString(this.font, "✕", margin + leftWidth - 19, rowY + 10, 0xFFFFFF);
        }

        guiGraphics.disableScissor();

        // 左侧滚动条
        if (totalHeight > contentHeight) {
            int scrollbarX = margin + leftWidth - 6;
            guiGraphics.fill(scrollbarX, contentTop, scrollbarX + 3, listBottom, 0xFF222222);

            int thumbHeight = Math.max(16, (int) ((float) contentHeight / totalHeight * contentHeight));
            int thumbY = contentTop + (int) ((float) scrollOffset / maxScroll * (contentHeight - thumbHeight));
            guiGraphics.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbHeight, 0xFF888888);
        }

        // ==================== 右侧工具面板容器 ====================
        int toolX = this.width - toolWidth - margin;
        guiGraphics.fill(toolX - 1, listTop - 1, toolX + toolWidth + 1, listBottom + 1, 0xFF444444);
        guiGraphics.fill(toolX, listTop, toolX + toolWidth, listBottom, 0xFF181818);

        // 渲染顶层交互控件（按钮与搜索框），避免 super.render() 重新触发背景模糊覆盖
        for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int margin = 12;
        int toolWidth = 145;
        int leftWidth = this.width - toolWidth - margin * 3;
        int listTop = 32;
        int contentTop = listTop + 26;
        int listBottom = this.height - 14;

        if (button == 0 && mouseX >= margin && mouseX <= margin + leftWidth
                && mouseY >= contentTop && mouseY <= listBottom) {
            int clickedIndex = (int) (mouseY - contentTop + scrollOffset) / ROW_HEIGHT;
            if (clickedIndex >= 0 && clickedIndex < filteredItems.size()) {
                WhitelistItem item = filteredItems.get(clickedIndex);
                int rowY = contentTop + (clickedIndex * ROW_HEIGHT) - scrollOffset;

                // 检查是否点击了删除按钮
                if (mouseX >= margin + leftWidth - 26 && mouseX <= margin + leftWidth - 12
                        && mouseY >= rowY + 7 && mouseY <= rowY + 21) {
                    currentWhitelist.remove(item.rawEntry);
                    rebuildDisplayItems();
                    saveConfig(); // 删除后立即自动保存并落盘
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.8F));
                    }
                    showStatus("已移除并生效: " + item.rawEntry, 0xFFAAAA);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            int margin = 12;
            int toolWidth = 145;
            int leftWidth = this.width - toolWidth - margin * 3;
            if (mouseX >= margin && mouseX <= margin + leftWidth) {
                int listTop = 32;
                int contentTop = listTop + 26;
                int listBottom = this.height - 14;
                int contentHeight = listBottom - contentTop;
                int totalHeight = filteredItems.size() * ROW_HEIGHT;
                int maxScroll = Math.max(0, totalHeight - contentHeight);

                scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 30)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        // 关闭界面前无条件再执行一次持久化落盘，确保无论 ESC 还是点击关闭都绝不丢配置
        saveConfig();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }
}
