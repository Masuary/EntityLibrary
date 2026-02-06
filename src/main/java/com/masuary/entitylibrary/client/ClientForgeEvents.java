package com.masuary.entitylibrary.client;

import com.masuary.entitylibrary.EntityLibraryMod;
import com.masuary.entitylibrary.client.screen.EntityLibraryScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EntityLibraryMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientForgeEvents {
    private ClientForgeEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            return;
        }

        while (ClientKeybinds.OPEN_LIBRARY.consumeClick()) {
            mc.setScreen(new EntityLibraryScreen(mc.screen));
        }
    }
}
