package io.blodhgarm.personality.compat.origins.client.gui;

import com.mojang.authlib.GameProfile;
import io.blodhgarm.personality.api.character.BaseCharacter;
import io.blodhgarm.personality.api.addon.BaseAddon;
import io.blodhgarm.personality.client.gui.ThemeHelper;
import io.blodhgarm.personality.client.gui.CharacterViewMode;
import io.blodhgarm.personality.client.gui.utils.polygons.ComponentAsPolygon;
import io.blodhgarm.personality.compat.origins.OriginAddon;
import io.blodhgarm.personality.compat.origins.client.gui.components.OriginHeaderComponent;
import io.blodhgarm.personality.compat.origins.client.gui.components.OriginImpactComponent;
import io.blodhgarm.personality.compat.origins.client.gui.components.OriginInfoContainer;
import io.blodhgarm.personality.api.addon.client.PersonalityScreenAddon;
import io.blodhgarm.personality.client.gui.utils.owo.ExtraSurfaces;
import io.blodhgarm.personality.misc.pond.owo.InclusiveBoundingArea;
import io.github.apace100.origins.Origins;
import io.github.apace100.origins.origin.*;
import io.github.apace100.origins.registry.ModItems;
import io.wispforest.owo.nbt.NbtKey;
import io.wispforest.owo.ui.base.BaseParentComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.function.Consumer;

public class OriginSelectionDisplayAddon extends PersonalityScreenAddon {

    public static Identifier ORIGINS_GUI_TEXTURE = new Identifier("personality", "textures/gui/origins_gui.png");

    public static final NbtKey<String> SKULL_OWNER_KEY = new NbtKey<>("SkullOwner", NbtKey.Type.STRING);

    //----------------------------

    private final ArrayList<OriginLayer> layerList;

    private final Map<OriginLayer, OriginLayerHelper> originLayerHelpers = new HashMap<>();
    private final Map<OriginLayer, Origin> selectedOrigins = new HashMap<>();

    private Origin currentOrigin;

    protected int scrollPos = 0;
    public static float time = 0;

    //----------------------------


    //----------------------------

    private int currentLayerIndex;
    private int currentOriginIndex = 0;
    private List<Origin> originSelection;
    private int maxSelection;

    //----------------------------


    //----------------------------

    public OriginSelectionDisplayAddon(CharacterViewMode mode, GameProfile playerProfile, @Nullable BaseCharacter character){
        this(mode, playerProfile, character, 0);
    }

    public OriginSelectionDisplayAddon(CharacterViewMode mode,  GameProfile playerProfile, @Nullable BaseCharacter character, int currentLayerIndex) {
        super(mode, playerProfile, character, new Identifier("origins", "origin_selection_addon"));

        this.layerList = new ArrayList<>();

        OriginLayers.getLayers().forEach(layer -> {
            if(layer.isEnabled()) layerList.add(layer);
        });

        Collections.sort(layerList);

        this.currentLayerIndex = currentLayerIndex;

        if(this.mode.importFromCharacter()){
            layerList.forEach(layer -> {
                OriginAddon addon = (OriginAddon) character.getAddon(layer.getIdentifier());

                try {
                    if (addon != null) selectedOrigins.put(layer, OriginRegistry.get(addon.getOriginId()));
                } catch (IllegalArgumentException e){
                    System.out.println("Oh No");
                    System.out.println(e.getMessage());
                }
            });
        }

        setupForSelectedLayer();
    }

    private Optional<AbstractClientPlayerEntity> getPlayerOptional(){
        return MinecraftClient.getInstance().world.getPlayers().stream()
                .filter(p -> p.getUuid() == this.playerProfile.getId())
                .findAny();
    }

