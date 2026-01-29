# Dialogue Tree Tutorial

## File Location

Dialogue files go in: `data/cobblemoncustommerchants/actions/`

Reference them in merchant/NPC configs with: `"action_id": "cobblemoncustommerchants:your_dialogue_file"`

---

## Basic Structure

```json
{
  "description": "Optional description for your reference",
  "dialogue_lines": [
    { /* dialogue node */ },
    { /* dialogue node */ }
  ]
}
```

You can add `{"_comment": "..."}` objects between nodes for organization - they're ignored by the parser.

---

## Dialogue Node Fields

### Required Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique identifier for this node (used by `next`, `default`, `return_line`) |
| `type` | string | Always `"dialogue"` |
| `text` | string | What the NPC says. Use empty `""` for silent router nodes |

### Optional Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `speaker` | string | *(none)* | Name shown before text (e.g., `"Treasure Hunter"`) |
| `priority` | int | `0` | Higher values are checked first. Only matters for entry points |
| `conditions` | array | `[]` | Array of conditions that must ALL be true to select this node |
| `entry_point` | bool | see below | Whether this node can be selected as a starting point |
| `repeatable` | bool | `true` | Whether this dialogue can trigger more than once per player |
| `return_line` | string | *(none)* | Node ID to jump to on repeat visits (after first `repeatable: false` visit) |
| `next` | string | *(none)* | ID of the next dialogue node (linear flow) |
| `branches` | array | `[]` | Array of conditional paths to check |
| `default` | string | *(none)* | Fallback node ID if no branch conditions match |
| `end` | bool | `false` | If `true`, conversation ends after this node |
| `actions` | array | `[]` | Array of effects to execute when this node plays |

---

## Boolean Field Defaults & Behavior

### `entry_point`

**Default:** `true` if the node has `conditions` or `priority` set, otherwise `false`

**What it does:** Determines if this node can be selected as the starting point when a player interacts with the NPC/merchant.

- When `true`: The system considers this node when choosing where to start
- When `false`: This node can only be reached via `next`, `default`, `branches`, or `return_line`

**Example:**
```json
{
  "id": "special_greeting",
  "conditions": [{"type": "has_item", "item": "minecraft:diamond"}],
  "priority": 100,
  "text": "A diamond holder! Welcome!",
  "end": true
}
// entry_point defaults to true because conditions is set
```

### `repeatable`

**Default:** `false`

**What it does:** Controls whether this dialogue node can trigger more than once per player.

- When `false` (default): After playing once, this node is marked as "seen" for that player and won't be selected again as an entry point. If it has a `return_line`, that node is used instead on subsequent visits.
- When `true`: This node can be selected/played every time conditions are met

**Example:**
```json
{
  "id": "first_meeting",
  "text": "Welcome, stranger! First time here?",
  "repeatable": false,
  "return_line": "repeat_greeting",
  "next": "introduction"
}
// First visit: plays "first_meeting" -> "introduction"
// Second visit: jumps directly to "repeat_greeting"
```

### `end`

**Default:** `false`

**What it does:** Signals that the conversation should end after this node.

- When `true`: After displaying this node's text and executing actions, the dialogue ends
- When `false`: The system looks for `next`, `branches`/`default` to continue, or ends if none exist

**Example:**
```json
{
  "id": "farewell",
  "text": "Safe travels, adventurer!",
  "end": true
}
// Conversation ends after this message
```

---

## Flow Control

### Linear Flow (using `next`)

```json
{
  "id": "line1",
  "text": "Hello there!",
  "next": "line2"
},
{
  "id": "line2",
  "text": "How can I help you?",
  "next": "line3"
},
{
  "id": "line3",
  "text": "Goodbye!",
  "end": true
}
```

### Conditional Branching (using `branches` + `default`)

```json
{
  "id": "check_pokemon",
  "text": "Let me check your team...",
  "branches": [
    {
      "conditions": [{"type": "has_pokemon_move", "move": "dive"}],
      "next": "has_dive"
    },
    {
      "conditions": [{"type": "has_pokemon_move", "move": "surf"}],
      "next": "has_surf"
    }
  ],
  "default": "no_water_move"
}
```

Branches are checked in order. First matching branch is taken. If none match, `default` is used.

