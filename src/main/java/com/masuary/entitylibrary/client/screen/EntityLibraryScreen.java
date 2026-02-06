package com.masuary.entitylibrary.client.screen;

import com.masuary.entitylibrary.EntityLibraryMod;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.gui.GuiComponent;
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
        ALL, LIVING, HOSTILE, PASSIVE;

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

    private Button filterButton;
    private Button namespaceButton;
    private Button copyButton;
    private Button resetScaleButton;

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

        applyFilter();

        if (this.selectedId != null) {
            cacheEntityMetadata();
            this.copyButton.visible = true;
            this.resetScaleButton.visible = true;
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
    }

    private void cacheEntityMetadata() {
        if (this.selectedId == null) {
            cachedDisplayName = "";
            cachedRegistryId = "";
            cachedSummonCommand = "";
            cachedMaxHealth = "";
            cachedDimensions = "";
            cachedCategory = "";
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
    }

    private String formatCategory(MobCategory category) {
        String name = category.name().toLowerCase(Locale.ROOT);
        return name.substring(0, 1).toUpperCase(Locale.ROOT)
                + name.substring(1).replace('_', ' ');
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
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);

        GuiComponent.fill(poseStack, rightPanelLeft - 4, rightPanelTop, rightPanelRight + 4, rightPanelBottom, 0xCC0A0A0A);

        int borderColor = 0xFF333333;
        GuiComponent.fill(poseStack, rightPanelLeft - 5, rightPanelTop, rightPanelLeft - 4, rightPanelBottom, borderColor);
        GuiComponent.fill(poseStack, rightPanelRight + 4, rightPanelTop, rightPanelRight + 5, rightPanelBottom, borderColor);
        GuiComponent.fill(poseStack, rightPanelLeft - 5, rightPanelTop - 1, rightPanelRight + 5, rightPanelTop, borderColor);
        GuiComponent.fill(poseStack, rightPanelLeft - 5, rightPanelBottom, rightPanelRight + 5, rightPanelBottom + 1, borderColor);

        this.font.draw(poseStack, this.title, LIST_LEFT, 6, 0xFFFFFF);

        super.render(poseStack, mouseX, mouseY, partialTicks);

        int listRight = LIST_LEFT + LIST_WIDTH;
        int listTop = 68;
        int listBottom = this.height - 40;

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

        GuiComponent.fill(poseStack, LIST_LEFT, listTop - 1, listRight, listTop, 0xFF333333);
        GuiComponent.fill(poseStack, LIST_LEFT, listBottom, listRight, listBottom + 1, 0xFF333333);

        String countText = this.filteredEntityIds.size() + " / " + this.allEntityIds.size();
        int countWidth = this.font.width(countText);
        this.font.draw(poseStack, countText, (float) (LIST_LEFT + LIST_WIDTH - countWidth), 60.0F, 0x808080);

        int panelContentLeft = rightPanelLeft + 4;
        int panelContentRight = rightPanelRight - 4;
        int panelMaxTextWidth = panelContentRight - panelContentLeft;

        if (this.selectedId == null) {
            String hintText = new TranslatableComponent(TRANSLATION_PREFIX + "no_selection").getString();
            int hintWidth = this.font.width(hintText);
            int hintX = rightPanelLeft + (rightPanelRight - rightPanelLeft - hintWidth) / 2;
            int hintY = rightPanelTop + (rightPanelBottom - rightPanelTop) / 2 - 4;
            this.font.draw(poseStack, hintText, hintX, hintY, 0x808080);
            return;
        }

        int panelY = rightPanelTop + 6;

        String truncatedDisplayName = this.font.plainSubstrByWidth(cachedDisplayName, panelMaxTextWidth);
        this.font.drawShadow(poseStack, truncatedDisplayName, panelContentLeft, panelY, 0xFFFFFF);
        panelY += 12;

        String truncatedRegistryId = this.font.plainSubstrByWidth(cachedRegistryId, panelMaxTextWidth);
        this.font.draw(poseStack, truncatedRegistryId, panelContentLeft, panelY, 0x808080);
        panelY += 14;

        GuiComponent.fill(poseStack, panelContentLeft, panelY, panelContentRight, panelY + 1, 0xFF333333);
        panelY += 6;

        if (!cachedMaxHealth.isEmpty()) {
            String healthAndSize = cachedMaxHealth;
            if (!cachedDimensions.isEmpty()) {
                healthAndSize += "    " + cachedDimensions;
            }
            String truncatedStats = this.font.plainSubstrByWidth(healthAndSize, panelMaxTextWidth);
            this.font.draw(poseStack, truncatedStats, panelContentLeft, panelY, 0xC0C0C0);
            panelY += 12;
        } else if (!cachedDimensions.isEmpty()) {
            String truncatedDimensions = this.font.plainSubstrByWidth(cachedDimensions, panelMaxTextWidth);
            this.font.draw(poseStack, truncatedDimensions, panelContentLeft, panelY, 0xC0C0C0);
            panelY += 12;
        }

        if (!cachedCategory.isEmpty()) {
            String truncatedCategory = this.font.plainSubstrByWidth(cachedCategory, panelMaxTextWidth);
            this.font.draw(poseStack, truncatedCategory, panelContentLeft, panelY, 0xC0C0C0);
            panelY += 12;
        }

        panelY += 2;

        String truncatedSummon = this.font.plainSubstrByWidth(cachedSummonCommand, panelMaxTextWidth - 44);
        this.font.draw(poseStack, truncatedSummon, panelContentLeft, panelY, 0x55FF55);

        this.copyButton.x = panelContentRight - 40;
        this.copyButton.y = panelY - 2;

        panelY += 18;

        int previewAreaTop = panelY;

        if (this.previewError != null) {
            String truncatedError = this.font.plainSubstrByWidth(this.previewError.getString(), panelMaxTextWidth);
            this.font.draw(poseStack, truncatedError, panelContentLeft, panelY, 0xFF8080);
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
            this.font.draw(poseStack, truncatedLivingError, panelContentLeft, panelY, 0xFF8080);
        }

        int resetButtonWidth = this.resetScaleButton.visible ? 52 : 0;
        int hintMaxWidth = panelMaxTextWidth - resetButtonWidth;
        int hintY = rightPanelBottom - 24;
        String dragHint = this.font.plainSubstrByWidth(new TranslatableComponent(TRANSLATION_PREFIX + "hint.drag_rotate").getString(), hintMaxWidth);
        String scrollHint = this.font.plainSubstrByWidth(new TranslatableComponent(TRANSLATION_PREFIX + "hint.scroll_scale").getString(), hintMaxWidth);
        this.font.draw(poseStack, dragHint, panelContentLeft, hintY, 0x606060);
        this.font.draw(poseStack, scrollHint, panelContentLeft, hintY + 10, 0x606060);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= rightPanelLeft && mouseX <= rightPanelRight
                && mouseY >= rightPanelTop + 80 && mouseY <= rightPanelBottom - 28
                && this.previewEntity instanceof LivingEntity) {
            this.isDraggingPreview = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
