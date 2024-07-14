package io.blodhgarm.personality.api.core;

import io.blodhgarm.personality.api.character.BaseCharacter;
import io.blodhgarm.personality.api.character.Character;
import io.blodhgarm.personality.api.reveal.KnownCharacter;
import net.minecraft.entity.player.PlayerEntity;

import org.jetbrains.annotations.Nullable;


/**
 * Main interface used on the Client to access and manipulate the KnownCharacter Cache.
 */
public interface KnownCharacterLookup {

    /**
     * Method used to add a known character to the given Lookup
     *
     * @param wrappedC The given Character to be put within the Map
     */
    void addKnownCharacter(KnownCharacter wrappedC);
    /**
     * Method used to remove a known character from the current Lookup
     */
    void removeKnownCharacter(String UUID);

    /**
     * @return Whether the given Character is known within the Lookup Map
     */
    default boolean doseKnowCharacter(Character character){
        return doseKnowCharacter(character.getUUID(), false);
    }

    /**
     * @return Whether the given Player is known within the Lookup Map
     */
    default boolean doseKnowCharacter(PlayerEntity player){
        return doseKnowCharacter(player.getUuidAsString(), true);
    }

    /**
     * Method used to get the possible KnownCharacter linked to the given Player uuid
     *
     * @param UUID The UUID being searched
     * @param isPlayerUUID Whether the UUID is of a player or Character
     * @return Whether the given UUID is known within the Lookup Map
     */
    boolean doseKnowCharacter(String UUID, boolean isPlayerUUID);

    /**
     * @return The {@link KnownCharacter} if found to be within the Lookup Map using the Player
     */
    @Nullable
    default BaseCharacter getKnownCharacter(PlayerEntity player){
        return getKnownCharacter(player.getUuid().toString(), true);
    }

    /**
     * @return The {@link KnownCharacter} if found to be within the Lookup Map using the Character
     */
    @Nullable
    default KnownCharacter getKnownCharacter(Character character){
        return getKnownCharacter(character.getUUID(), false);
    }

    /**
     * Method used to get the possible KnownCharacter linked to the given Player uuid
     *
     * @param UUID The UUID being searched
     * @param isPlayerUUID Whether the UUID is of a player or Character
     * @return The {@link KnownCharacter} if found to be known within the Lookup
     */
    @Nullable KnownCharacter getKnownCharacter(String UUID, boolean isPlayerUUID);

    /**
     * @return The Owner of all the given Known Characters within the Lookup
     */
    @Nullable Character getOwnerCharacter();
}
