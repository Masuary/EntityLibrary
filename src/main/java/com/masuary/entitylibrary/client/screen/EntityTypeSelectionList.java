package com.masuary.entitylibrary.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class EntityTypeSelectionList extends ObjectSelectionList<EntityTypeSelectionList.Entry> {
    private final EntityLibraryScreen owner;

    private List<ResourceLocation> allIds = Collections.emptyList();
    private String query = "";

    EntityTypeSelectionList(
            final EntityLibraryScreen owner,
            final Minecraft mc,
            final int width,
            final int height,
            final int top,
            final int bottom,
            final int itemHeight
    ) {
        // NeoForge 1.21.1 uses the 5-argument constructor (no custom item height).
        super(mc, width, height, top, bottom);
        this.owner = owner;

        // Mojang/NeoForge 1.21.1 removed the constructor overload that accepts a custom row height.
        // To keep the UI compact (matching the Forge 1.18.2/1.21.x builds), we set the underlying
        // row height via reflection. If mappings change or the field is renamed, we gracefully fall
        // back to the default.
        setRowHeightCompat(itemHeight);
    }

    private void setRowHeightCompat(final int itemHeight) {
        if (itemHeight <= 0) {
            return;
        }

        // Try a few likely field names across minor mapping/API changes.
        final String[] candidates = new String[] {"itemHeight", "rowHeight", "entryHeight"};
        for (final String name : candidates) {
            if (trySetIntField(this, name, itemHeight)) {
                return;
            }
        }
    }

    private static boolean trySetIntField(final Object instance, final String fieldName, final int value) {
        Class<?> c = instance.getClass();
        while (c != null && c != Object.class) {
            try {
                final java.lang.reflect.Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.setInt(instance, value);
                return true;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    void setAllIds(final List<ResourceLocation> ids) {
        this.allIds = new ArrayList<>(ids);
        applyFilter(this.query);
    }

    void applyFilter(final String query) {
        this.query = query == null ? "" : query;
        this.clearEntries();

        for (final ResourceLocation id : this.allIds) {
            if (this.query.isEmpty() || id.toString().toLowerCase().contains(this.query.toLowerCase())) {
                this.addEntry(new Entry(id));
            }
        }

        // Preserve selection if still present
        final Entry selected = this.getSelected();
        if (selected != null && !this.children().contains(selected)) {
            this.setSelected(null);
        }
    }

    @Override
    public int getRowWidth() {
        return this.width - 12;
    }

    // Keep rows aligned to the list's left edge (instead of centered), so the selectable
    // area doesn't look like oversized "buttons" on small/zoomed GUIs.
    public int getRowLeft() {
        return this.getX() + 2;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getX() + this.width - 6;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        final boolean handled = super.mouseClicked(mouseX, mouseY, button);
        final Entry selected = this.getSelected();
        if (selected != null) {
            this.owner.onSelected(selected.id);
        }
        return handled;
    }

    final class Entry extends ObjectSelectionList.Entry<Entry> {
        final ResourceLocation id;

        Entry(final ResourceLocation id) {
            this.id = id;
        }

        @Override
        public Component getNarration() {
            return Component.literal(this.id.toString());
        }

        @Override
        public void render(
                final GuiGraphics gg,
                final int index,
                final int y,
                final int x,
                final int rowWidth,
                final int rowHeight,
                final int mouseX,
                final int mouseY,
                final boolean hovered,
                final float partialTick
        ) {
            final int color = hovered ? 0xFFFFFF : 0xDDDDDD;
            final var font = Minecraft.getInstance().font;
            final int textY = y + Math.max(0, (rowHeight - font.lineHeight) / 2);
            gg.drawString(font, this.id.toString(), x + 4, textY, color, false);
        }

        @Override
        public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
            EntityTypeSelectionList.this.setSelected(this);
            return true;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof Entry other)) return false;
            return this.id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return this.id.hashCode();
        }
    }
}
