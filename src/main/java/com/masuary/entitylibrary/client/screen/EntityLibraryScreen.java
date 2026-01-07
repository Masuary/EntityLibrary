package com.masuary.entitylibrary.client.screen;

import com.masuary.entitylibrary.EntityLibraryMod;
import com.masuary.entitylibrary.client.EntityLibraryClientData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Standalone client-only screen that lists registered LivingEntity types and renders a preview instance.
 *
 * The preview entity is instantiated client-side but is NOT added to the world.
 */
public final class EntityLibraryScreen extends Screen {
    private static final int ENTRY_HEIGHT = 18;

    private static final int TITLE_Y = 8;
    private static final int SEARCH_H = 18;

    // GUI sprites (written to the GUI atlas under textures/gui/sprites)
    private static final ResourceLocation SPRITE_PANEL = ResourceLocation.fromNamespaceAndPath(EntityLibraryMod.MODID, "entity_library/panel");
    private static final ResourceLocation SPRITE_SEARCH = ResourceLocation.fromNamespaceAndPath(EntityLibraryMod.MODID, "entity_library/searchbox");

    // Extra padding keeps the first/last visible rows from being partially covered by list fades.
    private static final int LIST_GAP = 8;

    private static final int PADDING = 12;
    private static final int PANEL_INSET = 10;

    // Controls (small buttons)
    private static final int BTN_H = 16;
    private static final int BTN_GAP = 4;
    private static final int BTN_ROW_GAP = 2;

    private final Screen parent;
    private final EntityLibraryClientData data = EntityLibraryClientData.get();
    private final Map<ResourceLocation, String> translatedNameCache = new HashMap<>();

    private EditBox searchBox;
    private EntityTypeSelectionList list;

    // Left panel layout
    private int listLeft;
    private int listWidth;
    private int searchY;
    private int buttonsY1;
    private int buttonsY2;

    // Outer left panel bounds (used for drawing + aligning the right preview panel)
    private int leftPanelX;
    private int leftPanelY;
    private int leftPanelW;
    private int leftPanelH;

    // List viewport (aligned rows)
    private int listTop;
    private int listBottom;

    // Right panel bounds
    private int rightPanelLeft;
    private int rightPanelRight;
    private int rightPanelTop;
    private int rightPanelBottom;

    // Preview rect (computed each frame)
    private int previewX1, previewY1, previewX2, previewY2;

    private LivingEntity previewEntity;
    private ResourceLocation selectedId;

    // View model
    private List<ResourceLocation> allLivingIds = new ArrayList<>();
    private List<String> namespaces = new ArrayList<>();
    private int namespaceIndex = 0;

    // Buttons (left)
    private Button btnAll;
    private Button btnFav;
    private Button btnRec;
    private Button btnIds;
    private Button btnSort;
    private Button btnMod;

    // Buttons (right)
    private Button btnCopySummon;
    private Button btnCopyId;
    private Button btnFavToggle;
    private Button btnDone;

    // Preview zoom (user-controlled, multiplicative)
    private float previewZoom = 1.0f;

    // Copy toast
    private int copyToastTicks = 0;
    private String lastCopied = "";

