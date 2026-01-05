# Entity Library (Forge 1.18.2)

**Entity Library** is a **client-side only** Forge mod for **Minecraft 1.18.2 (Forge 40.3.11)** that provides a “library” UI to **browse and preview all summonable living entities** (i.e., entities usable with `/summon`) **without actually spawning them into the world**.

This is intended for pack devs, admins, and players who want a quick way to preview mobs/monsters from vanilla and mods.

---

## Features

- **Library UI** listing all registered entity IDs (including modded)
- **LivingEntity-only list** (non-living entity types are hidden)
- **Search** by entity ID (substring match)
- **3D preview** of the selected entity (no world spawn; not added to the level)
- Shows a copy-friendly summon hint: `/summon <id> ~ ~ ~`
- **Scale control**: mouse wheel over the preview to adjust size

---

## Usage

### Open the library
- Default keybind: **O**
- You can change it in: **Options → Controls → Key Binds → Entity Library**

### Interact
- Use the **search box** to filter the entity list.
- Click an entry to preview it.
- Use the **mouse wheel** while hovering the preview area to change the preview scale.

---

## Important notes / limitations

- The list is filtered by attempting to instantiate each `EntityType` client-side and keeping only those that produce a `LivingEntity`.
  - Some modded “living” entities may be omitted if they **cannot be constructed safely on a `ClientLevel`** without server context or spawn data.
- Preview entities are created only for rendering and are **never added to the world** (no ticking, no server-side spawn).
- This mod is designed to be **client-only**. A server does not need it installed.

---

## Installation (players)

1. Install **Minecraft Forge 1.18.2 (40.3.11)**.
2. Put the built jar into your `mods/` folder.
3. Launch the game and press **O**.

---

## Build from source (developers)

### Requirements
- Java **17**
- Gradle Wrapper (included)

### Build
From the project root:
- Windows: `gradlew.bat build`
- Linux/macOS: `./gradlew build`

The jar will be in:
- `build/libs/`

### Run in dev
- Windows: `gradlew.bat runClient`
- Linux/macOS: `./gradlew runClient`

---

## Technical overview (how it avoids real spawning)

- Enumerates `ForgeRegistries.ENTITIES`
- For list filtering and preview, calls `EntityType#create(Minecraft.getInstance().level)` **but does not add** the entity to the world.
- Renders the detached entity in a GUI using the client entity renderer.

---

## Compatibility

- Minecraft **1.18.2**
- Forge **40.3.11**
- Works with modpacks; lists entities from all loaded mods.

---

## License

See `LICENSE.txt`.
