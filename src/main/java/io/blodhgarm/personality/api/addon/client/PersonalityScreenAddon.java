package io.blodhgarm.personality.api.addon.client;

import com.mojang.authlib.GameProfile;
import io.blodhgarm.personality.api.character.BaseCharacter;
import io.blodhgarm.personality.api.addon.BaseAddon;
import io.blodhgarm.personality.client.gui.CharacterViewMode;
import io.wispforest.owo.ui.base.BaseParentComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.util.Identifier;

import org.jetbrains.annotations.Nullable;
import java.util.Map;

public abstract class PersonalityScreenAddon {

    public final Identifier addonId;

    private AddonObservable observable = null;

    protected final CharacterViewMode mode;

    @Nullable protected final BaseCharacter character;

    protected final GameProfile playerProfile;

    private BaseParentComponent rootBranchComponent = null;

    public PersonalityScreenAddon(CharacterViewMode mode, GameProfile playerProfile, @Nullable BaseCharacter character, Identifier addonId){
        this.mode = mode;

        this.character = character;
        this.playerProfile = playerProfile;

        this.addonId = addonId;
    }

    public Identifier addonId(){
        return addonId;
    }

    public final FlowLayout createMainFlowlayout(boolean darkMode){
        return (FlowLayout) Containers.verticalFlow(Sizing.content(), Sizing.content())
                .child(this.build(darkMode))
                .id(this.addonId().toString());
    }

    public final PersonalityScreenAddon linkAddon(AddonObservable observable){
        this.observable = observable;

        return this;
    }

    public final Component addBranchComponent(BaseParentComponent rootComponent){
        this.rootBranchComponent = rootComponent;

        return buildBranchComponent(rootComponent);
    }

    public BaseParentComponent getRootComponent(){
        return rootBranchComponent;
    }

    public final void closeAddon(){
        this.observable.pushScreenAddon(this);
    }

    public final AddonObservable getObserver(){
        return this.observable;
    }

    //-------------------------------------------------------------------------------

    public boolean requiresUserInput(){
        return false;
    }

    public boolean hasSideScreenComponent(){
        return true;
    }

    /**
     * Method used to build the main portion of the addon Screen
     */
    public abstract FlowLayout build(boolean darkMode);

    /**
     * Method used to add the component that will toggle the addons side screen
     */
    protected abstract Component buildBranchComponent(BaseParentComponent rootBranchComponent);

    /**
     * Used to update the given branch component
     */
    public abstract void branchUpdate();

    public abstract Map<Identifier, BaseAddon> getAddonData();

    public abstract boolean isDataEmpty(BaseParentComponent rootComponent);
}
