package io.blodhgarm.personality.api.character;

import io.blodhgarm.personality.PersonalityMod;
import io.blodhgarm.personality.api.addon.BaseAddon;
import io.blodhgarm.personality.api.reveal.KnownCharacter;
import io.blodhgarm.personality.misc.config.PersonalityConfig;
import io.blodhgarm.personality.utils.Constants;
import io.wispforest.owo.ui.core.Color;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

import static java.lang.Math.*;
import static java.lang.Math.log;

/**
 * Base Interface for Characters used in both {@link Character} & {@link KnownCharacter}.
 */
public interface BaseCharacter {

    enum Stage {YOUTH, PRIME, OLD}

    Map<String, KnownCharacter> getKnownCharacters();

    Map<Identifier, BaseAddon> getAddons();

    /**
     * Method used to grab from the characters internal AddonMap via an Identifier
     * @param addonId Addons Identifier
     * @return Addon ID linked to the character Addon or null
     */
    @Nullable
    default BaseAddon getAddon(Identifier addonId){ return getAddons().get(addonId); }

    /**
     * Method that will return the Characters uuid as a String
     */
    String getUUID();

    /**
     * Method that will return the Creator of the Characters uuid as a String
     */
    String getPlayerUUID();

    /**
     * Method that will return the Characters Name as a String
     */
    String getName();

    /**
     * Method that will return the Characters Name formatted with its various Aliases
     */
    Text getFormattedName();

    String getAlias();

    /**
     * Method that will return the Characters Gender
     */
    String getGender();

    /**
     * Method that will return the Physical Description of what the Character looks like
     */
    String getDescription();

    /**
     * Method that will return the Biography of the Characters Life and Backstory
     */
    String getBiography();

    /**
     * Method used to get the information pertaining to how old the character is
     */
    int getAge();

    boolean isDead();

    int getDeathWindow();

    int getTotalPlaytime();

    int getCurrentPlaytime();

    default void beforeEvent(String event){}

    /**
     * Such a method is used to set the manager for the respect side either client or server. Is automatically done on deserlisation
     * but new iterations manually created <bold>! MUST !</bold> be called or bad things will happen!
     */
    BaseCharacter setCharacterManager(CharacterManager<? extends PlayerEntity, ? extends Character> manager);

    default String getInfo() {
        return getInfo(false);
    }

    default String getInfo(boolean trimTextBoxElements) {
        StringBuilder baseInfoText = new StringBuilder();

        baseInfoText.append(getName() + "§r\n"
                + "\n§lUUID§r: " + getUUID()
                + "\n§lGender§r: " + getGender()
                + "\n§lDescription§r: " + (trimTextBoxElements ? trim(getDescription(), 75) : getDescription())
                + "\n§lBio§r: " + (trimTextBoxElements ? trim(getBiography(), 75) : getBiography())
                + "\n§lAge§r: " + getAge() + /*" / " + getMaxAge() + */ " (" + getStage() + ")"
                + "\n§lPlaytime§r: " + (getTotalPlaytime() / Constants.HOUR_IN_MILLISECONDS));

        this.getAddons().values().forEach((baseAddon) -> baseInfoText.append(baseAddon.getInfo()));

        return baseInfoText.toString();
    }

    static String trim(String text, int maxWordCount){
        String[] words = text.split(" ");

        if(words.length > maxWordCount){
            words = Arrays.copyOf(words, maxWordCount);
        }

        return String.join(" ", words).concat(" ... (More Info in Character Screen)");
    }

    default Stage getStage() {
        int age = getAge();
        return age < 25 ? Stage.YOUTH : age < 60 ? Stage.PRIME : Stage.OLD;
    }

    default Health getHealthStage(){
        float currentAge = getAge();
        float maxAge = getMaxAge();

        int divisors = Health.validValues().length - 1;

        Float[] array = createArray(0f, divisors, v -> v + ((maxAge - v) / 2));

        for(; divisors > 0; divisors--) if(currentAge > array[divisors - 1]) break;

        return Health.validValues()[divisors];
    }

    default int getMaxAge() {
        return PersonalityMod.CONFIG.defaultMaximumAge() + Math.min(PersonalityMod.CONFIG.maximumExtraAge(), getExtraAge());
    }

    default int getExtraAge() {
        PersonalityConfig.ExtraAgeConfiguration config = PersonalityMod.CONFIG.extraAgeConfiguration;
        double hoursPlayed = (float) getTotalPlaytime() / Constants.HOUR_IN_MILLISECONDS;
        int extraYears = 0;

        if (hoursPlayed > config.minimumHoursForExtraLife() && getAge() > config.minAge()) {
            for (int i = 1; ; i++) {
                double hoursNeeded = config.minimumHoursForExtraLife() + config.multiplier() * switch (config.calculationCurve()) {
                    case NONE -> 0;
                    case LINEAR -> i - 1;
                    case QUADRATIC -> pow(i, 2) - 1;
                    case SQRT -> sqrt(i) - 1;
                    case EXPONENTIAL, EXPONENTIAL_EXTREME -> pow(E, i - 1) - 1;
                    case LOGARITHMIC, LOGARITHMIC_EXTREME -> log(i);
                };

                if (hoursPlayed < hoursNeeded) break;

                hoursPlayed -= hoursNeeded;
            }
        }

        return extraYears;
    }

    enum Health {
        UNKNOWN(new Color(0.75f, 0.75f, 0.75f)),
        GREAT(Color.ofDye(DyeColor.LIME)),
        GOOD(Color.ofDye(DyeColor.GREEN)),
        OKAY(Color.ofDye(DyeColor.YELLOW)),
        UNWELL(Color.ofDye(DyeColor.ORANGE)),
        NEAR_DEATH(Color.ofDye(DyeColor.RED));

        private static final Health[] VALID_VALUES = new Health[]{GREAT, GOOD, OKAY, UNWELL, NEAR_DEATH};

        private final Color color;

        Health(Color color){
            this.color = color;
        }

        public static Health[] validValues(){
            return VALID_VALUES;
        }

        public MutableText getLabel(){
            return Text.translatable("personality.health." + name().toLowerCase());
        }

        public MutableText getTooltip(){
            return Text.translatable("personality.health." + name().toLowerCase() + ".tooltip");
        }

        public Color getColor(){
            return this.color;
        }
    }

    static <T extends Number> T[] createArray(T startingValue, int n, Function<T, T> computeNextValue){
        T[] array = (T[]) Array.newInstance(startingValue.getClass(), n);

        for(int i = 0; i < n; i++){
            startingValue = computeNextValue.apply(startingValue);

            array[i] = startingValue;
        }

        return array;
    }
}
