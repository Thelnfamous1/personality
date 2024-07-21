package io.blodhgarm.personality;

import com.mojang.logging.LogUtils;
import io.blodhgarm.personality.client.network.ClientNetworkHandler;
import io.blodhgarm.personality.packets.*;
import io.blodhgarm.personality.server.ServerCharacters;
import io.wispforest.owo.Owo;
import io.wispforest.owo.network.OwoNetChannel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

import java.util.List;

public class Networking {

    public static Logger LOGGER = LogUtils.getLogger();

    public static List<String> adminActions = List.of(
            "associate",
            "disassociate",
            "create",
            "edit",
            "revive",
            "kill",
            "delete"
    );

    public static final OwoNetChannel CHANNEL = OwoNetChannel.create(new Identifier("personality", "main"));

    public static void registerNetworking(){
        //S2C - Server to Client
        CHANNEL.registerClientboundDeferred(OpenPersonalityScreenS2CPacket.class);

        CHANNEL.registerClientboundDeferred(SyncS2CPackets.Initial.class);
        CHANNEL.registerClientboundDeferred(SyncS2CPackets.SyncCharacterData.class);
        CHANNEL.registerClientboundDeferred(SyncS2CPackets.SyncAddonData.class);
        CHANNEL.registerClientboundDeferred(SyncS2CPackets.RemoveCharacter.class);
        CHANNEL.registerClientboundDeferred(SyncS2CPackets.Association.class);
        CHANNEL.registerClientboundDeferred(SyncS2CPackets.Dissociation.class);

        CHANNEL.registerClientboundDeferred(ServerCharacters.ReturnInformation.class);

        CHANNEL.registerClientboundDeferred(SyncS2CPackets.SyncOnlinePlaytimes.class);

        CHANNEL.registerClientboundDeferred(CharacterDeathPackets.OpenCharacterDeathScreen.class);

        CHANNEL.registerClientboundDeferred(CharacterDeathPackets.CheckDeathScreenOpen.class);

        CHANNEL.registerClientboundDeferred(CharacterDeathPackets.ReceivedDeathMessage.class);

        CHANNEL.registerClientboundDeferred(RevealCharacterPackets.SuccessfulDiscovery.class);

        //C2S - Client to Server
        CHANNEL.registerServerbound(SyncC2SPackets.ModifyBaseCharacterData.class, SyncC2SPackets.ModifyBaseCharacterData::modifyCharacter);
        CHANNEL.registerServerbound(SyncC2SPackets.ModifyAddonData.class, SyncC2SPackets.ModifyAddonData::modifyAddons);
        CHANNEL.registerServerbound(SyncC2SPackets.ModifyEntireCharacter.class, SyncC2SPackets.ModifyEntireCharacter::modifyEntireCharacter);

        CHANNEL.registerServerbound(SyncC2SPackets.NewCharacter.class, SyncC2SPackets.NewCharacter::newCharacter);

        CHANNEL.registerServerbound(SyncC2SPackets.RegistrySync.class, SyncC2SPackets.RegistrySync::registriesSync);

        CHANNEL.registerServerbound(RevealCharacterPackets.RevealByInfoLevel.class, RevealCharacterPackets.RevealByInfoLevel::revealInformationToPlayers);
        CHANNEL.registerServerbound(RevealCharacterPackets.RevealByLooking.class, RevealCharacterPackets.RevealByLooking::doseRevealCharacter);

        CHANNEL.registerServerbound(AdminActionPackets.AssociateAction.class, AdminActionPackets.AssociateAction::attemptAssociateAction);
        CHANNEL.registerServerbound(AdminActionPackets.DisassociateAction.class, AdminActionPackets.DisassociateAction::attemptDisassociateAction);

        CHANNEL.registerServerbound(AdminActionPackets.EditAction.class, AdminActionPackets.EditAction::attemptEditAction);

        CHANNEL.registerServerbound(AdminActionPackets.CharacterBasedAction.class, AdminActionPackets.CharacterBasedAction::attemptAction);

        CHANNEL.registerServerbound(CharacterDeathPackets.DeathScreenOpenResponse.class, CharacterDeathPackets.DeathScreenOpenResponse::setIfScreenOpen);

        CHANNEL.registerServerbound(CharacterDeathPackets.CustomDeathMessage.class, CharacterDeathPackets.CustomDeathMessage::useCustomDeathMessage);
    }

    @Environment(EnvType.CLIENT)
    public static void registerNetworkingClient(){
        //S2C - Server to Client
        CHANNEL.registerClientbound(OpenPersonalityScreenS2CPacket.class, OpenPersonalityScreenS2CPacket::openScreen);

        CHANNEL.registerClientbound(SyncS2CPackets.Initial.class, ClientNetworkHandler::initialSync);
        CHANNEL.registerClientbound(SyncS2CPackets.SyncCharacterData.class, ClientNetworkHandler::syncCharacter);
        CHANNEL.registerClientbound(SyncS2CPackets.SyncAddonData.class, SyncS2CPackets.SyncAddonData::syncAddons);
        CHANNEL.registerClientbound(SyncS2CPackets.RemoveCharacter.class, SyncS2CPackets.RemoveCharacter::removeCharacter);
        CHANNEL.registerClientbound(SyncS2CPackets.Association.class, SyncS2CPackets.Association::syncAssociation);
        CHANNEL.registerClientbound(SyncS2CPackets.Dissociation.class, SyncS2CPackets.Dissociation::syncDissociation);

        CHANNEL.registerClientbound(ServerCharacters.ReturnInformation.class, ClientNetworkHandler::handleServerCharactersReturnInformation);

        CHANNEL.registerClientbound(SyncS2CPackets.SyncOnlinePlaytimes.class, ClientNetworkHandler::syncOnlinePlaytimes);

        CHANNEL.registerClientbound(CharacterDeathPackets.OpenCharacterDeathScreen.class, ClientNetworkHandler::handleOpenCharacterDeathScreen);

        CHANNEL.registerClientbound(CharacterDeathPackets.CheckDeathScreenOpen.class, ClientNetworkHandler::handleCheckDeathScreenOpen);

        CHANNEL.registerClientbound(CharacterDeathPackets.ReceivedDeathMessage.class, CharacterDeathPackets.ReceivedDeathMessage::outputCustomDeathMessage);

        CHANNEL.registerClientbound(RevealCharacterPackets.SuccessfulDiscovery.class, RevealCharacterPackets.SuccessfulDiscovery::onDiscovery);
    }

    public static <R extends Record> void sendC2S(R packet) {
        CHANNEL.clientHandle().send(packet);
    }

    public static <R extends Record> void sendToAll(R packet) {
        CHANNEL.serverHandle(Owo.currentServer()).send(packet);
    }

    public static <R extends Record> void sendS2C(PlayerEntity player, R packet) {
        CHANNEL.serverHandle(player).send(packet);
    }


}
