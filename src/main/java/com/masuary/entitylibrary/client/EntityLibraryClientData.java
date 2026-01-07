package com.masuary.entitylibrary.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lightweight client-side persistence for Entity Library UI state.
 *
 * Stored at: config/entitylibrary-client.json
 */
public final class EntityLibraryClientData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "entitylibrary-client.json";
    private static final int MAX_RECENTS = 64;

    private static EntityLibraryClientData INSTANCE;

    // Persisted fields (Gson serializes these)
    public boolean showIdsInline = true;
    public String viewMode = "ALL";      // ALL | FAV | REC
    public String sortMode = "NAME";     // NAME | ID
    public String namespaceFilter = "ALL"; // ALL or a namespace string

    public final Set<String> favorites = new LinkedHashSet<>();
    public final List<String> recents = new ArrayList<>();

    public static EntityLibraryClientData get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        final Path path = getPath();
        if (!Files.exists(path)) {
            INSTANCE = new EntityLibraryClientData();
            return;
        }

        try {
            final String json = Files.readString(path, StandardCharsets.UTF_8);
            final EntityLibraryClientData parsed = GSON.fromJson(json, EntityLibraryClientData.class);
            INSTANCE = parsed == null ? new EntityLibraryClientData() : parsed;
            INSTANCE.sanitize();
        } catch (final IOException | JsonSyntaxException e) {
            INSTANCE = new EntityLibraryClientData();
        }
    }

    public void save() {
        final Path path = getPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (final IOException ignored) {
        }
    }

    public boolean isFavorite(final ResourceLocation id) {
        return id != null && favorites.contains(id.toString());
    }

    public void toggleFavorite(final ResourceLocation id) {
        if (id == null) return;
        final String s = id.toString();
        if (favorites.contains(s)) {
            favorites.remove(s);
        } else {
            favorites.add(s);
        }
        save();
    }

    public void touchRecent(final ResourceLocation id) {
        if (id == null) return;
        final String s = id.toString();
        recents.remove(s);
        recents.add(0, s);

        if (recents.size() > MAX_RECENTS) {
            recents.subList(MAX_RECENTS, recents.size()).clear();
        }
        save();
    }

    private void sanitize() {
        if (viewMode == null || (!viewMode.equals("ALL") && !viewMode.equals("FAV") && !viewMode.equals("REC"))) {
            viewMode = "ALL";
        }
        if (sortMode == null || (!sortMode.equals("NAME") && !sortMode.equals("ID"))) {
            sortMode = "NAME";
        }
        if (namespaceFilter == null || namespaceFilter.isBlank()) {
            namespaceFilter = "ALL";
        }

        // Ensure recents list doesn't grow without bound if edited externally.
        if (recents.size() > MAX_RECENTS) {
            recents.subList(MAX_RECENTS, recents.size()).clear();
        }
    }

    private static Path getPath() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private EntityLibraryClientData() {
    }
}
