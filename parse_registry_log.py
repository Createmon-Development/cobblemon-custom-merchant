#!/usr/bin/env python3
"""
Parses the Cobblemon Drop Registry log output and creates a CSV spreadsheet
"""

import re
import csv
import sys

def parse_log_file(log_file_path):
    """Parse the registry log and extract item data"""
    items = []
    current_item = {}

    with open(log_file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    for line in lines:
        line = line.strip()

        # Remove log prefix
        if ']:' in line:
            line = line.split(']:',  1)[1].strip()

        # Skip header/footer lines
        if '===' in line or '---' in line:
            continue

        # New item starts with two spaces and item ID
        if line.startswith('minecraft:') or line.startswith('cobblemon:'):
            # Save previous item if it exists
            if current_item:
                items.append(current_item)
            # Start new item
            current_item = {'item_id': line}

        # Parse drop stats
        elif 'Avg Drop Chance:' in line:
            match = re.search(r'([\d.]+)%', line)
            if match:
                current_item['avg_drop_chance'] = match.group(1)

        elif 'Avg Drop Quantity:' in line:
            match = re.search(r'([\d.]+)', line)
            if match:
                current_item['avg_drop_quantity'] = match.group(1)

        elif 'Pokemon Count:' in line:
            match = re.search(r'(\d+) pokemon', line)
            if match:
                current_item['pokemon_count'] = match.group(1)

        # Parse item properties
        elif 'Cobblemon-Exclusive:' in line:
            current_item['cobblemon_exclusive'] = 'true' in line.lower()

        elif 'Craftable:' in line:
            current_item['craftable'] = 'true' in line.lower()

        elif 'Growable:' in line:
            current_item['growable'] = 'true' in line.lower()

        # Parse formula variables
        elif 'Rarity Score:' in line:
            match = re.search(r'Rarity Score: ([\d.]+)', line)
            if match:
                current_item['rarity_score'] = match.group(1)

        elif 'Exclusivity Multiplier:' in line:
            match = re.search(r'([\d.]+)x', line)
            if match:
                current_item['exclusivity_mult'] = match.group(1)

        elif 'Craftability Multiplier:' in line:
            match = re.search(r'([\d.]+)x', line)
            if match:
                current_item['craftability_mult'] = match.group(1)

        elif 'Availability Divisor:' in line:
            match = re.search(r'([\d.]+)', line)
            if match:
                current_item['availability'] = match.group(1)

        elif 'Gameplay Modifier:' in line:
            match = re.search(r'([\d.]+)x', line)
            if match:
                current_item['gameplay_modifier'] = match.group(1)
                current_item['has_custom_modifier'] = '(CUSTOM)' in line

        elif 'Global Multiplier:' in line:
            match = re.search(r'([\d.]+)x', line)
            if match:
                current_item['global_mult'] = match.group(1)

        # Parse calculated values
        elif 'Calculated Base Value:' in line:
            match = re.search(r'([\d.]+) relic coins', line)
            if match:
                current_item['base_value'] = match.group(1)

        elif 'Price Range' in line:
            match = re.search(r'(\d+)-(\d+) coins', line)
            if match:
                current_item['price_min'] = match.group(1)
                current_item['price_max'] = match.group(2)

        elif 'Dropped by:' in line:
            # Extract the Pokemon list
            pokemon_text = line.split('Dropped by:', 1)[1].strip()
            current_item['dropped_by'] = pokemon_text

    # Don't forget the last item
    if current_item:
        items.append(current_item)

    return items

def write_csv(items, output_path):
    """Write items to CSV file"""
    if not items:
        print("No items to write!")
        return

    # Define column order
    columns = [
        'item_id',
        'avg_drop_chance',
        'avg_drop_quantity',
        'pokemon_count',
        'cobblemon_exclusive',
        'craftable',
        'growable',
        'rarity_score',
        'exclusivity_mult',
        'craftability_mult',
        'availability',
        'gameplay_modifier',
        'has_custom_modifier',
        'global_mult',
        'base_value',
        'price_min',
        'price_max',
        'dropped_by'
    ]

    with open(output_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=columns, extrasaction='ignore')
        writer.writeheader()
        writer.writerows(items)

    print(f"Wrote {len(items)} items to {output_path}")

def main():
    log_file = '/tmp/registry_log.txt'
    output_file = 'black_market_items.csv'

    if len(sys.argv) > 1:
        log_file = sys.argv[1]
    if len(sys.argv) > 2:
        output_file = sys.argv[2]

    print(f"Parsing {log_file}...")
    items = parse_log_file(log_file)

    print(f"Writing to {output_file}...")
    write_csv(items, output_file)

    # Print summary statistics
    print("\n=== Summary Statistics ===")
    print(f"Total Items: {len(items)}")

    minecraft_items = [i for i in items if not i.get('cobblemon_exclusive', False)]
    cobblemon_items = [i for i in items if i.get('cobblemon_exclusive', False)]

    print(f"Minecraft Items: {len(minecraft_items)}")
    print(f"Cobblemon-Exclusive Items: {len(cobblemon_items)}")

    # Calculate value ranges
    base_values = [float(i.get('base_value', 0)) for i in items if 'base_value' in i]
    if base_values:
        print(f"\nBase Value Range: {min(base_values):.2f} - {max(base_values):.2f} coins")
        print(f"Average Base Value: {sum(base_values) / len(base_values):.2f} coins")

    # Items with custom modifiers
    custom_mod_items = [i for i in items if i.get('has_custom_modifier', False)]
    print(f"\nItems with Custom Gameplay Modifiers: {len(custom_mod_items)}")

if __name__ == '__main__':
    main()
