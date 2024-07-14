package io.blodhgarm.personality.api.addon.client;

import com.mojang.authlib.GameProfile;
import io.blodhgarm.personality.api.character.BaseCharacter;
import io.blodhgarm.personality.client.gui.CharacterViewMode;
import io.blodhgarm.personality.client.gui.screens.CharacterViewScreen;
import net.minecraft.util.Identifier;

import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * A Class where all {@link PersonalityScreenAddon} implementations can register
 * the needed Factory to build the addon for the {@link CharacterViewScreen}
 */
public class PersonalityScreenAddonRegistry {

    public static Map<Identifier, AddonFactory<PersonalityScreenAddon>> ALL_SCREEN_ADDONS = new HashMap<>();

    /**
     * Method to which you can register the Addon Factory for your addon
     * @param addonId The Screen Addons Identifier
     * @param factory The Factory to build the Screen Addon
     */
    public static void registerScreenAddon(Identifier addonId, AddonFactory<PersonalityScreenAddon> factory){
        ALL_SCREEN_ADDONS.put(addonId, factory);
    }

    public interface AddonFactory<T extends PersonalityScreenAddon> {
        T buildAddon(CharacterViewMode mode, @Nullable GameProfile playerProfile, @Nullable BaseCharacter character);
    }
}
