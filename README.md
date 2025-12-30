# Cobblemon Custom Merchants

A barebones NeoForge mod for Minecraft 1.21.1 that adds a custom merchant system for Cobblemon with dynamic pricing and rotating inventories.

## Features

- **Custom Merchant Entity**: Immobile, invincible villager-like NPCs with custom trading
- **Black Market System**: Dynamic rotating inventory with:
  - Per-player unique offers
  - Rarity-based pricing calculated from Cobblemon drop rates
  - Time-based inventory rotation (12 hours default)
  - Trade usage limits (3 uses per item per rotation)
  - Persistent saved data across server restarts
- **Merchant Debug Stick**: Tool for spawning, picking up, and managing merchants

## Core Components Ported from cobblemonpokeboxes

### Entity System
- `CustomMerchantEntity` - The merchant entity with support for REGULAR and BLACK_MARKET types
- `ModEntities` - Entity registration

### Item System
- `MerchantDebugStick` - Debug tool for merchant management
- `ModItems` - Item registration

### Black Market Trading Logic
- `CobblemonDropRegistry` - Analyzes Cobblemon species drops via reflection
- `CobblemonDropData` - Stores drop rate and rarity data
- `DropValueCalculator` - Calculates prices based on:
  - Rarity (inverse of drop rate)
  - Exclusivity (Cobblemon-exclusive items get bonus)
  - Availability (number of Pokemon that drop it)
  - Craftability (non-craftable items get bonus)
  - Per-player price variability (-40% to +20%)
- `BlackMarketConfig` - Configuration constants and rotation timing
- `BlackMarketInventory` - Per-player inventory persistence and offer generation

### Client Rendering
- `ModEntityRenderers` - Entity renderer registration
- `CustomMerchantRenderer` - Villager-style renderer

## What Was NOT Ported (GUI-related)

The following GUI-related files were intentionally excluded to keep this mod barebones:
- Custom menu screens (BlackMarketMenu, BlackMarketScreen)
- HUD overlays (BlackMarketHudOverlay)
- Custom menu types
- Trader registry and trader commands
- Pokebox items and blocks
- Config system UI

The mod uses **vanilla merchant menus** for simplicity.

## Package Structure

```
net.fit.cobblemonmerchants
├── CobblemonMerchants (Main mod class)
├── item
│   ├── ModItems
│   └── custom
│       └── MerchantDebugStick
└── merchant
    ├── CustomMerchantEntity
    ├── ModEntities
    ├── blackmarket
    │   ├── BlackMarketConfig
    │   ├── BlackMarketInventory
    │   ├── CobblemonDropData
    │   ├── CobblemonDropRegistry
    │   └── DropValueCalculator
    └── client
        ├── CustomMerchantRenderer
        └── ModEntityRenderers
```

## Dependencies

- **Minecraft**: 1.21.1
- **NeoForge**: 21.0.167
- **Cobblemon**: 1.7.x (optional - mod will use fallback data if not present)

## Building

```bash
./gradlew build
```

Output JAR will be in `build/libs/cobblemonmerchants-1.0.0.jar`

## Usage

1. Install Cobblemon (recommended but optional)
2. Place the mod JAR in your `mods/` folder
3. Use `/give @s cobblemonmerchants:merchant_debug_stick` to get the debug tool
4. Right-click a block to spawn a Black Market merchant
5. Trade items for relic coins!

## Trade Mechanics

### Black Market
- Players **give items** → **receive relic coins**
- Inventory rotates every 12 hours (configurable in BlackMarketConfig)
- Each player gets unique offers based on their UUID
- 3 trades per item per rotation
- Prices calculated from Cobblemon drop data:
  - Rarer items = more coins
  - Cobblemon-exclusive items = 1.5x multiplier
  - Non-craftable items = 1.2x multiplier
  - Divided by number of Pokemon that drop it

## License

MIT