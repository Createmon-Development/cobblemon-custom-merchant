package net.fit.cobblemonmerchants.cobblemon;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/**
 * Helper class for accessing Cobblemon party data via reflection.
 * This avoids compile-time dependency issues while still allowing
 * integration with Cobblemon when it's loaded.
 *
 * Based on Cobblemon API:
 * - Cobblemon.storage.getParty(player) -> PlayerPartyStore
 * - PlayerPartyStore implements Iterable<Pokemon>
 * - Pokemon.moveSet -> MoveSet
 * - MoveSet implements Iterable<Move>
 * - Move.name -> String
 */
public class CobblemonPartyHelper {

    private static boolean cobblemonAvailable = false;
    private static boolean initialized = false;

    // Cached reflection objects
    private static Class<?> cobblemonClass;
    private static Class<?> pokemonClass;
    private static Class<?> moveClass;
    private static Object storageInstance;
    private static Method getPartyMethod;

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
            pokemonClass = Class.forName("com.cobblemon.mod.common.pokemon.Pokemon");
            moveClass = Class.forName("com.cobblemon.mod.common.api.moves.Move");

            CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Found Cobblemon class: {}", cobblemonClass.getName());

            // Cobblemon is a Kotlin object, try multiple ways to access the storage
            // Method 1: Try INSTANCE.getStorage() (Kotlin object pattern)
            try {
                java.lang.reflect.Field instanceField = cobblemonClass.getField("INSTANCE");
                Object instance = instanceField.get(null);
                if (instance != null) {
                    Method getStorageMethod = instance.getClass().getMethod("getStorage");
                    storageInstance = getStorageMethod.invoke(instance);
                    CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Got storage via INSTANCE.getStorage()");
                }
            } catch (NoSuchFieldException | NoSuchMethodException e) {
                CobblemonMerchants.LOGGER.debug("[CobblemonPartyHelper] INSTANCE.getStorage() not available: {}", e.getMessage());
            }

            // Method 2: Try getStorage() directly on the class
            if (storageInstance == null) {
                try {
                    Method getStorageMethod = cobblemonClass.getMethod("getStorage");
                    storageInstance = getStorageMethod.invoke(null);
                    CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Got storage via static getStorage()");
                } catch (NoSuchMethodException e) {
                    CobblemonMerchants.LOGGER.debug("[CobblemonPartyHelper] Static getStorage() not available: {}", e.getMessage());
                }
            }

            // Method 3: Try storage field directly
            if (storageInstance == null) {
                try {
                    java.lang.reflect.Field storageField = cobblemonClass.getField("storage");
                    storageInstance = storageField.get(null);
                    CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Got storage via direct field access");
                } catch (NoSuchFieldException e) {
                    CobblemonMerchants.LOGGER.debug("[CobblemonPartyHelper] storage field not available: {}", e.getMessage());
                }
            }

            // Method 4: Try declared fields (including private)
            if (storageInstance == null) {
                for (java.lang.reflect.Field f : cobblemonClass.getDeclaredFields()) {
                    if (f.getName().toLowerCase().contains("storage")) {
                        f.setAccessible(true);
                        storageInstance = f.get(null);
                        if (storageInstance != null) {
                            CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Got storage via declared field: {}", f.getName());
                            break;
                        }
                    }
                }
            }