    public EntityLibraryScreen(final Screen parent) {
        super(Component.translatable("screen.entitylibrary.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final Minecraft mc = Minecraft.getInstance();

        this.searchY = TITLE_Y + this.font.lineHeight + 6;
        final int doneY = this.height - 24;

        // Responsive width: keep the left panel usable across very small and very large windows.
        this.leftPanelX = PADDING;
        this.leftPanelY = TITLE_Y - 6;

        int targetPanelW = (int) (this.width * 0.38f);
        targetPanelW = Math.max(280, Math.min(440, targetPanelW));

        // Ensure the preview panel has a sane minimum width.
        final int minPreviewW = 320;
        final int maxPanelW = Math.max(240, this.width - (PADDING * 3) - minPreviewW);
        this.leftPanelW = Math.min(targetPanelW, maxPanelW);

        this.listLeft = this.leftPanelX + PANEL_INSET;
        this.listWidth = Math.max(120, this.leftPanelW - (PANEL_INSET * 2));

        // Buttons live under the search box.
        this.buttonsY1 = this.searchY + SEARCH_H + 4;
        this.buttonsY2 = this.buttonsY1 + BTN_H + BTN_ROW_GAP;

        // List starts under the second button row.
        this.listTop = this.buttonsY2 + BTN_H + LIST_GAP;

        final int bottomCandidate = doneY - 8;
        final int usableHeight = Math.max(0, bottomCandidate - this.listTop);
        final int alignedHeight = (usableHeight / ENTRY_HEIGHT) * ENTRY_HEIGHT;
        // Keep at least a couple of rows visible even on very small GUI scales.
        final int minHeight = ENTRY_HEIGHT * 4;
        final int targetBottom = this.listTop + Math.max(minHeight, alignedHeight);
        this.listBottom = Math.min(bottomCandidate, targetBottom);

        // Left panel height extends to the bottom of the list area.
        this.leftPanelH = this.listBottom - this.leftPanelY;

        // Right panel bounds (match full height of the left panel so both panels align)
        this.rightPanelLeft = this.leftPanelX + this.leftPanelW + PADDING;
        this.rightPanelRight = this.width - PADDING;
        this.rightPanelTop = this.leftPanelY;
        this.rightPanelBottom = this.listBottom;

        // Search box (inset to look centered in the textured frame)
        final int searchX = this.listLeft + 6;
        final int searchW = Math.max(60, this.listWidth - 12);

        this.searchBox = new EditBox(this.font, searchX, this.searchY + 2, searchW, SEARCH_H - 4, Component.translatable("screen.entitylibrary.search"));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(128);
        this.searchBox.setResponder(this::applyFilter);
        this.addRenderableWidget(this.searchBox);

        // Left control buttons
        createLeftButtons();

        // List widget
        this.list = new EntityTypeSelectionList(
                this,
                mc,
                this.listWidth,
                Math.max(0, this.listBottom - this.listTop),
                this.listTop,
                this.listBottom,
                ENTRY_HEIGHT
        );
        this.list.setX(this.listLeft);
        this.addRenderableWidget(this.list);

        // Right buttons (copy / favorite)
        createRightButtons();

        populateList();
        refreshBaseList();
        applyFilter(this.searchBox.getValue());
        refreshButtons();
    }

    private void createLeftButtons() {
        final int x0 = this.listLeft;
        final int y1 = this.buttonsY1;
        final int y2 = this.buttonsY2;

        final int wSmall = 36;
        int x = x0;

        this.btnAll = Button.builder(Component.literal("All"), b -> setViewMode("ALL"))
                .tooltip(Tooltip.create(Component.translatable("screen.entitylibrary.tooltip.view_all")))
                .bounds(x, y1, wSmall, BTN_H).build();
        x += wSmall + BTN_GAP;

        this.btnFav = Button.builder(Component.literal("Fav"), b -> setViewMode("FAV"))
                .tooltip(Tooltip.create(Component.translatable("screen.entitylibrary.tooltip.view_favorites")))
                .bounds(x, y1, wSmall, BTN_H).build();
        x += wSmall + BTN_GAP;

        this.btnRec = Button.builder(Component.literal("Rec"), b -> setViewMode("REC"))
                .tooltip(Tooltip.create(Component.translatable("screen.entitylibrary.tooltip.view_recent")))
                .bounds(x, y1, wSmall, BTN_H).build();
        x += wSmall + BTN_GAP;

        final int wIds = 62;
        this.btnIds = Button.builder(Component.literal("IDs: Off"), b -> toggleIdsInline())
                .tooltip(Tooltip.create(Component.translatable("screen.entitylibrary.tooltip.toggle_ids")))
                .bounds(x, y1, wIds, BTN_H).build();

        // Row 2: Sort + Mod filter
        final int wSort = 88;
        this.btnSort = Button.builder(Component.literal("Sort: Name"), b -> toggleSortMode())
                .tooltip(Tooltip.create(Component.translatable("screen.entitylibrary.tooltip.toggle_sort")))
                .bounds(x0, y2, wSort, BTN_H).build();

        final int wMod = Math.max(60, this.listWidth - wSort - BTN_GAP);
        this.btnMod = Button.builder(Component.literal("Mod: All"), b -> cycleNamespaceFilter())
                .tooltip(Tooltip.create(Component.translatable("screen.entitylibrary.tooltip.filter_mod")))
                .bounds(x0 + wSort + BTN_GAP, y2, wMod, BTN_H).build();

        this.addRenderableWidget(this.btnAll);
        this.addRenderableWidget(this.btnFav);
        this.addRenderableWidget(this.btnRec);
        this.addRenderableWidget(this.btnIds);
        this.addRenderableWidget(this.btnSort);
        this.addRenderableWidget(this.btnMod);
    }

    private void createRightButtons() {
        final int panelLeft = this.rightPanelLeft;
        final int panelRight = this.rightPanelRight;
        final int panelTop = this.rightPanelTop;
        final int panelBottom = this.rightPanelBottom;

        // Favorite toggle in the header (small star)
        this.btnFavToggle = Button.builder(Component.literal("☆"), b -> {
                    if (this.selectedId != null) {
                        toggleFavorite(this.selectedId);
                    }
                })
                .tooltip(Tooltip.create(Component.translatable("screen.entitylibrary.tooltip.toggle_favorite")))
                .bounds(panelRight - 28, panelTop + 4, 20, BTN_H)
                .build();
        this.addRenderableWidget(this.btnFavToggle);

        // Copy buttons at the bottom-right panel
        final int y = panelBottom - (BTN_H + 10);
        this.btnCopySummon = Button.builder(Component.literal("Copy Summon"), b -> copySummon())
                .tooltip(Tooltip.create(Component.translatable("screen.entitylibrary.tooltip.copy_summon")))
                .bounds(panelLeft + 10, y, 96, BTN_H).build();
        this.btnCopyId = Button.builder(Component.literal("Copy ID"), b -> copyId())
                .tooltip(Tooltip.create(Component.translatable("screen.entitylibrary.tooltip.copy_id")))
                .bounds(panelLeft + 10 + 96 + BTN_GAP, y, 72, BTN_H).build();

        // Done button inside the preview panel (same row as the copy buttons, aligned right)
        this.btnDone = Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .tooltip(Tooltip.create(Component.translatable("screen.entitylibrary.tooltip.done")))
                .bounds(panelRight - 10 - 72, y, 72, BTN_H)
                .build();

        this.addRenderableWidget(this.btnCopySummon);
        this.addRenderableWidget(this.btnCopyId);
        this.addRenderableWidget(this.btnDone);
    }

    private void setViewMode(final String mode) {
        if (mode == null) return;
        this.data.viewMode = mode;
        this.data.save();
        refreshBaseList();
        applyFilter(this.searchBox.getValue());
        refreshButtons();
    }

    private void toggleIdsInline() {
        this.data.showIdsInline = !this.data.showIdsInline;
        this.data.save();
        refreshButtons();
    }

    private void toggleSortMode() {
        this.data.sortMode = this.data.sortMode.equals("NAME") ? "ID" : "NAME";
        this.data.save();
        refreshBaseList();
        applyFilter(this.searchBox.getValue());
        refreshButtons();
    }

    private void cycleNamespaceFilter() {
        if (this.namespaces.isEmpty()) {
            this.data.namespaceFilter = "ALL";
            this.data.save();
            refreshButtons();
            refreshBaseList();
            applyFilter(this.searchBox.getValue());
            return;
        }

        // namespaces list always includes "ALL" at index 0
        this.namespaceIndex = (this.namespaceIndex + 1) % this.namespaces.size();
        this.data.namespaceFilter = this.namespaces.get(this.namespaceIndex);
        this.data.save();

        refreshBaseList();
        applyFilter(this.searchBox.getValue());
        refreshButtons();
    }

    private void refreshButtons() {
        // View mode buttons: disable the active one as a simple indicator
        final String vm = this.data.viewMode;
        if (this.btnAll != null) this.btnAll.active = !vm.equals("ALL");
        if (this.btnFav != null) this.btnFav.active = !vm.equals("FAV");
        if (this.btnRec != null) this.btnRec.active = !vm.equals("REC");

        if (this.btnIds != null) {
            this.btnIds.setMessage(Component.literal(this.data.showIdsInline ? "IDs: On" : "IDs: Off"));
        }

        final boolean sortEnabled = !vm.equals("REC"); // Recents is intentionally ordered by recency
        if (this.btnSort != null) {
            this.btnSort.active = sortEnabled;
            this.btnSort.setMessage(Component.literal(this.data.sortMode.equals("NAME") ? "Sort: Name" : "Sort: ID"));
        }

        if (this.btnMod != null) {
            final String ns = this.data.namespaceFilter == null ? "ALL" : this.data.namespaceFilter;
            this.btnMod.setMessage(Component.literal("Mod: " + (ns.equals("ALL") ? "All" : ns)));
        }

        if (this.btnFavToggle != null) {
            if (this.selectedId == null) {
                this.btnFavToggle.active = false;
                this.btnFavToggle.setMessage(Component.literal("☆"));
            } else {
                this.btnFavToggle.active = true;
                this.btnFavToggle.setMessage(Component.literal(this.data.isFavorite(this.selectedId) ? "★" : "☆"));
            }
        }

        final boolean hasSelection = this.selectedId != null;
        if (this.btnCopySummon != null) this.btnCopySummon.active = hasSelection;
        if (this.btnCopyId != null) this.btnCopyId.active = hasSelection;
    }

    private void populateList() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        translatedNameCache.clear();

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
        this.allLivingIds = livingIds;

        // Build namespace list (first element must be ALL)
        final Set<String> nsSet = new HashSet<>();
        for (final ResourceLocation id : livingIds) {
            nsSet.add(id.getNamespace());
        }
        final List<String> nsList = new ArrayList<>(nsSet);
        nsList.sort(String::compareTo);

        this.namespaces = new ArrayList<>();
        this.namespaces.add("ALL");
        this.namespaces.addAll(nsList);

        // restore namespace index from persisted filter
        this.namespaceIndex = 0;
        final String filter = this.data.namespaceFilter == null ? "ALL" : this.data.namespaceFilter;
        for (int i = 0; i < this.namespaces.size(); i++) {
            if (this.namespaces.get(i).equals(filter)) {
                this.namespaceIndex = i;
                break;
            }
        }
    }

    private void refreshBaseList() {
        final List<ResourceLocation> base = new ArrayList<>();
        final String vm = this.data.viewMode;

        if (vm.equals("FAV")) {
            for (final String s : this.data.favorites) {
                try {
                    base.add(ResourceLocation.parse(s));
                } catch (Throwable ignored) {
                }
            }
        } else if (vm.equals("REC")) {
            for (final String s : this.data.recents) {
                try {
                    base.add(ResourceLocation.parse(s));
                } catch (Throwable ignored) {
                }
            }
        } else {
            base.addAll(this.allLivingIds);
        }

        // Apply namespace filter at the base list stage (less work later)
        final String ns = this.data.namespaceFilter == null ? "ALL" : this.data.namespaceFilter;
        if (!ns.equals("ALL")) {
            base.removeIf(id -> id == null || !ns.equals(id.getNamespace()));
        }

        // Sort, unless in REC mode
        if (!vm.equals("REC")) {
            if (this.data.sortMode.equals("NAME")) {
                base.sort(Comparator.comparing(a -> getTranslatedName(a).toLowerCase(Locale.ROOT)));
            } else {
                base.sort(Comparator.comparing(ResourceLocation::toString));
            }
        }

        this.list.setAllIds(base);
        this.list.applyFilter(this.searchBox.getValue());

        // Keep selection highlight when possible
        if (this.selectedId != null) {
            this.list.trySelectById(this.selectedId);
        }
    }

    private void applyFilter(final String rawQuery) {
        final String query = rawQuery == null ? "" : rawQuery.toLowerCase(Locale.ROOT).trim();
        if (this.list != null) {
            this.list.applyFilter(query);
        }
    }

    void onSelected(final ResourceLocation id) {
        this.selectedId = id;
        this.previewEntity = createPreviewEntity(id);

        // Update recents and (if viewing recents) refresh base list to reflect new order
        this.data.touchRecent(id);
        if (this.data.viewMode.equals("REC")) {
            refreshBaseList();
        }

        refreshButtons();
    }

    void toggleFavorite(final ResourceLocation id) {
        this.data.toggleFavorite(id);
        if (this.data.viewMode.equals("FAV")) {
            refreshBaseList();
        }
        refreshButtons();
    }

    boolean isFavorite(final ResourceLocation id) {
        return this.data.isFavorite(id);
    }

    boolean showIdsInline() {
        return this.data.showIdsInline;
    }

    String getTranslatedName(final ResourceLocation id) {
        if (id == null) return "";
        return translatedNameCache.computeIfAbsent(id, key -> {
            final EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(key);
            final String descId = type.getDescriptionId();
            final Component translated = Component.translatable(descId);

            // If a translation is missing, Minecraft typically returns the key itself.
            if (translated.getString().equals(descId)) {
                return key.toString();
            }
            return translated.getString();
        });
    }

    Component getEntityDisplayName(final ResourceLocation id) {
        if (id == null) return Component.literal("(none)");
        final EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        final String key = type.getDescriptionId();
        final Component translated = Component.translatable(key);

        if (translated.getString().equals(key)) {
            return Component.literal(id.toString());
        }
        return translated;
    }

    private LivingEntity createPreviewEntity(final ResourceLocation id) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || id == null) {
            return null;
        }

