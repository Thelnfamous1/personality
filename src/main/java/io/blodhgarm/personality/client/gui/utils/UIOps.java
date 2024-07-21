package io.blodhgarm.personality.client.gui.utils;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.blodhgarm.personality.client.ClientReflectionUtils;
import io.blodhgarm.personality.client.gui.utils.profiles.DelayableGameProfile;
import io.blodhgarm.personality.client.gui.utils.profiles.WrappedGameProfile;
import io.blodhgarm.personality.misc.pond.EntityComponentExtension;
import io.blodhgarm.personality.misc.pond.ShouldRenderNameTagExtension;
import io.blodhgarm.personality.mixin.EntityComponentAccessor;
import io.blodhgarm.personality.utils.Constants;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.EntityComponent;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.util.Util;
import org.slf4j.Logger;

import org.jetbrains.annotations.Nullable;
import java.util.Optional;
import java.util.UUID;

public class UIOps {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static GameProfile getProfile(@Nullable String playerUUID){
        return getProfile(playerUUID, false);
    }

    public static GameProfile getProfile(@Nullable String playerUUID, boolean threaded){
        DelayableGameProfile profile = getDelayedProfile(playerUUID);

        if(threaded){
            Util.getIoWorkerExecutor().submit(profile);
        } else {
            profile.run();
        }

        return profile;
    }

    public static DelayableGameProfile getDelayedProfile(@Nullable String playerUUID){
        var playerProfile = new WrappedGameProfile(Constants.ERROR_PROFILE);

        Runnable runnable = null;
        
        if(playerUUID != null) {
            Optional<PlayerListEntry> player = MinecraftClient.getInstance().getNetworkHandler().getPlayerList()
                    .stream()
                    .filter(e -> e.getProfile().getId().toString().equals(playerUUID))
                    .findAny();

            if (player.isPresent()) {
                playerProfile.setProfile(player.get().getProfile());
            } else {
                runnable = () -> {
                    try {
                        var profile = MinecraftClient.getInstance().getSessionService()
                                .fillProfileProperties(new GameProfile(UUID.fromString(playerUUID), Constants.ERROR_PROFILE.getName()), false);

                        if (!Constants.isErrored(profile)) playerProfile.setProfile(profile);
                    } catch (IllegalArgumentException exception){
                        LOGGER.error("[UIOps]: It seems that a playerUUID was attempted to be parsed but was found to be invalid: [UUID: {}]", playerUUID);

                        exception.printStackTrace();
                    }
                };
            }
        }

        return new DelayableGameProfile(playerProfile, runnable);
    }
    
    public static <E extends Entity> EntityComponent<E> playerEntityComponent(Sizing entitySizing, @Nullable String playerUUID){
        return playerEntityComponent(entitySizing, getProfile(playerUUID));
    }

    public static <E extends Entity> EntityComponent<E> playerEntityComponent(Sizing entitySizing, GameProfile profile){
        return Components.entity(entitySizing, createRenderablePlayerEntity(profile))
                .configure(ShouldRenderNameTagExtension.disable(
                        c -> ((EntityComponentExtension)c).removeXAngle(true)
                ));
    }

    public static <E extends Entity> EntityComponent<E> playerEntityComponent(Sizing entitySizing, DelayableGameProfile profile){
        Entity e = (profile.isRunnable()) ? MinecraftClient.getInstance().player : createRenderablePlayerEntity(profile);

        EntityComponent<E> component = (Components.entity(entitySizing, e)
                .configure(ShouldRenderNameTagExtension.disable(
                        c -> ((EntityComponentExtension)c).removeXAngle(true)
                )));

        Util.getIoWorkerExecutor()
                .submit(profile.wrapRunnable((profile1) -> ((EntityComponentAccessor<E>) component).setEntity((E) createRenderablePlayerEntity(profile1))));

        return component;
    }
    
    private static Entity createRenderablePlayerEntity(GameProfile profile){
        Entity e = EntityComponent.createRenderablePlayer(profile);

        if(profile == null || Constants.isErrored(profile)) {
            ClientReflectionUtils.editRenderablePlayerEntity(e, Constants.MISSING_SKIN_TEXTURE_ID, "default");
        }
        
        return e;
    }
}
