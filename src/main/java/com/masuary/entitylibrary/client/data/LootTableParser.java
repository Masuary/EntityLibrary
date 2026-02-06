package com.masuary.entitylibrary.client.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LootTableParser {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new Gson();
    private static final Map<ResourceLocation, List<String>> CACHE = new HashMap<>();

    private LootTableParser() {}

    @Nullable
    public static List<String> getDrops(ResourceLocation entityId) {
        if (CACHE.containsKey(entityId)) {
            return CACHE.get(entityId);
        }

        List<String> result = parseDrops(entityId);
        CACHE.put(entityId, result);
        return result;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    @Nullable
    private static List<String> parseDrops(ResourceLocation entityId) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return null;
        }

        EntityType<?> entityType = ForgeRegistries.ENTITIES.getValue(entityId);
        if (entityType == null) {
            return List.of();
        }

        ResourceLocation lootTableId = entityType.getDefaultLootTable();
        if (lootTableId.equals(ResourceLocation.tryParse("minecraft:empty"))) {
            return List.of();
        }

        ResourceLocation jsonPath = ResourceLocation.tryParse(
                lootTableId.getNamespace() + ":loot_tables/" + lootTableId.getPath() + ".json"
        );

        try {
            ResourceManager resourceManager = server.getResourceManager();
            Resource resource = resourceManager.getResource(jsonPath);
            Set<String> itemNames = new LinkedHashSet<>();

            try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root != null && root.has("pools")) {
                    JsonArray pools = root.getAsJsonArray("pools");
                    for (JsonElement poolElement : pools) {
                        extractItemsFromPool(poolElement.getAsJsonObject(), itemNames);
                    }
                }
            }

            return new ArrayList<>(itemNames);
        } catch (Exception e) {
            LOGGER.debug("Could not read loot table for {}: {}", entityId, e.getMessage());
            return List.of();
        }
    }

    private static void extractItemsFromPool(JsonObject pool, Set<String> itemNames) {
        if (!pool.has("entries")) {
            return;
        }

        JsonArray entries = pool.getAsJsonArray("entries");
        for (JsonElement entryElement : entries) {
            extractItemsFromEntry(entryElement.getAsJsonObject(), itemNames);
        }
    }

    private static void extractItemsFromEntry(JsonObject entry, Set<String> itemNames) {
        String type = entry.has("type") ? entry.get("type").getAsString() : "";

        if ("minecraft:item".equals(type)) {
            if (entry.has("name")) {
                String itemId = entry.get("name").getAsString();
                String displayName = getItemDisplayName(itemId);
                if (displayName != null) {
                    itemNames.add(displayName);
                }
            }
        } else if ("minecraft:loot_table".equals(type)) {
            if (entry.has("name")) {
                String referencedTable = entry.get("name").getAsString();
                ResourceLocation refId = ResourceLocation.tryParse(referencedTable);
                if (refId != null) {
                    List<String> nested = getDrops(refId);
                    if (nested != null) {
                        itemNames.addAll(nested);
                    }
                }
            }
        } else if ("minecraft:alternatives".equals(type) || "minecraft:sequence".equals(type) || "minecraft:group".equals(type)) {
            if (entry.has("children")) {
                JsonArray children = entry.getAsJsonArray("children");
                for (JsonElement child : children) {
                    extractItemsFromEntry(child.getAsJsonObject(), itemNames);
                }
            }
        }
    }

    @Nullable
    private static String getItemDisplayName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return null;
        }

        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            return id.getPath();
        }

        return item.getDescription().getString();
    }
}
