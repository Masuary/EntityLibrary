package com.masuary.entitylibrary.client;

import com.masuary.entitylibrary.EntityLibraryMod;
import com.masuary.entitylibrary.client.EntityLibraryClientData;
import com.masuary.entitylibrary.client.screen.EntityLibraryScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Client-only mod entrypoint. Registers the keybind and opens the Entity Library screen.
 */
@Mod(value = EntityLibraryMod.MODID, dist = Dist.CLIENT)
public final class EntityLibraryClient {

    public EntityLibraryClient(final IEventBus modEventBus, final ModContainer container) {
        // Load client UI state (favorites/recents/settings) early so the screen can use it immediately.
        EntityLibraryClientData.get();
        modEventBus.addListener(this::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.register(this);
    }

    private void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
        event.register(ClientKeyMappings.OPEN_LIBRARY);
    }

    @SubscribeEvent
    public void onClientTick(final ClientTickEvent.Post event) {

        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        while (ClientKeyMappings.OPEN_LIBRARY.consumeClick()) {
            mc.setScreen(new EntityLibraryScreen(mc.screen));
        }
    }
}
