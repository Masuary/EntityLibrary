package com.masuary.entitylibrary;

import net.minecraftforge.fml.common.Mod;

/**
 * Client-side utility mod: provides a searchable library screen to preview entities without spawning them.
 */
@Mod(EntityLibraryMod.MODID)
public class EntityLibraryMod {
    public static final String MODID = "entitylibrary";

    public EntityLibraryMod() {
        // Intentionally empty. All behaviour is wired through client-only event subscribers.
    }
}