### Silent Routers

Nodes with empty `text` that exist purely for branching logic:

```json
{
  "id": "item_router",
  "text": "",
  "entry_point": false,
  "branches": [
    {
      "conditions": [{"type": "has_item", "item": "mod:special_item"}],
      "next": "has_item_path"
    }
  ],
  "default": "no_item_path"
}
```

---

## Entry Point Selection

When a player interacts with an NPC/merchant, the system:

1. **Gathers** all potential entry points (nodes where `entry_point` is true or implied)
2. **Filters** by conditions - only nodes where ALL conditions pass
3. **Filters** by `repeatable` - skips nodes with `repeatable: false` that player has already seen
4. **Selects** the node with the highest `priority`

**Important:** If a selected node has `repeatable: false` AND a `return_line`, subsequent visits bypass this entire selection process and jump directly to `return_line`.

---

## The `return_line` System

When you want different behavior on repeat visits:

```json
{
  "id": "first_greeting",
  "text": "Welcome! First time here?",
  "priority": 50,
  "repeatable": false,
  "return_line": "return_visit",
  "next": "give_tutorial"
},
{
  "id": "return_visit",
  "text": "Good to see you again!",
  "entry_point": false,
  "repeatable": true,
  "end": true
}
```

**Flow:**
- **First visit:** `first_greeting` → `give_tutorial` → ...
- **Second+ visit:** Jumps directly to `return_visit`

**Critical:** `return_line` bypasses entry point selection entirely. It doesn't check priorities or conditions - it goes straight to that node.

---

## Available Conditions

| Type | Parameters | Description |
|------|------------|-------------|
| `has_item` | `item` | Player has item anywhere in inventory |
| `has_item_state` | `item`, `component`, `value` | Item in inventory has specific NBT component value |
| `holding_item` | `item` | Player is holding item in main hand |
| `holding_item_state` | `item`, `component`, `value` or `bool_value` | Held item has specific NBT value |
| `has_pokemon_move` | `move` | Any Pokemon in player's party knows the move |
| `merchant_variant` | `variant` | Merchant/NPC is currently set to this variant |

### Condition Examples

```json
// Check for item in inventory
{"type": "has_item", "item": "minecraft:diamond"}

// Check item NBT state
{"type": "holding_item_state", "item": "mod:orb", "component": "orb_state", "value": 3}

// Check boolean NBT state
{"type": "holding_item_state", "item": "mod:tablet", "component": "glowing", "bool_value": true}

// Check Pokemon move
{"type": "has_pokemon_move", "move": "dive"}

// Check merchant variant
{"type": "merchant_variant", "variant": "housed"}
```

### Multiple Conditions (AND logic)

All conditions in an array must be true:

```json
"conditions": [
  {"type": "merchant_variant", "variant": "housed"},
  {"type": "holding_item_state", "item": "mod:orb", "component": "state", "value": 3}
]
// Both must be true for this node to be selected
```

---

## Available Actions

| Type | Parameters | Description |
|------|------------|-------------|
| `give_item` | `item`, `count` | Give item to player's inventory |
| `play_sound` | `sound` | Play a sound effect |
| `apply_effect` | `effect`, `duration`, `amplifier` | Apply potion effect to player |
| `teleport_option` | `text`, `hover_text`, `destination` | Show clickable teleport link in chat |
| `broadcast` | `text` | Send message to all players on server |
| `atmospheric` | `text` | Show grey italic message (flavor text) |

### Action Examples

```json
"actions": [
  {"type": "give_item", "item": "minecraft:diamond", "count": 5},
  {"type": "play_sound", "sound": "minecraft:entity.player.levelup"},
  {"type": "apply_effect", "effect": "minecraft:speed", "duration": 6000, "amplifier": 1},
  {"type": "teleport_option",
    "text": "[Teleport to Base]",
    "hover_text": "Click to teleport home",
    "destination": {"x": 100, "y": 64, "z": 200, "dimension": "minecraft:overworld"}
  }
]
```

---

## Common Patterns

### One-Time Event with Repeat Fallback

