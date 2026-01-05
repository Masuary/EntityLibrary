package com.masuary.entitylibrary.client.screen;

import com.masuary.entitylibrary.EntityLibraryMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Simple in-game "library" screen that lists all registered entity types and renders a preview
 * without spawning/adding it to the world.
 */
public class EntityLibraryScreen extends Screen {
    private static final int LIST_LEFT = 10;
    private static final int LIST_WIDTH = 230;

    private final Screen previous;

    private EditBox searchBox;
    private EntityTypeSelectionList list;

    private final List<ResourceLocation> allEntityIds = new ArrayList<>();
    private final List<ResourceLocation> filteredEntityIds = new ArrayList<>();

    /**
     * When enabled, the left-hand list hides all non-living entity types.
     * This is useful for a "monster/mob library" where only LivingEntity previews make sense.
     */
    private final boolean hideNonLivingEntityTypes = true;

    /**
     * Cache for whether a given entity id produces a LivingEntity when instantiated on the client.
     * We use instantiation as a pragmatic proxy because EntityType does not expose its base class directly.
     */
    private final Map<ResourceLocation, Boolean> livingTypeCache = new HashMap<>();

    @Nullable
    private ResourceLocation selectedId;

    @Nullable
    private Entity previewEntity;

    @Nullable
    private String previewError;

    private int previewScale = 40;
    private boolean userScaled = false;

    public EntityLibraryScreen(@Nullable Screen previous) {
        super(new TranslatableComponent("screen." + EntityLibraryMod.MODID + ".title"));
        this.previous = previous;
    }

    public EntityLibraryScreen() {
        this(null);
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();

        // Conservative: ensure per-open determinism (some modded entities can behave oddly if created
        // without a fully-initialized client level). We'll recalc as needed.
        this.livingTypeCache.clear();

        // Build a stable, sorted list of all entity ids present in the registry.
        allEntityIds.clear();
        allEntityIds.addAll(ForgeRegistries.ENTITIES.getKeys());
        allEntityIds.sort(Comparator
                .comparing(ResourceLocation::getNamespace)
                .thenComparing(ResourceLocation::getPath));

        int listTop = 44;
        int listBottom = this.height - 40;

        this.list = new EntityTypeSelectionList(this, mc, LIST_WIDTH, this.height, listTop, listBottom, 18);
        this.list.setLeftPos(LIST_LEFT);
        this.addWidget(this.list);

        this.searchBox = new EditBox(this.font, LIST_LEFT, 18, LIST_WIDTH, 18,
                new TranslatableComponent("screen." + EntityLibraryMod.MODID + ".search"));
        this.searchBox.setMaxLength(128);
        this.searchBox.setResponder(s -> this.applyFilter());
        this.addRenderableWidget(this.searchBox);

        // Close button
        this.addRenderableWidget(new Button(
                LIST_LEFT,
                this.height - 28,
                LIST_WIDTH,
                20,
                new TranslatableComponent("gui.done"),
                b -> this.onClose()
        ));

        applyFilter();

        // Select first entry by default.
        if (this.selectedId == null && !this.filteredEntityIds.isEmpty()) {
            selectEntityId(this.filteredEntityIds.get(0));
        }
    }

    private void applyFilter() {
        String needle = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);

        this.filteredEntityIds.clear();

        for (ResourceLocation id : this.allEntityIds) {
            // Search filter first (cheap), then optional LivingEntity-only filter (potentially expensive).
            if (!needle.isEmpty()) {
                String s = id.toString().toLowerCase(Locale.ROOT);
                if (!s.contains(needle)) {
                    continue;
                }
            }

            if (this.hideNonLivingEntityTypes && !isLivingEntityType(id)) {
                continue;
            }

            this.filteredEntityIds.add(id);
        }

        this.list.setEntries(this.filteredEntityIds, this.selectedId);

