package com.masuary.entitylibrary.client.screen;

import com.masuary.entitylibrary.client.data.FavoritesManager;
import com.masuary.entitylibrary.client.data.Theme;
import com.masuary.entitylibrary.client.data.ThemeManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

public class EntityTypeSelectionList extends ObjectSelectionList<EntityTypeSelectionList.Entry> {
    private final EntityLibraryScreen parent;
    private final Font font;

    public EntityTypeSelectionList(EntityLibraryScreen parent, Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
        super(minecraft, width, height, top, bottom, itemHeight);
        this.parent = parent;
        this.font = minecraft.font;
        this.setRenderBackground(false);
        this.setRenderTopAndBottom(false);
    }

    public void setEntries(List<ResourceLocation> ids, @Nullable ResourceLocation selected) {
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
    public void setSelected(@Nullable Entry entry) {
        super.setSelected(entry);
        if (entry != null) {
            this.parent.selectEntityId(entry.id);
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
        private final String displayName;

        public Entry(ResourceLocation id) {
            this.id = id;
            EntityType<?> type = ForgeRegistries.ENTITIES.getValue(id);
            this.displayName = type != null ? type.getDescription().getString() : id.getPath();
        }

        @Override
        public void render(PoseStack poseStack, int index, int y, int x, int rowWidth, int rowHeight, int mouseX, int mouseY, boolean isHovered, float partialTicks) {
            Theme theme = ThemeManager.get().currentTheme();
            boolean isSelected = EntityTypeSelectionList.this.getSelected() == this;
            int nameColor = isSelected ? theme.listSelectedText() : theme.listUnselectedText();
            int idColor = isSelected ? theme.listSelectedIdText() : theme.listUnselectedIdText();

            Font font = EntityTypeSelectionList.this.font;
            boolean isFavorite = FavoritesManager.get().isFavorite(this.id);
            int maxTextWidth = rowWidth - 6;

            String truncatedName = font.plainSubstrByWidth(this.displayName, maxTextWidth);
            String truncatedId = font.plainSubstrByWidth(this.id.toString(), maxTextWidth);

            if (isFavorite) {
                String star = "\u2605 ";
                int starWidth = font.width(star);
                font.draw(poseStack, star, (float) (x + 3), (float) (y + 1), theme.favoriteStarColor());
                String remainingName = font.plainSubstrByWidth(this.displayName, maxTextWidth - starWidth);
                font.draw(poseStack, remainingName, (float) (x + 3 + starWidth), (float) (y + 1), nameColor);
            } else {
                font.draw(poseStack, truncatedName, (float) (x + 3), (float) (y + 1), nameColor);
            }
            font.draw(poseStack, truncatedId, (float) (x + 3), (float) (y + 12), idColor);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            EntityTypeSelectionList.this.setSelected(this);
            return true;
        }

        @Override
        public Component getNarration() {
            return new TextComponent(this.displayName);
        }
    }
}