```json
{
  "id": "intro",
  "text": "Ah, a new face! Let me tell you about this place...",
  "priority": 50,
  "repeatable": false,
  "return_line": "repeat_greeting",
  "next": "explanation"
},
{
  "id": "explanation",
  "text": "This temple holds ancient secrets...",
  "entry_point": false,
  "next": "give_item"
},
{
  "id": "give_item",
  "text": "Take this map, it will guide you.",
  "entry_point": false,
  "actions": [
    {"type": "give_item", "item": "mod:map", "count": 1}
  ],
  "end": true
},
{
  "id": "repeat_greeting",
  "text": "Back again? The temple awaits.",
  "entry_point": false,
  "repeatable": true,
  "end": true
}
```

### Progressive Quest States

```json
// Lowest priority - default fallback
{
  "id": "no_progress",
  "text": "Bring me something interesting and we'll talk.",
  "priority": 0,
  "repeatable": true,
  "end": true
},

// Medium priority - has quest item
{
  "id": "has_quest_item",
  "text": "You found the artifact! Let me examine it...",
  "conditions": [
    {"type": "holding_item", "item": "mod:artifact"}
  ],
  "priority": 100,
  "repeatable": false,
  "return_line": "quest_complete_reminder",
  "next": "reward_player"
},

// Continuation nodes
{
  "id": "reward_player",
  "text": "Magnificent! Here's your reward.",
  "entry_point": false,
  "actions": [
    {"type": "give_item", "item": "minecraft:emerald", "count": 10}
  ],
  "end": true
},

{
  "id": "quest_complete_reminder",
  "text": "I've already rewarded you for that artifact.",
  "entry_point": false,
  "repeatable": true,
  "end": true
}
```

### Conditional Branching with Fallback

```json
{
  "id": "check_readiness",
  "text": "Are you prepared for the journey?",
  "next": "evaluate_pokemon"
},
{
  "id": "evaluate_pokemon",
  "text": "",
  "entry_point": false,
  "branches": [
    {
      "conditions": [{"type": "has_pokemon_move", "move": "fly"}],
      "next": "can_fly"
    },
    {
      "conditions": [{"type": "has_pokemon_move", "move": "teleport"}],
      "next": "can_teleport"
    }
  ],
  "default": "travel_on_foot"
},
{
  "id": "can_fly",
  "text": "Your Pokemon can fly you there!",
  "entry_point": false,
  "end": true
},
{
  "id": "can_teleport",
  "text": "Your Pokemon can teleport you there!",
  "entry_point": false,
  "end": true
},
{
  "id": "travel_on_foot",
  "text": "You'll have to walk. It's a long journey.",
  "entry_point": false,
  "end": true
}
```

---

## Tips & Best Practices

1. **Use `_comment` objects** for section headers to organize your dialogue
2. **Order nodes logically** - follow the player's journey from start to finish
3. **Higher priority = checked first** - use 0 for fallbacks, 100+ for specific conditions
4. **Silent routers** (empty `text`) are useful for complex branching without visible dialogue
5. **`return_line` bypasses priorities** - it jumps directly to that node, no questions asked
6. **Test with `/reload`** - dialogue configs reload without server restart
7. **Use descriptive IDs** - `step3_has_dive` is clearer than `dialogue_7`
8. **Always set `entry_point: false`** on continuation nodes to prevent them from being selected as starting points

---

## Testing & Debugging

1. **Reload configs:** Run `/reload` in-game to apply changes
2. **Check logs:** Look for parsing errors in the server console
3. **Test all paths:** Make sure to test with and without the required items/Pokemon
4. **Verify `repeatable`:** Test first visit AND return visits
5. **Check priorities:** If the wrong dialogue plays, verify priority values

---

## Quick Reference

```
Entry Point Selection:
  1. Gather nodes with entry_point=true (or conditions/priority set)
  2. Filter by passing conditions
  3. Filter out non-repeatable nodes already seen
  4. Pick highest priority
  5. If that node has return_line and was seen before, jump to return_line instead

Node Flow:
  - next: "id"           → Go to specific node
  - branches + default   → Check conditions, go to matching branch or default
  - end: true            → Stop conversation
  - (none of above)      → Stop conversation

Defaults:
  - entry_point: true if conditions/priority set, false otherwise
  - repeatable: false
  - end: false
  - priority: 0
```
