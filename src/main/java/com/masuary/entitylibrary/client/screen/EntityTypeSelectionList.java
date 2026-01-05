package com.masuary.entitylibrary.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.List;

/**
 * Left-hand side list widget for entity ids.
 */
public class EntityTypeSelectionList extends ObjectSelectionList<EntityTypeSelectionList.Entry> {
    private final EntityLibraryScreen parent;
    private final Font font;

    public EntityTypeSelectionList(EntityLibraryScreen parent, Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
        super(minecraft, width, height, top, bottom, itemHeight);
        this.parent = parent;
        this.font = minecraft.font;
        this.setRenderBackground(true);
        this.setRenderTopAndBottom(false);
    }

    public void setEntries(List<ResourceLocation> ids, ResourceLocation selected) {
        this.clearEntries();
        for (ResourceLocation id : ids) {
            this.addEntry(new Entry(id));
        }

        if (selected != null) {
            for (Entry e : this.children()) {
                if (e.id.equals(selected)) {
                    this.setSelected(e);
                    this.centerScrollOn(e);
                    break;
                }
            }
        }
    }

    @Override
    protected int getScrollbarPosition() {
        return this.x1 - 6;
    }

    @Override
    public int getRowWidth() {
        return this.width - 12;
    }

    public class Entry extends ObjectSelectionList.Entry<Entry> {
        public final ResourceLocation id;

        public Entry(ResourceLocation id) {
            this.id = id;
        }

        @Override
        public void render(PoseStack poseStack, int index, int y, int x, int rowWidth, int rowHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            String text = this.id.toString();
            int color = isSelected ? 0xFFFFFF : 0xC0C0C0;
            EntityTypeSelectionList.this.font.draw(poseStack, text, (float) (x + 3), (float) (y + 2), color);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            EntityTypeSelectionList.this.setSelected(this);
            EntityTypeSelectionList.this.parent.selectEntityId(this.id);
            return true;
        }

        @Override
        public Component getNarration() {
            return new TextComponent(this.id.toString());
        }
    }
}
