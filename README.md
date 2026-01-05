# Entity Library (NeoForge 1.21.1)

Client-side utility mod that provides an in-game “library” screen to browse and 3D-preview **LivingEntity** types
that are summonable by ID (including modded entities), **without actually spawning them into the world**.

## Features
- Searchable list of registered **LivingEntity** IDs
- 3D preview panel (preview entity is created client-side but **not added** to the world)
- Shows a `/summon <id> ~ ~ ~` hint for the selected entry
- Mouse wheel over the preview area to change preview scale

## Controls
- Open the library: **O** (configurable in Minecraft Controls)

## Requirements
- Minecraft **1.21.1**
- NeoForge **21.1.217** (or newer for the 1.21.1 line)
- Java **21**

## Development / Build
From the project root:

```bash
./gradlew build
```

The built jar will be in:

```
build/libs/
```

To run a dev client:

```bash
./gradlew runClient
```

## Notes / Limitations
- This version intentionally hides non-living entity types (projectiles, boats, minecarts, etc.).
- Some modded entities may still fail to preview if they require server-only initialization or special spawn data.
  In that case the preview will show as unavailable rather than crashing the game.
