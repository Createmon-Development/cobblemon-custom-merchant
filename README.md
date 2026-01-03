# Credits:
Sprite work for the relic coin bag done by [Sharcys]([gaywhiteboy.com](https://gaywhiteboy.com))

# Cobblemon Custom Merchants

A NeoForge mod for Minecraft 1.21.1 that adds a customizable merchant system for Cobblemon with datapack-driven trades and dynamic Black Market pricing.

## Features

- **Datapack-Driven Merchants**: Configure custom merchants via JSON files
- **Tag-Based Trading**: Support for item tags (e.g., all music discs, pottery sherds, fossils)
- **Custom Display Names**: Override item names in trade displays
- **Flexible Positioning**: Place trades at specific slots in the GUI
- **Black Market System**: Dynamic rotating inventory with rarity-based pricing
- **Villager Appearances**: Merchants render as villagers with customizable biomes and professions

## Commands

### `/spawnmerchant`

Spawn a custom merchant at your location.

**Usage:**
```
/spawnmerchant <merchant_type> [villager_biome] [villager_profession]
```

**Parameters:**
- `merchant_type` (required): Resource location of the merchant config (e.g., `cobblemoncustommerchants:basic`)
- `villager_biome` (optional): Biome type for villager appearance (e.g., `minecraft:plains`, `minecraft:desert`)
- `villager_profession` (optional): Profession for villager appearance (e.g., `minecraft:farmer`, `cobblemon:nurse`)

**Examples:**
```
/spawnmerchant cobblemoncustommerchants:basic
/spawnmerchant cobblemoncustommerchants:treasure_hunter minecraft:plains minecraft:cartographer
/spawnmerchant cobblemoncustommerchants:basic minecraft:desert cobblemon:nurse
```

## Merchant Configuration

Merchants are configured using JSON files placed in `data/<namespace>/merchants/<merchant_id>.json`.

### Basic Structure

```json
{
  "display_name": "Merchant Name",
  "villager_biome": "minecraft:plains",
  "villager_profession": "minecraft:farmer",
  "trades": [
    {
      "input": {
        "id": "cobblemon:relic_coin",
        "count": 5
      },
      "output": {
        "id": "minecraft:diamond",
        "count": 1
      }
    }
  ]
}
```

### Merchant Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `display_name` | String | **Yes** | Name displayed above the merchant |
| `villager_biome` | String | No | Biome type for villager appearance (default: `minecraft:plains`) |
| `villager_profession` | String | No | Profession for villager appearance (default: `minecraft:none`) |
| `trades` | Array | **Yes** | List of trade entries |

### Trade Entry Fields

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `input` | ItemRequirement | **Yes** | - | What the player gives (first slot) |
| `second_input` | ItemRequirement | No | - | Optional second input item |
| `output` | ItemStack | **Yes** | - | What the player receives |
| `max_uses` | Integer | No | Unlimited | Maximum number of times this trade can be used (Integer.MAX_VALUE = unlimited) |
| `villager_xp` | Integer | No | 0 | Experience given to villagers (unused for custom merchants) |
| `price_multiplier` | Float | No | 0.0 | Price adjustment multiplier (unused for custom merchants) |
| `trade_display_name` | String | No | Auto-generated | Override the trade title in tooltips |
| `position` | Integer | No | Auto-fill | Specific slot position (0-26 for 3x9 grid) |

### ItemRequirement Format

ItemRequirement can be either an exact item or a tag:

#### Exact Item
```json
{
  "id": "minecraft:diamond",
  "count": 5,
  "display_name": "Shiny Rock",
  "ignore_components": false
}
```

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `id` | String | **Yes** | - | Item resource location |
| `count` | Integer | **Yes** | - | Number of items required |
| `display_name` | String | No | Item name | Override the item's display name |
| `ignore_components` | Boolean | No | false | If true, matches item type only (useful for goat horns, potions, etc.) |

#### Item Tag
```json
{
  "tag": "minecraft:music_discs",
  "count": 1,
  "display_name": "Music Disc"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `tag` | String | **Yes** | Item tag resource location |
| `count` | Integer | **Yes** | Number of items required |
| `display_name` | String | No | Override the tag's display name |

### Example Configurations

#### Basic Merchant
```json
{
  "display_name": "Basic Merchant",
  "villager_biome": "minecraft:plains",
  "villager_profession": "cobblemon:nurse",
  "trades": [
    {
      "input": {
        "id": "cobblemon:relic_coin",
        "count": 1
      },
      "output": {
        "id": "cobblemon:poke_ball",
        "count": 5
      }
    },
    {
      "input": {
        "id": "cobblemon:relic_coin",
        "count": 3
      },
      "output": {
        "id": "cobblemon:potion",
        "count": 1
      }
    }
  ]
}
```

#### Tag-Based Trading (Treasure Hunter)
```json
{
  "display_name": "Treasure Hunter",
  "villager_biome": "minecraft:plains",
  "villager_profession": "minecraft:cartographer",
  "trades": [
    {
      "input": {
        "tag": "minecraft:decorated_pot_sherds",
        "count": 1,
        "display_name": "Pottery Sherd"
      },
      "output": {
        "id": "cobblemon:relic_coin",
        "count": 3
      }
    },
    {
      "input": {
        "id": "minecraft:goat_horn",
        "count": 1,
        "display_name": "Goat Horn",
        "ignore_components": true
      },
      "output": {
        "id": "cobblemon:relic_coin",
        "count": 4
      }
    },
    {
      "input": {
        "tag": "cobblemon:fossils",
        "count": 1,
        "display_name": "Fossil"
      },
      "output": {
        "id": "cobblemon:relic_coin",
        "count": 8
      }
    }
  ]
}
```

#### Positioned Trades
```json
{
  "display_name": "Organized Merchant",
  "villager_profession": "minecraft:librarian",
  "trades": [
    {
      "input": {
        "id": "minecraft:emerald",
        "count": 1
      },
      "output": {
        "id": "minecraft:book",
        "count": 1
      },
      "position": 4
    },
    {
      "input": {
        "id": "minecraft:diamond",
        "count": 1
      },
      "output": {
        "id": "minecraft:enchanted_book",
        "count": 1
      },
      "position": 13
    }
  ]
}
```

## Trade Display Names

Trade titles are auto-generated based on relic coins:

- **Buy**: When trading relic coins for items (e.g., "Buy Poke Ball")
- **Sell**: When trading items for relic coins (e.g., "Sell Goat Horn")
- **Trade**: When neither or both are relic coins (e.g., "Trade Pottery Sherd")

You can override this with the `trade_display_name` field:
```json
{
  "input": {
    "id": "minecraft:diamond",
    "count": 64
  },
  "output": {
    "id": "minecraft:netherite_ingot",
    "count": 1
  },
  "trade_display_name": "Expensive Upgrade"
}
```

## Position System

The `position` field allows you to place trades at specific slots in the 3x9 grid (27 total slots).

**Position numbering** (0-26):
```
Row 1:  0  1  2  3  4  5  6  7  8
Row 2:  9 10 11 12 13 14 15 16 17
Row 3: 18 19 20 21 22 23 24 25 26
```

**Behavior:**
- Trades with explicit `position` values are placed first
- Trades without `position` fill remaining slots left-to-right, top-to-bottom
- If a position is out of bounds (< 0 or >= 27), it's ignored

## Reloading Merchants

To reload merchant configurations without restarting the server:

```
/reload
```

This reloads all datapacks, including merchant JSON files. Changes will take effect for newly spawned merchants. Existing merchants will keep their old configuration until despawned and respawned.

## Useful Item Tags

### Minecraft 1.21 Tags

| Tag | Description | Example Items |
|-----|-------------|---------------|
| `minecraft:decorated_pot_sherds` | Pottery sherds | Angler Sherd, Archer Sherd, etc. |
| `minecraft:creeper_drop_music_discs` | Music discs dropped by creepers | 13, Cat, Blocks, etc. |
| `minecraft:logs` | All log types | Oak Log, Birch Log, etc. |
| `minecraft:planks` | All plank types | Oak Planks, etc. |

**Note:** The `minecraft:music_discs` tag was removed in 1.21. Use `minecraft:creeper_drop_music_discs` instead.

### Cobblemon Tags

| Tag | Description |
|-----|-------------|
| `cobblemon:fossils` | All Cobblemon fossils (15 types) |
| `cobblemon:poke_balls` | All Poke Ball types |
| `cobblemon:evolution_stones` | All evolution stones |
| `cobblemon:berries` | All berries |
| `cobblemon:mints` | All nature mints |

Full list available at: `cobblemon/common/src/main/resources/data/cobblemon/tags/item/`

## Ignore Components

The `ignore_components` field is useful for items with data components that shouldn't affect matching:

```json
{
  "input": {
    "id": "minecraft:goat_horn",
    "count": 1,
    "display_name": "Goat Horn",
    "ignore_components": true
  },
  "output": {
    "id": "cobblemon:relic_coin",
    "count": 4
  }
}
```

**Use cases:**
- **Goat Horns**: Each variant (Ponder, Sing, etc.) has different `instrument` components
- **Potions**: Different potion effects have different components
- **Enchanted Books**: Books with different enchantments
- **Any item** where you want to accept all variants regardless of NBT/components

## Black Market System

The Black Market merchant type has special behavior with dynamic pricing:

```json
{
  "display_name": "Black Market",
  "villager_biome": "minecraft:swamp",
  "villager_profession": "minecraft:nitwit",
  "trades": []
}
```

**Features:**
- Trades are generated dynamically based on Cobblemon drop data
- Inventory rotates every 12 hours
- Per-player unique offers based on UUID
- Prices calculated from rarity, exclusivity, and craftability
- Leave `trades` array empty for automatic generation

## Dependencies

- **Minecraft**: 1.21.1
- **NeoForge**: 21.0.167+
- **Cobblemon**: 1.7.x (optional - mod will use fallback data if not present)

## Building

```bash
./gradlew build
```

Output JAR will be in `build/libs/cobblemoncustommerchants-1.0.0.jar`

## Installation

1. Install Cobblemon (recommended but optional)
2. Place the mod JAR in your `mods/` folder
3. Use `/spawnmerchant` command to spawn merchants

## Troubleshooting

### Broken Trades (Barrier Block)

If a trade shows a barrier block, it means the trade failed to load:

**Common causes:**
- Invalid item ID
- Empty or removed item tag
- Invalid tag resource location

**Solution:** Check server logs and fix the merchant JSON file, then `/reload`

### Merchant Not Spawning

- Check console for errors
- Verify the merchant JSON file exists at the correct path
- Ensure the resource location in the command is correct
- Confirm you have OP permissions (level 2)

### Changes Not Appearing

- Use `/reload` to reload datapacks
- Despawn old merchants and spawn new ones
- Check that the JSON file is in the correct datapack directory

## License

MIT