    private void setupForSelectedLayer(){
        this.originSelection = new ArrayList<>(10);

        OriginLayer currentLayer = this.layerList.get(this.currentLayerIndex);
        Origin origin = this.selectedOrigins.get(currentLayer);

        if(!this.mode.isModifiableMode()) {
            if(origin != null) {
                ItemStack displayItem = origin.getDisplayItem();

                if (displayItem.getItem() == Items.PLAYER_HEAD && !displayItem.has(SKULL_OWNER_KEY)) {
                    displayItem.put(SKULL_OWNER_KEY, playerProfile.getName());
                }
            } else {
                origin = Origin.EMPTY;
            }

            this.originSelection.add(origin);
        } else {
            this.maxSelection = generateLayerData(this.originSelection, currentLayer);

            if(this.maxSelection == 0) {
                currentLayerIndex++;

                //TODO: Um idk if such is a good idea ngl
                if(currentLayerIndex >= layerList.size()) return;

                setupForSelectedLayer();
            }
        }

        if(origin == null){
            this.currentOriginIndex = 0;

            origin = getCurrentOriginInternal();
        } else {
            // If the OriginLayer was already selected, then we should set the display to such
            this.currentOriginIndex = this.originSelection.indexOf(origin);
        }

        resetWithOrigin(origin);
    }

    private int generateLayerData(List<Origin> originSelection, OriginLayer currentLayer){
        Optional<AbstractClientPlayerEntity> player = getPlayerOptional();

        List<Identifier> originIdentifiers = player.isPresent() ? currentLayer.getOrigins(player.get()) : currentLayer.getOrigins();

        originIdentifiers.forEach(originId -> {
            Origin origin = OriginRegistry.get(originId);

            if(!origin.isChoosable()) return;

            ItemStack displayItem = origin.getDisplayItem();

            if(displayItem.getItem() == Items.PLAYER_HEAD && !displayItem.has(SKULL_OWNER_KEY)) {
                displayItem.put(SKULL_OWNER_KEY, playerProfile.getName());
            }

            originSelection.add(origin);
        });

        originSelection.sort((a, b) -> {
            int impDelta = a.getImpact().getImpactValue() - b.getImpact().getImpactValue();
            return impDelta == 0 ? a.getOrder() - b.getOrder() : impDelta;
        });

        int maxSelection = originSelection.size();

        if(currentLayer.isRandomAllowed() && player.isPresent() && currentLayer.getRandomOrigins(player.get()).size() > 0) {
            maxSelection += 1;
        }

        if(!originLayerHelpers.containsKey(currentLayer)){
            originLayerHelpers.put(currentLayer, new OriginLayerHelper(currentLayer, originSelection.size() != 0 ? originSelection.get(0) : null, maxSelection));
        }

        return maxSelection;
    }

    //----------------------------

    public void resetWithOrigin(Origin origin) {
        this.currentOrigin = origin;
        this.scrollPos = 0;
        this.time = 0;
    }

    //----------------------------


    //----------------------------

    private Origin getCurrentOriginInternal() {
        OriginLayer layer = layerList.get(currentLayerIndex);

        if(currentOriginIndex == originSelection.size()) {
            OriginLayerHelper helper = originLayerHelpers.get(layer);

            if(helper.randomOrigin == null) initRandomOrigin(layerList);

            return helper.randomOrigin;
        }

        return originSelection.get(currentOriginIndex);
    }

    private void initRandomOrigin(ArrayList<OriginLayer> layerList) {
        Optional<AbstractClientPlayerEntity> player = getPlayerOptional();

        Origin randomOrigin = new Origin(Origins.identifier("random"), new ItemStack(ModItems.ORB_OF_ORIGIN), Impact.NONE, -1, Integer.MAX_VALUE);

        MutableText randomOriginText = (MutableText)Text.of("");

        if(player.isPresent()) {
            List<Identifier> randoms = layerList.get(currentLayerIndex).getRandomOrigins(player.get());

            randoms.sort((ia, ib) -> {
                Origin a = OriginRegistry.get(ia);
                Origin b = OriginRegistry.get(ib);
                int impDelta = a.getImpact().getImpactValue() - b.getImpact().getImpactValue();
                return impDelta == 0 ? a.getOrder() - b.getOrder() : impDelta;
            });

            for (Identifier id : randoms) {
                randomOriginText.append(OriginRegistry.get(id).getName());
                randomOriginText.append(Text.of("\n"));
            }
        } else {
            randomOriginText.append("SHIT HAS HIT THE FAN AND YOU GET NOTHING!");
        }

        originLayerHelpers.get(layerList.get(currentLayerIndex)).setRandomInfo(randomOrigin, randomOriginText);
    }

