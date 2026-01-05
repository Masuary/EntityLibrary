package com.masuary.entitylibrary.client;

import com.masuary.entitylibrary.EntityLibraryMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.ClientRegistry;
import org.lwjgl.glfw.GLFW;

public final class ClientKeybinds {
    private ClientKeybinds() {}

    public static final KeyMapping OPEN_LIBRARY = new KeyMapping(
            "key." + EntityLibraryMod.MODID + ".open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.misc"
    );

    @Mod.EventBusSubscriber(modid = EntityLibraryMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Forge 1.18.2 does not have RegisterKeyMappingsEvent; register during client setup.
            event.enqueueWork(() -> ClientRegistry.registerKeyBinding(OPEN_LIBRARY));
        }
    }
}
