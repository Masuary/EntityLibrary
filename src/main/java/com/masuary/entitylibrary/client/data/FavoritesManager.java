package com.masuary.entitylibrary.client.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class FavoritesManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final FavoritesManager INSTANCE = new FavoritesManager();

    private final Set<ResourceLocation> favorites = new HashSet<>();

    private FavoritesManager() {}

    public static FavoritesManager get() {
        return INSTANCE;
    }

    public boolean isFavorite(ResourceLocation id) {
        return favorites.contains(id);
    }

    public void toggle(ResourceLocation id) {
        if (favorites.contains(id)) {
            favorites.remove(id);
        } else {
            favorites.add(id);
        }
        save();
    }

    public Set<ResourceLocation> favorites() {
        return Set.copyOf(favorites);
    }

    public void load() {
        Path configFile = getConfigPath();
        if (!Files.exists(configFile)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(configFile)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null && json.has("favorites")) {
                favorites.clear();
                JsonArray array = json.getAsJsonArray("favorites");
                for (var element : array) {
                    ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
                    if (id != null) {
                        favorites.add(id);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load favorites config", e);
        }
    }

    private void save() {
        Path configFile = getConfigPath();
        try {
            Files.createDirectories(configFile.getParent());
            JsonObject json = new JsonObject();
            JsonArray array = new JsonArray();
            for (ResourceLocation id : favorites) {
                array.add(id.toString());
            }
            json.add("favorites", array);
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to save favorites config", e);
        }
    }

    private static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve("entitylibrary").resolve("favorites.json");
    }
}