    public boolean isOriginRandom(Origin origin){
        Origin randomOrigin = this.originLayerHelpers.get(this.layerList.get(currentLayerIndex)).randomOrigin;

        return randomOrigin != null && origin == randomOrigin;
    }

    public Origin getCurrentOrigin() {
        return currentOrigin;
    }

    //----------------------------


    //----------------------------

    @Override
    public FlowLayout build(boolean isDarkMode){
        Surface panel = isDarkMode ? Surface.DARK_PANEL : Surface.PANEL;

        FlowLayout rootComponent = Containers.verticalFlow(Sizing.content(), Sizing.content());

        OriginLayer currentLayer = layerList.get(currentLayerIndex);

        Text randomOriginText = null;

        if(mode.isModifiableMode() && isOriginRandom(getCurrentOrigin())){
            randomOriginText = originLayerHelpers.get(currentLayer).randomOriginText;
        }

        FlowLayout originLayout = Containers.verticalFlow(Sizing.fixed(176), Sizing.content()); //182

        originLayout.child(
                    Components.button(Text.of("❌"), (ButtonComponent component) -> this.closeAddon())
                            .renderer(ButtonComponent.Renderer.flat(0,0,0))
                            .sizing(Sizing.fixed(12))
                            .positioning(Positioning.absolute(155, -2))
        );

        originLayout.child(
                Containers.horizontalFlow(Sizing.content(), Sizing.content())
                        .child(
                                Components.button(Text.of("<"), createButtonAction(ButtonVariant.LEFT, rootComponent))
                                        .sizing(Sizing.fixed(10))
                        )
                        .child(
                                Containers.horizontalFlow(Sizing.fixed(60), Sizing.content())
                                        .child(
                                                Components.label(Text.translatable(currentLayer.getTranslationKey()))
                                                        .color(ThemeHelper.dynamicColor())
                                                        .id("layer_label")
                                        )
                                        .horizontalAlignment(HorizontalAlignment.CENTER)
                                        .margins(Insets.top(1))
                        )
                        .child(
                                Components.button(Text.of(">"), createButtonAction(ButtonVariant.RIGHT, rootComponent))
                                        .sizing(Sizing.fixed(10))
                        )
                        .horizontalAlignment(HorizontalAlignment.CENTER)
                        .surface(panel)
                        .padding(Insets.of(4, 4, 4, 4))
                        .margins(Insets.bottom(2))
        );

//        originLayout.child(
//                Components.box(Sizing.fixed(150), Sizing.fixed(1))
//                        .color(Color.ofArgb(0xFFa0a0a0))
//                        .margins(Insets.of(3,4,0,0))
//        );

        originLayout.child(
                        Containers.verticalFlow(Sizing.fixed(162), Sizing.fixed(143))// y = 168
                                .child(
                                        new OriginHeaderComponent(Sizing.fixed(150), Sizing.fixed(26), getCurrentOrigin(), layerList.get(currentLayerIndex))
                                                .margins(Insets.of(4,0,4,0))
                                                .verticalAlignment(VerticalAlignment.CENTER)
                                                .zIndex(1)
                                )
                                .child(Containers.verticalScroll(Sizing.fixed(150), Sizing.fixed(121),// y = 142, x = 154
                                                new OriginInfoContainer(Sizing.fixed(137), Sizing.content(), getCurrentOrigin(), randomOriginText, mode.isModifiableMode() && isOriginRandom(getCurrentOrigin())) //143
                                                        .margins(Insets.left(6)
                                                        ).zIndex(1))
                                        .scrollbar(ScrollContainer.Scrollbar.vanilla())
                                        .scrollbarThiccness(8)
                                        .fixedScrollbarLength(27)
                                        .positioning(Positioning.absolute(6, 20))
                                        .padding(Insets.of(8, 4, 0,0))
                                        .id("origin_info"))
                                .surface(ExtraSurfaces.INVERSE_PANEL)
//                                .margins(Insets.of(1,0,0,0))
                );

        FlowLayout selectionControl = Containers.horizontalFlow(Sizing.content(), Sizing.content());

        if(this.mode.isModifiableMode()){
            selectionControl
                    .child(Components.button(Text.of("<"), (ButtonComponent button) -> {
                                currentOriginIndex = (currentOriginIndex - 1 + maxSelection) % maxSelection;
                                Origin newOrigin = getCurrentOriginInternal();
                                resetWithOrigin(newOrigin);
                                updateOriginData(rootComponent);
                            }).sizing(Sizing.fixed(20))
                    )
                    .child(Components.button(Text.translatable(Origins.MODID + ".gui.select"), (ButtonComponent button) -> {
                                selectedOrigins.put(layerList.get(currentLayerIndex), getCurrentOriginInternal());

                                this.currentLayerIndex = this.currentLayerIndex + 1;

                                if(this.currentLayerIndex >= this.layerList.size()){
                                    this.currentLayerIndex = 0;
                                }

                                setupForSelectedLayer();

                                rootComponent.childById(LabelComponent.class, "layer_label")
                                        .text(Text.translatable(layerList.get(currentLayerIndex).getTranslationKey()));

                                updateOriginData(rootComponent);

                                if(selectedOrigins.keySet().containsAll(layerList)) {
                                    this.closeAddon();
                                }
                            }).sizing(Sizing.fixed(100), Sizing.fixed(20))
                            .margins(Insets.horizontal(10)))
                    .child(Components.button(Text.of(">"), (ButtonComponent button) -> {
                                currentOriginIndex = (currentOriginIndex + 1) % maxSelection;
                                Origin newOrigin = getCurrentOriginInternal();
                                resetWithOrigin(newOrigin);
                                updateOriginData(rootComponent);
                            }).sizing(Sizing.fixed(20))
                    );
        } else {
            selectionControl.sizing(Sizing.content(), Sizing.fixed(2));
        }

        originLayout.child(selectionControl
                //.positioning(Positioning.absolute(8, 150))
                .margins(Insets.top(5))
        );

        return (FlowLayout) rootComponent
                .child(originLayout.horizontalAlignment(HorizontalAlignment.CENTER)
                        .padding(Insets.of(6)) //6
                        .surface(panel)
                )
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .margins(Insets.left(ThemeHelper.guiScale4OrAbove() ? 2 : 10));
    }

