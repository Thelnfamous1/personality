package io.blodhgarm.personality.packets;

import com.mojang.authlib.GameProfile;
import io.blodhgarm.personality.PersonalityMod;
import io.blodhgarm.personality.client.ClientCharacters;
import io.blodhgarm.personality.misc.pond.CharacterToPlayerLink;
import io.blodhgarm.personality.server.ServerCharacterTick;
import io.wispforest.owo.network.ClientAccess;
import io.wispforest.owo.network.ServerAccess;
import io.wispforest.owo.ui.component.EntityComponent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;


public class CharacterDeathPackets {

    public record CheckDeathScreenOpen(){}

    public record OpenCharacterDeathScreen() {}

    public record ReceivedDeathMessage(String playerUUID, String deathMessage, boolean tripped){
        @Environment(EnvType.CLIENT)
        public static void outputCustomDeathMessage(ReceivedDeathMessage message, ClientAccess access){
            DamageSource source = ServerCharacterTick.getSource(access.player(), message.tripped, message.deathMessage);

            GameProfile profile = ClientCharacters.INSTANCE.getPlayer(message.playerUUID).getProfile(MinecraftClient.getInstance().getSessionService());

            PlayerEntity entity = ((CharacterToPlayerLink) EntityComponent.createRenderablePlayer(profile))
                    .toggleOnlyCharacterName(!PersonalityMod.CONFIG.showPlayerNameInChat());

            access.player().sendMessage(source.getDeathMessage(entity), false);
        }
    }

    public record DeathScreenOpenResponse(boolean isOpen){
        public static void setIfScreenOpen(DeathScreenOpenResponse message, ServerAccess access){
            if(!message.isOpen()) ServerCharacterTick.hasDeathScreenOpen.put(access.player().getUuidAsString(), false);
        }
    }

    public record CustomDeathMessage(String message){
        public static void useCustomDeathMessage(CustomDeathMessage message, ServerAccess access){
            ServerCharacterTick.killCharacter(access.player().getWorld(), access.player().getUuidAsString(), message.message);
        }
    }

}
