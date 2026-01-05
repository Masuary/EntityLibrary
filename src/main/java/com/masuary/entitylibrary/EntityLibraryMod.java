package com.masuary.entitylibrary;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Entity Library
 *
 * Client-side utility to browse and preview LivingEntity types without spawning them into the world.
 */
@Mod(EntityLibraryMod.MODID)
public final class EntityLibraryMod {
    public static final String MODID = "entitylibrary";
    public static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("unused")
    public EntityLibraryMod(final IEventBus modEventBus, final ModContainer container) {
        // Intentionally empty. Client setup is handled in the dist-specific mod entrypoint.
    }
}
