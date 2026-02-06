package com.masuary.entitylibrary.client.screen;

import com.masuary.entitylibrary.EntityLibraryMod;
import com.masuary.entitylibrary.client.data.FavoritesManager;
import com.masuary.entitylibrary.client.data.LootTableParser;
import com.masuary.entitylibrary.client.data.Theme;
import com.masuary.entitylibrary.client.data.ThemeManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

public class EntityLibraryScreen extends Screen {
    private static final int LIST_LEFT = 10;
    private static final int LIST_WIDTH = 230;
    private static final String TRANSLATION_PREFIX = "screen." + EntityLibraryMod.MODID + ".";

    private static final Map<ResourceLocation, Boolean> livingTypeCache = new HashMap<>();
    private static final Map<ResourceLocation, MobCategory> categoryCache = new HashMap<>();
    private static final Map<ResourceLocation, String> displayNameCache = new HashMap<>();

    private enum FilterCategory {
        ALL, LIVING, HOSTILE, PASSIVE, FAVORITES;

        FilterCategory next() {
            FilterCategory[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }

        String translationKey() {
            return switch (this) {
                case ALL -> TRANSLATION_PREFIX + "filter.all";
                case LIVING -> TRANSLATION_PREFIX + "filter.living";
                case HOSTILE -> TRANSLATION_PREFIX + "filter.hostile";
                case PASSIVE -> TRANSLATION_PREFIX + "filter.passive";
                case FAVORITES -> TRANSLATION_PREFIX + "filter.favorites";
            };
        }
    }

    private final Screen previous;

    private EditBox searchBox;
    private EntityTypeSelectionList list;

    private final List<ResourceLocation> allEntityIds = new ArrayList<>();
    private final List<ResourceLocation> filteredEntityIds = new ArrayList<>();

    private FilterCategory currentFilter = FilterCategory.LIVING;
    private List<String> availableNamespaces = new ArrayList<>();
    private int currentNamespaceIndex = 0;

    @Nullable
    private ResourceLocation selectedId;

    @Nullable
    private Entity previewEntity;

    @Nullable
    private Component previewError;

    private int previewScale = 40;
    private boolean userScaled = false;

    private float previewYaw = 0.0F;
    private float previewPitch = 0.0F;
    private boolean isDraggingPreview = false;

    private int rightPanelLeft;
    private int rightPanelRight;
    private int rightPanelTop;
    private int rightPanelBottom;

    private String cachedDisplayName = "";
    private String cachedRegistryId = "";
    private String cachedSummonCommand = "";
    private String cachedMaxHealth = "";
    private String cachedDimensions = "";
    private String cachedCategory = "";
    private String cachedDrops = "";

    private boolean animatePreview = false;

    private Button filterButton;
    private Button namespaceButton;
    private Button copyButton;
    private Button resetScaleButton;
    private Button nbtButton;
    private Button favoriteButton;
    private Button animateButton;
    private Button themeButton;

    public EntityLibraryScreen(@Nullable Screen previous) {
        super(new TranslatableComponent(TRANSLATION_PREFIX + "title"));
        this.previous = previous;
    }

