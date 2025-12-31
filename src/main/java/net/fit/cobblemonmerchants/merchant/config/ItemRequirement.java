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
 * - Exact item: {"id": "minecraft:stick", "count": 1}
 * - Tag: {"tag": "minecraft:logs", "count": 4}
 */
public class ItemRequirement {
    private final ItemStack exactItem;
    private final TagKey<Item> tag;
    private final int count;

    private ItemRequirement(ItemStack exactItem, TagKey<Item> tag, int count) {
        if ((exactItem != null) == (tag != null)) {
            throw new IllegalArgumentException("Must specify exactly one of 'id' or 'tag'");
        }
        this.exactItem = exactItem;
        this.tag = tag;
        this.count = count;
    }

    public static ItemRequirement fromItem(ItemStack item) {
        return new ItemRequirement(item, null, item.getCount());
    }

    public static ItemRequirement fromTag(TagKey<Item> tag, int count) {
        return new ItemRequirement(null, tag, count);
    }

    // Dispatch codec that checks for "tag" field to determine format
    public static final Codec<ItemRequirement> CODEC = Codec.PASSTHROUGH.comapFlatMap(
        dynamic -> {
            // Check if "tag" field exists
            var tagField = dynamic.get("tag");
            if (tagField.result().isPresent()) {
                // Parse as tag format
                var tagLocResult = ResourceLocation.CODEC.parse(tagField.result().get());
                var countResult = dynamic.get("count").flatMap(d -> Codec.INT.parse(d));

                return tagLocResult.flatMap(tagLoc ->
                    countResult.map(count -> {
                        TagKey<Item> tag = TagKey.create(Registries.ITEM, tagLoc);
                        return ItemRequirement.fromTag(tag, count);
                    })
                );
            } else {
                // Parse as ItemStack format
                return ItemStack.CODEC.parse(dynamic).map(ItemRequirement::fromItem);
            }
        },
        req -> {
            if (req.isTag()) {
                // Encode as tag format
                return new com.mojang.serialization.Dynamic<>(
                    com.mojang.serialization.JsonOps.INSTANCE,
                    com.mojang.serialization.JsonOps.INSTANCE.createMap(
                        java.util.Map.of(
                            com.mojang.serialization.JsonOps.INSTANCE.createString("tag"),
                            com.mojang.serialization.JsonOps.INSTANCE.createString(req.tag.location().toString()),
                            com.mojang.serialization.JsonOps.INSTANCE.createString("count"),
                            com.mojang.serialization.JsonOps.INSTANCE.createInt(req.count)
                        )
                    )
                );
            } else {
                // Encode as ItemStack
                return new com.mojang.serialization.Dynamic<>(
                    com.mojang.serialization.JsonOps.INSTANCE,
                    ItemStack.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, req.exactItem)
                        .result().orElse(com.mojang.serialization.JsonOps.INSTANCE.empty())
                );
            }
        }
    );

    /**
     * Check if the given ItemStack matches this requirement.
     * For exact items, checks item type and components.
     * For tags, checks if the item is in the tag.
     */
    public boolean matches(ItemStack stack) {
        if (exactItem != null) {
            return ItemStack.isSameItemSameComponents(stack, exactItem);
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
     */
    public ItemCost toItemCost() {
        if (exactItem != null) {
            return new ItemCost(exactItem.getItem(), count);
        } else {
            // Use first item from tag
            Item firstItem = BuiltInRegistries.ITEM.stream()
                .filter(item -> item.builtInRegistryHolder().is(tag))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Tag " + tag + " is empty"));
            return new ItemCost(firstItem, count);
        }
    }
}
