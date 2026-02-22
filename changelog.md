**Entity Library v0.2.0**

### New Features
- **Favorites System** - Star your favorite entities and filter to show only favorites. Persists across sessions.
- **NBT Editor** - New screen to customize entity NBT tags (NoAI, Glowing, Invulnerable, Silent, NoGravity, PersistenceRequired, CustomNameVisible) with a custom name field. Generates a live `/summon` command preview with copy support.
- **Loot Table / Drops Display** - Shows what items an entity drops, parsed from loot tables (singleplayer only).
- **Dark / Light Theme** - Toggle between dark and light themes. Choice is saved to config.
- **Custom GUI Textures** - Replaced plain backgrounds with textured 9-patch panels and tiling backgrounds for both themes.
- **Animation Toggle** - Preview entities with their walk/idle animation enabled or disabled.
- **Entity Filters** - Filter by category: All, Living, Hostile, Passive, or Favorites.
- **Namespace/Mod Filter** - Cycle through registered namespaces to show entities from a specific mod.

### Improvements
- **Detail Panel** - Right-side panel now shows: display name, registry ID, max health, dimensions, mob category, `/summon` command with copy button.
- **Interactive Preview** - Drag to rotate, scroll to zoom, auto-scale based on entity size, reset button.
- **Entity Count** - Shows filtered/total entity count above the list (e.g., "52 / 137").
- **Improved Search** - Now matches against both registry ID and localized display name.
- **List Entries** - Now show both display name and registry ID per entry, with a gold star for favorites.

### Cleanup
- Removed MDK boilerplate files (CREDITS.txt, LICENSE.txt, changelog.txt).
- Cleaned up build.gradle and gradle.properties.
