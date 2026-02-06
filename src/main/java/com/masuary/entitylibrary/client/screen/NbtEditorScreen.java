package com.masuary.entitylibrary.client.screen;

import com.masuary.entitylibrary.EntityLibraryMod;
import com.masuary.entitylibrary.client.data.Theme;
import com.masuary.entitylibrary.client.data.ThemeManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NbtEditorScreen extends Screen {
    private static final String TRANSLATION_PREFIX = "screen." + EntityLibraryMod.MODID + ".nbt.";

    private final EntityLibraryScreen parent;
    private final ResourceLocation entityId;
    private final String baseSummonCommand;

    private final Map<String, Boolean> booleanTags = new LinkedHashMap<>();
    private final Map<String, Button> tagButtons = new LinkedHashMap<>();
    private EditBox customNameInput;
    private String commandPreview = "";

    public NbtEditorScreen(EntityLibraryScreen parent, ResourceLocation entityId, String baseSummonCommand) {
        super(new TranslatableComponent(TRANSLATION_PREFIX + "title"));
        this.parent = parent;
        this.entityId = entityId;
        this.baseSummonCommand = baseSummonCommand;

        booleanTags.put("CustomNameVisible", false);
        booleanTags.put("NoGravity", false);
        booleanTags.put("Glowing", false);
        booleanTags.put("Silent", false);
        booleanTags.put("Invulnerable", false);
        booleanTags.put("NoAI", false);
        booleanTags.put("PersistenceRequired", false);
    }

    @Override
    protected void init() {
        tagButtons.clear();

        int centerX = this.width / 2;
        int startY = 40;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 24;

        int currentY = startY;

        for (Map.Entry<String, Boolean> entry : booleanTags.entrySet()) {
            String tagName = entry.getKey();
            boolean tagValue = entry.getValue();

            Button button = new Button(
                    centerX - buttonWidth / 2, currentY, buttonWidth, buttonHeight,
                    buildTagLabel(tagName, tagValue),
                    b -> {
                        boolean newValue = !booleanTags.get(tagName);
                        booleanTags.put(tagName, newValue);
                        b.setMessage(buildTagLabel(tagName, newValue));
                        rebuildCommandPreview();
                    }
            );
            this.addRenderableWidget(button);
            tagButtons.put(tagName, button);
            currentY += spacing;
        }

        currentY += 6;

        this.customNameInput = new EditBox(
                this.font,
                centerX - buttonWidth / 2, currentY, buttonWidth, buttonHeight,
                new TranslatableComponent(TRANSLATION_PREFIX + "custom_name")
        );
        this.customNameInput.setMaxLength(256);
        this.customNameInput.setResponder(s -> rebuildCommandPreview());
        this.addRenderableWidget(this.customNameInput);

        currentY += spacing + 10;

        int bottomButtonWidth = 80;
        int bottomButtonSpacing = 10;

        this.addRenderableWidget(new Button(
                centerX - bottomButtonWidth - bottomButtonSpacing / 2, this.height - 30,
                bottomButtonWidth, 20,
                new TranslatableComponent(TRANSLATION_PREFIX + "copy"),
                b -> {
                    if (!commandPreview.isEmpty()) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(commandPreview);
                    }
                }
        ));

        this.addRenderableWidget(new Button(
                centerX + bottomButtonSpacing / 2, this.height - 30,
                bottomButtonWidth, 20,
                new TranslatableComponent(TRANSLATION_PREFIX + "back"),
                b -> Minecraft.getInstance().setScreen(parent)
        ));

        rebuildCommandPreview();
    }

    private TextComponent buildTagLabel(String tagName, boolean value) {
        String translatedName = new TranslatableComponent(TRANSLATION_PREFIX + "tag." + tagName.toLowerCase()).getString();
        String stateText = value ? "\u00A7aON" : "\u00A7cOFF";
        return new TextComponent(translatedName + ": " + stateText);
    }

    private void rebuildCommandPreview() {
        List<String> nbtParts = new ArrayList<>();

        for (Map.Entry<String, Boolean> entry : booleanTags.entrySet()) {
            if (entry.getValue()) {
                nbtParts.add(entry.getKey() + ":1b");
            }
        }

        String customName = customNameInput != null ? customNameInput.getValue().trim() : "";
        if (!customName.isEmpty()) {
            String escapedName = customName.replace("\\", "\\\\").replace("\"", "\\\"");
            nbtParts.add("CustomName:'\"" + escapedName + "\"'");
        }

        if (nbtParts.isEmpty()) {
            commandPreview = "/summon " + entityId + " ~ ~ ~";
        } else {
            commandPreview = "/summon " + entityId + " ~ ~ ~ {" + String.join(",", nbtParts) + "}";
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        Theme theme = ThemeManager.get().currentTheme();

        TextureRenderer.drawTilingBackground(poseStack, 0, 0, this.width, this.height);

        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 14, theme.titleColor());

        super.render(poseStack, mouseX, mouseY, partialTicks);

        int previewY = this.height - 60;
        String previewLabel = new TranslatableComponent(TRANSLATION_PREFIX + "command_preview").getString();
        this.font.draw(poseStack, previewLabel, 10, previewY - 12, theme.secondaryText());

        int maxCommandWidth = this.width - 20;
        String truncatedCommand = this.font.plainSubstrByWidth(commandPreview, maxCommandWidth);
        this.font.draw(poseStack, truncatedCommand, 10, previewY, theme.commandText());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.customNameInput != null) {
            this.customNameInput.tick();
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