        // If the current selection is no longer visible, clear it so the user doesn't see stale data.
        if (this.selectedId != null && !this.filteredEntityIds.contains(this.selectedId)) {
            this.selectedId = null;
            this.previewEntity = null;
            this.previewError = null;
        }
    }

    private boolean isLivingEntityType(ResourceLocation id) {
        return this.livingTypeCache.computeIfAbsent(id, key -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return false;
            }

            var type = ForgeRegistries.ENTITIES.getValue(key);
            if (type == null) {
                return false;
            }

            try {
                Entity e = type.create(mc.level);
                return e instanceof LivingEntity;
            } catch (Throwable t) {
                // If an entity can't be created client-side, treat it as non-previewable and hide it.
                return false;
            }
        });
    }

    public void selectEntityId(ResourceLocation id) {
        if (id.equals(this.selectedId)) {
            return;
        }

        this.selectedId = id;
        this.previewError = null;
        this.previewEntity = null;

        rebuildPreviewEntity();
    }

    private void rebuildPreviewEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            this.previewError = "Join a world to preview entities.";
            return;
        }
        if (this.selectedId == null) {
            return;
        }

        var type = ForgeRegistries.ENTITIES.getValue(this.selectedId);
        if (type == null) {
            this.previewError = "Unknown entity type: " + this.selectedId;
            return;
        }

        try {
            Entity e = type.create(mc.level);
            if (e == null) {
                this.previewError = "This entity type cannot be instantiated on the client.";
                return;
            }

            // Ensure it stays a pure preview.
            e.setPos(0.0D, 0.0D, 0.0D);
            e.setSilent(true);
            e.setInvulnerable(true);
            if (e instanceof Mob mob) {
                mob.setNoAi(true);
            }

            this.previewEntity = e;

            if (!this.userScaled && e instanceof LivingEntity living) {
                float h = Math.max(0.5F, living.getBbHeight());
                int auto = (int) Math.max(20, Math.min(90, 55.0F / h));
                this.previewScale = auto;
            }
        } catch (Throwable t) {
            this.previewError = "Preview failed: " + t.getClass().getSimpleName();
            this.previewEntity = null;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.searchBox != null) {
            this.searchBox.tick();
        }

        // If the level changed (e.g. dimension switch), refresh the preview entity.
        // (We keep it simple and recreate whenever we have a selection but no preview.)
        if (this.selectedId != null && this.previewEntity == null && this.previewError == null) {
            rebuildPreviewEntity();
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);

        // Title
        this.font.draw(poseStack, this.title, LIST_LEFT, 6, 0xFFFFFF);

        // Widgets
        super.render(poseStack, mouseX, mouseY, partialTicks);
        this.list.render(poseStack, mouseX, mouseY, partialTicks);

        // Right-side preview panel
        int panelLeft = LIST_LEFT + LIST_WIDTH + 16;
        int panelRight = this.width - 12;
        int panelTop = 18;

        // Basic instructions
        int y = panelTop;
        this.font.draw(poseStack, new TextComponent("Search / click an entity to preview"), panelLeft, y, 0xC0C0C0);
        y += 12;
        this.font.draw(poseStack, new TextComponent("Mouse wheel over preview: scale"), panelLeft, y, 0xC0C0C0);
        y += 18;

        if (this.selectedId == null) {
            this.font.draw(poseStack, new TextComponent("No selection"), panelLeft, y, 0xFFFFFF);
            return;
        }

        this.font.draw(poseStack, new TextComponent("Selected: " + this.selectedId), panelLeft, y, 0xFFFFFF);
        y += 12;
        this.font.draw(poseStack, new TextComponent("Summon: /summon " + this.selectedId + " ~ ~ ~"), panelLeft, y, 0xC0C0C0);
        y += 18;

        if (this.previewError != null) {
            this.font.draw(poseStack, new TextComponent(this.previewError), panelLeft, y, 0xFF8080);
            return;
        }

        if (!(this.previewEntity instanceof LivingEntity living)) {
            this.font.draw(poseStack, new TextComponent("Preview available for LivingEntity types only."), panelLeft, y, 0xFF8080);
            return;
        }

        // Render the preview roughly centered in the right panel.
        int previewCenterX = (panelLeft + panelRight) / 2;
        int previewBaseY = this.height - 70;

        float dx = (float) (previewCenterX - mouseX);
        float dy = (float) (previewBaseY - mouseY);

        EntityPreviewRenderer.render(previewCenterX, previewBaseY, this.previewScale, dx, dy, living);

        // Small footer info
        this.font.draw(poseStack, new TextComponent("Scale: " + this.previewScale), panelLeft, this.height - 52, 0xC0C0C0);
        this.font.draw(poseStack, new TextComponent("This is a visual preview only (entity is never added to the world)."), panelLeft, this.height - 40, 0xC0C0C0);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int panelLeft = LIST_LEFT + LIST_WIDTH + 16;
        if (mouseX >= panelLeft && this.previewEntity instanceof LivingEntity) {
            this.userScaled = true;
            int step = delta > 0 ? 2 : -2;
            this.previewScale = clamp(this.previewScale + step, 10, 200);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.previous);
    }
}
