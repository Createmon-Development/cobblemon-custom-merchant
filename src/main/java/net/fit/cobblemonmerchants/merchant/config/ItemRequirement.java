package net.fit.cobblemonmerchants.merchant.config;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;

/**
 * Represents an item requirement that can match either a specific item or any item in a tag.
 * Supports JSON formats:
 * - Exact item: {"id": "minecraft:stick", "count": 1, "display_name": "Custom Name", "ignore_components": false}
 * - Tag: {"tag": "minecraft:logs", "count": 4, "display_name": "Any Log"}
 */
public class ItemRequirement {
    private final ItemStack exactItem;
    private final TagKey<Item> tag;
    private final int count;
    private final String displayName;
    private final boolean ignoreComponents;

    private ItemRequirement(ItemStack exactItem, TagKey<Item> tag, int count, String displayName, boolean ignoreComponents) {
        if ((exactItem != null) == (tag != null)) {
            throw new IllegalArgumentException("Must specify exactly one of 'id' or 'tag'");
        }
        this.exactItem = exactItem;
        this.tag = tag;
        this.count = count;
        this.displayName = displayName;
        this.ignoreComponents = ignoreComponents;
    }

    public static ItemRequirement fromItem(ItemStack item) {
        return new ItemRequirement(item, null, item.getCount(), null, false);
    }

    public static ItemRequirement fromItem(ItemStack item, String displayName) {
        return new ItemRequirement(item, null, item.getCount(), displayName, false);
    }

    public static ItemRequirement fromItem(ItemStack item, String displayName, boolean ignoreComponents) {
        return new ItemRequirement(item, null, item.getCount(), displayName, ignoreComponents);
    }

    public static ItemRequirement fromTag(TagKey<Item> tag, int count) {
        return new ItemRequirement(null, tag, count, null, false);
    }

    public static ItemRequirement fromTag(TagKey<Item> tag, int count, String displayName) {
        return new ItemRequirement(null, tag, count, displayName, false);
    }

    // Dispatch codec that checks for "tag" field to determine format
    public static final Codec<ItemRequirement> CODEC = Codec.PASSTHROUGH.comapFlatMap(
        dynamic -> {
            // Check for optional display_name field
            var displayNameField = dynamic.get("display_name");
            String displayName = displayNameField.result().flatMap(d -> Codec.STRING.parse(d).result()).orElse(null);

            // Check for optional ignore_components field
            var ignoreComponentsField = dynamic.get("ignore_components");
            boolean ignoreComponents = ignoreComponentsField.result().flatMap(d -> Codec.BOOL.parse(d).result()).orElse(false);

            // Check if "tag" field exists
            var tagField = dynamic.get("tag");
            if (tagField.result().isPresent()) {
                // Parse as tag format
                var tagLocResult = ResourceLocation.CODEC.parse(tagField.result().get());
                var countResult = dynamic.get("count").flatMap(d -> Codec.INT.parse(d));

                return tagLocResult.flatMap(tagLoc ->
                    countResult.map(count -> {
                        TagKey<Item> tag = TagKey.create(Registries.ITEM, tagLoc);
                        return ItemRequirement.fromTag(tag, count, displayName);
                    })
                );
            } else {
                // Parse as ItemStack format
                return ItemStack.CODEC.parse(dynamic).map(item ->
                    displayName != null ? ItemRequirement.fromItem(item, displayName, ignoreComponents) :
                        ignoreComponents ? ItemRequirement.fromItem(item, null, ignoreComponents) : ItemRequirement.fromItem(item)
                );
            }
        },
        req -> {
            if (req.isTag()) {
                // Encode as tag format
                java.util.Map<com.mojang.serialization.DataResult<com.google.gson.JsonElement>, com.mojang.serialization.DataResult<com.google.gson.JsonElement>> map = new java.util.HashMap<>();
                map.put(
                    com.mojang.serialization.DataResult.success(com.mojang.serialization.JsonOps.INSTANCE.createString("tag")),
                    com.mojang.serialization.DataResult.success(com.mojang.serialization.JsonOps.INSTANCE.createString(req.tag.location().toString()))
                );
                map.put(
                    com.mojang.serialization.DataResult.success(com.mojang.serialization.JsonOps.INSTANCE.createString("count")),
                    com.mojang.serialization.DataResult.success(com.mojang.serialization.JsonOps.INSTANCE.createInt(req.count))
                );
                if (req.displayName != null) {
                    map.put(
                        com.mojang.serialization.DataResult.success(com.mojang.serialization.JsonOps.INSTANCE.createString("display_name")),
                        com.mojang.serialization.DataResult.success(com.mojang.serialization.JsonOps.INSTANCE.createString(req.displayName))
                    );
                }
                return new com.mojang.serialization.Dynamic<>(
                    com.mojang.serialization.JsonOps.INSTANCE,
                    com.mojang.serialization.JsonOps.INSTANCE.createMap(
                        map.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                            e -> e.getKey().result().orElseThrow(),
                            e -> e.getValue().result().orElseThrow()
                        ))
                    )
                );
            } else {
                // Encode as ItemStack with optional display_name and ignore_components
                var baseEncoding = ItemStack.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, req.exactItem)
                    .result().orElse(com.mojang.serialization.JsonOps.INSTANCE.empty());

