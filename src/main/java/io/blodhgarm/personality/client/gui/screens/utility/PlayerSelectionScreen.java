package io.blodhgarm.personality.client.gui.screens.utility;

import io.blodhgarm.personality.client.gui.ThemeHelper;
import io.blodhgarm.personality.client.gui.components.builders.LabeledObjectToComponent;
import io.blodhgarm.personality.client.gui.components.grid.LabeledGridLayout;
import io.blodhgarm.personality.client.gui.components.grid.MultiToggleButton;
import io.blodhgarm.personality.client.gui.components.grid.SearchbarComponent;
import io.blodhgarm.personality.client.gui.screens.AdminCharacterScreen;
import io.blodhgarm.personality.client.gui.utils.UIOps;
import io.blodhgarm.personality.client.gui.utils.owo.ExtraSurfaces;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class PlayerSelectionScreen extends BaseOwoScreen<FlowLayout> {

    @Nullable private Screen originScreen = null;

    private final List<PlayerListEntry> selectedPlayers = new ArrayList<>();

    private final Consumer<List<PlayerListEntry>> selectedAction;

    private boolean setSingleSelection = false;
    private boolean allowForNoSelection = false;

    public PlayerSelectionScreen(Screen originScreen, Consumer<List<PlayerListEntry>> selectedAction){
        this.originScreen = originScreen;

        this.selectedAction = selectedAction;
    }

    public PlayerSelectionScreen(Consumer<List<PlayerListEntry>> selectedAction){
        this.selectedAction = selectedAction;
    }

    public PlayerSelectionScreen setSingleSelection(boolean value){
        this.setSingleSelection = value;

        return this;
    }

    public PlayerSelectionScreen allowForNoSelection(boolean value){
        this.allowForNoSelection = value;

        return this;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        FlowLayout mainLayout = Containers.verticalFlow(Sizing.content(), Sizing.content())
                .configure((FlowLayout layout) -> {
                    layout.positioning(Positioning.relative(50, 50))
                            .surface(ThemeHelper.dynamicSurface())
                            .verticalAlignment(VerticalAlignment.CENTER)
                            .horizontalAlignment(HorizontalAlignment.CENTER)
                            .padding(Insets.of(6));
                });

        Collection<PlayerListEntry> playerList = this.client.getNetworkHandler().getPlayerList();

        LabeledGridLayout<PlayerListEntry> playerLayout = new LabeledGridLayout<PlayerListEntry>(Sizing.content(), Sizing.content())
                .addEntries(playerList)
                .configure(layout -> {
                    layout.setRowDividingLine(1)
                            .setColumnDividingLine(1);
                });

        List<MultiToggleButton> buttons = new ArrayList<>();

        playerLayout
                .addBuilder(0,
                        new LabeledObjectToComponent<>(
                                isParentVertical -> {
                                    return Containers.verticalFlow(Sizing.content(), Sizing.content())
                                            .child(AdminCharacterScreen.buildButton(
                                                    buttons,
                                                    component -> playerLayout.sortEntries(null),
                                                    component -> playerLayout.sortEntries((ch1, ch2) -> {
                                                        boolean character1Selected = this.selectedPlayers.contains(ch1);
                                                        boolean character2Selected = this.selectedPlayers.contains(ch2);

                                                        int compValue = 1;

                                                        if(character1Selected == character2Selected) compValue = 0;
                                                        if(character1Selected) compValue = -1;

                                                        return compValue;
                                                    }),
                                                    component -> playerLayout.sortEntries((ch1, ch2) -> {
                                                        boolean character1Selected = this.selectedPlayers.contains(ch1);
                                                        boolean character2Selected = this.selectedPlayers.contains(ch2);

                                                        int compValue = -1;

                                                        if(character1Selected == character2Selected) compValue = 0;
                                                        if(character1Selected) compValue = 1;

                                                        return compValue;
                                                    })
                                            )).margins(Insets.bottom(1));
                                },
                                (entry, isParentVertical) -> {
                                    return Components.button(Text.of(""), buttonComponent -> {
                                                if(!selectedPlayers.contains(entry)) {
                                                    if(setSingleSelection && !selectedPlayers.isEmpty()) return;

                                                    selectedPlayers.add(entry);
                                                } else {
                                                    selectedPlayers.remove(entry);
                                                }
                                            }).renderer((drawContext, button, delta) -> {
                                                boolean isSelected = selectedPlayers.contains(entry);

                                                ButtonComponent.Renderer.VANILLA.draw(drawContext, button, delta);

                                                if(isSelected) {
                                                    drawContext.drawRectOutline(button.x() + 2, button.y() + 2, button.width() - 4, button.height() - 4, new Color(0.95f, 0.95f, 0.95f).argb());
                                                }
                                            })
                                            .sizing(Sizing.fixed(8));
                                }
                        )
                )
                .addBuilder(-1,
                        new LabeledObjectToComponent<>(
                                isParentVertical -> Components.label(Text.of("")),
                                (entry, isParentVertical) -> {
                                    return UIOps.playerEntityComponent(Sizing.fixed(20), entry.getProfile())
                                            .scale(0.65f)
                                            .margins(Insets.of(3));
                                }
                        )
                )
                .addBuilder(-1,
                        new LabeledObjectToComponent<>(
                                isParentVertical -> {
                                    return Components.label(Text.of("Name"))
                                            .horizontalSizing(Sizing.fixed(100));
                                },
                                (entry, isParentVertical) -> {
                                    return Components.label(Text.of(entry.getProfile().getName()))
                                            .margins(Insets.of(3));
                                }
                        )
                );


        mainLayout.child(
                new SearchbarComponent<>(playerLayout, item -> item.getDisplayName().getString())
                        .configure((SearchbarComponent<?> component) -> {
                                component.adjustTextboxWidth(Sizing.fixed(200))
                                        .verticalAlignment(VerticalAlignment.CENTER);
                        }).build()
        ).child(
                Containers.verticalScroll(Sizing.content(), Sizing.fixed(120), playerLayout)
                        .surface(ExtraSurfaces.INVERSE_PANEL)
                        .padding(Insets.of(3))
        ).child(
                Containers.horizontalFlow(Sizing.content(), Sizing.content())
                        .child(
                                Components.button(Text.of("Back"), component -> cancelAction())
                                        .horizontalSizing(Sizing.fixed(50))
                        )
                        .child(
                                Components.button(Text.of("Confirm"), component -> acceptAction())
                                        .horizontalSizing(Sizing.fixed(50))
                                        .margins(Insets.left(4))
                        ).margins(Insets.top(3))
        );

        rootComponent.child(mainLayout);
    }

    public void rebuildPlayerList(){

    }

    public void cancelAction(){
        if(this.originScreen == null){
            this.close();
        } else {
            MinecraftClient.getInstance().setScreen(originScreen);
        }
    }

    public void acceptAction(){
        if(!allowForNoSelection && this.selectedPlayers.isEmpty()) {
            return;
        }

        selectedAction.accept(this.selectedPlayers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