    private enum ButtonVariant { LEFT,RIGHT; }

    private Consumer<ButtonComponent> createButtonAction(ButtonVariant variant, BaseParentComponent rootComponent){
        return buttonComponent -> {
            if(variant == ButtonVariant.LEFT) {
                this.currentLayerIndex = this.currentLayerIndex - 1;

                if (this.currentLayerIndex < 0) {
                    this.currentLayerIndex = this.layerList.size() - 1;
                }
            } else {
                this.currentLayerIndex = this.currentLayerIndex + 1;

                if(this.currentLayerIndex >= this.layerList.size()){
                    this.currentLayerIndex = 0;
                }
            }

            setupForSelectedLayer();

            if(getObserver().isAddonOpen(this)){
                rootComponent.childById(LabelComponent.class, "layer_label")
                        .text(Text.translatable(layerList.get(currentLayerIndex).getTranslationKey()));

                updateOriginData(rootComponent);
            } else {
                branchUpdate();
            }
        };
    }

    @Override
    public void branchUpdate() {
        if(getRootComponent() != null) {
            getRootComponent().childById(OriginHeaderComponent.class, "addon_component_origin_header").Origin(getCurrentOrigin(), layerList.get(currentLayerIndex));
        }
    }

    @Override
    public Component buildBranchComponent(BaseParentComponent rootComponent) {
        ButtonComponent button = Components.button(Text.of(this.mode.isModifiableMode() ? "✎" : "♢"), c -> getObserver().pushScreenAddon(this));

        button.sizing(Sizing.fixed(12))
                .positioning(Positioning.absolute(ThemeHelper.guiScale4OrAbove() ? 86 : 106, 30));

        FlowLayout layout = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(26 + 7))
                .child(
                        new OriginHeaderComponent(Sizing.fixed(122), Sizing.fixed(32), getCurrentOrigin(), layerList.get(currentLayerIndex))
                                .shortVersion(true)
                                .showLayerInfo(true)
                                .showButtons(
                                        createButtonAction(ButtonVariant.LEFT, rootComponent),
                                        createButtonAction(ButtonVariant.RIGHT, rootComponent)
                                )
                                .rebuildComponent()
                                .id("addon_component_origin_header")
//                                .margins(Insets.right(2))
                )
                .child(button);

