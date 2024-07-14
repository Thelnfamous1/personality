package io.blodhgarm.personality.client.gui.components.character;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.blodhgarm.personality.Networking;
import io.blodhgarm.personality.api.addon.BaseAddon;
import io.blodhgarm.personality.api.addon.client.AddonObservable;
import io.blodhgarm.personality.api.addon.client.PersonalityScreenAddon;
import io.blodhgarm.personality.api.addon.client.PersonalityScreenAddonRegistry;
import io.blodhgarm.personality.api.character.BaseCharacter;
import io.blodhgarm.personality.api.character.Character;
import io.blodhgarm.personality.client.ClientCharacters;
import io.blodhgarm.personality.client.gui.CharacterViewMode;
import io.blodhgarm.personality.client.gui.GenderSelection;
import io.blodhgarm.personality.client.gui.ThemeHelper;
import io.blodhgarm.personality.client.gui.components.ColorableTextBoxComponent;
import io.blodhgarm.personality.client.gui.components.EditBoxComponent;
import io.blodhgarm.personality.client.gui.utils.UIOps;
import io.blodhgarm.personality.client.gui.utils.owo.ExtraSurfaces;
import io.blodhgarm.personality.client.gui.utils.owo.layout.ButtonAddon;
import io.blodhgarm.personality.client.gui.utils.polygons.ComponentAsPolygon;
import io.blodhgarm.personality.misc.pond.owo.AnimationExtension;
import io.blodhgarm.personality.misc.pond.owo.ButtonAddonDuck;
import io.blodhgarm.personality.misc.pond.owo.InclusiveBoundingArea;
import io.blodhgarm.personality.packets.SyncC2SPackets;
import io.wispforest.owo.ui.base.BaseParentComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.DiscreteSliderComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.util.ScissorStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CursorMovement;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.stat.Stats;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.slf4j.Logger;

