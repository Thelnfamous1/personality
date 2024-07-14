package io.blodhgarm.personality.api.addon;

import com.google.gson.JsonSyntaxException;
import io.blodhgarm.personality.PersonalityMod;
import io.blodhgarm.personality.api.PersonalityEntrypoint;
import io.blodhgarm.personality.api.character.Character;
import io.blodhgarm.personality.api.core.BaseRegistry;
import io.blodhgarm.personality.server.ServerCharacters;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.ApiStatus;

import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * AddonRegistry is used internally to read and validate addons on both client and server
 */
public class AddonRegistry<A extends BaseAddon> extends BaseRegistry {

    private final Map<Identifier, AddonLoader<A>> LOADERS = new HashMap<>();

    //--------------------------------------------------------------------------------

    public AddonRegistry() { super(); }

    /**
     * The current Instant of the Addon Registry used for registering your addon within the {@link PersonalityEntrypoint}
     */
    public static final AddonRegistry<BaseAddon> INSTANCE = new AddonRegistry<>();

    /**
     * Method used to register your addon for Personality
     * @param addonId The Addons Identifier
     * @param addonClass The class that extends the BaseAddon
     * @param defaultAddon The default version of your given Addon
     * @param addonValidator Predicate used to confirm that the addon is valid during the creation of the character
     */
    public void registerAddon(Identifier addonId, Class<A> addonClass, Supplier<A> defaultAddon, Predicate<A> addonValidator){
        if(!LOADERS.containsKey(addonId)){
            LOADERS.put(addonId, new AddonLoader<>(addonId, addonClass, defaultAddon, addonValidator));
        } else {
            LOGGER.warn("[AddonRegistry]: The given Identifier [{}] is already found within the Loaders map meaning it will not be registered.", addonId);
        }
    }

    //--------------------------------------------------------------------------------


    public Optional<AddonLoader<A>> getAddonLoader(Identifier addonId){
        return Optional.ofNullable(LOADERS.get(addonId));
    }

    @Nullable
    public Class<A> getAddonClass(Identifier addonId){
        Optional<AddonLoader<A>> loader = getAddonLoader(addonId);

        return loader.map(AddonLoader::addonClass).orElse(null);
    }

    public Map<Identifier, A> getDefaultAddons(){
        Map<Identifier, A> defaultAddonsMap = new HashMap<>();

        LOADERS.forEach((identifier, loaderStorage) -> defaultAddonsMap.put(identifier, loaderStorage.defaultAddon.get()));

        return defaultAddonsMap;
    }

    @Nullable
    public A validateOrDefault(Identifier addonId, @Nullable A addon){
        Optional<AddonLoader<A>> loaderStorage = getAddonLoader(addonId);

        if(loaderStorage.isPresent()) {
            if (addon != null) {
                if (loaderStorage.get().addonValidator().test(addon)) return addon;

                LOGGER.warn("[AddonValidation] A characters addon from {} was found to be invalid, will be replaced with the default value", addonId);
            } else {
                LOGGER.warn("[AddonValidation] A characters addon from {} was found to be null, will be replaced with the default value", addonId);
            }

            return loaderStorage.get().defaultAddon().get();
        } else {
            LOGGER.warn("[AddonValidation] A addonId [{}] was passed within validation check and was found to not exist within the Registry!", addonId);
        }

        return null;
    }

    public void checkAndDefaultPlayerAddons(PlayerEntity player){
        this.getDefaultAddons().forEach((identifier, addon) -> {
            if(!addon.isEqualToPlayer(player) && addon.getAddonEnvironment().shouldApply(player.getWorld())){
                addon.applyAddon(player);
            }
        });
    }

    //--------------------------------------------------------------------------------

    @Override
    public void clearRegistry() {
        this.LOADERS.clear();

        if(FabricLoader.getInstance().isDevelopmentEnvironment()) LOGGER.info("[" + this.getRegistryId() + "]: Registry has been cleared!");
    }

