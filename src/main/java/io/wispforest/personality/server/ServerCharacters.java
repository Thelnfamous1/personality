package io.wispforest.personality.server;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.wispforest.personality.Character;
import io.wispforest.personality.Networking;
import io.wispforest.personality.packets.SyncS2CPackets;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ServerCharacters {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type REF_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static Path CHARACTER_PATH = FabricLoader.getInstance().getGameDir();
    private static Path REFERENCE_PATH = CHARACTER_PATH.resolve("reference.json");

    public static BiMap<String, String> playerIDToCharacterID = HashBiMap.create();
    public static Map<String, Character> characterIDToCharacter = new HashMap<>();

    @Nullable
    public static Character getCharacter(ServerPlayerEntity player) {
        return getCharacter(playerIDToCharacterID.get(player.getUuidAsString()));
    }

    @Nullable
    public static Character getCharacter(String uuid) {
        Character c = characterIDToCharacter.get(uuid);
        if (c != null)
            return c;

        try {
            Path path = getPath(uuid);
            if (!Files.exists(path))
                return null;

            String characterJson = Files.readString(path);
            c = GSON.fromJson(characterJson, Character.class);
            characterIDToCharacter.put(uuid, c);
            Networking.sendToAll(new SyncS2CPackets.SyncCharacter(characterJson));

            return c;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    public static ServerPlayerEntity getPlayer(Character c) {
        return getPlayer(c.getInfo());
    }

    @Nullable
    public static ServerPlayerEntity getPlayer(String uuid) {
        return PersonalityServer.server.getPlayerManager().getPlayer(uuid);
    }

    @Nullable
    public static String getPlayerUUID(Character c) {
        return getPlayerUUID(c.getUUID());
    }

    @Nullable
    public static String getPlayerUUID(String uuid) {
        return playerIDToCharacterID.inverse().get(uuid);
    }

    public static void saveCharacter(Character character) {
        String characterJson = GSON.toJson(character);
        Networking.sendToAll(new SyncS2CPackets.SyncCharacter(characterJson));
        try {
            Files.writeString(getPath(character.getUUID()), characterJson);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteCharacter(Character character) {
        deleteCharacter(character.getUUID());
    }

    public static void deleteCharacter(String uuid) {
        playerIDToCharacterID.inverse().remove(uuid);
        characterIDToCharacter.remove(uuid);
        Networking.sendToAll(new SyncS2CPackets.RemoveCharacter(uuid));
        try {
            Files.delete(getPath(uuid));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Path getPath(String uuid) {
        return CHARACTER_PATH.resolve(uuid + ".json5");
    }

    public static void loadCharacterReference() {
        REFERENCE_PATH = PersonalityServer.server.getSavePath(WorldSavePath.ROOT).resolve("mod_data/personality");
        CHARACTER_PATH = REFERENCE_PATH.resolve("characters");

        playerIDToCharacterID.clear();
        characterIDToCharacter.clear();

        try {
            JsonObject o = GSON.fromJson(Files.readString(REFERENCE_PATH), JsonObject.class);
            playerIDToCharacterID = HashBiMap.create(GSON.fromJson(o.getAsJsonObject("player_to_character"), REF_MAP_TYPE));
        } catch (IOException e) {
            if (e instanceof NoSuchFileException)
                saveCharacterReference();
            else
                e.printStackTrace();
        }
    }

    public static void saveCharacterReference() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("format", 1);
            json.add("player_to_character", GSON.toJsonTree(playerIDToCharacterID, REF_MAP_TYPE));

            Files.writeString(REFERENCE_PATH, GSON.toJson(json));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