    public EntityLibraryScreen() {
        this(null);
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();

        allEntityIds.clear();
        allEntityIds.addAll(ForgeRegistries.ENTITIES.getKeys());
        allEntityIds.sort(Comparator
                .comparing(ResourceLocation::getNamespace)
                .thenComparing(ResourceLocation::getPath));

        TreeSet<String> namespaces = new TreeSet<>();
        for (ResourceLocation id : allEntityIds) {
            namespaces.add(id.getNamespace());
        }
        availableNamespaces = new ArrayList<>(namespaces);

        rightPanelLeft = LIST_LEFT + LIST_WIDTH + 16;
        rightPanelRight = this.width - 10;
        rightPanelTop = 14;
        rightPanelBottom = this.height - 10;

        this.searchBox = new EditBox(this.font, LIST_LEFT, 18, LIST_WIDTH, 18,
                new TranslatableComponent(TRANSLATION_PREFIX + "search"));
        this.searchBox.setMaxLength(128);
        this.searchBox.setResponder(s -> this.applyFilter());
        this.addRenderableWidget(this.searchBox);

        int filterRowY = 38;
        int halfWidth = (LIST_WIDTH - 2) / 2;

        this.filterButton = new Button(
                LIST_LEFT, filterRowY, halfWidth, 20,
                new TranslatableComponent(currentFilter.translationKey()),
                b -> {
                    this.currentFilter = this.currentFilter.next();
                    b.setMessage(new TranslatableComponent(this.currentFilter.translationKey()));
                    this.applyFilter();
                }
        );
        this.addRenderableWidget(this.filterButton);

        this.namespaceButton = new Button(
                LIST_LEFT + halfWidth + 2, filterRowY, LIST_WIDTH - halfWidth - 2, 20,
                buildNamespaceLabel(),
                b -> {
                    this.currentNamespaceIndex = (this.currentNamespaceIndex + 1) % (this.availableNamespaces.size() + 1);
                    b.setMessage(buildNamespaceLabel());
                    this.applyFilter();
                }
        );
        this.addRenderableWidget(this.namespaceButton);

        int listTop = 68;
        int listBottom = this.height - 40;

        this.list = new EntityTypeSelectionList(this, mc, LIST_WIDTH, this.height, listTop, listBottom, 24);
        this.list.setLeftPos(LIST_LEFT);
        this.addWidget(this.list);

        this.addRenderableWidget(new Button(
                LIST_LEFT, this.height - 28, LIST_WIDTH, 20,
                new TranslatableComponent("gui.done"),
                b -> this.onClose()
        ));

        int panelContentRight = rightPanelRight - 8;

        this.copyButton = new Button(
                panelContentRight - 40, 0, 40, 16,
                new TranslatableComponent(TRANSLATION_PREFIX + "copy"),
                b -> {
                    if (!cachedSummonCommand.isEmpty()) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(cachedSummonCommand);
                    }
                }
        );
        this.copyButton.visible = false;
        this.addRenderableWidget(this.copyButton);

        this.resetScaleButton = new Button(
                rightPanelRight - 56, rightPanelBottom - 24, 48, 16,
                new TranslatableComponent(TRANSLATION_PREFIX + "reset"),
                b -> {
                    this.userScaled = false;
                    this.previewYaw = 0.0F;
                    this.previewPitch = 0.0F;
                    autoCalculateScale();
                }
        );
        this.resetScaleButton.visible = false;
        this.addRenderableWidget(this.resetScaleButton);

        this.nbtButton = new Button(
                panelContentRight - 86, 0, 40, 16,
                new TranslatableComponent(TRANSLATION_PREFIX + "edit_nbt"),
                b -> {
                    if (this.selectedId != null) {
                        Minecraft.getInstance().setScreen(new NbtEditorScreen(this, this.selectedId, cachedSummonCommand));
                    }
                }
        );
        this.nbtButton.visible = false;
        this.addRenderableWidget(this.nbtButton);

        this.favoriteButton = new Button(
                0, 0, 16, 16,
                new TextComponent("\u2606"),
                b -> {
                    if (this.selectedId != null) {
                        FavoritesManager.get().toggle(this.selectedId);
                        updateFavoriteButtonLabel();
                        if (currentFilter == FilterCategory.FAVORITES) {
                            applyFilter();
                        }
                    }
                }
        );
        this.favoriteButton.visible = false;
        this.addRenderableWidget(this.favoriteButton);

        this.animateButton = new Button(
                rightPanelRight - 112, rightPanelBottom - 24, 52, 16,
                buildAnimateLabel(),
                b -> {
                    this.animatePreview = !this.animatePreview;
                    b.setMessage(buildAnimateLabel());
                }
        );
        this.animateButton.visible = false;
        this.addRenderableWidget(this.animateButton);

        int themeButtonWidth = 50;
        this.themeButton = new Button(
                LIST_LEFT + LIST_WIDTH - themeButtonWidth, 0, themeButtonWidth, 16,
                buildThemeLabel(),
                b -> {
                    ThemeManager.get().toggle();
                    b.setMessage(buildThemeLabel());
                }
        );
        this.addRenderableWidget(this.themeButton);

        applyFilter();

        if (this.selectedId != null) {
            cacheEntityMetadata();
            this.copyButton.visible = true;
            this.resetScaleButton.visible = true;
            this.nbtButton.visible = true;
            this.favoriteButton.visible = true;
            this.animateButton.visible = true;
            updateFavoriteButtonLabel();
        } else if (!this.filteredEntityIds.isEmpty()) {
            selectEntityId(this.filteredEntityIds.get(0));
        }
    }

