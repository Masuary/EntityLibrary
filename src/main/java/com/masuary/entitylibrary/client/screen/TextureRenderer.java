package com.masuary.entitylibrary.client.screen;

import com.masuary.entitylibrary.client.data.ThemeManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.resources.ResourceLocation;

public final class TextureRenderer {
    private static final ResourceLocation DARK_TEXTURE = ResourceLocation.tryParse("entitylibrary:textures/gui/gui_dark.png");
    private static final ResourceLocation LIGHT_TEXTURE = ResourceLocation.tryParse("entitylibrary:textures/gui/gui_light.png");

    private static final int TEX_SIZE = 256;

    private static final int BG_U = 0;
    private static final int BG_V = 0;
    private static final int BG_SIZE = 64;

    private static final int PANEL_U = 0;
    private static final int PANEL_V = 64;
    private static final int PANEL_SIZE = 48;
    private static final int PANEL_BORDER = 4;
    private static final int PANEL_INNER = PANEL_SIZE - PANEL_BORDER * 2;

    private static final int SEP_U = 0;
    private static final int SEP_V = 114;
    private static final int SEP_H = 2;

    private TextureRenderer() {}

    private static void bindTexture() {
        ResourceLocation texture = ThemeManager.get().isDark() ? DARK_TEXTURE : LIGHT_TEXTURE;
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawTilingBackground(PoseStack poseStack, int x, int y, int width, int height) {
        bindTexture();
        RenderSystem.enableBlend();
        for (int ty = 0; ty < height; ty += BG_SIZE) {
            for (int tx = 0; tx < width; tx += BG_SIZE) {
                int drawW = Math.min(BG_SIZE, width - tx);
                int drawH = Math.min(BG_SIZE, height - ty);
                GuiComponent.blit(poseStack, x + tx, y + ty, BG_U, BG_V, drawW, drawH, TEX_SIZE, TEX_SIZE);
            }
        }
        RenderSystem.disableBlend();
    }

    public static void drawPanel(PoseStack poseStack, int x, int y, int width, int height) {
        bindTexture();
        RenderSystem.enableBlend();

        int b = PANEL_BORDER;
        int innerW = width - b * 2;
        int innerH = height - b * 2;

        // Corners
        GuiComponent.blit(poseStack, x, y, PANEL_U, PANEL_V, b, b, TEX_SIZE, TEX_SIZE);
        GuiComponent.blit(poseStack, x + width - b, y, PANEL_U + PANEL_SIZE - b, PANEL_V, b, b, TEX_SIZE, TEX_SIZE);
        GuiComponent.blit(poseStack, x, y + height - b, PANEL_U, PANEL_V + PANEL_SIZE - b, b, b, TEX_SIZE, TEX_SIZE);
        GuiComponent.blit(poseStack, x + width - b, y + height - b, PANEL_U + PANEL_SIZE - b, PANEL_V + PANEL_SIZE - b, b, b, TEX_SIZE, TEX_SIZE);

        // Top and bottom edges (tiled)
        for (int tx = 0; tx < innerW; tx += PANEL_INNER) {
            int drawW = Math.min(PANEL_INNER, innerW - tx);
            GuiComponent.blit(poseStack, x + b + tx, y, PANEL_U + b, PANEL_V, drawW, b, TEX_SIZE, TEX_SIZE);
            GuiComponent.blit(poseStack, x + b + tx, y + height - b, PANEL_U + b, PANEL_V + PANEL_SIZE - b, drawW, b, TEX_SIZE, TEX_SIZE);
        }

        // Left and right edges (tiled)
        for (int ty = 0; ty < innerH; ty += PANEL_INNER) {
            int drawH = Math.min(PANEL_INNER, innerH - ty);
            GuiComponent.blit(poseStack, x, y + b + ty, PANEL_U, PANEL_V + b, b, drawH, TEX_SIZE, TEX_SIZE);
            GuiComponent.blit(poseStack, x + width - b, y + b + ty, PANEL_U + PANEL_SIZE - b, PANEL_V + b, b, drawH, TEX_SIZE, TEX_SIZE);
        }

        // Center (tiled)
        for (int ty = 0; ty < innerH; ty += PANEL_INNER) {
            for (int tx = 0; tx < innerW; tx += PANEL_INNER) {
                int drawW = Math.min(PANEL_INNER, innerW - tx);
                int drawH = Math.min(PANEL_INNER, innerH - ty);
                GuiComponent.blit(poseStack, x + b + tx, y + b + ty, PANEL_U + b, PANEL_V + b, drawW, drawH, TEX_SIZE, TEX_SIZE);
            }
        }

        RenderSystem.disableBlend();
    }

    public static void drawSeparator(PoseStack poseStack, int x, int y, int width) {
        bindTexture();
        for (int tx = 0; tx < width; tx += TEX_SIZE) {
            int drawW = Math.min(TEX_SIZE, width - tx);
            GuiComponent.blit(poseStack, x + tx, y, SEP_U, SEP_V, drawW, SEP_H, TEX_SIZE, TEX_SIZE);
        }
    }
}