                if (req.displayName != null || req.ignoreComponents) {
                    // Add display_name and/or ignore_components to the encoded item
                    java.util.Map<com.google.gson.JsonElement, com.google.gson.JsonElement> itemMap = new java.util.HashMap<>();
                    if (baseEncoding.isJsonObject()) {
                        baseEncoding.getAsJsonObject().entrySet().forEach(e ->
                            itemMap.put(new com.google.gson.JsonPrimitive(e.getKey()), e.getValue())
                        );
                    }
                    if (req.displayName != null) {
                        itemMap.put(
                            com.mojang.serialization.JsonOps.INSTANCE.createString("display_name"),
                            com.mojang.serialization.JsonOps.INSTANCE.createString(req.displayName)
                        );
                    }
                    if (req.ignoreComponents) {
                        itemMap.put(
                            com.mojang.serialization.JsonOps.INSTANCE.createString("ignore_components"),
                            com.mojang.serialization.JsonOps.INSTANCE.createBoolean(true)
                        );
                    }
                    baseEncoding = com.mojang.serialization.JsonOps.INSTANCE.createMap(itemMap);
                }

                return new com.mojang.serialization.Dynamic<>(com.mojang.serialization.JsonOps.INSTANCE, baseEncoding);
            }
        }
    );

    /**
     * Check if the given ItemStack matches this requirement.
     * For exact items, checks item type and optionally components (based on ignoreComponents flag).
     * For tags, checks if the item is in the tag.
     */
    public boolean matches(ItemStack stack) {
        if (exactItem != null) {
            if (ignoreComponents) {
                // Only check item type, ignore components
                return stack.getItem() == exactItem.getItem();
            } else {
                // Check both item type and components
                return ItemStack.isSameItemSameComponents(stack, exactItem);
            }
        } else {
            return stack.is(tag);
        }
    }

    public int getCount() {
        return count;
    }

    public boolean isTag() {
        return tag != null;
    }

    public TagKey<Item> getTag() {
        return tag;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get a representative ItemStack for display purposes.
     * For exact items, returns the exact item.
     * For tags, returns the first item in the tag registry.
     */
    public ItemStack getDisplayStack() {
        if (exactItem != null) {
            return exactItem;
        } else {
            // Find first item in tag for display
            return BuiltInRegistries.ITEM.stream()
                .filter(item -> item.builtInRegistryHolder().is(tag))
                .findFirst()
                .map(item -> new ItemStack(item, count))
                .orElse(ItemStack.EMPTY);
        }
    }

    /**
     * Convert to ItemCost for vanilla merchant offers.
     * Note: Tags are not fully supported in vanilla ItemCost,
     * so we use the first item from the tag.
     * Returns null if the tag is empty (broken trade).
     */
    public ItemCost toItemCost() {
        if (exactItem != null) {
            return new ItemCost(exactItem.getItem(), count);
        } else {
            // Use first item from tag
            Item firstItem = BuiltInRegistries.ITEM.stream()
                .filter(item -> item.builtInRegistryHolder().is(tag))
                .findFirst()
                .orElse(null);
            if (firstItem == null) {
                // Tag is empty - return barrier item as fallback
                return new ItemCost(net.minecraft.world.item.Items.BARRIER, count);
            }
            return new ItemCost(firstItem, count);
        }
    }
}