    private Component buildNamespaceLabel() {
        if (currentNamespaceIndex == 0) {
            return new TranslatableComponent(TRANSLATION_PREFIX + "mod.all");
        }
        String namespace = availableNamespaces.get(currentNamespaceIndex - 1);
        return new TranslatableComponent(TRANSLATION_PREFIX + "mod.prefix", namespace);
    }

    private boolean passesFilter(ResourceLocation id) {
        if (currentNamespaceIndex > 0) {
            String selectedNamespace = availableNamespaces.get(currentNamespaceIndex - 1);
            if (!id.getNamespace().equals(selectedNamespace)) {
                return false;
            }
        }

        return switch (currentFilter) {
            case ALL -> true;
            case LIVING -> isLivingEntityType(id);
            case HOSTILE -> getCategory(id) == MobCategory.MONSTER;
            case PASSIVE -> {
                MobCategory category = getCategory(id);
                yield category == MobCategory.CREATURE
                        || category == MobCategory.AMBIENT
                        || category == MobCategory.WATER_CREATURE
                        || category == MobCategory.WATER_AMBIENT
                        || category == MobCategory.UNDERGROUND_WATER_CREATURE
                        || category == MobCategory.AXOLOTLS;
            }
            case FAVORITES -> FavoritesManager.get().isFavorite(id);
        };
    }

    private MobCategory getCategory(ResourceLocation id) {
        MobCategory cached = categoryCache.get(id);
        if (cached != null) {
            return cached;
        }

        EntityType<?> type = ForgeRegistries.ENTITIES.getValue(id);
        if (type == null) {
            categoryCache.put(id, MobCategory.MISC);
            return MobCategory.MISC;
        }

        MobCategory category = type.getCategory();
        categoryCache.put(id, category);
        return category;
    }

    private String getDisplayNameString(ResourceLocation id) {
        String cached = displayNameCache.get(id);
        if (cached != null) {
            return cached;
        }

        EntityType<?> type = ForgeRegistries.ENTITIES.getValue(id);
        if (type == null) {
            displayNameCache.put(id, id.getPath());
            return id.getPath();
        }

        String name = type.getDescription().getString();
        displayNameCache.put(id, name);
        return name;
    }

    private void applyFilter() {
        String needle = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);

        this.filteredEntityIds.clear();

        for (ResourceLocation id : this.allEntityIds) {
            if (!passesFilter(id)) {
                continue;
            }

            if (!needle.isEmpty()) {
                String idString = id.toString().toLowerCase(Locale.ROOT);
                String displayName = getDisplayNameString(id).toLowerCase(Locale.ROOT);
                if (!idString.contains(needle) && !displayName.contains(needle)) {
                    continue;
                }
            }

            this.filteredEntityIds.add(id);
        }

        this.list.setEntries(this.filteredEntityIds, this.selectedId);

