package top.dreamfall.blockdurability.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.dreamfall.blockdurability.BlockHPConfig;
import top.dreamfall.blockdurability.BlockHPManager;

import java.util.*;

/**
 * 专为 Block Durability 设计的可视化游戏内配置界面。
 * 支持方块分类图标动态轮播、白名单双列磁贴开关、自定义标签增删以及显式【保存并应用】逻辑。
 */
public class BlockDurabilityConfigScreen extends Screen {

    private final Screen parent;
    private int currentTab = 0; // 0 = 白名单可视化, 1 = 核心机制与参数

    // ==================== 白名单分类定义 ====================
    public record WhitelistCategory(
            String id,
            String name,
            String tooltip,
            List<String> tags,
            List<Item> displayItems
    ) {}

    private final List<WhitelistCategory> categories = new ArrayList<>();
    private final Set<String> activeCategories = new HashSet<>();
    private final List<String> customWhitelist = new ArrayList<>();
    private int customListPage = 0;

    // ==================== 数值与开关缓存 ====================
    private double hardnessMultiplier;
    private double minBlockHP;
    private double explosionMultiplier;
    private int healTimeoutSeconds;
    private boolean useBulletDamage;
    private boolean explosionEnabled;
    private boolean handleVanillaExplosions;

    // ==================== UI 控件 ====================
    private EditBox customInputBox;

    public BlockDurabilityConfigScreen(Screen parent) {
        super(Component.literal("Block Durability 配置中心"));
        this.parent = parent;
        initCategories();
        loadFromConfig();
    }

    private void initCategories() {
        categories.add(new WhitelistCategory(
                "logs", "原木类 (Logs)", "所有原木与去皮原木",
                List.of("tag:minecraft:logs"),
                List.of(Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG,
                        Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG)
        ));

        categories.add(new WhitelistCategory(
                "planks", "木板类 (Planks)", "所有木板与竹板",
                List.of("tag:minecraft:planks"),
                List.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS,
                        Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS, Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS, Items.BAMBOO_PLANKS)
        ));

        categories.add(new WhitelistCategory(
                "glass", "玻璃类 (Glass)", "所有玻璃块与玻璃板",
                List.of("tag:c:glass_blocks", "tag:c:glass_panes"),
                List.of(Items.GLASS, Items.TINTED_GLASS, Items.WHITE_STAINED_GLASS, Items.GLASS_PANE, Items.RED_STAINED_GLASS_PANE)
        ));

        categories.add(new WhitelistCategory(
                "leaves", "树叶类 (Leaves)", "所有树叶与杜鹃花丛",
                List.of("tag:minecraft:leaves"),
                List.of(Items.OAK_LEAVES, Items.SPRUCE_LEAVES, Items.BIRCH_LEAVES, Items.JUNGLE_LEAVES,
                        Items.ACACIA_LEAVES, Items.DARK_OAK_LEAVES, Items.MANGROVE_LEAVES, Items.CHERRY_LEAVES)
        ));

        categories.add(new WhitelistCategory(
                "doors", "木门与活板门 (Doors)", "所有木门与木活板门",
                List.of("tag:minecraft:wooden_doors", "tag:minecraft:wooden_trapdoors"),
                List.of(Items.OAK_DOOR, Items.SPRUCE_DOOR, Items.BIRCH_DOOR, Items.DARK_OAK_DOOR,
                        Items.OAK_TRAPDOOR, Items.SPRUCE_TRAPDOOR)
        ));

        categories.add(new WhitelistCategory(
                "fences", "栅栏与建材 (Fences)", "木栅栏、栅栏门、楼梯、台阶与按钮",
                List.of("tag:minecraft:fences", "tag:minecraft:fence_gates", "tag:minecraft:wooden_stairs",
                        "tag:minecraft:wooden_slabs", "tag:minecraft:wooden_buttons", "tag:minecraft:wooden_pressure_plates"),
                List.of(Items.OAK_FENCE, Items.SPRUCE_FENCE, Items.OAK_FENCE_GATE, Items.OAK_STAIRS, Items.OAK_SLAB)
        ));

