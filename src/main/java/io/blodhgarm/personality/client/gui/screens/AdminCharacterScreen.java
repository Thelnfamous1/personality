package io.blodhgarm.personality.client.gui.screens;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import io.blodhgarm.personality.Networking;
import io.blodhgarm.personality.PersonalityMod;
import io.blodhgarm.personality.api.character.Character;
import io.blodhgarm.personality.client.ClientCharacters;
import io.blodhgarm.personality.client.gui.CharacterViewMode;
import io.blodhgarm.personality.client.gui.ThemeHelper;
import io.blodhgarm.personality.client.gui.components.character.CharacterBasedGridLayout;
import io.blodhgarm.personality.client.gui.components.grid.LabeledGridLayout;
import io.blodhgarm.personality.client.gui.components.grid.MultiToggleButton;
import io.blodhgarm.personality.client.gui.components.grid.SearchbarComponent;
import io.blodhgarm.personality.client.gui.screens.utility.ConfirmationScreen;
import io.blodhgarm.personality.client.gui.screens.utility.PlayerSelectionScreen;
import io.blodhgarm.personality.client.gui.utils.ModifiableCollectionHelper;
import io.blodhgarm.personality.client.gui.utils.UIOps;
import io.blodhgarm.personality.client.gui.utils.owo.ExtraSurfaces;
import io.blodhgarm.personality.client.gui.utils.owo.VariantButtonSurface;
import io.blodhgarm.personality.client.gui.utils.owo.layout.ButtonAddon;
import io.blodhgarm.personality.client.gui.utils.polygons.ComponentAsPolygon;
import io.blodhgarm.personality.client.gui.utils.profiles.DelayableGameProfile;
import io.blodhgarm.personality.misc.pond.owo.ButtonAddonDuck;
import io.blodhgarm.personality.misc.pond.owo.InclusiveBoundingArea;
import io.blodhgarm.personality.packets.AdminActionPackets.AssociateAction;
import io.blodhgarm.personality.packets.AdminActionPackets.CharacterBasedAction;
import io.blodhgarm.personality.packets.AdminActionPackets.DisassociateAction;
import io.blodhgarm.personality.server.PrivilegeManager;
import io.blodhgarm.personality.utils.Constants;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.collections4.list.UnmodifiableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class AdminCharacterScreen extends BaseOwoScreen<FlowLayout> implements ModifiableCollectionHelper<AdminCharacterScreen, Character> {

    private static final Identifier ADMIN_BUTTON_TEXTURE = PersonalityMod.id("textures/gui/admin_screen_buttons.png");

    private int charactersPerPage = 100;
    private int waitTimeToUpdatePages = -1;

    private int pageCount = 0;
    private int currentPageNumber = 1;

    private final List<Character> defaultCharacterView = UnmodifiableList.unmodifiableList(ClientCharacters.INSTANCE.characterLookupMap().valueList());
    private final List<Character> characterView = new ArrayList<>();

    private CharacterBasedGridLayout<Character> characterLayout;

    private final List<String> selectedCharacters = new ArrayList<>();

    @Nullable private FilterFunc<Character> cached_filter = null;
    @Nullable private Comparator<Character> cached_comparator = null;

    private final Deque<Runnable> componentChanges = new ArrayDeque<>();

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    public void tick() {
        while(componentChanges.peek() != null) componentChanges.pop().run();

        if(waitTimeToUpdatePages == 0){
            TextBoxComponent component = this.uiAdapter.rootComponent.childById(TextBoxComponent.class, "per_page_amount");

            if(!component.getText().isEmpty()) {
                try {
                    charactersPerPage = Integer.parseInt(component.getText());

                    buildPageBar(this.uiAdapter.rootComponent);

                    travelToSelectPage(1);
                } catch (NumberFormatException e) { throw new RuntimeException(e); }
            }

            waitTimeToUpdatePages = -1;
        } else if(waitTimeToUpdatePages > 0){
            waitTimeToUpdatePages--;
        }

        super.tick();
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        FlowLayout mainLayout = Containers.verticalFlow(Sizing.content(), Sizing.content())
                .configure((FlowLayout layout) -> {
                        layout.surface(ThemeHelper.dynamicSurface())
                                .horizontalAlignment(HorizontalAlignment.CENTER)
                                .padding(Insets.of(4))
                                .allowOverflow(true);
                }).child(
                        new SearchbarComponent<>(this, Character::getName, this::updateCharacterListComponent)
                                .configure((SearchbarComponent<?> component) -> {
                                        component.adjustTextboxWidth(Sizing.fixed(200))
                                                .verticalAlignment(VerticalAlignment.CENTER);
                                }).build()
                );

        //---- Admin Buttons

        List<Component> adminButtons = new ArrayList<>();

        BiFunction<List<GameProfile>, List<Character>, MutableText> textAppend = (gameProfiles, selectedCharacters) -> {
            MutableText mutableText = Text.empty();

            for (int i = 0; i < selectedCharacters.size(); i++) {
                boolean isSingleEntry = selectedCharacters.size() == 1;
                boolean showPlayerInfo = !gameProfiles.isEmpty();

                String label = (!isSingleEntry && !showPlayerInfo) ? String.valueOf(i + 1) : "Character";
                String spacer = isSingleEntry ? "" : "  ";

                if (!isSingleEntry) mutableText.append(Text.literal((i + 1) + ": \n").formatted(Formatting.BOLD, Formatting.GRAY));

                mutableText
                        .append(Text.literal(spacer + label + ": ").formatted(Formatting.BOLD))
                        .append(Text.of(selectedCharacters.get(0).getName()));

                if (showPlayerInfo) {
                    GameProfile profile = MinecraftClient.getInstance().getSessionService()
                            .fillProfileProperties(gameProfiles.get(i), false);

                    mutableText
                            .append(Text.literal("\n" + spacer + "Player: ").formatted(Formatting.BOLD))
                            .append(Text.of(profile.getName()));
                }

                if(i + 1 != selectedCharacters.size()) mutableText.append("\n");
            }

            return mutableText;
        };

        if(PrivilegeManager.PrivilegeLevel.MODERATOR.test(MinecraftClient.getInstance().player)){
            List<Component> components = List.of(
                //Associate Action
                buildAdminButton("Associate", 16, 3, component -> {
                    if(!shouldDoAdminAction("Associate", false)) return;

                    List<Character> selectedCharacters = this.getSelectedCharacters();

                    PlayerSelectionScreen screen = new PlayerSelectionScreen(this, playerEntities -> {
                        if(playerEntities.isEmpty()) return;

                        PlayerListEntry p = playerEntities.get(0);
                        Character c = selectedCharacters.get(0);

                        ConfirmationScreen confirmation = new ConfirmationScreen(this, () -> Networking.sendC2S(new AssociateAction(c.getUUID(), p.getProfile().getId().toString())))
                                .setLabel(Text.literal("Are you sure you want to Attempt to ").append(Text.literal("Associate:").formatted(Formatting.BOLD)))
                                .setBodyText(
                                        textAppend.apply(List.of(p.getProfile()), selectedCharacters)
//                                        Text.empty()
//                                        .append(Text.literal("Character: ").formatted(Formatting.BOLD)).append(Text.of(c.getName() + "\n"))
//                                        .append(Text.literal("Player: ").formatted(Formatting.BOLD)).append(Text.of(p.getProfile().getName() + ""))
                                );

                        MinecraftClient.getInstance().setScreen(confirmation);
                    }).setSingleSelection(true);

                    MinecraftClient.getInstance().setScreen(screen);
                }),
                //Disassociate Action
                buildAdminButton("Disassociate", 0, 3, component -> {
                    if(!shouldDoAdminAction("Disassociate", true)) return;

                    MutableText allCharactersText = Text.empty();

                    List<Character> selectedCharacters = this.getSelectedCharacters();

                    for (int i = 0; i < selectedCharacters.size(); i++) {
                        String label = selectedCharacters.size() == 1 ? "Character" : String.valueOf(i + 1);

                        allCharactersText
                                .append(Text.literal(label + ": ").formatted(Formatting.BOLD)).append(Text.of(selectedCharacters.get(0).getName() + "\n"));
                    }

                    Character c = selectedCharacters.get(0);
                    String playerUUID = ClientCharacters.INSTANCE.getPlayerUUID(c.getUUID());

                    if(playerUUID == null) {
                        SystemToast.add(
                                MinecraftClient.getInstance().getToastManager(),
                                SystemToast.Type.PERIODIC_NOTIFICATION,
                                Text.of("Character isn't Associated to anyone!"),
                                Text.of("The selected Character don't have any player associated to them.")
                        );

                        return;
                    }

                    ConfirmationScreen confirmation = new ConfirmationScreen(this, () -> Networking.sendC2S(new DisassociateAction(c.getUUID(), true)))
                            .setLabel(Text.literal("Are you sure you want to Attempt to ").append(Text.literal("Disassociate:").formatted(Formatting.BOLD)))
                            .setBodyText(
                                    textAppend.apply(selectedCharacters.stream()
                                            .map(c1 -> {
                                                String pUUIDstring = ClientCharacters.INSTANCE.getPlayerUUID(c1);

                                                UUID pUUID = pUUIDstring != null ? UUID.fromString(pUUIDstring) : null;

                                                return new GameProfile(pUUID, "[ERROR: UNKNOWN NAME]");
                                            }).toList(), selectedCharacters
                                    )
//                                    Text.empty()
//                                    .append(Text.literal("Character: ").formatted(Formatting.BOLD)).append(Text.of(c.getName() + "\n"))
//                                    .append(Text.literal("Player: ").formatted(Formatting.BOLD)).append(Text.of(playerProfile.getName() + ""))
                            );

                    MinecraftClient.getInstance().setScreen(confirmation);
                })
            );

            adminButtons.addAll(components);
        }

        if(PrivilegeManager.PrivilegeLevel.ADMIN.test(MinecraftClient.getInstance().player)){
            List<Component> components = List.of(
                    //Edit Action
                    buildAdminButton("Edit", 32, 3, component -> {
                        CharacterBasedGridLayout<Character> layout = this.uiAdapter.rootComponent.childById(CharacterBasedGridLayout.class, "character_list");

                        layout.changeMode((layout.getMode() == CharacterViewMode.VIEWING) ? CharacterViewMode.EDITING : CharacterViewMode.VIEWING);
                    }),
                    //Revive Action
                    buildAdminButton("Revive", 48, 3, component -> {
                        if(!shouldDoAdminAction("Revive", true)) return;

                        List<Character> selectedCharacters = this.getSelectedCharacters();

                        Consumer<@Nullable PlayerListEntry> reviveAction = (p) -> {
                            MutableText allCharactersText = Text.empty();

                            for (int i = 0; i < selectedCharacters.size(); i++) {
                                String label = selectedCharacters.size() == 1 ? "Character" : String.valueOf(i + 1);

                                allCharactersText
                                        .append(Text.literal(label + ": ").formatted(Formatting.BOLD))
                                        .append(Text.of(selectedCharacters.get(0).getName() + "\n"));
                            }

                            AtomicReference<String> pUUID = new AtomicReference<>("");

                            if(p != null){
                                pUUID.set(p.getProfile().getId().toString());
                                allCharactersText.append(Text.literal("Player: ").formatted(Formatting.BOLD)).append(Text.of(p.getProfile().getName() + ""));
                            }

                            ConfirmationScreen confirmation = new ConfirmationScreen(this, () -> Networking.sendC2S(new CharacterBasedAction(selectedCharacters.stream().map(Character::getUUID).toList(), "revive", pUUID.get())))
                                    .setLabel(Text.literal("Are you sure you want to Attempt to ").append(Text.literal("Revive:").formatted(Formatting.BOLD)))
                                    .setBodyText(Text.empty().append(allCharactersText));

                            MinecraftClient.getInstance().setScreen(confirmation);
                        };

                        if(selectedCharacters.size() == 1){
                            PlayerSelectionScreen screen = new PlayerSelectionScreen(this, playerEntities -> {
                                reviveAction.accept(!playerEntities.isEmpty() ? playerEntities.get(0) : null);
                            }).setSingleSelection(true)
                                    .allowForNoSelection(true);

                            MinecraftClient.getInstance().setScreen(screen);
                        } else {
                            reviveAction.accept(null);
                        }
                    }),
                    //Kill Action
                    buildAdminButton("Kill", 64, 3, component -> {
                        if(!shouldDoAdminAction("Kill", true)) return;

                        List<Character> selectedCharacters = this.getSelectedCharacters();

                        MutableText allCharactersText = Text.empty();

                        for (int i = 0; i < selectedCharacters.size(); i++) {
                            Character c = selectedCharacters.get(i);

                            String label = selectedCharacters.size() == 1 ? "Character" : String.valueOf(i + 1);

                            allCharactersText
                                    .append(Text.literal(label + ": ").formatted(Formatting.BOLD))
                                    .append(Text.of(c.getName() + "\n"));
                        }

                        ConfirmationScreen confirmation = new ConfirmationScreen(this, () -> Networking.sendC2S(new CharacterBasedAction(selectedCharacters.stream().map(Character::getUUID).toList(), "kill")))
                                .setLabel(Text.literal("Are you sure you want to Attempt to ").append(Text.literal("Kill:").formatted(Formatting.BOLD)))
                                .setBodyText(Text.empty().append(allCharactersText));

                        MinecraftClient.getInstance().setScreen(confirmation);
                    }),
                    //Delete Action
                    buildAdminButton("Delete", 80, 0, component -> {
                        if(!shouldDoAdminAction("Delete", true)) return;

                        List<Character> selectedCharacters = this.getSelectedCharacters();

                        MutableText allCharactersText = Text.empty();

                        for (int i = 0; i < selectedCharacters.size(); i++) {
                            Character c = selectedCharacters.get(i);

                            String label = selectedCharacters.size() == 1 ? "Character" : String.valueOf(i + 1);

                            allCharactersText
                                    .append(Text.literal(label + ": ").formatted(Formatting.BOLD)).append(Text.of(c.getName() + "\n"));
                        }

                        ConfirmationScreen confirmation = new ConfirmationScreen(this, () -> Networking.sendC2S(new CharacterBasedAction(selectedCharacters.stream().map(Character::getUUID).toList(), "delete")))
                                .setLabel(Text.literal("Are you sure you want to Attempt to ").append(Text.literal("Delete:").formatted(Formatting.BOLD)))
                                .setBodyText(Text.empty().append(allCharactersText));

                        MinecraftClient.getInstance().setScreen(confirmation);
                    })
            );

            adminButtons.addAll(components);
        }

        if(!adminButtons.isEmpty()){
            FlowLayout adminButtonSidebar = Containers.verticalFlow(Sizing.content(), Sizing.content())
                    .configure((FlowLayout component) -> {
                        component.padding(Insets.of(6))
                                .surface(ThemeHelper.dynamicSurface())
                                .verticalAlignment(VerticalAlignment.CENTER)
                                .positioning(Positioning.relative(-20, 50)); //30
                    })
                    .children(adminButtons);

            ((InclusiveBoundingArea<FlowLayout>) mainLayout)
                    .addInclusionZone(new ComponentAsPolygon(adminButtonSidebar))
                    .child(adminButtonSidebar);
        }

        //----

        List<MultiToggleButton> buttons = new ArrayList<>();

        //TODO: MULTI THREAD THIS TO PREVENT HUGE SPIKE THE STALES MINECRAFT
        CharacterBasedGridLayout<Character> characterLayout = new CharacterBasedGridLayout<Character>(Sizing.content(), Sizing.content(), this)
                .configure((CharacterBasedGridLayout<Character> layout) -> {
                    layout.openAsAdmin(true)
                            .setRowDividingLine(1)
                            .setColumnDividingLine(1)
                            .id("character_list");
                })
                .addBuilder(0,
                        isParentVertical -> {
                            return Containers.verticalFlow(Sizing.content(), Sizing.content())
                                    .child(buildButton(
                                            buttons,
                                            component -> this.sortEntries(null),
                                            component -> this.sortEntries((ch1, ch2) -> {
                                                boolean character1Selected = this.selectedCharacters.contains(ch1.getUUID());
                                                boolean character2Selected = this.selectedCharacters.contains(ch2.getUUID());

                                                int compValue = 1;

                                                if(character1Selected == character2Selected) compValue = 0;
                                                if(character1Selected) compValue = -1;

                                                return compValue;
                                            }),
                                            component -> this.sortEntries((ch1, ch2) -> {
                                                boolean character1Selected = this.selectedCharacters.contains(ch1.getUUID());
                                                boolean character2Selected = this.selectedCharacters.contains(ch2.getUUID());

                                                int compValue = -1;

                                                if(character1Selected == character2Selected) compValue = 0;
                                                if(character1Selected) compValue = 1;

                                                return compValue;
                                            })
                                    )).margins(Insets.bottom(1));
                        },
                        (character, mode, isParentVertical) -> {
                            return Components.button(Text.of(""), buttonComponent -> {
                                        if(!selectedCharacters.contains(character.getUUID())) {
                                            selectedCharacters.add(character.getUUID());
                                        } else {
                                            selectedCharacters.remove(character.getUUID());
                                        }
                                    }).renderer((drawContext, button, delta) -> {
                                        boolean isSelected = selectedCharacters.contains(character.getUUID());

                                        ButtonComponent.Renderer.VANILLA.draw(drawContext, button, delta);

                                        if(isSelected) {
                                            drawContext.drawRectOutline(button.x() + 2, button.y() + 2, button.width() - 4, button.height() - 4, new Color(0.95f, 0.95f, 0.95f).argb());
                                        }
                                    })
                                    .sizing(Sizing.fixed(8));
                        }
                ).addBuilder(
                        isParentVertical -> Components.label(Text.of("Created By")),
                        (character, mode, isParentVertical) -> {
                            //TODO: Figure out why Multithreading causing "Cannot invoke "io.wispforest.owo.ui.util.FocusHandler.lastFocusSource()" because "focusHandler" is null"
                            /*
                             * This is most likely caused by the dismounting of a component from when such is mounted and layed out on the main thread leading to
                             * the consequence of the parent being null and more issues. What must be done is the data is sent to the main thread to be updated
                             * and properly go thru the actions of dismounting their
                             */
                            LabelComponent component = (LabelComponent) Components.label(Text.of("Waiting for Response...              "))
                                    .maxWidth(125)
                                    .horizontalSizing(Sizing.fixed(125))
                                    .margins(Insets.of(2));

                            UUID uuid = null;

                            try {
                                uuid = UUID.fromString(character.getPlayerUUID());
                            } catch (IllegalArgumentException ignored){}

                            Consumer<@Nullable GameProfile> consumer = (profile1) -> componentChanges.add(() -> {
                                if(profile1 != null && !Constants.isErrored(profile1) && !profile1.getName().isBlank()) {
                                    component.text(Text.literal(profile1.getName()));
                                } else {
                                    String text = character.getPlayerUUID().isBlank()
                                            ? "Error: Character missing Player uuid Info!"
                                            : character.getPlayerUUID();

                                    component.text(Text.literal(text).formatted(Formatting.RED))
                                            .tooltip(Text.of("Could not find the given Players name, showing uuid"));
                                }
                            });

                            if(uuid != null){
                                DelayableGameProfile profile = UIOps.getDelayedProfile(uuid.toString());

                                Util.getIoWorkerExecutor()
                                        .submit(profile.wrapRunnable(consumer));
                            } else {
                                consumer.accept(null);
                            }

                            return component;

//                            LabelComponent component = Components.label(Text.empty());
//
//                            GameProfile profile = UIOps.getProfile(character.getPlayerUUID());
//
//                            if(Constants.isErrored(profile)){
//                                component.text(Text.literal(profile.getName()));
//                            } else {
//                                String text = !character.getPlayerUUID().isBlank()
//                                        ? character.getPlayerUUID()
//                                        : "Error: Character missing Player UUID Info!";
//
//                                component.text(Text.literal(text).formatted(Formatting.RED))
//                                        .tooltip(Text.of("Could not find the given Players name, showing UUID"));
//                            }
//
//                            return component.maxWidth(125).margins(Insets.of(2));
                        }
                ).addBuilder(
                        isParentVertical -> {
                            return Containers.horizontalFlow(Sizing.content(), Sizing.content())
                                    .child(
                                            Components.label(Text.of("Status"))
                                                    .margins(Insets.right(3))
                                    )
                                    .child(buildButton(
                                            buttons,
                                            component -> this.sortEntries(null),
                                            component -> this.sortEntries((ch1, ch2) -> {
                                                int compValue = 1;

                                                if(ch1.isDead() == ch2.isDead()) compValue = 0;
                                                if(ch1.isDead()) compValue = -1;

                                                return compValue;
                                            }),
                                            component -> this.sortEntries((ch1, ch2) -> {
                                                int compValue = -1;

                                                if(ch1.isDead() == ch2.isDead()) compValue = 0;
                                                if(ch1.isDead()) compValue = 1;

                                                return compValue;
                                            }))
                                    );
                        },
                        (character, mode, isParentVertical) -> {
                            Text labelText = (character.isDead())
                                    ? Text.literal("Deceased").formatted(Formatting.RED)
                                    : Text.literal("Living").formatted(Formatting.GREEN);

                            return Components.label(labelText)
                                    .margins(Insets.of(2));
                        }
                );

        this.characterLayout = characterLayout;

        mainLayout.child(
                Containers.verticalScroll(Sizing.content(), Sizing.fixed(155), characterLayout)
                        .surface(ExtraSurfaces.INVERSE_PANEL)
                        .padding(Insets.of(4))
        );

        //----

        mainLayout.child(Containers.horizontalFlow(Sizing.content(), Sizing.content())
                .configure((FlowLayout layout) -> {
                        layout.verticalAlignment(VerticalAlignment.CENTER)
                                .margins(Insets.vertical(2))
                                .id("page_bar");
                }));

        //----

        rootComponent.child(mainLayout.positioning(Positioning.relative(50, 50)));

        //----

        applyFiltersAndSorting();
    }

    //---

    @SafeVarargs
    public static MultiToggleButton buildButton(List<MultiToggleButton> buttons, Consumer<ButtonComponent> ...onToggleEvents){
        return (MultiToggleButton) MultiToggleButton.of(
                        List.of(
                                new MultiToggleButton.ToggleVariantInfo("-", "Normal Order", onToggleEvents[0]),
                                new MultiToggleButton.ToggleVariantInfo("⏶", "Sort Up", onToggleEvents[1]),
                                new MultiToggleButton.ToggleVariantInfo("⏷", "Sort Down", onToggleEvents[2])
                        )
                ).linkButton(buttons)
                .sizing(Sizing.fixed(9), Sizing.fixed(9)); //12
    }

    private static Component buildAdminButton(String tooltip, int uOffset, int bottomMargin, Consumer<ButtonComponent> buttonAction){
        return Components.button(Text.empty(), buttonAction)
                .renderer((drawContext, button, delta) -> {
                    ButtonComponent.Renderer.VANILLA.draw(drawContext, button, delta);

                    RenderSystem.enableDepthTest();
                    RenderSystem.setShaderTexture(0, ADMIN_BUTTON_TEXTURE);
                    drawContext.drawTexture(ADMIN_BUTTON_TEXTURE,button.x() + 4, button.y() + 4, uOffset, 0, 16, 16, 96, 16);
                })
                .tooltip(Text.of(tooltip))
                .sizing(Sizing.fixed(24), Sizing.fixed(24)) // 22
                .margins(Insets.bottom(bottomMargin));
    }

    public boolean shouldDoAdminAction(String action, boolean allowManySelected){
        boolean doAction = true;

        if(!allowManySelected && this.selectedCharacters.size() > 1){
            SystemToast.add(
                    MinecraftClient.getInstance().getToastManager(),
                    SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.of("Action has To Many Selected!"),
                    Text.of(action + " action isn't allowed to have more than one target!")
            );

            doAction = false;
        }

        if(this.selectedCharacters.isEmpty()){
            SystemToast.add(
                    MinecraftClient.getInstance().getToastManager(),
                    SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.of("Action has no Character Selected!"),
                    Text.of("You haven't made a selection on any character!")
            );

            doAction = false;
        }

        return doAction;
    }

    //---

    public void buildPageBar(FlowLayout rootComponent){
        FlowLayout pageBar = rootComponent.childById(FlowLayout.class, "page_bar");

        pageBar.clearChildren();

        this.pageCount = MathHelper.ceil(characterView.size() / (float) charactersPerPage);

        // 9 Max count

        int startingPageNumber = (pageCount < 9 || this.currentPageNumber <= 4) ? 1 : this.currentPageNumber - 4;

        int maxPageCount = Math.min(startingPageNumber + 9, pageCount);

        //Used to show least the first page when there is only a single page
        if(pageCount == 1) maxPageCount++;

        for(int currentPageNumber = startingPageNumber; currentPageNumber < maxPageCount; currentPageNumber++){
            int componentPageNumber = currentPageNumber;

            String buttonId = String.valueOf(componentPageNumber);

            pageBar.child(
                    ((ButtonAddonDuck<FlowLayout>) Containers.horizontalFlow(Sizing.fixed(12), Sizing.fixed(12))
                            .child(
                                    Components.label(Text.literal(buttonId)).id("button_label")
                            )
                            .horizontalAlignment(HorizontalAlignment.CENTER)
                            .verticalAlignment(VerticalAlignment.CENTER))
                            .setButtonAddon(layout -> {
                                return new ButtonAddon<>(layout)
                                        .useCustomButtonSurface(
                                                VariantButtonSurface.surfaceLike(Size.square(3), Size.square(48), false, ThemeHelper.isDarkMode(), false)
                                        )
                                        .onPress(button -> {
                                            if(this.currentPageNumber == componentPageNumber) return;

                                            travelToSelectPage(componentPageNumber);

                                            buildPageBar(this.uiAdapter.rootComponent);
                                        });
                            })
                            .margins((componentPageNumber != startingPageNumber) ? Insets.left(2) : Insets.of(0))
                            .id("page_button_" + buttonId)
            );
        }

        for(int i = 1; i <= pageCount; i++){
            FlowLayout buttonComponent = this.uiAdapter.rootComponent.childById(FlowLayout.class, "page_button_" + i);

            if(buttonComponent != null) {
                buttonComponent.childById(LabelComponent.class, "button_label")
                        .color(i == this.currentPageNumber ? Color.BLUE.interpolate(Color.WHITE, 0.35f) : ThemeHelper.dynamicColor());
            }
        }
    }

    public void travelToSelectPage(int newPageNumber){
        LabeledGridLayout gridLayout = this.uiAdapter.rootComponent.childById(LabeledGridLayout.class, "character_list");

        int startingIndex = (newPageNumber - 1) * this.charactersPerPage;
        int endingIndex = Math.min(startingIndex + this.charactersPerPage, this.characterView.size());

        gridLayout.clearEntries();

        if(this.characterView.size() > 0) gridLayout.addEntries(this.characterView.subList(startingIndex, endingIndex));

        gridLayout.rebuildComponents();

        for(int i = 1; i <= pageCount; i++){
            FlowLayout buttonComponent = this.uiAdapter.rootComponent.childById(FlowLayout.class, "page_button_" + i);

            if(buttonComponent != null) {
                buttonComponent.childById(LabelComponent.class, "button_label")
                        .color(i == newPageNumber ? Color.BLUE.interpolate(Color.WHITE, 0.35f) : ThemeHelper.dynamicColor());
            }
        }

        this.currentPageNumber = newPageNumber;
    }

    public void shouldAttemptUpdate(Character c){
        if(characterLayout.getCharactersWithinLayout().stream().anyMatch(c1 -> Objects.equals(c.getUUID(), c1.getUUID()))){
            applyFiltersAndSorting();

            this.travelToSelectPage(this.currentPageNumber);
        }
    }

    public List<Character> getSelectedCharacters(){
        return this.selectedCharacters.stream()
                .map(ClientCharacters.INSTANCE::getCharacter)
                .toList();
    }

    public void clearSelectedEntries(){
        this.selectedCharacters.clear();
    }

    //-------------------------------------------------------

    @Override public void setFilter(FilterFunc<Character> filter) { this.cached_filter = filter; }
    @Override public FilterFunc<Character> getFilter() { return this.cached_filter; }

    @Override public void setComparator(Comparator<Character> comparator) { this.cached_comparator = comparator; }
    @Override public Comparator<Character> getComparator() { return this.cached_comparator; }

    public void updateCharacterListComponent(){
        buildPageBar(this.uiAdapter.rootComponent);

        travelToSelectPage(1);
    }

    @Override
    public void applyFiltersAndSorting() {
        ModifiableCollectionHelper.super.applyFiltersAndSorting();

        updateCharacterListComponent();
    }

    @Override public List<Character> getList() { return this.characterView; }
    @Override public List<Character> getDefaultList() { return this.defaultCharacterView; }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
