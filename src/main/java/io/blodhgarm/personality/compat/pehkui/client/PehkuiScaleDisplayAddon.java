package io.blodhgarm.personality.compat.pehkui.client;

import com.mojang.authlib.GameProfile;
import io.blodhgarm.personality.api.character.BaseCharacter;
import io.blodhgarm.personality.api.addon.BaseAddon;
import io.blodhgarm.personality.api.addon.client.PersonalityScreenAddon;
import io.blodhgarm.personality.client.gui.ThemeHelper;
import io.blodhgarm.personality.client.gui.CharacterViewMode;
import io.blodhgarm.personality.compat.pehkui.PehkuiAddonRegistry;
import io.blodhgarm.personality.compat.pehkui.ScaleAddon;
import io.wispforest.owo.ui.base.BaseParentComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.DiscreteSliderComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

public class PehkuiScaleDisplayAddon extends PersonalityScreenAddon {

    private double startingValue = 0.0;

    @Nullable private ScaleAddon scaleAddon = null;

    public PehkuiScaleDisplayAddon(CharacterViewMode mode, GameProfile playerProfile, @Nullable BaseCharacter character) {
        super(mode, playerProfile, character, new Identifier("pehkui", "scale_selection_addon"));

        if(mode.importFromCharacter()){
            scaleAddon = (ScaleAddon) character.getAddon(PehkuiAddonRegistry.addonId);

            if(scaleAddon != null) startingValue = scaleAddon.getHeightOffset();
        }
    }

    @Override
    public boolean hasSideScreenComponent() {
        return false;
    }

    @Override
    public FlowLayout build(boolean darkMode) {
        return Containers.verticalFlow(Sizing.content(), Sizing.content());
    }

    @Override
    public Component buildBranchComponent(BaseParentComponent rootBranchComponent) {
        boolean modifiable = this.mode.isModifiableMode();

        MutableText text = Text.literal("Height: ");

        if(!modifiable && scaleAddon != null) {
            //TODO: TRANSLATIONS!
            text.append(
                    Text.literal(scaleAddon.shouldShowHeight()
                            ? String.format("%.2fm", scaleAddon.getHeightOffset() + 1.8)
                            : "Unknown")
            );
        }

        FlowLayout layout = Containers.horizontalFlow(Sizing.content(), Sizing.content())
                .child(
                        Components.label(text)
                                .color(ThemeHelper.dynamicColor())
                );

        if(modifiable) {
            layout.child(
                    Components.discreteSlider(Sizing.fixed(108), -0.5f, 0.5f) //128
                            .decimalPlaces(2)
                            .snap(true)
                            .setFromDiscreteValue(startingValue)
                            .message(s -> {
                                if (!s.startsWith("-") && !s.equals("0.00")) {
                                    s = "+" + s;
                                }
                                return Text.literal(s);
                            })
                            .id("height_slider")
            );
        }

        return layout
                .verticalAlignment(VerticalAlignment.CENTER)
                .margins(Insets.bottom(8));
    }

    @Override
    public void branchUpdate() {}

    @Override
    public Map<Identifier, BaseAddon> getAddonData() {
        Map<Identifier, BaseAddon> addonData = new HashMap<>();

        float heightOffset = (float) getRootComponent().childById(DiscreteSliderComponent.class, "height_slider").discreteValue();

        addonData.put(new Identifier("pehkui", "height_modifier"), new ScaleAddon(heightOffset));

        return addonData;
    }

    @Override
    public boolean isDataEmpty(BaseParentComponent rootComponent) {
        return false;
    }
}
