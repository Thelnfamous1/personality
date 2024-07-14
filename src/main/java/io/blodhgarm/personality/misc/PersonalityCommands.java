package io.blodhgarm.personality.misc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.logging.LogUtils;
import io.blodhgarm.personality.Networking;
import io.blodhgarm.personality.api.character.BaseCharacter;
import io.blodhgarm.personality.api.character.Character;
import io.blodhgarm.personality.api.reveal.InfoLevel;
import io.blodhgarm.personality.api.reveal.KnownCharacter;
import io.blodhgarm.personality.api.reveal.RevelInfoManager;
import io.blodhgarm.personality.client.gui.CharacterViewMode;
import io.blodhgarm.personality.client.gui.GenderSelection;
import io.blodhgarm.personality.packets.CharacterDeathPackets;
import io.blodhgarm.personality.packets.OpenPersonalityScreenS2CPacket;
import io.blodhgarm.personality.server.PrivilegeManager;
import io.blodhgarm.personality.server.ServerCharacters;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.minecraft.command.argument.EntityArgumentType.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class PersonalityCommands {

    private static final String
            CHARACTER_UUID_KEY = "character_uuid",
            TARGET_CHARACTER_UUID_KEY = "character_uuid",
            PLAYER_UUID_KEY = "player_uuid",
            PLAYER_KEY = "player",
            TARGET_PLAYER_KEY = "target_player",
            PLAYERS_KEY = "players";


    private static final Logger LOGGER = LogUtils.getLogger();

    public static final SuggestionProvider<ServerCommandSource> REVEAL_LEVEL_SUGGESTION =
            (c, b) -> CommandSource.suggestMatching(Arrays.stream(InfoLevel.VALID_VALUES).map(InfoLevel::name), b);

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralCommandNode<ServerCommandSource> personalityCommandNode = dispatcher.register(buildCommand("personality"));
            dispatcher.register(literal("p").redirect(personalityCommandNode));

            LiteralCommandNode<ServerCommandSource> characterManagerCommandNode = dispatcher.register(buildCharacterCommands("characterManager", dispatcher));
            dispatcher.register(literal("cm").redirect(characterManagerCommandNode));

        });
    }

    public static LiteralArgumentBuilder<ServerCommandSource> buildCharacterCommands(String base, CommandDispatcher<ServerCommandSource> dispatcher){
        return literal(base)

            .then(literal("list-all").requires(PrivilegeManager.privilegeCheck("list-all")) //MODERATION_CHECK
                .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(1))
                .executes(PersonalityCommands::listAllCharacters)
            )

            .then(literal("create").requires(PrivilegeManager.privilegeCheck("create"))
                .then(argument("name", word() )
                    .then(argument("gender", word() ).suggests((c, b) -> CommandSource.suggestMatching(Arrays.stream(GenderSelection.valuesWithoutOther()).map(GenderSelection::name), b))
                        .then(argument("description", string() )
                            .then(argument("biography", string() )
                                .then(argument("age", integer(17, 60) )
                                    .executes(PersonalityCommands::create) )))))
            )

            .then(literal("associate").requires(PrivilegeManager.privilegeCheck("associate"))
                .then(argument(CHARACTER_UUID_KEY, UuidArgumentType.uuid())
                    .then(argument(PLAYER_KEY, player())
                        .executes(c -> associate(c, getPlayer(c, PLAYER_KEY)))))
            )

            .then(literal("disassociate").requires(PrivilegeManager.privilegeCheck("disassociate"))
                    .then(argument(PLAYER_KEY, player())
                            .executes(c -> disassociate(c, getPlayer(c, PLAYER_KEY).getUuidAsString(), false))
                    )
                    .then(argument(CHARACTER_UUID_KEY, UuidArgumentType.uuid())
                            .executes(c -> disassociate(c, getUUID(c, CHARACTER_UUID_KEY), true))
                    )
            )

            .then(literal("get")
                .then(literal("self")
                    .executes(c -> get(c, getCharacter(c, 0))))
                .then(literal("player").requires(PrivilegeManager.privilegeCheck("get_player"))
                    .then(argument(PLAYER_KEY, player())
                        .executes(c -> get(c, getCharacter(c, 1)))))
                .then(literal("uuid").requires(PrivilegeManager.privilegeCheck("get_uuid"))
                    .then(argument(CHARACTER_UUID_KEY, UuidArgumentType.uuid())
                        .executes(c -> get(c, getCharacter(c, 2)))))
            )

            .then(literal("set").requires(PrivilegeManager.privilegeCheck("set"))
                .then(setters(literal("self"), c -> PersonalityCommands.getCharacter(c, 0) ))
                .then(literal("player")
                    .then(setters(argument(PLAYER_KEY, player()), c -> PersonalityCommands.getCharacter(c, 1))))
                .then(literal("uuid")
                    .then(setters(argument(CHARACTER_UUID_KEY, UuidArgumentType.uuid()), c -> PersonalityCommands.getCharacter(c, 2))))
            )

            .then(literal("known")
                .then(literal("list")
                    .executes(PersonalityCommands::listKnownCharacters))
                .then(
                    literal("add").requires(PrivilegeManager.privilegeCheck("known_add"))
                        .then(
                            buildKnowledgeCommand(
                                argument("info_level", string()).suggests(REVEAL_LEVEL_SUGGESTION),
                                PersonalityCommands::addKnownCharacter
                            )
                        )
                )
                .then(
                    buildKnowledgeCommand(
                            literal("remove").requires(PrivilegeManager.privilegeCheck("known_remove")),
                            PersonalityCommands::removeKnownCharacter
                    )
                )
            )

            .then(literal("revive").requires(PrivilegeManager.privilegeCheck("revive"))
//                    .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(1))
//                    .executes(c -> reviveCharacter(c, 0))
//                        .then(argument(PLAYERS_KEY, players())
//                                .executes(c -> reviveCharacter(c, 3)))
                    .then(argument(CHARACTER_UUID_KEY, UuidArgumentType.uuid())
                            .executes(c -> reviveCharacter(c, 2)))
            )

            .then(literal("delete").requires(PrivilegeManager.privilegeCheck("delete"))
                .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(1))
