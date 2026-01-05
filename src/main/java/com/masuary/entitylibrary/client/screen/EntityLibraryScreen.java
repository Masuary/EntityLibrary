package com.masuary.entitylibrary.client.screen;

import com.masuary.entitylibrary.EntityLibraryMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Standalone client-only screen that lists registered LivingEntity types and renders a preview instance.
 *
 * The preview entity is instantiated client-side but is NOT added to the world.
 */
public final class EntityLibraryScreen extends Screen {
    private static final int LIST_LEFT = 12;
    private static final int LIST_WIDTH = 220;
    private static final int ENTRY_HEIGHT = 18;

    private static final int TITLE_Y = 8;
    private static final int SEARCH_H = 18;
    // Extra padding keeps the first/last visible rows from being partially covered by
    // AbstractSelectionList's top/bottom fade.
    private static final int LIST_GAP = 8;

    private static final int PADDING = 12;

    private final Screen parent;

    private EditBox searchBox;
    private EntityTypeSelectionList list;

    private int listTop;
    private int listBottom;

    private int previewScale = 60;

    private LivingEntity previewEntity;
    private ResourceLocation selectedId;

    // Computed each frame
    private int previewX1, previewY1, previewX2, previewY2;

    public EntityLibraryScreen(final Screen parent) {
        super(Component.translatable("screen.entitylibrary.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final Minecraft mc = Minecraft.getInstance();

        final int searchY = TITLE_Y + this.font.lineHeight + 6;
        final int doneY = this.height - 24;

        // We add a small gap above the first row (below the search box) and also ensure the
        // bottom edge lands exactly on a row boundary. Otherwise, the last visible row can be
        // partially clipped when the available height is not a multiple of ENTRY_HEIGHT.
        this.listTop = searchY + SEARCH_H + LIST_GAP;

        final int bottomCandidate = doneY - 8;
        final int usableHeight = Math.max(0, bottomCandidate - this.listTop);
        final int alignedHeight = (usableHeight / ENTRY_HEIGHT) * ENTRY_HEIGHT;
        // Keep at least a couple of rows visible even on very small GUI scales.
        final int minHeight = ENTRY_HEIGHT * 4;
        final int targetBottom = this.listTop + Math.max(minHeight, alignedHeight);
        this.listBottom = Math.min(bottomCandidate, targetBottom);

        this.searchBox = new EditBox(
                this.font,
                LIST_LEFT,
                searchY,
                LIST_WIDTH,
                SEARCH_H,
                Component.translatable("screen.entitylibrary.search")
        );
        this.searchBox.setMaxLength(128);
        this.searchBox.setBordered(true);
        this.searchBox.setSuggestion(Component.translatable("screen.entitylibrary.search").getString());
        this.searchBox.setResponder(this::applyFilter);
        this.addRenderableWidget(this.searchBox);

        this.list = new EntityTypeSelectionList(
                this,
                mc,
                LIST_WIDTH,
                // NeoForge 1.21.1's ObjectSelectionList constructor takes a "height" parameter
                // that is used for internal clipping. Passing the full screen height can cause
                // the list to clip rows at the bottom, even when we compute a proper bottom.
                //
                // Use the list's actual viewport height instead.
                (this.listBottom - this.listTop),
                this.listTop,
                this.listBottom,
                ENTRY_HEIGHT
        );
        this.list.setX(LIST_LEFT);
        this.addRenderableWidget(this.list);

        // Done button
        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                        .bounds(this.width - 90 - PADDING, this.height - 24, 90, 20)
                        .build()
        );

        populateList();
        applyFilter(this.searchBox.getValue());
    }

    @Override
    public void tick() {
        super.tick();
    }

    private void populateList() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        final List<ResourceLocation> livingIds = new ArrayList<>();
        for (final ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            try {
                final EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
                final Entity created = type.create(mc.level);
                if (created instanceof LivingEntity) {
                    livingIds.add(id);
                }
            } catch (Throwable ignored) {
                // Some modded entity types may not be safely creatable on a ClientLevel.
            }
        }

        livingIds.sort(Comparator.comparing(ResourceLocation::toString));
        this.list.setAllIds(livingIds);
    }

