package io.blodhgarm.personality.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.blodhgarm.personality.Networking;
import io.blodhgarm.personality.PersonalityMod;
import io.blodhgarm.personality.api.character.Character;
import io.blodhgarm.personality.api.character.ServerCharacter;
import io.blodhgarm.personality.api.utils.PlayerAccess;
import io.blodhgarm.personality.misc.PersonalityTags;
import io.blodhgarm.personality.misc.config.ConfigHelper;
import io.blodhgarm.personality.misc.config.PersonalityConfig;
import io.blodhgarm.personality.misc.pond.DamageSourceExtended;
import io.blodhgarm.personality.packets.CharacterDeathPackets;
import io.blodhgarm.personality.packets.SyncS2CPackets;
import io.wispforest.owo.Owo;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registerable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServerCharacterTick implements ServerTickEvents.EndWorldTick {

    public static final RegistryKey<DamageType> OLD_AGE_KEY = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, PersonalityMod.id("old_age"));
    public static final RegistryKey<DamageType> TRIPPED_BY_DEATH_KEY = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("tripped_by_death"));

    private static final UUID AGING_SLOWNESS_MODIFIER_UUID = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278F");

    private int currentPacketDelay = 0;

    private static final Map<String, DeathTracker> deathTrackerMap = new ConcurrentHashMap<>();

    private int checkForDeathScreen = 0;

    public static final Map<String, Boolean> hasDeathScreenOpen = new ConcurrentHashMap<>();

    @Override
    public void onEndTick(ServerWorld world) {
        List<Character> onlineCharacters = new ArrayList<>();

        for (ServerPlayerEntity player : world.getPlayers()) {
            Character c = ServerCharacters.INSTANCE.getCharacter(player);

            if (c == null) continue;

            onlineCharacters.add(c);

            ((ServerCharacter) c).updateCurrentPlaytime();

            String playerUUID = player.getUuidAsString();

            // Kill Too Old Characters
            if (c.getAge() >= c.getMaxAge() && !deathTrackerMap.containsKey(playerUUID) && !c.isDead()) {
                //TODO: Figure out way for handling Characters that don't have players that are online

                deathTrackerMap.put(playerUUID, new DeathTracker(world, playerUUID).resetTimer());

                continue;
            }

            // Apply Slowness to Old Characters without a stick
            PersonalityConfig.AgingSlowness config = PersonalityMod.CONFIG.agingSlowness;
            EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);

            boolean check = ConfigHelper.shouldApply(config, c)
                    && !player.getOffHandStack().isIn(PersonalityTags.Items.WALKING_STICKS)
                    && !player.getMainHandStack().isIn(PersonalityTags.Items.WALKING_STICKS);

            if (!check) {
                instance.removeModifier(AGING_SLOWNESS_MODIFIER_UUID);

                continue;
            }

            if (instance.getModifier(AGING_SLOWNESS_MODIFIER_UUID) != null) continue;

            instance.addTemporaryModifier(new EntityAttributeModifier(AGING_SLOWNESS_MODIFIER_UUID, "Old Person with No Stick", -ConfigHelper.apply(config, c), EntityAttributeModifier.Operation.MULTIPLY_BASE));
        }

        deathTrackerMap.forEach((s, deathTracker) -> {
            if(hasDeathScreenOpen.get(s)) return;

            deathTracker.reduceTimer();
        });

        checkForDeathScreen++;

        if(checkForDeathScreen > (31 * 20)){
            deathTrackerMap.forEach((s, deathTracker) -> {
                ServerPlayerEntity player = Owo.currentServer().getPlayerManager().getPlayer(s);

                if(player != null) {
                    Networking.sendS2C(Owo.currentServer().getPlayerManager().getPlayer(s), new CharacterDeathPackets.CheckDeathScreenOpen());
                } else {
                    hasDeathScreenOpen.put(s, false);
                }
            });
        }

        currentPacketDelay++;

        if(currentPacketDelay > (30 * 20)){
            if(!onlineCharacters.isEmpty()) {
                JsonArray jsonArray = new JsonArray();

                for (Character onlineCharacter : onlineCharacters) {
                    JsonObject jsonObject = new JsonObject();

                    jsonObject.addProperty("uuid", onlineCharacter.getUUID());
                    jsonObject.addProperty("currentPlaytime", onlineCharacter.getCurrentPlaytime());

                    jsonArray.add(jsonObject);
                }

                Networking.sendToAll(new SyncS2CPackets.SyncOnlinePlaytimes(jsonArray.toString()));
            }

            currentPacketDelay = 0;
        }
    }

    public static void killCharacter(World world, String playerUUID, @Nullable String deathMessage){
        ServerCharacters.INSTANCE.killCharacter(ServerCharacters.INSTANCE.getCharacter(ServerCharacters.INSTANCE.getCharacterUUID(playerUUID)));

        ServerPlayerEntity player = Owo.currentServer().getPlayerManager().getPlayer(playerUUID);

        RegistryKey<DamageType> damageType = deathMessage == null ? TRIPPED_BY_DEATH_KEY : OLD_AGE_KEY;

        if(player != null) deathMessage = " wasn't able to run from death forever, even when being offline!";

        CustomDamageSource source = new CustomDamageSource(deathMessage == null ? "" : deathMessage, world.getDamageSources().registry.entryOf(damageType), player, null);

        if(player != null && PersonalityMod.CONFIG.killPlayerOnCharacterDeath()){
            ((DamageSourceExtended) source).disableDeathMessage(true);

            player.damage(source, Float.MAX_VALUE);

            ((DamageSourceExtended) source).disableDeathMessage(false);
        }

        Networking.sendToAll(new CharacterDeathPackets.ReceivedDeathMessage(playerUUID, source.message, source.getTypeKey() == TRIPPED_BY_DEATH_KEY));

        deathTrackerMap.remove(playerUUID);
    }

    public static class DeathTracker {
        private final String playerUUID;
        private final World world;

        public int numberOfTries = 0;

        public int attemptTimer = 0;

        public DeathTracker(World world, String playerUUID){
            this.playerUUID = playerUUID;
            this.world = world;
        }

        public void reduceTimer(){
            this.attemptTimer--;

            if(this.numberOfTries > 3){
                killCharacter(world, playerUUID, null);

                return;
            }

            if(this.attemptTimer < 0){
                this.numberOfTries++;

                this.resetTimer();
            }
        }

        public DeathTracker resetTimer(){
            this.attemptTimer = PersonalityMod.CONFIG.characterDeathWindow();

            PlayerAccess<ServerPlayerEntity> playerAccess = ServerCharacters.INSTANCE.getPlayer(this.playerUUID);

            if(playerAccess.playerValid()) {
                Networking.sendS2C(playerAccess.player(), new CharacterDeathPackets.OpenCharacterDeathScreen());
            }

            return this;
        }
    }

    public static class CustomDamageSource extends DamageSource {

        protected final String message;

        public CustomDamageSource(String deathMessage, RegistryEntry<DamageType> type, @Nullable Entity source, @Nullable Entity attacker) {
            super(type, source, attacker);

            this.message = deathMessage;
        }

        public RegistryKey<DamageType> getTypeKey(){
            return this.getTypeRegistryEntry().getKey().get();
        }
        @Override
        public Text getDeathMessage(LivingEntity entity) {
            Text text = Text.of("");

            if(message.isBlank()){
                text = super.getDeathMessage(entity);
            }

            if(!Objects.equals(text, Text.empty()) && !message.isBlank()){
                text = entity.getDisplayName().copy()
                        .append(Text.of(" " + this.message));
            }

            return text;
        }
    }

    public static CustomDamageSource getSource(PlayerEntity player, boolean tripped, String message){
        RegistryKey<DamageType> damageType = tripped ? ServerCharacterTick.TRIPPED_BY_DEATH_KEY : ServerCharacterTick.OLD_AGE_KEY;

        return new CustomDamageSource(message, player.getWorld().getDamageSources().registry.entryOf(damageType), player, null);
    }

    public static void personality$bootstrap(Registerable<DamageType> damageTypeRegisterable) {
        damageTypeRegisterable.register(OLD_AGE_KEY, new DamageType("oldAge", 0.0F));
        damageTypeRegisterable.register(TRIPPED_BY_DEATH_KEY, new DamageType("trippedByDeath", 0.0F));
    }
}
