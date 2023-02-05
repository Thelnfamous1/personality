package io.blodhgarm.personality.client;

import com.google.common.collect.HashBiMap;
import io.blodhgarm.personality.PersonalityMod;
import io.blodhgarm.personality.api.character.BaseCharacter;
import io.blodhgarm.personality.api.character.Character;
import io.blodhgarm.personality.api.character.CharacterManager;
import io.blodhgarm.personality.api.utils.PlayerAccess;
import io.blodhgarm.personality.api.addon.AddonRegistry;
import io.blodhgarm.personality.api.core.KnownCharacterLookup;
import io.blodhgarm.personality.api.reveal.InfoRevealLevel;
import io.blodhgarm.personality.api.reveal.KnownCharacter;
import io.blodhgarm.personality.packets.SyncS2CPackets;
import io.blodhgarm.personality.utils.DebugCharacters;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public class ClientCharacters extends CharacterManager<AbstractClientPlayerEntity> implements KnownCharacterLookup {

    public static ClientCharacters INSTANCE = new ClientCharacters();

    public Map<String, BaseCharacter> clientKnownCharacterMap = new HashMap<>();

    public ClientCharacters() {
        super("client");

        CharacterManager.getClientCharacterFunc = () -> this.getCharacter(MinecraftClient.getInstance().player);
    }

    public PlayerAccess<AbstractClientPlayerEntity> getPlayer(String uuid) {
        String playerUUID = playerToCharacterReferences().inverse().get(uuid);

        if(playerUUID != null) {
            AbstractClientPlayerEntity player = MinecraftClient.getInstance().world.getPlayers()
                    .stream()
                    .filter(abstractClientPlayerEntity -> Objects.equals(abstractClientPlayerEntity.getUuidAsString(), playerUUID)) //playerUUID
                    .findFirst()
                    .orElse(null);

            return new PlayerAccess<>(playerUUID, player);
        }

        return super.getPlayer(uuid);
    }

    public void init(List<SyncS2CPackets.CharacterData> characters, Map<String, String> associations) {
        playerIDToCharacterID = HashBiMap.create(associations);
        characterIDToCharacter.clear();

        for (SyncS2CPackets.CharacterData entry : characters) {
            Character c = PersonalityMod.GSON.fromJson(entry.characterData(), Character.class);

            AddonRegistry.INSTANCE.deserializesAddons(c, entry.addonData());

            characterIDToCharacter.put(c.getUUID(), c);
        }

        if(FabricLoader.getInstance().isDevelopmentEnvironment()) DebugCharacters.loadDebugCharacters(this);

        clientKnownCharacterMap.clear();
    }

    @Override
    public void revealCharacterInfo(AbstractClientPlayerEntity source, Collection<AbstractClientPlayerEntity> targets, InfoRevealLevel level) {}

    @Override
    public Consumer<AbstractClientPlayerEntity> revealCharacterInfo(Character source, Character targetCharacter, InfoRevealLevel level) { return target -> {}; }

    @Override
    public void associateCharacterToPlayer(String cUUID, String playerUUID) {
        super.associateCharacterToPlayer(cUUID, playerUUID);

        PlayerAccess<AbstractClientPlayerEntity> playerAssociated = this.getPlayer(playerUUID);

        setKnownCharacters(playerAssociated, cUUID);
    }

    public void setKnownCharacters(PlayerAccess<AbstractClientPlayerEntity> playerAccess, String cUUID){
        Character clientC = this.getCharacter(MinecraftClient.getInstance().player);

        if(playerAccess != null && clientC != null) {
            if(playerAccess.player() != null && playerAccess.player() == MinecraftClient.getInstance().player) {
                clientC.getKnownCharacters().forEach((s, knownCharacter) -> {
                    knownCharacter.setCharacterManager(this);

                    PlayerAccess<AbstractClientPlayerEntity> otherP = this.getPlayer(knownCharacter.getWrappedCharacter());

                    if (otherP.valid()) {
                        this.addKnownCharacter(otherP.UUID(), knownCharacter);
                    }
                });
            } else {
                if (clientC.getKnownCharacters().containsKey(cUUID)) {
                    KnownCharacter knownCharacter = clientC.getKnownCharacters().get(cUUID);

                    knownCharacter.setCharacterManager(this);

                    this.addKnownCharacter(playerAccess.UUID(), knownCharacter);
                }
            }
        }
    }

    @Override
    public String dissociateUUID(String UUID, boolean isCharacterUUID) {
        PlayerAccess<AbstractClientPlayerEntity> playerDissociated;
        Character oldC;

        if(isCharacterUUID){
            oldC = this.getCharacter(UUID);
            playerDissociated = this.getPlayer(oldC);
        } else {
            playerDissociated = this.getPlayer(this.getCharacterUUID(UUID));
            oldC = this.getCharacter(this.getCharacterUUID(playerDissociated.UUID()));
        }

        revokeKnownCharacters(playerDissociated, oldC);

        return super.dissociateUUID(UUID, isCharacterUUID);
    }

    public void revokeKnownCharacters(PlayerAccess<AbstractClientPlayerEntity> playerAccess, Character oldC){
        Character clientC = this.getCharacter(MinecraftClient.getInstance().player);

        if(playerAccess != null && clientC != null) {
            if(playerAccess.player() != null && playerAccess.player() == MinecraftClient.getInstance().player) {
                clientC.getKnownCharacters().forEach((s, knownCharacter) -> {
                    PlayerAccess<AbstractClientPlayerEntity> otherP = this.getPlayer(knownCharacter.getWrappedCharacter());

                    if (otherP.valid()) this.removeKnownCharacter(otherP.UUID());
                });
            } else {
                if (oldC != null && this.clientKnownCharacterMap.containsKey(playerAccess.UUID())) {

                    this.removeKnownCharacter(playerAccess.UUID());
                }
            }
        }
    }

    @Override
    public void addKnownCharacter(String playerUUID, BaseCharacter character) {
        this.clientKnownCharacterMap.put(playerUUID, character);
    }

    @Override
    public void removeKnownCharacter(String playerUUID) {
        this.clientKnownCharacterMap.remove(playerUUID);
    }

    @Override
    @Nullable
    public BaseCharacter getKnownCharacter(String UUID) {
        return this.clientKnownCharacterMap.get(UUID);
    }
}
