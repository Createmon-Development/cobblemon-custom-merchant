# Black Market Item Analysis

Generated from Cobblemon Drop Registry logs on 2026-01-01

## Summary Statistics

- **Total Items**: 231 (note: 240 were logged, 9 may have parsing issues)
- **Minecraft Items**: 105
- **Cobblemon-Exclusive Items**: 126
- **Base Value Range**: 0.02 - 39.00 relic coins
- **Average Base Value**: 5.32 relic coins
- **Items with Custom Gameplay Modifiers**: 3

## Data Files

- **CSV Spreadsheet**: `black_market_items.csv`
- **Parser Script**: `parse_registry_log.py`

## CSV Columns

| Column | Description |
|--------|-------------|
| `item_id` | Minecraft/Cobblemon item resource location |
| `avg_drop_chance` | Average drop chance percentage across all Pokemon |
| `avg_drop_quantity` | Average quantity when dropped |
| `pokemon_count` | Number of different Pokemon that drop this item |
| `cobblemon_exclusive` | Whether item is Cobblemon-only (true/false) |
| `craftable` | Whether item can be crafted (true/false) |
| `growable` | Whether item can be grown/farmed (true/false) |
| `rarity_score` | Calculated rarity (100 / drop_chance, with quantity penalty) |
| `exclusivity_mult` | Multiplier for Cobblemon-exclusive items (1.5x or 1.0x) |
| `craftability_mult` | Multiplier for non-craftable items (1.3x or 1.0x) |
| `availability` | Divisor based on Pokemon count (lower = rarer) |
| `gameplay_modifier` | Custom balance modifier for specific items |
| `has_custom_modifier` | Whether item has non-default gameplay modifier |
| `global_mult` | Server-wide price multiplier (currently 1.0x) |
| `base_value` | Calculated base value in relic coins (before player variance) |
| `price_min` | Minimum price with -40% player variance |
| `price_max` | Maximum price with +20% player variance |
| `dropped_by` | List of Pokemon that drop this item |

## Value Formula

```
Base Value = (Rarity Score × Exclusivity × Craftability) / Availability × Gameplay Modifier × Global Multiplier

Where:
  Rarity Score = 100 / Avg Drop Chance
  If 100% drop: Rarity Score × (1 / Avg Quantity)
  Exclusivity = 1.5 if Cobblemon-exclusive, else 1.0
  Craftability = 1.3 if not craftable AND not growable, else 1.0
  Availability = max(1.0, Pokemon Count)
```

## Current Gameplay Modifiers

From DropValueCalculator.java:

| Item | Modifier | Reason |
|------|----------|--------|
| minecraft:carrot | 0.3× | Too common/farmable |
| minecraft:potato | 0.3× | Too common/farmable |
| minecraft:poisonous_potato | 0.1× | Nearly worthless |
| minecraft:ender_pearl | 1.4× | High gameplay value |
| minecraft:gold_ingot | 1.2× | High gameplay value |
| cobblemon:rare_candy | 2.0× | Very high gameplay value |
| cobblemon:exp_share | 1.5× | High gameplay value |

## High Value Items (Base Value > 10 coins)

Items worth focusing on for balancing:

1. **Cake**: 10.40 coins (4.17% drop, 3 Pokemon)
2. **Diamond**: 13.00 coins (5.00% drop, 2 Pokemon)
3. **High-tier Cobblemon items**: (likely Evolution Stones, Rare Candies, etc.)

## Low Value Items (Base Value < 1 coin)

Very common items that round to minimum 1 coin:

- Items with 100% drop from many Pokemon (e.g., bone from 32 Pokemon)
- Farmable items (apples, mushrooms, etc.)
- Common crafting materials

## Recommendations for Fine-Tuning

### 1. Items That May Need Gameplay Modifiers

Review the CSV for:
- **Evolution Stones**: May need higher multipliers if too cheap
- **Held Items**: May need adjustment based on utility
- **Rare Berries**: Consider gameplay value vs drop rate

### 2. Global Multiplier Adjustment

Currently at 1.0×. Consider:
- **Increase to 1.5-2.0×** if all prices feel too low
- **Decrease to 0.5-0.7×** if all prices feel too high

### 3. Category-Specific Adjustments

**Cobblemon-Exclusive Multiplier** (currently 1.5×):
- Increase if Cobblemon items should be more valuable
- Decrease if too expensive relative to Minecraft items

**Non-Craftable Multiplier** (currently 1.3×):
- Affects items that can only be obtained through drops
- Consider increasing if mob drops feel undervalued

### 4. Per-Player Variance

Currently: -40% to +20% (0.6× to 1.2×)
- Creates price differences between players
- Can be adjusted in BlackMarketConfig.java

## How to Use This Data

1. **Open CSV in Excel/Google Sheets** for sorting and filtering
2. **Sort by base_value** to see most/least valuable items
3. **Filter by cobblemon_exclusive** to review category balance
4. **Look for outliers** that don't match expected value
5. **Add gameplay modifiers** in DropValueCalculator.java for specific items
6. **Adjust multipliers** in BlackMarketConfig.java for global changes

## Updating the Analysis

To regenerate this data after changes:

```bash
# 1. Start the game/server to regenerate logs
# 2. Extract the registry section from latest.log
grep -n "COBBLEMON DROP REGISTRY" logs/latest.log
sed -n 'START_LINE,END_LINEp' logs/latest.log > /tmp/registry_log.txt

# 3. Run the parser
python parse_registry_log.py /tmp/registry_log.txt black_market_items.csv
```

## Notes

- The first row in the CSV has an empty item_id - this appears to be a parsing issue with a specific log entry
- Some items may have special characters in Pokemon names (accents, etc.)
- Price ranges shown are for single items; actual trades may offer multiple items with count variability (0.7× to 1.3×)