    @Override
    public List<Identifier> getRegisteredIds(){
        return List.copyOf(this.LOADERS.keySet());
    }

    @ApiStatus.Internal
    public Map<Identifier, String> loadAddonsFromDisc(Character c, boolean returnJsonAddonData){
        Path characterFolder = ServerCharacters.getSpecificCharacterPath(c.getUUID());

        Map<Identifier, String> addonData = new HashMap<>();

        LOADERS.forEach((s, registryHelper) -> {
            Path currentAddonFolder = characterFolder.resolve("addons/" + s.getNamespace() + "/" + s.getPath() + ".json");

            A addon;

            try {
                String addonJson = Files.readString(currentAddonFolder);

                if(returnJsonAddonData) addonData.put(s, addonJson);

                addon = PersonalityMod.GSON.fromJson(addonJson, registryHelper.addonClass());
            } catch (IOException e){
                LOGGER.error("[AddonLoading] {} addon for [Name: {}, uuid: {}] was unable to be loaded from the Disc, setting such to default and saved to disc.", s, c.getName(), c.getUUID());

                addon = registryHelper.defaultAddon.get();
                ServerCharacters.INSTANCE.saveAddonForCharacter(c, s, addon);

                e.printStackTrace();
            } catch (JsonSyntaxException e){
                LOGGER.error("[AddonLoading] {} addon for [Name: {}, uuid: {}] was unable to be serialized, setting such to default.", s, c.getName(), c.getUUID());

                addon = registryHelper.defaultAddon.get();
                addon.loadedProperly = false;

                e.printStackTrace();
            }

            if(!registryHelper.addonValidator.test(addon)){
                //throw new AddonInvalidException("A given Character Addon was found to be invalid! [Name: " + c.getName() + " , uuid: " + c.getUUID() + "]")
                LOGGER.error("[AddonLoading] {} addon for [Name: {}, uuid: {}] was found to be invalid.", s, c.getName(), c.getUUID());

                addon.loadedProperly = false;
            }

            c.getAddons().put(s, addon);
        });

        return addonData;
    }

    public Map<Identifier, BaseAddon> deserializesAddons(Character c, Map<Identifier, String> addonData, boolean validateOrDefault){
        return Util.make(new HashMap<>(), addonMap -> addonData.forEach((addonId, s2) -> {
            if(addonId == null){
                LOGGER.error("[AddonLoading] {} addon id was found to be invalid, ignoring addon", addonId);
                return;
            }

            AddonLoader<A> registryHelper = LOADERS.get(addonId);

            if(registryHelper == null){
                LOGGER.error("[AddonLoading] {} addon for [Name: {}, uuid: {}] was unable to be found, ignoring addon", addonId, c.getName(), c.getUUID());
                return;
            }

            A addon;

            try {
                addon = PersonalityMod.GSON.fromJson(s2, registryHelper.addonClass());

                if(validateOrDefault) addon = this.validateOrDefault(addonId, addon);
            } catch (JsonSyntaxException e){
                LOGGER.error("[AddonLoading] {} addon for [Name: {}, uuid: {}] was unable to be serialized, setting such to default.", addonId, c.getName(), c.getUUID());

                addon = registryHelper.defaultAddon.get();
                addon.loadedProperly = false;

                e.printStackTrace();
            }

            addonMap.put(addonId, addon);
        }));
    }

    public Map<Identifier, String> serializesAddons(Character c){
        Map<Identifier, String> addonData = new HashMap<>();

        c.getAddons().forEach((identifier, addon) -> addonData.put(identifier, PersonalityMod.GSON.toJson(addon)));

        return addonData;
    }

    @Override
    public Identifier getRegistryId() {
        return new Identifier(PersonalityMod.MODID, "addon_registry");
    }

    //--------------------------------------------------------------------------------

    public record AddonLoader<A extends BaseAddon>(Identifier addonId, Class<A> addonClass, Supplier<A> defaultAddon, Predicate<A> addonValidator){ }
}