//                .executes(c -> deleteCharacter(c, 0))
//                .then(argument(PLAYERS_KEY, players())
//                        .executes(c -> deleteCharacter(c, 3)))
                .then(argument(CHARACTER_UUID_KEY, UuidArgumentType.uuid())
                        .executes(c -> deleteCharacter(c, 2)))
            )

            .then(literal("kill").requires(PrivilegeManager.privilegeCheck("kill"))
//                .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(1))
//                .executes(c -> killCharacter(c, 0))
                .then(argument(PLAYERS_KEY, players())
                        .executes(c -> killCharacter(c, 3)))
                .then(argument(CHARACTER_UUID_KEY, UuidArgumentType.uuid())
                        .executes(c -> killCharacter(c, 2)))
            );
    }

    public static ArgumentBuilder<ServerCommandSource, ?> buildKnowledgeCommand(ArgumentBuilder<ServerCommandSource, ?> base, TriFunction<CommandContext<ServerCommandSource>, Integer, Integer, Integer> func){
        return base.then(literal("self")
                        .then(argument(PLAYERS_KEY, players())
                                .executes(c -> func.apply(c, 0, 3)))
                        .then(argument(CHARACTER_UUID_KEY, UuidArgumentType.uuid())
                                .executes(c -> func.apply(c, 0, 2)))
                        .executes(c -> func.apply(c, 0, 0))
                ).then(argument(PLAYER_KEY, players())
                        .then(argument(PLAYERS_KEY, players())
                                .executes(c -> func.apply(c, 1, 3)))
                        .then(argument(CHARACTER_UUID_KEY, UuidArgumentType.uuid())
                                .executes(c -> func.apply(c, 1, 2)))
                        .executes(c -> func.apply(c, 1, 0))
                ).then(argument(CHARACTER_UUID_KEY, UuidArgumentType.uuid())
                        .then(argument(PLAYERS_KEY, players())
                                .executes(c -> func.apply(c, 2, 3)))
                        .then(argument(CHARACTER_UUID_KEY, UuidArgumentType.uuid())
                                .executes(c -> func.apply(c, 2, 2)))
                        .executes(c -> func.apply(c, 2, 0))
                );
    }

    public static LiteralArgumentBuilder<ServerCommandSource> buildCommand(String base) {
        return literal(base)
            .then(literal("reveal")
                .then(revealCommandBuilder(literal("small"), c -> RevelInfoManager.RevealRange.SMALL.range))
                .then(revealCommandBuilder(literal("medium"), c -> RevelInfoManager.RevealRange.MEDIUM.range))
                .then(revealCommandBuilder(literal("large"), c -> RevelInfoManager.RevealRange.LARGE.range))
                .then(literal("range")
                    .then(revealCommandBuilder(argument("range", integer(0)), c -> getInteger(c,"range")))
                )
                .then(literal("players").requires(PrivilegeManager.privilegeCheck("reveal_players"))
                    .then(revealCommandBuilder(argument(PLAYERS_KEY, players()), c -> -1))
                )
            )
            .then(literal("screen")
                .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(1))
                .then(
                    buildScreenCommand(literal("creation").requires(PrivilegeManager.privilegeCheck("screen_creation")), CharacterViewMode.CREATION, "creation")
                )
                .then(
                    buildScreenCommand(literal("view"), CharacterViewMode.VIEWING, "view")
                )
                .then(
                    buildScreenCommand(literal("edit"), CharacterViewMode.EDITING, "edit")
                ).then(
                    literal("kill")
                        .then(literal("self").requires(PrivilegeManager.privilegeCheck("screen_kill_targeted"))
                                .executes(c -> {
                                    Networking.sendS2C(c.getSource().getPlayer(), new CharacterDeathPackets.OpenCharacterDeathScreen());

                                    return msg(c, "Opening Screen");
                                })
                        ).then(argument(TARGET_PLAYER_KEY, player()).requires(PrivilegeManager.privilegeCheck("screen_kill_targeted"))
                                .executes(c -> {
                                    PlayerEntity player;

                                    try {
                                        player = getPlayer(c, "target_player");
                                    } catch (CommandSyntaxException e){
                                        return msg(c, "Could not find the target person to open the screen for!");
                                    }

                                    Networking.sendS2C(player, new CharacterDeathPackets.OpenCharacterDeathScreen());

                                    return msg(c, "Opening Screen");
                                })
                        )
                )
            );
    }

    private static <T extends ArgumentBuilder> T revealCommandBuilder(T b, Function<CommandContext<ServerCommandSource>, Integer> intFunc) {
        return (T) b.then(
                argument("info_level", string()).suggests(REVEAL_LEVEL_SUGGESTION)
                        .executes(c -> revealRange(c, intFunc.apply(c)))
        );
    };

    private static LiteralArgumentBuilder<ServerCommandSource> buildScreenCommand(LiteralArgumentBuilder<ServerCommandSource> node, CharacterViewMode mode, String type){
        node
            .then(literal("self")
                    .executes(c -> PersonalityCommands.openCharacterScreen(c, 0, mode, false))
            );

        if(mode.importFromCharacter()){
            node
                .then(argument(PLAYER_KEY, player()).requires(PrivilegeManager.privilegeCheck("screen_" + type + "_player"))
                        .executes(c -> PersonalityCommands.openCharacterScreen(c, 1, mode, false))
                )
                .then(argument(CHARACTER_UUID_KEY, string()).requires(PrivilegeManager.privilegeCheck("screen_" + type + "_uuid"))
                        .executes(c -> PersonalityCommands.openCharacterScreen(c, 2, mode, false))
                );
        } else {
            node
                .then(argument(TARGET_PLAYER_KEY, player()).requires(PrivilegeManager.privilegeCheck("screen_" + type + "_targeted"))
                        .executes(c -> PersonalityCommands.openCharacterScreen(c, -1, mode, true))
                );
        }

        return node;
    }

    private static ArgumentBuilder<ServerCommandSource,?> setters(ArgumentBuilder<ServerCommandSource,?> builder, Function<CommandContext<ServerCommandSource>, Character> character) {
        return builder.then(literal("name").then(argument("name", word())
                    .executes(c -> setProperty(c, () -> { character.apply(c).setName(getString(c, "name")); return msg(c, "Name Set"); }))))
            .then(literal("gender").then(argument("gender", word()).suggests( (c,b) -> suggestions(b, "male", "female", "nonbinary"))
                    .executes(c -> setProperty(c, () -> { character.apply(c).setGender(getString(c, "gender")); return msg(c, "Gender Set"); }))))
            .then(literal("description").then(argument("description", greedyString())
                    .executes(c -> setProperty(c, () -> { character.apply(c).setDescription(getString(c, "description")); return msg(c, "Description Set"); }))))
            .then(literal("biography").then(argument("biography", greedyString())
                    .executes(c -> setProperty(c, () -> { character.apply(c).setBiography(getString(c, "biography")); return msg(c, "Biography Set"); }))))
            .then(literal("age").then(argument("age", integer(17))
                    .executes(c -> setProperty(c, () -> { character.apply(c).setAge(getInteger(c, "age")); return msg(c, "Age Set"); }))));
            /*.then(literal("playtime").then(argument("playtime",  longArg())
                    .executes(c -> setProperty(c, () -> { boolean success = character.apply(c).setPlaytime(getInteger(c, "playtime")); return msg(c, success ? "Playtime Set" : "Couldn't set Playtime, player not online"); })))); */
    }

    private static int create(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayer();

            if(player == null) return requiresPlayerOperatorMsg(context);

            Character c = new Character(
                    player.getUuidAsString(),
                    getString(context, "name"),
                    getString(context, "gender"),
                    getString(context, "description"),
                    getString(context, "biography"),
                    getInteger(context, "age")
                    //player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME))
            );

            c.setCharacterManager(ServerCharacters.INSTANCE);

            ServerCharacters.INSTANCE.associateCharacterToPlayer(c.getUUID(), player.getUuidAsString());

            ServerCharacters.INSTANCE.pushToSaveQueue(c);
            ServerCharacters.INSTANCE.saveCharacterReference();

            return msg(context, "Character Created");
        } catch (Exception e) { e.printStackTrace(); }

        return 0;
    }

    private static int get(CommandContext<ServerCommandSource> context, Character c) {
        if (c == null) return msg(context, "§cYou don't have a Character");

        context.getSource().sendFeedback(() -> Text.literal("\n§nCharacter: " + c.getInfo(true) + "\n"), false);

        return 1;
    }

    private static int setProperty(CommandContext<ServerCommandSource> context, Supplier<Integer> code) {
        try {
            ServerCharacters.INSTANCE.pushToSaveQueue(ServerCharacters.INSTANCE.getCharacter(context.getSource().getPlayer()));

            return code.get();
        } catch (Exception e) { e.printStackTrace(); }

        return errorMsg(context);
    }

    private static int associate(CommandContext<ServerCommandSource> context, PlayerEntity player) {
        String msg = ServerCharacters.INSTANCE.associateCharacterToPlayer(getUUID(context, CHARACTER_UUID_KEY), player.getUuidAsString())
                ? "Player associated to selected character!"
                : "§cUnable to locate the selected character!";

        return msg(context, msg);
    }

    private static int disassociate(CommandContext<ServerCommandSource> context, String UUID, boolean isCharacter) {
        String playerUUID = ServerCharacters.INSTANCE.dissociateUUID(UUID, isCharacter);

        String targetType = (isCharacter ? "Player" : "Character");

        String returnMsg = playerUUID == null
                ? "§cTargeted " + targetType + " was not found to be Associated to anything"
                : targetType + " disassociated!";

        return msg(context, returnMsg);
    }

    private static int revealRange(CommandContext<ServerCommandSource> context, int range) {
        ServerPlayerEntity player = context.getSource().getPlayer();

        if(player == null) return requiresPlayerOperatorMsg(context);

        InfoLevel level = getRevealLevel(context);

        if(level == null) return errorMsg(context);

        try {
            if(range != -1) {
                ServerCharacters.INSTANCE.revealCharacterInfo(player, range, level);
            } else {
                ServerCharacters.INSTANCE.revealCharacterInfo(player, getPlayers(context, PLAYERS_KEY), level);
            }

            return msg(context, "Identity Revealed");
        } catch (Exception e) { e.printStackTrace(); }

        return errorMsg(context);
    }

    private static int listKnownCharacters(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayer();

            if(player == null) return requiresPlayerOperatorMsg(context);

            Character c = ServerCharacters.INSTANCE.getCharacter(player);

            if(c == null) return errorNoCharacterMsg(context, context.getSource().getPlayer());

            MutableText text = Text.literal("\n§nKnown Characters§r:\n\n");

            for (Map.Entry<String, KnownCharacter> entry : c.getKnownCharacters().entrySet()) {
                String characterUUID = entry.getKey();

                BaseCharacter pc = entry.getValue();

                if(pc != null) {
                    text.append(Text.literal(pc.getName() + "\n").setStyle(Style.EMPTY.withHoverEvent(
                            new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§n" + pc.getInfo(true))))));
                } else {
                    LOGGER.error("A known Character of [{}] wasn't found by the character manager: [uuid: {}]", player, characterUUID);
                }
            }

            player.sendMessage(text);

            return 1;
        }
        catch (Exception e) { e.printStackTrace(); }

        return errorMsg(context);
    }

    private static int listAllCharacters(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayer();

            if(player == null) return requiresPlayerOperatorMsg(context);

            MutableText text = Text.literal("\n§nAll Characters§r:\n\n");

            if(ServerCharacters.INSTANCE.characterLookupMap().values().isEmpty()) {
                return msg(context, "§cThere are no Characters bound to this world.");
            }

            ServerCharacters.INSTANCE.characterLookupMap().values().forEach(character -> {
                text.append(Text.literal(character.getName() + "\n").setStyle(Style.EMPTY.withHoverEvent(
                        new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§n" + character.getInfo(true))))));
            });

            player.sendMessage(text);

            return 1;
        }
        catch (Exception e) { e.printStackTrace(); }

        return errorMsg(context);
    }

    //TODO: CREATE COMMAND FOR MODIFYING OTHER CHARACTERS
    private static int addKnownCharacter(CommandContext<ServerCommandSource> context, int sourceCharacterType, int targetCharacterType) {
        InfoLevel level = getRevealLevel(context);

        if(level == null) return errorMsg(context);

        Character sourceC = getCharacter(context, sourceCharacterType);

        if(sourceC == null) return errorNoCharacterMsg(context);

        Function<String, KnownCharacter> buildFunc = owner -> {
            return (KnownCharacter) new KnownCharacter(owner, sourceC.getUUID())
                    .setDiscoveredTime()
                    .updateInfoLevel(level)
                    .setCharacterManager(ServerCharacters.INSTANCE);
        };

        if(targetCharacterType > 2){
            try {
                for (ServerPlayerEntity p : getPlayers(context, PLAYERS_KEY)) {
                    Character targetC = ServerCharacters.INSTANCE.getCharacter(p);

                    if(targetC == null) {
                        LOGGER.error("Could not add [{}] from [{}] Known Map as its Character wasn't found by the character manager", sourceC, p);

                        continue;
                    }

                    KnownCharacter wrappedSourceC;

                    if(targetC.doseKnowCharacter(sourceC)){
                        wrappedSourceC = targetC.getKnownCharacter(sourceC);
                    } else {
                        wrappedSourceC = buildFunc.apply(targetC.getUUID());

                        targetC.addKnownCharacter(wrappedSourceC);
                    }

                    wrappedSourceC.updateInfoLevel(level);

                    //TODO: Change to a huge packet that bulk sends this info?
                    ServerCharacters.INSTANCE.pushToSaveQueue(targetC);
                }
            } catch (CommandSyntaxException e){
                e.printStackTrace();

                return errorMsg(context);
            }
        } else {
            Character targetC = getCharacter(context, true, targetCharacterType);

            if(targetC == null) {
                LOGGER.error("Could not add [{}] from Target Known Map as it wasn't found by the character manager", sourceC);

                return errorNoCharacterMsg(context);
            }

            KnownCharacter wrappedSourceC;

            if(targetC.doseKnowCharacter(sourceC)){
                wrappedSourceC = targetC.getKnownCharacter(sourceC);
            } else {
                wrappedSourceC = buildFunc.apply(targetC.getUUID());

                targetC.addKnownCharacter(wrappedSourceC);
            }

            wrappedSourceC.updateInfoLevel(level);

            ServerCharacters.INSTANCE.pushToSaveQueue(targetC);
        }

        return msg(context, "Character(s) Added");
    }

    private static int removeKnownCharacter(CommandContext<ServerCommandSource> context, int sourceCharacterType, int targetCharacterType) {
        Character c = getCharacter(context, sourceCharacterType);

        if(c == null) return errorNoCharacterMsg(context);

        if(targetCharacterType > 2) {
            try {
                for (ServerPlayerEntity p : getPlayers(context, PLAYERS_KEY)) {
                    Character pCharacter = ServerCharacters.INSTANCE.getCharacter(p);

                    if (pCharacter == null) {
                        LOGGER.error("Could not remove [{}] from [{}] Known Map as its Character wasn't found by the character manager", c, p);

                        continue;
                    }

                    pCharacter.removeKnownCharacter(c.getUUID());

                    //TODO: Change to a huge packet that bulk sends this info?
                    ServerCharacters.INSTANCE.pushToSaveQueue(pCharacter);
                }
            } catch (CommandSyntaxException e){
                e.printStackTrace();

                return errorMsg(context);
            }

        } else {
            Character pCharacter = getCharacter(context, true, targetCharacterType);

            if(pCharacter == null) {
                LOGGER.error("Could not remove [{}] from Target Known Map as it wasn't found by the character manager", c);

                return errorNoCharacterMsg(context);
            }

            pCharacter.removeKnownCharacter(c.getUUID());

            ServerCharacters.INSTANCE.pushToSaveQueue(pCharacter);
        }

        return msg(context, "Character(s) Removed");
    }

    //---

    public static int reviveCharacter(CommandContext<ServerCommandSource> context, int characterSelectionType){
        return performActionOnCharacter(context, characterSelectionType, "revive");
    }

    public static int killCharacter(CommandContext<ServerCommandSource> context, int characterSelectionType){
        return performActionOnCharacter(context, characterSelectionType, "kill");
    }

    private static int deleteCharacter(CommandContext<ServerCommandSource> context, int characterSelectionType) {
        return performActionOnCharacter(context, characterSelectionType, "delete");
    }

    public static int performActionOnCharacter(CommandContext<ServerCommandSource> context, int characterSelectionType, String action){
        List<Character> characters = new ArrayList<>();

        if(characterSelectionType != 4) {
            characters.add(getCharacter(context, characterSelectionType));
        } else {
            try {
                characters.addAll(getPlayers(context, PLAYERS_KEY).stream()
                        .map(ServerCharacters.INSTANCE::getCharacter)
                        .toList()
                );
            } catch (CommandSyntaxException e){ e.printStackTrace(); }
        }

        ServerCharacters.ReturnInformation info = ServerCharacters.INSTANCE.attemptActionOn(
                characters.stream().filter(Objects::nonNull).map(Character::getUUID).toList(),
                action,
                context.getSource().getPlayer()
        );

        return info.success() ? msg(context, info.returnMessage()) : errorMsg(context, info.returnMessage());
    }

    //----------------------

    private static int openCharacterScreen(CommandContext<ServerCommandSource> context, int characterSelectionType, CharacterViewMode mode, boolean gatherPlayer){
        PlayerEntity player;

        if(gatherPlayer) {
            try {
                player = getPlayer(context, "target_player");
            } catch (CommandSyntaxException e){
                return msg(context, "Could not find the target person to open the screen for!");
            }
        } else {
            player = context.getSource().getPlayer();
        }

        if (player == null) msg(context, "Command requires a target player to open the screen for!");

        Character character = getCharacter(context, characterSelectionType);

        if(character == null && mode.importFromCharacter()) return msg(context, "Could not locate the Character though the given selection method");

        Networking.CHANNEL.serverHandle(player).send(new OpenPersonalityScreenS2CPacket(mode, character == null ? "" : character.getUUID()));

        return msg(context, "Opening Screen");
    }

    //Helpers:

    private static String getUUID(CommandContext<ServerCommandSource> context, String name){
        return UuidArgumentType.getUuid(context, name).toString();
    }


    private static CompletableFuture<Suggestions> suggestions(SuggestionsBuilder builder, String... suggestions) {
        for (String suggestion : suggestions) builder.suggest(suggestion);

        return builder.buildFuture();
    }

    @Nullable
    private static Character getCharacter(CommandContext<ServerCommandSource> context, int characterSelectionType) {
        return getCharacter(context, false, characterSelectionType);
    }

    @Nullable
    private static Character getCharacter(CommandContext<ServerCommandSource> context, boolean isTarget, int characterSelectionType) {
        try {
            return switch (characterSelectionType) {
                case 0 -> ServerCharacters.INSTANCE.getCharacter(context.getSource().getPlayer());
                case 1 -> ServerCharacters.INSTANCE.getCharacter(getPlayer(context, isTarget ? TARGET_PLAYER_KEY : PLAYER_KEY));
                case 2 -> ServerCharacters.INSTANCE.getCharacter(getUUID(context, isTarget ? TARGET_CHARACTER_UUID_KEY : CHARACTER_UUID_KEY));
                default -> null;
            };
        } catch (Exception e) { e.printStackTrace(); }

        return null;
    }

    public static InfoLevel getRevealLevel(CommandContext<ServerCommandSource> context){
        try {
            return InfoLevel.valueOf(getString(context, "info_level"));
        } catch (Exception e){ e.printStackTrace(); }

        return null;
    }

    public static CharacterViewMode getScreenMode(CommandContext<ServerCommandSource> context){
        try {
            return CharacterViewMode.valueOf(getString(context, "screen_mode"));
        } catch (Exception e){ e.printStackTrace(); }

        return null;
    }


    private static int msg(CommandContext<ServerCommandSource> context, String msg) {
        context.getSource().sendFeedback(() -> Text.literal("§2WJR: §a" + msg), false);
        return 1;
    }

    private static int requiresPlayerOperatorMsg(CommandContext<ServerCommandSource> context){
        return msg(context, "§cCommand can only be called by a Player!.");
    }

    private static int errorMsg(CommandContext<ServerCommandSource> context){
        return errorMsg(context, "Something went Wrong.");
    }

    private static int errorMsg(CommandContext<ServerCommandSource> context, String message){
        return msg(context, "§c" + message);
    }

    private static int errorNoCharacterMsg(CommandContext<ServerCommandSource> context, ServerPlayerEntity player){
        return msg(context, "§cThe current Player could not be found within the CharacterManager: [Player: " + player.toString()  + "] ");
    }

    private static int errorNoCharacterMsg(CommandContext<ServerCommandSource> context){
        return msg(context, "§cThe given Target's Character could not be found within the CharacterManager!");
    }

}
