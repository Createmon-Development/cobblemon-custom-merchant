package net.fit.cobblemonmerchants.cobblemon;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Helper class for accessing Cobblemon party data via reflection.
 * This avoids compile-time dependency issues while still allowing
 * integration with Cobblemon when it's loaded.
 */
public class CobblemonPartyHelper {

    private static boolean cobblemonAvailable = false;
    private static boolean initialized = false;

    // Cached reflection objects
    private static Class<?> cobblemonClass;
    private static Class<?> pokemonStoreClass;
    private static Class<?> pokemonClass;
    private static Class<?> moveSetClass;
    private static Method getStorageMethod;
    private static Method getPartyMethod;
    private static Method iteratorMethod;

    /**
     * Initializes the reflection cache for Cobblemon classes.
     * Should be called once during mod initialization.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        try {
            // Try to load Cobblemon classes
            cobblemonClass = Class.forName("com.cobblemon.mod.common.Cobblemon");
            pokemonStoreClass = Class.forName("com.cobblemon.mod.common.api.storage.party.PlayerPartyStore");
            pokemonClass = Class.forName("com.cobblemon.mod.common.pokemon.Pokemon");
            moveSetClass = Class.forName("com.cobblemon.mod.common.pokemon.MoveSet");

            // Get the storage method from Cobblemon
            Object storageInstance = cobblemonClass.getField("storage").get(null);
            getPartyMethod = storageInstance.getClass().getMethod("getParty", UUID.class);

            cobblemonAvailable = true;
            CobblemonMerchants.LOGGER.info("CobblemonPartyHelper initialized successfully - Cobblemon integration enabled");

        } catch (ClassNotFoundException e) {
            CobblemonMerchants.LOGGER.info("Cobblemon not found - party/move conditions will always return false");
            cobblemonAvailable = false;
        } catch (Exception e) {
            CobblemonMerchants.LOGGER.warn("Failed to initialize Cobblemon party helper: {}", e.getMessage());
            cobblemonAvailable = false;
        }

        initialized = true;
    }

    /**
     * Checks if Cobblemon is available for party operations.
     * @return true if Cobblemon is loaded and accessible
     */
    public static boolean isCobblemonAvailable() {
        if (!initialized) {
            initialize();
        }
        return cobblemonAvailable;
    }