        if (this.selectedId != null && !this.filteredEntityIds.contains(this.selectedId)) {
            this.selectedId = null;
            discardPreviewEntity();
            this.previewError = null;
            this.copyButton.visible = false;
            this.resetScaleButton.visible = false;
            this.nbtButton.visible = false;
            this.favoriteButton.visible = false;
            this.animateButton.visible = false;
        }
    }

    private boolean isLivingEntityType(ResourceLocation id) {
        Boolean cached = livingTypeCache.get(id);
        if (cached != null) {
            return cached;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }

        var type = ForgeRegistries.ENTITIES.getValue(id);
        if (type == null) {
            livingTypeCache.put(id, false);
            return false;
        }

        try {
            Entity entity = type.create(mc.level);
            if (entity == null) {
                livingTypeCache.put(id, false);
                return false;
            }
            boolean isLiving = entity instanceof LivingEntity;
            entity.discard();
            livingTypeCache.put(id, isLiving);
            return isLiving;
        } catch (Throwable t) {
            livingTypeCache.put(id, false);
            return false;
        }
    }

    public void selectEntityId(ResourceLocation id) {
        if (id.equals(this.selectedId)) {
            return;
        }

        this.selectedId = id;
        this.previewError = null;
        this.previewYaw = 0.0F;
        this.previewPitch = 0.0F;
        discardPreviewEntity();
        rebuildPreviewEntity();
        cacheEntityMetadata();

        if (this.copyButton != null) {
            this.copyButton.visible = true;
        }
        if (this.resetScaleButton != null) {
            this.resetScaleButton.visible = true;
        }
        if (this.nbtButton != null) {
            this.nbtButton.visible = true;
        }
        if (this.favoriteButton != null) {
            this.favoriteButton.visible = true;
            updateFavoriteButtonLabel();
        }
        if (this.animateButton != null) {
            this.animateButton.visible = true;
        }
    }

    private void cacheEntityMetadata() {
        if (this.selectedId == null) {
            cachedDisplayName = "";
            cachedRegistryId = "";
            cachedSummonCommand = "";
            cachedMaxHealth = "";
            cachedDimensions = "";
            cachedCategory = "";
            cachedDrops = "";
            return;
        }

        cachedDisplayName = getDisplayNameString(this.selectedId);
        cachedRegistryId = this.selectedId.toString();
        cachedSummonCommand = "/summon " + cachedRegistryId + " ~ ~ ~";

        EntityType<?> type = ForgeRegistries.ENTITIES.getValue(this.selectedId);
        if (type != null) {
            EntityDimensions dimensions = type.getDimensions();
            cachedDimensions = String.format("Size: %.1f x %.1f", dimensions.width, dimensions.height);

            MobCategory category = type.getCategory();
            cachedCategory = "Category: " + formatCategory(category);
        } else {
            cachedDimensions = "";
            cachedCategory = "";
        }

        if (this.previewEntity instanceof LivingEntity living) {
            cachedMaxHealth = "Health: " + (int) living.getMaxHealth();
        } else {
            cachedMaxHealth = "";
        }

        List<String> drops = LootTableParser.getDrops(this.selectedId);
        if (drops == null) {
            cachedDrops = new TranslatableComponent(TRANSLATION_PREFIX + "drops.unavailable").getString();
        } else if (drops.isEmpty()) {
            cachedDrops = new TranslatableComponent(TRANSLATION_PREFIX + "drops.none").getString();
        } else {
            cachedDrops = new TranslatableComponent(TRANSLATION_PREFIX + "drops.prefix").getString()
                    + " " + String.join(", ", drops);
        }
    }

    private String formatCategory(MobCategory category) {
        String name = category.name().toLowerCase(Locale.ROOT);
        return name.substring(0, 1).toUpperCase(Locale.ROOT)
                + name.substring(1).replace('_', ' ');
    }

    private Component buildAnimateLabel() {
        String key = animatePreview
                ? TRANSLATION_PREFIX + "animate.on"
                : TRANSLATION_PREFIX + "animate.off";
        return new TranslatableComponent(key);
    }

    private Component buildThemeLabel() {
        return new TextComponent(ThemeManager.get().isDark() ? "Dark" : "Light");
    }

    private void updateFavoriteButtonLabel() {
        if (this.favoriteButton == null || this.selectedId == null) {
            return;
        }
        boolean isFav = FavoritesManager.get().isFavorite(this.selectedId);
        this.favoriteButton.setMessage(new TextComponent(isFav ? "\u2605" : "\u2606"));
    }

    private void autoCalculateScale() {
        if (this.previewEntity instanceof LivingEntity living) {
            float entityHeight = Math.max(0.5F, living.getBbHeight());
            this.previewScale = (int) Math.max(20, Math.min(90, 55.0F / entityHeight));
        }
    }

    private void discardPreviewEntity() {
        if (this.previewEntity != null) {
            this.previewEntity.discard();
            this.previewEntity = null;
        }
    }

    private void rebuildPreviewEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            this.previewError = new TranslatableComponent(TRANSLATION_PREFIX + "error.no_world");
            return;
        }
        if (this.selectedId == null) {
            return;
        }

        var type = ForgeRegistries.ENTITIES.getValue(this.selectedId);
        if (type == null) {
            this.previewError = new TranslatableComponent(TRANSLATION_PREFIX + "error.unknown_entity", this.selectedId.toString());
            return;
        }

        try {
            Entity entity = type.create(mc.level);
            if (entity == null) {
                this.previewError = new TranslatableComponent(TRANSLATION_PREFIX + "error.cannot_instantiate");
                return;
            }

            entity.setPos(0.0D, 0.0D, 0.0D);
            entity.setSilent(true);
            entity.setInvulnerable(true);
            if (entity instanceof Mob mob) {
                mob.setNoAi(true);
            }

            this.previewEntity = entity;

            if (!this.userScaled && entity instanceof LivingEntity living) {
                float entityHeight = Math.max(0.5F, living.getBbHeight());
                this.previewScale = (int) Math.max(20, Math.min(90, 55.0F / entityHeight));
            }

            if (entity instanceof LivingEntity living) {
                cachedMaxHealth = "Health: " + (int) living.getMaxHealth();
            }
        } catch (Throwable t) {
            this.previewError = new TranslatableComponent(TRANSLATION_PREFIX + "error.preview_failed", t.getClass().getSimpleName());
            discardPreviewEntity();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.searchBox != null) {
            this.searchBox.tick();
        }

        if (this.selectedId != null && this.previewEntity == null && this.previewError == null) {
            rebuildPreviewEntity();
        }

        if (this.animatePreview && this.previewEntity instanceof LivingEntity living) {
            living.tickCount++;
            living.animationSpeed = 0.6F;
            living.animationPosition += 0.6F;
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        Theme theme = ThemeManager.get().currentTheme();

        int listTop = 68;
        int listBottom = this.height - 40;

        TextureRenderer.drawTilingBackground(poseStack, 0, 0, this.width, this.height);
        TextureRenderer.drawPanel(poseStack, LIST_LEFT - 4, listTop - 4, LIST_WIDTH + 8, listBottom - listTop + 8);
        TextureRenderer.drawPanel(poseStack, rightPanelLeft - 8, rightPanelTop - 5, rightPanelRight - rightPanelLeft + 16, rightPanelBottom - rightPanelTop + 10);

        this.font.draw(poseStack, this.title, LIST_LEFT, 6, theme.titleColor());

        super.render(poseStack, mouseX, mouseY, partialTicks);

        Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int scissorX = (int) (LIST_LEFT * scale);
        int scissorY = (int) ((this.height - listBottom) * scale);
        int scissorW = (int) (LIST_WIDTH * scale);
        int scissorH = (int) ((listBottom - listTop) * scale);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorW, scissorH);
        this.list.render(poseStack, mouseX, mouseY, partialTicks);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        String countText = this.filteredEntityIds.size() + " / " + this.allEntityIds.size();
        int countWidth = this.font.width(countText);
        this.font.draw(poseStack, countText, (float) (LIST_LEFT + LIST_WIDTH - countWidth), 60.0F, theme.countText());

        int panelContentLeft = rightPanelLeft + 4;
        int panelContentRight = rightPanelRight - 4;
        int panelMaxTextWidth = panelContentRight - panelContentLeft;

        if (this.selectedId == null) {
            String hintText = new TranslatableComponent(TRANSLATION_PREFIX + "no_selection").getString();
            int hintWidth = this.font.width(hintText);
            int hintX = rightPanelLeft + (rightPanelRight - rightPanelLeft - hintWidth) / 2;
            int hintY = rightPanelTop + (rightPanelBottom - rightPanelTop) / 2 - 4;
            this.font.draw(poseStack, hintText, hintX, hintY, theme.noSelectionText());
            return;
        }

        int panelY = rightPanelTop + 6;

        this.favoriteButton.x = panelContentRight - 16;
        this.favoriteButton.y = panelY - 4;

        String truncatedDisplayName = this.font.plainSubstrByWidth(cachedDisplayName, panelMaxTextWidth - 22);
        this.font.drawShadow(poseStack, truncatedDisplayName, panelContentLeft, panelY, theme.primaryText());
        panelY += 12;

        String truncatedRegistryId = this.font.plainSubstrByWidth(cachedRegistryId, panelMaxTextWidth);
        this.font.draw(poseStack, truncatedRegistryId, panelContentLeft, panelY, theme.secondaryText());
        panelY += 14;

        TextureRenderer.drawSeparator(poseStack, panelContentLeft, panelY, panelContentRight - panelContentLeft);
        panelY += 6;

        if (!cachedMaxHealth.isEmpty()) {
            String healthAndSize = cachedMaxHealth;
            if (!cachedDimensions.isEmpty()) {
                healthAndSize += "    " + cachedDimensions;
            }
            String truncatedStats = this.font.plainSubstrByWidth(healthAndSize, panelMaxTextWidth);
            this.font.draw(poseStack, truncatedStats, panelContentLeft, panelY, theme.statsText());
            panelY += 12;
        } else if (!cachedDimensions.isEmpty()) {
            String truncatedDimensions = this.font.plainSubstrByWidth(cachedDimensions, panelMaxTextWidth);
            this.font.draw(poseStack, truncatedDimensions, panelContentLeft, panelY, theme.statsText());
            panelY += 12;
        }

        if (!cachedCategory.isEmpty()) {
            String truncatedCategory = this.font.plainSubstrByWidth(cachedCategory, panelMaxTextWidth);
            this.font.draw(poseStack, truncatedCategory, panelContentLeft, panelY, theme.statsText());
            panelY += 12;
        }

        if (!cachedDrops.isEmpty()) {
            String truncatedDrops = this.font.plainSubstrByWidth(cachedDrops, panelMaxTextWidth);
            this.font.draw(poseStack, truncatedDrops, panelContentLeft, panelY, theme.statsText());
            panelY += 12;
        }

        panelY += 2;

        String truncatedSummon = this.font.plainSubstrByWidth(cachedSummonCommand, panelMaxTextWidth - 90);
        this.font.draw(poseStack, truncatedSummon, panelContentLeft, panelY, theme.commandText());

        this.copyButton.x = panelContentRight - 40;
        this.copyButton.y = panelY - 2;

        this.nbtButton.x = panelContentRight - 86;
        this.nbtButton.y = panelY - 2;

        panelY += 18;

        int previewAreaTop = panelY;

        if (this.previewError != null) {
            String truncatedError = this.font.plainSubstrByWidth(this.previewError.getString(), panelMaxTextWidth);
            this.font.draw(poseStack, truncatedError, panelContentLeft, panelY, theme.errorText());
        } else if (this.previewEntity instanceof LivingEntity living) {
            int previewCenterX = (rightPanelLeft + rightPanelRight) / 2;
            int previewAreaBottom = rightPanelBottom - 28;
            int previewBaseY = previewAreaTop + (previewAreaBottom - previewAreaTop) * 3 / 4;

            double guiScale = window.getGuiScale();
            int panelScissorX = (int) ((rightPanelLeft - 4) * guiScale);
            int panelScissorY = (int) ((this.height - previewAreaBottom) * guiScale);
            int panelScissorW = (int) ((rightPanelRight - rightPanelLeft + 8) * guiScale);
            int panelScissorH = (int) ((previewAreaBottom - previewAreaTop) * guiScale);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(panelScissorX, panelScissorY, panelScissorW, panelScissorH);
            EntityPreviewRenderer.render(previewCenterX, previewBaseY, this.previewScale, this.previewYaw, this.previewPitch, living);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            String livingOnlyError = new TranslatableComponent(TRANSLATION_PREFIX + "error.living_only").getString();
            String truncatedLivingError = this.font.plainSubstrByWidth(livingOnlyError, panelMaxTextWidth);
            this.font.draw(poseStack, truncatedLivingError, panelContentLeft, panelY, theme.errorText());
        }

        int bottomButtonsWidth = (this.resetScaleButton.visible ? 52 : 0) + (this.animateButton.visible ? 56 : 0);
        int hintMaxWidth = panelMaxTextWidth - bottomButtonsWidth;
        int hintY = rightPanelBottom - 24;
        String dragHint = this.font.plainSubstrByWidth(new TranslatableComponent(TRANSLATION_PREFIX + "hint.drag_rotate").getString(), hintMaxWidth);
        String scrollHint = this.font.plainSubstrByWidth(new TranslatableComponent(TRANSLATION_PREFIX + "hint.scroll_scale").getString(), hintMaxWidth);
        this.font.draw(poseStack, dragHint, panelContentLeft, hintY, theme.hintText());
        this.font.draw(poseStack, scrollHint, panelContentLeft, hintY + 10, theme.hintText());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && mouseX >= rightPanelLeft && mouseX <= rightPanelRight
                && mouseY >= rightPanelTop + 80 && mouseY <= rightPanelBottom - 28
                && this.previewEntity instanceof LivingEntity) {
            this.isDraggingPreview = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.isDraggingPreview && button == 0) {
            this.previewYaw -= (float) deltaX * 0.04F;
            this.previewPitch = Mth.clamp(this.previewPitch - (float) deltaY * 0.04F, -1.2F, 1.2F);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.isDraggingPreview && button == 0) {
            this.isDraggingPreview = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= rightPanelLeft && mouseX <= rightPanelRight
                && mouseY >= rightPanelTop && mouseY <= rightPanelBottom
                && this.previewEntity instanceof LivingEntity) {
            this.userScaled = true;
            int step = delta > 0 ? 2 : -2;
            this.previewScale = Mth.clamp(this.previewScale + step, 10, 200);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        discardPreviewEntity();
        Minecraft.getInstance().setScreen(this.previous);
    }
}
