package io.blodhgarm.personality.api.character;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import io.blodhgarm.personality.api.addon.AddonRegistry;
import io.blodhgarm.personality.api.reveal.RevelInfoManager;
import io.blodhgarm.personality.api.utils.PlayerAccess;
import io.blodhgarm.personality.client.ClientCharacters;
import io.blodhgarm.personality.utils.DebugCharacters;
import io.blodhgarm.personality.utils.ReflectionUtils;
import io.blodhgarm.personality.utils.gson.ExtraTokenData;
import io.blodhgarm.personality.utils.gson.WrappedTypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.JsonHelper;
import net.minecraft.world.World;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import org.jetbrains.annotations.Nullable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Base interface for storing and managing Character data
 * @param <P> Is a version of player either ClientPlayerEntity or {@link ServerPlayerEntity}
 */
public abstract class CharacterManager<P extends PlayerEntity, C extends Character> implements RevelInfoManager<P> {

    protected static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, CharacterManager<?, ?>> MANAGER_REGISTRY = new HashMap<>();

    protected BiMap<String, String> playerIDToCharacterID = HashBiMap.create();
    protected ListOrderedMap<String, Character> characterIDToCharacter = new ListOrderedMap<>();

    public CharacterManager(String mangerId){
        MANAGER_REGISTRY.put(mangerId, this);
    }

    /**
     * @return The Character of the Client in which used only in {@link ClientCharacters}
     */
    @Nullable
    public Character getClientCharacter() {
        return null;
    }

    //-----------------------------------------------------------------------------------------

    @Nullable
    public static <P extends PlayerEntity> CharacterManager<P, Character> getManger(PlayerEntity entity){
        return entity.getWorld() != null ? getManger(entity.getWorld()) : null;
    }

    @Nullable
    public static <P extends PlayerEntity> CharacterManager<P, Character> getManger(World world){
        return getManger(world.isClient() ? "client" : "server");
    }

    @Nullable
    public static <P extends PlayerEntity> CharacterManager<P, Character> getManger(String managerId){
        return (CharacterManager<P, Character>) MANAGER_REGISTRY.get(managerId);
    }

    //-----------------------------------------------------------------------------------------

    /**
     * Map that holds the reference of player and character
     */
    @NotNull
    public BiMap<String, String> playerToCharacterReferences(){
        return playerIDToCharacterID;
    }

    /**
     * A Map holding the Character Reference to its uuid
     */
    @NotNull
    public ListOrderedMap<String, Character> characterLookupMap(){
        return characterIDToCharacter;
    }

    @ApiStatus.Internal
    protected void clearRegistries(){
        this.characterIDToCharacter.clear();
        this.playerIDToCharacterID.clear();
    }

    /**
     * Method to sort the {@link #characterIDToCharacter} Map via some reflection hackyness
     */
    @ApiStatus.Internal
    public void sortCharacterLookupMap(){
        ReflectionUtils.getMapInsertOrder(characterLookupMap())
                .sort(Comparator.comparing(o -> getCharacter(o).getName(), Comparator.naturalOrder()));
    }

    /**
     * Attempt to get the Character based on the player's possible character reference uuid
     *
     * @param player The Possible Player for a Character
     * @return If a Character for a player or null if none are found
     */
    @Nullable
    public Character getCharacter(P player){
        String cUUID = null;

        if(player != null) cUUID = getCharacterUUID(player.getUuid().toString());

        return cUUID != null ? getCharacter(cUUID) : null;
    }

    /**
     * Attempt to get the Character based on the player's uuid
     *
     * @param cUUID The Possible uuid of a Character
     * @return A Character linked to the given uuid
     */
    @Nullable
    public Character getCharacter(String cUUID){
        Character character = characterLookupMap().get(cUUID);

        if(FabricLoader.getInstance().isDevelopmentEnvironment() && character == null){
            character = DebugCharacters.DEBUG_CHARACTERS_MAP.get(cUUID);
        }

        return character;
    }

    /**
     * Attempt to get the Character based on the player's uuid
     *
     * @param pUUID The Possible uuid of a player for a Character
     * @return If a Character for a player or null if none are found
     */
    public String getCharacterUUID(String pUUID){
        return playerToCharacterReferences().getOrDefault(pUUID, "INVALID");
    }

    /**
     * @return if such player has an associated Character
     */
    public boolean hasCharacter(P player){
        return this.hasCharacter(player.getUuidAsString());
    }