            // Method 5: List all available methods and fields for debugging
            if (storageInstance == null) {
                CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Available Cobblemon methods:");
                for (Method m : cobblemonClass.getMethods()) {
                    if (m.getName().toLowerCase().contains("storage") || m.getName().toLowerCase().contains("party")) {
                        CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper]   Method: {} -> {}", m.getName(), m.getReturnType().getName());
                    }
                }
                CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Available Cobblemon fields:");
                for (java.lang.reflect.Field f : cobblemonClass.getFields()) {
                    CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper]   Field: {} -> {}", f.getName(), f.getType().getName());
                }
                throw new Exception("Could not find storage instance through any method");
            }

            CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Storage instance class: {}",
                storageInstance.getClass().getName());

            // Try to find getParty method - it may take ServerPlayer or Player
            Method[] methods = storageInstance.getClass().getMethods();
            for (Method m : methods) {
                if (m.getName().equals("getParty")) {
                    Class<?>[] params = m.getParameterTypes();
                    CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Found getParty method with params: {}",
                        java.util.Arrays.toString(params));
                    if (params.length == 1 && (
                            ServerPlayer.class.isAssignableFrom(params[0]) ||
                            params[0].getName().contains("Player"))) {
                        getPartyMethod = m;
                        break;
                    }
                }
            }

            if (getPartyMethod == null) {
                // List available methods on storage for debugging
                CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Available storage methods:");
                for (Method m : storageInstance.getClass().getMethods()) {
                    if (m.getName().toLowerCase().contains("party") || m.getName().toLowerCase().contains("get")) {
                        CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper]   {} params={}", m.getName(),
                            java.util.Arrays.toString(m.getParameterTypes()));
                    }
                }
                throw new Exception("Could not find getParty(ServerPlayer) method");
            }

            cobblemonAvailable = true;
            CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Initialized successfully - Cobblemon integration enabled");

        } catch (ClassNotFoundException e) {
            CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Cobblemon not found - party/move conditions will always return false");
            cobblemonAvailable = false;
        } catch (Exception e) {
            CobblemonMerchants.LOGGER.warn("[CobblemonPartyHelper] Failed to initialize: {}", e.getMessage());
            e.printStackTrace();
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

        CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Checking for move '{}' in player {}'s party",
            moveName, player.getName().getString());

        if (!cobblemonAvailable) {
            CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Cobblemon not available, returning false");
            return false;
        }

        try {
            // Get the player's party
            Object party = getPartyMethod.invoke(storageInstance, player);
            if (party == null) {
                CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Player has no party");
                return false;
            }

            CobblemonMerchants.LOGGER.debug("[CobblemonPartyHelper] Got party: {}", party.getClass().getName());

            // PartyStore implements Iterable<Pokemon>
            if (!(party instanceof Iterable<?>)) {
                CobblemonMerchants.LOGGER.warn("[CobblemonPartyHelper] Party is not iterable");
                return false;
            }

            // Iterate through party slots
            int pokemonCount = 0;
            for (Object pokemon : (Iterable<?>) party) {
                if (pokemon == null) {
                    continue;
                }
                pokemonCount++;

                String speciesName = getPokemonSpeciesName(pokemon);
                CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Checking Pokemon #{}: {}",
                    pokemonCount, speciesName != null ? speciesName : "Unknown");

                if (pokemonHasMove(pokemon, moveName)) {
                    CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Found move '{}' on {}!", moveName, speciesName);
                    return true;
                }
            }

            CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Checked {} Pokemon, move '{}' not found",
                pokemonCount, moveName);
            return false;

        } catch (Exception e) {
            CobblemonMerchants.LOGGER.warn("[CobblemonPartyHelper] Error checking party for move {}: {}",
                moveName, e.getMessage());
            e.printStackTrace();
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
            // Get the MoveSet from the Pokemon using getMoveSet()
            Method getMoveSetMethod = pokemon.getClass().getMethod("getMoveSet");
            Object moveSet = getMoveSetMethod.invoke(pokemon);

            if (moveSet == null) {
                CobblemonMerchants.LOGGER.debug("[CobblemonPartyHelper] Pokemon has null moveSet");
                return false;
            }

            // MoveSet implements Iterable<Move>
            if (!(moveSet instanceof Iterable<?>)) {
                CobblemonMerchants.LOGGER.debug("[CobblemonPartyHelper] MoveSet is not iterable, trying getMoves()");
                // Try getMoves() method
                try {
                    Method getMovesMethod = moveSet.getClass().getMethod("getMoves");
                    Object movesList = getMovesMethod.invoke(moveSet);
                    if (movesList instanceof Iterable<?>) {
                        moveSet = movesList;
                    }
                } catch (NoSuchMethodException ignored) {}
            }

            StringBuilder moveListDebug = new StringBuilder();
            for (Object move : (Iterable<?>) moveSet) {
                if (move == null) {
                    continue;
                }

                // Get the move name using getName()
                String slotMoveName = getMoveNameFromMove(move);
                if (slotMoveName != null) {
                    if (moveListDebug.length() > 0) moveListDebug.append(", ");
                    moveListDebug.append(slotMoveName);

                    // Compare case-insensitively and also try without underscores/spaces
                    String normalizedTarget = moveName.toLowerCase().replace("_", "").replace(" ", "");
                    String normalizedSlot = slotMoveName.toLowerCase().replace("_", "").replace(" ", "");

                    if (normalizedSlot.equals(normalizedTarget)) {
                        return true;
                    }
                }
            }

            CobblemonMerchants.LOGGER.info("[CobblemonPartyHelper] Pokemon moves: [{}]", moveListDebug);
            return false;

        } catch (Exception e) {
            CobblemonMerchants.LOGGER.warn("[CobblemonPartyHelper] Error checking Pokemon for move: {}", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Gets the move name from a Move object.
     */
    private static String getMoveNameFromMove(Object move) {
        try {
            // Try getName() first (standard getter)
            try {
                Method getNameMethod = move.getClass().getMethod("getName");
                Object name = getNameMethod.invoke(move);
                if (name != null) {
                    return name.toString();
                }
            } catch (NoSuchMethodException ignored) {}

            // Try accessing 'name' property directly (Kotlin style)
            try {
                java.lang.reflect.Field nameField = move.getClass().getDeclaredField("name");
                nameField.setAccessible(true);
                Object name = nameField.get(move);
                if (name != null) {
                    return name.toString();
                }
            } catch (NoSuchFieldException ignored) {}

            // Try via template (MoveTemplate)
            try {
                Method getTemplateMethod = move.getClass().getMethod("getTemplate");
                Object template = getTemplateMethod.invoke(move);
                if (template != null) {
                    Method templateNameMethod = template.getClass().getMethod("getName");
                    Object name = templateNameMethod.invoke(template);
                    if (name != null) {
                        return name.toString();
                    }
                }
            } catch (NoSuchMethodException ignored) {}

            // Log available methods for debugging
            CobblemonMerchants.LOGGER.debug("[CobblemonPartyHelper] Move class: {}, methods: {}",
                move.getClass().getName(),
                java.util.Arrays.stream(move.getClass().getMethods())
                    .map(Method::getName)
                    .filter(n -> n.contains("name") || n.contains("Name") || n.equals("get"))
                    .toList());

            return null;

        } catch (Exception e) {
            CobblemonMerchants.LOGGER.debug("[CobblemonPartyHelper] Error getting move name: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets the species name from a Pokemon object.
     */
    private static String getPokemonSpeciesName(Object pokemon) {
        try {
            // Try Pokemon.getSpecies().getName()
            Method getSpeciesMethod = pokemon.getClass().getMethod("getSpecies");
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
            CobblemonMerchants.LOGGER.debug("[CobblemonPartyHelper] Error getting species name: {}", e.getMessage());
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
            Object party = getPartyMethod.invoke(storageInstance, player);
            if (party == null) {
                return false;
            }

            for (Object pokemon : (Iterable<?>) party) {
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
            CobblemonMerchants.LOGGER.debug("[CobblemonPartyHelper] Error checking party for species {}: {}", speciesName, e.getMessage());
            return false;
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
            Object party = getPartyMethod.invoke(storageInstance, player);

            if (party == null) {
                return 0;
            }

            // Try to get size directly
            try {
                Method occupiedMethod = party.getClass().getMethod("occupied");
                Object size = occupiedMethod.invoke(party);
                if (size instanceof Integer) {
                    return (Integer) size;
                }
            } catch (NoSuchMethodException ignored) {}

            // Count manually
            int count = 0;
            for (Object pokemon : (Iterable<?>) party) {
                if (pokemon != null) {
                    count++;
                }
            }
            return count;

        } catch (Exception e) {
            CobblemonMerchants.LOGGER.debug("[CobblemonPartyHelper] Error getting party size: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Gets the first Pokemon with a specific move and returns its species name.
     * Used for variable substitution like <pokemon_name>.
     * @param player The player to check
     * @param moveName The move to look for
     * @return The species name of the first Pokemon with that move, or null if not found
     */
    public static String getFirstPokemonWithMove(ServerPlayer player, String moveName) {
        if (!initialized) {
            initialize();
        }

        if (!cobblemonAvailable) {
            return null;
        }

        try {
            Object party = getPartyMethod.invoke(storageInstance, player);
            if (party == null) {
                return null;
            }

            for (Object pokemon : (Iterable<?>) party) {
                if (pokemon == null) {
                    continue;
                }

                if (pokemonHasMove(pokemon, moveName)) {
                    return getPokemonSpeciesName(pokemon);
                }
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }
}
