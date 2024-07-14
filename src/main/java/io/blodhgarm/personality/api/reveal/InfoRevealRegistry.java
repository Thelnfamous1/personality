package io.blodhgarm.personality.api.reveal;

import io.blodhgarm.personality.PersonalityMod;
import io.blodhgarm.personality.api.core.BaseRegistry;
import io.blodhgarm.personality.api.utils.InfoRevealResult;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.apache.commons.collections4.map.LinkedMap;

import org.jetbrains.annotations.Nullable;
import java.util.*;

/**
 * Registry used for Registering Obfuscated replacements for given Info about a Character
 */
public class InfoRevealRegistry extends BaseRegistry {

    public static InfoRevealRegistry INSTANCE = new InfoRevealRegistry();

    public final LinkedMap<InfoLevel, List<Identifier>> FINALIZED_REGISTRY = new LinkedMap<>();

    public final LinkedMap<InfoLevel, List<Identifier>> REGISTRY = new LinkedMap<>();

    private final Map<Identifier, ObfuscatedReplacement<?>> OBFUSCATED_REPLACEMENT = new HashMap<>();

    public InfoRevealRegistry(){
        super();

        Arrays.stream(InfoLevel.VALID_VALUES).forEach(level -> {
            REGISTRY.put(level, new ArrayList<>());
            FINALIZED_REGISTRY.put(level, new ArrayList<>());
        });
    }

    /**
     * Method used to register Info Reveal Data
     *
     * @param level The given level which this info can be showed rather than Obfuscation
     * @param valueId The given value's Identifier being called within Known Character
     * @param replacementInfo A Helper used to show the Obfuscated info if the character doesn't know that info
     * @return
     */
    public InfoRevealRegistry registerValueForRevealing(InfoLevel level, Identifier valueId, ObfuscatedReplacement<?> replacementInfo){
        List<Identifier> valueForLevel = REGISTRY.get(level);

        boolean alreadyRegisteredRevealLevel = valueForLevel.contains(valueId);
        boolean alreadyExistingReplacement = OBFUSCATED_REPLACEMENT.containsKey(valueId);

        if(!alreadyRegisteredRevealLevel && !alreadyExistingReplacement){
            valueForLevel.add(valueId);
            OBFUSCATED_REPLACEMENT.put(valueId, replacementInfo);
        }

        if(alreadyRegisteredRevealLevel || alreadyExistingReplacement){
            LOGGER.error("[InfoRevealRegistry]: The given Value Identifier ({}) has already been registered, meaning such won't be registered again!", valueId);
        }

        return this;
    }

    public boolean showInformation(InfoLevel level, Identifier identifier){
        if(FINALIZED_REGISTRY.get(level).contains(identifier)) return true;

        int currentRevelLevel = FINALIZED_REGISTRY.indexOf(level);

        for(int i = 0; i < currentRevelLevel; i++){
            if(FINALIZED_REGISTRY.getValue(i).contains(identifier)) return true;
        }

        return false;
    }

    public <T> InfoRevealResult<T> defaultOrReplace(InfoLevel level, Identifier identifier, T defaultValue){
        if(showInformation(level, identifier)) return new InfoRevealResult<>(false, defaultValue);

        ObfuscatedReplacement<T> replacement = getReplacement(identifier);

        if(replacement == null) {
            LOGGER.error("[InfoRevealRegistry]: It seems that a Character Info [Id: {}] needed to Obfuscated but the Replacement was not found, meaning default info is shown!", identifier);

            return new InfoRevealResult<>(false, defaultValue);
        } else {
            return new InfoRevealResult<>(true, replacement.getReplacement());
        }
    }

    @Nullable
    public <T> ObfuscatedReplacement<T> getReplacement(Identifier identifier){
        return (ObfuscatedReplacement<T>) OBFUSCATED_REPLACEMENT.get(identifier);
    }

    public interface ObfuscatedReplacement<T> {
        T getReplacement();
    }

    @Override
    public void clearRegistry() {
        this.REGISTRY.forEach((level, listOfIds) -> listOfIds.clear());
        this.FINALIZED_REGISTRY.forEach((level, listOfIds) -> listOfIds.clear());

        this.OBFUSCATED_REPLACEMENT.clear();

        if(FabricLoader.getInstance().isDevelopmentEnvironment()) LOGGER.info("[" + this.getRegistryId() + "]: Registry has been cleared!");
    }

    @Override
    public Identifier getRegistryId() {
        return new Identifier(PersonalityMod.MODID, "info_reveal_registry");
    }

    @Override
    public List<Identifier> getRegisteredIds() {
        List<Identifier> registeredIds = new ArrayList<>();

        REGISTRY.forEach((level, identifiers) -> registeredIds.addAll(identifiers));

        return registeredIds;
    }
}