    /**
     * Checks if a player has a Pokemon with a specific move in their party.
     * @param player The player to check
     * @param moveName The move name to look for (e.g., "dive", "surf")
     * @return true if any Pokemon in the party has the specified move
     */
    public static boolean hasPartyPokemonWithMove(ServerPlayer player, String moveName) {
        if (!initialized) {
            initialize();
        }

        if (!cobblemonAvailable) {
            return false;
        }

        try {
            // Get the storage instance
            Object storageInstance = cobblemonClass.getField("storage").get(null);

            // Get the player's party
            Object party = getPartyMethod.invoke(storageInstance, player.getUUID());
            if (party == null) {
                return false;
            }

            // Get the iterator/slots from the party
            Method getSlotsMethod = party.getClass().getMethod("iterator");
            Iterable<?> slots = (Iterable<?>) getSlotsMethod.invoke(party);

            // Iterate through party slots
            for (Object pokemon : slots) {
                if (pokemon == null) {
                    continue;
                }

                if (pokemonHasMove(pokemon, moveName)) {
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            CobblemonMerchants.LOGGER.debug("Error checking party for move {}: {}", moveName, e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a specific Pokemon has a move.
     * @param pokemon The Pokemon object (from reflection)
     * @param moveName The move name to check for
     * @return true if the Pokemon has the move
     */
    private static boolean pokemonHasMove(Object pokemon, String moveName) {
        try {
            // Get the MoveSet from the Pokemon
            Method getMoveSetMethod = pokemonClass.getMethod("getMoveSet");
            Object moveSet = getMoveSetMethod.invoke(pokemon);

            if (moveSet == null) {
                return false;
            }

            // Get the moves from the MoveSet
            // MoveSet extends Iterable<MoveSlot>
            Method iteratorMethod = moveSet.getClass().getMethod("iterator");
            Iterable<?> moves = (Iterable<?>) iteratorMethod.invoke(moveSet);

            for (Object moveSlot : moves) {
                if (moveSlot == null) {
                    continue;
                }

                // Get the move from the slot
                String slotMoveName = getMoveNameFromSlot(moveSlot);
                if (slotMoveName != null && slotMoveName.equalsIgnoreCase(moveName)) {
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            CobblemonMerchants.LOGGER.debug("Error checking Pokemon for move: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gets the move name from a MoveSlot object.
     */
    private static String getMoveNameFromSlot(Object moveSlot) {
        try {
            // Try to get the move name - could be via template or direct
            // MoveSlot has a method like getName() or getMoveTemplate().getName()

            // First try direct name
            try {
                Method getNameMethod = moveSlot.getClass().getMethod("getName");
                Object name = getNameMethod.invoke(moveSlot);
                if (name != null) {
                    return name.toString();
                }
            } catch (NoSuchMethodException ignored) {
            }

            // Try via template
            try {
                Method getTemplateMethod = moveSlot.getClass().getMethod("getMoveTemplate");
                Object template = getTemplateMethod.invoke(moveSlot);
                if (template != null) {
                    Method templateNameMethod = template.getClass().getMethod("getName");
                    Object name = templateNameMethod.invoke(template);
                    if (name != null) {
                        return name.toString();
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }

            // Try via move property
            try {
                Method getMoveMethod = moveSlot.getClass().getMethod("getMove");
                Object move = getMoveMethod.invoke(moveSlot);
                if (move != null) {
                    Method moveNameMethod = move.getClass().getMethod("getName");
                    Object name = moveNameMethod.invoke(move);
                    if (name != null) {
                        return name.toString();
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks if a player has a specific Pokemon species in their party.
     * @param player The player to check
     * @param speciesName The species name to look for (e.g., "pikachu", "charizard")
     * @return true if the player has the specified species in their party
     */
    public static boolean hasPartyPokemonSpecies(ServerPlayer player, String speciesName) {
        if (!initialized) {
            initialize();
        }

        if (!cobblemonAvailable) {
            return false;
        }

        try {
            // Get the storage instance
            Object storageInstance = cobblemonClass.getField("storage").get(null);

            // Get the player's party
            Object party = getPartyMethod.invoke(storageInstance, player.getUUID());
            if (party == null) {
                return false;
            }

            // Get the iterator from the party
            Method getSlotsMethod = party.getClass().getMethod("iterator");
            Iterable<?> slots = (Iterable<?>) getSlotsMethod.invoke(party);

            // Iterate through party slots
            for (Object pokemon : slots) {
                if (pokemon == null) {
                    continue;
                }

                String pokemonSpecies = getPokemonSpeciesName(pokemon);
                if (pokemonSpecies != null && pokemonSpecies.equalsIgnoreCase(speciesName)) {
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            CobblemonMerchants.LOGGER.debug("Error checking party for species {}: {}", speciesName, e.getMessage());
            return false;
        }
    }

    /**
     * Gets the species name from a Pokemon object.
     */
    private static String getPokemonSpeciesName(Object pokemon) {
        try {
            // Try Pokemon.getSpecies().getName()
            Method getSpeciesMethod = pokemonClass.getMethod("getSpecies");
            Object species = getSpeciesMethod.invoke(pokemon);
            if (species != null) {
                Method getNameMethod = species.getClass().getMethod("getName");
                Object name = getNameMethod.invoke(species);
                if (name != null) {
                    return name.toString();
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the count of Pokemon in a player's party.
     * @param player The player to check
     * @return The number of Pokemon in the party (0-6)
     */
    public static int getPartySize(ServerPlayer player) {
        if (!initialized) {
            initialize();
        }

        if (!cobblemonAvailable) {
            return 0;
        }

        try {
            Object storageInstance = cobblemonClass.getField("storage").get(null);
            Object party = getPartyMethod.invoke(storageInstance, player.getUUID());

            if (party == null) {
                return 0;
            }

            // Try to get size directly
            try {
                Method sizeMethod = party.getClass().getMethod("size");
                Object size = sizeMethod.invoke(party);
                if (size instanceof Integer) {
                    return (Integer) size;
                }
            } catch (NoSuchMethodException ignored) {
            }

            // Count manually
            int count = 0;
            Method getSlotsMethod = party.getClass().getMethod("iterator");
            Iterable<?> slots = (Iterable<?>) getSlotsMethod.invoke(party);
            for (Object pokemon : slots) {
                if (pokemon != null) {
                    count++;
                }
            }
            return count;

        } catch (Exception e) {
            CobblemonMerchants.LOGGER.debug("Error getting party size: {}", e.getMessage());
            return 0;
        }
    }
}