    /**
     * @return if such player's UUID has an associated Character
     */
    public boolean hasCharacter(String pUUID){
        return !Objects.equals(getCharacterUUID(pUUID), "INVALID");
    }

    @ApiStatus.Internal
    public C deserializeCharacter(String json){
        return deserializeCharacter(json, null);
    }

    @ApiStatus.Internal
    public C deserializeCharacter(String json, @Nullable ExtraTokenData data){
        return (C) JsonHelper.deserialize(getGson(), json, getToken().setExtraData(data))
                .setCharacterManager(this);
    }

    @ApiStatus.Internal
    protected abstract WrappedTypeToken<C> getToken();

    @ApiStatus.Internal
    public abstract Gson getGson();

    //----

    /**
     * Attempt to get the Player based on the Character
     *
     * @param c The Character that may be connected to a player
     * @return A Player if a Character is associated to such or null if none are found
     */
    public PlayerAccess<P> getPlayerFromCharacter(Character c){
        return c == null ? (PlayerAccess<P>) PlayerAccess.EMPTY : getPlayerFromCharacter(c.getUUID());
    }

    /**
     * Attempt to get the Player based on the Characters uuid
     *
     * @param cUUID A characters uuid
     * @return A Player if a Character is associated to such or null if none are found
     */
    public PlayerAccess<P> getPlayerFromCharacter(String cUUID){
        return getPlayer(this.playerIDToCharacterID.inverse().get(cUUID));
    }

    public PlayerAccess<P> getPlayer(@Nullable String pUUID){
        return (PlayerAccess<P>) PlayerAccess.EMPTY;
    }

    //----

    /**
     * Attempt to get the Player's uuid based on the Character
     *
     * @param c A character
     * @return A Player's uuid if a Character is associated to such or null if none are found
     */
    @Nullable
    public String getPlayerUUID(Character c){
        return c == null ? null : getPlayerUUID(c.getUUID());
    }

    /**
     * Attempt to get the Player's uuid based on the Character's uuid
     *
     * @param cUUID A character's uuid
     * @return A Player's uuid if a Character is associated to such or null if none are found
     */
    @Nullable
    public String getPlayerUUID(String cUUID){
        return playerToCharacterReferences().inverse().get(cUUID);
    }


    /**
     * Method used to associate a Player to a given character within the Reference Map
     *
     * @param cUUID Characters uuid
     * @param playerUUID Player uuid
     *
     * @return if the character was found within the {@link #characterLookupMap()}
     */
    public boolean associateCharacterToPlayer(String cUUID, String playerUUID){
        if(!characterLookupMap().containsKey(cUUID)) return false;

        playerToCharacterReferences().put(playerUUID, cUUID);

        PlayerAccess<P> playerAccess = getPlayerFromCharacter(cUUID);

        if(playerAccess.player() != null) applyAddons(playerAccess.player());

        return true;
    }

    /**
     * Method used to apply addons to the given player if they have a character
     */
    public void applyAddons(P player){
        Character c = getCharacter(player);

        if(c != null){
            c.getAddons().values().forEach(addon -> {
                if(!addon.isEqualToPlayer(player) && addon.getAddonEnvironment().shouldApply(player.getWorld())){
                    addon.applyAddon(player);
                }
            });

            c.getKnownCharacters().forEach((s, knownCharacter) -> knownCharacter.setCharacterManager(this));
        } else  {
            AddonRegistry.INSTANCE.checkAndDefaultPlayerAddons(player);
        }
    }

    /**
     * Remove the given uuid (Player or Character) from the Player Character Reference Map
     *
     * @return Player uuid removed if found within the map
     */
    @Nullable
    public String dissociateUUID(String UUID, boolean isCharacterUUID){
        BiMap<String, String> map = isCharacterUUID ? playerToCharacterReferences().inverse() : playerToCharacterReferences();

        if(!map.containsKey(UUID)) return null;

        String valueRemoved = map.remove(UUID);

        return isCharacterUUID ? valueRemoved : UUID;
    }

    /**
     * Method will dissociate the given Character uuid and remove it from the main lookup map
     *
     * @param cUUID Selected Character uuid as a String
     */
    public void removeCharacter(String cUUID){
        dissociateUUID(cUUID, true);
        characterLookupMap().remove(cUUID);
    }

}
