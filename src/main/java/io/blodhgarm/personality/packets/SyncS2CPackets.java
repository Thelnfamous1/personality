package io.blodhgarm.personality.packets;

import com.mojang.logging.LogUtils;
import io.blodhgarm.personality.api.addon.AddonRegistry;
import io.blodhgarm.personality.api.addon.BaseAddon;
import io.blodhgarm.personality.api.character.Character;
import io.blodhgarm.personality.api.utils.PlayerAccess;
import io.blodhgarm.personality.client.ClientCharacters;
import io.blodhgarm.personality.client.gui.screens.AdminCharacterScreen;
import io.wispforest.owo.network.ClientAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

public class SyncS2CPackets {

    public static final Logger LOGGER = LogUtils.getLogger();

    public record Initial(List<CharacterData> characters, Map<String, String> associations, boolean loadBaseRegistries) {

        public Initial(Map<String, Map<Identifier, String>> characters, Map<String, String> associations, boolean loadBaseRegistries){
            this(characters.entrySet().stream().map(entry -> new CharacterData(entry.getKey(), entry.getValue())).toList(), associations, loadBaseRegistries);
        }

    }

    public record CharacterData(String characterData, Map<Identifier, String> addonData){}

    /**
     * Packet used to sync either just base Character information plus any addon data
     */
    public record SyncCharacterData(String characterJson, Map<Identifier, String> addonData) {

    }

    public record SyncAddonData(String characterUUID, Map<Identifier, String> addonData){

        @Environment(EnvType.CLIENT)
        public static void syncAddons(SyncAddonData message, ClientAccess access) {
            Character c = ClientCharacters.INSTANCE.getCharacter(message.characterUUID());

            if(c == null){
                LOGGER.error("[SyncAddons] It seems that there was no Character [uuid: {}] to sync addons with, such will be ignored.", message.characterUUID());

                return;
            }

            Map<Identifier, BaseAddon> addonMap = AddonRegistry.INSTANCE.deserializesAddons(c, message.addonData, false);

            if(message.addonData.size() != addonMap.size()){
                LOGGER.warn("[SyncAddons]: Something within the addon loading process has gone wrong leading to a mismatch in addon data!");
            }

            //Return early as the loading process hasn't found any valid addons when deserializing
            if(addonMap.isEmpty()) return;

            c.getAddons().putAll(addonMap);

            PlayerAccess<AbstractClientPlayerEntity> playerAccess = ClientCharacters.INSTANCE.getPlayerFromCharacter(c);

            if(playerAccess.playerValid(access.player()::equals)) {
                ClientCharacters.INSTANCE.applyAddons(playerAccess.player());
            }

            if(MinecraftClient.getInstance().currentScreen instanceof AdminCharacterScreen s) s.shouldAttemptUpdate(c);
        }
    }

    public record RemoveCharacter(String characterUUID) {
        @Environment(EnvType.CLIENT)
        public static void removeCharacter(RemoveCharacter message, ClientAccess access) {
            ClientCharacters.INSTANCE.removeCharacter(message.characterUUID);
        }
    }

    public record Association(String characterUUID, String newPlayerUUID) {
        @Environment(EnvType.CLIENT)
        public static void syncAssociation(Association message, ClientAccess access) {
            ClientCharacters.INSTANCE.associateCharacterToPlayer(message.characterUUID, message.newPlayerUUID);
        }
    }

    public record Dissociation(String uuid, boolean characterUUID) {
        @Environment(EnvType.CLIENT)
        public static void syncDissociation(Dissociation message, ClientAccess access) {
            ClientCharacters.INSTANCE.dissociateUUID(message.uuid, message.characterUUID);

            AddonRegistry.INSTANCE.checkAndDefaultPlayerAddons(access.player());
        }
    }

    public record SyncOnlinePlaytimes(String jsonData){
    }

}
