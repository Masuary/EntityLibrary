package com.masuary.entitylibrary.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Client key mappings. */
public final class ClientKeyMappings {
    private ClientKeyMappings() {}

    /** Default keybind: O */
    public static final KeyMapping OPEN_LIBRARY = new KeyMapping(
            "key.entitylibrary.open",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.misc"
    );
}
