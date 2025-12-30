# Cobblemon Custom Merchants Datapack

This is an example datapack for the Cobblemon Custom Merchants mod. Use this to customize merchant trades or add new merchants!

## Installation

1. Copy the entire `cobblemon_custom_merchants_datapack` folder to your world's `datapacks` folder:
   ```
   saves/[your_world_name]/datapacks/cobblemon_custom_merchants_datapack/
   ```

2. Run `/reload` in-game to reload datapacks

3. Spawn merchants using:
   ```
   /spawnmerchant cobblemoncustommerchants:merchant_name
   ```

## Available Merchants

### Regular Merchants (Static Trades)
- `basic` - Basic Merchant (Dausky_'s skin)
- `advanced` - Advanced Merchant (Fit_'s skin)
- `gambler` - Gambler (aracekarr's skin)
- `treasure_hunter` - Treasure Hunter (MHF_Steve skin) - **NEW!**

### Special Merchants (Dynamic Trades)
- `black_market` - Black Market (imanant's skin)
  - Trades are dynamically generated from Cobblemon drop tables
  - Each player gets unique offers that rotate over time
  - Use `/refreshblackmarket` to force new offers for all players

## Creating Custom Merchants

### File Structure
```
data/cobblemoncustommerchants/merchants/your_merchant.json
```

### Basic Format
```json
{
  "display_name": "Your Merchant Name",
  "player_skin_name": "PlayerName",
  "trades": [
    {
      "input": {
        "id": "minecraft:diamond",
        "count": 1
      },
      "output": {
        "id": "cobblemon:relic_coin",
        "count": 10
      },
      "max_uses": 2147483647
    }
  ]
}
```

### Trade Options

#### Required Fields
- `input` - First cost item
  - `id` - Item ID or tag (e.g., `"minecraft:diamond"` or `"#minecraft:music_discs"`)
  - `count` - Amount required
- `output` - Item merchant gives
  - `id` - Item ID
  - `count` - Amount given

#### Optional Fields
- `second_input` - Second cost item (for two-item trades)
- `max_uses` - Max times this trade can be used (default: unlimited = 2147483647)
- `villager_xp` - XP gained per trade (default: 0)
- `price_multiplier` - Price variation multiplier (default: 0.0)

### Using Item Tags

You can use Minecraft or mod item tags to accept multiple items in one trade slot:

#### Vanilla Minecraft Tags
- `#minecraft:music_discs` - Any music disc
- `#minecraft:decorated_pot_sherds` - Any pottery sherd
- `#minecraft:goat_horn_instruments` - Any goat horn

#### Cobblemon Tags
- `#cobblemon:fossils` - Any Cobblemon fossil
- `#cobblemon:poke_balls` - Any Pokéball
- `#cobblemon:berries` - Any berry
- `#cobblemon:evolution_stones` - Any evolution stone

Find more tags in Cobblemon's data folder: `data/cobblemon/tags/item/`

### Advanced Example
```json
{
  "display_name": "Master Trader",
  "player_skin_name": "Notch",
  "trades": [
    {
      "input": {
        "id": "minecraft:diamond",
        "count": 3
      },
      "second_input": {
        "id": "cobblemon:relic_coin",
        "count": 5
      },
      "output": {
        "id": "cobblemon:master_ball",
        "count": 1
      },
      "max_uses": 5,
      "villager_xp": 50
    },
    {
      "input": {
        "id": "#cobblemon:fossils",
        "count": 1
      },
      "output": {
        "id": "cobblemon:relic_coin",
        "count": 30
      },
      "max_uses": 2147483647
    }
  ]
}
```

## Commands

- `/spawnmerchant <merchant_type>` - Spawn a merchant
- `/spawnmerchant <merchant_type> <player_skin>` - Spawn with custom skin
- `/refreshblackmarket` - Force refresh all Black Market inventories

## Tools

- **Merchant Debug Stick** - `/give @s cobblemoncustommerchants:merchant_debug_stick`
  - Right-click merchant: Pick up and store
  - Right-click block: Place stored merchant
  - Sneak + Left-click merchant: Remove merchant

## Example Merchants

### Basic Merchant
Sells basic items (Pokéballs, Potions, Revives) for 1-2 Relic Coins each.

### Advanced Merchant
Sells upgraded items (Great Balls, Ultra Balls, Super/Hyper/Max Potions) for 3-10 Relic Coins.

### Gambler
Sells rare items (Rare Candy, Master Ball, Ability Capsule) for 25-150 Relic Coins with limited stock.

### Treasure Hunter (NEW!)
**Buys** (outputs Relic Coins) miscellaneous treasure items:
- Wishing Star: 25 RC
- Pottery Sherds: 3 RC each
- Music Discs: 10 RC each
- Goat Horns: 5 RC each
- Relic Coin Pouch: 5 RC (break down into coins)
- Relic Coin Stack: 12 RC (break down into coins)
- Any Fossil: 30 RC each

### Black Market
Dynamically generates trades based on Cobblemon Pokémon drop tables. Each player gets unique rotating offers that change over time.

## Notes

- Unlimited trades use `"max_uses": 2147483647`
- Item tags start with `#` (e.g., `"#minecraft:music_discs"`)
- Player skin names can be any valid Minecraft username
- The mod automatically loads all JSON files from `data/cobblemoncustommerchants/merchants/`
- Use `/reload` after making changes to merchant configs