        ((InclusiveBoundingArea<FlowLayout>) layout).addInclusionZone(new ComponentAsPolygon(button));

        return layout.allowOverflow(true)
                //.horizontalAlignment(HorizontalAlignment.CENTER)
                //.verticalAlignment(VerticalAlignment.CENTER)
                .margins(Insets.of(2, 0, 3, 0));
    }

    @Override
    public Map<Identifier, BaseAddon> getAddonData() {
        Map<Identifier, BaseAddon> addonData = new HashMap<>();

        layerList.stream()
            .filter(originLayer -> !selectedOrigins.containsKey(originLayer))
            .forEach(originLayer -> {
                OriginLayerHelper helper = originLayerHelpers.get(originLayer);

                if(helper == null){
                    generateLayerData(new ArrayList<>(10), originLayer);

                    helper = originLayerHelpers.get(originLayer);
                }

                if(helper.maxSelection == 0) return;

                selectedOrigins.putIfAbsent(originLayer, originLayerHelpers.get(originLayer).defaultOrigin);
            });

        selectedOrigins.forEach((originLayer, origin1) ->
            addonData.put(originLayer.getIdentifier(), new OriginAddon(origin1.getIdentifier(), originLayer.getIdentifier()))
        );

        return addonData;
    }

    @Override
    public boolean isDataEmpty(BaseParentComponent rootComponent) {
        return false;
    }

    public void updateOriginData(BaseParentComponent rootComponent){
        ScrollContainer<?> container = rootComponent.childById(ScrollContainer.class, "origin_info");

        OriginInfoContainer child = ((OriginInfoContainer)container.child());

        //container.onChildMutated(child);
        container.scrollTo(child);

        if(this.mode.isModifiableMode() && isOriginRandom(getCurrentOrigin())){
            child.randomOrigin(getCurrentOrigin(), originLayerHelpers.get(layerList.get(currentLayerIndex)).randomOriginText);
        } else {
            child.origin(getCurrentOrigin());
        }

        rootComponent.childById(LabelComponent.class, "origin_name").text(getCurrentOrigin().getName());
        rootComponent.childById(OriginImpactComponent.class, "origin_impact").setImpact(getCurrentOrigin().getImpact());
        rootComponent.childById(ItemComponent.class, "origin_icon").stack(getCurrentOrigin().getDisplayItem());

        branchUpdate();
    }

    private static class OriginLayerHelper {
        public Origin randomOrigin = null;
        public MutableText randomOriginText = null;

        public final Origin defaultOrigin;
        public final OriginLayer layer;
        public final int maxSelection;

        public OriginLayerHelper(OriginLayer layer, Origin defaultOrigin, int maxSelection){
            this.layer = layer;
            this.defaultOrigin = defaultOrigin;
            this.maxSelection = maxSelection;
        }

        public void setRandomInfo(Origin randomOrigin, MutableText randomOriginText){
            this.randomOrigin = randomOrigin;
            this.randomOriginText = randomOriginText;
        }
    }

    //----------------------------
}