    private void applyFilter(final String rawQuery) {
        final String query = rawQuery == null ? "" : rawQuery.toLowerCase(Locale.ROOT).trim();
        this.list.applyFilter(query);
    }

    void onSelected(final ResourceLocation id) {
        this.selectedId = id;
        this.previewEntity = createPreviewEntity(id);
    }

    private LivingEntity createPreviewEntity(final ResourceLocation id) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }

        final EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        try {
            final Entity created = type.create(mc.level);
            if (!(created instanceof LivingEntity living)) {
                return null;
            }

            living.setSilent(true);
            living.setInvulnerable(true);
            if (living instanceof Mob mob) {
                mob.setNoAi(true);
            }

            return living;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void render(final GuiGraphics gg, final int mouseX, final int mouseY, final float partialTick) {
        this.renderBackground(gg, mouseX, mouseY, partialTick);

        super.render(gg, mouseX, mouseY, partialTick);

        // Title
        gg.drawString(this.font, this.title, LIST_LEFT, TITLE_Y, 0xFFFFFF, false);

        // Right panel bounds
        final int panelLeft = LIST_LEFT + LIST_WIDTH + PADDING;
        final int panelRight = this.width - PADDING;
        final int panelTop = this.listTop;
        final int panelBottom = this.listBottom;

        // Panel background
        gg.fill(panelLeft, panelTop, panelRight, panelBottom, 0xAA000000);
        gg.fill(panelLeft + 1, panelTop + 1, panelRight - 1, panelBottom - 1, 0x66000000);

        // Compute preview box inside panel
        previewX1 = panelLeft + 10;
        previewY1 = panelTop + 28;
        previewX2 = panelRight - 10;
        previewY2 = panelBottom - 64;

        // Header text
        final String sel = (selectedId == null) ? "(none)" : selectedId.toString();
        gg.drawString(this.font, Component.literal(sel), panelLeft + 8, panelTop + 6, 0xFFFFFF, false);

        // Preview
        if (previewEntity != null) {
            final int w = Math.max(20, previewX2 - previewX1);
            final int h = Math.max(20, previewY2 - previewY1);
            final int scaleLimit = (int) Math.min(w / 2.2f, h / 2.2f);
            final int scale = Math.max(20, Math.min(this.previewScale, scaleLimit));

            // Render the entity inside the preview rectangle.
            // The entity is NOT added to the world.
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    gg,
                    previewX1,
                    previewY1,
                    previewX2,
                    previewY2,
                    scale,
                    0.0f,
                    (float) mouseX,
                    (float) mouseY,
                    previewEntity
            );
        } else {
            gg.drawCenteredString(this.font, Component.literal("Select an entity"), (panelLeft + panelRight) / 2, (previewY1 + previewY2) / 2, 0xAAAAAA);
        }

        // Summon hint
        final int hintY = panelBottom - 54;
        gg.drawString(this.font, Component.literal("Summon hint:"), panelLeft + 8, hintY, 0xCCCCCC, false);
        final String cmd = selectedId == null ? "/summon <id> ~ ~ ~" : ("/summon " + selectedId + " ~ ~ ~");
        gg.drawString(this.font, Component.literal(cmd), panelLeft + 8, hintY + 12, 0xFFFFFF, false);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (isMouseOverPreview(mouseX, mouseY)) {
            final int step = (scrollY > 0) ? 4 : -4;
            this.previewScale = Math.max(20, Math.min(200, this.previewScale + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isMouseOverPreview(final double mouseX, final double mouseY) {
        return mouseX >= previewX1 && mouseX <= previewX2 && mouseY >= previewY1 && mouseY <= previewY2;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
