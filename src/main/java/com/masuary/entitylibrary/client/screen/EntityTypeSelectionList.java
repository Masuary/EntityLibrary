package com.masuary.entitylibrary.client.screen;

import com.masuary.entitylibrary.EntityLibraryMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lightweight scrollable list for entity IDs with a custom textured selection highlight.
 *
 * This intentionally does NOT use AbstractSelectionList/ObjectSelectionList because those widgets
 * render a top/bottom gradient fade that can interfere visually with custom textured panels.
 */
final class EntityTypeSelectionList extends AbstractWidget {

    private static final int DEFAULT_ROW_H = 18;

    // GUI sprite: selection highlight
    private static final ResourceLocation SPRITE_SELECTION = ResourceLocation.fromNamespaceAndPath(EntityLibraryMod.MODID, "entity_library/selection");

    private static final int SCROLLBAR_W = 8;

    /**
     * Padding between the scrollbar and the list widget bounds.
     *
     * We apply this both horizontally and vertically so the bar never overlaps the
     * panel's rounded border and the bottom "cap" doesn't clip into the frame.
     */
    private static final int SCROLLBAR_PAD_X = 2;
    // Needs to be larger than the panel border thickness so the bar never clips the rounded frame.
    private static final int SCROLLBAR_PAD_Y = 6;

    // Colors
    private static final int COLOR_GOLD = 0xFFB8954B;
    private static final int COLOR_TRACK = 0xCC050505;
    private static final int COLOR_TRACK_INNER = 0x88000000;
    private static final int COLOR_THUMB = 0xFF15110A;
    private static final int COLOR_THUMB_HOVER = 0xFF1F180C;

    private final EntityLibraryScreen owner;
    private final Minecraft mc;
    private final int rowHeight;

    private List<ResourceLocation> allIds = Collections.emptyList();
    private final List<ResourceLocation> filtered = new ArrayList<>();
    private String query = "";

    private int scrollPixels = 0;
    private int selectedIndex = -1;

    EntityTypeSelectionList(
            final EntityLibraryScreen owner,
            final Minecraft mc,
            final int width,
            final int height,
            final int top,
            final int bottom,
            final int itemHeight
    ) {
        super(0, top, width, height, Component.empty());
        this.owner = owner;
        this.mc = mc;
        this.rowHeight = itemHeight > 0 ? itemHeight : DEFAULT_ROW_H;
    }

    void setAllIds(final List<ResourceLocation> ids) {
        this.allIds = ids == null ? Collections.emptyList() : new ArrayList<>(ids);
        applyFilter(this.query);
    }

    void applyFilter(final String query) {
        this.query = query == null ? "" : query;

        // Preserve selection by id if possible
        ResourceLocation prevSelected = null;
        if (this.selectedIndex >= 0 && this.selectedIndex < this.filtered.size()) {
            prevSelected = this.filtered.get(this.selectedIndex);
        }

        this.filtered.clear();
        final String q = this.query.trim().toLowerCase();
        for (final ResourceLocation id : this.allIds) {
            if (matchesQuery(id, q)) {
                this.filtered.add(id);
            }
        }

        if (prevSelected != null) {
            this.selectedIndex = this.filtered.indexOf(prevSelected);
        }
        if (this.selectedIndex < 0 || this.selectedIndex >= this.filtered.size()) {
            this.selectedIndex = -1;
        }

        this.scrollPixels = Mth.clamp(this.scrollPixels, 0, getMaxScroll());
    }

    private boolean matchesQuery(final ResourceLocation id, final String q) {
        if (id == null) return false;
        if (q == null || q.isEmpty()) return true;

        final String idStr = id.toString().toLowerCase();
        if (idStr.contains(q)) return true;

        final String name = this.owner.getTranslatedName(id).toLowerCase();
        return name.contains(q);
    }

    void trySelectById(final ResourceLocation id) {
        if (id == null) return;
        final int idx = this.filtered.indexOf(id);
        if (idx >= 0) {
            this.selectedIndex = idx;
        }
    }

    private int getContentHeight() {
        return this.filtered.size() * this.rowHeight;
    }

    private int getMaxScroll() {
        return Math.max(0, getContentHeight() - this.height);
    }

    private boolean isMouseOverList(final double mouseX, final double mouseY) {
        return mouseX >= this.getX() && mouseX < (this.getX() + this.width)
                && mouseY >= this.getY() && mouseY < (this.getY() + this.height);
    }

