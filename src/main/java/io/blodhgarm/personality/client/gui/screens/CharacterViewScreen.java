package io.blodhgarm.personality.client.gui.screens;

import com.mojang.authlib.GameProfile;
import io.blodhgarm.personality.api.character.BaseCharacter;
import io.blodhgarm.personality.client.gui.CharacterViewMode;
import io.blodhgarm.personality.client.gui.components.character.CharacterViewComponent;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import org.jetbrains.annotations.Nullable;

public class CharacterViewScreen extends BaseOwoScreen<FlowLayout> {

    private final CharacterViewMode currentMode;

    @Nullable private final BaseCharacter currentCharacter;
    private final GameProfile playerProfile;

    private boolean adminMode = false;
    private Screen originScreen = null;

    public CharacterViewScreen(CharacterViewMode currentMode, GameProfile playerProfile, @Nullable BaseCharacter character) {
        this.currentMode = currentMode;

        this.playerProfile = playerProfile;
        this.currentCharacter = character;
    }

    public CharacterViewScreen adminMode(boolean value){
        this.adminMode = value;

        return this;
    }

    public CharacterViewScreen setOriginScreen(Screen originScreen){
        this.originScreen = originScreen;

        return this;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.child(
                new CharacterViewComponent(currentMode, playerProfile, currentCharacter)
                        .buildComponent(rootComponent, adminMode, true, this::close)
        );
    }

    @Override
    public void close() {
        this.client.setScreen(this.originScreen);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if((modifiers & GLFW.GLFW_MOD_CONTROL) == 2 && (modifiers & GLFW.GLFW_MOD_ALT) == 4 && keyCode == GLFW.GLFW_KEY_R){
            this.uiAdapter = null;
            this.clearAndInit();

            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return currentMode.importFromCharacter();
    }
}
