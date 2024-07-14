package io.blodhgarm.personality;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jthemedetecor.OsThemeDetector;
import io.blodhgarm.personality.api.PersonalityEntrypoint;
import io.blodhgarm.personality.api.addon.AddonRegistry;
import io.blodhgarm.personality.api.addon.BaseAddon;
import io.blodhgarm.personality.api.character.BaseCharacter;
import io.blodhgarm.personality.api.core.BaseRegistry;
import io.blodhgarm.personality.api.events.FinalizedPlayerConnectionEvent;
import io.blodhgarm.personality.api.events.OnWorldSaveEvent;
import io.blodhgarm.personality.api.reveal.InfoLevel;
import io.blodhgarm.personality.api.reveal.InfoRevealLoader;
import io.blodhgarm.personality.api.reveal.InfoRevealRegistry;
import io.blodhgarm.personality.compat.origins.OriginsAddonRegistry;
import io.blodhgarm.personality.compat.pehkui.PehkuiAddonRegistry;
import io.blodhgarm.personality.compat.trinkets.TrinketsGlasses;
import io.blodhgarm.personality.item.GlassesItem;
import io.blodhgarm.personality.item.WoodenCane;
import io.blodhgarm.personality.misc.PersonalityCommands;
import io.blodhgarm.personality.misc.PersonalitySoundEvents;
import io.blodhgarm.personality.misc.config.PersonalityConfig;
import io.blodhgarm.personality.server.PrivilegeManager;
import io.blodhgarm.personality.server.ServerCharacterTick;
import io.blodhgarm.personality.server.ServerCharacters;
import io.blodhgarm.personality.utils.DebugCharacters;
import io.blodhgarm.personality.utils.gson.LocalDateTimeTypeAdapter;
import io.wispforest.owo.registration.reflect.FieldRegistrationHandler;
import io.wispforest.owo.registration.reflect.ItemRegistryContainer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.time.LocalDateTime;

public class PersonalityMod implements ModInitializer, PersonalityEntrypoint, ItemRegistryContainer {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeTypeAdapter().nullSafe())
            .create();

    public static final PersonalityConfig CONFIG = PersonalityConfig.createAndLoad();
    public static final OsThemeDetector detector = OsThemeDetector.getDetector();

    public static final String MODID = "personality";

    public static GlassesItem BASIC_GLASSES = new GlassesItem(new Item.Settings().maxCount(1));
    public static Item CANE = new WoodenCane(new Item.Settings().maxCount(1));

    public static Identifier id(String path) {
        return new Identifier(MODID, path);
    }

    @Override
    public void onInitialize() {
        ItemGroupEvents.MODIFY_ENTRIES_ALL.register((group, entries) -> {
            if(group == Registries.ITEM_GROUP.getOrThrow(ItemGroups.TOOLS)){
                entries.add(BASIC_GLASSES);
            } else if(group == Registries.ITEM_GROUP.getOrThrow(ItemGroups.COMBAT)){
                entries.add(CANE);
            }
        });

        FieldRegistrationHandler.register(PersonalitySoundEvents.class, MODID, false);

        if(FabricLoader.getInstance().isDevelopmentEnvironment()) DebugCharacters.init();

        PrivilegeManager.init();

        if(FabricLoader.getInstance().isModLoaded("trinkets")) TrinketsGlasses.init();

        FieldRegistrationHandler.register(PersonalityMod.class, MODID, false);

        PersonalityCommands.register();
        Networking.registerNetworking();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            loadRegistries("ServerStarted");

            ServerCharacters.INSTANCE.onServerStarted(server);
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(PersonalityMod.id("on_server_stop"), server -> {
            for (BaseRegistry value : BaseRegistry.REGISTRIES.values()) value.clearRegistry();

            ServerCharacters.INSTANCE.onServerStopped(server);
        });

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> loadRegistries("EndDataPackReload"));

        ServerTickEvents.END_WORLD_TICK.register(PersonalityMod.id("tick"), new ServerCharacterTick());

        FinalizedPlayerConnectionEvent.CONNECTION_FINISHED.register(PersonalityMod.id("on_player_join"), ServerCharacters.INSTANCE);

        OnWorldSaveEvent.ON_SAVE.register(ServerCharacters.INSTANCE);

        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
                .registerReloadListener(new InfoRevealLoader());
    }

    public static void loadRegistries(String eventCall){
        if(FabricLoader.getInstance().isDevelopmentEnvironment()) System.out.println("Event[" + eventCall + "]: Registry[BaseRegistries]");

        FabricLoader.getInstance().getEntrypoints("personality", PersonalityEntrypoint.class).forEach(personalityEntrypoint -> {
            personalityEntrypoint.addonRegistry(AddonRegistry.INSTANCE);
            personalityEntrypoint.infoRevealRegistry(InfoRevealRegistry.INSTANCE);
        });

        InfoRevealLoader.handleCachedData(InfoRevealRegistry.INSTANCE);
    }

    @Override
    public <T extends BaseAddon> void addonRegistry(AddonRegistry<T> registry) {
        if(FabricLoader.getInstance().isModLoaded("origins")) OriginsAddonRegistry.INSTANCE.addonRegistry(registry);
        if(FabricLoader.getInstance().isModLoaded("pehkui")) PehkuiAddonRegistry.INSTANCE.addonRegistry(registry);
    }

    @Override
    public void infoRevealRegistry(InfoRevealRegistry registry) {
        registry.registerValueForRevealing(InfoLevel.GENERAL, PersonalityMod.id("description"), () -> "Unknown");
        registry.registerValueForRevealing(InfoLevel.GENERAL, PersonalityMod.id("alias"), () -> "Unknown");

        registry.registerValueForRevealing(InfoLevel.ASSOCIATE, PersonalityMod.id("gender"), () -> "Unknown");

        registry.registerValueForRevealing(InfoLevel.ASSOCIATE, PersonalityMod.id("age"), () -> -1);
        registry.registerValueForRevealing(InfoLevel.ASSOCIATE, PersonalityMod.id("health"), () -> BaseCharacter.Health.UNKNOWN);

        registry.registerValueForRevealing(InfoLevel.TRUSTED, PersonalityMod.id("biography"), () -> "Unknown");

        registry.registerValueForRevealing(InfoLevel.CONFIDANT, PersonalityMod.id("name"), () -> "Unknown");

        if(FabricLoader.getInstance().isModLoaded("origins")) OriginsAddonRegistry.INSTANCE.infoRevealRegistry(registry);
        if(FabricLoader.getInstance().isModLoaded("pehkui")) PehkuiAddonRegistry.INSTANCE.infoRevealRegistry(registry);
    }

    //----------------------------------

    public static boolean hasEffect(LivingEntity entity, TagKey<StatusEffect> effectTagKey){
        for(StatusEffect statusEffect : entity.getActiveStatusEffects().keySet()){
            var entry = Registries.STATUS_EFFECT.getEntry(StatusEffect.getRawId(statusEffect));

            if(entry.isPresent() && entry.get().isIn(effectTagKey)) return true;
        }

        return false;
    }
}