import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class CharacterViewComponent extends FlowLayout implements AddonObservable {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final List<Identifier> implementedAddons = List.of(
            new Identifier("origins", "origin_selection_addon"),
            new Identifier("pehkui", "scale_selection_addon")
    );

    //------------------------

    private final Map<Identifier, PersonalityScreenAddon> screenAddons = new HashMap<>();

    private GenderSelection currentSelection = GenderSelection.MALE;

    //------------------------

    private final CharacterViewMode currentMode;

    @Nullable private final BaseCharacter currentCharacter;

    private final GameProfile playerProfile;

    //------------------------

    public CharacterViewComponent(CharacterViewMode currentMode, GameProfile playerProfile, @Nullable BaseCharacter character) {
        super(Sizing.content(), Sizing.content(), Algorithm.HORIZONTAL);

        this.currentMode = currentMode;

        this.playerProfile = playerProfile;
        this.currentCharacter = character;

        PersonalityScreenAddonRegistry.ALL_SCREEN_ADDONS
                .forEach((identifier, addonFactory) -> screenAddons.put(identifier, addonFactory.buildAddon(this.currentMode, this.playerProfile, this.currentCharacter).linkAddon(this)));
    }

    public CharacterViewComponent buildComponent(FlowLayout rootComponent, boolean adminMode) {
        return this.buildComponent(rootComponent, adminMode, false, () -> {});
    }

    public CharacterViewComponent buildComponent(FlowLayout rootComponent, boolean adminMode, boolean buildAsScreen, Runnable closeFunc) {
        boolean isModifiable = this.currentMode.isModifiableMode();
        boolean importCharacterData = this.currentMode.importFromCharacter();

        this.allowOverflow(true)
                .id("main_flow_layout");

        //--------------------------- Right Panel ----------------------------

        //-- Name Property --
        MutableText nameText = Text.empty()
                .append(isModifiable ? Text.literal("*").formatted(Formatting.BOLD, Formatting.RED) : Text.empty())
                .append(Text.literal("Name: "))
                .append(!isModifiable ? currentCharacter.getFormattedName() : Text.empty());

        FlowLayout namePropertyLayout = Containers.horizontalFlow(Sizing.content(), Sizing.content())
                .child(Components.label(nameText)
                        .maxWidth(144)
                        .color(ThemeHelper.dynamicColor())
                ).configure(layout -> {
                    if (!isModifiable)  return;

                    layout.child(
                            ColorableTextBoxComponent.textBox(Sizing.fixed(107), importCharacterData ? currentCharacter.getName() : "") //132
                                    .bqColor(Color.ofArgb(0xFF555555))
                                    .configure(c -> { if(c instanceof ColorableTextBoxComponent widget){ widget.setMaxLength(40); }})
                                    .id("character_name")
                            );
                });
        //-----------------------

        //-- Age Property --
        int age = importCharacterData ? currentCharacter.getAge() : -1;

        MutableText ageLabel = Text.empty()
                .append(Text.literal("Age: "))
                .append(!isModifiable ? Text.literal(age > 0 ? age + " Years" : "Unknown") : Text.empty());

        FlowLayout agePropertyLayout = Containers.horizontalFlow(Sizing.content(), Sizing.content())
                .child(
                        Components.label(ageLabel)
                                .color(ThemeHelper.dynamicColor())
                                .margins(Insets.right(6))
                ).configure(layout -> {
                    if(!isModifiable) return;

                    layout.child(
                            Components.discreteSlider(Sizing.fixed(114), 17, 60) //134
                                    .setFromDiscreteValue(importCharacterData ? age : 17)
                                    .snap(true)
                                    .id("age_slider")
                            );
                });
        //------------------

        //-- Final Layout of Right Panel --
        FlowLayout characterPropertiesContainer = Containers.verticalFlow(Sizing.content(), Sizing.content())
                .child(namePropertyLayout
                        .horizontalAlignment(HorizontalAlignment.LEFT)
                        .verticalAlignment(VerticalAlignment.CENTER)
                        .margins(Insets.bottom(8))
                )
                .child(agePropertyLayout
                        .verticalAlignment(VerticalAlignment.CENTER)
                        .margins(Insets.bottom(8))
                )
                .child(
                        createGenderComponent(isModifiable, importCharacterData) //Gender Properties
                )
                .child(
                        Containers.verticalFlow(Sizing.content(), Sizing.content())
                                .child(
                                        Components.label(Text.of("Physical Description: "))
                                                .color(ThemeHelper.dynamicColor())
                                                .margins(Insets.of(0, 4, 0, 0))
                                )
                                .child(
                                        EditBoxComponent.editBox(Sizing.fixed(136), Sizing.fixed(60), Text.of(""), Text.of(""), importCharacterData ? currentCharacter.getDescription() : "")
                                                .setCursorPosition(CursorMovement.ABSOLUTE, 0)
                                                .textWidth(130)
                                                .bqColor(Color.ofArgb(0xFF555555))
                                                .canEdit(isModifiable)
                                                .configure(component -> {
                                                    if(component instanceof EditBoxComponent betterEditBoxWidget){
                                                        betterEditBoxWidget.setMaxLength(5000);
                                                    }

//                                                    component.horizontalSizing().animate(1000, Easing.CUBIC, Sizing.fixed(236)).forwards();
                                                })
                                                .id("description_text_box")
                                )
                                .margins(Insets.bottom(8))
                )
                .child(
                        Containers.verticalFlow(Sizing.content(), Sizing.content())
                                .child(
                                        Components.label(Text.of("Bio: "))
                                                .color(ThemeHelper.dynamicColor())
                                                .margins(Insets.of(0, 4, 0, 0))
                                )
                                .child(
                                        EditBoxComponent.editBox(Sizing.fixed(136), Sizing.fixed(60), Text.of(""), Text.of(""), importCharacterData ? currentCharacter.getBiography() : "")
                                                .setCursorPosition(CursorMovement.ABSOLUTE, 0)
                                                .textWidth(130)
                                                .bqColor(Color.ofArgb(0xFF555555))
                                                .canEdit(isModifiable)
                                                .configure(component -> {
                                                    if(component instanceof EditBoxComponent betterEditBoxWidget){
                                                        betterEditBoxWidget.setMaxLength(5000);
                                                    }

//                                                    component.horizontalSizing().animate(1000, Easing.CUBIC, Sizing.fixed(236)).forwards();
                                                })
                                                .id("biography_text_box")
                                )
                );

        //---- Add pehkui branch component specified with the given index
        if(screenAddons.containsKey(implementedAddons.get(1))){
            characterPropertiesContainer.child(2, screenAddons.get(implementedAddons.get(1)).addBranchComponent(rootComponent));
        }
        //----

        //---- Add AdminScreen Only uuid information
        if(!isModifiable && adminMode){
            BiFunction<String, String, FlowLayout> layoutFunc = (label, uuid) -> {
                return ((ButtonAddonDuck<FlowLayout>) Containers.horizontalFlow(Sizing.content(), Sizing.content()))
                        .setButtonAddon(flowLayout -> {
                            return new ButtonAddon<>(flowLayout)
                                .useCustomButtonSurface((addon, context, component) -> {
                                    ScissorStack.drawUnclipped(() -> {
                                        if (!addon.isHovered()) return;

                                        context.drawRectOutline(component.x() - 2, component.y() - 2, component.width() + 4, component.height() + 4, Color.WHITE.argb());
                                    });
                                })
                                .onPress(button -> MinecraftClient.getInstance().keyboard.setClipboard(uuid));
                        }).child(
                                Components.label(Text.empty().append(Text.literal(label + uuid)))
                                        .color(ThemeHelper.dynamicColor())
                        );
            };

            characterPropertiesContainer
                    .children(1,
                            List.of(
                                    Containers.horizontalScroll(Sizing.fixed(144), Sizing.fixed(13), layoutFunc.apply("uuid: ", currentCharacter.getUUID()))
                                            .horizontalAlignment(HorizontalAlignment.LEFT)
                                            .verticalAlignment(VerticalAlignment.TOP)
                                            .margins(Insets.bottom(4)),
                                    Containers.horizontalScroll(Sizing.fixed(144), Sizing.fixed(13), layoutFunc.apply("Player uuid: ", currentCharacter.getPlayerUUID()))
                                            .horizontalAlignment(HorizontalAlignment.LEFT)
                                            .verticalAlignment(VerticalAlignment.TOP)
                                            .margins(Insets.bottom(4))
                            )
                    );
        }
        //----

        //Add any branch components from the various addons
        screenAddons.entrySet()
                .stream()
                .filter(entry -> !implementedAddons.contains(entry.getKey()))
                .forEach(entry -> characterPropertiesContainer
                        .child(entry.getValue().addBranchComponent(characterPropertiesContainer))
                );
        //------------------


        //---------------------------- Left Panel ----------------------------

        //--- Player View ---
        boolean originAddonExists = screenAddons.containsKey(implementedAddons.get(0));

        FlowLayout playerDisplayComponent = Containers.verticalFlow(Sizing.content(), Sizing.fixed(149))
                .configure((FlowLayout layout) -> {
                    if(originAddonExists) layout.child(screenAddons.get(implementedAddons.get(0)).addBranchComponent(rootComponent));
                })
                .child(
                        UIOps.playerEntityComponent(Sizing.fixed(originAddonExists ? 90 : 110), playerProfile)
                                .scale(originAddonExists ? 0.45F : 0.44F)
                                //.scaleToFit(true)
                                .allowMouseRotation(true)
                                .margins(originAddonExists
                                         ? Insets.of(5, 4, 0, 0)
                                         : Insets.of(20, 5, 0, 0)
                                )
                );

        if(this.currentMode.importFromCharacter()){
            BaseCharacter.Health health = currentCharacter.getHealthStage();

            playerDisplayComponent.child(
                    Containers.horizontalFlow(Sizing.content(), Sizing.content())
                            .child(Components.label(Text.literal("Health: ")))
                            .child(
                                    Components.label(health.getLabel())
                                            .color(health.getColor())
                            )
            );
        }

        //------------------

        //-- Final Layout of left Panel --
        FlowLayout mainOptionsSection = Containers.horizontalFlow(Sizing.content(), Sizing.content())
                .child(
                        Containers.verticalFlow(Sizing.content(), Sizing.content())
                                .child(
                                        playerDisplayComponent
                                                .surface(ExtraSurfaces.INVERSE_PANEL)
                                                .horizontalAlignment(HorizontalAlignment.CENTER)
                                                .margins(Insets.right(4))
                                )
                )
                .child(
                        Containers.verticalScroll(Sizing.content(), Sizing.fixed(149), characterPropertiesContainer)
                                .surface(ThemeHelper.dynamicSurface())
                                .padding(Insets.of(6))
                );
        //------------------


        //--------- Finalization Of Layout ---------
        FlowLayout characterPanel = Containers.verticalFlow(Sizing.content(), Sizing.fixed(182)) //Sizing.fixed(326)
                .configure((FlowLayout layout) -> {
                    layout.horizontalAlignment(HorizontalAlignment.CENTER)
                            .padding(Insets.of(6))
                            .surface(ThemeHelper.dynamicSurface());
                })
                .child(
                        Components.label(Text.of("Create Your Character"))
                                .color(ThemeHelper.dynamicColor())
                                .margins(Insets.of(1, 0, 0,0))
                )
                .child(
                        Components.box(Sizing.fixed(252), Sizing.fixed(1))
                                .color(Color.ofArgb(0xFFa0a0a0))
                                .margins(Insets.of(3, 4, 0, 0))
                )
                .child(mainOptionsSection);

        FlowLayout editingLayout = null;

        if(this.currentMode.isModifiableMode()) {
            boolean editing = this.currentMode == CharacterViewMode.EDITING;

            FlowLayout buttonLayout = Containers.horizontalFlow(Sizing.content(), Sizing.content())
                    .child(
                            Components.button(Text.of(this.currentMode.isModifiableMode() ? "Done" : "Close"),
                                            (ButtonComponent button) -> {
                                                if (this.currentMode.isModifiableMode() && !this.finishCharacterCreation(rootComponent)) {
                                                    //TODO: Handle this better with custom info on what is missing

                                                    MinecraftClient.getInstance().getToastManager()
                                                            .add(new SystemToast(
                                                                    SystemToast.Type.PERIODIC_NOTIFICATION,
                                                                    Text.of("Missing Information for Character"),
                                                                    Text.of("Your character is missing required information to be created!"))
                                                            );

                                                    return;
                                                }

                                                closeFunc.run();
                                            }
                                    ).horizontalSizing(Sizing.fixed(editing ? 48 : 100))
                                    .margins(Insets.top(6))
                    );

            if(editing){
                buttonLayout.child(0, Components.button(Text.of("Back"), (ButtonComponent button) -> closeFunc.run())
                        .horizontalSizing(Sizing.fixed(48))
                        .margins(Insets.of(6, 0, 0,4)));
            }

            editingLayout = (FlowLayout) Containers.verticalFlow(Sizing.content(), Sizing.content())
                    .child(characterPanel)
                    .child(buttonLayout)
                    .horizontalAlignment(HorizontalAlignment.CENTER);
        }

        this.child(editingLayout != null ? editingLayout : characterPanel);

        //-------------------------------------------------------------------------


        //---------------------- Screen Only Manipulation ----------------------

        if(buildAsScreen) {
            this.positioning(Positioning.relative(50, -175));

            this.positioning().animate(1000, Easing.CUBIC, Positioning.relative(50, 50)).forwards();

            rootComponent.surface(Surface.VANILLA_TRANSLUCENT);

            if(this.currentMode.importFromCharacter()) {
                characterPanel.child(
                        Components.button(Text.literal("❌").formatted(ThemeHelper.dynamicTextColor()), (ButtonComponent component) -> closeFunc.run())
                                .textShadow(ThemeHelper.isDarkMode())
                                .renderer(ButtonComponent.Renderer.flat(0, 0, 0))
                                .sizing(Sizing.fixed(12))
                                .positioning(Positioning.relative(100, 0))
                );
            }
        }

        //---------------------------------- END ----------------------------------

        return this;
    }

    private Component createGenderComponent(boolean modifiable, boolean importCharacterData){
        MutableText text = Text.literal("Gender: ");

        if (!modifiable) text.append(Text.of(currentCharacter.getGender().replace("_", " ")));

        FlowLayout mainLayout = Containers.horizontalFlow(Sizing.content(), Sizing.content())
                .child(Components.label(text).color(ThemeHelper.dynamicColor()));

        if(modifiable) {
            if (importCharacterData) currentSelection = GenderSelection.attemptToGetGender(currentCharacter.getGender());

            ButtonComponent buttonComponent = Components.button(currentSelection.translation(), (ButtonComponent button) -> {
                currentSelection = currentSelection.getNextSelection();

                button.setMessage(currentSelection.translation());
                button.horizontalSizing(Sizing.fixed(currentSelection.textSizing() + 10));

                if (currentSelection.openTextField()) {
                    mainLayout.child(createGenderTextField(importCharacterData));
                } else {
                    ColorableTextBoxComponent child = mainLayout.childById(ColorableTextBoxComponent.class, "gender_text_field");

                    if (child != null) mainLayout.removeChild(child);
                }
            });

            mainLayout
                    .child(buttonComponent
                            .horizontalSizing(Sizing.fixed(currentSelection.textSizing() + 10))
                            .margins(Insets.of(1, 1, 0, 4))
                    ).verticalAlignment(VerticalAlignment.CENTER);

            if (currentSelection.openTextField()) mainLayout.child(createGenderTextField(importCharacterData));
        }

        return mainLayout.margins(Insets.bottom(8));
    }

    private Component createGenderTextField(boolean importCharacterData){
        return ColorableTextBoxComponent.textBox(Sizing.fixed(58), importCharacterData ? currentCharacter.getGender() : "")
                .setEditAbility(currentSelection.openTextField())
                .tooltip(Text.of("Required"))
                .configure(component -> { if(component instanceof ColorableTextBoxComponent widget) widget.setMaxLength(100); })
                .id("gender_text_field");
    }

    @Override
    public void pushScreenAddon(PersonalityScreenAddon screenAddon){
        FlowLayout addonMainFlow = this.childById(FlowLayout.class, "current_addon_screen");

        boolean shouldReturn = false;

        Positioning position = ((AnimationExtension<Positioning>) this.positioning().animation()).getCurrentValue();

        if(addonMainFlow != null){
            FlowLayout finalAddonMainFlow = addonMainFlow;

            finalAddonMainFlow.positioning().animation().backwards()
                    .finished().subscribe((direction, looping) -> {
                        this.removeChild(finalAddonMainFlow);
                    });

            shouldReturn = finalAddonMainFlow.childById(Component.class, screenAddon.addonId().toString()) != null;
        }

        if(shouldReturn || screenAddon == null){
            this.positioning().animate(1000, Easing.CUBIC, Positioning.relative(50, position.y)).forwards()
                    .finished().subscribe((direction, looping) -> {
//                        EditBoxComponent descBox = this.childById(EditBoxComponent.class, "description_text_box");
//
//                        if(descBox != null && descBox.horizontalSizing().animation() != null){
//                            descBox.horizontalSizing().animation().forwards();
//                        }
//
//                        EditBoxComponent bioBox = this.childById(EditBoxComponent.class, "biography_text_box");
//
//                        if(bioBox != null && bioBox.horizontalSizing().animation() != null){
//                            bioBox.horizontalSizing().animation().forwards();
//                        }
                    });

            return;
        }

        addonMainFlow = (FlowLayout) Containers.verticalFlow(Sizing.content(), Sizing.content())
                .child(screenAddon.createMainFlowlayout(ThemeHelper.isDarkMode()))
                .id("current_addon_screen");

        this.child(addonMainFlow.positioning(Positioning.relative(500, 0)));

        ((InclusiveBoundingArea<FlowLayout>) this).addInclusionZone(new ComponentAsPolygon(addonMainFlow));

        var currentScreen = MinecraftClient.getInstance().currentScreen;

        int targetPosition = 50;

        float screenSpaceRatio = (currentScreen.width - (this.width() + addonMainFlow.width())) / (float) (currentScreen.width - this.width());

        this.positioning().animate(1000, Easing.CUBIC, Positioning.relative(Math.round(screenSpaceRatio * targetPosition), position.y)).forwards();

        addonMainFlow.positioning().animate(1000, Easing.CUBIC, Positioning.relative(250, 0)).forwards();

        //----

//        EditBoxComponent descBox = this.childById(EditBoxComponent.class, "description_text_box");
//
//        if(descBox != null && descBox.horizontalSizing().animation() != null){
//            descBox.horizontalSizing().animation().backwards();
//        }
//
//        EditBoxComponent bioBox = this.childById(EditBoxComponent.class, "biography_text_box");
//
//        if(bioBox != null && bioBox.horizontalSizing().animation() != null){
//            bioBox.horizontalSizing().animation().backwards();
//        }
    }

    @Override
    public boolean isAddonOpen(PersonalityScreenAddon screenAddon){
        FlowLayout addonMainFlow = this.childById(FlowLayout.class, "current_addon_screen");

        return addonMainFlow != null && addonMainFlow.childById(Component.class, screenAddon.addonId().toString()) != null;
    }

    private boolean finishCharacterCreation(BaseParentComponent rootComponent){
        String name = rootComponent.childById(TextFieldWidget.class, "character_name").getText();

        if(name.isEmpty()) return false;

        String gender = currentSelection != GenderSelection.OTHER
                ? currentSelection.translation().getString()
                : rootComponent.childById(TextFieldWidget.class, "gender_text_field").getText();

        if(gender.isBlank()) return false;

        ClientPlayerEntity entity = MinecraftClient.getInstance().player;

        String description = rootComponent.childById(EditBoxComponent.class, "description_text_box").getText();

        String biography = rootComponent.childById(EditBoxComponent.class, "biography_text_box").getText();

        int age = (int) rootComponent.childById(DiscreteSliderComponent.class, "age_slider").discreteValue();

        int activityOffset = entity.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME));

        //-----------------------------Addon Data-----------------------------

        Map<Identifier, BaseAddon> addonData = new HashMap<>();

        for(Map.Entry<Identifier, PersonalityScreenAddon> entry : screenAddons.entrySet()){
            PersonalityScreenAddon addon = entry.getValue();

            if(addon.isDataEmpty(rootComponent) && addon.requiresUserInput()) return false;

            addonData.putAll(addon.getAddonData());
        }

        Map<Identifier, String> addonDataJson = Util.make(new HashMap<>(), map -> addonData.forEach((id, addon) -> map.put(id, ClientCharacters.INSTANCE.getGson().toJson(addon))));

        Character character = new Character(this.currentCharacter != null ? this.currentCharacter.getUUID() : UUID.randomUUID().toString(), entity.getUuidAsString(), name, gender, description, biography, age);

        if(this.currentMode == CharacterViewMode.EDITING){
            if(this.currentCharacter == null) return false;

            List<String> changedValues = new ArrayList<>();

            Map<String, Supplier<Boolean>> mapInteraction = Map.of(
                    "gender", () -> this.currentCharacter.getGender().equals(gender),
                    "name", () -> this.currentCharacter.getName().equals(name),
                    "description", () -> this.currentCharacter.getDescription().equals(description),
                    "biography", () -> this.currentCharacter.getBiography().equals(biography),
                    "age", () -> this.currentCharacter.getAge() == age
            );

            boolean baseCharacterDataChanged = false;

            for (Map.Entry<String, Supplier<Boolean>> entry : mapInteraction.entrySet()) {
                if(entry.getValue().get()) continue;

                baseCharacterDataChanged = true;

                changedValues.add(entry.getKey());
            }

            boolean addonDataChanged = false;

            for(Map.Entry<Identifier, BaseAddon> entry : addonData.entrySet()){
                BaseAddon addon = this.currentCharacter.getAddon(entry.getKey());

                if(entry.getValue().equals(addon)) continue;

                addonDataChanged = true;

                changedValues.add(entry.getKey().toString());
            }

            var value = (baseCharacterDataChanged ? 1 : 0) + (addonDataChanged ? 2 : 0);

            /*
             *          Switch Key
             * ---------------------------------------------
             * 0 | Nothing has changed, do nothing
             * 1 | Only Base Character data has changed
             * 2 | Only Addon data Has changed
             * 3 | Both Base Character and Addon data has been changed
             */

            switch (value){
                case 0 -> {} //Nothing to change so return
                case 1 -> Networking.sendC2S(new SyncC2SPackets.ModifyBaseCharacterData(ClientCharacters.INSTANCE.getGson().toJson(character), changedValues));
                case 2 -> Networking.sendC2S(new SyncC2SPackets.ModifyAddonData(character.getUUID(), addonDataJson, changedValues));
                case 3 -> Networking.sendC2S(new SyncC2SPackets.ModifyEntireCharacter(ClientCharacters.INSTANCE.getGson().toJson(character), character.getUUID(), addonDataJson, changedValues));
            }

        } else {
            Networking.sendC2S(new SyncC2SPackets.NewCharacter(ClientCharacters.INSTANCE.getGson().toJson(character), addonDataJson, true));
        }

        return true;
    }

    //-------------------------------------------------------
}
