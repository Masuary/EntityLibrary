package com.masuary.entitylibrary.client.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ThemeManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ThemeManager INSTANCE = new ThemeManager();

    private Theme currentTheme = Theme.DARK;

    private ThemeManager() {}

    public static ThemeManager get() {
        return INSTANCE;
    }

    public Theme currentTheme() {
        return currentTheme;
    }

    public boolean isDark() {
        return currentTheme == Theme.DARK;
    }

    public void toggle() {
        currentTheme = isDark() ? Theme.LIGHT : Theme.DARK;
        save();
    }

    public void load() {
        Path configFile = getConfigPath();
        if (!Files.exists(configFile)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(configFile)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null && json.has("theme")) {
                String themeValue = json.get("theme").getAsString();
                currentTheme = "light".equalsIgnoreCase(themeValue) ? Theme.LIGHT : Theme.DARK;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load theme config", e);
        }
    }

    private void save() {
        Path configFile = getConfigPath();
        try {
            Files.createDirectories(configFile.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("theme", isDark() ? "dark" : "light");
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to save theme config", e);
        }
    }

    private static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve("entitylibrary").resolve("theme.json");
    }
}
