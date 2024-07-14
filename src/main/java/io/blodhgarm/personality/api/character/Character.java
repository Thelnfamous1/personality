package io.blodhgarm.personality.api.character;

import io.blodhgarm.personality.api.addon.BaseAddon;
import io.blodhgarm.personality.api.core.KnownCharacterLookup;
import io.blodhgarm.personality.api.reveal.KnownCharacter;
import io.blodhgarm.personality.api.utils.PlayerAccess;
import io.blodhgarm.personality.misc.PersonalityTags;
import io.blodhgarm.personality.server.ServerCharacters;
import io.blodhgarm.personality.utils.Constants;
import io.blodhgarm.personality.utils.DebugCharacters;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import org.jetbrains.annotations.Nullable;
import java.util.*;

/**
 * Main Class implementation for a Character within Personality
 */
public class Character implements BaseCharacter, KnownCharacterLookup {

    public boolean isDead;

    protected final String uuid;
    protected final String playerUUID;

    protected String name;

    protected List<String> aliases = new ArrayList<>();

    @Nullable protected String alias = null;

    protected String gender;
    protected String description;
    protected String biography;

    protected int startingAgeOffset;

    protected long created;

    protected int totalPlaytime;

    protected transient int currentPlaytime = 0;

    protected int currentDeathWindow;

    protected Map<String, KnownCharacter> knownCharacters = new HashMap<>();

    protected transient Map<Identifier, BaseAddon> characterAddons = new HashMap<>();

    protected transient CharacterManager<PlayerEntity, Character> manager;

    public Character(String playerUUID, String name, String gender, String description, String biography, int ageOffset) {
        this(UUID.randomUUID().toString(), playerUUID, name, gender, description, biography, ageOffset);
    }

    public Character(String uuid, String playerUUID, String name, String gender, String description, String biography, int ageOffset) {
        this(uuid, System.currentTimeMillis(), playerUUID, name, gender, description, biography, ageOffset);
    }

    public Character(String uuid, Long created, String playerUUID, String name, String gender, String description, String biography, int ageOffset) {
        this.uuid = uuid;
        this.playerUUID = playerUUID;

        this.name = name;
        this.gender = gender;
        this.description = description;
        this.biography = biography;
        this.startingAgeOffset = ageOffset;
        this.created = created;
        this.isDead = false;

        this.totalPlaytime = 0;
    }

    @Override
    public void beforeEvent(String event) {
        if(!event.equals("save")) return;

        knownCharacters.values().forEach(k -> k.beforeEvent(event));
    }

    @Override
    public BaseCharacter setCharacterManager(CharacterManager<? extends PlayerEntity, ? extends Character> manager) {
        this.manager = (CharacterManager<PlayerEntity, Character>) manager;

        return this;
    }

    //---------------------------

    @Override
    public Map<Identifier, BaseAddon> getAddons(){
        if(this.characterAddons == null) this.characterAddons = new HashMap<>();

        return this.characterAddons;
    }

    @Override
    public Map<String, KnownCharacter> getKnownCharacters(){
        if(this.knownCharacters == null) this.knownCharacters = new HashMap<>();

        return this.knownCharacters;
    }

    @Override
    public String getUUID() {
        return uuid;
    }

    @Override
    public String getPlayerUUID() {
        return this.playerUUID;
    }

    //---------------------------

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    //---------------------------

    @Override
    @Nullable
    public String getAlias() {
        return this.alias;
    }

    public Character setAlias(String alias) {
        this.alias = alias;

        return this;
    }

    @Override
    public Text getFormattedName() {
        MutableText name = Text.literal(getName());

        if(getAlias() != null){
            name.append(Text.of(" : "));
            name.append(Text.literal(getAlias()).formatted(Formatting.ITALIC));
        }

        return name;
    }

    //---------------------------

    @Override
    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    //---------------------------

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    //---------------------------

    public String getBiography() {
        return biography;
    }

    public void setBiography(String biography) {
        this.biography = biography;
    }

    //---------------------------

    public boolean isDead() {
        return isDead;
    }

    @Override
    public int getDeathWindow() {
        return this.currentDeathWindow;
    }

    public void setIsDead(boolean isDead) {
        this.isDead = isDead;
    }

    //---------------------------

    public long getCreatedAt() {
        return created;
    }

    public int getAge() {
        return Math.round(getPreciseAge());
    }

    public float getPreciseAge() {
        return startingAgeOffset + ((float) (System.currentTimeMillis() - getCreatedAt()) / Constants.WEEK_IN_MILLISECONDS);
    }

    public void setAge(int age) {
        startingAgeOffset = age - (int)((System.currentTimeMillis() - getCreatedAt()) / Constants.WEEK_IN_MILLISECONDS);
    }

    //---------------------------

    @Override
    public int getTotalPlaytime() {
        return this.currentPlaytime + totalPlaytime;
    }

    @Override
    public int getCurrentPlaytime() {
        return this.currentPlaytime;
    }

    //---------------------------

    public boolean isObscured() {
        PlayerAccess<ServerPlayerEntity> player = ServerCharacters.INSTANCE.getPlayerFromCharacter(uuid);

        if (player.playerValid()) {
            for (ItemStack stack : player.player().getItemsEquipped()) {
                if (stack.isIn(PersonalityTags.Items.OBSCURES_IDENTITY)) return true;
            }
        }

        return false;
    }

    public boolean isError(){
        return DebugCharacters.ERROR == this;
    }

    @Override
    public String toString() {
        return "Character{" +
                "\nuuid=" + uuid +
                ",\n name=" + name +
                ",\n gender=" + gender +
                ",\n description=" + description +
                ",\n biography=" + biography +
                ",\n ageOffset=" + startingAgeOffset +
                ",\n created=" + created +
                ",\n totalPlaytime=" + totalPlaytime +
                ",\n knowCharacters=" + knownCharacters +
                "\n}";
    }

    //--------------------------------------------

    @Override
    public void addKnownCharacter(KnownCharacter wrappedC) {
        this.getKnownCharacters().put(wrappedC.wrappedCharacterUUID, wrappedC);
    }

    @Override
    public void removeKnownCharacter(String cUUID) {
        this.getKnownCharacters().remove(cUUID);
    }

    @Override
    public boolean doseKnowCharacter(String UUID, boolean isPlayerUUID) {
        String cUUID = isPlayerUUID ? manager.getCharacterUUID(UUID) : UUID;

        if(Objects.equals(cUUID, "INVALID")) return false;

        return getKnownCharacters().containsKey(cUUID);
    }

    @Override
    @Nullable
    public KnownCharacter getKnownCharacter(String UUID, boolean isPlayerUUID) {
        String cUUID = isPlayerUUID ? manager.getCharacterUUID(UUID) : UUID;

        if(Objects.equals(cUUID, "INVALID")) return null;

        return getKnownCharacters().get(cUUID);
    }

    @Override
    @Nullable
    public Character getOwnerCharacter() {
        return this;
    }

    //--------------------------------------------


}