        categories.add(new WhitelistCategory(
                "signs", "告示牌 (Signs)", "立式与悬挂式告示牌",
                List.of("tag:minecraft:standing_signs", "tag:minecraft:wall_signs",
                        "tag:minecraft:ceiling_hanging_signs", "tag:minecraft:wall_hanging_signs"),
                List.of(Items.OAK_SIGN, Items.SPRUCE_SIGN, Items.OAK_HANGING_SIGN, Items.CHERRY_HANGING_SIGN)
        ));

        categories.add(new WhitelistCategory(
                "wool", "羊毛与地毯 (Wool)", "所有羊毛方块与地毯",
                List.of("tag:minecraft:wool", "tag:minecraft:wool_carpets"),
                List.of(Items.WHITE_WOOL, Items.RED_WOOL, Items.BLUE_WOOL, Items.YELLOW_WOOL,
                        Items.GREEN_WOOL, Items.WHITE_CARPET)
        ));
    }

    private void loadFromConfig() {
        activeCategories.clear();
        customWhitelist.clear();

        List<? extends String> configList = BlockHPConfig.BLOCK_WHITELIST.get();
        Set<String> remainingEntries = new LinkedHashSet<>(configList);

        for (WhitelistCategory cat : categories) {
            boolean allPresent = true;
            for (String tag : cat.tags()) {
                if (!configList.contains(tag)) {
                    allPresent = false;
                    break;
                }
            }
            if (allPresent) {
                activeCategories.add(cat.id());
                cat.tags().forEach(remainingEntries::remove);
            }
        }

        customWhitelist.addAll(remainingEntries);

        hardnessMultiplier = BlockHPConfig.HARDNESS_MULTIPLIER.get();
        minBlockHP = BlockHPConfig.MIN_BLOCK_HP.get();
        explosionMultiplier = BlockHPConfig.EXPLOSION_DAMAGE_MULTIPLIER.get();
        healTimeoutSeconds = BlockHPConfig.HEAL_TIMEOUT_SECONDS.get();
        useBulletDamage = BlockHPConfig.USE_BULLET_DAMAGE.get();
        explosionEnabled = BlockHPConfig.EXPLOSION_ENABLED.get();
        handleVanillaExplosions = BlockHPConfig.HANDLE_VANILLA_EXPLOSIONS.get();
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int topY = 28;
        int tabBtnWidth = 140;

        // 顶栏标签页切换
        this.addRenderableWidget(Button.builder(
                Component.literal(currentTab == 0 ? "§a§l▶ 方块分类白名单" : "方块分类白名单"),
                btn -> { currentTab = 0; init(); }
        ).bounds(this.width / 2 - tabBtnWidth - 8, topY, tabBtnWidth, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal(currentTab == 1 ? "§a§l▶ 机制与数值参数" : "机制与数值参数"),
                btn -> { currentTab = 1; init(); }
        ).bounds(this.width / 2 + 8, topY, tabBtnWidth, 20).build());

        if (currentTab == 0) {
            initWhitelistTab();
        } else {
            initParamsTab();
        }

        // 底部显式操作栏（保存并生效 / 恢复默认 / 取消返回）
        int bottomY = this.height - 28;
        int actionBtnWidth = 100;
        int startX = this.width / 2 - (actionBtnWidth * 3 + 20) / 2;

        this.addRenderableWidget(Button.builder(
                Component.literal("§a§l✔ 保存并生效"),
                btn -> saveAndClose()
        ).bounds(startX, bottomY, actionBtnWidth, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("§e↺ 恢复默认"),
                btn -> resetToDefaults()
        ).bounds(startX + actionBtnWidth + 10, bottomY, actionBtnWidth, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("§c✕ 取消返回"),
                btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(this.parent);
                    }
                }
        ).bounds(startX + (actionBtnWidth + 10) * 2, bottomY, actionBtnWidth, 20).build());
    }

    private void initWhitelistTab() {
        int startY = 54;
        int colWidth = 175;
        int colGap = 16;
        int leftColX = this.width / 2 - colWidth - colGap / 2;
        int rightColX = this.width / 2 + colGap / 2;
        int rowHeight = 24;

        // 8 个双列卡片式排列 (4行)
        for (int i = 0; i < categories.size(); i++) {
            WhitelistCategory cat = categories.get(i);
            int colX = (i % 2 == 0) ? leftColX : rightColX;
            int rowY = startY + (i / 2) * (rowHeight + 4);
            boolean active = activeCategories.contains(cat.id());

            Button toggleBtn = Button.builder(
                    Component.literal(active ? "§a[已启用]" : "§7[已禁用]"),
                    btn -> {
                        if (activeCategories.contains(cat.id())) {
                            activeCategories.remove(cat.id());
                        } else {
                            activeCategories.add(cat.id());
                        }
                        init();
                    }
            ).bounds(colX + colWidth - 55, rowY, 52, 20).build();

            this.addRenderableWidget(toggleBtn);
        }

        // 自定义输入区
        int customY = startY + 4 * (rowHeight + 4) + 6;
        int totalWidth = colWidth * 2 + colGap;
        int inputWidth = totalWidth - 65;

        customInputBox = new EditBox(this.font, leftColX, customY, inputWidth, 20, Component.literal("自定义条目"));
        customInputBox.setHint(Component.literal("输入自定义方块ID或Tag (如 minecraft:obsidian)"));
        this.addRenderableWidget(customInputBox);

        this.addRenderableWidget(Button.builder(Component.literal("§b＋ 添加"), btn -> {
            String text = customInputBox.getValue().trim();
            if (!text.isEmpty() && !customWhitelist.contains(text)) {
                customWhitelist.add(text);
                customInputBox.setValue("");
                init();
            }
        }).bounds(leftColX + inputWidth + 5, customY, 60, 20).build());

        // 自定义条目列表展示（分页与删除按钮）
        int listY = customY + 24;
        int bottomY = this.height - 28;
        int maxCustomShown = Math.max(1, Math.min(4, (bottomY - listY - 8) / 20));

        int totalPages = Math.max(1, (int) Math.ceil((double) customWhitelist.size() / maxCustomShown));
        if (customListPage >= totalPages) customListPage = totalPages - 1;
        if (customListPage < 0) customListPage = 0;

        int startIndex = customListPage * maxCustomShown;
        int endIndex = Math.min(customWhitelist.size(), startIndex + maxCustomShown);

        for (int i = startIndex; i < endIndex; i++) {
            String entry = customWhitelist.get(i);
            int rowY = listY + (i - startIndex) * 20;
            this.addRenderableWidget(Button.builder(Component.literal("§c✕ 删除"), btn -> {
                customWhitelist.remove(entry);
                init();
            }).bounds(leftColX + totalWidth - 50, rowY, 48, 18).build());
        }

        // 分页按钮
        if (totalPages > 1) {
            int pageBtnY = listY + maxCustomShown * 20 + 2;
            if (pageBtnY < bottomY - 2) {
                if (customListPage > 0) {
                    this.addRenderableWidget(Button.builder(Component.literal("◀"), btn -> {
                        customListPage--;
                        init();
                    }).bounds(leftColX, pageBtnY, 24, 16).build());
                }
                if (customListPage < totalPages - 1) {
                    this.addRenderableWidget(Button.builder(Component.literal("▶"), btn -> {
                        customListPage++;
                        init();
                    }).bounds(leftColX + 30, pageBtnY, 24, 16).build());
                }
            }
        }
    }

    private void initParamsTab() {
        int startY = 60;
        int rowHeight = 25;
        int leftX = this.width / 2 - 140;

        // 硬度系数调节
        this.addRenderableWidget(Button.builder(Component.literal("- 0.1"), btn -> {
            hardnessMultiplier = Math.max(0.1, Math.round((hardnessMultiplier - 0.1) * 10.0) / 10.0);
            init();
        }).bounds(leftX + 180, startY, 42, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("+ 0.1"), btn -> {
            hardnessMultiplier = Math.min(10.0, Math.round((hardnessMultiplier + 0.1) * 10.0) / 10.0);
            init();
        }).bounds(leftX + 228, startY, 42, 20).build());

        // 爆炸伤害倍率
        int y1 = startY + rowHeight;
        this.addRenderableWidget(Button.builder(Component.literal("- 0.2"), btn -> {
            explosionMultiplier = Math.max(0.0, Math.round((explosionMultiplier - 0.2) * 10.0) / 10.0);
            init();
        }).bounds(leftX + 180, y1, 42, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("+ 0.2"), btn -> {
            explosionMultiplier = Math.min(10.0, Math.round((explosionMultiplier + 0.2) * 10.0) / 10.0);
            init();
        }).bounds(leftX + 228, y1, 42, 20).build());

        // 自愈超时秒数
        int y2 = startY + rowHeight * 2;
        this.addRenderableWidget(Button.builder(Component.literal("- 10s"), btn -> {
            healTimeoutSeconds = Math.max(0, healTimeoutSeconds - 10);
            init();
        }).bounds(leftX + 180, y2, 42, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("+ 10s"), btn -> {
            healTimeoutSeconds = Math.min(3600, healTimeoutSeconds + 10);
            init();
        }).bounds(leftX + 228, y2, 42, 20).build());

        // 枪械真实威力
        int y3 = startY + rowHeight * 3;
        this.addRenderableWidget(Button.builder(
                Component.literal(useBulletDamage ? "§a[ 真实威力 ]" : "§7[ 固定伤害 ]"),
                btn -> { useBulletDamage = !useBulletDamage; init(); }
        ).bounds(leftX + 180, y3, 90, 20).build());

        // 爆炸破坏总开关
        int y4 = startY + rowHeight * 4;
        this.addRenderableWidget(Button.builder(
                Component.literal(explosionEnabled ? "§a[ ✔ 开启 ]" : "§c[ ✕ 关闭 ]"),
                btn -> { explosionEnabled = !explosionEnabled; init(); }
        ).bounds(leftX + 180, y4, 90, 20).build());

        // 处理原版爆炸开关
        int y5 = startY + rowHeight * 5;
        this.addRenderableWidget(Button.builder(
                Component.literal(handleVanillaExplosions ? "§a[ ✔ 兼容原版 ]" : "§7[ 仅TaCZ ]"),
                btn -> { handleVanillaExplosions = !handleVanillaExplosions; init(); }
        ).bounds(leftX + 180, y5, 90, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染暗色半透明菜单底色
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 顶部标题
        guiGraphics.drawCenteredString(this.font, "§lBlock Durability 方块耐久配置", this.width / 2, 10, 0xFFFFFF);

        if (currentTab == 0) {
            renderWhitelistVisuals(guiGraphics);
        } else {
            renderParamsVisuals(guiGraphics);
        }
    }

    private void renderWhitelistVisuals(GuiGraphics guiGraphics) {
        int startY = 54;
        int colWidth = 175;
        int colGap = 16;
        int leftColX = this.width / 2 - colWidth - colGap / 2;
        int rightColX = this.width / 2 + colGap / 2;
        int rowHeight = 24;

        long time = System.currentTimeMillis();

        for (int i = 0; i < categories.size(); i++) {
            WhitelistCategory cat = categories.get(i);
            int colX = (i % 2 == 0) ? leftColX : rightColX;
            int rowY = startY + (i / 2) * (rowHeight + 4);

            // 槽位凹槽背景
            guiGraphics.fill(colX - 1, rowY - 1, colX + 21, rowY + 21, 0xFF555555);
            guiGraphics.fill(colX, rowY, colX + 20, rowY + 20, 0xFF1E1E1E);

            // 动态轮播图标（每 1.2 秒自动切换同类下一个方块物品）
            if (!cat.displayItems().isEmpty()) {
                int itemIndex = (int) ((time / 1200) % cat.displayItems().size());
                ItemStack stack = new ItemStack(cat.displayItems().get(itemIndex));
                guiGraphics.renderItem(stack, colX + 2, rowY + 2);
            }

            // 分类标题与说明
            guiGraphics.drawString(this.font, cat.name(), colX + 25, rowY + 2, 0xFFFFFF);
            guiGraphics.drawString(this.font, "§8" + cat.tooltip(), colX + 25, rowY + 12, 0x888888);
        }

        // 自定义条目列表
        int customY = startY + 4 * (rowHeight + 4) + 6;
        int listY = customY + 24;
        int bottomY = this.height - 28;
        int maxCustomShown = Math.max(1, Math.min(4, (bottomY - listY - 8) / 20));

        int startIndex = customListPage * maxCustomShown;
        int endIndex = Math.min(customWhitelist.size(), startIndex + maxCustomShown);

        for (int i = startIndex; i < endIndex; i++) {
            String entry = customWhitelist.get(i);
            int rowY = listY + (i - startIndex) * 20;
            guiGraphics.drawString(this.font, "§e• " + entry, leftColX + 2, rowY + 5, 0xFFFF55);
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) customWhitelist.size() / maxCustomShown));
        if (totalPages > 1) {
            int pageBtnY = listY + maxCustomShown * 20 + 2;
            if (pageBtnY < bottomY - 2) {
                guiGraphics.drawString(this.font, "§7页码: " + (customListPage + 1) + "/" + totalPages,
                        leftColX + 60, pageBtnY + 4, 0xAAAAAA);
            }
        }
    }

    private void renderParamsVisuals(GuiGraphics guiGraphics) {
        int startY = 60;
        int rowHeight = 25;
        int leftX = this.width / 2 - 140;

        guiGraphics.drawString(this.font, "§f方块硬度系数倍率:", leftX, startY + 6, 0xFFFFFF);
        guiGraphics.drawString(this.font, "§e" + String.format("%.1f", hardnessMultiplier) + "x", leftX + 130, startY + 6, 0xFFFF55);

        int y1 = startY + rowHeight;
        guiGraphics.drawString(this.font, "§f爆炸伤害破坏倍率:", leftX, y1 + 6, 0xFFFFFF);
        guiGraphics.drawString(this.font, "§e" + String.format("%.1f", explosionMultiplier) + "x", leftX + 130, y1 + 6, 0xFFFF55);

        int y2 = startY + rowHeight * 2;
        guiGraphics.drawString(this.font, "§f脱战自愈时间 (秒):", leftX, y2 + 6, 0xFFFFFF);
        guiGraphics.drawString(this.font, "§e" + healTimeoutSeconds + " 秒", leftX + 130, y2 + 6, 0xFFFF55);

        int y3 = startY + rowHeight * 3;
        guiGraphics.drawString(this.font, "§f枪械子弹伤害计算:", leftX, y3 + 6, 0xFFFFFF);

        int y4 = startY + rowHeight * 4;
        guiGraphics.drawString(this.font, "§f爆炸波及耐久机制:", leftX, y4 + 6, 0xFFFFFF);

        int y5 = startY + rowHeight * 5;
        guiGraphics.drawString(this.font, "§f原版与模组爆炸支持:", leftX, y5 + 6, 0xFFFFFF);
    }

    private void saveAndClose() {
        // 汇总结算新白名单
        List<String> newWhitelist = new ArrayList<>();
        for (WhitelistCategory cat : categories) {
            if (activeCategories.contains(cat.id())) {
                newWhitelist.addAll(cat.tags());
            }
        }
        for (String custom : customWhitelist) {
            if (!newWhitelist.contains(custom)) {
                newWhitelist.add(custom);
            }
        }

        // 保存所有参数
        BlockHPConfig.BLOCK_WHITELIST.set(newWhitelist);
        BlockHPConfig.HARDNESS_MULTIPLIER.set(hardnessMultiplier);
        BlockHPConfig.EXPLOSION_DAMAGE_MULTIPLIER.set(explosionMultiplier);
        BlockHPConfig.HEAL_TIMEOUT_SECONDS.set(healTimeoutSeconds);
        BlockHPConfig.USE_BULLET_DAMAGE.set(useBulletDamage);
        BlockHPConfig.EXPLOSION_ENABLED.set(explosionEnabled);
        BlockHPConfig.HANDLE_VANILLA_EXPLOSIONS.set(handleVanillaExplosions);

        // 显式持久化写盘与热刷新
        BlockHPConfig.SPEC.save();
        BlockHPManager.invalidateCache();

        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.minecraft.setScreen(this.parent);
        }
    }

    private void resetToDefaults() {
        activeCategories.clear();
        for (WhitelistCategory cat : categories) {
            if (!cat.id().equals("wool")) {
                activeCategories.add(cat.id());
            }
        }
        customWhitelist.clear();

        hardnessMultiplier = 1.5;
        explosionMultiplier = 1.0;
        healTimeoutSeconds = 120;
        useBulletDamage = false;
        explosionEnabled = true;
        handleVanillaExplosions = true;

        init();
    }
}