        try {
            final EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            final Entity created = type.create(mc.level);
            if (created instanceof LivingEntity living) {
                return living;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void copySummon() {
        if (this.selectedId == null) return;
        final String cmd = "/summon " + this.selectedId + " ~ ~ ~";
        Minecraft.getInstance().keyboardHandler.setClipboard(cmd);
        this.lastCopied = cmd;
        this.copyToastTicks = 40;
    }

    private void copyId() {
        if (this.selectedId == null) return;
        final String s = this.selectedId.toString();
        Minecraft.getInstance().keyboardHandler.setClipboard(s);
        this.lastCopied = s;
        this.copyToastTicks = 40;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.copyToastTicks > 0) {
            this.copyToastTicks--;
        }
    }

    @Override
    public void renderBackground(final GuiGraphics gg, final int mouseX, final int mouseY, final float partialTick) {
        // NO-OP.
        // We render the vanilla background exactly once in render() via super.renderBackground(...).
        // This prevents Screen#render() (invoked by super.render(...)) from drawing the blurred backdrop a second time,
        // which makes custom panel sprites look "behind" the background.
    }

    @Override
    public void render(final GuiGraphics gg, final int mouseX, final int mouseY, final float partialTick) {
        // Draw the vanilla blurred backdrop ONCE.
        super.renderBackground(gg, mouseX, mouseY, partialTick);

        // Left panel (behind search + list)
        gg.fill(this.leftPanelX, this.leftPanelY, this.leftPanelX + this.leftPanelW, this.leftPanelY + this.leftPanelH, 0xFF0B0C0E);
        gg.blitSprite(SPRITE_PANEL, this.leftPanelX, this.leftPanelY, this.leftPanelW, this.leftPanelH);

        // Right panel (aligned to list viewport)
        gg.fill(this.rightPanelLeft, this.rightPanelTop, this.rightPanelRight, this.rightPanelBottom, 0xFF0B0C0E);
        gg.blitSprite(SPRITE_PANEL, this.rightPanelLeft, this.rightPanelTop, this.rightPanelRight - this.rightPanelLeft, this.rightPanelBottom - this.rightPanelTop);

        // Search box sprite (frame behind the EditBox)
        final int searchFrameX = this.listLeft;
        final int searchFrameY = this.searchY;
        gg.blitSprite(SPRITE_SEARCH, searchFrameX, searchFrameY, this.listWidth, SEARCH_H);

        // Render children (list, edit box, buttons)
        super.render(gg, mouseX, mouseY, partialTick);

        // Title
        gg.drawString(this.font, this.title, this.leftPanelX + PANEL_INSET, TITLE_Y, 0xFFFFFF, false);

// Header text (translated entity name)
        final Component sel = (selectedId == null) ? Component.literal("(none)") : getEntityDisplayName(selectedId);
        gg.drawString(this.font, sel, this.rightPanelLeft + 10, this.rightPanelTop + 6, 0xFFFFFF, false);

        // Compute preview box inside the right panel
        this.previewX1 = this.rightPanelLeft + 10;
        this.previewY1 = this.rightPanelTop + 28;
        this.previewX2 = this.rightPanelRight - 10;

        // Reserve space at bottom for the copy buttons
        this.previewY2 = this.rightPanelBottom - (BTN_H + 18);

        // Preview
        if (previewEntity != null) {
            final int w = Math.max(20, previewX2 - previewX1);
            final int h = Math.max(20, previewY2 - previewY1);
            final int scaleLimit = (int) Math.min(w / 2.2f, h / 2.2f);

            // Auto-scale based on bounding box height; clamp and apply user zoom.
            final float bbH = Math.max(0.1f, previewEntity.getBbHeight());
            final float auto = 60.0f * (1.8f / bbH);
            final int scale = (int) Mth.clamp(auto * this.previewZoom, 20.0f, (float) Math.max(20, scaleLimit));

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
            gg.drawCenteredString(this.font, Component.literal("Select an entity to preview"), (previewX1 + previewX2) / 2, (previewY1 + previewY2) / 2, 0xAAAAAA);
        }

        // Copy toast (shown after copy actions)
        if (this.copyToastTicks > 0) {
            final int toastY = this.rightPanelBottom - (BTN_H + 34);
            gg.drawString(this.font, Component.literal("Copied"), this.rightPanelLeft + 10, toastY, 0x88FF88, false);
        }
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (isMouseOverPreview(mouseX, mouseY)) {
            final float step = (scrollY > 0) ? 0.1f : -0.1f;
            this.previewZoom = Mth.clamp(this.previewZoom + step, 0.25f, 3.0f);
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
