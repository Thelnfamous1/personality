package io.blodhgarm.personality.client.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.blodhgarm.personality.Networking;
import io.blodhgarm.personality.PersonalityMod;
import io.blodhgarm.personality.api.addon.AddonRegistry;
import io.blodhgarm.personality.api.addon.BaseAddon;
import io.blodhgarm.personality.api.character.Character;
import io.blodhgarm.personality.api.core.BaseRegistry;
import io.blodhgarm.personality.api.utils.PlayerAccess;
import io.blodhgarm.personality.client.ClientCharacters;
import io.blodhgarm.personality.client.gui.screens.AdminCharacterScreen;
import io.blodhgarm.personality.client.gui.screens.CharacterDeathScreen;
import io.blodhgarm.personality.packets.CharacterDeathPackets;
import io.blodhgarm.personality.packets.SyncC2SPackets;
import io.blodhgarm.personality.packets.SyncS2CPackets;
import io.blodhgarm.personality.server.ServerCharacters;
import io.blodhgarm.personality.utils.CharacterReferenceData;
import io.wispforest.owo.network.ClientAccess;
import net.fabricmc.loader.impl.util.StringUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;

public class ClientNetworkHandler {
    public static void handleCheckDeathScreenOpen(CharacterDeathPackets.CheckDeathScreenOpen m, ClientAccess a) {
        Networking.sendC2S(new CharacterDeathPackets.DeathScreenOpenResponse(a.runtime().currentScreen instanceof CharacterDeathScreen));
    }

    public static void handleOpenCharacterDeathScreen(CharacterDeathPackets.OpenCharacterDeathScreen m, ClientAccess a) {
        if (MinecraftClient.getInstance().player.getCharacter(false) != null){
            a.runtime().setScreen(new CharacterDeathScreen());

            Networking.sendC2S(new CharacterDeathPackets.DeathScreenOpenResponse(true));
        }
    }

    public static void handleServerCharactersReturnInformation(ServerCharacters.ReturnInformation message, ClientAccess access) {
        MinecraftClient client = access.runtime();

        client.getToastManager().add(
                SystemToast.create(client, SystemToast.Type.PERIODIC_NOTIFICATION, Text.of(StringUtil.capitalize(message.action())), Text.of(message.returnMessage()))
        );

        if(message.success()){
            if(Networking.adminActions.contains(message.action()) && MinecraftClient.getInstance().currentScreen instanceof AdminCharacterScreen screen){
                screen.clearSelectedEntries();
            }

            Networking.LOGGER.info("Action: {}, Message: {}", message.action(), message.returnMessage());
        } else {
            Networking.LOGGER.error("Action: {}, Message: {}", message.action(), message.returnMessage());
        }
    }

    public static void initialSync(SyncS2CPackets.Initial message, ClientAccess access) {
        if(message.loadBaseRegistries()){
            PersonalityMod.loadRegistries("InitialSync");

            //This may be a faulty approach to confirming registries are synced due to its placement as if the packet is lost, it would allow the client to connect and issues may happen
            Networking.sendC2S(SyncC2SPackets.RegistrySync.of(BaseRegistry.REGISTRIES));
        }

        ClientCharacters.INSTANCE.init(message.characters(), message.associations());

        ClientCharacters.INSTANCE.applyAddons(access.player());
    }

    public static void syncCharacter(SyncS2CPackets.SyncCharacterData message, ClientAccess access) {
        Character c = ClientCharacters.INSTANCE.deserializeCharacter(message.characterJson());

        c.setCharacterManager(ClientCharacters.INSTANCE);

        boolean addonDataDeserialized = false;

        Character oldCharacter = ClientCharacters.INSTANCE.getCharacter(c.getUUID());

        if(!message.addonData().isEmpty()) {
            Map<Identifier, BaseAddon> addonMap = AddonRegistry.INSTANCE.deserializesAddons(c, message.addonData(), false);

            if(message.addonData().size() != addonMap.size()){
                SyncS2CPackets.LOGGER.warn("[SyncCharacter]: Something within the addon loading process has gone wrong leading to a mismatch in addon data!");
            }

            if(!addonMap.isEmpty()){
                c.getAddons().putAll(addonMap);

                addonDataDeserialized = true;
            }
        } else if(oldCharacter != null) {
            c.getAddons().putAll(oldCharacter.getAddons());
        }

        if(!c.equals(oldCharacter)) {
            ClientCharacters.INSTANCE.characterLookupMap().put(c.getUUID(), c);

            ClientCharacters.INSTANCE.sortCharacterLookupMap();
        }

        PlayerAccess<AbstractClientPlayerEntity> playerAccess = ClientCharacters.INSTANCE.getPlayerFromCharacter(c);

        boolean shouldApplyAddons = playerAccess.playerValid(access.player()::equals) && addonDataDeserialized;

        if(shouldApplyAddons) ClientCharacters.INSTANCE.applyAddons(playerAccess.player());

        c.getKnownCharacters().forEach((s, knownCharacter) -> knownCharacter.setCharacterManager(ClientCharacters.INSTANCE));

        if(MinecraftClient.getInstance().currentScreen instanceof AdminCharacterScreen s) s.shouldAttemptUpdate(c);
    }

    public static void syncOnlinePlaytimes(SyncS2CPackets.SyncOnlinePlaytimes message, ClientAccess access){
        ClientCharacters.INSTANCE.getGson().fromJson(message.jsonData(), JsonArray.class).forEach(e -> {
            if(!(e instanceof JsonObject o && o.has("uuid"))) return;

            ClientCharacters.INSTANCE.deserializeCharacter(
                    o.toString(),
                    new CharacterReferenceData(ClientCharacters.INSTANCE, o.remove("uuid").getAsString())
            );
        });
    }
}