    @Override
    protected void renderWidget(final GuiGraphics gg, final int mouseX, final int mouseY, final float partialTick) {
        final int x = this.getX();
        final int y = this.getY();
        final int w = this.width;
        final int h = this.height;

        // Clip list content to the widget rectangle.
        gg.enableScissor(x, y, x + w, y + h);

        final int firstIndex = this.scrollPixels / this.rowHeight;
        final int yOffset = -(this.scrollPixels % this.rowHeight);
        final int visibleRows = (h / this.rowHeight) + 2;

        final int scrollbarW = SCROLLBAR_W;
        final int contentW = Math.max(10, w - (scrollbarW + SCROLLBAR_PAD_X));

        // Keep some horizontal padding so the highlight never hugs the panel border.
        final int rowX = x + 2;
        final int rowW = contentW - 4;

        for (int i = 0; i < visibleRows; i++) {
            final int idx = firstIndex + i;
            if (idx < 0 || idx >= this.filtered.size()) {
                continue;
            }

            final int rowY = y + yOffset + (i * this.rowHeight);
            if (rowY + this.rowHeight < y || rowY > y + h) {
                continue;
            }

            final boolean hovered = mouseX >= x && mouseX < (x + contentW) && mouseY >= rowY && mouseY < rowY + this.rowHeight;
            final boolean selected = idx == this.selectedIndex;

            if (selected || hovered) {
                gg.blitSprite(SPRITE_SELECTION, rowX, rowY, rowW, this.rowHeight);
            }

            final ResourceLocation id = this.filtered.get(idx);
            final boolean fav = this.owner.isFavorite(id);
            final boolean showIds = this.owner.showIdsInline();

            final int textY = rowY + Math.max(0, (this.rowHeight - this.mc.font.lineHeight) / 2);
            int textX = rowX + 8;

            // Star indicator (small, subtle)
            if (fav) {
                gg.drawString(this.mc.font, "★", rowX + 6, textY, COLOR_GOLD, false);
                textX += 10;
            }

            final int nameColor = hovered ? 0xFFFFFF : 0xE5E5E5;
            final String name = this.owner.getTranslatedName(id);
            gg.drawString(this.mc.font, name, textX, textY, nameColor, false);

            if (showIds) {
                final int nameW = this.mc.font.width(name);
                final String suffix = " (" + id + ")";
                gg.drawString(this.mc.font, suffix, textX + nameW, textY, 0x999999, false);
            }
        }

        gg.disableScissor();

        // Scrollbar (draw on top, outside scissor)
        renderScrollbar(gg, mouseX, mouseY, x + contentW, y, w - contentW, h);
    }

    private void renderScrollbar(final GuiGraphics gg, final int mouseX, final int mouseY, final int sbX, final int sbY, final int sbW, final int sbH) {
        final int contentH = getContentHeight();
        if (contentH <= sbH) {
            return; // No scrollbar needed
        }

        final int barX = sbX + SCROLLBAR_PAD_X / 2;
        final int barW = Math.max(4, sbW - SCROLLBAR_PAD_X);
        final int trackTop = sbY + SCROLLBAR_PAD_Y;
        final int trackBottom = sbY + sbH - SCROLLBAR_PAD_Y;
        final int trackH = Math.max(10, trackBottom - trackTop);

        // Track
        gg.fill(barX + 1, trackTop + 1, barX + barW - 1, trackBottom - 1, COLOR_TRACK);
        gg.fill(barX + 2, trackTop + 2, barX + barW - 2, trackBottom - 2, COLOR_TRACK_INNER);
        drawOutline(gg, barX, trackTop, barX + barW, trackBottom, COLOR_GOLD);

        // Thumb
        final int maxScroll = getMaxScroll();
        final float scrollRatio = maxScroll <= 0 ? 0.0f : (this.scrollPixels / (float) maxScroll);

        int thumbH = (int) ((trackH * (float) trackH) / (float) contentH);
        thumbH = Mth.clamp(thumbH, 18, trackH);

        final int thumbRange = trackH - thumbH;
        final int thumbY = trackTop + (int) (thumbRange * scrollRatio);

        final boolean hoverThumb = mouseX >= barX && mouseX < (barX + barW) && mouseY >= thumbY && mouseY < (thumbY + thumbH);
        gg.fill(barX + 1, thumbY + 1, barX + barW - 1, thumbY + thumbH - 1, hoverThumb ? COLOR_THUMB_HOVER : COLOR_THUMB);
        drawOutline(gg, barX, thumbY, barX + barW, thumbY + thumbH, COLOR_GOLD);
    }

    private static void drawOutline(final GuiGraphics gg, final int x1, final int y1, final int x2, final int y2, final int argb) {
        // Top
        gg.fill(x1, y1, x2, y1 + 1, argb);
        // Bottom
        gg.fill(x1, y2 - 1, x2, y2, argb);
        // Left
        gg.fill(x1, y1, x1 + 1, y2, argb);
        // Right
        gg.fill(x2 - 1, y1, x2, y2, argb);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (!isMouseOverList(mouseX, mouseY)) {
            return false;
        }

        final int step = (int) (-scrollY * this.rowHeight);
        this.scrollPixels = Mth.clamp(this.scrollPixels + step, 0, getMaxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if ((button != 0 && button != 1) || !isMouseOverList(mouseX, mouseY)) {
            return false;
        }

        final int x = this.getX();
        final int y = this.getY();
        final int w = this.width;
        final int scrollbarW = SCROLLBAR_W;
        final int contentW = Math.max(10, w - (scrollbarW + SCROLLBAR_PAD_X));

        if (mouseX >= x + contentW) {
            // Clicked scrollbar area; ignore for now.
            return false;
        }

        final int localY = (int) mouseY - y + this.scrollPixels;
        final int idx = localY / this.rowHeight;

        if (idx >= 0 && idx < this.filtered.size()) {
            this.selectedIndex = idx;
            final ResourceLocation id = this.filtered.get(idx);

            // Left click: select; Right click: toggle favorite + select
            if (button == 1) {
                this.owner.toggleFavorite(id);
            }
            this.owner.onSelected(id);
            return true;
        }

        return false;
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput narrationElementOutput) {
        // No narration needed for this simple list.
    }
}
